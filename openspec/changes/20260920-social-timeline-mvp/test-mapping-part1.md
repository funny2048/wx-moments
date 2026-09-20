# Test Mapping (Part 1): 20260920-social-timeline-mvp — 入口 1-11（TC001-TC082）

> tester 产出：批次1 用例 → 测试类/方法映射。测试基座：`moments-web/src/test/java`，dev profile
> `@SpringBootTest + @AutoConfigureMockMvc + @Transactional`（SchemaInitRunner 自动建表；MockMvc 同线程
> 参与测试事务，触发型一律回滚不留脏数据）。红绿判定由主 agent 执行 `mvn test`，本表"结果"列为待跑。
>
> **基线 A id 口径**：dev 库已有 35 用户存量（自增 id 低段），基线固定使用 9 亿段高位 id
> （`SocialTestSupport` 常量：张三=900000001…钱七=900000005、标签 900000011-13、帖子 900000098-104），
> 与用例字面 id（1/2/3/4/5、11/12/13、98-104）语义一一对应；"不存在 id"统一以 `max(id)+100000` 替代字面 999999。
>
> **并发/缓存用例回滚策略**：并发用例（TC034/044/053/063/071/082①）`@Transactional` 管不到其他线程，
> 方法级 `Propagation.NOT_SUPPORTED` 真实提交 + `@AfterEach` 按业务主键/造数 id 区间清理（MessageConsumeAsyncTests 口径）；
> 各类 `@AfterEach` 追加 evictAll()（INCR fver/ftagver）使测试写入的 Redis key 失效自清。
>
> **类型口径**：触发=自造数据+事务回滚+查库硬断言；观测=分布/容差软校验不计红灯（违规 warn+计数+汇总）。

