#!/usr/bin/env node
'use strict';

/**
 * Bug Pattern Check Hook（PostToolUse 异步）
 *
 * 在 Write/Edit 工具调用后自动对 .java 文件执行 6 类轻量 bug 模式检测：
 *   1. 空指针风险 — 链式调用无 null 保护
 *   2. 金额精度  — double/float 用于金额字段
 *   3. 日期边界  — 日期比较只用 </> 无 <=/>=
 *   4. 校验缺失  — Controller 方法缺少 ParamsValid
 *   5. 事务内RPC — @Transactional 方法内调外部服务
 *   6. 幂等性    — 关键操作缺少 @RedisLockable
 *
 * 输出到 stderr，全部为警告级别，exit(0) 不阻塞流程。
 */

const fs = require('fs');
const path = require('path');

// ── Config ─────────────────────────────────────────────────
const MAX_LINES = 300; // 只检测前 300 行（性能约束）

// ── Entry ──────────────────────────────────────────────────
let data = '';
process.stdin.setEncoding('utf-8');
process.stdin.on('data', (chunk) => { data += chunk; });
process.stdin.on('end', () => {
  try {
    const input = JSON.parse(data);
    const filePath = extractFilePath(input);
    if (!filePath || !filePath.endsWith('.java')) {
      process.exit(0);
    }
    if (!fs.existsSync(filePath)) {
      process.exit(0);
    }

    const content = fs.readFileSync(filePath, 'utf-8');
    const lines = content.split('\n');
    const scope = lines.slice(0, MAX_LINES).join('\n');

    const warnings = [];
    checkNullPointer(scope, filePath, warnings);
    checkAmountPrecision(scope, filePath, warnings);
    checkDateBoundary(scope, filePath, warnings);
    checkValidationMissing(scope, filePath, warnings);
    checkTransactionalRPC(scope, filePath, warnings);
    checkIdempotency(scope, filePath, warnings);

    if (warnings.length > 0) {
      process.stderr.write('\n[bug-pattern-check] 检测到 ' + warnings.length + ' 个潜在风险:\n\n');
      warnings.forEach((w) => process.stderr.write(w + '\n'));
      process.stderr.write('\n');
    }
  } catch (err) {
    // Silent fail — never block the user
    process.stderr.write('[bug-pattern-check] 检测异常: ' + err.message + '\n');
  }
  process.exit(0);
});
setTimeout(() => process.exit(0), 5000);

function extractFilePath(data) {
  const input = data.tool_input || data.input || {};
  return input.file_path || input.path || '';
}

// ── Check 1: Null Pointer ─────────────────────────────────
function checkNullPointer(scope, filePath, warnings) {
  // 链式 getter 调用 >= 3 层且无 Optional/nullCheck
  const chainRegex = /(\w+)\s*=\s*(\w+(?:\.\w+)*)\.(get\w+\(\))(?:\.(get\w+\(\)))/g;
  let match;
  while ((match = chainRegex.exec(scope)) !== null) {
    const expr = match[0];
    // Skip if already in Optional or has null check nearby
    if (expr.includes('Optional.') || expr.includes('.orElse') || expr.includes('.ifPresent')) continue;
    warnings.push(formatWarn(filePath, match.index, '🔴 空指针风险', '链式getter无null保护',
      'yxhd-14907', '建议用 Optional.ofNullable().map().orElse() 链式null安全'));
    break; // one per pattern
  }

  // List.size() without CollectionUtils check or preceding null guard
  const listSizeRegex = /(\w+)\s*\.\s*size\s*\(\s*\)/g;
  while ((match = listSizeRegex.exec(scope)) !== null) {
    const varName = match[1];
    // Check if there's a null guard within the last 2 lines before this
    const before = scope.slice(Math.max(0, match.index - 200), match.index);
    if (!before.includes('CollectionUtils.isEmpty') && !before.includes('CollectionUtils.isNotEmpty')
        && !before.includes(varName + ' != null') && !before.includes(varName + '!=null')) {
      warnings.push(formatWarn(filePath, match.index, '🔴 空指针风险', `'${varName}.size()' 缺少空判断`,
        'yxhd-14907', '建议使用 CollectionUtils.isEmpty() / Optional.ofNullable()'));
      break;
    }
  }
}

