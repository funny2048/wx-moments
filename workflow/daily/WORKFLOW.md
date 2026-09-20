# 执行模式（强制）

> 版本控制：本文件属 workflow-assets 版本体（daily 档），版本唯一事实源为 harness 根 `harness-version.json`，本文内不写死版本号；落后比对：`node .claude/scripts/harness-version.js check`

**各阶段执行模式判定规则：**

1. 必须遵循各个阶段要求的主 agent 还是 subagent 的执行方式。
2. **subagent 执行的阶段**返回后，主 context 只记录产物路径和阶段状态，**禁止**将产物内容读回主 context；**主 agent 执行的阶段**（如阶段二、阶段五评审 gate）仅可读取该阶段输入清单内声明的产物，禁止超范围读取。**编排者豁免**：主 agent 作为 S3/S4/S6/S7 循环阶段（S5 用户评审 gate 除外）的编排者，可读取 `explore.md` 的结论/风险/语义闭环章节——阶段二是主 agent 自身产物，其理解是四循环编排判断的锚点，context 压缩后重入也靠它恢复理解
3. 若 subagent 启动失败，标记阶段阻塞，**禁止**回退到主 agent 自行处理
4. **阶段跳过/恢复一律以 `workflow-state.md` 的阶段状态为准**：只有标记为 `[x]`（PASS）的阶段才允许跳过。产物文件存在 ≠ 阶段完成——被回退的阶段即使产物还在，也必须重做。
5. **回退时必须**：将被回退阶段的 `[x]` 回拨为 `[ ]`，并把旧产物重命名为 `{name}.redo.md`（保留一次历史，避免覆盖丢失），防止重做时读到旧的错误产物。
6. **小体量任务不派 subagent**：≤5 处纯文档表述修改、单文件小改、纯验证命令（编译/测试复检），由主 agent 直接执行，避免 subagent 启动与读文件开销。
7. **subagent 单任务聚焦单一交付物**：一个 subagent 只做一类事（仅编码 / 仅测试码 / 仅审查），禁止"编码+跑测试+跑 sonar"长链混跑（mvn 长耗时叠加易触发 watchdog）。验证环节（mvn test / sonar）统一由主 agent 或独立验证步骤执行。
8. **subagent 中断恢复**：watchdog 超时或 API 错误中断的 subagent，优先 `SendMessage` resume 续跑（上下文保留）；恢复前主 agent 必须先核查已落盘的改动状态（git diff/grep），已完成部分不重做，只接管缺失环节。
9. **模型降级**：subagent 因主模型额度/配额失败时，直接降级 sonnet 重派或按第 8 条处理，不空等恢复。
10. **/goal 是主会话级命令**：WORKFLOW 只指示主 agent 在循环阶段设置达标条件与 turn 上限，无法强制执行——这是约定不是机制；熔断兜底由 turn 上限 + 人工介入保证。

---

## Telemetry 采集（强制）

工作流执行过程逐事件落盘到 `openspec/changes/{变更ID}/telemetry.jsonl`，供归档统计与效能分析：

1. **指针绑定**：阶段一完成 change 初始化后，主 agent 必须执行 `echo "<变更ID>" > openspec/changes/.current-change`。人工干预事件依赖该指针定位归属 change，指针缺失时事件丢弃（宁漏记不错记）
2. **阶段事件**：每个阶段开始/结束，由该阶段执行方调用 `node .claude/scripts/telemetry-log.js <变更ID> stage-start <阶段号>` / `stage-end <阶段号>`
3. **回退事件**：审查/验证不通过触发回退时记 `retry <目标阶段号>`；重试达上限或红绿熔断记 `circuit-break <阶段号>`
4. **人工干预与用户等待（hook 自动采集，无需手动调用）**：UserPromptSubmit hook 记录用户每轮输入（intervention 事件）；工作流向用户提问**一律使用 AskUserQuestion 工具**，其 PreToolUse/PostToolUse hook 自动记录 ask-user / ask-done 事件（ask-done 含 duration_ms = 提问到用户答完的等待毫秒数），阶段耗时据此可拆分 AI 工作时间与用户等待时间
5. **指针清理**：归档（阶段八）完成后清除 `.current-change`；SessionStart hook 每 session 起步（含 resume）静默清指针，兜底防串台。hook 配置随项目 settings 分发，exec form 调用 `node ${CLAUDE_PROJECT_DIR}/.claude/scripts/telemetry-log.js`（跨平台零 bash/jq；遥测路径恒 exit 0 且 stdout 为空，不污染上下文）