| 用例编号 | 入口 | 测试类#方法 | 类型 | 结果 | 备注 |
|---|---|---|---|---|---|
| TC001 | 1 用户列表 | UserApiTests#listUsersDefaultPage | 触发 | 待跑 | 前置"基线B(≥100用户)"适配为自造25用户+存量合同断言（total=countAll 硬等值） |
| TC002 | 1 | UserApiTests#listUsersPageSizeBoundaryAndEmptyPage | 触发 | 待跑 | 1/500 边界 + 空页合法；**回炉1 修复**：users 数组改取 `$.data.users`（UserPageOut 为 {total,users} 对象信封，原 dataListOf 裸取 $.data 抛 CCE） |
| TC003 | 1 | UserApiTests#listUsersInvalidParams | 触发 | 待跑 | 6 无效类合并同码 1001（NB1 承载断言） |
| TC004 | 1 | UserApiTests#listUsersEmptyResultStructure | 触发 | 待跑 | **环境适配**：前置"8表无业务数据"与共享 dev 存量互斥（禁清库），以页码超界空页承载空集契约（code=0/users=[]/禁null）；total=0 字面断言需空表，留痕 |
| TC005 | 2 用户详情 | UserApiTests#getUserDetail | 触发 | 待跑 | 全字段断言（含造数可控时间） |
| TC006 | 2 | UserApiTests#getUserInvalidUserId | 触发 | 待跑 | 0/-1/abc 合并 1001 |
| TC007 | 2 | UserApiTests#getUserNotFound | 触发 | 待跑 | 不存在 id 以 max+100000 替代 999999（防数据增长撞id） |
| TC008 | 2 | UserApiTests#getUserSoftDeleted | 触发 | 待跑 | 事务内 UPDATE is_del=1 + 1002 断言 + 软删行查库核对 |
| TC009 | 3 好友列表 | FriendshipApiTests#listFriendsWithTagNames | 触发 | 待跑 | tagNames 组装全量断言（含无标签好友空数组） |
| TC010 | 3 | FriendshipApiTests#listFriendsByCookie | 触发 | 待跑 | Cookie mockUserId 视角 |
| TC011 | 3 | FriendshipApiTests#viewerParamOverridesCookie | 触发 | 待跑 | query 优先于 Cookie（A3） |
| TC012 | 3 | FriendshipApiTests#viewerMissing | 触发 | 待跑 | 1003 |
| TC013 | 3 | FriendshipApiTests#viewerParamInvalidNoFallback | 触发 | 待跑 | B3 四类非法合并，合法 Cookie 在场不回退 |
| TC014 | 3 | FriendshipApiTests#cookieDirtyValueTreatedAsMissing | 触发 | 待跑 | Cookie 脏值宽松→1003 |
| TC015 | 3 | FriendshipApiTests#listFriendsFilteredByTag | 触发 | 待跑 | tagId 命中 + tagNames 全量不缩水（建议6） |
| TC016 | 3 | FriendshipApiTests#listFriendsFilterNoBinding | 触发 | 待跑 | B4 补齐：零绑定标签空集（创建走业务接口） |
| TC017 | 3 | FriendshipApiTests#tagIdInvalid | 触发 | 待跑 | 0/abc → 1001 |
| TC018 | 3 | FriendshipApiTests#tagIdNotFoundOrNotOwner | 触发 | 待跑 | 1004 不存在+非归属合并；不存在 id 以 max+100000 替代 |
| TC019 | 3 | FriendshipApiTests#listFriendsEmptyView | 触发 | 待跑 | 钱七空集 |
| TC020 | 4 好友详情 | FriendshipApiTests#getFriendDetailWithTags | 触发 | 待跑 | 含 tagNames 顺序断言 |
| TC021 | 4 | FriendshipApiTests#getFriendDetailInvalidUserId | 触发 | 待跑 | 0/abc → 1001 |
| TC022 | 4 | FriendshipApiTests#getFriendDetailUserNotFound | 触发 | 待跑 | 1002 |
| TC023 | 4 | FriendshipApiTests#getFriendDetailNonFriendUser | 触发 | 待跑 | 非好友读语义（tagNames=[]） |
| TC024 | 4 | FriendshipApiTests#getFriendDetailViewerMissing | 触发 | 待跑 | 1003 横切 |
| TC025 | 5 标签列表 | FriendTagListApiTests#listTagsWithFriendCount | 触发 | 待跑 | COUNT(DISTINCT) 口径 |
| TC026 | 5 | FriendTagListApiTests#duplicatedBindingDisplayDedup | 触发 | 待跑 | 直插重复绑定模拟并发残留，friendCount/tagNames 去重兜底 |
| TC027 | 5 | FriendTagListApiTests#listTagsEmptyUser | 触发 | 待跑 | 空集 |
| TC028 | 5 | FriendTagListApiTests#listTagsViewerInvalid | 触发 | 待跑 | 1003/1001 横切 |
| TC029 | 6 创建标签 | TagCreateApiTests#createTagTrimAndSixteenCharBound | 触发 | 待跑 | trim 语义 + 恰16字上界 + DB 落库核对 |
| TC030 | 6 | TagCreateApiTests#createTagInvalidName | 触发 | 待跑 | null/""/纯空格/17字 ×4 合并 + DB 0 新增 |
| TC031 | 6 | TagCreateApiTests#createTagDuplicatedName | 触发 | 待跑 | 1005 + DB 查重核对 |
| TC032 | 6 | TagCreateApiTests#createTagAfterSoftDeleted | 触发 | 待跑 | 前置自含（建议7）：创建→接口删除→重建；新旧两行 is_del 断言 |
| TC033 | 6 | TagCreateApiTests#createTagRepeatRequest | 触发 | 待跑 | 幂等-重复：0/1005 + DB 恰1行 |
| TC034 | 6 | TagCreateApiTests#createTagConcurrent | 触发 | 待跑 | NOT_SUPPORTED 真实提交；可接受路径 {0,1005}/{0,0} + 终态 ≤2 行；@AfterEach 清理 |
| TC035 | 6 | TagCreateApiTests#createTagFailThenRetry | 触发 | 待跑 | 幂等-重试三段 |
| TC036 | 6 | TagCreateApiTests#createTagViewerMissing | 触发 | 待跑 | 1003 + DB 0 行 |
| TC037 | 7 修改标签 | TagUpdateApiTests#updateTagNameMain | 触发 | 待跑 | 最小更新实体：created_stime 不变核对 |
| TC038 | 7 | TagUpdateApiTests#updateTagInvalidParams | 触发 | 待跑 | tagId+tagName 无效合并 1001 |
| TC039 | 7 | TagUpdateApiTests#updateTagNotFound | 触发 | 待跑 | 1004（max+100000 替代 999999） |
| TC040 | 7 | TagUpdateApiTests#updateTagNotOwner | 触发 | 待跑 | 1006 越权 + DB 不变核对 |
| TC041 | 7 | TagUpdateApiTests#updateTagNameConflicts | 触发 | 待跑 | 1005 + DB 不变 |
| TC042 | 7 | TagUpdateApiTests#updateTagToOwnName | 触发 | 待跑 | 查重排除自身 |
| TC043 | 7 | TagUpdateApiTests#updateTagRepeatRequest | 触发 | 待跑 | 幂等-重复：重放 2 次终值唯一 |
| TC044 | 7 | TagUpdateApiTests#updateTagConcurrent | 触发 | 待跑 | NOT_SUPPORTED；后写覆盖终值 ∈{名A,名B} |
| TC045 | 7 | TagUpdateApiTests#updateTagFailThenRetry | 触发 | 待跑 | 1005→修正重试 0 |
| TC046 | 8 删除标签 | TagDeleteApiTests#deleteTagCascadeSoftDelete | 触发 | 待跑 | C13 两表级联 + post_visibility_tag 不级联（is_del 仍0）核对 |
| TC047 | 8 | TagDeleteApiTests#deleteTagPrivacyNaturalExpiry | 触发 | 待跑 | 规则⑥全语义（1011/0/0 三路），前置删标签动作自含 |
| TC048 | 8 | TagDeleteApiTests#deleteTagInvalidId | 触发 | 待跑 | 0/abc → 1001 |
| TC049 | 8 | TagDeleteApiTests#deleteTagNotFound | 触发 | 待跑 | 1004（max+100000 替代 999999） |
| TC050 | 8 | TagDeleteApiTests#deleteTagNotOwner | 触发 | 待跑 | 1006 + DB 不变 |
| TC051 | 8 | TagDeleteApiTests#deleteTagCacheConsistency | 触发 | 待跑 | 断言通道=接口回读；tagNames 走 DB 直查（deleteTag 的 INCR 走 afterCommit，回滚事务不触发，不影响本通道） |
| TC052 | 8 | TagDeleteApiTests#deleteTagRepeatRequest | 触发 | 待跑 | 已删态再删=1004 同分支合并（建议1/建议5） |
| TC053 | 8 | TagDeleteApiTests#deleteTagConcurrent | 触发 | 待跑 | NOT_SUPPORTED；{0,1004}/{0,0} + 终态 is_del=1 |
| TC054 | 8 | TagDeleteApiTests#deleteTagFailThenRetry | 触发 | 待跑 | 1004→重试 0 |
| TC055 | 9 绑定 | TagBindApiTests#bindUserToTag | 触发 | 待跑 | DB +1 行核对 |
| TC056 | 9 | TagBindApiTests#bindInvalidParams | 触发 | 待跑 | 路径参数无效 ×4 合并 1001 |
| TC057 | 9 | TagBindApiTests#bindTagNotFoundOrNotOwner | 触发 | 待跑 | 1004/1006 合并 + DB 0 行 |
| TC058 | 9 | TagBindApiTests#bindUserNotFound | 触发 | 待跑 | **按裁决断言 1002**（用户存在性先于好友关系，B3） |
| TC059 | 9 | TagBindApiTests#bindNonFriendUser | 触发 | 待跑 | 1007 主路径 + DB 0 行 |
| TC060 | 9 | TagBindApiTests#bindUserRepeat | 触发 | 待跑 | 1008；前置自含（本方法先绑定成功再重复，不依赖 TC055 执行顺序） |
| TC061 | 9 | TagBindApiTests#bindUserCacheConsistency | 触发 | 待跑 | **前置适配**：基线 A 中 (12,3) 已绑定（TC065 消费该态），与本用例"未绑定"前置冲突；前置自含为先接口解绑再绑定（不依赖用例间顺序），断言通道=接口回读 |
| TC062 | 9 | TagBindApiTests#bindUserRepeatRequest | 触发 | 待跑 | 幂等-重复：0/1008 + DB 恰1行 |
| TC063 | 9 | TagBindApiTests#bindUserConcurrent | 触发 | 待跑 | NOT_SUPPORTED；{0,1008}/{0,0} + 展示侧 tagNames 去重 + friendCount 不虚高；**回炉1 修复**：friendCount 断言由写死 1 改为与 DB `COUNT(DISTINCT friend_user_id)` 硬等值（基线王五已绑"朋友"，去重正确值=2，原断言漏算基线绑定）；DB 侧保留"允许重复行 ≥1"留痕口径（R2-建议4） |
| TC064 | 9 | TagBindApiTests#bindUserFailThenRetry | 触发 | 待跑 | SQL 补对称好友关系（C11）后重试 0；**回炉1 修复**：补关系由 JdbcTemplate 改为 Mapper batchInsert 直插（同为 SQL 层造数不走业务接口）——诊断结论见下 |
| TC065 | 10 解绑 | TagUnbindApiTests#unbindUserFromTag | 触发 | 待跑 | is_del=1 软删行保留核对 |
| TC066 | 10 | TagUnbindApiTests#unbindIdempotentWhenNotBound | 触发 | 待跑 | 幂等语义核心（未绑定/已解绑再解绑均 0） |
| TC067 | 10 | TagUnbindApiTests#rebindAfterUnbind | 触发 | 待跑 | 软删不占键：新旧行 id 不同断言 |
| TC068 | 10 | TagUnbindApiTests#unbindInvalidParamsAndOwnership | 触发 | 待跑 | 1001/1004/1006 合并 |
| TC069 | 10 | TagUnbindApiTests#unbindUserCacheConsistency | 触发 | 待跑 | 预热→解绑→回读即时不含 |
| TC070 | 10 | TagUnbindApiTests#unbindRepeatRequest | 触发 | 待跑 | 二连发均 0 + DB 单次效果 |
| TC071 | 10 | TagUnbindApiTests#unbindConcurrent | 触发 | 待跑 | NOT_SUPPORTED；均 0 + 终态 is_del=1 |
| TC072 | 10 | TagUnbindApiTests#unbindFailThenRetry | 触发 | 待跑 | 1006→修正 0（幂等成功口径） |
| TC073 | 11 模拟数据 | MockDataApiTests#generateDefaultConfig | 触发 | 待跑 | 前置"空库"适配为增量断言（=N0+100，语义更强）；统计出参全核对 |
| TC074 | 11 | MockDataApiTests#generateFriendshipSymmetry | 观测 | 待跑 | 对称/自环/防重三断言**硬**（本批 id>快照隔离存量）；记录数 ≈1000 ±15% 观测软校验，<850 硬失败转人工复核 |
| TC075 | 11 | MockDataApiTests#generateTagDistributionObserve | 观测 | 待跑 | ±5pp 软校验不计红灯；**分母口径备注**：绑定落库后主/次标签不可区分，分母=响应 bindingCount（含30%次标签，与权重期望收敛一致；用例字面"主标签绑定数"无法事后从 DB 识别）；多标签样本>0 概率性软校验 |
| TC076 | 11 | MockDataApiTests#generateWithClear | 触发 | 待跑 | clear 在测试事务内执行并回滚（dev 存量完好）；8 表逐表 is_del 断言 + fver INCR 直读 Redis 断言 + 旧视角回读 |
| TC077 | 11 | MockDataApiTests#generateAppendMode | 触发 | 待跑 | C15 追加：旧 N 保留 + 10 |
| TC078 | 11 | MockDataApiTests#generateConfigOutOfRange | 触发 | 待跑 | 1040×8 笔（4字段×2越界）+ DB 0 写入 |
| TC079 | 11 | MockDataApiTests#generatePostsTypeDistribution | 触发 | 待跑 | post +50 与可见性两表唯一性**硬**；type 分布分母=50 ±15pp 观测软校验不计红灯 |
| TC080 | 11 | MockDataApiTests#generateBodyTypeInvalid | 触发 | 待跑 | R3 承载：body 类型非法→1001 非 100 |
| TC081 | 11 | MockDataApiTests#generateRepeatAppend | 触发 | 待跑 | 追加幂等口径：二连发 +100 |
| TC082 | 11 | MockDataApiTests#generateConcurrent | 触发 | 待跑 | **①并发分支自动化**（NOT_SUPPORTED + 造数 id 区间清理，finally 兜底）；**②中断恢复（kill -9 + clear 重跑）为用例声明的手工验证项留痕，不设自动化方法** |