// ── Check 2: Amount Precision ──────────────────────────────
function checkAmountPrecision(scope, filePath, warnings) {
  // Fields: double/float with amount-related names
  const amountFieldRegex = /(?:private|protected|public)\s+(double|float)\s+(\w*(?:amount|price|money|fee|subsidy|rebate|coupon|budget|cost|salary)\w*)/gi;
  let match;
  while ((match = amountFieldRegex.exec(scope)) !== null) {
    warnings.push(formatWarn(filePath, match.index, '🔴 金额精度', `金额字段 '${match[2]}' 使用 ${match[1]}`,
      'yxhd-16905', '必须使用 BigDecimal'));
  }

  // BigDecimal.doubleValue() in computation context
  if (/\.doubleValue\s*\(\s*\)/.test(scope)) {
    warnings.push(formatWarn(filePath, scope.search(/\.doubleValue\s*\(\s*\)/), '🟡 金额精度', 'BigDecimal 转 double 计算',
      'yxhd-16905', '全程使用 BigDecimal 运算，指定 scale+RoundingMode'));
  }

  // BigDecimal.equals() — should use compareTo
  if (/\.equals\s*\(\s*new\s+BigDecimal/.test(scope) || /BigDecimal.*\.equals\(/.test(scope)) {
    warnings.push(formatWarn(filePath, scope.search(/\.equals\s*\(/), '🟡 金额精度', 'BigDecimal 用 equals 比较',
      'yxhd-16905', 'BigDecimal 比较请使用 .compareTo()，equals 对精度敏感'));
  }
}

// ── Check 3: Date Boundary ─────────────────────────────────
function checkDateBoundary(scope, filePath, warnings) {
  // Date comparison: .before() / .after() chain without .before()+.after() combo
  // Pattern: after(A) && before(B) — should be !before(A) && !after(B)
  if (/\.after\s*\(.*\)\s*&&\s*.*\.before\s*\(/.test(scope)) {
    warnings.push(formatWarn(filePath, scope.search(/\.after\s*\(.*\)\s*&&\s*.*\.before\s*\(/), '🟡 日期边界',
      '日期范围不含边界（after+and+before）',
      'yxhd-16924', '建议用 !date.before(start) && !date.after(end) 包含边界'));
  }

  // BETWEEN without boundary confirmation comment
  const betweenMatch = scope.match(/BETWEEN\s+#\{(\w+)\}\s+AND\s+#\{(\w+)\}/gi);
  if (betweenMatch) {
    warnings.push(formatWarn(filePath, scope.search(/BETWEEN/), '🔵 日期提示',
      'SQL BETWEEN 闭区间确认',
      'yxhd-16924', 'SQL BETWEEN 是闭区间 [A,B]，确认业务是否需要 [start, end)'));
  }
}

// ── Check 4: Validation Missing ────────────────────────────
function checkValidationMissing(scope, filePath, warnings) {
  // Only check Controller classes
  if (!scope.includes('@RestController') && !scope.includes('@Controller')) return;

  // Check for public methods with _appId param but NO ParamsValid
  const publicMethodRegex = /public\s+\w+\s+(\w+)\s*\(/g;
  let match;
  let hasParamsValid = scope.includes('ParamsValid');
  let hasPublicMethod = publicMethodRegex.test(scope);

  if (hasPublicMethod && !hasParamsValid && scope.includes('String _appId')) {
    warnings.push(formatWarn(filePath, scope.search(/public\s+\w+\s+\w+\s*\(/), '🟡 校验缺失',
      'Controller 方法未使用 ParamsValid 校验',
      'yxhd-14733', 'Controller public 方法第一行必须使用 ParamsValid 链式校验参数'));
  }
}

// ── Check 5: Transactional RPC ──────────────────────────────
function checkTransactionalRPC(scope, filePath, warnings) {
  if (!/@Transactional/.test(scope)) return;

  // Check for RPC calls inside transactional methods
  const rpcPatterns = [
    { pattern: /HttpGateway\.execute/, name: 'HttpGateway RPC 调用' },
    { pattern: /\.send\s*\(/, name: 'MQ 发送' },
    { pattern: /redisTemplate\./, name: 'Redis 操作' },
    { pattern: /msgQueueService\./, name: 'MQ 服务调用' },
  ];

  rpcPatterns.forEach(({ pattern, name }) => {
    if (pattern.test(scope)) {
      warnings.push(formatWarn(filePath, scope.search(pattern), '🔴 事务内RPC',
        `@Transactional 方法内存在 ${name}`,
        'java-guide第3条', '请将 RPC/MQ/Redis 移到事务方法外'));
    }
  });
}

// ── Check 6: Idempotency ───────────────────────────────────
function checkIdempotency(scope, filePath, warnings) {
  // Check for create/save/submit methods without @RedisLockable
  const criticalMethodRegex = /(?:public|private)\s+\w+\s+(create|save|submit|pay|refund|settle)(\w*)\s*\(/gi;
  let match;
  while ((match = criticalMethodRegex.exec(scope)) !== null) {
    const methodName = match[1] + (match[2] || '');
    // Skip if already has RedisLockable
    if (scope.includes('@RedisLockable')) continue;
    // Only warn for obvious names
    if (/create|submit|pay|refund|settle/i.test(methodName)) {
      warnings.push(formatWarn(filePath, match.index, '🟡 幂等性',
        `'${methodName}' 方法缺少 @RedisLockable`,
        'yxhd-14336', '关键操作建议添加 @RedisLockable 分布式锁防重'));
      break; // one warning per file
    }
  }
}

// ── Output Formatting ──────────────────────────────────────
function formatWarn(filePath, offset, severity, message, bugRef, suggestion) {
  return `  ${severity} ${filePath}\n    问题: ${message}\n    参考bug: ${bugRef}\n    建议: ${suggestion}\n`;
}
