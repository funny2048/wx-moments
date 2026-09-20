# design-review-3: 20260920-social-timeline-mvp

> 审查人：adversarial-reviewer（mode: design，第 3 轮终审）
> 审查对象：design.md / api.md / tasks.md / sql.md（review-2 回炉修复后版本，修复映射 design §14：R1 16 行 + R2 10 行）
> 验证基准：design-review-1.md（阻塞 3 + 建议 12）+ design-review-2.md（阻塞 2 + 建议 8）
> 需求基准：prd.md（C1-C17）+ explore.md（六问 + D1-D7 + R1-R11）
> 生成时间：2026-09-21
> 轮次性质：**终审（收敛性审查）**——重派上限 2 次已用尽，本轮 PASS 则阶段三通过；再有阻塞级则熔断交人工。判定纪律：仅「会导致阶段四/阶段六口径分裂或实现必然失败」的问题计阻塞级；纯优化/风格/远期事项归建议级，终审不因建议级未采纳而 FAIL。
> 输入说明：knowledge/domain/service-hot.md 不存在（绿地项目），跳过

## 结论

- **不通过（阻塞级 1 条）**——按终审规则触发**熔断**，产物保留交人工
- 结论标准对照：阻塞级 = 1 ≠ 0 且待澄清清单为空 → FAIL。唯一阻塞为 **R2-NB2 修复不完整**（review-2 阻塞 2 的同构残留），非新引入的设计错误，修复面极小（任务归属/DAG 单点调整，不触碰任何接口契约与数据模型）
- R1/R2 历史条目闭环：26 条中 **25 条闭环、1 条（R2-NB2）半闭环**（详见闭环状态表）

---

## R2 阻塞级修复验证（终审基准）

| 上轮条目 | 验证结论 |
|---------|---------|
| **NB1** 非数字→1001 无承载 | **已修复（四文档口径闭环）**：① design §6.1 BizExceptionAdvice 显式声明处理两类异常，② `@ExceptionHandler(MethodArgumentTypeMismatchException.class)` 统一转 1001，注明"Spring 绑定层抛出根本到不了 Service 校验"、覆盖全部数值 query/路径参数；② design §9 异常场景表对应行齐备；③ api.md §1.4"非数字的承载机制"段与 design §6.1/§9 互引一致；④ tasks T100 伪代码含完整 handler 实现（log.warn 含 name/value/targetType 上下文 + `ApiResult.buildFailure(1001)`），验收标准明文"`GET /api/users?pageNo=abc` 返回 code=1001（非 100）；路径参数非数字（如 /api/users/xx）同 1001"。承载机制与 B2"含非数字"承诺面的断裂已闭合。残留边界：JSON 请求体字段类型非法（HttpMessageNotReadableException）不在该 handler 覆盖内、落框架 code=100——api.md 对 body 非数字**无明文 1001 承诺**（§1.4 承诺面为主语"数值 query/路径参数"），不构成同类"承诺无承载"，列本轮建议 2。 |
| **NB2** T020 跨任务方法引用未声明依赖 | **修复不完整（→ 本轮阻塞 1）**：已修复面——`selectTagNamesByOwnerAndFriends` 方法与 `FriendTagRelationMapper` 建文件动作归属前移 T020（tasks T020 描述/涉及文件/伪代码/验收第 2 条、T030 涉及文件改"追加"+伪代码注明"T020 已建文件"、design §6.3 标注"随 Mapper 建文件归属 T020"、§14 映射行齐备），**tagNames 组装链自洽**。未修复面——T020 伪代码 `listFriends` 的 **tagId 过滤分支**仍引用 T030 产物（详见阻塞 1），T020 验收两条标准在该分支上互斥不成立，§14 映射行"T020 仅依赖 T010 链可独立编译交付"的声明与事实不符。 |

R2 建议级 8 条落地抽查（逐条）：建议 1 术语不变式 ✓（design §3.2.16 + api 接口16 均改"不透明游标 + hasMore=true 必非 null"双锚点不变式表述，非空截断=页末锚点/空页=扫描末条）；建议 2 哨兵对称 ✓（design §6.2 FriendTagCacheManager 空 tagIds 回填哨兵 "0" + tasks T030 伪代码同步）；建议 3 @Value 单一承载 ✓（design §6.2 明文"不引入 @ConfigurationProperties"+ T040/T100 两处 `@Value("${moments.image.root-path:${user.home}/moments-data/images}")` **逐字一致**核验通过）；建议 4 COLLATE ✓（T001/T002 验收"与 sql.md 逐字一致（含 COLLATE）"兜底，T001 伪代码已补）；建议 5 shuffle 无放回 ✓（T090 伪代码明示）；建议 6 有界性留痕 ✓（design §3.2.15）；建议 7 cursor 正则收紧 ✓（design §3.2.16 / tasks T003 / T080 / api 接口16 四处 `^\d{13}:\d{1,18}$` + parseLong try-catch 双保险一致）；建议 8 COUNT DISTINCT ✓（design §3.2.5/§5.6 规则 5 / api 接口5 / tasks T030 四处一致）。

