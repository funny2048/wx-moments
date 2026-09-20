# Workflow State

## Change
- id: 20260920-social-timeline-mvp
- mode: standard
- mode 判定依据（Tier Gate）：
  - PRD 触点初判：8 张新表 DDL、约 14+ 个新增 API 端点、4 种可见性状态规则、Cursor 分页、多领域（user/friendship/tag/post/feed）→ 明显不满足准入四条（小文件≤10 ✗、无契约变更 ✗、无状态机 ✗、单域 ✗）
  - pipeline.md 复核：全部链路为新增（绿地），触达节点与初判一致
  - 用户确认：用户夜间授权自主推进（目标指令），AI 代行判定 standard，待追认

## Current Stage
- done（8/8 阶段全部 PASS，2026-09-21 归档）

## Stage 6 编排裁决记录（AI 代行，待追认）
- 裁决8：T100 补 MaxUploadSizeExceededException → 1021（守住 api.md >5MB 契约；>6MB 容器拒绝不再落 code=100）
- 裁决9（tc137 分流）：真实容器取证（4 穿越变体，无文件字节泄露，`..%2f` 被 Tomcat 400）→ R6 安全实质成立，非业务缺陷；断言改为安全实质断言（404/400/或 JSON 错误信封且无文件内容特征），用例安全意图不变。偏差根因：design §7.3 写"resolver→404"，实际 funny 框架 GlobalExceptionAdvice 兜底为 200+code=100——属响应形态技术性偏差，不值得回滚 S4/S5，明早追认
- 裁决4（S6-E4 缺口①）：multipart 上限——application.yml 读取受权限限制，T100 改用**编程式 MultipartConfigElement**（maxFileSize=6MB / maxRequestSize=12MB），规避受限文件且不硬编码散落
- 裁决5（S6-E4 缺口②）：moments-service pom 追加 moments-client 依赖——认可（root dependencyManagement 已管版本，最小追加）
- 裁决6（S6-E2）：CodeGenerator 不使用，DO 手写——认可（冻结基线 T004 明确手写；生成器为 sample 遗留配置与本项目包结构不符，且无法合规取凭证）
- 裁决7（S6-E4 缺口③）：Controller file 参数 required=false + 代码注释——认可（满足 api.md 缺失→1020 契约）

## Stage 4 待澄清裁决记录（AI 代行，待追认，源自 test-case-review-1）
- 裁决1 imageUrls 前缀契约：以 design.md 为准补齐 api.md 声明（^/images/ + 文件名白名单 [A-Za-z0-9._-]，非法 → 1001），四文档对齐
- 裁决2 bindUser 校验顺序：统一「用户存在性 1002 先于好友关系 1007」（存在性先于关系，语义正确）；design §3.2.9 已是该序，仅修 tasks T030 伪代码顺序
- 裁决3 mock 分布容差分母：分母 = 生成的好友关系对总数（非标签绑定数），绝对百分点容差 ±5pp，观测软校验不计红灯
- 处置：裁决1/2 涉及阶段三产物 2 行级勘误，由原设计 subagent 修复并 §14 留痕（不回滚阶段三状态——design.md 真源本已正确，属 tasks/api 派生文档同步缺口）

## Stage 3 熔断与人工裁决记录（AI 代行，待追认）
- 循环A 轨迹：review-1 FAIL(3阻塞) → 回炉1 → review-2 FAIL(2阻塞，回归) → 回炉2 → review-3 FAIL(1阻塞：T020 tagId 过滤分支残留引用 T030 的 FriendTagMapper 方法)
- 熔断处置：按用户目标指令（夜间授权自主决策）代行人工单点裁决——采用方案① FriendTagMapper 最小集（selectByTagId）前移 T020，T030 改追加；与 RelationMapper 已确立的前移模式一致，验收标准/DAG/契约零变动
- 复核策略：修复后仅一次收敛性复核 design-review-4（只验证修复+回归扫描，不做发散重审）；若再出阻塞级则真熔断，阶段三保持 [ ] 阻塞待用户晨间裁决
- 同步裁决 review-3 建议②：补 HttpMessageNotReadableException handler → 1001（与 TypeMismatch handler 同模式，3 行，统一 JSON body 非法入参行为）；建议①③④照常修复

