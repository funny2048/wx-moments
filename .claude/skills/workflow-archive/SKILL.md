---
name: workflow-archive
description: daily 工作流阶段八验收归档编排器。收集变更统计、委派 workflow-reviewer(mode:archive) 核验归档清单、按结论更新 workflow-state、输出变更摘要与 PR 建议。当用户要求"归档"、"验收归档"、"archive change"，或工作流推进到阶段八时触发。
---

# 验收归档编排（daily 工作流 · 阶段八）

## 定位与分工

本 skill 是**编排器**，不是执行者：

- **主 agent 执行**（本 skill 内）：变更统计收集、workflow-state 更新、变更摘要与 PR 提示——数据必须来自实际 git 命令与文件，禁止猜测
- **subagent 执行**（委派）：归档清单核验 + `archive-report.md` 生成，委派 `workflow-reviewer`（`mode: archive`）——满足统一状态规则"阶段产物必须由 subagent 生成"

## 前置检查（不满足则终止并告知用户）

1. 标准模式：`workflow-state.md` 中 `stage-7-dual-review` 已标记 `[x]`；轻量模式（`mode: lightweight`）：步骤2 完成（reviewer 审查通过，state 记审查产物路径或通过标注）
2. 标准模式：`test-mapping.md` 存在且用例缺口为 0（阶段六红绿循环出口条件）；轻量模式豁免（测试义务由步骤2 DoD JUnit 承担，以 `mvn test` 结果为准）
3. `mvn test` 通过（从阶段六验证结果确认，不重复全量跑；如需复核只跑入口级测试类）

## 执行步骤

### S1 收集变更统计与效能度量（主 agent，只读 git + telemetry.jsonl + tasks.md）

```bash
git diff --numstat HEAD | awk '{added+=$1; deleted+=$2} END {print "已跟踪新增:", added, "已跟踪删除:", deleted}'
git diff --name-only HEAD | wc -l
git ls-files --others --exclude-standard | wc -l
```

统计口径：改动文件数 = 已跟踪变更数 + 未跟踪新文件数。

效能度量采集（telemetry.jsonl 存在时执行，缺失则各项记"未采集"）：

```bash
python3 - << 'PYEOF'
import json, pathlib
tl = pathlib.Path("openspec/changes/{变更ID}/telemetry.jsonl")
events = [json.loads(l) for l in tl.read_text(encoding="utf-8").splitlines() if l.strip()] if tl.exists() else []
starts, ends, retry, cb, itv = {}, {}, 0, 0, []
for e in events:
    ev = e.get("event", "")
    if ev == "stage-start": starts[e.get("stage","")] = e["ts"]
    elif ev == "stage-end": ends[e.get("stage","")] = e["ts"]
    elif ev == "retry": retry += 1
    elif ev == "circuit-break": cb += 1
    elif ev == "intervention": itv.append(e["ts"])
names = ["需求接入与领域匹配","需求探索澄清","方案设计与对抗验证","测试用例设计与对抗",
         "用户评审与冻结","编码+测试红绿循环","并行双评","验收归档"]
for s in sorted(set(starts) | set(ends), key=int):
    dur = round((ends[s] - starts[s]) / 60, 1) if s in starts and s in ends else "未闭合"
    lo, hi = starts.get(s, 0), ends.get(s, float("inf"))
    n = sum(1 for t in itv if lo <= t < hi)  # 半开区间：阶段衔接秒的干预只归下一个阶段，防重复计数
    print(f"阶段{s} {names[int(s)-1] if s.isdigit() and 0 < int(s) <= 8 else s} | {dur} min | 干预 {n} 次")
print(f"retry={retry} circuit-break={cb} 总干预={len(itv)}")
PYEOF
```

AI 任务完成率：从 `tasks.md` 统计总任务数与完成标记（✅ / `[x]`），人工介入重构任务数取 tasks.md 中人工标注（无标注则为 0）；公式 `(总任务数 - 人工介入重构数) / 总任务数`。

### S2 委派核验（subagent）

派 general-purpose subagent，读取 `.claude/agents/workflow-reviewer.md` 执行：

```
任务参数:
  change-id: {变更ID}
  mode: archive
  metrics: S1 效能度量产出（逐行粘贴，作为报告"效能度量"章节唯一数据源）
```

产出 `openspec/changes/{变更ID}/archive-report.md`。subagent 失败则阶段阻塞（禁止主 agent 代写报告）。

### S3 处理结论（主 agent）

读 archive-report.md **结论行**（只读结论，不整篇读回主 context）：

- **PASS** → 更新 `workflow-state.md`：`stage-8-archive` 标记 `[x]`，Artifacts 记录 `archive-report` 路径，`Current Stage` 置为 done，进入 S4
- **FAIL** → 按报告问题类型回退：代码问题回阶段六、设计问题回阶段三、测试问题回阶段六（红绿）或阶段四（用例语义，增补后重过阶段五评审）；将被回退阶段（及下游已执行阶段）`[x]` 回拨为 `[ ]`，旧产物重命名 `{name}.redo.md`；终止本 skill 并告知用户回退原因

### S4 变更摘要 + PR 提示（主 agent）

汇总输出（对话内呈现，不落盘）：

- change 产物索引：prd / domain / pipeline / explore / design / api / tasks / sql / design-review / test-cases / test-case-review / review-pack / test-mapping / code-review / arch-review（≤10 文件规模豁免时以 workflow-state 的 skipped 标注代替）/ archive-report
- S1 统计数据与效能度量（阶段耗时 / 人工干预 / 代码资产 / AI 任务完成率 / 回退与熔断）
- PR 建议：建议分支名 `feature/{变更ID}`、PR 描述引用 design.md 与 archive-report.md

## 边界（不可违反）

- **git 写操作全部禁止**（merge / push / rebase / reset / add / commit）——归用户手动执行
- FAIL 不自动重试归档；回退后由工作流重走对应阶段
- 统计数据必须有实际命令来源
- 效能度量缺失数据（无 telemetry.jsonl / 阶段未闭合）一律标注"未采集"，禁止编造；**度量缺失不作为归档 FAIL 依据**，仅影响报告完整性

## 中断恢复

任一步中断后，按 `workflow-state.md` 的 `stage-8-archive` 状态与 `archive-report.md` 是否存在判断重入点：报告已存在 → 直接进 S3；否则从 S1 重跑（统计命令幂等）。

## 自评（harness 规范）

- 固定脚本可查的（统计、state 更新）由主 agent 脚本化执行，AI 不自由发挥
- 单一委派点（workflow-reviewer），标准输入输出文档交互
- 全文 < 120 行，无超大项目窗口风险（统计命令输出可控）
