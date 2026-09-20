# Code Review: 20260920-social-timeline-mvp

## 概览
- 审查文件数：89 主代码（5 修改 + 84 新增；另 20 个测试文件仅编译性证据不深审，见截断披露）/ diff 行数：14,832 / diff-hash：`da13eabc4d08`
- 基线：HEAD（fdb6b58），全部改动为工作区未提交变更；untracked 文件以文件本体为 diff 全量
- 覆盖：3 batch（数据层+契约层 / 业务层 / 接入层+静态页）× (L1,L2) + L3×1 + L4×1 = 8 finder（达 ≤8 硬顶）；3 条 HIGH 全部经 haiku verifier 证伪复核
- 截断披露：① 测试代码 20 文件（~5k 行）按 源码>配置>测试 优先级 + 测试边界不纳入 L1/L2 深审，编译性有证据（mvn test 143/143 全绿 ×2 连跑一致）；② `.playwright-mcp/` 为运行时产物排除；③ 各 batch 行数超 2k 指引值（3-batch 硬顶优先，finder 限定只看本批文件）
- BLOCKER / HIGH / MEDIUM / LOW：**0 / 1 / 9 / 5**（HIGH 经复核；MEDIUM 中 #3 经复核降级、其余 8 条未复核；LOW 均未复核）
- 检查清单来源：内置 lens（REVIEW.md 不存在）+ .claude/rules/{java,mysql}-guide.md + lessons 三文档（空模板，L3 基准回落到两份 rules）
- 结论：**PASS**（无 CONFIRMED BLOCKER；Gate 语义仅 BLOCKER 触发 FAIL）

## BLOCKER（CONFIRMED，必须修复）
（无）

## HIGH（CONFIRMED）
1. **moments-service/src/main/java/com/funny/moments/service/social/impl/FriendTagServiceImpl.java:97/117/141/146/172/182** — 标签全部 6 处写路径裸调 `friendTagCacheManager.evictAll()`（Redis INCR）无异常降级，违背 C12「Redis 弱依赖」定稿
   - lens：L1∪L2∪L3（三 finder 独立命中）/ 类型：逻辑+规范
   - 证据：Redis 不可用/抖动时调用 createTag/updateTag/bindUser/unbindUser（直调）或 deleteTag（afterCommit 回调内）→ `evictAll()` 内裸 `incr` 抛异常上抛 → DB 已提交但接口 5xx：createTag 重试命中伪 1005（重名误报）、deleteTag 重试命中伪 1004；且版本号未 INCR，旧标签缓存 stale 至 TTL 1h，`canView` tagHit 仍用旧集合
   - 复核：82/100 — 6 处裸调属实；同文件族 3 处降级示范（FriendTagCacheManager.getTagIds L89、FriendIdsCacheManager.get L84、MockDataServiceImpl.evictCaches L255-262 均有 catch+warn）证明属遗漏而非设计取舍；design §9 L820 明确承诺缓存组件 catch→warn→回源且无写路径豁免条款
   - 建议：在 `FriendTagCacheManager.evictAll()` 内部收口 try-catch（warn 后继续，stale 由 TTL 1h 自然上界兜底）——一处修复覆盖全部 6 个调用点，成本极低；**非 Gate 阻塞，建议随下轮修复或并入归档前整改**