## Artifacts
- prd: prd.md（含澄清补充 C1-C17 自主决策留痕）
- domain: domain.md
- pipeline: pipeline.md（graph-query subagent 产出，降级模式：graphify 图谱不存在，绿地项目）
- explore: explore.md（主 agent 产出；语义闭环六问全部可答落盘，含数据示例；外部服务约束检查 3 项均不适用/已明确；D1-D7 自主决策留痕待追认）
- design: design.md（tech-designer subagent 产出，§14 三轮审查修复记录）
- api: api.md（17 接口四段式）
- tasks: tasks.md（17 task / 9 并行组）
- sql: sql.md（8 表 DDL，兼容 MySQL 5.7.44/8.0）
- design-review: design-review-1.md(FAIL 3) / design-review-2.md(FAIL 2) / design-review-3.md(FAIL 1→熔断+AI代行裁决) / design-review-4.md(PASS，裁决落地复核)
- test-cases: test-cases.md（143 条，含隐私投影速查表/页面级用例映射/回炉修复记录；冻结版）
- test-case-review: test-case-review-1.md(FAIL 4阻塞+9建议+3待澄清) / test-case-review-2.md(PASS，13/13 闭环，5 建议已处置)
- review-pack: review-pack.md（主 agent 直写：14 项决策 AI 代行确认 + 追认清单；冻结基线 design/api/tasks/sql/test-cases 五件生效）
- test-mapping: test-mapping.md（part1 入口1-11 82条 + part2 入口12-17 61条 合并，143/143 映射缺口 0，含三轮红绿修复记录）
## Stage 6 出口证据
- `mvn test -pl moments-web -am`：**143/143 全绿 × 2 连跑一致**（/tmp/mvn-final-a.log、/tmp/mvn-final-b.log，均 BUILD SUCCESS EXIT=0）
- 红绿轨迹：轮1 5红（全测试码缺陷）→ 轮2 1红 → 终跑 13红（系统性基线缺陷根治）→ 终验 0红 ×2；无业务码缺陷回流 executor 的案例（tc137 为响应形态偏差，裁决9）
- 页面级测试：13/13 PASS（page-test-report.md + page-evidence/ 11 截图；隐私矩阵四视角 + 真实容器穿越攻防 4 变体）
- 应用入口：java -jar moments-web/target/moments-web.jar --spring.profiles.active=dev → http://localhost:8081/api/index.html（当前保持运行，dev 库已恢复 E2E 复核场景：作者 900007911 + P0-P5 标记帖）
- code-review: code-review.md（PASS：BLOCKER 0 / HIGH 1 / MEDIUM 9 / LOW 5；CONFIRMED HIGH #1 evictAll 无降级已整改收口，整改后 143/143 复绿 BUILD SUCCESS）
- arch-review: arch-review.md（PASS：严重 0/BLOCKER 0，建议 3 条——建议1 design §14 补录已执行，建议2/3 为基线分歧留痕不阻塞）
- review-scale: review-scale.txt（89 文件 >10 → 全量双评）
- archive-report: archive-report.md（workflow-reviewer subagent 产出，结论 PASS；独立复跑 mvn test 143/143 全绿 /tmp/mvn-archive-verify.log）
- page-test-report: page-test-report.md（13/13 PASS + page-evidence/ 11 截图）
- telemetry: telemetry.jsonl

## Stage Status
<!-- [x] = 该阶段已 PASS；回退时必须回拨为 [ ] -->
- [x] stage-1-intake-domain
- [x] stage-2-explore
- [x] stage-3-design-loop
- [x] stage-4-test-design-loop
- [x] stage-5-user-review-gate
- [x] stage-6-impl-test-loop
- [x] stage-7-dual-review
- [x] stage-8-archive

## Stage 1 备注
- 领域匹配：绿地项目，无已有领域（domain-index.md 不存在，知识抽取未初始化）；全部为新增领域
- graphify 图谱不存在 → graph-query 走降级模式（agent 定义前置检查允许，禁止空产出）
- 用户 gate 代行：领域匹配结论与 Tier Gate 由 AI 自主判定（standard），依据留痕于本文件与 prd.md 澄清补充

## 环境约束（阶段六/七执行前提，2026-09-20 夜验证）
- 本地 MySQL 实为 **5.7.44**（brew mysql@5.7，端口 3306；无 Docker、无 8.0 实例；PRD 的 8.0 为目标环境）→ DDL/SQL 必须双兼容 5.7/8.0（utf8mb4_general_ci、无 8.0 专有语法），已补发给设计 agent
- Redis 6379 ✓（brew 服务在跑）；Maven 3.8.8 + JDK 21 ✓；Node v24 ✓（Playwright 页面级测试可用）
- MySQL MCP 桥接断连（Connection closed）——不影响应用自身数据源；数据级验证以 mvn test + mapper 断言为准
- application*.yml 读取受权限限制（规范亦禁止读连接信息）——启动参数/端口以运行时日志为准
