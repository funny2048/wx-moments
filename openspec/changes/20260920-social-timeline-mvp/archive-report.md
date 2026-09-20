# Archive Report: 20260920-social-timeline-mvp

> 核验人：workflow-reviewer（mode: archive，独立只读核验）
> 核验时间：2026-09-21（阶段八）
> 核验方式：产物逐项比对 + 测试独立复跑 + 规范抽样扫描 + 双评结论与整改落地溯源

## 结论
- **PASS**

## 归档清单

- [x] **所有测试通过** — 独立复跑 `mvn test -pl moments-web -am`：**143/143 全绿，BUILD SUCCESS，EXIT=0**（/tmp/mvn-archive-verify.log）。另有阶段六出口双跑证据 /tmp/mvn-final-a.log、/tmp/mvn-final-b.log（143/143 ×2 连跑一致）+ code-review 整改后复绿 /tmp/mvn-post-fix.log（143/143），四份日志均在案。
- [x] **架构审查通过** — arch-review.md 结论 **PASS：严重 0 / BLOCKER 0**，9 项范围全 PASS；建议 3 条均为文档级，建议1（Stage 6 裁决4/8 补录 design §14）已核实执行（design.md L924-925 S7-补录1/2），建议2/3 为基线分歧留痕不阻塞。code-review.md 同为 **PASS：CONFIRMED BLOCKER 0**；唯一 CONFIRMED HIGH #1（FriendTagCacheManager.evictAll 无 Redis 降级）已核实整改落地（evictAll L105-112 内部 try-catch 收口，warn 后靠 TTL 兜底），整改后 143/143 复绿。规模门控：review-scale.txt 计 89 文件 >10 → 全量双评，非豁免。
- [x] **代码符合项目规范**（抽样核验，总体合规）— mapper XML 全目录 xmllint 通过；零 `${}`、零注解 SQL、零 QueryWrapper/LambdaQueryWrapper、DO 零 Lombok、select 全带 `is_del=0`（arch-review 16 处覆盖核过）；Controller `String _appId` 首参 29 处全覆盖；DDL 8 表通用 4 字段（id/created_stime/modified_stime/is_del）齐全、COMMENT 61 处、utf8mb4_general_ci 双兼容 5.7/8.0。**已知偏差（双评在案、非本次新发现、不阻塞）**：post 表 `status` 裸名（code-review MEDIUM #11，违反 mysql-guide xx_status）；DO 主键 Long 与 java-guide §2.4 Integer 冲突但与 mysql-guide bigint 模板自洽（LOW #12，两份规范互斥，待项目定夺口径）；PUT/DELETE 端点为冻结设计明确定义与 java-guide GET/POST 偏好的基线分歧（arch-review 建议2）。
- [x] **无遗留 TODO/FIXME** — `grep -rn "TODO|FIXME" --include="*.java"` 全模块仅命中 `moments-web/.../utils/SpringContextUtils.java:11`，经 `git ls-files` 确认为已跟踪既有文件（脚手架 sample 遗留），不在 review-scale.txt 变更清单内 → **本次变更新增遗留 = 0**。
- [x] **文档已更新（change 目录产物齐全）** — prd（含澄清补充 C1-C17 自主决策留痕，L502）/ domain / pipeline / explore（D1-D7 留痕）/ design（§1-§14 含 S7 补录）/ api / tasks / sql / design-review-1~4（3FAIL→熔断代行→PASS）/ test-cases（143 条冻结版）/ test-case-review-1~2（FAIL→PASS）/ review-pack（14 项 AI 代行决策 + 追认清单）/ test-mapping（143/143 映射缺口 0）/ code-review / arch-review / review-scale.txt / page-test-report（13/13）/ page-evidence（11 截图实存）/ telemetry.jsonl，全部存在。workflow-state.md：stage-1~7 全 `[x]`，stage-8 进行中（本次归档）。git 边界合规：未 commit（HEAD=fdb6b58，全部为工作区变更，归用户手动）。

## 效能度量
> 数据来源：telemetry.jsonl + git + tasks.md（经 S1 采集，任务参数 `metrics` 为唯一数据源逐行粘贴；缺失/异常项如实标注，不作为 FAIL 依据）

| 阶段 | 耗时 | 人工干预 | 状态 |
|---|---|---|---|
| 阶段1 需求接入与领域匹配 | 5.6 min | 0 次 | 完成 |
| 阶段2 需求探索澄清 | 0.0 min ※ | 0 次 | 完成 |
| 阶段3 方案设计与对抗验证 | 47.8 min | 3 次 | 完成（4 轮审查，含熔断代行） |
| 阶段4 测试用例设计与对抗 | 26.8 min | 2 次 | 完成 |
| 阶段5 用户评审与冻结 | 0.0 min ※ | 0 次 | 完成（AI 代行 + 留痕） |
| 阶段6 编码+测试红绿循环 | 121.1 min | 4 次 | 完成（143/143 ×2） |
| 阶段7 并行双评 | 13.0 min | 0 次 | 完成（双 PASS，HIGH#1 已整改复绿） |
| 阶段8 验收归档 | 未闭合 | 0 次 | 进行中（本报告产出时点） |