## 汇总

- 用例总数（本批次）：82（TC001-TC082，入口 1-11）| 已映射：82 | 缺口：0
- 测试方法：82 个（TC082 一个方法覆盖自动化分支①；分支② 手工留痕）
- 测试类：9 个 + 公用支撑 1 个（`support/SocialTestSupport.java`）
- 编译验证：`mvn test-compile -pl moments-web -am` 通过
- 结果列"待跑"：红绿循环（mvn test 连跑两遍）由主 agent 执行，本表结果列以主 agent 判定回填为准

## 环境适配与口径留痕（供主 agent 复核）

1. **TC004 空库前置不可达**：共享 dev 库有存量数据且禁止清库/清表，空集契约以"页码超界空页"承载（code=0/users=[]/禁null）；total=0 字面断言需空表环境。若需原语义，建议独立空库 profile 或接受留痕。
2. **基线 id 高位段**：用例字面 id 1-5/11-13/98-104 换算为 9 亿段（防 dev 自增冲突）；"不存在 id"以 max(id)+100000 替代 999999。语义不变。
3. **TC061 前置冲突**：基线 A (12,3) 已绑定与用例"绑定 (12,3)"前置矛盾（TC065 需要已绑定态），以方法内先解绑再绑定实现前置自含；未改用例断言。
4. **TC058**：按 B3 裁决断言 1002（存在性先于好友关系），与冻结用例一致。
5. **TC075 分母**：软校验项，分母采用 bindingCount（详见行内备注）；若主 agent 要求严格按字面"主标签绑定数"分母，需回阶段四澄清主/次标签落库标识（当前 schema 无该字段，无法区分）。
6. **并发用例清理**：TC034 等以 NOT_SUPPORTED + @AfterEach 按业务主键（基线高位 id 段）/造数 id 区间（TC082①，id>各表快照 max）物理 DELETE 清理——仅触碰本测试自造行，等价 @Sql AFTER 口径。
7. **TC074 结构断言口径**：对称/自环/防重对"本批生成行"（id>快照）硬断言，避免 dev 存量历史数据干扰；全局性唯一键由 DB uniq 兜底。
8. **缓存卫生**：各类 @AfterEach evictAll()（INCR fver/ftagver）使测试期间写入的 Redis 缓存 key 全部失效（TTL 1h 自然过期兜底），防止回滚事务的未提交数据残留在缓存影响后续用例（含批次2 入口12-17 的 canView tagHit 读取）。

