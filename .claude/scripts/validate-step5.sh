#!/bin/bash
# validate-step5.sh — L1 API 产出校验
# 验证 API 文档结构完整性（Param 已延迟到方案设计/编码阶段，此处不校验）
# 用法：在目标项目根目录执行 bash /path/to/validate-step5.sh

checked=0
passed=0
failed=0

for api_file in knowledge/domain/api/*-api.md; do
  [ -f "$api_file" ] || continue
  checked=$((checked + 1))
  errors=""

  # 检查1：文件非空
  lines=$(wc -l < "$api_file")
  if [ "$lines" -lt 5 ]; then
    errors="${errors}  ✗ 文件过短(${lines}行，最少5行);"
  fi

  # 检查2：包含产品功能 section
  if ! grep -q '## 产品功能' "$api_file"; then
    errors="${errors}  ✗ 缺少'## 产品功能'section;"
  fi

  # 检查3：包含业务规则与约束
  if ! grep -q '业务规则与约束' "$api_file"; then
    errors="${errors}  ✗ 缺少'业务规则与约束';"
  fi

  # 检查4：包含影响范围
  if ! grep -q '影响范围' "$api_file"; then
    errors="${errors}  ✗ 缺少'影响范围';"
  fi

  if [ -z "$errors" ]; then
    echo "✓ $(basename "$api_file") (${lines}行)"
    passed=$((passed + 1))
  else
    echo "✗ $(basename "$api_file"):${errors}"
    failed=$((failed + 1))
  fi
done

echo ""
echo "L1 API 文件: ${passed}/${checked} 通过"

if [ $failed -gt 0 ]; then
  echo "⚠ 有 ${failed} 个 API 文件结构不完整"
  exit 1
fi
