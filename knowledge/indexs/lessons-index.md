# 经验教训索引 — AI 阶段操作指南

> 本索引按"开发→测试→上线→线上"四阶段组织，告诉 AI 在当前阶段该读什么、该产出什么。

## 编码阶段（当前在改代码）

- **必读**: [conventions.md](../lessons/conventions.md) + [dev-session.md](../lessons/dev-session.md)
- **必做**: 避开已知坑点，遵循隐性约定
- **产出**: session 中犯的错 → harness-memory 提取到 dev-session.md

## 修 Bug 阶段（测试提了 Bug）

- **必读**: [test-bugs.md](../lessons/test-bugs.md) + [conventions.md](../lessons/conventions.md)
- **必做**: 修复后回顾同类 Bug 是否存在
- **产出**: Bug 修复后补一条教训到 test-bugs.md

## 上线前阶段（准备发版）

- **必读**: [release-risk.md](../lessons/release-risk.md)（逐条确认风险状态）
- **必做**: 评估本次变更是否引入新风险
- **产出**: 新发现的风险追加到 release-risk.md

## 线上排查阶段（故障发生了）

- **必读**: [incident-postmortem.md](../lessons/incident-postmortem.md) + [conventions.md](../lessons/conventions.md)
- **必做**: 先查历史是否有类似故障
- **产出**: 故障恢复后补写复盘到 incident-postmortem.md

## 快速导航

| 阶段 | 文件 | 条目数 | 最近更新 |
|------|------|--------|---------|
| 隐性约定 | [conventions.md](../lessons/conventions.md) | — | — |
| 开发 | [dev-session.md](../lessons/dev-session.md) | — | — |
| 测试 | [test-bugs.md](../lessons/test-bugs.md) | — | — |
| 上线 | [release-risk.md](../lessons/release-risk.md) | — | — |
| 线上 | [incident-postmortem.md](../lessons/incident-postmortem.md) | — | — |
