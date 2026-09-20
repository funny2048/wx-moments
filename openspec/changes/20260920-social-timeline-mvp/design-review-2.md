# design-review-2: 20260920-social-timeline-mvp

> 审查人：adversarial-reviewer（mode: design，第 2 轮复审）
> 审查对象：design.md / api.md / tasks.md / sql.md（review-1 回炉修复后版本，修复映射 design §14）
> 验证基准：design-review-1.md（阻塞 3 + 建议 12）
> 需求基准：prd.md（C1-C17）+ explore.md（六问 + D1-D7 + R1-R11）
> 生成时间：2026-09-20
> 输入说明：knowledge/domain/service-hot.md 不存在（绿地项目），跳过

## 结论

- **不通过**（阻塞级 2 条）
- 结论标准对照：阻塞级 = 2 ≠ 0 → FAIL，回 S3 重派设计（修复范围小：1 条补参数绑定异常承载、1 条调 tasks 依赖归属）；待澄清清单为空

---

## 上轮阻塞级修复验证（复审基准）

| 上轮条目 | 验证结论 |
|---------|---------|
| **B1** Feed 空页死循环 | **已修复（主体）**：双锚点算法（design §5.4）走查通过——①截断场景 `collected>pageSize` 用页末锚点，被截断可见帖满足游标严格小于语义下一页重扫、canView 幂等不重复输出；②恰好集满 `collected==pageSize` 走扫描进度锚点，无截断可见帖、不重不漏；③空页 `items=[] && hasMore=true && nextCursor≠null` 组合显式定义，翻页链从最后一批末条延续，已扫不可见候选不重扫；④前端连续 3 空页停止 + 非空页重置计数（T110 `emptyPageCount` 逻辑）与 design §3.2.16 一致。design §5.4 / api.md 接口16 / tasks T080 三处**行为契约**一致（hasMore=true 必非 null、空页指向扫描末条、1030 双路径）。附带发现术语精度问题（本轮建议 1）。 |
| **B2** 越界策略三处矛盾 | **部分修复**：三文档"缺省用默认、显式越界抛 1001（mock 配置 1040）"口径已统一（design §3.2.1/§3.2.15/§5.4、api.md §1.4+接口1/15/16、tasks T010/T070/T080 逐处核对一致，无"越界归默认"残留）。**但修复把口径扩大到"含非数字"却未补承载机制 → 本轮阻塞 1**。 |
| **B3** viewerId 校验缺失 | **已修复**：`^\d{1,18}$` 正则 + parse 后 >0 + try-catch NumberFormatException 三层齐备（design §6.1 / api.md §1.2 / tasks T100 三处一致）；正则先行使 18 位上限恒在 Long 范围内，catch 为双保险；`viewerId=0` → 1001、20 位超长 → 1001、非法不回退 Cookie、Cookie 脏值宽松 1003，边界走查全部通过。 |

上轮建议级 12 条验证：建议 1/2/3/6/10/11/12 已落地（含建议 12 拒绝留痕充分：无登录体系无用户维度挂频控 + IP 频控过度设计 + 环境可重置兜底，理由成立且 §7.3+§12 双留痕）；建议 4 落地但留 friendCount 残缺（本轮建议 8）；建议 5 落地但有界性未留痕（本轮建议 6）；建议 7 落地且中断恢复语义自洽；建议 8 仅对称修复好友缓存、标签缓存残留穿透（本轮建议 2）；建议 9 落地但承载机制含占位符陷阱（本轮建议 3）。

DDL MySQL 5.7.44 兼容扫描（sql.md 逐条）：COLLATE 全表 `utf8mb4_general_ci` ✓、无 `utf8mb4_0900_ai_ci` ✓、DEFAULT 仅字面量与 CURRENT_TIMESTAMP ✓、无函数索引/invisible index ✓、无 CHECK ✓、无外键 ✓、USING BTREE 与 TINYINT 显示宽度 5.7 合法 ✓、保留字表名 `user` 反引号包裹 ✓。**sql.md 本体通过**；残留见建议 4（T001/T002 伪代码摘要缺 COLLATE）。

---

## 阻塞级问题