---

## 环境前置检查（工作流启动前强制，主 agent 执行）

进入阶段一前必须执行 `node .claude/scripts/openspec-check.js`（含中断恢复重入），按退出码分流，阻断码禁止进入阶段一：

| 退出码 | 含义 | 处置 |
|--------|------|------|
| 0 | CLI 就绪 + 项目已初始化 + `openspec update` 完成 | 进入阶段一 |
| 1 | openspec 未安装且自动安装失败（脚本自动 `npm install -g @fission-ai/openspec` 后复验；node/npm 均不可用时提示先安装 Node.js） | 阻断，报告用户人工处理 |
| 2 | 项目未初始化（缺 `openspec/` 目录） | 阻断，提示用户执行 `openspec init --tools claude` 后重跑检查；禁止工作流自行 init（init 会写 AGENTS.md/CLAUDE.md 指令块，属安装期职责） |
| 3 | `openspec update` 失败 | 不阻断，向用户提示后继续 |

检查幂等、不落 `workflow-state.md`（环境状态与阶段状态解耦），每次工作流启动都执行；脚本为跨平台 Node 实现（Windows/macOS 行为一致），自带 PATH 兜底解析（nvm 版本目录 / Windows npm 全局目录），openspec 装在 nvm 下也能探到

---

# 执行阶段

---

### 阶段一：需求接入与领域匹配（主 agent 执行，必须最先执行）

| 项目 | 内容 |
|------|------|
| **触发条件** | 用户提供一次产品 PRD 或提出新需求 |
| **前置检查** | 若 `workflow-state.md` 中 `stage-1-intake-domain` 已标记 `[x]`（PASS），直接进入阶段二 |
| **输入** | 原始 PRD 内容、PRD 文件路径或需求描述 + `knowledge/lessons/conventions.md` |
| **处理** | 1) 创建 change 目录并写 `prd.md` + `workflow-state.md` + `.current-change` 指针；2) PRD 含糊点逐条向用户澄清，结论回写 `prd.md`「澄清补充」附录；3) 领域匹配（domain-index 粗匹配）；4) 委派 `graph-query` subagent 产出链路文档；5) **Tier Gate**：PRD 触点初判 × 图谱触点复核 → 轻量/标准判定（用户确认） |
| **输出** | `openspec/changes/{变更ID}/prd.md`（含「澄清补充」附录）+ `domain.md` + `pipeline.md` + `workflow-state.md`（含 mode 判定） |
| **验收标准** | change 目录/prd/workflow-state 存在；`domain.md` section 列表与 `workflow/_shared/domain-match-template.md` 完全一致；`pipeline.md` 包含核心节点、边关系、社区聚类；mode 已判定（lightweight 附 Tier Gate 依据） |
| **Telemetry** | stage-start 1 / stage-end 1；初始化完成后必须写 `.current-change` 指针（见 Telemetry 采集规则第 1 条） |

