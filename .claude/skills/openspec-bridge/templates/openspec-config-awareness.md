---
alwaysApply: true
---

# OpenSpec 配置感知

当你执行 OpenSpec 相关操作（propose、apply、verify、archive）时，必须先读取 `openspec/config.yaml` 中的 rules 字段，确保操作符合项目配置的约束。

读取路径：`openspec/config.yaml`

## 特别注意：propose 前置条件

当执行 propose 操作时，config.yaml 的 `rules.propose` 要求先完成 `harness-code-map` 数据流追踪分析。这是强制步骤，不可跳过。具体来说：

1. 先读取用户的需求描述
2. 执行 `/harness-code-map` 技能，追踪相关 API 和定时任务的数据流
3. 基于追踪结果再创建 proposal、design、tasks 工件
4. 追踪结果必须作为 proposal.md 和 design.md 的输入
