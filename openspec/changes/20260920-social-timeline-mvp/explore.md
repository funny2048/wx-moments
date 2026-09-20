# 需求探索文档 — Social Timeline MVP

##### 文档信息
| 项目 | 内容 |
|------|------|
| 需求名称 | Social Timeline MVP（一期好友关系 + 二期朋友圈隐私 Feed） |
| 创建日期 | 2026-09-20 |
| 文档状态 | 已确认（AI 自主代行，待用户追认） |
| 确认人 | funny2048（目标指令授权自主推进） |
| 确认时间 | 2026-09-20 深夜（自主模式） |

---

##### 一、需求背景

Social Timeline 是社交 Feed 系统架构实验室项目，通过模拟微信朋友圈核心业务验证 Feed 架构方案。本次变更交付 MVP 全量：一期（用户/好友关系/好友标签/模拟数据 + 6 个管理页面）+ 二期（朋友圈图文发布/4 种隐私可见范围/统一 canView 判断/Cursor 分页 Feed 瀑布流）。

技术约束（PRD §7）：服务端仅 MySQL 8.0 + Redis，图片存本地文件夹；前端不限但样式贴近微信朋友圈。用户目标额外要求：**页面级 + 数据级测试**。

绿地现状：moments-* 五模块仅为公司脚手架（infra 控制器 + FileClient 文件服务 + 签名/安全配置），无任何社交业务代码；`frontend/` 目录为空；openspec/specs 为空。

##### 二、需求涉及领域
- ✅ user（模拟用户）
- ✅ friendship（好友关系）
- ✅ friend-tag（好友标签）
- ✅ mock-data（模拟数据生成）
- ✅ post（朋友圈发布）
- ✅ visibility（隐私权限）
- ✅ feed（朋友圈 Feed）
- ✅ image（本地图片上传）
- ❌ 不涉及已有领域（绿地，infra 文件上传域已排除，见 domain.md）

##### 三、功能范围

| 领域 | 功能 | Controller | 方法 |
|------|------|------------|------|
| user | 用户列表/详情（含用户切换支撑） | UserController（新） | listUsers / getUser |
| friendship | 我的好友/好友详情/按标签查好友 | FriendshipController（新） | getFriends / getFriendDetail / getFriendsByTag |
| friend-tag | 标签 CRUD + 好友标签绑定/解绑 | FriendTagController（新） | listTags / createTag / updateTag / deleteTag / bindUser / unbindUser |
| mock-data | 批量初始化模拟数据 | MockDataController（新） | generate |
| post | 发圈/详情/软删/按用户查帖 | PostController（新） | createPost / getPost / deletePost / listUserPosts |
| feed | Cursor 分页 Feed | FeedController（新） | getFeed |
| image | 本地图片上传 | ImageController（新） | upload |
| 前端页面 | 8 个静态页（同源托管） | resources/static（新） | users / friends / friend-detail / tags / friend-tags / mock-data / post-create / feed |

**不包含**（PRD §8 + 边界约束）：
- ❌ 注册登录/好友申请/好友验证（当前用户走 Cookie mockUserId，C3）
- ❌ 内容审核/视频/链接/音乐/位置/@好友（PRD §3.1 暂不实现）
- ❌ 点赞/评论/转发/收藏/通知/推荐/广告/商业化
- ❌ Offset 分页（PRD §3.10 明确 Cursor）
- ❌ 三期高并发架构（推拉模式等，仅做基础拉模式）

##### 四、影响范围
| 领域 | 功能 |
|------|-----|
| （绿地新增，无已有业务受影响） | 全部为新增链路；不改动 infra 脚手架（FileController/SecurityController/ApolloController/CodeGenerator 保持原样） |

##### 五、改动点梳理

###### 5.1 改动点清单

**接口层**（全部新增，旧逻辑均为"无"）：