**执行规则：**
1. 将当前需求映射为唯一 `变更ID`，统一作为后续阶段产物目录名。命名格式：`{yyyymmdd}-{变更语义名}`——日期前缀取 change 创建当日（如 `20260824-order-export`），语义名用 kebab-case；同日多变更以语义名区分，目录按日期自然排序，禁止复用历史变更ID。
2. 原始 PRD 必须优先落到 `openspec/changes/{变更ID}/prd.md`，作为后续所有阶段的唯一需求基线；若 PRD 信息不足以唯一确定需求范围，必须先让用户确认。
3. 初始化 `workflow-state.md`（结构见文末），并执行 `echo "<变更ID>" > openspec/changes/.current-change` 绑定 telemetry 指针。
4. **PRD 澄清（主 agent 执行）**：落盘 `prd.md` 后逐条排查含糊点（范围/边界/粒度/约束/基准），逐项向用户确认；结论由主 agent 追加写至 `prd.md`「澄清补充」附录（保持单一需求基线），未澄清项留阶段二六问补漏，后续所有阶段禁止重复提问。
5. **Tier Gate（图谱定位后判定，禁止提前）**：主 agent 基于 PRD 的触点/flags 分析仅为初判信号，必须与 `pipeline.md` 图谱触点复核一致（无跨域写、无状态机/契约/DDL 触碰、触达节点与初判触点吻合）→ 提议 `mode: lightweight`，用户确认后在 state 落判定依据（引用 pipeline.md + 准入四条结论）并走轻量通道；信号不一致、准入四条任一不满足或用户拒绝 → `mode: standard` 全流程。**禁止仅凭 PRD 文本单信号定级，禁止在图谱查询前判规模**；Tier Gate 提议可与规则 10 的领域匹配确认同场呈现（减少打断）。
6. **领域匹配**：先读 `workflow/_shared/domain-match-template.md` 提取唯一格式标准；读 `knowledge/indexs/domain-index.md` 全文，用 `匹配关键词`、`不归属本域`、`功能索引` 判断候选领域；命中后必须读对应 API 文件和 Service 文件二次确认；涉及状态流转/核心规则/事务/锁/事件/缓存/跨域副作用时必须读 `knowledge/domain/service-hot.md`。
7. `domain.md` 的 `二、涉及的领域及功能` 必须包含三张短表：`匹配摘要`（人工确认：触达/领域/功能/入口/置信度/匹配依据）、`图谱查询输入`（阶段一末尾图谱消费：ID/查询种子/必读文件/风险提示）、`已排除领域`（排除原因）。禁止保留 FEWSHOT 注释/示例行/占位符；生成后逐 section 对比模板校验。
8. **图谱查询（委派 subagent）**：`subagent_type: "general-purpose"`，读取 `.claude/agents/graph-query.md` 执行，输入 `domain.md`，输出 `pipeline.md`。只总结 graph 输出的链路，禁止读取代码看详细逻辑；必须生成链路文档路径，作为阶段二的必需输入。
9. 严格匹配已有领域，若匹配不在 `domain-index.md` 已列领域内，**必须让用户澄清**；模板是唯一格式标准，描述有歧义以模板为准。
10. **领域匹配结论须让用户确认**（`匹配摘要` + `已排除领域` 两张表一并呈现），确认后方可进入阶段二——多领域命中或置信度中/高时尤其不得跳过。

---

### 阶段二：需求探索澄清（主 agent 执行）

| 项目 | 内容 |
|------|------|
| **触发条件** | 阶段一完成后执行 |
| **前置检查** | 若 `stage-2-explore` 已标记 `[x]`，直接进入阶段三；若无 `pipeline.md`，**禁止执行此阶段**，返回阶段一补图谱查询 |
| **执行方式** | 主 agent 使用 `/opsx:explore`，遇到不确定点**必须暂停并让用户确认**后再继续 |
| **输入** | `openspec/changes/{变更ID}/prd.md`（含「澄清补充」附录）+ `pipeline.md` + `knowledge/lessons/conventions.md` + `knowledge/lessons/dev-session.md` |
| **输出** | `openspec/changes/{变更ID}/explore.md` |
| **规范** | 输出内容遵循 `workflow/_shared/prd-explore-template.md`；如模板缺失，使用最小必要结构 |
| **验收标准** | 文档包含：背景、目标、范围、影响分析、风险评估；**语义闭环 Gate 六问全部可答且落盘**（问答含数据示例写入 explore.md「语义闭环」章节，见下） |
| **Telemetry** | stage-start 2 / stage-end 2 |

**语义闭环 Gate（六问，未通过禁止进阶段三）：**

> 一条需求只有"能用具体数据例子无矛盾地走查出确定结果"才算澄清完成。任何一问答不上来，必须回给用户，禁止自行假设。

1. **语义走查**：给每条规则构造 1-2 组具体数据示例人工走一遍，能否得出无矛盾的确定结果？
2. **归属边界**：每条规则/改动由哪个系统/团队实施？本仓库与上游的活是否划清并经用户确认？
3. **作用范围与粒度维度**：影响哪些业务类型/分组/枚举/入口分支？去重/排重/聚合/统计类规则必须明确粒度维度（系统级/用户级/(用户+对象)复合），key 由哪些字段组成——高频理解偏差点，禁自行假设。
4. **约束与优先级**：数量限额、排序、优先级规则各自归属到哪个对象？
5. **时间与基准**：周期/对比/触发基准如何定义？首次执行、无历史数据、空集时的行为？
6. **可验证性**：每条规则能否翻译成给定-当-则验收用例？写不出 = 还有含糊。

