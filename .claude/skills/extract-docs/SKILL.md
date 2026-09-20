---
name: extract-docs
description: 模块级业务+技术文档抽取器。输入一个模块/功能名（如「商广剩余流量对接」「驻店工单」「渠道保量」），从知识图谱与源码中提取带证据的事实，产出六部分文档 <短英文名>.md（概述/功能与流程/关键设计/技术架构分析/知识清单/测试问题集）+ 机器可校验元数据 <短英文名>.json，并强制通过防假知识门禁脚本。用户说「提取XX文档」「梳理XX模块」「生成XX知识库」「整理XX交接文档」「摸清XX陌生代码」「extract-docs」时必须用本 skill；凡是要把一个业务模块系统性文档化、给产品经理或新同事讲清一个模块的场景，即使用户没点名 skill 也要用。
---

# extract-docs — 模块文档抽取器

你是一位精通代码分析与文档工程的全栈技术专家：从陌生代码库提取核心业务逻辑，生成高质量技术与产品文档。本 skill 把这件事标准化——**事实全部来自知识图谱+源码双证，产物机器可校验**。

## 产物

| 文件 | 位置 | 作用 |
|---|---|---|
| `<短英文名>.md` | `knowledge/_manual/<domain>/` | 六部分人读文档（结构见 [`references/doc-template.md`](references/doc-template.md)） |
| `<短英文名>.json` | 同上 | 机器可校验元数据（schema 见 [`references/metadata-schema.md`](references/metadata-schema.md)），**是知识清单的唯一事实源**，兼作后续 doc-sync 的 source-map |

短英文名用模块的代码层命名（如 SmartAd→`smartad-dsp`）。**文件名不带 `module-` 前缀**（zhudian 域历史文件带前缀系旧约定，新产物一律不带）。

## 铁律（每条对应一种真实事故，违反=返工）