| 序号 | 领域 | 功能 | 文件 | 方法 | 新逻辑 |
|------|----|----|----|----|------|
| 1 | user | 用户列表 | UserController | listUsers | GET /api/users，返回全部模拟用户 |
| 2 | user | 用户详情 | UserController | getUser | GET /api/users/{userId} |
| 3 | friendship | 我的好友 | FriendshipController | getFriends | GET /api/friends，当前用户好友列表 |
| 4 | friendship | 好友详情 | FriendshipController | getFriendDetail | GET /api/friends/{userId}，基础信息+我给其打的标签 |
| 5 | friendship | 按标签查好友 | FriendshipController | getFriendsByTag | GET /api/friends?tagId= |
| 6 | friend-tag | 标签列表 | FriendTagController | listTags | GET /api/friend-tags |
| 7 | friend-tag | 创建标签 | FriendTagController | createTag | POST /api/friend-tags（归属创建人 C5） |
| 8 | friend-tag | 修改标签 | FriendTagController | updateTag | PUT /api/friend-tags/{tagId} |
| 9 | friend-tag | 删除标签 | FriendTagController | deleteTag | DELETE，软删+级联软删绑定（C13），事务 |
| 10 | friend-tag | 绑定好友 | FriendTagController | bindUser | POST /api/friend-tags/{tagId}/users/{userId}，校验好友关系+查重，事务 |
| 11 | friend-tag | 解绑好友 | FriendTagController | unbindUser | DELETE /api/friend-tags/{tagId}/users/{userId}，事务 |
| 12 | mock-data | 生成模拟数据 | MockDataController | generate | POST /api/mock-data，配置用户数/人均好友数/标签数/clear |
| 13 | post | 发布 | PostController | createPost | POST /api/posts，图文校验+4 表同事务 |
| 14 | post | 详情 | PostController | getPost | GET /api/posts/{postId}，canView 校验 |
| 15 | post | 删除 | PostController | deletePost | DELETE，作者校验+软删（C7） |
| 16 | post | 按用户查帖 | PostController | listUserPosts | GET /api/posts?userId=（C14 最小扩展，canView 过滤） |
| 17 | feed | Feed | FeedController | getFeed | GET /api/feed?cursor=&pageSize=，Cursor 分页 |
| 18 | image | 图片上传 | ImageController | upload | POST /api/images/upload，本地文件夹+校验（C16/C17） |

**异步任务**：无（PRD 无 MQ/Job 需求）。

**服务层**：8 个新 Service（User/Friendship/FriendTag/MockData/Post/Feed/Visibility/LocalImage），事务点：createPost（4 表）、deleteTag（级联）、bindUser/unbindUser、deletePost、mock-data 分批事务；Redis 缓存操作一律在事务提交后（java-guide §5）。

**数据层**：8 张新表（user/friendship/friend_tag/friend_tag_relation/post/post_image/post_visibility_user/post_visibility_tag）+ 8 Mapper + XML + DO；每查询带 is_del=0。

###### 5.2 流程图

**隐私判断 canView(userId, postId)（§3.8 核心）**：

```
canView(viewerId, postId)
  │
  ├─ post = selectById(postId)（is_del=0，不存在 → 不可见）
  │
  ├─ viewerId == post.userId ？ ──是──▶ 可见（作者本人最高优先级）
  │
  ├─ visibilityType？
  │   ├─ 1 公开        ──▶ 可见
  │   ├─ 2 私密        ──▶ 不可见
  │   ├─ 3 部分可见    ──▶ 标签命中(viewer ∈ 作者给其打的指定标签好友) OR
  │   │                    好友命中(viewer ∈ post_visibility_user)
  │   │                    ？──是──▶ 可见 ──否（含空集）──▶ 不可见（C6）
  │   └─ 4 不给谁看    ──▶ 标签命中 OR 好友命中
  │                        ？──是──▶ 不可见 ──否（含空集）──▶ 可见（C6）
  │
  └─ 注：非好友观众对 type=3 空集规则同上；type=1 对非好友亦可见（"所有人可见"）
```

**Feed 查询（§3.11 + C4/C8/C12）**：

```
getFeed(cursor, pageSize)
  │
  ├─ 当前用户 = Cookie mockUserId（C3）
  ├─ 好友ID集合 ← Redis（miss → friendship 表回源 → 回填）
  ├─ 候选 = post WHERE user_id IN (自己 + 好友IDs) AND is_del=0
  │         ORDER BY create_time DESC, id DESC（C8 双字段）
  │         cursor 解码 = (createTime, postId)，WHERE (create_time, id) < (cursor.ct, cursor.id)
  │         防御：cursor 解码失败 → 当作首页/报参数错误（设计定稿）
  ├─ 逐帖 canView 过滤（仅非本人帖子需要；本人帖子作者必可见）
  ├─ 批量取图 selectByPostIds + 批量取作者 selectByUserIds（防 N+1）
  └─ 返回 {items, nextCursor, hasMore}；不足 pageSize → hasMore=false
```

**发帖（4 表同事务）**：

```
createPost ─ @Transactional(rollbackFor=Exception.class)
  ├─ 校验：content ≤2000 字（D4）；图片数 ≤9；visibilityType ∈1-4
  ├─ post insert（status=1）
  ├─ post_image batchInsert（image_url + sort 1..n）
  ├─ type=3/4 且指定好友 → post_visibility_user batchInsert
  └─ type=3/4 且指定标签 → post_visibility_tag batchInsert
```

