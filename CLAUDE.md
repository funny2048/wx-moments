## Development Instructions

@AGENTS.md
@workflow/daily/WORKFLOW.md


## knowledge
本项目已初始化 Domain Harness 知识框架，知识文档存放在 knowledge/ 目录下。
- 技术架构索引：knowledge/indexs/tech-index.md
- 经验教训索引：knowledge/indexs/lessons-index.md
- 领域路由表：knowledge/indexs/domain-index.md
- L1 API 接口：knowledge/domain/api/*.md
- L1 接口参数：knowledge/domain/api/param/*.md
- L2 Service 契约：knowledge/domain/service/*.md
- 技术架构：knowledge/tech/*.md
- 经验教训：knowledge/lessons/*.md


## graphify

- **graphify** - any input to knowledge graph. Trigger: `/graphify`
  When the user types `/graphify`, invoke the Skill tool with `skill: "graphify"` before doing anything else.

## 强制规则
- 禁止直接改写代码，必须走 CLAUDE.md 中已安装的工作流。
- 禁止通过`git diff`、`git log`等方式查看代码进行需求文档解析。
