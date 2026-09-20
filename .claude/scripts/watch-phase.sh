#!/usr/bin/env bash
# phase 心跳检测（合并版）：对比指定 phase 目录下全部产物的组合指纹，累计连续无变化次数（stuck_count）
# + P6/P7 产物齐全性检测 + test-result.json 测试结果检测，按优先级单行输出。
# 覆盖 P6（tasks.md / implementation.md）与 P7（review.md / acceptance.md / git-diff-check.md /
# test-result.json / coverage-matrix.md）全周期——任一产物变化即视为 TM 在推进。
# 由主 agent（编排器）在确认「正在等待 tm-<phase>」后调用（/loop-durable 周期触发）。
#
# 用法：
#   bash .claude/scripts/watch-phase.sh <change-id> <phase>            # 检测（默认）
#   bash .claude/scripts/watch-phase.sh <change-id> <phase> reset      # 重置基线（删重建后调用）
#   例: bash .claude/scripts/watch-phase.sh testNprd phase1
#
# 输出（单行，优先级 FAIL > STUCK > MISSING > OK，供主 agent 解析）：
#   OK <phase> [grace|reset]
#                            —— 产物指纹有变化（推进中，stuck_count 清零）+ 产物齐全 + 测试通过
#                               （或 test-result.json 未生成）；grace=reset 后首轮启动宽限；reset=基线重置
#   STUCK <phase> <count> [missing-stale]
#                            —— 产物指纹连续 count 次无变化（TM 疑似卡死）；或同一缺失连续 ≥3 轮升级
#                              （missing-stale 标记）。count==1 → 催促；count>=2 → 删重建
#   FAIL <phase> <count> <reason>
#                            —— test-result.json 存在但测试未通过（优先级最高，短路 STUCK 累计）
#                              count 为独立计数，不消耗主 agent Retry Count；TM 应走 TM-11 回退
#   MISSING <phase> <count> <list>
#                            —— 缺失 P6/P7 产物文件（list 为缺失清单，相对 phase 目录）
#                              count==1 → 催促补产物；count==2 → 建议人工核对；
#                              count>=3 → 脚本升级为 STUCK missing-stale（纳入删重建链路）
#   ERROR <msg>              —— 参数缺失 / phase 目录或 tasks.md 不存在
#
# 产物清单（依据 WORKFLOW-CODE.md M8 步骤0）：
#   per-task：implementation.md（P6）、review.md（P7 步骤0）
#   phase 级：acceptance.md / coverage-matrix.md / git-diff-check.md / test-result.json（test-result.json 不存在算正常推进）
#
# 快照：openspec/changes/<change-id>/.phase-watch
#   字段（TSV）：phase \t hash \t stuck_count \t missing_count \t fail_count \t grace \t last_missing
#   - stuck_count：指纹停滞累计（仅非 FAIL 态累计），count>=2 触发删重建
#   - missing_count：同一缺失集合连续轮次累计，>=3 升级 STUCK
#   - fail_count：FAIL 冻结态独立计数，不消耗主 agent Retry Count
#   - grace：reset 后置 1，下一轮跳过 STUCK 累计（新 TM 启动宽限，防震荡）
#   - last_missing：上次缺失清单，用于判断缺失集合是否变化（变化则 missing_count 重置为 1）

set -euo pipefail

CHANGE_ID="${1:-}"
PHASE="${2:-}"
ACTION="${3:-check}"

if [ -z "$CHANGE_ID" ] || [ -z "$PHASE" ]; then
  echo "ERROR usage: watch-phase.sh <change-id> <phase> [reset]"
  exit 0
fi

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
PHASE_DIR="$ROOT/openspec/changes/$CHANGE_ID/$PHASE"
TASKS_FILE="$PHASE_DIR/tasks.md"
SNAP_FILE="$ROOT/openspec/changes/$CHANGE_ID/.phase-watch"

# phase 目录与 tasks.md 必须存在（tasks.md 是 P6 起点，缺失说明 phase 未初始化）
if [ ! -d "$PHASE_DIR" ]; then
  echo "ERROR phase dir not found: $PHASE_DIR"
  exit 0
