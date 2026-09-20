#!/usr/bin/env node
'use strict';

/**
 * Telemetry 采集脚本（跨平台 Node 实现，零 npm 依赖）
 * 记录工作流阶段事件到 openspec/changes/{changeId}/telemetry.jsonl
 * 每行一个 JSON：{"ts":<epoch秒>,"event":"<类型>","stage":"<阶段>","detail":"<描述>"}
 *
 * 用法（主 agent 显式调用，changeId 必填，禁止自动探测）:
 *   node telemetry-log.js <changeId> stage-start  <阶段号>
 *   node telemetry-log.js <changeId> stage-end    <阶段号>
 *   node telemetry-log.js <changeId> retry        <阶段号>
 *   node telemetry-log.js <changeId> impl-review  <轮次>
 *   node telemetry-log.js <changeId> circuit-break <阶段号>
 *
 * 用法（hook 自动调用）:
 *   node telemetry-log.js --session-start    # SessionStart hook：静默解绑（串台根治兜底）
 *   echo '<stdin-json>' | node telemetry-log.js --intervention   # UserPromptSubmit hook：记录人工干预
 *   node telemetry-log.js --ask-user    # PreToolUse(AskUserQuestion) hook：记录向用户提问时刻
 *   echo '<stdin-json>' | node telemetry-log.js --ask-done   # PostToolUse(AskUserQuestion) hook：记录答完时刻+等待时长
 *   changeId 取自 openspec/changes/.current-change 指针文件；缺失则丢弃事件，绝不串台。
 *
 * 主 agent 进入某个 change 工作前必须更新指针（保证 --intervention 落对地方）:
 *   echo "<changeId>" > openspec/changes/.current-change
 *   归档（/opsx:archive）后清除指针：node telemetry-log.js --session-start 或 rm openspec/changes/.current-change
 *
 * 迁移说明（2026-09-08，sh → Node，行为逐条等价）:
 *   - jq 依赖移除：JSON 转义与「按字符切前 200 字」（防 UTF-8 字节截断致末字乱码，2026-08-20 串台根治）
 *     全部由 JS 原生承担（JSON.stringify / String.slice），Windows 无 jq 也能跑
 *   - 指针缺失丢弃、遥测失败静默（绝不因采集问题打断工作流）等语义原样保留
 *   - 新增 --ask-user/--ask-done（官方文档实证 AskUserQuestion 触发 Pre/PostToolUse）：
 *     ask-done 落官方 duration_ms 字段（提问→用户答完的等待毫秒数，code.claude.com/docs/en/hooks）
 *   - hook 静默契约：遥测路径恒 exit 0 且 stdout 为空（UserPromptSubmit/SessionStart 的
 *     纯文本 stdout 会被注入上下文，exit 2 会阻断 prompt——两者都禁止）
 */

const fs = require('fs');
const path = require('path');

const PROJECT_DIR = path.resolve(process.env.CLAUDE_PROJECT_DIR || process.cwd());
const CHANGES_DIR = path.join(PROJECT_DIR, 'openspec', 'changes');
const CURRENT_FILE = path.join(CHANGES_DIR, '.current-change');

/** 追加一行 JSONL；失败静默（遥测永不打断工作流） */
function appendLine(file, obj) {
  try {
    fs.appendFileSync(file, JSON.stringify(obj) + '\n');
  } catch (_) { /* 磁盘/权限异常时丢弃事件 */ }
}

function ensureDir(dir) {
  try { fs.mkdirSync(dir, { recursive: true }); } catch (_) { }
}

// SessionStart hook：静默解绑，串台兜底（在解析 changeId 之前处理）
if (process.argv[2] === '--session-start') {
  try { fs.unlinkSync(CURRENT_FILE); } catch (_) { /* 不存在即达成 */ }
  process.exit(0);
}

/** 从指针文件读取当前 changeId（仅 --intervention 使用）；缺失返回空串 */
function readCurrentChange() {
  try {
    return fs.readFileSync(CURRENT_FILE, 'utf8').split('\n')[0].trim();
  } catch (_) { return ''; }
}

if (process.argv[2] === '--intervention') {
  const changeId = process.env.TELEMETRY_CHANGE_ID || readCurrentChange();
  // 指针缺失时丢弃事件，绝不靠 mtime 猜测写进别的 change
  if (!changeId) process.exit(0);
  let detail = '';
  try {
    // hook stdin 本身就是 JSON：取 prompt 前 200 字（按字符切，不切断 UTF-8）
    const parsed = JSON.parse(fs.readFileSync(0, 'utf8'));
    const prompt = parsed && parsed.prompt != null ? String(parsed.prompt) : '';
    detail = prompt.slice(0, 200);
  } catch (_) { /* 非法/空 JSON → 空串 */ }
  const outFile = path.join(CHANGES_DIR, changeId, 'telemetry.jsonl');
  ensureDir(path.dirname(outFile));
  appendLine(outFile, { ts: Math.floor(Date.now() / 1000), event: 'intervention', detail });
  process.exit(0);
}

if (process.argv[2] === '--ask-user' || process.argv[2] === '--ask-done') {
  // AskUserQuestion 的 PreToolUse/PostToolUse hook：自动采集提问与用户等待时长（官方 duration_ms）
  const done = process.argv[2] === '--ask-done';
  const changeId = process.env.TELEMETRY_CHANGE_ID || readCurrentChange();
  // 指针缺失时丢弃事件，绝不串台（与 intervention 同语义）
  if (!changeId) process.exit(0);
  let detail = '';
  let ms;
  try {
    const payload = JSON.parse(fs.readFileSync(0, 'utf8'));
    if (!done) {
      const qs = payload && payload.tool_input && Array.isArray(payload.tool_input.questions)
        ? payload.tool_input.questions : [];
      detail = qs.map((q) => (q && (q.header || q.question)) || '')
        .filter(Boolean).join('；').slice(0, 200);
    } else {
      if (payload && typeof payload.duration_ms === 'number') ms = payload.duration_ms;
      const resp = payload && payload.tool_response;
      detail = resp == null ? '' : JSON.stringify(resp).slice(0, 200);
    }
  } catch (_) { /* 非法/空 JSON → 空值 */ }
  const line = { ts: Math.floor(Date.now() / 1000), event: done ? 'ask-done' : 'ask-user', detail };
  if (ms !== undefined) line.ms = ms;
  const outFile = path.join(CHANGES_DIR, changeId, 'telemetry.jsonl');
  ensureDir(path.dirname(outFile));
  appendLine(outFile, line);
  process.exit(0);
}

// 正常事件：changeId 为首个位置参数，必填
const changeId = process.argv[2] || '';
if (!changeId) {
  console.error('telemetry-log: missing changeId. Usage: node telemetry-log.js <changeId> <event> [stage] [detail]');
  process.exit(1);
}
const event = process.argv[3] || 'unknown';
const stage = process.argv[4] || '';
const detail = process.argv[5] || '';

const outFile = path.join(CHANGES_DIR, changeId, 'telemetry.jsonl');
ensureDir(path.dirname(outFile));
appendLine(outFile, { ts: Math.floor(Date.now() / 1000), event, stage, detail });
process.exit(0);