1. **api.md:§1.4 + 接口1/接口3/接口15 异常码 ↔ design.md:§3.2.1 + §6.1 + tasks.md:T010** — "非数字参数返回 1001"承诺无承载机制：Controller 数值参数 Integer/Long 直收，非数字在 Spring 绑定层即抛 MethodArgumentTypeMismatchException，BizExceptionAdvice 只处理 BizException，最终落框架兜底 code=100
   - 失败场景：`GET /api/users?pageNo=abc` → Spring 类型转换失败（请求根本到不了 Service 层的 B2 校验，T010 伪代码里"非数字 → BizException(PARAM_INVALID)"对 Integer 入参是死代码）→ MethodArgumentTypeMismatchException → design §6.1 BizExceptionAdvice（仅 @ExceptionHandler(BizException.class)）不拦截 → 框架 GlobalExceptionAdvice 兜底 → 返回 `{code:100}`；而 api.md 接口1 参数表明文承诺"显式传入 <1 或**非数字**返回 1001"、§1.4"显式传入越界（**含非数字**）一律返回 1001"。阶段四用例按 api.md 设计 `pageNo=abc → 1001` 参数维度（MECE 含非法类型维度），阶段六 executor 按 design §6.1/T010 签名 `listUsers(String _appId, Integer pageNo, Integer pageSize)` 实现 → 用例必红回炉——这正是 B2 要消灭的"规格与实现口径分裂"在非数字维度的残留。同类断层覆盖接口3 tagId（"≤0 或非数字"）、接口15 userId/limit、接口16 pageSize 等全部数值 query/路径参数。
   - 攻击维度：7 边界与数值（上下界之外的类型边界）+ 规格真源一致性（承诺无实现承载）
   - 建议：三选一并同步四文档——① BizExceptionAdvice 增加 `@ExceptionHandler(MethodArgumentTypeMismatchException.class)` 统一转 1001（改动最小，与 A10 范式一致）；② 数值散参全部改 String 接收 + Service 层统一解析校验（校验逻辑集中但签名大改）；③ api.md 收缩承诺为"非数字返回系统码 100"（不推荐，违背 B2 统一精神）。修复归 design author。

2. **tasks.md:T020（涉及文件清单 + Depends）↔ T030（FriendTagRelationMapper 方法清单）** — 跨任务方法引用未声明依赖：T020 的 tagNames 组装依赖 T030 才创建的 `selectTagNamesByOwnerAndFriends`，T020 按清单无法独立编译交付
   - 失败场景：T020（G4，Depends 仅 T010）实现 `FriendshipServiceImpl.listFriends`，伪代码明确"tagNames：……自定义 selectTagNamesByOwnerAndFriends(ownerId, friendIds) GROUP BY 返回 (friendUserId, tagName 集合)"——该方法与所属 FriendTagRelationMapper 均在 T030（G5，after T020）的涉及文件与方法清单中（T030 明文标注"selectTagNamesByOwnerAndFriends(T020 用)"）。T020 执行时该 Mapper 不存在 → FriendshipServiceImpl 注入无类可注 → `mvn test-compile -pl moments-web -am` 失败，T020 验收（GET /api/friends 三层打通含 tagNames）无法通过；executor 只能偏离任务清单自行提前建 Mapper（执行记录与 tasks 失真）或卡死。
   - 攻击维度：8 过度设计与可回滚性（任务拆解可执行性）+ 规格真源一致性（任务依赖 DAG 缺边）
   - 建议：将 `selectTagNamesByOwnerAndFriends`（连同 FriendTagRelationMapper 骨架或该方法所属 XML）归属前移至 T020 涉及文件，或 T020 的 tagNames 组装改为 T020 内自带查询、T030 仅复用——任选其一，保证每个 task 按清单+Depends 可独立完成。修复归 design author。

---

## 建议级问题