**落盘要求**：六问的问答结论与数据示例必须写入 `explore.md` 的「语义闭环」章节——它是阶段四用例设计的种子数据（走查示例直接复用为测试用例），也是主 agent 编排 S3-S7 循环的理解锚点。

**已澄清项消费（禁止重复提问）**：逐问先对照 `prd.md`「澄清补充」附录——已澄清项直接引用结论落盘（标注来源澄清附录），仅未澄清项（附录未覆盖 / 六问新暴露）才回给用户。

**执行约束：**
- 严格依据链路文档反查需求改动点，以 controller、job 为入口。
- **外部服务集成约束检查（不可跳过）**：读取 `.claude/rules/external-service-constraints.md` 对照 PRD 逐项检查，结果写入 explore.md 风险评估章节；遗漏则 explore.md 视为不完整。
- 需求探索澄清阶段不允许自行扩展需求范围。
- 执行结束后必须让用户确认探索结论；改动细节不清楚必须让用户澄清。

---

### 阶段三：方案设计与对抗验证（循环A，设计产出与对抗审查均为强制 subagent）

**/goal 接入（主 agent 进入本阶段时设置）：**
```
/goal 设计对抗审查阻塞级问题=0 且待澄清清单为空 or stop after 4 turns
```

| 项目 | 内容 |
|------|------|
| **触发条件** | 阶段二完成且用户确认 |
| **前置检查** | 若 `stage-3-design-loop` 已标记 `[x]`，直接进入阶段四；若无 `explore.md`，禁止执行 |
| **subagent（设计）** | `subagent_type: "general-purpose"`，读取 `.claude/agents/tech-designer-simple.md` 执行（生成 design/api/tasks/sql） |
| **subagent（对抗）** | `subagent_type: "general-purpose"`，读取 `.claude/agents/adversarial-reviewer.md` 执行（`mode: design`） |
| **输入（设计）** | `prd.md` + `explore.md` + `knowledge/lessons/conventions.md` + `knowledge/lessons/test-bugs.md`（+ `clarify.md` 重派时） |
| **输入（对抗）** | `design.md` + `api.md` + `tasks.md` + `sql.md` + `prd.md` + `explore.md` + `knowledge/domain/service-hot.md`（涉及时） |
| **输出** | `design.md`、`api.md`、`tasks.md`、`sql.md` + `design-review-N.md`（N 从 1 递增） |
| **验收标准** | 四文档齐全任务可执行；对抗审查结论通过（阻塞级=0 且待澄清清单为空） |
| **Telemetry** | stage-start 3 / stage-end 3；回炉重派记 retry 3；4 turns 熔断记 circuit-break 3 |

**强制要求（澄清闭环协议）**
- 设计 subagent 遇到实现细节不清楚，**必须将待澄清问题逐条写入 `openspec/changes/{变更ID}/clarify.md` 并中止**，返回时只报"待澄清，见 clarify.md"；主 agent 逐条向用户确认，结论追加写回 clarify.md（对应问题下方补"结论："行）；重派时 clarify.md 作为附加输入，已澄清项禁止重复提问（已澄清数据源：`prd.md`「澄清补充」附录，提问前必须先对照）。

**循环体：**
1. tech-designer-simple 产出 design/api/tasks/sql（clarify.md 协议见上）
2. adversarial-reviewer（mode: design）攻击八类维度：状态机前置条件/事务边界/幂等/粒度维度/跨域副作用/外部服务约束/边界空集/过度设计与可回滚性 → 产出 `design-review-N.md`（阻塞级/建议级 + 待澄清清单 + 已攻击角度）
3. 阻塞级 > 0 → 回炉重派设计 → 再审；重派最多 2 次，超限熔断产物保留交人工
4. 达标或熔断后 `/goal clear`

---

### 阶段四：测试用例设计与对抗性设计（循环B）

**/goal 接入（主 agent 进入本阶段时设置）：**

