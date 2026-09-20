# design-review-1: 20260920-social-timeline-mvp

> 审查人：adversarial-reviewer（mode: design）
> 审查对象：design.md / api.md / tasks.md / sql.md
> 需求基准：prd.md（C1-C17）+ explore.md（六问 + D1-D7 + R1-R11）
> 生成时间：2026-09-20
> 输入说明：knowledge/domain/service-hot.md 不存在（绿地项目），按任务参数跳过

## 结论

- **不通过**（阻塞级 3 条）
- 结论标准对照：阻塞级 = 3 ≠ 0 → FAIL，回 S3 重派设计（clarify.md 协议）；待澄清清单为空（见文末说明）

---

## 阻塞级问题

1. **design.md:§5.4（Feed 组装步骤）+ §3.2.16 / api.md:接口16（nextCursor 字段）** — 3 批放大取数用尽且累计可见帖 visible=0 时，`nextCursor` 无从构造，`hasMore=true` 与 `items=[]` 组合行为未定义
   - 失败场景：候选流中连续 ≥3×(pageSize×3)=9×pageSize 条帖子对 viewer 不可见（SQL 已预过滤 type=2 非作者帖，剩余不可见候选全部来自 type=3/4 未命中；mock postCount>0 时 type=3/4 占 40% 且随机抽标签/好友，抽中 viewer 的概率低，连续长段不可见可自然/人为构造，Playwright 隐私矩阵测试反复拉 Feed 也会逼近）→ 3 批用尽、visible=0 → `items=[]`；按 §5.4 "hasMore = 截断发生 OR（末批取满 batchSize 且仍未取满 pageSize 的保守 true）" 输出 `hasMore=true`；而 `nextCursor = items 末条 (created_stime,id)` 在 items 为空时**没有末条**——设计仅定义 "hasMore=false 时为 null"，未定义空 items + hasMore=true 的输出。实现者随手返回 null 后，T110 前端逻辑 `cursor=data.nextCursor; hasMore=data.hasMore` 下一页退化为**无 cursor 首页请求**，重新拉到同样 180 条不可见候选 → 永久循环空页请求（死循环 + 无法翻到深部可见帖）。api.md 接口16 "hasMore=false 时为 null" 隐含 hasMore=true 时 nextCursor 必非 null，与该场景直接冲突。
   - 攻击维度：7 边界与空集（分页语义完整性）
   - 建议：区分"本页末条"与"扫描进度"两个锚点语义——3 批用尽且 visible 不足时 nextCursor 必须取**最后一批末条**（保证翻页链延续、已扫过的不可见帖不重扫），并在 §5.4 与 api.md 接口16 显式定义 `items=[] 且 hasMore=true` 时的合法输出组合；同时定义前端连续空页的停止规则。修复归 design author。

2. **tasks.md:T010 伪代码 ↔ design.md:§3.2.1 + api.md:接口1** — 参数越界处理策略三处直接矛盾，实现与用例将各拿一半基准
   - 失败场景：design §3.2.1 声明 pageNo "≥1、默认 1"、pageSize "1-500、默认 20"，异常码列 1001 参数校验失败；api.md 接口1 异常码表明确 "1001 pageNo/pageSize 越界或非数字"；但 tasks T010 伪代码写 "校验 pageNo>=1、pageSize 1-500（**越界归默认，不抛错**）"。阶段四用例按 api.md 设计 `pageNo=0 → 1001`，阶段六 executor 按 T010 伪代码实现 `pageNo=0 → 归默认返回第一页` → 用例必红进入回炉；反之实现按 api.md 则 T010 伪代码失真。同类策略散布不一致：接口16 pageSize 越界抛 1001（T080 明确）、接口11 越界抛 1040、接口15 limit 越界行为**完全未定义**（T070 只写"默认 100 上限 500"）。
   - 攻击维度：7 边界与数值（上下界）+ 规格真源一致性（design §3 是真源，tasks/api 派生物与真源冲突）
   - 建议：全项目统一越界策略（建议全部按 api.md 口径抛 1001，归默认仅限"参数缺失"），修正 T010 伪代码，补齐接口15 limit 越界行为定义。