1. **design.md:§3.2.16 / api.md:接口16（nextCursor 字段说明）↔ design.md:§5.4** — nextCursor 被统一表述为"扫描进度锚点"，与 §5.4 双锚点定义（`collected>pageSize` 截断时实际输出**页末锚点**=items 末条）术语冲突
   - 失败场景：全可见流 pageSize=20、batchSize=60，第一批 60 条全可见 → collected=60>20 → nextCursor=第 20 条锚点（页末锚点）≠ 最后扫描候选末条（第 60 条）；tester 若按"扫描进度锚点"字面语义断言非空页 `nextCursor 解码值 == 末条扫描位置`则必红。对外契约核心承诺（hasMore=true 必非 null、空页指向扫描末条、翻页链延续）三处一致成立，故无行为缺陷，纯术语精度。
   - 攻击维度：7 边界与空集（分页语义表述）+ 真源一致性
   - 建议：§3.2.16 与 api.md 接口16 的 nextCursor 行改为"不透明游标；hasMore=true 时必非 null（空页时指向已扫描候选流末条，非空截断时为页末条）"，删除把非空页也绝对化为扫描进度锚点的表述。

2. **design.md:§6.2（FriendTagCacheManager）** — 建议 8 修复不对称：好友 ID 缓存补了空集哨兵，标签绑定缓存未补——owner 对 friend 无标签时 miss 回源空集不回填，canView 的 tagHit 在无标签 viewer 场景每帖穿透 DB
   - 失败场景：赵六（explore 设定无标签）刷 Feed，页内 8 条 type=3/4 帖（mock 分布 40%）→ 每帖 tagHit 调 `getTagIds(post.userId, 赵六)` → miss → 回源空 → 不回填 → 同页下一帖再次 miss 回源 → 单页 8 次 DB 点查；Mock 生成后"前 N 名用户大多无标签"是常态而非边缘。正确性无错（空集结果正确），纯穿透性能缺口，与建议 8 修复的好友缓存防御不对称。
   - 攻击维度：7 边界与空集（空集缓存语义）
   - 建议：FriendTagCacheManager 对空 tagIds 同样回填哨兵（复用 "0" 方案，tagId 恒 >0 同理无冲突）或在 §6.2 声明该穿透为接受的权衡（写明理由）。

3. **tasks.md:T040（ImageStorageProperties）↔ T100（WebConfig @Value）** — 同一配置值双机制双默认值：`@ConfigurationProperties` 字段默认值写 `${user.home}/moments-data/images` 不会被占位符解析（Spring 只对绑定来源值解析，字段 Java 默认值保留字面量），与 WebConfig `@Value` 嵌套默认值（可解析）分裂
   - 失败场景：yml 未配置 `moments.image.root-path`（design §6.2 明说"yml 不强制修改，默认值兜底"）→ executor 按 T040 伪代码在 ImageStorageProperties 写 `private String rootPath = "${user.home}/moments-data/images";` → LocalImageServiceImpl 落盘到字面量目录 `./${user.home}/moments-data/images/`（相对路径，恰好复发建议 9 要修的工作目录漂移）→ WebConfig 静态映射却指向真实 `~/moments-data/images` → 上传成功但访问 404，T110 页面级场景⑦（上传→发图帖→Feed 展示）断裂。
   - 攻击维度：8 过度设计与可回滚性（配置承载机制）+ 修复回归（建议 9 引入）
   - 建议：统一单一承载——LocalImageService 侧同样走 `@Value("${moments.image.root-path:${user.home}/moments-data/images}")` 注入（删 ImageStorageProperties 或仅作类型 holder 不带占位符默认值），四文档同步。

4. **tasks.md:T001/T002（DDL 伪代码摘要）↔ sql.md:§1（A11 强制项）** — 伪代码表选项仅 `ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT=...`，缺 `COLLATE = utf8mb4_general_ci`；sql.md 本体全表有
   - 失败场景：executor 抄 T001/T002 伪代码要点（而非逐字复制 sql.md）写 schema 文件 → 8.0 目标环境执行时 utf8mb4 默认 collation 落 `utf8mb4_0900_ai_ci` → 恰好违反 A11"禁 8.0 专有排序规则"的本意，dev(5.7) 与目标(8.0) 行为分裂（排序/比较语义差异埋雷）。
   - 攻击维度：5 跨域副作用（环境一致性）+ 真源派生物失真
   - 建议：T001/T002 伪代码表选项补 `COLLATE = utf8mb4_general_ci`，或在两 task 验收标准加"与 sql.md 逐字一致（含 COLLATE）"。