fi
if [ ! -f "$TASKS_FILE" ]; then
  echo "ERROR tasks.md not found: $TASKS_FILE"
  exit 0
fi

# 收集 phase 目录下全部 .md / .json 产物（含 task 子目录），按路径排序保证顺序稳定
files=$(find "$PHASE_DIR" -type f \( -name '*.md' -o -name '*.json' \) | sort)
if [ -z "$files" ]; then
  echo "ERROR no fingerprint sources under: $PHASE_DIR"
  exit 0
fi

# 组合指纹：逐文件 md5 拼接后再 md5（任一产物变化 → 总指纹变化）
# 兼容 macOS（md5）/ Linux（md5sum）
compute_hash() {
  local combined=""
  if command -v md5 >/dev/null 2>&1; then
    while IFS= read -r f; do
      combined+=$(md5 -q "$f")
    done <<< "$files"
    printf '%s' "$combined" | md5 -q
  else
    while IFS= read -r f; do
      combined+=$(md5sum "$f" | awk '{print $1}')
    done <<< "$files"
    printf '%s' "$combined" | md5sum | awk '{print $1}'
  fi
}
hash=$(compute_hash)

# ===== FAIL 检测：test-result.json 红绿 =====
# 输出 "FAIL <reason>" 或空（通过 / 不存在 / 解析失败 / 无 python3）
check_test_result() {
  local tr="$PHASE_DIR/test-result.json"
  [ -f "$tr" ] || return 0
  command -v python3 >/dev/null 2>&1 || return 0
  local result
  result=$(python3 - "$tr" <<'PY' || echo "PARSE_ERROR"
import json, sys
try:
    with open(sys.argv[1], encoding='utf-8') as f:
        d = json.load(f)
except Exception:
    print("PARSE_ERROR"); sys.exit(0)

# 显式失败信号优先
for fk in ('failed', 'failures', 'fail', 'error', 'errors'):
    if fk in d:
        try:
            if int(d[fk]) > 0:
                print(f"FAIL failed={d[fk]}")
                sys.exit(0)
        except (ValueError, TypeError):
            pass

# 显式通过信号
for pk in ('passed', 'pass', 'success', 'status'):
    if pk in d:
        v = d[pk]
        if pk == 'status':
            if str(v).lower() in ('fail', 'failed', 'error'):
                print(f"FAIL status={v}"); sys.exit(0)
            if str(v).lower() in ('pass', 'passed', 'success', 'ok'):
                print("OK_ALL"); sys.exit(0)
        elif str(v).lower() in ('true', 'pass', 'passed', 'success', 'ok'):
            print("OK_ALL"); sys.exit(0)

print("OK_ALL")
PY
)
  case "$result" in
    FAIL\ *) echo "$result" ;;
  esac
}

# ===== MISSING 检测：缺失产物清单（空格分隔），空则无缺失 =====
check_missing() {
  local task_ids
  task_ids=$(grep -oE 'T[0-9]{3}' "$TASKS_FILE" | sort -u)
  local miss=()
  # per-task 产物（已创建的 task 目录才检）
  if [ -n "$task_ids" ]; then
    for tid in $task_ids; do
      task_dir="$PHASE_DIR/$tid"
      if [ -d "$task_dir" ]; then
        [ -f "$task_dir/implementation.md" ] || miss+=("$tid/implementation.md")
        [ -f "$task_dir/review.md" ] || miss+=("$tid/review.md")
      fi
    done
  fi
  # phase 级产物（test-result.json 单独处理：不存在算正常）
  for f in acceptance.md coverage-matrix.md git-diff-check.md; do
    [ -f "$PHASE_DIR/$f" ] || miss+=("$f")
  done
  printf '%s' "${miss[*]}"
}

# 写快照：phase hash stuck missing fail grace last_missing
write_snap() {
  printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\n' \
    "$PHASE" "$hash" "${1:-0}" "${2:-0}" "${3:-0}" "${4:-0}" "${5:-}" > "$SNAP_FILE"
}

# reset 子命令：删重建后重建基线 + 启动宽限 grace=1，不比对、不报警
if [ "$ACTION" = "reset" ]; then
  write_snap 0 0 0 1 ""
  echo "OK $PHASE reset"
  exit 0