```
/goal 用例对抗审查阻塞级问题=0 且用例与方案映射完整 or stop after 4 turns
```

| 项目 | 内容 |
|------|------|
| **触发条件** | 阶段三对抗验证通过 |
| **前置检查** | 若 `stage-4-test-design-loop` 已标记 `[x]`，直接进入阶段五；若无 `design.md`/`api.md`/`tasks.md`，禁止执行 |
| **subagent（用例）** | `subagent_type: "general-purpose"`，读取 `.claude/agents/test-case-designer.md` 执行 |
| **subagent（对抗）** | `subagent_type: "general-purpose"`，读取 `.claude/agents/adversarial-reviewer.md` 执行（`mode: test-case`） |
| **输入（用例）** | `api.md` + `design.md` + `tasks.md` + `sql.md` + `explore.md`「语义闭环」章节（六问数据示例为用例种子） + `knowledge/domain/service-hot.md`（涉及时）——**不依赖代码变更**（前移后尚无代码） |
| **输出** | `test-cases.md`（Markdown 缩进树脑图）+ `test-case-review-N.md` |
| **验收标准** | 入口（API/Job/MQ）全覆盖；参数维度 MECE；写入口幂等三件套齐全；单入口 ≤15 条；写操作预期含 DB 核对点；对抗审查通过（阻塞级=0 且映射完整） |
| **Telemetry** | stage-start 4 / stage-end 4；回炉记 retry 4（用例缺陷→本阶段，需求/设计缺陷→阶段三）；熔断记 circuit-break 4 |

**循环体：**
1. test-case-designer 基于 design/api/tasks/sql（黑盒，禁读实现代码）产出 test-cases.md
2. adversarial-reviewer（mode: test-case）攻击六类维度：入口/参数维度 MECE 缺口、幂等三件套（重复/并发/重试）、预期可核对性（写操作 DB 核对点）、边界空集、用例与方案脱钩、冗余未压缩 → 产出 `test-case-review-N.md`
3. 阻塞级 > 0 → 用例缺陷回本阶段重做 / 需求设计缺陷回阶段三；重派最多 2 次，超限熔断交人工
4. 达标或熔断后 `/goal clear`

---

### 阶段五：用户评审与冻结（review gate，主 agent 呈现 + 用户决策）

| 项目 | 内容 |
|------|------|
| **触发条件** | 阶段四对抗验证通过（阻塞级=0 且映射完整） |
| **前置检查** | 若 `stage-5-user-review-gate` 已标记 `[x]`，直接进入阶段六；若无 `test-cases.md` 或两轮对抗审查产物，禁止执行 |
| **执行方式** | 主 agent 汇总产物落盘 review 包并在对话内呈现索引；**用户逐项决策**（确认 / 拒绝并说明原因）；无 /goal 接入（人工决策不消耗 turn 上限，等待由用户节奏决定） |
| **输入** | `design.md` + `api.md` + `tasks.md` + `sql.md` + `test-cases.md` + `design-review-N.md` + `test-case-review-N.md` + `clarify.md`（涉及时） |
| **处理** | 1) 主 agent 从各产物结论章节提炼关键决策摘要（方案取舍/接口契约/用例覆盖/两轮对抗结论/待澄清问答）落盘 `review-pack.md`；2) 对话内呈现 review 包索引，等待用户逐项确认；3) 拒绝项必须记录原因与回退目标（设计缺陷→回阶段三，用例缺陷→回阶段四）；4) 全部确认后冻结 design/api/tasks/sql/test-cases 为实现基线，冻结清单记入 review-pack.md |
| **输出** | `review-pack.md`（含用户逐项结论与冻结基线清单）+ `workflow-state.md` 阶段推进 |
| **验收标准** | review-pack.md 含用户明确结论（无未决项）；冻结基线清单完整（文件级）；拒绝路径已落状态（回退目标明确） |
| **Telemetry** | stage-start 5 / stage-end 5；用户拒绝触发回退记 `retry <目标阶段号>`（3 或 4，按 Telemetry 规则 3 语义） |