---

## 阻塞级问题

1. **tasks.md:T020（伪代码 listFriends 的 tagId 分支 + 验收第 1 条）↔ T030（涉及文件清单）** — R2-NB2 修复不完整：tagNames 组装依赖已前移，但 **tagId 过滤分支仍跨依赖 T030 产物**，T020 按清单+Depends 无法独立完整交付
   - 失败场景：T020（G4，Depends 仅 T010）的接口契约为 `GET /api/friends?tagId=`（api.md 接口 3：tagId 可选过滤 + 异常码 1004 归属校验）。T020 伪代码明文「tagId 非空 → **FriendTagMapper.selectByTagId** 校验归属(1004) → relationMapper.**selectFriendIdsByTag**」，验收第 1 条要求「tagId 过滤与归属校验（1004）可复现」。但：① `FriendTagMapper.java` 在 **T030** 涉及文件清单（新建），T020 涉及文件清单不含它 → T020 时点类不存在，FriendshipServiceImpl 注入无类可注；② `selectFriendIdsByTag` 属 T030「追加其余方法」清单（design §6.3 中该方法无 T020 归属标注）→ T020 时点调用无方法可调。executor 三条路径全部失败：严格按清单+伪代码 → `mvn test-compile -pl moments-web -am` 编译失败；砍掉 tagId 分支交付 → 验收第 1 条失败且窗口期内 `?tagId=` 被静默忽略（偏离 api.md 接口 3 的 1004 契约）；自行提前建 FriendTagMapper → 执行记录与 tasks 失真（正是 R2-NB2 要消灭的路径）。也无法绕过：selectTagNamesByOwnerAndFriends 是 (owner, friendIds) → tagNames 正向查询，语义上不能按 tagId 反查好友集合；java-guide 2.2 禁止 Service 层 QueryWrapper 兜底。T020 验收第 2 条「按涉及文件 + Depends（仅 T010 链）可独立编译交付」与第 1 条「tagId 过滤 1004 可复现」在该分支上**互斥**，design §14 R2-NB2 映射行的修复声明与产物事实不符。
   - 攻击维度：8 过度设计与可回滚性（任务拆解可执行性）+ 规格真源一致性（NB2 修复回归——修复只迁移了 tagNames 组装依赖，漏迁同函数内 tagId 过滤依赖）
   - 建议（修复方向，不代写）：三选一并同步 tasks/design §6.3/§2.2——① `FriendTagMapper` 建文件（或仅 `selectByTagId` + `selectFriendIdsByTag` 最小集，含 FriendTagRelationMapper 追加该方法）整体前移 T020，T030 改追加其余；② tagId 过滤分支整体后移 T030（T020 验收剔除"1004 可复现"项，接口契约不变、任务边界重划）；③ T020 Depends 增补 T030 并调整 G4/G5 并行组。任选其一保证 T020「涉及文件 + Depends + 伪代码 + 验收」四者自洽。

---

## 建议级问题