###### 5.3 改动影响范围
| 序号 | 领域 | 功能 | 影响说明 |
|------|------|------|--------|
| 1 | 全部 | 全新增 | 绿地无回归风险；唯一触碰已有代码点：WebConfig 增静态资源映射（/images/** → 本地目录），需验证不影响已有 CORS/拦截器配置 |
| 2 | mock-data | clear=true | 清空 8 张表业务数据（软删）+ 缓存全量失效，页面必须二次确认 |
| 3 | 环境 | DDL 初始化 | 本地库需建 8 张表（初始化机制见 D1） |

##### 六、风险评估（含外部服务集成约束检查）

**外部服务集成约束检查（对照 .claude/rules/external-service-constraints.md，逐项）**：
1. 视频上传→视频中心：✅ 不适用（PRD §3.1 暂不实现视频）
2. 广告素材图片→ADM 存储桶：✅ 不适用（本需求为朋友圈图片非广告素材；PRD §7 明确本地文件夹存储，实验室定位）
3. 文件/附件/下载存储方案：✅ 已明确（本地可配置文件夹 + /images/** 静态映射；不复用公司 FileClient 对象存储）

**技术风险**（消费 pipeline.md 风险 1-11 + 补充）：
| # | 风险 | 应对 |
|---|------|------|
| R1 | Feed 权限过滤 N+1/大 IN 列表（1000+ 好友） | 好友 ID 集合 Redis 缓存（C12）+ 图片/作者批量查询 + canView 每帖仅查可见性两表（SQL 预过滤公开/私密帖）在阶段三定稿 |
| R2 | 缓存一致性（好友/标签变更未失效） | 变更路径事务提交后统一失效；缓存失败回源 DB 降级；测试覆盖 |
| R3 | 发帖 4 表事务边界 | @Transactional(rollbackFor=Exception.class) 包裹；文件 IO 移出事务 |
| R4 | Cursor 伪造/解码失败 | Base64 解码 + 格式校验，非法 cursor 拒绝请求（参数错误），不静默当首页 |
| R5 | 双向好友对称性 | batchInsert 成对写入同事务；测试断言对称 |
| R6 | 路径穿越（/images/**） | 文件名白名单 [a-zA-Z0-9._-]，拒绝 .. 与绝对路径 |
| R7 | 软删联动遗漏 | 全查询带 is_del=0（mapper XML 模板化）；帖子软删后 Feed/详情/列表全路径测试 |
| R8 | 本地 MySQL/Redis 可用性（页面级测试前提） | 阶段六启动前连通性验证；Redis 不可用走回源降级（C12 弱依赖设计） |
| R9 | DDL 初始化机制（权限约束下无法人工建表） | 见 D1 决策：dev 环境幂等 schema runner |
| R10 | 模拟数据规模（10 万级）性能 | 本期只验收 1000+ 好友关系（PRD 最低要求）；批量插入分批 ≤500；10 万级属三期研究范围 |
| R11 | 长事务（mock-data 全量生成） | 分批事务提交，批 ≤500 |

##### 七、语义闭环 Gate（六问，全部可答）

> 数据示例即阶段四用例种子。人物设定：用户 张三(id=1)、李四(id=2，张三的好友，被打标签"同学")、王五(id=3，张三的好友，被打标签"同事"+"朋友")、赵六(id=4，张三的好友，无标签)、钱七(id=5，非张三好友)。

**Q1 语义走查（每条规则具体数据示例）**

规则① canView-部分可见：张三发帖 P1（type=3，标签=[同事]，指定好友=[李四]）
- 王五(标签"同事"命中) → ✅可见；李四(指定好友命中) → ✅可见；赵六(无命中) → ❌不可见；钱七(非好友,无命中) → ❌不可见；张三(作者) → ✅可见 → 无矛盾

规则② canView-不给谁看：张三发帖 P2（type=4，标签=[同事]，指定好友=[赵六]）
- 王五(同事命中) → ❌不可见；赵六(指定命中) → ❌不可见；李四(无命中) → ✅可见；钱七(无命中) → ✅可见（"不给谁看"语义上钱七本非好友，帖子对无命中者可见=公开效果，但钱七是否能看到该帖？Feed 候选集=自己+好友的帖子，钱七不在张三 Feed 候选内——**判定：Feed 场景天然只见好友+自己的帖子；type=1"所有人可见"在 listUserPosts/详情直达场景对非好友生效**（走查通过，无矛盾））

规则③ canView-私密：张三发帖 P3（type=2）→ 张三 ✅；李四/王五/赵六/钱七 ❌

规则④ 空集（C6）：P4（type=3，无标签无好友）→ 除张三外全不可见；P5（type=4，无标签无好友）→ 张三的好友全可见（等同公开）

规则⑤ Cursor：张三 Feed 有 P1(t=10:00:00,id=101)、P2(t=10:00:00,id=102)、P3(t=09:59:59,id=100)，pageSize=2 → 第一页 [P2,P1]（同秒按 id DESC），nextCursor=Base64("10:00:00:101")，第二页 [P3]，hasMore=false

规则⑥ 标签删除级联（C13）：张三删"同事"标签 → 王五的绑定关系软删；P1(type=3,标签=[同事]) 此后仅李四可见（"同事"条件失效）；P2(type=4,标签=[同事]) 此后王五可见（排除条件失效）

规则⑦ 模拟数据分布（§2.5）：生成 1000 好友关系 → 标签分布误差在统计容差内（同事~250/同学~200/朋友~250/家人~50/亲戚~50/兴趣~150/其他~50），一个好友可多标签（王五=同事+朋友）

**Q2 归属边界**：全部改动归属本仓库（绿地，无上游依赖）；前端页面同仓库静态托管（C2）；不改动公司脚手架 infra 代码。✅ 已划清（AI 代行确认）

**Q3 作用范围与粒度维度**：
- friendship 粒度：(user_id, friend_user_id) 单条，双向=2 条对称记录（C11），唯一键去重
- 标签绑定粒度：(tag_id, friend_user_id)，tag 归属创建人（C5），唯一键去重
- 可见性粒度：(post_id, tag_id) / (post_id, user_id) 复合，per-post
- Feed 粒度：用户级（当前用户视角）；cursor key=(create_time, id) 复合（C8）
- canView 匹配维度：标签匹配=「作者→观众」的 friend_tag_relation 存在性；好友匹配=post_visibility_user 包含观众

**Q4 约束与优先级**：
- 图片：≤9 张/帖（C17+§3.1），单张 ≤5MB，格式 jpg/png/gif/webp
- visibilityType ∈ {1,2,3,4}，非法值拒绝
- canView 优先级：作者本人 > visibilityType 规则（作者本人对 type=2 私密帖也可见）
- 删除帖子：仅作者本人（越权拒绝）
- 操作标签/绑定：仅标签创建人
- content ≤2000 字（D4），允许纯空图/纯文/图文（不允许全空）
- pageSize 默认 20，范围 1-50（D3）

**Q5 时间与基准**：
- Feed 排序基准：post.create_time DESC, id DESC（服务器时间，插入时生成）
- 首次进入：cursor 为空 → 从最新开始
- 空集行为：无帖子 → items=[], nextCursor=null, hasMore=false；无好友 → 仅自己的帖子
- 模拟数据：clear 默认 false 追加；首次生成（空库）直接插入
- 缓存基准：好友/标签变更即时失效（无 TTL 语义依赖，TTL 兜底 1h）

**Q6 可验证性**：上述 Q1-Q5 每条均可翻译为给定-当-则用例（例：给定 P1(type=3,标签=[同事],好友=[李四])，当王五请求 GET /api/posts/{P1}，则 200 且返回详情；当赵六请求，则拒绝/404）→ 阶段四 test-cases.md 种子。✅ 无写不出的规则

##### 八、待澄清问题（用户确认列由 AI 代行，待追认）

| 编号 | 优先级 | 问题 | 候选答案 | AI 代行决策 | 依据 |
|------|-------|------|---------|------------|------|
| D1 | HIGH | DDL 初始化机制（权限约束下主 agent 无法直接人工建表） | A) dev profile 幂等 runner（CREATE TABLE IF NOT EXISTS）B) 手工执行 sql 脚本 C) spring.sql.init | A | 幂等可重复、免人工 DB 操作、实验室定位；sql.md 仍产出完整 DDL 脚本备查 |
| D2 | HIGH | 默认 8 标签的初始化时机 | A) 每用户创建时初始化默认 8 标签 B) 查询时兜底合成 | A | 标签归属创建人（C5）需落库；模拟数据为每用户生成默认标签+额外标签 |
| D3 | MID | Feed pageSize 边界 | 默认 20，1-50，越界拒绝 | — | 防御性编码 |
| D4 | MID | content 长度 | ≤2000 字，DB varchar(2000) | — | 微信语义约 2000；防御上限 |
| D5 | MID | 占位头像/占位图生成 | 本地生成 PNG（首字母/纯色），不引外部服务 | — | 无外网依赖，页面级测试可离线跑 |
| D6 | LOW | nickname 唯一性 | 不唯一，允许重名 | — | 微信语义允许 |
| D7 | LOW | 删帖后图片文件 | 保留不物理删 | — | 最小改动；磁盘回收属优化项 |

**未回答问题**：无（C1-C17 + D1-D7 全覆盖）。

---
*变更ID: 20260920-social-timeline-mvp*
*澄清日期: 2026-09-20（AI 自主模式，用户目标指令授权）*