3. **tasks.md:T100（CurrentUserResolver 伪代码）↔ api.md:§1.2** — viewerId 校验缺失 `>0` 下界与 Long 上界：viewerId=0 穿透 requireUser 产生脏数据，超长数字抛未声明异常
   - 失败场景：api.md §1.2 声明 viewerId 为 ">0 整数"，但 T100 伪代码仅 `viewerId.matches("^\\d+$")` 后直接 `Long.valueOf(viewerId)`：① `viewerId=0` 匹配纯数字正则 → 通过 requireUser（不抛 1003）→ `POST /api/posts?viewerId=0` 落库 `user_id=0` 的帖子（作者用户不存在，脏数据），Feed 组装 `selectByUserIds` 查无此人 → 昵称/头像空串的幽灵帖；`GET /api/friends?viewerId=0` 返回空好友而非 1003/1001；② `viewerId=99999999999999999999`（20 位，超 Long）→ `Long.valueOf` 抛 NumberFormatException 未捕获 → 框架兜底 code=100，api.md 无此口径（既非 1001 也非 1003），违反 java-guide 7.2"数值范围同时校验上界与下界"。
   - 攻击维度：7 边界与数值 + 规格落地一致性（api 声明未落到实现指引）
   - 建议：resolve 对 viewerId 增加 `>0` 与位数/Long 范围上界校验，非法值回退 Cookie 解析或抛 1001，口径同步写入 api.md §1.2 与 design A3。

---

## 建议级问题

1. **api.md:§1.5 ↔ 接口14** — 通用约定"解绑/**删除**幂等"与接口14 异常码"1010 帖子不存在**或已删除**"矛盾：deletePost 对已删帖返回 1010 并非幂等成功（用户双击删除按钮第二次报错 toast）。统一表述（删帖非幂等按 1010，或改幂等成功）。
2. **design.md:§7.3（安全表）** — `viewerId` 参数优先级 > Cookie 意味着任意人可冒充任意 userId 发帖/删帖/管理标签，越权防线对知情者形同虚设。C3/C10 已豁免签名（实验室），但 §7.3"水平越权"行只写了标签/帖子归属校验，未对 viewerId 完全信任这一假设留痕——补一行"viewerId 即身份、无认证，实验室定位接受"（重点攻击线索 5 的留痕要求）。
3. **tasks.md:T030（deleteTag）** — INCR 时机伪代码给两个选项 "TransactionSynchronizationManager afterCommit **或方法尾**"："方法尾"即事务提交前，等价于事务内操作 Redis，违反 design §5.6 规则 9 与 java-guide §5 自身。删除错误选项，防 executor 照抄。
4. **design.md:§5.6 规则 5（幂等-绑定）** — SELECT 查重 + INSERT 之间无锁，且 friend_tag_relation 有意不建 DB 唯一键 → 并发/双击绑定可落两条 `is_del=0` 记录，tagNames 组装展示重复标签名。建议查询组装侧去重兜底，或明确依赖前端防抖并留痕。
5. **design.md:§3.2.15（listUserPosts）** — `limit` 语义未定义（扫描上限还是结果上限）：实现为 `selectUserPosts(userId, limit)` 先截断再 canView 过滤，目标用户前 100 条全为对 viewer 不可见的私密/部分可见帖、旧公开帖排在第 100 条之后时，非好友视角返回 `[]` 但实际存在可见帖——漏展示。与 Feed 的放大补偿口径不一致（Feed 有 pageSize×3×3，此处无）。
6. **tasks.md:T080** — `Base64.getDecoder()` 对非法 Base64 串（如 `!!!`）抛 IllegalArgumentException，伪代码仅在 matcher 校验后写"失败抛 1030"，未声明 catch decode 异常 → 实现偏离 api.md 接口16 "Base64 解码失败 → 1030" 的口径、落 code=100。补 catch 转 1030。
7. **design.md:§5.5（clear 段）/§9** — clear=true 单事务内 8 表全量 `UPDATE ... WHERE is_del=0`，与 R11"批间提交防长事务"精神冲突（大库时锁时间长）；且生成中断时 evictAll 不执行 → 缓存 stale 直至人工重跑。建议 clear 也分批或声明实验室规模接受 + 中断恢复说明。
8. **design.md:§6.2（FriendIdsCacheManager）** — 无好友用户回源空集后不回填（`isNotEmpty(dbIds)` 才 sadd）→ 该类用户每次 Feed 都穿透缓存查 DB。若为故意（空集合判别歧义）应声明，否则加空标记 key。
9. **design.md:§6.2（ImageStorageProperties）** — `root-path` 默认 `./data/images` 相对路径受进程工作目录影响（IDE 启动 vs 命令行启动目录不同 → 已上传图片"丢失"、占位图重复生成到不同目录）。建议默认值改绝对路径或文档强约束启动目录。
10. **api.md:接口12** — visibilityTagIds/UserIds 说明 "type=3/4 生效；type=1/2 时**忽略须传空**" 自相矛盾（忽略 还是 必须为空？）。明确 type=1/2 携带非空列表是静默忽略（不校验不落库）还是报 1001，避免实现与用例各解一词。
11. **explore.md:§5.1（改动点 10/11）↔ design.md:§6.2** — explore 将 bindUser/unbindUser 标注"事务"，design 明确"单表写无事务（规范仅强制多表）"。行为等价（单 DML 自动提交），但两份基准文档口径不一致，tester 事务断言口径会摇摆，统一即可。
12. **design.md:§7.3（上传滥用）** — 上传接口无鉴权（C10 豁免）且无频率/总量限制，仅 5MB 单张上限，可无限刷文件填满磁盘。实验室可接受，建议留痕声明或加简单频控。

