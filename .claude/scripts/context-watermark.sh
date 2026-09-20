#!/usr/bin/env bash
# Context watermark hook (PostToolBatch)
# Estimates transcript size and injects context-window reminders at 60/70/80/90%.
set -euo pipefail

input=$(cat)

session_id=$(printf '%s' "$input" | jq -r '.session_id // "unknown-session"')
transcript_path=$(printf '%s' "$input" | jq -r '.transcript_path // ""')
cwd=$(printf '%s' "$input" | jq -r '.cwd // env.CLAUDE_PROJECT_DIR // "."')

if [[ -z "$transcript_path" || ! -f "$transcript_path" ]]; then
    echo '{}'
    exit 0
fi

context_window_tokens="${CLAUDE_CONTEXT_WINDOW_TOKENS:-200000}"
if ! [[ "$context_window_tokens" =~ ^[0-9]+$ ]] || [[ "$context_window_tokens" -le 0 ]]; then
    context_window_tokens=200000
fi

remember_dir="$cwd/.remember"
state_dir="$remember_dir/context-watermarks"
snapshot_dir="$remember_dir/context-snapshots/$session_id"
mkdir -p "$state_dir" "$snapshot_dir"

state_file="$state_dir/$session_id.json"
timestamp=$(date -u +"%Y%m%dT%H%M%SZ")

# Rough token estimate: English/code averages near 4 chars/token. This is a guardrail,
# not a billing or exact context-window measurement.
char_count=$(wc -c < "$transcript_path" | tr -d ' ')
estimated_tokens=$(( (char_count + 3) / 4 ))
percent=$(( estimated_tokens * 100 / context_window_tokens ))

if [[ "$percent" -lt 60 ]]; then
    echo '{}'
    exit 0
fi

last_threshold=0
if [[ -f "$state_file" ]]; then
    last_threshold=$(jq -r '.last_threshold // 0' "$state_file" 2>/dev/null || echo 0)
fi

threshold=0
if [[ "$percent" -ge 90 ]]; then
    threshold=90
elif [[ "$percent" -ge 80 ]]; then
    threshold=80
elif [[ "$percent" -ge 70 ]]; then
    threshold=70
elif [[ "$percent" -ge 60 ]]; then
    threshold=60
fi

if [[ "$threshold" -le "$last_threshold" ]]; then
    echo '{}'
    exit 0
fi

snapshot_path=""
if [[ "$threshold" -ge 70 ]]; then
    snapshot_path="$snapshot_dir/${timestamp}-p${threshold}.md"
    {
        echo "# Context Snapshot p${threshold}"
        echo
        echo "- Session: $session_id"
        echo "- Created: $timestamp"
        echo "- Trigger: context-watermark"
        echo "- Estimated tokens: $estimated_tokens"
        echo "- Estimated usage: ${percent}%"
        echo "- Transcript: $transcript_path"
        echo
        echo "## Mechanical Summary"
        echo
        echo "This file was created by the context watermark hook. Claude must update this snapshot with semantic task state before continuing complex work."
        echo
        echo "## Recent Transcript Tail"
        echo
        echo '```jsonl'
        tail -n 40 "$transcript_path"
        echo '```'
    } > "$snapshot_path"
fi

jq -n \
    --arg session_id "$session_id" \
    --arg timestamp "$timestamp" \
    --argjson threshold "$threshold" \
    --argjson percent "$percent" \
    --argjson estimated_tokens "$estimated_tokens" \
    --arg snapshot_path "$snapshot_path" \
    '{
      session_id: $session_id,
      updated_at: $timestamp,
      last_threshold: $threshold,
      estimated_usage_percent: $percent,
      estimated_tokens: $estimated_tokens,
      last_snapshot_path: $snapshot_path
    }' > "$state_file"

if [[ "$threshold" -eq 60 ]]; then
    message="上下文窗口估算已达到 ${percent}%（阈值 60%）。必须提醒用户当前上下文进入高占用阶段，收敛任务范围，列出剩余工作、风险、验证状态，以及是否建议 /compact。"
else
    message="上下文窗口估算已达到 ${percent}%（阈值 ${threshold}%）。已创建机械快照：${snapshot_path}。在继续复杂工作前，必须把该文件补充为语义化 context snapshot，包含当前目标、约束、已完成工作、已修改文件、关键决策、未完成任务、测试状态、阻塞点和 compact 后恢复信息。"
fi

jq -n \
    --arg message "$message" \
    '{
      hookSpecificOutput: {
        hookEventName: "PostToolBatch",
        additionalContext: $message
      }
    }'