5. **tasks.md:T090（帖子可见性生成）** — mock 帖 type=3/4 抽标签/好友写可见性两表未声明去重，而两表有 uniq_post_tag/uniq_post_user 唯一键且无 catch 兜底（friendship 有 catch 重复跳过，此处没有）
   - 失败场景：postCount>0，type=3 帖从作者 8 标签中随机抽 3 个，若抽样有放回（ThreadLocalRandom 逐次抽 index 不查重，8 取 3 重复概率约 34%）→ batchInsert 撞唯一键 → DuplicateKeyException → 该分批事务整体回滚 → mock 生成中途中断报错。T060 发帖链路明确"去重 Stream.distinct"，T090 同场景未写，谨慎度不一致。
   - 攻击维度：3 幂等（批量未去重）+ 7 边界（随机抽样重复）
   - 建议：T090 伪代码补"可见性标签/好友抽样用 shuffle+subList 或 distinct 保证不重复"。

6. **design.md:§3.2.15（limit 语义定稿段）** — 放大补偿是有界的（3 批×limit×3 = 9×limit 条候选），但表述为"避免……漏掉排在截断点之后的旧公开帖"，绝对化承诺了实际只"缓解"的效果
   - 失败场景：目标用户连续 ≥9×limit 条对 viewer 不可见帖（如 limit=100 时连续 900 条 type=2/3 未命中），第 901 条起的公开帖仍查不到——listUserPosts 是单请求无 nextCursor，不像 Feed 有翻页链延续与"3 空页停止"的显式权衡留痕。实验室量级（mock 每用户 1-2 帖）不可达，无现实触发路径，但语义表述与实现能力不符。
   - 攻击维度：7 边界与空集（有界扫描的截断残留）
   - 建议：§3.2.15 补一句"连续不可见超过 9×limit 时仍截断（与 Feed 的 3 空页停止同族有界权衡，实验室规模不可达）"。

7. **tasks.md:T080（cursorId 解析）** — cursor 正则 `^\d{13}:\d+$` 对 postId 段无位数上限（`\d+` 无界），超长数字段通过格式校验后 `Long.parseLong` 溢出抛 NumberFormatException 未 catch → 落 code=100 而非 1030（B3 同族防御缺口）
   - 失败场景：构造 `cursor=Base64("1789918203000:99999999999999999999999")`（23 位 id）→ 正则匹配通过 → 解析溢出 → 未声明异常 → 框架兜底 100；api.md 接口16 虽未明文承诺该输入返回 1030，但"非法 cursor 拒绝（R4）"精神与 B3 修复模式（try-catch NumberFormatException）同族，cursor 是用户可控输入应全路径防御。
   - 攻击维度：7 边界与数值（Long 上界）
   - 建议：cursorId 解析同样 try-catch → 1030（与 cursorTime 解析合并处理），或正则收紧为 `^\d{13}:\d{1,18}$`。

8. **design.md:§5.6 规则 5（建议 4 组合方案）/ T030（TagOut 组装）** — 展示侧 distinct 只兜底 tagNames，标签列表的 friendCount（COUNT 绑定数）在并发双绑定残留两条 is_del=0 记录时虚高 1
   - 失败场景：并发双击绑定落两条 (tag, friend) 记录 → tagNames 经 distinct 显示正常，但 `GET /api/friend-tags` 的 friendCount=COUNT(*)=2 而实际 1 个好友。同窗口同根因的另一半表现，触发概率与建议 4 原问题相同（实验室极低）。
   - 攻击维度：4 粒度维度（计数粒度 vs 去重粒度不一致）
   - 建议：friendCount 统计改 `COUNT(DISTINCT friend_user_id)`，T030 组装处一并落。

---

## 已攻击角度（未发现缺陷的维度也须列出）