---

## 已攻击角度（未发现缺陷的维度也须列出）

- **1 状态机**：已检查帖子生命周期（正常→已删单向、前置=作者+存在、重复删除走 1010、无回退分支——软删不可逆实验室可接受）、标签生命周期（正常→已删级联 relation，post_visibility_tag 按tagId 自然失效 C13）、绑定生命周期（解绑后重绑走 INSERT 新记录、软删旧记录不占唯一性，反复绑定解绑表膨胀可接受）——发现建议 1。
- **2 事务边界**：已检查 createPost 4 表 @Transactional(rollbackFor) 且事务内无 Redis/文件 IO、deleteTag 级联 2 表事务、mock 生成分批事务 ≤500、deletePost 单表软删、getFeed 只读无事务——发现建议 3、7。
- **3 幂等**：已检查绑定 SELECT 查重（发现建议 4 并发窗口）、mock 对称 pair Set 去重 + uniq_user_friend 兜底、mock 追加语义（C15）、发帖不做 requestId 幂等——**裁决：依据成立不阻塞**（帖子无业务唯一键、同文连发是微信合法语义、非资金操作、前端防抖；§12 遗留 1 已留档且给了 DistributedLock 补救路径）。
- **4 粒度维度**：已检查 friendship (user_id, friend_user_id)、标签绑定 (tag_id, friend_user_id)、可见性 (post_id, tag_id)/(post_id, user_id)、Feed 用户级、cursor key (created_stime, id) 复合——与 explore Q3 逐项对齐，key 构成全部显式定义，未发现含糊。
- **5 跨域副作用**：已检查删标签→relation 级联 + post_visibility_tag 自然失效 + ftagver INCR、mock→fver+ftagver 双 INCR、发帖→无缓存联动需求（Feed 实时查询）、删帖→图片文件保留（D7）、无删用户入口——未发现遗漏。**专项（重点线索 3）**：A4 全局版本号并发窗口按三顺序交错推演（GET ver → 写 INCR → smembers miss → 回源回填旧 key；GET ver → miss → 回源旧值 → INCR → 回填旧 key）——旧版本 key 在 INCR 后不再被新读者命中、TTL 1h 兜底，仅毫秒级单次 stale read 且自愈，实验室规模不构成缺陷。
- **6 外部服务约束**：已对照 external-service-constraints.md——无视频（PRD §3.1 排除）、朋友圈图片非广告素材（不适用 ADM，explore 已检查）、本地文件夹存储符合 PRD §7——未发现违规。
- **7 边界与空集**：已检查 Feed 空集（Q5 ✓）、type=3/4 空集（C6 ✓ 走查通过）、cursor 开闭区间（严格 < 双字段 ✓）、mock 批量上限 ≤500 ✓、好友 IN 列表规模（R1 已识别）——发现阻塞 1/2/3、建议 5/6/8。
- **8 过度设计与可回滚性**：已检查版本号失效 vs SCAN 批删的 trade-off 声明（A4 说出代价）、DDL 幂等 IF NOT EXISTS 无 DROP、回滚=git revert+表保留、8 Service 分层无无据抽象——未发现过度设计；发现建议 9（相对路径）。
- **专项（重点线索 7）canView 三路径口径**：详情（1011）/listUserPosts（filter canView，非好友对 type=1 可见）/Feed（候选集限好友+自己，SQL 预过滤私密非作者 + type=1 直通 + type=3/4 走 canView 单实现）——与 explore Q1 规则②裁决（"Feed 天然只见好友+自己，type=1 全局可见性作用于详情/列表直达场景"）一致，三路径无口径分裂；listUserPosts 的截断语义缺口见建议 5（非口径问题）。
- **专项（重点线索 8）静态资源与上传防御**：白名单正则 `^[A-Za-z0-9._-]+$` 拒绝含 `/` 的 resource path（`../application.yml` 不匹配 → 404，T100 有验收测试项）、上传文件名服务端 UUID 重命名防覆盖防穿越、扩展名小写化白名单、双扩展（.jpg.exe）按最后扩展拒绝、svg/html 不在白名单（无脚本执行面）——防御链完整；Content-Type sniffing 与频控缺失见建议 12（现代浏览器下风险低）。
- **专项（重点线索 9）DDL 一致性与索引**：sql.md 8 表字段/默认值/COMMENT 与 design §4.1、api.md 出参逐表核对一致（status/visibility_type/gender 枚举全值注释、tagName 16 字符校验 vs varchar(32) 字符语义兼容、content 2000 字符 fits varchar(2000)）；Feed 查询 `user_id IN (...) ORDER BY created_stime DESC, id DESC`——InnoDB 二级索引 idx_created_stime 隐含主键即 (created_stime, id)，游标 range 扫描与排序可全索引满足，idx_user_created 支撑 IN ref 访问，实验室万级数据量下无阻塞级风险——未发现缺陷。
- **专项（重点线索 4）mock 对称与部分失败**：对称双记录"成对入批"（同批同事务，批级原子性保证不出现单边记录）、部分失败已提交批次保留 + 追加语义可重跑（§9 已声明）、clear=true 重跑即重建——除建议 7 的 clear 单事务与中断缓存失效外未发现不一致。
- **专项（重点线索 1）§12 遗留 6 条逐条裁决**：①发帖无 requestId——依据成立（见幂等维度裁决），不阻塞；②postCount PRD 外扩展——默认 0 不触发，已有阶段五追认安排，不阻塞；③A9"兴趣好友 15%"拆球友10+客户5——分布解释权留痕待追认，权重总和 100 校验一致，不阻塞；④10 万级性能——R10 三期范围已声明 trade-off，不阻塞；⑤viewerId 命名——追认流程内，不阻塞；⑥删帖图片不物理删——D7 决策，不阻塞。

---

## 待澄清清单

（空）

说明：本次审查未暴露新的必须由用户裁决的需求含糊点。design §12 遗留 2/3/5（postCount 扩展、A9 分布映射、viewerId 参数命名）已自带阶段五用户评审 gate 的追认流程安排，不重复列入。建议级 5（listUserPosts limit 语义）与建议级 10（type=1/2 携带可见性列表的行为）为设计可自决项，修复时随阻塞级一并定稿即可。

---

> Gate 处置：阻塞级 3 > 0 → 回 S3 重派 tech-designer（clarify.md 协议：本报告阻塞级问题即回炉输入），修复后 adversarial-reviewer 再审（design-review-2）。本报告只读，未修改任何被审产物。