## 回炉修复记录（红绿第 1 轮，测试码缺陷 3 条，均未改业务码/用例语义）

1. **TC064 诊断结论**：红灯根因 **不是** FriendIdsCacheManager Redis 缓存——bindUser 的好友校验走
   `FriendshipMapper.existsFriendship` 直查 SQL（FriendTagServiceImpl L158），不经缓存；真因是测试事务内的
   **MyBatis 会话级一级缓存**：首次 bind 的 existsFriendship(张三,钱七)=0 被同事务 SqlSession 缓存，
   测试用 JdbcTemplate 直插 friendship 不经 MyBatis → 不清会话缓存 → 重试的同参 SELECT 命中旧值 0 → 误报 1007。
   修复：补关系改经 `friendshipMapper.batchInsert` 直插（仍为 SQL 层造数，不走 bindUser 业务接口），
   Mapper 写语句自动清空会话缓存，重试读到新关系。业务码 bindUser 校验逻辑（方向/条件）复核无误，无需转 executor。
2. **TC063**：friendCount 断言原写死 1，漏算基线 A 中"朋友"标签已绑定王五（去重正确值=2）——为测试断言 bug
   而非实现违规；改为与 DB `COUNT(DISTINCT friend_user_id)` 硬等值（即冻结口径"不虚高"本体），断言强度不降。
   DB 侧按 R2-建议4 留痕口径保留"并发窗口允许重复行（∈[1,2]）"断言。
3. **TC002**：UserPageOut 的 data 为 `{total, users}` 对象信封，原 dataListOf 裸取 `$.data` 强转 List 抛
   ClassCastException；新增 `SocialTestSupport.userListOf` 取 `$.data.users`，断言不变。