## MEDIUM / LOW（未复核，finder 初判；#3 除外）
- **MEDIUM #3（经复核 CONFIRMED 80 分，由 HIGH 降级）** PostController.java:47（+CurrentUserResolver.java:44 同族）— C 端写接口无签名认证、身份靠客户端可控 viewerId 可任意冒充。复核结论：事实链全部属实，但 C10 豁免留痕充分（prd C10 / design §7.3 显式接受该攻击向量 / api.md §1.2 / design-review-1 同向量已裁定建议级闭环），实现与冻结基线一致；真实缺口是**阶段五为 AI 代行评审、C10 尚无用户追认**（review-pack §五.1 在案）。处置走用户追认通道：用户否决 C10 时恢复 HIGH 回炉设计，否则按实验室形态接受
- MEDIUM #4（未复核） CurrentUserResolver.java:44 — 同 #3 身份机制的水平越权面（design §6.1 A3/B3 文档化，随 #3 一并追认）
- MEDIUM #5（未复核） schema/social_timeline.sql:28 — `uniq_user_friend` 唯一索引未含 is_del，与软删互斥：软删残留 pair 永久占位，同 pair 重建触发整批回滚（当前 MVP 无删好友端点，mock 全新 ID 段暂不可触发；L3 finder 亦因不可触发未列——两 finder 口径分歧，采纳 L2 保守记录）
- MEDIUM #6（未复核） FriendTagMapper.xml:71 等 6 处 foreach IN 无空集合 XML 防护，纯靠 Service 前置拦截（FriendTagMapper 71/85、FriendTagRelationMapper 32、UserMapper 37、PostImageMapper 48、PostMapper 49）；常态空集路径（无标签用户/无好友用户）一旦漏拦截即 `IN ()` BadSqlGrammar 500
- MEDIUM #7（未复核） MockDataServiceImpl.java:377 — generateTags 的 tagId 回读时序错位：ownerBuffer 满 500 回读时标签 buffer 仍有未落库行，映射永久缺失 → 绑定/type3-4 可见性名单静默缺失且接口不报错（userCount>500 触发，默认 100 不触发；上限 10 万时可触发）
- MEDIUM #8（未复核） UserServiceImpl.java:60 — listUsers pageNo 无上界，`(no-1)*size` int 溢出为负 → LIMIT 负偏移 SQL 500；深分页全表扫描
- MEDIUM #9（未复核） LocalImageServiceImpl.java:81 — upload 仅校验扩展名+大小，不校验内容（魔数/Content-Type）即落盘对外静态目录 /images/
- MEDIUM #10（未复核） PostMapper.xml:31/48/53 — SQL 硬编码 `status=1` / `visibility_type!=2` 魔法数字，PostStatusEnum/VisibilityTypeEnum 未消费且无注释，后续枚举扩展时口径静默分叉
- MEDIUM #11（未复核） schema/social_timeline.sql:68 — post 表裸用 `status`（TINYINT(2)），违反 mysql-guide `xx_status` 命名；同表 visibility_type 已规范，status 为唯一裸名（绿地窗口改名成本最低）
- LOW #12（未复核） UserDO.java:21 — 8 DO 主键 Long 与 java-guide §2.4「Integer」冲突，但与 mysql-guide bigint 模板自洽（两份规范互斥，建议项目定夺口径；DO 注释已自述为有意决策）
- LOW #13（未复核） schema:42 等 — idx_user/idx_tag/idx_friend/idx_post 索引名截断字段后缀，同文件命名口径不一
- LOW #14（未复核） FriendTagRelationMapper.xml:36 — GROUP BY 含 tag_id 与 Javadoc 去重口径不符，并发同名标签输出重复名
- LOW #15（未复核） PostServiceImpl.java:103 / FeedServiceImpl.java:87 — createPost ~103 行、getFeed ~104 行超 §12 方法体 ≤50 行建议
- LOW #16（未复核） app.js:57 — getQueryParam 畸形百分号编码抛未捕获 URIError，页面初始化中断（自伤型，无跨用户影响）

## 复核未过（REJECTED，仅供参考，不触发 Gate）
1. **MockDataController.java:37** 破坏性接口（clear=true 软删 8 表 + MAX_USER_COUNT=100000）无鉴权/@Profile — 50 分：代码事实全部属实，但免鉴权系冻结基线显式裁决（api.md 接口 11 权限列「否」、design §3「实验室工具接口」、§7.3 对抗审查+评审后显式接受、§11 上线仅本地实验室）；SchemaInitRunner 的 @Profile("dev") 是 D1/A6 组件级指定而非项目级守卫标准。残留价值为建议级加固：**加一行 `@Profile("dev")` 或服务端确认 token 可消除与 SchemaInitRunner 的口径不一致**（与 #3 同走追认通道）

## 结转（增量轮：上轮 CONFIRMED 且仍未修复）
（首轮，无结转）

## 建议改进 / 做得好的地方
- 建议改进（按性价比排序）：① evictAll 收口 catch（#1，一处修复六点收益）；② IN 空集合 XML 防护或统一断言（#6）；③ MockDataController 加 @Profile("dev")（REJECT 项残留建议）；④ pageNo 上界 + 偏移量 long 运算（#8）；⑤ 上传文件魔数校验（#9）
- 做得好的地方：mapper XML 全量 `#{}` 占位 + `is_del=0` + `&lt;` 转义 + UPDATE 带 WHERE + xmllint 全通过；契约层包装类型/In-Out 命名/Date→Str 齐全；DO 手写 setter+trim 无 Lombok；枚举三件套与 DDL COMMENT 对齐；事务边界干净（createPost/writePostBatch 事务内无 Redis、deleteTag afterCommit 才 evict）；前端全量 innerHTML 插值过 `App.esc()`，静态资源双重防路径穿越；Feed 双锚点游标分页与可见性四视角实现与冻结基线逐项一致（L4 零偏差）；toMap 均带 merge function、无 null 返回、随机数 ThreadLocalRandom 等规范细节到位

## 摘要（供主 Agent 汇总）
首轮全量评审（8 finder + 3 verifier）：89 文件 14.8k 行，PASS——无 CONFIRMED BLOCKER。1 条 CONFIRMED HIGH（标签写路径 evictAll 无 Redis 降级，违背 C12，一处 catch 收口可修，建议归档前整改）；2 条安全类（写接口无签名/viewerId 可冒充、mock 清库接口无守卫）经复核因 C10/实验室豁免留痕充分分别降级 MEDIUM 与 REJECT，共同残留缺口是阶段五 AI 代行决策待用户追认（review-pack §五.1）；另有 8 条未复核 MEDIUM（IN 空集、pageNo 溢出、mock 回读时序、上传内容校验、魔法数字、status 命名等）+ 5 LOW。实现与冻结基线一致性由 L4 核查零偏差，数据层/前端规范面质量高。

PASS 20260920-social-timeline-mvp
