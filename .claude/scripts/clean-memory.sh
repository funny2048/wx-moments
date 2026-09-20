#!/bin/bash
# 清除项目记忆和会话文件
# 用法: bash scripts/clean-memory.sh

# 动态获取项目根目录（脚本在 scripts/ 下）
PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# Claude Code 的项目记忆路径规则：/ 替换为 -，去掉开头的 /
# Claude Code 路径规则：去掉开头 /，/ 和 _ 都替换为 -
MEMORY_DIR="$HOME/.claude/projects/-$(echo "$PROJECT_DIR" | sed 's|^/||;s|/|-|g;s/_/-/g')"
REMEMBER_DIR="${PROJECT_DIR}/.remember"

echo "=== 清除项目记忆 ==="

# 1. 清除 .claude/projects 下的会话文件
if [ -d "$MEMORY_DIR" ]; then
    count=$(find "$MEMORY_DIR" -type f -name "*.jsonl" | wc -l | tr -d ' ')
    size=$(du -sh "$MEMORY_DIR" | cut -f1)
    echo "[清除] $MEMORY_DIR ($count 个会话文件, $size)"
    find "$MEMORY_DIR" -type f -name "*.jsonl" -delete
    find "$MEMORY_DIR" -mindepth 1 -maxdepth 1 -type d -exec rm -rf {} \;
    echo "[完成] 会话文件已清除"
else
    echo "[跳过] $MEMORY_DIR 不存在"
fi

# 2. 清除 .remember 目录（重建空的 hook-errors.log）
if [ -d "$REMEMBER_DIR" ]; then
    echo "[清除] $REMEMBER_DIR"
    rm -rf "$REMEMBER_DIR"
    mkdir -p "${REMEMBER_DIR}/logs"
    touch "${REMEMBER_DIR}/logs/hook-errors.log"
    echo "[完成] .remember 目录已清除（已重建 logs/hook-errors.log）"
else
    echo "[跳过] $REMEMBER_DIR 不存在"
fi

echo "=== 清除完成 ==="