1. **design.md:§6.3（FriendTagMapper 方法清单）↔ tasks.md:T030（伪代码）** — 真源方法枚举漏项：T030 伪代码与验收含 `countFriendsByTagIds(tagIds)`（friendCount 的 COUNT(DISTINCT) 承载，R2-建议 8 修复引入），api 接口 5 / design §3.2.5 / §5.6 行为描述处均有承诺，但 design §6.3 FriendTagMapper 方法列表（selectByTagId/selectByUserId/updateTagName/updateIsDel/batchInsert/logicDeleteAll）未列该方法——派生物比真源多一个方法。执行以 tasks 伪代码+验收为据不会失败，修 design §6.3 补一行即可。
2. **design.md:§6.1（BizExceptionAdvice）** — JSON 请求体字段类型非法（如 `POST /api/posts` body `visibilityType:"abc"`、`POST /api/mock-data` body `userCount:"abc"`）由 Jackson 抛 `HttpMessageNotReadableException`，不在 NB1 handler（仅 MethodArgumentTypeMismatchException，覆盖 query/路径参数）覆盖内 → 落框架 code=100。api.md 对 body 非数字无明文 1001/1040 承诺（§1.4 承诺面为数值 query/路径参数），故非"承诺无承载"；但阶段四若按 query 同族逻辑给 body 字段设计"非法类型→1001"维度用例，将与实现（100）分裂。建议：Advice 追加 `HttpMessageNotReadableException` → 1001 handler，或 api.md §1.4 显式声明"JSON body 字段类型非法返回系统码 100"收缩承诺面（二选一，阶段四用例设计前定稿即可）。
3. **tasks.md:T005（标题）↔ 涉及文件清单 + design.md:§2.2** — 数字口径残留：T005 标题「In DTO x7」与 design §2.2「`model.in` x7」，但 T005 涉及文件与伪代码均只有 **4 个**（TagCreateIn/TagEditIn/MockDataGenerateIn/PostCreateIn；用户/好友/Feed 已散参化）。执行以文件清单为准无歧义，修两处数字即可。
4. **tasks.md:T010（伪代码注释）+ T002（伪代码 post 表选项）** — 修复痕迹清理（合并条）：① T010 注释「显式传入 pageNo<1 或 pageSize<1 或 >500 **或非数字** → BizException(PARAM_INVALID)」中"非数字"对 Integer 入参是死代码（review-2 阻塞 1 已定性；NB1 走绑定层路线后 Service 层永见不到非数字），删该字样防 executor 照抄写无效分支；② T002 伪代码 post 表表选项仍无 COLLATE（T001 已补）——验收"与 sql.md 逐字一致（含 COLLATE）"已兜底，伪代码系要点摘要非缺陷，为绝对一致可顺手补。

---

## R1/R2 历史条目闭环状态表（终审凭据）

### R1（design-review-1，16 条）

| 条目 | 级别 | 闭环状态 | 终审核验 |
|------|------|---------|---------|
| B1 Feed 空页死循环 | 阻塞 | ✅ 闭环 | 双锚点算法（design §5.4）四分支（截断/恰好集满/空页/扫尽）+ `items=[]&&hasMore=true&&nextCursor≠null` 合法组合 + 前端 3 空页停止，§5.4/api 接口16/T080/T110 四处行为契约一致，review-2 验证后无回归 |
| B2 越界策略三处矛盾 | 阻塞 | ✅ 闭环 | 「缺省默认、显式越界抛 1001（mock 1040）」统一（design §3.2.1/§3.2.15/§5.4、api §1.4+接口1/15/16、tasks T010/T070/T080）；review-2 暴露的"含非数字"承载断层已由 NB1 修复闭合（本轮验证） |
| B3 viewerId 校验缺失 | 阻塞 | ✅ 闭环 | `^\d{1,18}$` + >0 + try-catch 三层（design §6.1 / api §1.2 / tasks T100）一致，0/超长/非数字/脏 Cookie 边界全通过，无回归 |
| 建议1 删帖幂等表述 | 建议 | ✅ 闭环 | §5.6 规则 5b「仅解绑幂等」+ api §1.5/接口14 + T110 防抖，四文档统一 |
| 建议2 viewerId 假设留痕 | 建议 | ✅ 闭环 | §7.3 显式假设留痕（"知情者可冒充……实验室显式接受"） |
| 建议3 INCR"或方法尾"违规选项 | 建议 | ✅ 闭环 | §5.6 规则 8 + §6.2 + T030 定稿 afterCommit 唯一方式，"方法尾"字样四文档无残留 |
| 建议4 并发绑定重复记录 | 建议 | ✅ 闭环 | §5.6 规则 5 组合方案（DB 唯一键不可行论证 + 展示 distinct + 前端防抖 + 窗口留痕）；计数侧由 R2-建议8 补齐 |
| 建议5 limit 先截断后过滤 | 建议 | ✅ 闭环 | §3.2.15 结果上限语义 + limit×3×3 放大补偿 + T070；有界性由 R2-建议6 补留痕 |
| 建议6 Base64 decode 未 catch | 建议 | ✅ 闭环 | §3.2.16/§5.4 + T080 try-catch IllegalArgumentException → 1030 |
| 建议7 clear 单事务 + 中断缓存 stale | 建议 | ✅ 闭环 | §5.5/§9 逐表独立小事务 + clear 完成即双 INCR（中断恢复语义）+ T090 同步 |
| 建议8 空好友集缓存穿透 | 建议 | ✅ 闭环 | 哨兵 "0" 回填（§5.4/§6.2/T020）；标签缓存对称由 R2-建议2 补齐 |
| 建议9 图片目录相对路径 | 建议 | ✅ 闭环 | `${user.home}/moments-data/images` 绝对路径默认；承载陷阱由 R2-建议3（@Value 单一承载）修正 |
| 建议10 type=1/2 可见性列表矛盾 | 建议 | ✅ 闭环 | §3.2.12 + api 接口12 + T060 定稿静默忽略 |
| 建议11 bind 事务口径不一 | 建议 | ✅ 闭环 | §6.2 留痕（单 DML 原子等价，tester 断言以 design 为准） |
| 建议12 上传无频控 | 建议 | ✅ 闭环（拒绝留痕） | §7.3 拒绝理由（无登录体系无用户维度凭证 + IP 频控过度设计 + 环境可重置兜底）+ §12 遗留 7 生产化补救路径 |
| 环境补充（5.7/8.0 双兼容） | 约束 | ✅ 闭环 | A11 + sql.md §0 五条约束 + 全表 utf8mb4_general_ci（review-2 逐条扫描通过，本轮无变化） |