- **1 状态机**：已检查帖子/标签/绑定生命周期（review-1 已过项复验无回归）+ 新增专项——mock clear 软删后自增不回退、新用户 id 全新 → friendship uniq_user_friend 与软删旧行无键冲突；追加模式批内 pair Set 去重 + DB 唯一键 catch 兜底闭环——除阻塞 2 涉及的任务序与建议 5 的可见性抽样外未发现状态缺口。
- **2 事务边界**：已检查 createPost 4 表事务内无中间件、deleteTag afterCommit INCR（建议 3 修复后"方法尾"选项已删净，四文档无残留）、clear 逐表小事务 + clear 后即 INCR 的中断恢复语义（clear 中途失败时缓存 stale 读到的是清空前一致快照，可接受）、bind/unbind 单 DML 自动提交等价性留痕——未发现违反 java-guide §5 的路径。自查过 T090 `clearTable(i)` 自调用 @Transactional 失效问题：单表单条 UPDATE 语句级原子与表级事务行为等价，无危害。
- **3 幂等**：已检查绑定查重 1008、解绑幂等、删帖/删标签非幂等留痕（建议 1 定稿后四文档表述统一）、发帖无 requestId 留档、mock 对称与追加语义——发现建议 5（可见性抽样未去重撞唯一键）。
- **4 粒度维度**：已检查 friendship/绑定/可见性/cursor 复合 key（与 explore Q3 逐项对齐）、哨兵 "0" 的 Set 成员维度混入与读取过滤——发现建议 8（friendCount 计数粒度未随 distinct 兜底）。
- **5 跨域副作用**：已检查删标签三联动（relation 级联 + visibility 自然失效 + ftagver）、mock 双 INCR、发帖无缓存联动需求、A4 版本号并发窗口（review-1 已推演，无回归）——未发现遗漏；发现建议 4（DDL 摘要 collation 缺失致 5.7/8.0 环境分裂，环境一致性侧）。
- **6 外部服务约束**：已对照 external-service-constraints.md——无视频、非广告素材、本地存储符合 PRD §7——未发现违规（与 review-1 一致，无变化）。
- **7 边界与空集**：已检查 B1 双锚点全组合走查（截断/恰好集满/空页/候选扫尽四分支无矛盾）、B3 viewerId 全边界（0/负/非数字/18 位/19 位/空串/带空格）、cursor 13 位毫秒与 2033 年外推、Feed 空集 Q5、批量 ≤500、除零无——发现阻塞 1（非数字承载断层）、建议 6/7。
- **8 过度设计与可回滚性**：已检查双锚点复杂度 vs SQL JOIN 预过滤替代方案（A5 坚持 canView 单实现是 PRD §3.8 语义，双锚点是该路线的必要代价且 trade-off 已述）、DDL 幂等无 DROP、回滚 git revert、配置双机制——发现阻塞 2（任务依赖缺口）、建议 3（配置承载陷阱）。
- **专项（B1 三处口径）**：design §5.4 算法 ↔ T080 伪代码 ↔ api.md 接口16 行为契约逐行比对——行为一致；术语精度问题列建议 1。
- **专项（修复回归扫描）**：逐条推演 3 阻塞 + 11 建议修复引入的新表面——B2 扩大的"含非数字"口径暴露阻塞 1；建议 9 的默认值承载引入建议 3；建议 5 的放大补偿引入建议 6 表述问题；建议 8 的不对称引入建议 2；其余修复无回归。
- **专项（DDL 5.7.44 兼容逐条扫描）**：sql.md 8 表 × A11 五条约束（collation/DEFAULT/索引/CHECK/类型）逐项核对通过（结论见文首验证节）；T001/T002 摘要残留列建议 4。
- **专项（上轮 §12 遗留 7 条）**：复验状态未变（postCount/A9/viewerId 命名待阶段五追认、其余留档完整），建议 12 拒绝理由充分（无登录体系无用户维度凭证 + IP 频控对单机实验室过度设计 + 环境可重置兜底，且给出生产化补救路径）。

---

## 待澄清清单

（空）

说明：本轮阻塞级与建议级均为设计可自决项（承载机制补齐、任务归属调整、表述统一），未暴露新的需用户裁决的需求含糊点。上轮已留档的 §12 追认项（postCount 扩展、A9 分布映射、viewerId 命名）仍走阶段五 gate，不重复列入。

---

> Gate 处置：阻塞级 2 > 0 → 回 S3 重派 tech-designer（本报告阻塞级问题为回炉输入；两处修复均为小改动，建议级可随阻塞一并处置）。重派上限 2 次（本轮为第 1 次重派后的复审，再回炉 1 次后若仍有阻塞级则熔断交人工）。本报告只读，未修改任何被审产物。