**执行规则：**
1. `review-pack.md` 由主 agent 直写（统一状态规则第 6 条豁免项）：它是对话呈现内容的沉淀，主 agent 拥有对话上下文；提炼时只读各产物的结论/摘要章节，禁止全文读入
2. **会话中断恢复**：stage-5 保持 `[ ]` 且产物未被回退改名时，重入直接重新呈现 `review-pack.md` 索引（零推断恢复）；回退穿越场景 `review-pack.md` 已按统一状态规则 7 改名 `.redo.md`，重入按处理步骤重新生成，禁止读取旧包
3. **冻结语义（硬约束）**：通过后 design/api/tasks/sql/test-cases 即为实现基线；后续（阶段六循环内/阶段七双评）发现的语义缺口必须回阶段四增补、重新过对抗并**重过本阶段评审**，禁止实现者直接改基线
4. 用户拒绝的处置：拒绝项与原因落 review-pack.md 并重命名为 `review-pack.redo.md`（保留拒绝历史）后，按回退目标回拨状态（含下游），修复后重新推进至本阶段重新评审

---

### 阶段六：编码+测试实现（循环C 红绿循环，subagent 执行）

**/goal 接入（主 agent 进入本阶段时设置）：**

```
/goal mvn test 全绿 且 test-mapping 缺口=0 or stop after 30 turns
```

| 项目 | 内容 |
|------|------|
| **触发条件** | 阶段五用户评审与冻结通过（`stage-5-user-review-gate: [x]`） |
| **前置检查** | 若 `stage-6-impl-test-loop` 已标记 `[x]`，直接进入阶段七；**`stage-5-user-review-gate` 未标记 `[x]` 禁止执行本阶段** |
| **subagent（业务码）** | 读取 `.claude/agents/executor.md` 执行（按 `tasks.md` 分发，大变更并行 fan-out） |
| **subagent（测试码）** | 读取 `.claude/agents/tester.md` 执行（按冻结用例实现） |
| **输入** | executor：`tasks.md`；tester：`test-cases.md`（冻结版）+ 代码变更清单 |
| **输出** | 业务代码变更 + dev 环境集成测试代码 + `test-mapping.md`（用例映射表） |
| **验收标准** | `mvn test` 全绿且连跑两遍一致；用例映射缺口为 0；无 mock 内部依赖空壳单测；触发型（自造数据）有 mapper 查库断言，核对型（查存量）观测软校验违规不计红灯 |
| **Telemetry** | stage-start 6 / stage-end 6；红灯回炉记 retry 6；熔断记 circuit-break 6 |

**循环体（同阶段两次 spawn + 机器判定红绿）：**
1. executor 按 tasks.md 实现业务码（大变更按功能切片并行派发多个 executor）
2. tester 按冻结用例生成测试码 + `test-mapping.md`（用例→测试类/方法映射）
3. 主 agent 跑 `mvn test`，红灯按**三分流**自动改正（首判归 tester，回 S4 的语义级判定必须主 agent 复核后执行）：
   - **测试码缺陷**（断言/校验逻辑写错、环境问题）→ tester 自修，禁改用例语义
   - **业务码缺陷** → 回 executor 修复
   - **用例语义缺口**（用例本身漏场景）→ 回阶段四增补过对抗 + 重过阶段五评审（用户追认）
4. 全绿 + 映射缺口 0 → 出口；**同一用例 3 轮修不绿 → circuit-break 交人工**

---

### 阶段七：对抗性审查·规模门控双评（code-review skill 编排）

| 项目 | 内容 |
|------|------|
| **触发条件** | 阶段六红绿循环出口（全绿 + 映射缺口 0） |
| **前置检查** | 若 `stage-7-dual-review` 已标记 `[x]`，直接进入阶段八；`test-mapping.md` 缺口非 0 时禁止执行 |
| **技能** | `code-review`（`.claude/skills/code-review/SKILL.md`） |
| **执行方式** | 主 agent 执行 skill 编排：S1 纯命令统计改动文件数（口径：已跟踪变更 + 未跟踪新文件，排除测试代码 / `*.md` / `openspec/` 工件 / `.DS_Store`、`__pycache__`、`target` 等垃圾产物）→ 按 gate 分流 |
| **规模门控** | 改动文件 **>10** → S2 并行双评（code-reviewer `scope: change` + architect-reviewer）+ 回炉循环（审查→修复 = 1 轮，5 轮熔断交人工）；**≤10** → 豁免双评，state 标注 `skipped(N)` 直接进阶段八 |
| **输出** | `openspec/changes/{变更ID}/review-scale.txt` + `code-review.md` + `arch-review.md`（豁免时仅 review-scale.txt，评审产物缺失以 state 标注为准） |
| **验收标准** | >10：双报告结论均 PASS（code-review 无 CONFIRMED BLOCKER；arch-review 无严重/高风险）；≤10：豁免依据（计数 + review-scale.txt）已落 state |
| **Telemetry** | stage-start 7 / stage-end 7；FAIL 回炉记 retry 7（修复目标）；5 轮熔断记 circuit-break 7 |

