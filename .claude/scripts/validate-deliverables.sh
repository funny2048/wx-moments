#!/bin/bash
# validate-deliverables.sh — 交付物完整性检查
# 用法：在目标项目根目录执行 bash /path/to/validate-deliverables.sh

PASS=0
FAIL=0

check() {
  if $@ > /dev/null 2>&1; then
    PASS=$((PASS + 1))
    return 0
  else
    FAIL=$((FAIL + 1))
    return 1
  fi
}

# 目录结构检查
echo "=== 目录结构检查 ==="
for d in knowledge/indexs knowledge/domain knowledge/domain/api knowledge/domain/api/param knowledge/domain/service knowledge/tech knowledge/lessons; do
  if [ -d "$d" ]; then echo "✓ $d/"; else echo "✗ $d/ 缺失"; FAIL=$((FAIL+1)); fi
done

# 必需文件存在性检查
echo ""
echo "=== 核心文件检查 ==="
for f in knowledge/indexs/domain-index.md knowledge/indexs/tech-index.md knowledge/indexs/lessons-index.md knowledge/tech/project-structure.md knowledge/tech/database.md knowledge/domain/service-hot.md; do
  if [ -f "$f" ] && [ -s "$f" ]; then
    lines=$(wc -l < "$f")
    echo "✓ $f ($lines 行)"
  elif [ -f "$f" ]; then
    echo "✗ $f 存在但为空"
    FAIL=$((FAIL+1))
  else
    echo "✗ $f 不存在"
    FAIL=$((FAIL+1))
  fi
done

# Lessons 模板文件检查
echo ""
echo "=== Lessons 模板检查 ==="
for f in knowledge/lessons/conventions.md knowledge/lessons/dev-session.md knowledge/lessons/test-bugs.md knowledge/lessons/release-risk.md knowledge/lessons/incident-postmortem.md; do
  if [ -f "$f" ]; then
    echo "✓ $f"
  else
    echo "✗ $f 不存在"
    FAIL=$((FAIL+1))
  fi
done

# L0-L1 一致性检查
echo ""
echo "=== L0→L1 一致性检查 ==="
grep -oE 'api/[a-z0-9-]+-api\.md' knowledge/indexs/domain-index.md 2>/dev/null | sort -u | while read f; do
  if [ -f "knowledge/domain/$f" ] && [ -s "knowledge/domain/$f" ]; then
    echo "✓ knowledge/domain/$f"
  else
    echo "✗ knowledge/domain/$f (domain-index.md 引用但文件缺失或为空)"
  fi
done

# L0 路由表结构检查
echo ""
echo "=== L0 路由表结构检查 ==="
if grep -q '^## .*([a-z0-9-]\+)' knowledge/indexs/domain-index.md 2>/dev/null; then
  for required in "职责边界" "匹配关键词" "不归属本域" "领域依赖" "功能索引"; do
    if grep -q "$required" knowledge/indexs/domain-index.md; then
      echo "✓ domain-index.md 包含 $required"
    else
      echo "✗ domain-index.md 缺少 $required"
      FAIL=$((FAIL+1))
    fi
  done
  if grep -qE '功能模块.*匹配关键词.*API 文件.*Service 文件.*主入口方法.*风险等级' knowledge/indexs/domain-index.md; then
    echo "✓ 功能索引表头完整"
  else
    echo "✗ 功能索引表头不完整（需包含：功能模块 | 匹配关键词 | API 文件 | Service 文件 | 主入口方法 | 风险等级）"
    FAIL=$((FAIL+1))
  fi
else
  echo "⚠ domain-index.md 尚未发现真实领域 section（初始化模板允许，抽取完成后必须补齐）"
fi

# L0→L2 Service 路径一致性检查
echo ""
echo "=== L0→L2 Service 路径一致性检查 ==="
grep -oE 'service/[a-z0-9-]+-service\.md' knowledge/indexs/domain-index.md 2>/dev/null | sort -u | while read f; do
  if [ -f "knowledge/domain/$f" ] && [ -s "knowledge/domain/$f" ]; then
    echo "✓ knowledge/domain/$f"
  else
    echo "✗ knowledge/domain/$f (domain-index.md 引用但文件缺失或为空)"
  fi
done