fi

# ===== 读快照 =====
prev_phase=""
prev_hash=""
prev_stuck=0
prev_missing=0
prev_fail=0
prev_grace=0
prev_last_missing=""
if [ -f "$SNAP_FILE" ]; then
  prev_phase=$(cut -f1 "$SNAP_FILE")
  prev_hash=$(cut -f2 "$SNAP_FILE")
  prev_stuck=$(cut -f3 "$SNAP_FILE");   [ -z "$prev_stuck" ]   && prev_stuck=0
  prev_missing=$(cut -f4 "$SNAP_FILE"); [ -z "$prev_missing" ] && prev_missing=0
  prev_fail=$(cut -f5 "$SNAP_FILE");    [ -z "$prev_fail" ]    && prev_fail=0
  prev_grace=$(cut -f6 "$SNAP_FILE");   [ -z "$prev_grace" ]   && prev_grace=0
  prev_last_missing=$(cut -f7 "$SNAP_FILE")
fi

# 切换 phase：重置全部计数（不沿用上个 phase 的状态）
if [ "$PHASE" != "$prev_phase" ]; then
  prev_hash=""
  prev_stuck=0
  prev_missing=0
  prev_fail=0
  prev_grace=0
  prev_last_missing=""
fi

# ===== 1. FAIL 检测（优先级最高，短路 STUCK 累计）=====
# test-result.json 失败 = TM 在处理失败（活着，走 TM-11 回退），不是卡死：
#   - 不计 stuck_count（即使指纹不变）
#   - 独立 fail_count 计数，不消耗主 agent Retry Count
#   - 主 agent 不催促、不删重建
# 注：FAIL 优先于 grace——reset 后若遗留 FAIL，仍如实报 FAIL（reset 只发生在非 FAIL 态，
# 正常不触发，此处为防御性：真实状态优先于启动宽限）
fail_reason=$(check_test_result)
if [ -n "$fail_reason" ]; then
  fail_count=$((prev_fail + 1))
  reason=${fail_reason#FAIL }
  write_snap 0 0 "$fail_count" 0 ""
  echo "FAIL $PHASE $fail_count $reason"
  exit 0
fi

# ===== 2. 启动宽限（grace=1，reset 后首轮）=====
# 新 TM 刚 spawn，首轮心跳前可能还没写出新产物 → 跳过 STUCK 累计，输出 OK grace，清 grace
# 防止「reset → 首轮指纹不变 → 立即 STUCK → 再次删重建」震荡
# （grace 在 FAIL 之后：FAIL 是真实状态不豁免，grace 只豁免 STUCK）
if [ "$prev_grace" = "1" ]; then
  write_snap 0 0 0 0 ""
  echo "OK $PHASE grace"
  exit 0
fi

# ===== 3. STUCK 检测（指纹不变，且非 FAIL）=====
if [ "$hash" = "$prev_hash" ]; then
  stuck_count=$((prev_stuck + 1))
  write_snap "$stuck_count" 0 0 0 ""
  echo "STUCK $PHASE $stuck_count"
  exit 0
fi

# ===== 指纹变化：清 stuck_count，进入 MISSING 检测 =====
missing_list=$(check_missing)
if [ -n "$missing_list" ]; then
  # 缺失集合与上次相同 → 累计；变化 → 重置为 1
  if [ "$missing_list" = "$prev_last_missing" ]; then
    missing_count=$((prev_missing + 1))
  else
    missing_count=1
  fi

  # 升级：同一缺失连续 ≥3 轮 → 升级 STUCK missing-stale（纳入删重建链路，避免永久人工核对）
  if [ "$missing_count" -ge 3 ]; then
    write_snap "$missing_count" 0 0 0 "$missing_list"
    echo "STUCK $PHASE $missing_count missing-stale"
    exit 0
  fi

  write_snap 0 "$missing_count" 0 0 "$missing_list"
  echo "MISSING $PHASE $missing_count $missing_list"
  exit 0
fi

# ===== 3. 全正常 =====
write_snap 0 0 0 0 ""
echo "OK $PHASE"