**/goal 接入（主 agent 判定走全量双评时设置）：**
```
/goal 代码评审与架构审查双 PASS or 5 轮熔断 or stop after 30 turns
```

---

### 阶段八：验收归档（workflow-archive skill 编排）

| 项目 | 内容 |
|------|------|
| **触发条件** | 阶段七双评通过 |
| **技能** | `workflow-archive`（`.claude/skills/workflow-archive/SKILL.md`） |
| **执行方式** | 主 agent 执行 skill 编排：统计收集与效能度量（S1）→ 委派核验（S2）→ 结论处理与 state 更新（S3）→ 变更摘要 + PR 提示（S4） |
| **核验 subagent** | `subagent_type: "general-purpose"`，读取 `.claude/agents/workflow-reviewer.md` 执行（`mode: archive`） |
| **输入** | 代码变更 + 测试结果 + `code-review.md` + `arch-review.md`（≤10 文件规模豁免时缺失，以 `workflow-state.md` 的 `skipped(N)` 标注为准）+ `test-mapping.md` + `telemetry.jsonl`（阶段耗时/人工干预统计源） + 当前 change 目录产物 |
| **输出** | `openspec/changes/{变更ID}/archive-report.md`（含效能度量章节）+ 变更摘要（对话内） |
| **验收标准** | `archive-report.md` 结论通过；代码、测试、规范三对齐；效能度量数据来源可追溯，缺失项标注未采集 |
| **Telemetry** | stage-start 8 / stage-end 8；归档完成后清除 `.current-change` 指针 |

**归档检查清单：**
- [ ] 所有测试通过
- [ ] 双评（代码+架构）通过（≤10 文件规模豁免时以 state 的 `skipped(N)` 标注为准）
- [ ] 代码符合项目规范
- [ ] 无遗留 TODO/FIXME
- [ ] 文档已更新
- [ ] 效能度量已按 telemetry.jsonl + git + tasks.md 生成（缺失项标注未采集，不阻塞归档）

**执行规则：**
- 核验与 `archive-report.md` 生成必须由 subagent 完成，主 agent 禁止代写（统一状态规则第 6 条）
- git 写操作（commit / push / PR 创建）归用户手动执行；openspec 目录归档（`/opsx:archive`）在 PR 合入后由用户手动触发

---

# 轻量变更通道（按规模分级）

> 满足准入门槛的变更走 3 步轻量流程，避免小改动套全流程的固定开销。

**准入判定来源（阶段一 Tier Gate）**：四条信号由主 agent 基于 PRD 的触点 / flags 初判 + `pipeline.md`（图谱复核）提供，主 agent 综合后提议，**用户确认后**方可进入轻量通道（判定依据落 workflow-state 备查）；禁止跳过图谱复核仅凭 PRD 文本定级。

**准入门槛（四条全满足，任一不满足即回标准 8 阶段，禁止凑数）：**
1. **单域**：改动落点 ≤1 个领域（`domain-index.md` 已列领域），无跨域写操作
2. **小文件**：预估实际改动文件 ≤10（含新增+修改，不含 openspec 工件）
3. **无契约变更**：无新增 Controller/对外 API、无表结构变更（DDL）、无 MQ 契约变更、无事务边界新增
4. **无状态机/无幂等新场景**：无状态流转规则、无新分布式锁、无新幂等场景