### R2（design-review-2，10 条）

| 条目 | 级别 | 闭环状态 | 终审核验 |
|------|------|---------|---------|
| NB1 非数字→1001 无承载 | 阻塞 | ✅ 闭环 | 四文档口径闭环（design §6.1/§9 + api §1.4 + tasks T100 伪代码与验收），见文首验证表 |
| NB2 T020 跨任务方法引用 | 阻塞 | ❌ **半闭环** | tagNames 组装依赖已前移自洽；**tagId 过滤分支（FriendTagMapper.selectByTagId + selectFriendIdsByTag）仍依赖 T030 产物**，T020 验收两条互斥 → 本轮阻塞 1 |
| 建议1 nextCursor 术语冲突 | 建议 | ✅ 闭环 | 不透明游标 + 双锚点不变式表述（design §3.2.16 + api 接口16） |
| 建议2 标签缓存哨兵不对称 | 建议 | ✅ 闭环 | FriendTagCacheManager 空 tagIds 哨兵回填（§6.2 + T030） |
| 建议3 配置双机制分裂 | 建议 | ✅ 闭环 | @Value 单一承载，两处默认值逐字一致（核验通过），不建 @ConfigurationProperties |
| 建议4 DDL 摘要缺 COLLATE | 建议 | ✅ 闭环 | T001/T002 验收"逐字一致（含 COLLATE）"（T001 伪代码已补；T002 伪代码残留归本轮建议 4，验收兜底） |
| 建议5 可见性抽样有放回撞唯一键 | 建议 | ✅ 闭环 | T090 shuffle+subList 无放回 |
| 建议6 放大补偿绝对化表述 | 建议 | ✅ 闭环 | §3.2.15 有界性留痕（9×limit 截断，同族权衡） |
| 建议7 cursor id 段无上限溢出 | 建议 | ✅ 闭环 | `^\d{13}:\d{1,18}$` + parseLong try-catch 双保险（四处一致） |
| 建议8 friendCount 计数粒度 | 建议 | ✅ 闭环 | COUNT(DISTINCT friend_user_id) 四处一致；附带引入 design §6.3 方法清单漏项（本轮建议 1，非行为缺陷） |

**统计**：26 条历史条目 = 25 闭环 + 1 半闭环（R2-NB2）。两轮 5 条阻塞中 4 条彻底闭环，唯一残留为 NB2 的任务归属问题（非设计语义错误）。

---

## 已攻击角度（未发现缺陷的维度也须列出）

