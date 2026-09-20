#!/usr/bin/env bash
# PostCompact hook: persist compact summary and point Claude to the latest snapshot.
set -euo pipefail

input=$(cat)

session_id=$(printf '%s' "$input" | jq -r '.session_id // "unknown-session"')
transcript_path=$(printf '%s' "$input" | jq -r '.transcript_path // ""')
cwd=$(printf '%s' "$input" | jq -r '.cwd // env.CLAUDE_PROJECT_DIR // "."')
trigger=$(printf '%s' "$input" | jq -r '.trigger // "unknown"')
compact_summary=$(printf '%s' "$input" | jq -r '.compact_summary // ""')

snapshot_dir="$cwd/.remember/context-snapshots/$session_id"
mkdir -p "$snapshot_dir"

timestamp=$(date -u +"%Y%m%dT%H%M%SZ")
snapshot_path="$snapshot_dir/${timestamp}-post-compact-${trigger}.md"
latest_pre_compact=""

if [[ -d "$snapshot_dir" ]]; then
    latest_pre_compact=$(find "$snapshot_dir" -type f -name '*pre-compact*.md' -print | sort | tail -n 1)
fi

{
    echo "# PostCompact Restore"
    echo
    echo "- Session: $session_id"
    echo "- Created: $timestamp"
    echo "- Trigger: $trigger"
    echo "- Transcript: $transcript_path"
    if [[ -n "$latest_pre_compact" ]]; then
        echo "- Latest pre-compact snapshot: $latest_pre_compact"
    fi
    echo
    echo "## Compact Summary"
    echo
    if [[ -n "$compact_summary" ]]; then
        printf '%s\n' "$compact_summary"
    else
        echo "No compact summary was provided by Claude Code."
    fi
    echo
    echo "## Restore Protocol"
    echo
    echo "Claude must restore the active goal, constraints, pending work, validation state, and next steps before resuming implementation. Avoid reloading large historical context unless the snapshot is insufficient."
} > "$snapshot_path"

state_dir="$cwd/.remember/context-watermarks"
mkdir -p "$state_dir"
jq -n \
    --arg session_id "$session_id" \
    --arg timestamp "$timestamp" \
    --arg trigger "$trigger" \
    --arg snapshot_path "$snapshot_path" \
    --arg latest_pre_compact "$latest_pre_compact" \
    '{
      session_id: $session_id,
      updated_at: $timestamp,
      compact_trigger: $trigger,
      post_compact_snapshot_path: $snapshot_path,
      latest_pre_compact_snapshot_path: $latest_pre_compact
    }' > "$state_dir/$session_id-post-compact.json"

printf 'PostCompact restore note saved: %s\n' "$snapshot_path" >&2
echo '{}'