1. **假知识比没知识更毒**：每个端点/Job/表/MQ/Redis/Apollo 断言必须过 `scripts/verify_metadata.py` 门禁（退出码 0 才可交付）。查无实据的条目：要么删，要么补真实证据——禁止"应该是这样"。
2. **声明与事实不脱节**：md 第五部分由 json 渲染，计数行与 json 数组长度由脚本对账。禁止两边手写出现「文档写 16 Job 实为 15」。
3. **跨文档矛盾收敛**：交付前 grep 本域既有文档的同类口径（数量、命名、链路方向），冲突必须收敛——A 文档说 2 个 Job、B 说 1 个，两份都不可信。
4. **跨平台**：路径一律经 `knowledge/projects.json` 解析，产物中禁止出现 `D:/`、`C:\` 等平台专属绝对路径（这份知识库要在 mac/windows 都能跑）。

另有通用要求：技术描述准确、术语一致、架构图与代码结构一致、所有技术断言有代码证据、关键决策说明理由。

## 执行流水线

| 阶段 | 输入 | 输出 | 要点 |
|---|---|---|---|
| P0 路由定位 | 模块/功能名 | 涉及应用清单 + 域归属 + 文件名 | 定不了就问用户，禁止猜 |
| P1 事实采集 | 应用清单 | <短英文名>.json 草稿 | 每条带 evidence |
| P2 预校验 | json 草稿 | `--skip-md` 门禁通过 | FAIL 修数据不修脚本 |
| P3 文档生成 | json + 源码理解 | <短英文名>.md | 严格按模板六部分 |
| P4 终验+登记 | json + md | 完整门禁通过 + 域登记 + git add | 计数对账在此收口 |

### P0 路由定位

1. `Read knowledge/_manual/domains.json`，用模块名、业务别名、核心类名/表名（中文+英文都试）匹配各域 `triggers`。
2. 命中域 → 读该域 `INDEX.md`。**INDEX 的一句话描述不覆盖全部落点——凭描述判定"该篇无关"而跳过是违规**，落点存疑时在域目录 `Grep` 关键词兜底（域内检索阶梯见 CLAUDE.md §2.0）。
3. 未命中域 → 按 CLAUDE.md §2.1 走 `manifest.json → fragments → 定向源码 grep`。
4. 确定三件事：涉及哪些应用（跨层：page/bff/api/backend/job 可能都有）、归属域（已有 or 新建）、`<短英文名>` 文件名。**涉及应用清单无法确定时问用户**。
5. 目标 md 已存在 → 转**更新模式**：测试问题集保留既有问题并追加（锚点重指向新小节），其余部分重采重写，最后与新 json 对账。
6. 前端页面在范围内时，先按 CLAUDE.md §5 看产品原型/设计稿（若需求引用了的话）再描述交互。

### P1 事实采集（生成 json 草稿）

按 [`references/metadata-schema.md`](references/metadata-schema.md) 逐类采集。检索纪律 = CLAUDE.md §2 渐进加载：fragments 优先定位，分片不足才对**具体子目录**定向 grep，禁止全项目裸 grep。

- **entries.apis / entries.jobs**：从 fragments `apisByDomain`/`jobs` 抄 nid 与路径；分片盲区（CLAUDE.md 已知 JOB/MQ 追踪弱）用源码 Controller 注解、Job 类补齐，evidence 写 `相对路径:行号`。
- **tables**：access 判定别只看一眼——按表名扫 mapper 全部语句（insert/update/delete=写，select=读），别用 `insert into 表名` 单行 grep 定写入方。
- **third_party_apis**：跨系统调用先对照 CLAUDE.md §1.1 retrofit Bean 映射表——**登记在表的 Bean 是工作区内项目互调**（不算三方），其余 `*Api` 才是外部三方（SmartAd/Dealer/Crm 等）；逐个写清提供的能力。
- **redis_keys / apollo_keys**：从常量类、`@Cacheable`/RedisTemplate 操作、`@Value`/Apollo 注解采集，动态段写 `*` 通配。
- **mq**：exchange/queue 声明、`@RabbitListener`、`convertAndSend` 两头都要采（direction 区分 producer/consumer；kafka 同理）。
- **模块边界判定**：入口（api/job/mq）归属看**源码载体**——声明在哪个项目就归哪个 app；`apps` 清单 = 有 entries 落点、或正文有专节描述其行为的应用；仅作为数据上下游被提及的外围能力不入 apps，只出现在数据流图与「业务边界」。与域文档/fragments 口径冲突时以源码为准，并在概述「业务边界」交代差异。
- **evidence 锚会被门禁核真**：nid 锚查存在性+指向一致性（写 t-90 而该表实际是 t-0 会被拦），源码锚查可定位+行号不越界。锚路径可省略 maven 模块/包前缀（脚本按路径尾部匹配），但禁止通配符、禁止裸类名不带定位。
- 采集完先跑预检：`python3 .claude/skills/extract-docs/scripts/verify_metadata.py <json> --skip-md`。SKIP 项（无验证手段）要么补证据要么删除并在 md 声明"未能实证"。

### P3 文档生成

`Read references/doc-template.md`，严格按六部分结构写。要点：

- 标题只允许 1/2/3 级；全文恰好 1 个一级标题。
- 第五部分知识清单**从 json 逐条渲染**，开头放固定格式清单计数行。
- mermaid 一张图颜色 ≤3 种（模板给了三色 classDef 范式：用户蓝/运营绿/定时任务与外部系统黄）。**2.1 业务流程图必须是角色协作视角**：主体只允许人/角色、外部系统、定时任务（写法「定时任务·批量/单个：做xx」），内部系统/表/MQ/接口路径一律下沉 3.1/3.4，细则见模板 2.1 节铁律。
- 门禁强制图（缺=FAIL）：业务流程图 flowchart、ER 图 erDiagram、时序图 sequenceDiagram；stateDiagram-v2 按 json `features` 条件强制；C4 缺失=WARN（C1 仅 1 个系统可豁免，但须在文档说明）。**C4 一律用 flowchart 等价视图，禁用 mermaid C4 原生语法（11+ 已移除，渲染必炸）**。不读脚本源码也要知道：这三张图是硬门槛。
- 3.1 C4 架构图：C1 粒度=营销活动整个系统，C2 粒度=各应用；C1 只有 1 个系统时可只画 C2 并说明。第三部分固定顺序：架构总览（3.1）先行，再到状态机（3.2）、ER、数据流、时序——宏观到微观。
- 3.2 状态机/审批流：模块含状态字段或审批环节就必须有 `stateDiagram-v2`（json `features` 里如实标记，门禁会查图）。
- 第四部分缺点分析四问必查：缓存一致性、幂等/重复消费、事务内 MQ+RPC、角色/数据权限——每条结论都挂代码证据，没有证据的缺陷不写。
- 第六部分测试问题集：3-6 个"考文档是否准确"的问题，参考答案放 `<details>` 折叠并锚定本文小节。

### P4 终验与登记收尾

1. 跑完整门禁（不带 `--skip-md`），退出码 0 才继续。
2. **新域**：建 `knowledge/_manual/<域>/INDEX.md`（照抄 zhudian/INDEX.md 结构：一句话定位 + 模块表 + 适用场景），并在 `domains.json` 追加一条 `{key, triggers[中英文关键词], index}`。**已有域**：在其 INDEX.md 模块表加一行。
3. 跨文档矛盾收敛：`grep -rn "<关键计数词>" knowledge/_manual/<域>/` 核对既有文档（**含 .html 可视化文档**）口径。发现冲突（方法数、配置键默认值、表读写归属、规格数量这类过时声明）时，**以源码实证为准就地修正旧文档**并更新其「核实日期」标注；当场无法实证的旧断言改标「待核实」，不盲改不盲信。旧 html 有生成脚本（如 zhudian 的 md2html.py）的走脚本重生成，手维的直接编辑。
4. 按 CLAUDE.md §6 收尾：涉及文件 `git add`（不 commit）；本次新增/变更的业务落点登记 `knowledge/_data/biz-glossary.json`，无落点也要明说。
5. 向用户回执：产物路径 + 门禁结果（PASS/FAIL/SKIP 计数）+ 更新模式下列出与旧文档的差异点。

## 与既有 skill 的分工

| skill | 关系 |
|---|---|
| `domain-harness-init`（用户级） | 整项目冷启动建域框架（L0/L1/L2 全量）；extract-docs 是**单模块深钻**，粒度更细、带架构分析 |
| `java-harness-init`（用户级） | 11 维技术架构扫描；写第四部分前可先跑它拿素材（可选） |
| `*-doc-sync`（zhudian/lead 等） | 文档的**持续同步**；extract-docs 的元数据 json evidence 即 sync 的 source-map |

## 异常处理

- 门禁 FAIL 且确认是脚本 bug → 修脚本并回归本 skill 冒烟用例，但不许靠改脚本"放行"编造数据。
- 模块横跨多个域 → 以主域落文件，边界在第一部分「业务边界」写清，其余域 INDEX 互挂链接。
- 源码不可达（projects.json 缺 key）→ 相关条目标 SKIP 并在 md 显式声明"未经源码实证"，不许静默降级。