- 代码资产：**89 文件**（review-scale 口径；含 5 修改 + 84 新增）/ +14,832 行（code-review diff 口径）；git 侧已跟踪变更 +99/-0 行 ×5 文件，未跟踪 196 文件（含 openspec 工件/截图/.playwright-mcp 临时产物，代码资产以 89 文件为准）
- AI 任务完成率：**100%**（tasks.md 17/17 ✅，人工介入重构 0）
- 回退与熔断：**retry 7 次 / circuit-break 1 次**（阶段三第 3 轮审查熔断 → AI 代行单点裁决，留痕 workflow-state「Stage 3 熔断与人工裁决记录」）
- 总人工干预事件：12 次

**度量口径备注（如实呈现）**：
- ※ 阶段2/5 显示 0.0 min 系 stage-start/end 同批记录的测量粒度问题（实际工作发生在阶段间隔内，总时长可由相邻阶段推算）
- "干预"为 UserPromptSubmit hook 自动采集事件；本变更用户在目标下达后休息，干预事件主要为夜间 goal 指令与 hook 注入，真实人工等待集中于阶段1 之前

## 遗留事项（⚠️ 首项为晨间追认必看，归档不阻塞）

1. **【待用户追认】全部 AI 代行决策清单**——用户夜间授权自主推进，以下决策均无实时人工确认，晨间逐项/整体追认，否决项按 WORKFLOW 回退规则处理：
   - prd.md「澄清补充」**C1-C17**（17 项需求澄清自主决策，L502 起）
   - explore.md **D1-D7**（7 项探索决策，含 dev 幂等 schema runner、DDL 初始化机制）
   - workflow-state 裁决记录 **裁决1-9**：Stage 4 三项（imageUrls 前缀契约 / bindUser 校验顺序 1002 先于 1007 / mock 容差分母）+ Stage 6 五项（T100 补 1021 异常承载 / tc137 分流安全实质断言 / 编程式 MultipartConfig / pom 追加依赖 / DO 手写）+ **Stage 3 熔断代行**（FriendTagMapper 前移 T020，方案①）
   - review-pack.md 第二节 **14 项冻结基线决策** + §五追认清单（含 C10 实验室免签名豁免——用户否决时该安全向量需回炉设计，code-review MEDIUM #3/#4 在案）
2. **code-review 未整改项**（PASS 语境下建议级，8 MEDIUM 未整改 + 5 LOW）：uniq_user_friend 唯一索引与软删互斥（#5）、foreach IN 空集防护（#6）、mock 回读时序（#7）、pageNo 上界防溢出（#8）、上传魔数校验（#9）、SQL 魔法数字（#10）、status 裸名（#11）、MockDataController 加 @Profile("dev") 加固建议（REJECT 残留）
3. **design §12 生产化前待办**：上传频控（裁决13 拒绝留痕）、发帖 requestId 幂等（依据留痕）、traceId/告警（实验室定位显式不接入）
4. **环境**：本地 MySQL 5.7.44 替代 PRD 8.0（DDL 双兼容，无功能影响）；MySQL MCP 桥接断连（不影响应用数据源）
5. **git 提交**：全部变更未 commit，commit/push/PR 由用户手动执行；openspec 归档（/opsx:archive）在 PR 合入后由用户手动触发
6. 应用保持运行：moments-web.jar（dev，8081），dev 库已恢复 E2E 复核场景

## 摘要（供主 Agent 汇总）
归档核验 PASS：8 阶段证据链闭合——独立复跑 mvn test 143/143 全绿（叠加阶段六双跑与整改复绿共 4 份全绿日志）；双评 PASS（code-review BLOCKER 0，唯一 HIGH evictAll 降级缺失已整改并复验落地；arch-review 严重 0，建议1 补录已执行）；规范抽查总体合规（XML 零 ${}/注解 SQL/QueryWrapper/Lombok，_appId 首参 29 处，DDL 通用字段齐全），已知偏差均系双评在案项非新发现；新增 TODO/FIXME 为 0（唯一命中为 sample 遗留既有文件）；19 类产物齐全，映射缺口 0，git 未 commit 边界合规。核心遗留：AI 代行决策（C1-C17/D1-D7/裁决1-9/review-pack 14 项）待用户晨间追认，code-review 8 条 MEDIUM 建议级未整改项与生产化待办留痕下轮迭代。
