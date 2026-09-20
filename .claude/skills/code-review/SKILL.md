---
name: code-review
description: daily 工作流阶段七双评编排器。纯命令统计改动文件规模（排除测试代码、md、openspec 工件），超过 10 个文件才并行派发 code-reviewer + architect-reviewer 双评并驱动回炉循环；不超过则豁免双评直接过阶段七。当用户要求"代码评审"、"双评"、"阶段七审查"，或工作流推进到阶段七时触发。
---

# 阶段七双评编排（daily 工作流）

## 定位与分工

本 skill 是**编排器**，不是执行者：

- **主 agent 执行**（本 skill 内）：规模统计（纯命令，零 AI 判断）、gate 判定、subagent 派发、结论解析、workflow-state 更新——不亲自评审
- **subagent 执行**（委派）：`code-reviewer`（`scope: change`）管正确性 / BLOCKER；`architect-reviewer` 管分层 / 基线 / 实现一致性

## 前置检查（不满足则阻塞并告知用户）

1. `workflow-state.md` 中 `stage-6-impl-test-loop` 已标记 `[x]`（前置检查单条件，统一状态规则 8）
2. `test-mapping.md` 存在且用例缺口为 0

## S1 规模预检（主 agent，纯命令）

进入本 skill 时先执行 `node .claude/scripts/telemetry-log.js {变更ID} stage-start 7`，然后统计改动文件数：

```bash
{ git diff --name-only HEAD; git ls-files --others --exclude-standard; } \
  | grep -v '^openspec/' \
  | grep -v '\.md$' \
  | grep -v '\.DS_Store$' \
  | grep -vE '(^|/)(__pycache__|target|build|node_modules)/' \
  | grep -vE '(^|/)src/test/' \
  | grep -vE '(Tests?|IT)\.java$' \
  | sort -u | tee openspec/changes/{变更ID}/review-scale.txt | wc -l
```

统计口径：已跟踪变更 + 未跟踪新文件；排除 `openspec/` 工件、`*.md`、系统与构建垃圾（`.DS_Store` / `__pycache__` / `target` / `build` / `node_modules`）、测试代码（`src/test/` 路径或 `Test.java` / `Tests.java` / `IT.java` 后缀）。过滤只向"多审"方向保守起见——漏排的杂项会推高计数触发评审，不会静默跳过。

- 计数 **> 10** → S2 全量双评（此时主 agent 设置 `/goal 代码评审与架构审查双 PASS or 5 轮熔断 or stop after 30 turns`）
- 计数 **≤ 10** → 跳过 S2/S3，直接走 S4 豁免出口；豁免依据 = `review-scale.txt` + 计数，禁止目测估算替代命令输出

## S2 双评并行派发（subagent，同时派发）

### code-reviewer（正确性 / BLOCKER）

```
Task:
  description: "code-review / {变更ID}"
  subagent_type: "general-purpose"
  prompt: |
    读取 .claude/agents/code-reviewer.md 并执行。

    ---
    ## 任务参数
    change-id: {变更ID}
    scope: change
```

### architect-reviewer（分层 / 基线 / 实现一致性）

```
Task:
  description: "arch-review / {变更ID}"
  subagent_type: "general-purpose"
  prompt: |
    读取 .claude/agents/architect-reviewer.md 并执行。

    ---
    ## 任务参数
    change-id: {变更ID}
```

subagent 返回后主 context 只记录结论行（code-reviewer 末行 `PASS|BLOCKER {变更ID}`；architect-reviewer「整体结论」行）与报告路径，禁止整篇读回。

## S3 结论处理与回炉循环

1. 双 PASS → S4 通过出口
2. code-review FAIL（存在 CONFIRMED BLOCKER）→ 解析报告 BLOCKER 清单，派 `executor` 修复 → 重派 code-reviewer（自动增量模式，只审修复文件）
3. arch-review FAIL → 结构 / 一致性缺陷派 `executor` 整改 → 重派 architect-reviewer；属设计本身缺陷 → 回阶段三（按统一状态规则回退）
4. 审查→修复 = 1 轮；5 轮未双 PASS → 熔断，产物保留交人工
5. 修复期间禁止改用例语义（语义缺口走阶段四增补协议，增补后重过阶段五评审）；每轮回炉记 telemetry `retry 7`，熔断记 `circuit-break 7`

## S4 出口与 state 更新（主 agent）

| 出口 | state 更新 |
|---|---|
| 双评通过 | `code-review` / `arch-review` 记报告路径；`stage-7-dual-review` 置 `[x]`；`Current Stage` → stage-8-archive |
| 规模豁免（≤10） | `code-review: skipped(N)`、`arch-review: skipped(N)`（N = S1 计数，依据 `review-scale.txt`）；`stage-7-dual-review` 置 `[x]`；`Current Stage` → stage-8-archive |
| 熔断 | 保持 stage-7 阻塞，写明熔断原因，交人工 |

出口完成后记 telemetry `stage-end 7`，达标或熔断后 `/goal clear`。

## 边界（不可违反）

- 主 agent 不亲自评审、不代写评审报告（统一状态规则 6；规模豁免出口例外——无评审产物，state 标注为凭）
- 豁免判定只认 S1 命令输出，禁止估算
- git 写操作全部禁止（commit / push 归用户手动执行）
- 评审 subagent 失败 → 阶段阻塞重派 / SendMessage 续跑，禁止主 agent 接管评审

## 中断恢复

- `stage-7-dual-review` 已 `[x]` → 整段跳过（前置检查唯一判据）
- 重入时 S1 命令幂等重跑；code-reviewer / architect-reviewer 各自 `review-work/` 断点由 agent 自身增量续跑
- 回退重入（阶段八 FAIL 回阶段六后再进阶段七）：gate 重算，state 中旧 `skipped` 标注 / 报告路径按统一状态规则回拨重置

## 自评（harness 规范）

- gate 判定 = 固定命令可查，AI 不自由发挥；豁免依据落盘 `review-scale.txt` 可审计
- 流水线拆分：S1 纯命令 / S2 并行 subagent / S3 回炉 / S4 state——标准输入输出文档交互
- 全文 < 130 行；超大项目无窗口风险（主 agent 只接触一行计数与结论行，不读 diff 内容）