# L1-param 一致性检查（INFO：param 在方案设计/编码阶段按需提取，init 阶段不强制）
echo ""
echo "=== L1→param 一致性检查（非阻塞） ==="
total=0
pass=0
skip=0
for api_file in knowledge/domain/api/*-api.md; do
  [ -f "$api_file" ] || continue
  total=$((total + 1))
  module_name=$(basename "$api_file" -api.md)
  param_file="knowledge/domain/api/param/${module_name}-api-param.md"
  if [ -f "$param_file" ] && [ -s "$param_file" ]; then
    pass=$((pass + 1))
    echo "✓ $param_file"
  else
    skip=$((skip + 1))
    echo "ℹ $param_file 未生成（方案设计/编码阶段按需提取）"
  fi
done
echo "param 文件: ${pass}/${total} 已生成，${skip} 待后续阶段"

# L0-L2 一致性检查
echo ""
echo "=== L0→L2 一致性检查 ==="
grep '^## ' knowledge/indexs/domain-index.md 2>/dev/null | while read line; do
  domain=$(echo "$line" | sed 's/## //' | grep -oE '\([A-Za-z0-9-]+\)' | tr -d '()' | tr '[:upper:]' '[:lower:]')
  if [ -z "$domain" ]; then
    domain=$(echo "$line" | sed 's/## //' | tr '[:upper:]' '[:lower:]' | tr ' ' '-')
  fi
  f="knowledge/domain/service/${domain}-service.md"
  if [ -f "$f" ] && [ -s "$f" ]; then
    echo "✓ $f"
  else
    if grep -A30 "^$line" knowledge/indexs/domain-index.md | grep -q '|.*|.*|.*| - |'; then
      echo "✓ $(echo "$line" | sed 's/## //') 为兜底域，跳过 Service 文件检查"
    else
      echo "✗ $f (domain-index.md 中有 $(echo "$line" | sed 's/## //') 但 Service 文件缺失)"
    fi
  fi
done

# database.md 格式检查
echo ""
echo "=== database.md 格式检查 ==="
if [ -f "knowledge/tech/database.md" ]; then
  has_yaml=$(grep -c 'instances:' "knowledge/tech/database.md" || true)
  if [ "$has_yaml" -gt 0 ]; then
    echo "✓ database.md 包含 YAML instances 结构"
  else
    echo "✗ database.md 缺少 YAML instances 结构"
    FAIL=$((FAIL+1))
  fi
fi

# L1 内容完整性抽查
echo ""
echo "=== L1 内容完整性抽查 ==="
for f in knowledge/domain/api/*-api.md; do
  [ -f "$f" ] || continue
  name=$(basename "$f")
  has_list=$(grep -c '接口列表' "$f" || true)
  has_func=$(grep -c '产品功能' "$f" || true)
  has_impact=$(grep -c '影响范围' "$f" || true)
  if [ "$has_list" -gt 0 ] && [ "$has_func" -gt 0 ] && [ "$has_impact" -gt 0 ]; then
    echo "✓ $name"
  else
    echo "✗ $name (缺: $([ $has_list -eq 0 ] && echo '接口列表 ')$([ $has_func -eq 0 ] && echo '产品功能 ')$([ $has_impact -eq 0 ] && echo '影响范围'))"
  fi
done

# L2 Service 文件内容抽查
echo ""
echo "=== L2 内容完整性抽查 ==="
for f in knowledge/domain/service/*-service.md; do
  [ -f "$f" ] || continue
  name=$(basename "$f")
  has_contract=$(grep -c '前置约束\|核心不变量\|后置效应' "$f" || true)
  if [ "$has_contract" -gt 0 ]; then
    echo "✓ $name"
  else
    echo "⚠ $name (业务契约内容较少，可能需补充)"
  fi
done

# service-hot 内容结构检查
echo ""
echo "=== service-hot 内容结构检查 ==="
if [ -f "knowledge/domain/service-hot.md" ]; then
  for required in "跨域热点" "高风险规则" "运行时热点" "设计偏差"; do
    if grep -q "$required" knowledge/domain/service-hot.md; then
      echo "✓ service-hot.md 包含 $required"
    else
      echo "✗ service-hot.md 缺少 $required"
      FAIL=$((FAIL+1))
    fi
  done
  if grep -qE 'ID.*严重度.*类型|ID.*严重度.*领域|ID.*严重度.*声明关系' knowledge/domain/service-hot.md; then
    echo "✓ service-hot.md 包含结构化热点表"
  else
    echo "✗ service-hot.md 缺少结构化热点表"
    FAIL=$((FAIL+1))
  fi
else
  echo "✗ knowledge/domain/service-hot.md 不存在"
  FAIL=$((FAIL+1))
fi

echo ""
echo "==============================================="
echo "检查完成。如有 ✗ 项，重新执行对应步骤修复。"
echo "==============================================="
