#!/usr/bin/env bash
# PreCompact hook: save a pre-compact mechanical snapshot.
set -euo pipefail

input=$(cat)

session_id=$(printf '%s' "$input" | jq -r '.session_id // "unknown-session"')
transcript_path=$(printf '%s' "$input" | jq -r '.transcript_path // ""')
cwd=$(printf '%s' "$input" | jq -r '.cwd // env.CLAUDE_PROJECT_DIR // "."')
trigger=$(printf '%s' "$input" | jq -r '.trigger // "unknown"')
custom_instructions=$(printf '%s' "$input" | jq -r '.custom_instructions // ""')

snapshot_dir="$cwd/.remember/context-snapshots/$session_id"
mkdir -p "$snapshot_dir"

timestamp=$(date -u +"%Y%m%dT%H%M%SZ")
snapshot_path="$snapshot_dir/${timestamp}-pre-compact-${trigger}.md"

{
    echo "# PreCompact Snapshot"
    echo
    echo "- Session: $session_id"
    echo "- Created: $timestamp"
    echo "- Trigger: $trigger"
    echo "- Transcript: $transcript_path"
    echo
    echo "## Custom Compact Instructions"
    echo
    if [[ -n "$custom_instructions" ]]; then
        printf '%s\n' "$custom_instructions"
    else
        echo "None"
    fi
    echo
    echo "## Required Semantic State"
    echo
    echo "Claude must preserve the current goal, constraints, completed work, modified files, pending tasks, validation state, blockers, and next steps across compact."
    echo
    if [[ -n "$transcript_path" && -f "$transcript_path" ]]; then
        echo "## Recent Transcript Tail"
        echo
        echo '```jsonl'
        tail -n 60 "$transcript_path"
        echo '```'
    fi
} > "$snapshot_path"

state_dir="$cwd/.remember/context-watermarks"
mkdir -p "$state_dir"
jq -n \
    --arg session_id "$session_id" \
    --arg timestamp "$timestamp" \
    --arg trigger "$trigger" \
    --arg snapshot_path "$snapshot_path" \
    '{
      session_id: $session_id,
      updated_at: $timestamp,
      compact_trigger: $trigger,
      pre_compact_snapshot_path: $snapshot_path
    }' > "$state_dir/$session_id-pre-compact.json"

printf 'PreCompact snapshot saved: %s\n' "$snapshot_path" >&2
echo '{}'