**3 步流程：**
- **步骤1** = 阶段一+阶段二 精简合并（**语义闭环六问 Gate 不省**），产出 prd/domain/explore + 用户确认
- **步骤2** = 主 agent 直做 design/tasks + 编码 + JUnit（测试义务由 DoD JUnit 承担，不产出独立 test-cases.md）+ 派 reviewer 审查（阻塞级问题整改后复审，最多 2 轮熔断，不接 /goal）
- **步骤3** = 阶段八精简归档（单 reviewer 单轮；workflow-archive 前置检查与 workflow-reviewer 核验清单按 `mode: lightweight` 豁免 test-cases / review-pack / test-mapping，以步骤1/2 产物 + `mvn test` 为准）
- `workflow-state.md` 标注 `mode: lightweight` + 准入判定四条结论备查
- 用户 gate 保留在步骤1 结束的需求确认，不重复设卡

---

# 统一状态更新规则

每个阶段完成后，必须更新 `openspec/changes/{变更ID}/workflow-state.md`：

1. 将当前阶段标记为完成。
2. 记录本阶段产物路径。
3. 将 `Current Stage` 推进到下一阶段。
4. 如果阶段阻塞，保持当前阶段不变，并写明阻塞原因。
5. 所有阶段产物优先落在当前 change 目录。
6. 阶段一（图谱）、三、四、六、七完成后，必须校验产物是否由 subagent 生成（非主 agent 直接写入），未通过校验则阶段标记为阻塞，回退重做；阶段八的 `archive-report.md` 同样必须由 subagent（workflow-reviewer）生成，主 agent 仅执行编排与 state 更新。阶段五例外（`review-pack.md` 由主 agent 直写——用户交互产物，主 agent 拥有对话上下文）。轻量通道步骤2 例外（主 agent 直做，需 `mode: lightweight` 备查）。阶段七规模豁免出口例外（≤10 文件豁免双评，无评审产物，state 标注 `skipped(N)` + `review-scale.txt` 为凭）。
7. **审查/验证不通过触发回退时**：将被回退阶段（及下游所有已执行阶段）的 `[x]` 回拨为 `[ ]`，`Current Stage` 回拨到重做起点，旧产物重命名为 `{name}.redo.md`（保留一次历史）。重做通过后产物正常覆盖。回退穿越阶段五时标准回拨已天然覆盖冻结失效语义（基线产物已变，重做推进后须重新过用户评审），无附加标志需要手动清理。
8. **前置检查只认 `[x]` 状态**：被回退阶段的旧产物即使存在，也因状态为 `[ ]` 触发重做，杜绝「产物还在→跳过→拿旧产物再审→再次不通过」的死循环。全部阶段统一单条件判定（含阶段六对阶段五的检查），无附加 gate 标志。
9. **Telemetry 事件**：各阶段 stage-start/stage-end、回退 retry、熔断 circuit-break 必须写入 `openspec/changes/{变更ID}/telemetry.jsonl`（调用 `.claude/scripts/telemetry-log.js`）；归档统计（阶段八 S1）优先从该文件取阶段耗时与人工干预次数。

**推荐最小结构：**

```md
# Workflow State

## Change
- id: {变更ID}
- mode: standard | lightweight（轻量通道附准入判定四条结论 + Tier Gate 依据：PRD 触点初判 / pipeline.md 复核 / 用户确认）

## Current Stage
- stage-1-intake-domain

## Artifacts
- prd: prd.md
- domain: pending
- pipeline: pending
- explore: pending
- design: pending
- api: pending
- tasks: pending
- sql: pending
- design-review: pending
- test-cases: pending
- test-case-review: pending
- review-pack: pending
- test-mapping: pending
<!-- code-review / arch-review：阶段七 ≤10 文件规模豁免时记 skipped(N)，N 为排除测试/md/openspec 后的改动文件数 -->
- code-review: pending
- arch-review: pending
- archive-report: pending
- telemetry: telemetry.jsonl

## Stage Status
<!-- [x] = 该阶段已 PASS（前置检查唯一跳过判据）；回退时必须回拨为 [ ]，禁止靠产物文件是否存在判断 -->
- [ ] stage-1-intake-domain
- [ ] stage-2-explore
- [ ] stage-3-design-loop
- [ ] stage-4-test-design-loop
- [ ] stage-5-user-review-gate
- [ ] stage-6-impl-test-loop
- [ ] stage-7-dual-review
- [ ] stage-8-archive
```