- **1 状态机**：帖子（正常→已删单向、仅作者、1010 非幂等留痕）/ 标签（级联软删 relation、visibility 自然失效）/ 绑定（解绑幂等、重绑 INSERT 新记录）——两轮已过项复验，NB 修复未触碰生命周期结构，无回归。
- **2 事务边界**：createPost 4 表 @Transactional(rollbackFor) 事务内无中间件、deleteTag afterCommit INCR（"方法尾"选项删净）、clear 逐表小事务 + clear 后即双 INCR、bind/unbind 单 DML 等价留痕、mock 分批 ≤500——未发现违反 java-guide §5 的路径。
- **3 幂等**：绑定查重 1008、解绑幂等、删帖/删标签非幂等定稿、发帖无 requestId 留档（依据成立维持 review-1 裁决）、mock shuffle 无放回（R2-建议5 修复后）——未发现缺口。
- **4 粒度维度**：friendship/绑定/可见性/cursor 复合 key 与 explore Q3 对齐；friendCount COUNT(DISTINCT) 与 tagNames distinct 口径统一（R2-建议8）；哨兵 "0" 读取过滤——未发现含糊。
- **5 跨域副作用**：删标签三联动、mock 双 INCR、发帖无缓存联动需求、A4 版本号并发窗口（review-1 已推演自愈）——未发现遗漏。
- **6 外部服务约束**：无视频、非广告素材、本地存储符合 PRD §7——未发现违规（三轮一致）。
- **7 边界与空集**：B1 双锚点四分支组合完备性复验（截断页末锚点/恰好集满/空页扫描锚点/扫尽 null，游标严格小于语义无重扫无跳空）、B3 viewerId 全边界、cursor 13 位毫秒 + 18 位 id + parseLong 双保险、Feed/listUserPosts 有界截断留痕、批量 ≤500——**NB1 承载面边界**：query/路径参数全覆盖 ✓，JSON body 类型错误不在覆盖内但无明文承诺（建议 2，不构成阻塞）。
- **8 过度设计与可回滚性**：双锚点 trade-off 已述、DDL 幂等无 DROP、回滚 git revert、@Value 单一承载（占位符陷阱已消）——**发现阻塞 1（T020 任务归属 DAG 缺口，NB2 修复回归）**；其余任务依赖链逐一核验（T060/T050/T070/T080/T090/T100/T110 按涉及文件+Depends 均可独立交付，T020 Controller 对 CurrentUserResolver 的 G3/G4 并行时序已有"暂用内联 Cookie 解析"过渡方案）——唯一断点即阻塞 1。
- **专项（终审重点 1：NB1 修复真实性）**：四文档（design §6.1/§9、api §1.4、tasks T100 伪代码+验收）逐处核验闭环，handler 覆盖面与 B2 承诺面（数值 query/路径参数含非数字）精确对齐；附带识别 body 维度承诺面外边界（建议 2）。
- **专项（终审重点 2：建议级 8 条落地抽查）**：8/8 落地（结论见文首验证节）；其中建议 4/8 各带一处无害文档残留（归并本轮建议 1/4）。
- **专项（终审重点 3：回归扫描）**：① §14 映射 26 行与正文逐行对照——25 行属实，R2-NB2 行"可独立编译交付"声明与产物不符（即阻塞 1）；② 任务 DAG 17 任务/9 并行组不变 ✓（两次修订均声明且核实）；③ 错误码表：api §1.6 ↔ tasks T003 SocialErrorCode 16 码（1001-1008/1010-1012/1020-1022/1030/1040 + 100 兜底）逐一对照无冲突无重义 ✓；④ api 17 接口 ↔ design §3.1 17 行 + 合并说明一致 ✓；⑤ B1/B2/B3 修复无行为回归 ✓；⑥ 新引入面：countFriendsByTagIds 漏列（建议 1）、x7 数字残留（建议 3）、死代码注释（建议 4）——均为文档精度级。
- **专项（判定纪律自查）**：本轮 4 条建议级均不满足"必然失败/口径分裂"阻塞标准——建议 1/3/4 为文档枚举与数字残留（执行依据自洽），建议 2 为承诺面之外的边界（用例设计时定稿即可）；未因建议级未全采纳而影响结论，结论仅由阻塞 1 驱动。

---

## 待澄清清单

（空）

说明：本轮阻塞级为任务拆解归属问题（设计可自决，修复方向已给三选一），未暴露新的需用户裁决的需求含糊点。§12 遗留追认项（postCount 扩展、A9 分布映射、viewerId 命名）仍走阶段五 gate，不重复列入。

---

> Gate 处置：阻塞级 1 > 0 → 按终审规则**熔断**（重派上限 2 次已用尽：review-1 FAIL → 第 1 次重派 → review-2 FAIL → 第 2 次重派 → 本轮 FAIL），产物保留交人工。交人工提示：唯一阻塞为 NB2 同构残留的单点任务归属问题，不涉及接口契约/数据模型/事务语义的任何变更，人工裁决三选一修复方向（阻塞 1 建议①②③）后可快速闭环重审或直接放行。本报告只读，未修改任何被审产物。
