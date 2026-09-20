# 项目导航地图

> AI 进入项目后的第一入口。本文档定义必加载文件、协作骨架和渐进式入口索引。
>
> **路径约定**：所有 `knowledge/` 前缀的引用相对于项目根目录解析。这些文件由 `domain-harness-init` skill 初始化时自动生成。如文件不存在，说明尚未完成知识抽取。

---

## 一、必加载文件

@knowledge/indexs/domain-index.md
@knowledge/indexs/lessons-index.md

### 文件层次说明

```
knowledge/
├── indexs/                     ← 导航层（索引文件，必加载）
│   ├── tech-index.md           → knowledge/tech/*.md
│   ├── domain-index.md         L0 领域路由表 + 领域入口
│   └── lessons-index.md        → knowledge/lessons/*.md
├── domain/                     ← 领域知识层（按需加载）
│   ├── api/                    L1 API 接口 + 参数
│   ├── service/                L2 Service 契约
│   └── service-hot.md          方法级跨域与高风险速查
├── tech/                       ← 技术架构层（按需加载，含 database.md）
└── lessons/                    ← 经验教训层（按阶段加载）
```

索引文件（`indexs/`）内部已设计"首要入口 + 按需加载"结构。加载 `domain-index.md` 后，先用匹配关键词和不归属本域判断候选领域，再根据功能索引读取对应 API/Service；涉及状态、规则、事务、锁、事件、缓存或跨域副作用时，必须读取 Service 文件和 `service-hot.md`。其中 `domain-index.md` 的领域依赖只回答“可能牵动谁”，`service-hot.md` 回答“具体哪里危险、还要读哪些文件、测试范围要扩到哪里”。

---

## 二、可用命令

| 命令 | 用途 | 阶段 |
|------|------|------|
| `/opsx:explore` | 只读探索、问题澄清、方案比较 | 需求分析 |
| `/opsx:propose` | 一次性生成 proposal / design / tasks | 方案设计 |
| `/opsx:update` | 修订既有 change 工件并保持一致 | 方案迭代 |
| `/opsx:apply` | 按 tasks 实施代码变更 | 编码实现 |
| `/opsx:sync` | 增量 spec 同步主 spec（不归档） | 规格同步 |
| `/opsx:archive` | 归档 change，处理 spec 同步 | 归档 |

---

## 三、可用 Skills

| Skill | 用途 |
|-------|------|
| `openspec-explore` | 需求探索 |
| `openspec-propose` | 生成方案 |
| `openspec-update-change` | 修订变更 |
| `openspec-apply-change` | 实施变更 |
| `openspec-sync-specs` | 同步规格 |
| `openspec-archive-change` | 归档变更 |
| `review-summary` | Review 总结 |
| `spring-architecture-review` | Spring 架构审查 |
| `sql-risk-review` | SQL 风险审查 |

---

## 六、默认约束

- 先补 change 工件，再进入代码实现
- 高风险操作先人工确认
- 不硬编码敏感信息，统一走环境变量
- 核心流程和关键判断补中文注释
- 对每个涉及改动的入口，必须追踪完整调用链，不能只看终点相同就假设路径相同
- 必加载文件外的知识文件，单次加载不超过 5 个，超出时分批加载
