---
name: openspec-bridge
description: >
  OpenSpec 配置桥接。解决 AI 不知道要读 openspec/config.yaml 的"链路断裂"问题。
  在项目初始化时自动安装为 alwaysApply 规则，让 AI 在执行 OpenSpec 操作前先读取配置。
  当用户说"初始化openspec"、"安装bridge-rule"、"openspec-bridge"时触发。
---

# openspec-bridge：OpenSpec 配置桥接

## 为什么需要这个 Skill

OpenSpec 的 config.yaml 支持自定义 rules 字段，但 AI 不知道要去读这个文件。
这个 Skill 在项目初始化时生成一个 Bridge Rule 文件，像"指路牌"一样告诉 AI：
"当你执行 OpenSpec 操作时，先去读 openspec/config.yaml 中的 rules。"

保持 config.yaml 作为 single source of truth，Bridge Rule 只做指路。

## 工作流程

### Step 1: 检查目标位置
检查 `.claude/rules/` 目录是否存在，不存在则创建。

### Step 2: 生成 Bridge Rule 文件
将模板 `templates/openspec-config-awareness.md` 复制到 `.claude/rules/openspec-config-awareness.md`

### Step 3: 验证
确认文件已正确安装，且内容包含 alwaysApply: true

## 输出
告知用户 Bridge Rule 已安装，AI 在后续 OpenSpec 操作中会自动读取 config.yaml。
