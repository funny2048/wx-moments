# Pipeline: 20260920-social-timeline-mvp — Social Timeline MVP（一期好友关系 + 二期朋友圈隐私 Feed）

> 基于 domain.md 与代码分析，生成 P2 图谱链路文档。
> 生成日期: 2026-09-20

> ⚠️ **降级生成：graphify 图谱不可用，链路基于领域文档推导，未经图谱验证。**
> `graphify-out/graph.json` 不存在（绿地项目，未跑过 graphify），已按 graph-query agent 前置检查走降级模式：跳过图谱查询，链路由 `domain.md`（P1 领域匹配结果）+ `prd.md`（含澄清补充 C1-C17）拼装，无任何代码级验证。

> 📌 **重要声明：本文档描述的全部链路为"规划的新增链路"，非已有链路。**
> 本项目为绿地项目：`knowledge/domain/` 不存在，moments-* 模块仅含公司脚手架 infra 代码（Health/Base/FeatureTest/File/Apollo/Security 控制器），无任何社交业务领域。下列 Controller / Service / Mapper / 数据表均为本次变更规划新建，当前代码中不存在。具体类名、路由、字段以阶段三 design/api/sql 定稿为准。
>
> FR 引用说明：PRD 未编 FR 号，本文以 PRD 章节号（§x.x）与澄清项（Cx）作为功能需求引用。

## 一、核心调用链总览

### 1.0 总体跨模块架构（规划）

```
浏览器（原生 HTML/CSS/JS 静态页，同源托管于 moments-web）
  users / friends / friend-detail / tags / friend-tags / mock-data / post-create / feed
     │ HTTP REST (JSON, Cookie: mockUserId = 当前用户, C3)      │ GET /images/{fileName}
     ▼                                                          ▼
┌────────────────────────────┐        ┌─────────────────────────────────────┐
│ moments-web（新增）         │ 静态   │ 本地图片文件夹（可配置路径, C16）     │
│  Controller x7 + 静态页 x8  │──映射──▶│ /images/** → file:{配置目录}         │
└─────────────┬──────────────┘        └─────────────────────────────────────┘
              │ 进程内接口调用（Controller → Service，三层规范）
              ▼
┌────────────────────────────┐
│ moments-service（新增）     │──▶ Redis：好友ID集合缓存 + 用户标签缓存
│  ServiceImpl x8            │    （canView 高频读；变更失效；失败回源 DB, C12）
└─────────────┬──────────────┘
              │ 进程内接口调用（Service → Mapper）
              ▼
┌────────────────────────────┐
│ moments-dao（新增）         │
│  Mapper x8 + DO x8 + XML x8 │
└─────────────┬──────────────┘
              │ MyBatis (JDBC)
              ▼
     MySQL 8.0（8 张新表）: user / friendship / friend_tag / friend_tag_relation /
        post / post_image / post_visibility_user / post_visibility_tag
```

### 1.1 一期链路：用户 / 好友 / 标签 / 模拟数据（全部新增）

```
┌───────────────────────────────────────────────────────────────────────────────┐
│                 Controller 层入口 · moments-web（一期，全部新增）               │
├────────────────┬──────────────────────┬─────────────────┬─────────────────────┤
│ UserController │ FriendshipController │ FriendTagCtrl   │ MockDataController  │
└───────┬────────┴──────────┬───────────┴────────┬────────┴──────────┬──────────┘
        │                   │                    │                   │
        ▼                   ▼                    ▼                   ▼
┌───────────────────────────────────────────────────────────────────────────────┐
│                 Service 层处理 · moments-service（一期，全部新增）              │
├────────────────┬──────────────────────┬─────────────────┬─────────────────────┤
│ UserServiceImpl│ FriendshipServiceImpl│FriendTagServiceImpl│MockDataServiceImpl│
└───────┬────────┴──────────┬───────────┴────────┬────────┴──────────┬──────────┘
        │                   │                    │                   │（复用左侧全部 Mapper）
        ▼                   ▼                    ▼                   ▼
┌───────────────────────────────────────────────────────────────────────────────┐
│                 Mapper / DAO 层 · moments-dao（一期，全部新增）                 │
├────────────────┬──────────────────────┬───────────────────────────────────────┤
│ UserMapper     │ FriendshipMapper     │ FriendTagMapper + FriendTagRelationMapper│
└────────────────┴──────────────────────┴───────────────────────────────────────┘
```

### 1.2 二期链路：发圈 / 隐私 / Feed / 图片（全部新增）

```
┌───────────────────────────────────────────────────────────────────────────────┐
│                 Controller 层入口 · moments-web（二期，全部新增）               │
├────────────────┬────────────────┬─────────────────────────────────────────────┤
│ PostController │ FeedController │ ImageController                             │
└───────┬────────┴───────┬────────┴────────────────┬────────────────────────────┘
        │                │                         │
        ▼                ▼                         ▼
┌───────────────────────────────────────────────────────────────────────────────┐
│                 Service 层处理 · moments-service（二期，全部新增）              │
├────────────────┬────────────────┬──────────────────────┬──────────────────────┤
│ PostServiceImpl│ FeedServiceImpl│ VisibilityServiceImpl│ LocalImageServiceImpl │
│                │ ──canView──▶   │ （无独立 Controller， │ （无 Mapper，         │
│                │                │  被 Post/Feed 调用）  │  写本地文件系统）      │
└───────┬────────┴───────┬────────┴──────────┬───────────┴──────────────────────┘
        │                │                   │
        ▼                ▼                   ▼（无 DB 边）
┌───────────────────────────────────────────────────────────────────────────────┐
│                 Mapper / DAO 层 · moments-dao（二期，全部新增）                 │
├────────────────────────────┬──────────────────────────────────────────────────┤
│ PostMapper + PostImageMapper│ PostVisibilityUserMapper + PostVisibilityTagMapper│
└────────────────────────────┴──────────────────────────────────────────────────┘
```

## 二、节点详情

> 所有 Controller 方法首参为 `String _appId`（java-guide 分层规范），当前用户由 Cookie `mockUserId` 解析、`userId` 查询参数可覆盖（C3）。下列路由为规划路由，定稿以阶段三 api.md 为准。

### 2.1 UserController 入口节点（新增）

| 方法 | 路由 | 涉及 FR | 当前行为（规划新增） |
|------|------|---------|---------|
| `listUsers` | GET /api/users | §2.1、§2.6、C3 | 用户列表（支撑"用户列表"页与用户切换器），无分页或简单分页 |
| `getUser` | GET /api/users/{userId} | §2.1、§2.6 | 用户详情（好友详情页头部展示） |

注：PRD §5 API 清单未列 users 端点，此为页面（§2.6 用户列表）+ 用户切换（C3）的最小必要支撑接口，同 C14 性质。

**UserServiceImpl 调用链（只读，无事务）：**
```
listUsers(_appId) / getUser(_appId, userId)
  └─ UserMapper.selectList() / selectByUserId(userId) // 查 user 表，is_del = 0
```

### 2.2 FriendshipController 入口节点（新增）

| 方法 | 路由 | 涉及 FR | 当前行为（规划新增） |
|------|------|---------|---------|
| `getFriends` | GET /api/friends | §2.2、§2.6 | 当前用户的好友列表（一期无添加好友 API，关系全部来自模拟数据） |
| `getFriendDetail` | GET /api/friends/{userId} | §2.2、§2.6 | 好友详情（基础信息 + 其标签 + 其朋友圈入口） |
| `getFriendsByTag` | GET /api/friends?tagId={tagId} | §2.4 | 按标签筛选好友 |

**FriendshipServiceImpl 调用链（只读，无事务）：**
```
getFriends(_appId)
  └─ FriendshipMapper.selectFriendIds(userId) // 查 friendship 表（双向关系存两条对称记录, C11）
     → UserMapper.selectByUserIds(friendIds)   // 批量取用户信息
getFriendsByTag(_appId, tagId)
  └─ FriendTagRelationMapper.selectFriendIdsByTag(tagId) → UserMapper.selectByUserIds(...) // 按标签查好友
```

### 2.3 FriendTagController 入口节点（新增）

| 方法 | 路由 | 涉及 FR | 当前行为（规划新增） |
|------|------|---------|---------|
| `listTags` | GET /api/friend-tags | §2.3、§2.4 | 当前用户的标签列表（含默认 8 标签语义） |
| `createTag` | POST /api/friend-tags | §2.4 | 创建标签（归属创建人, C5） |
| `updateTag` | PUT /api/friend-tags/{tagId} | §2.4 | 修改标签 |
| `deleteTag` | DELETE /api/friend-tags/{tagId} | §2.4、C13 | 软删标签 + 级联软删绑定关系；post_visibility_tag 不级联（自然失效） |
| `bindUser` | POST /api/friend-tags/{tagId}/users/{userId} | §2.4 | 好友-标签绑定 |
| `unbindUser` | DELETE /api/friend-tags/{tagId}/users/{userId} | §2.4 | 好友-标签解绑 |

**FriendTagServiceImpl.deleteTag 调用链：**
```
deleteTag(_appId, tagId) → @Transactional(rollbackFor = Exception.class)
  ├─ FriendTagMapper.selectByTagId(tagId)                    // 前置校验：存在性 + 归属（创建人）
  ├─ FriendTagMapper.updateIsDel(tagId)                      // 软删 friend_tag
  └─ FriendTagRelationMapper.updateIsDelByTagId(tagId)       // 级联软删绑定（C13）
  （事务提交后）Redis 标签缓存失效 —— 缓存操作在事务外，避免事务内操作 Redis
```

**FriendTagServiceImpl.bindUser / unbindUser 调用链：**
```
bindUser(_appId, tagId, userId) → @Transactional(rollbackFor = Exception.class)
  ├─ FriendTagMapper.selectByTagId(tagId)                          // 校验标签归属
  ├─ FriendshipMapper.existsFriendship(当前用户, userId)            // 校验好友关系存在
  ├─ FriendTagRelationMapper.selectByTagAndUser(tagId, userId)     // 先查重（幂等, java-guide 6.1）
  └─ FriendTagRelationMapper.insert(relation)                      // 绑定
  （事务提交后）Redis 好友标签缓存失效
```

### 2.4 MockDataController 入口节点（新增）

| 方法 | 路由 | 涉及 FR | 当前行为（规划新增） |
|------|------|---------|---------|
| `generate` | POST /api/mock-data（规划路由） | §2.5、C15、C16 | 按配置（用户数/人均好友数/标签数, clear 默认 false）批量生成模拟数据；按 §2.5 真实社交分布（同事25%/同学20%/朋友25%/家人5%/亲戚5%/兴趣15%/其他5%） |

**MockDataServiceImpl.generate 调用链：**
```
generate(_appId, config)
  ├─（clear=true 时，事务内）UserMapper/FriendshipMapper/FriendTagMapper/FriendTagRelationMapper
  │        /PostMapper/PostImageMapper/PostVisibilityUserMapper/PostVisibilityTagMapper 批量软删清空
  ├─（事务外，文件 IO 不进事务）LocalImageService.generatePlaceholderGrid(1..9) // 占位图, C16
  ├─（分批事务, 每批 ≤500）UserMapper.batchInsert(users)                          // 用户
  ├─（分批事务）FriendshipMapper.batchInsert(对称双记录 A→B + B→A, C11)           // 1000+ 好友关系
  ├─（分批事务）FriendTagMapper.batchInsert + FriendTagRelationMapper.batchInsert // 标签按社交分布
  └─（完成后）Redis 好友/标签缓存全量失效
```
注：批量生成必须分批提交（防长事务），批大小上限按 java-guide 7.2（≤500）。

### 2.5 PostController 入口节点（新增）

| 方法 | 路由 | 涉及 FR | 当前行为（规划新增） |
|------|------|---------|---------|
| `createPost` | POST /api/posts | §3.1、§3.2、C17 | 发布朋友圈（纯文字/纯图/图文，最多 9 图；visibilityType 1-4 落库） |
| `getPost` | GET /api/posts/{postId} | §3.2 | 帖子详情（经 canView 权限校验） |
| `deletePost` | DELETE /api/posts/{postId} | C7 | 软删除（is_del 置 1；status 保留业务态 1=正常 0=删除） |
| `listUserPosts` | GET /api/posts?userId={userId} | C14 | 查指定用户帖子（经 canView 过滤，支撑"好友详情"页） |

**PostServiceImpl.createPost 调用链：**
```
createPost(_appId, in) → @Transactional(rollbackFor = Exception.class)
  ├─ 入参校验：图片数 ≤9 / 格式 jpg, png, gif, webp / 单张 ≤5MB（C17）
  ├─ PostMapper.insert(post)                        // post 表：user_id, content, visibility_type, status=1
  ├─ PostImageMapper.batchInsert(postId, images)    // post_image 表：image_url + sort（图片单独存储带排序）
  ├─ PostVisibilityUserMapper.batchInsert(postId, userIds) // visibilityType=3/4 且指定好友时
  └─ PostVisibilityTagMapper.batchInsert(postId, tagIds)  // visibilityType=3/4 且指定标签时
```

**PostServiceImpl.getPost / deletePost 调用链：**
```
getPost(_appId, postId)
  ├─ PostMapper.selectById(postId)                  // is_del = 0
  ├─ VisibilityServiceImpl.canView(当前用户, postId) // 统一权限判断，不可见则拒绝
  └─ PostImageMapper.selectByPostId(postId)         // 组装图片列表（按 sort）
deletePost(_appId, postId) → @Transactional(rollbackFor = Exception.class)
  ├─ PostMapper.selectById(postId) + 作者本人校验     // 修改/删除前校验归属（java-guide 9）
  └─ PostMapper.updateIsDel(postId)                 // 软删
```

### 2.6 FeedController 入口节点（新增）

| 方法 | 路由 | 涉及 FR | 当前行为（规划新增） |
|------|------|---------|---------|
| `getFeed` | GET /api/feed?cursor={cursor}&pageSize=20 | §3.9、§3.10、§3.11、C4、C8 | 自己 + 好友的帖子，权限过滤，createTime DESC + id DESC 双字段排序，Cursor 分页（不 Offset）；返回 items / nextCursor / hasMore |

**FeedServiceImpl.getFeed 调用链（只读，无事务）：**
```
getFeed(_appId, cursor, pageSize)
  ├─ Redis GET 好友ID集合缓存 → miss 则 FriendshipMapper.selectFriendIds(当前用户) 回源并回填（C12）
  ├─ PostMapper.selectFeedPage(userId + friendIds, cursor, pageSize)
  │        // create_time DESC, id DESC；cursor = Base64("createTime:postId"), C8
  ├─ VisibilityServiceImpl.canView(当前用户, postId)   // 逐帖权限过滤（仅好友帖需过滤）
  ├─ PostImageMapper.selectByPostIds(postIds)          // 批量取图，避免 N+1
  └─ UserMapper.selectByUserIds(authorIds)             // 批量取作者头像/昵称（Feed 展示项）
```

### 2.7 ImageController 入口节点（新增）

| 方法 | 路由 | 涉及 FR | 当前行为（规划新增） |
|------|------|---------|---------|
| `upload` | POST /api/images/upload | §7、C16、C17 | 上传图片至本地文件夹（可配置路径），返回 `/images/{fileName}` 访问 URL；校验 jpg/png/gif/webp、≤5MB |

注：不复用已有 infra FileController/FileServiceImpl（走公司 FileClient 对象存储，与 PRD §7 本地文件夹存储不符，见 domain.md 已排除领域）。

**LocalImageServiceImpl.upload 调用链（无事务，无 Mapper）：**
```
upload(_appId, file)
  ├─ 校验：扩展名 ∈ {jpg, png, gif, webp}、size ≤ 5MB（C17）
  ├─ 生成唯一文件名（UUID/时间戳前缀，防覆盖）
  ├─ Files.write(path, bytes) → 本地图片文件夹（可配置根目录）
  └─ 返回 imageUrl = /images/{fileName}（由 moments-web 静态映射 /images/** → file:{目录} 提供访问）
```

### 2.8 VisibilityServiceImpl（新增，无独立 Controller 的内部节点）

```
canView(userId, postId) —— 统一隐私判断（§3.8）
  ├─ PostMapper.selectById(postId)                          // 取作者 + visibilityType
  ├─ 作者本人 → true；visibilityType=1(公开) → true；=2(私密) → false
  ├─ visibilityType=3(部分可见)：
  │    ├─ PostVisibilityTagMapper.selectTagIdsByPostId → Redis 标签缓存（miss 回源 FriendTagRelationMapper）// 作者给好友打的标签匹配
  │    ├─ PostVisibilityUserMapper.selectUserIdsByPostId    // 指定好友集合
  │    └─ 标签命中 OR 好友命中 → true；空集 → 除作者外不可见（C6）
  └─ visibilityType=4(不给谁看)：
       └─ 标签命中 OR 好友命中 → false；空集 → 等同公开（C6）
```

## 三、边关系

### 3.1 跨模块边（通信方式 / 方向 / 接口路径）

| 起点 | 终点 | 关系 | 数据流 |
|------|------|------|--------|
| 前端静态页（同源托管） | moments-web 各 Controller | HTTP（同源 REST，方向：页面 → 服务端） | 页面交互发 JSON 请求，路由见 §5 API 清单（/api/users、/api/friends、/api/friend-tags、/api/mock-data、/api/posts、/api/feed、/api/images/upload）；Cookie 携带 mockUserId（C3） |
| 浏览器 | moments-web 静态资源映射 | HTTP GET（方向：页面 → 服务端） | GET /images/{fileName} → 磁盘文件读取（本地图片文件夹） |
| moments-web | moments-service | 进程内 Java 接口调用（方向：Controller → Service） | IXxxService 接口调用（三层规范） |
| moments-service | moments-dao | 进程内 Java 接口调用（方向：Service → Mapper） | XxxMapper 接口 + XML SQL |
| moments-dao | MySQL 8.0 | MyBatis/JDBC（读写） | 8 张新表读写（见 §3.2） |
| moments-service | Redis | RedisTemplate（读写缓存） | 好友 ID 集合缓存 + 用户标签缓存；好友/标签变更时失效；失败回源 DB（C12） |
| moments-web（LocalImageService 写入方在 service） | 本地图片文件夹 | 文件系统写入（NIO Files） | 上传图片字节流 + 模拟占位图（C16） |

### 3.2 进程内调用边（Controller→Service、Service→Mapper、跨 Service）

| 起点 | 终点 | 关系 | 数据流 |
|------|------|------|--------|
| UserController.listUsers/getUser | IUserService | CALLS | userId（可空）→ UserOut 列表/详情 |
| FriendshipController.getFriends/getFriendDetail/getFriendsByTag | IFriendshipService | CALLS | 当前用户 userId / tagId → 好友列表、好友详情（含标签） |
| FriendTagController.* | IFriendTagService | CALLS | tagId、tagIn、userId → 标签 CRUD、绑定/解绑结果 |
| MockDataController.generate | IMockDataService | CALLS | MockDataGenerateIn{用户数/人均好友数/标签数/clear} → 生成统计结果 |
| PostController.createPost/getPost/deletePost/listUserPosts | IPostService | CALLS | PostCreateIn{content, imageUrls[], visibilityType, visibilityUserIds[], visibilityTagIds[]} / postId / userId |
| FeedController.getFeed | IFeedService | CALLS | cursor、pageSize → {items[], nextCursor, hasMore} |
| ImageController.upload | ILocalImageService | CALLS | MultipartFile → imageUrl |
| UserServiceImpl | UserMapper | CALLS | selectList / selectByUserId / selectByUserIds / batchInsert |
| FriendshipServiceImpl | FriendshipMapper | CALLS | selectFriendIds / existsFriendship / batchInsert（对称双记录） |
| FriendshipServiceImpl | UserMapper | CALLS | selectByUserIds（好友信息组装，跨 Mapper） |
| FriendTagServiceImpl | FriendTagMapper / FriendTagRelationMapper | CALLS | CRUD / selectFriendIdsByTag / 级联软删 |
| PostServiceImpl | PostMapper / PostImageMapper / PostVisibilityUserMapper / PostVisibilityTagMapper | CALLS | insert / batchInsert / selectById / updateIsDel |
| FeedServiceImpl | FriendshipMapper / PostMapper / PostImageMapper / UserMapper | CALLS | selectFriendIds / selectFeedPage(cursor) / selectByPostIds / selectByUserIds |
| FeedServiceImpl | VisibilityServiceImpl | CALLS（跨 Service） | canView(userId, postId) 逐帖过滤 |
| PostServiceImpl | VisibilityServiceImpl | CALLS（跨 Service） | canView(userId, postId) 详情/列表权限校验 |
| VisibilityServiceImpl | PostMapper / PostVisibilityUserMapper / PostVisibilityTagMapper / FriendTagRelationMapper | CALLS | 权限判断数据源 |
| MockDataServiceImpl | IUserService / IFriendshipService / IFriendTagService / ILocalImageService | CALLS（跨 Service，复用） | 批量插入用户/好友/标签；生成占位图（C16） |

## 四、社区聚类

### 社区1: 用户（user，§2.1、C3）
- UserController.listUsers / getUser → UserServiceImpl → UserMapper
- **数据表**: user（新建：nickname、avatar、gender、city 等）

### 社区2: 好友关系（friendship，§2.2、C11）
- FriendshipController.getFriends / getFriendDetail / getFriendsByTag → FriendshipServiceImpl → FriendshipMapper（+ UserMapper 组装）
- **数据表**: friendship（新建，双向好友存两条对称记录 A→B / B→A）

### 社区3: 好友标签（friend-tag，§2.3、§2.4、C5、C13）
- FriendTagController 全部 6 端点 → FriendTagServiceImpl → FriendTagMapper + FriendTagRelationMapper
- **数据表**: friend_tag（新建，user_id=标签创建人）、friend_tag_relation（新建，标签-好友绑定）

### 社区4: 模拟数据（mock-data，§2.5、C15、C16）
- MockDataController.generate → MockDataServiceImpl → 复用社区1/2/3 全部 Mapper + LocalImageService 占位图
- **数据表**: 写全部 8 张表（清空重建或追加）；1000+ 好友关系，按真实社交分布

### 社区5: 朋友圈发布（post，§3.1、§3.2、C7、C14、C17）
- PostController.createPost / getPost / deletePost / listUserPosts → PostServiceImpl → PostMapper + PostImageMapper + 可见性两 Mapper
- **数据表**: post（新建：content、visibility_type、status）、post_image（新建：image_url、sort）

### 社区6: 隐私权限（visibility，§3.3-§3.8、C6）
- VisibilityServiceImpl.canView（内部节点，被社区5/7 调用）→ PostVisibilityUserMapper + PostVisibilityTagMapper + FriendTagRelationMapper + Redis 缓存
- **数据表**: post_visibility_user（新建）、post_visibility_tag（新建）；4 种可见范围，标签 OR 好友匹配，空集语义见 C6

### 社区7: 朋友圈 Feed（feed，§3.9-§3.11、C4、C8、C12）
- FeedController.getFeed → FeedServiceImpl → FriendshipMapper + PostMapper + PostImageMapper + UserMapper + VisibilityServiceImpl + Redis
- **数据表**: 读 post / post_image / friendship + 权限两表；createTime DESC + id DESC，Cursor 分页

### 社区8: 图片（image，§7、C16、C17）
- ImageController.upload → LocalImageServiceImpl → 本地文件系统（无 Mapper）
- **数据表**: 无（文件落本地文件夹，/images/{fileName} 静态映射访问）

## 五、数据流汇总

### 表字段现状（8 张表全部不存在，均需新建）

> ⚠️ 绿地项目：下列表当前均不存在，字段为规划字段（依据 PRD §4.1/§4.2 + 澄清 C5/C7/C11 + mysql-guide 通用字段规范），❌ 标记整表需新建；字段级定稿以阶段三 sql.md 为准。所有表含通用字段：id(bigint 主键)、created_stime、modified_stime、is_del。

#### user 表（❌ 不存在，需新建）
| 字段 | 类型 | 说明 | 使用场景 |
|------|------|------|---------|
| id | bigint | 主键（即 userId） | §2.1 全部用户功能 |
| nickname | varchar | 昵称 | §2.1、§3.9 Feed 展示 |
| avatar | varchar | 头像 URL | §2.1、§3.9 Feed 展示 |
| gender | tinyint | 性别 | §2.1 |
| city | varchar | 城市 | §2.1 |

#### friendship 表（❌ 不存在，需新建）
| 字段 | 类型 | 说明 | 使用场景 |
|------|------|------|---------|
| id | bigint | 主键 | — |
| user_id | bigint | 关系一方 | §2.2、§3.11 Feed 好友圈获取 |
| friend_user_id | bigint | 关系另一方 | 同上；双向关系存两条对称记录（C11），索引 idx_user_id |

#### friend_tag 表（❌ 不存在，需新建）
| 字段 | 类型 | 说明 | 使用场景 |
|------|------|------|---------|
| id | bigint | 主键 | — |
| user_id | bigint | 标签创建人（标签归属创建人, C5） | §2.3、§3.6/§3.7 作者视角标签匹配 |
| tag_name | varchar | 标签名（默认 8 标签） | §2.3 |

#### friend_tag_relation 表（❌ 不存在，需新建）
| 字段 | 类型 | 说明 | 使用场景 |
|------|------|------|---------|
| id | bigint | 主键 | — |
| tag_id | bigint | 标签 ID | §2.4 绑定/解绑、按标签查好友 |
| friend_user_id | bigint | 被打标签的好友 | 同上；删除标签时级联软删（C13） |

#### post 表（❌ 不存在，需新建）
| 字段 | 类型 | 说明 | 使用场景 |
|------|------|------|---------|
| id | bigint | 主键（postId） | §3.1-§3.11 |
| user_id | bigint | 作者 | §3.8 权限、Feed |
| content | text/varchar | 文字内容 | §3.1（长度定稿以 sql.md 为准） |
| visibility_type | tinyint | 可见范围 1 公开/2 私密/3 部分可见/4 不给谁看 | §3.3-§3.8 |
| status | tinyint | 业务态 1 正常 0 删除（软删走 is_del, C7） | C7 |

#### post_image 表（❌ 不存在，需新建）
| 字段 | 类型 | 说明 | 使用场景 |
|------|------|------|---------|
| id | bigint | 主键 | — |
| post_id | bigint | 所属帖子 | §3.2 |
| image_url | varchar | 图片 URL（/images/{fileName}） | §3.9 Feed 展示 |
| sort | int | 排序（≤9 图） | §3.1 |

#### post_visibility_user 表（❌ 不存在，需新建）
| 字段 | 类型 | 说明 | 使用场景 |
|------|------|------|---------|
| id | bigint | 主键 | — |
| post_id | bigint | 所属帖子 | §3.6/§3.7 指定好友 |
| user_id | bigint | 指定可见/不可见用户 | §3.8 canView |

#### post_visibility_tag 表（❌ 不存在，需新建）
| 字段 | 类型 | 说明 | 使用场景 |
|------|------|------|---------|
| id | bigint | 主键 | — |
| post_id | bigint | 所属帖子 | §3.6/§3.7 指定标签 |
| tag_id | bigint | 指定可见/不可见标签 | §3.8 canView；删除标签不级联（C13，按 tagId 匹配自然失效） |

### 当前 Service 审核状态现状
- 绿地项目：**无任何既有业务 Service**，不存在既有审核状态需变更。
- 内容审核明确不做：PRD §3.1"暂不实现内容审核"、§8 项目边界。规划中帖子直接可见，无 auditStatus 概念。
- 帖子生命周期仅有两态语义需设计定稿：status（业务态 1=正常 0=删除，C7）与 is_del（软删标准字段）并存，发布时 status=1；`DELETE /api/posts/{postId}` 置 is_del=1。

## 六、改动影响范围

> 全部为新增文件/新增表（绿地）。**关联关系图缺失，跨模块边未验证**：`knowledge/项目关联关系图-API.md` 与 `knowledge/项目关联关系图-中间件.md` 均不存在，本表跨模块交互边来自 domain.md「三、跨模块交互」推导，未经桥接文档复核。具体文件名以阶段三 design/tasks 定稿为准。

| 文件 | 所在模块 | 改动类型 | 涉及 FR |
|------|---------|---------|---------|
| controller/UserController.java | moments-web | 新增 | §2.1、C3 |
| controller/FriendshipController.java | moments-web | 新增 | §2.2、§2.4 |
| controller/FriendTagController.java | moments-web | 新增 | §2.3、§2.4 |
| controller/MockDataController.java | moments-web | 新增 | §2.5、C15 |
| controller/PostController.java | moments-web | 新增 | §3.1、§3.2、C7、C14 |
| controller/FeedController.java | moments-web | 新增 | §3.9-§3.11 |
| controller/ImageController.java | moments-web | 新增 | §7、C16、C17 |
| WebMvc 配置（静态页托管 + /images/** → file:{目录} 映射） | moments-web | 新增 | C2、C16 |
| resources/static/ 页面 x8（用户列表/我的好友/好友详情/标签管理/好友标签管理/模拟数据生成/发布页/朋友圈瀑布流） | moments-web | 新增 | §2.6、§3.9、C2 |
| service/IUserService.java + impl/UserServiceImpl.java | moments-service | 新增 | §2.1 |
| service/IFriendshipService.java + impl/FriendshipServiceImpl.java | moments-service | 新增 | §2.2 |
| service/IFriendTagService.java + impl/FriendTagServiceImpl.java | moments-service | 新增 | §2.3、§2.4、C13 |
| service/IMockDataService.java + impl/MockDataServiceImpl.java | moments-service | 新增 | §2.5、C15 |
| service/IPostService.java + impl/PostServiceImpl.java | moments-service | 新增 | §3.1、§3.2 |
| service/IFeedService.java + impl/FeedServiceImpl.java | moments-service | 新增 | §3.9-§3.11 |
| service/IVisibilityService.java + impl/VisibilityServiceImpl.java | moments-service | 新增 | §3.3-§3.8、C6 |
| service/ILocalImageService.java + impl/LocalImageServiceImpl.java | moments-service | 新增 | §7、C16、C17 |
| Redis 缓存组件（好友 ID 集合 + 标签缓存，变更失效） | moments-service | 新增 | C12 |
| mapper/UserMapper.java + XML + UserDO | moments-dao | 新增 | §2.1 |
| mapper/FriendshipMapper.java + XML + FriendshipDO | moments-dao | 新增 | §2.2、C11 |
| mapper/FriendTagMapper.java + XML + FriendTagDO | moments-dao | 新增 | §2.3、C5 |
| mapper/FriendTagRelationMapper.java + XML + FriendTagRelationDO | moments-dao | 新增 | §2.4、C13 |
| mapper/PostMapper.java + XML + PostDO | moments-dao | 新增 | §3.1、§3.2、C7 |
| mapper/PostImageMapper.java + XML + PostImageDO | moments-dao | 新增 | §3.1、§3.2 |
| mapper/PostVisibilityUserMapper.java + XML + PostVisibilityUserDO | moments-dao | 新增 | §3.6、§3.7 |
| mapper/PostVisibilityTagMapper.java + XML + PostVisibilityTagDO | moments-dao | 新增 | §3.6、§3.7、C13 |
| DDL：8 张新表建表脚本 | sql | 新增 | §4.2 |
| 不改动：infra 脚手架（FileController/FileServiceImpl/SecurityController/ApolloController/CodeGenerator） | moments-* | 复用/不动 | 最小改动原则（见 domain.md 已排除领域） |

## 七、风险点

1. **降级模式风险（流程）**: graphify 图谱不存在，本文链路基于 domain.md + PRD 推导，未经图谱验证；类名/路由/字段均为规划值，阶段三 design 必须逐一定稿，禁止直接照抄本文档当实现契约。
2. **跨模块边未验证（流程）**: 两份项目关联关系图桥接文件缺失，moments-web → moments-service → moments-dao 的装配方式（依赖注入/bean 扫描配置）需在阶段三对照项目脚手架实际装配方式确认。
3. **Feed 权限过滤性能**: 1000+ 好友 → `selectFeedPage` 的 IN 列表与逐帖 canView 存在 N+1 放大风险；需好友 ID 集合 Redis 缓存（C12）+ 可见性批量判断/SQL 预过滤方案在阶段三定稿；10 万级扩展（§2.5）下需分批加载。
4. **缓存一致性**: 好友/标签变更（mock-data 重建、标签绑定/解绑/删除）必须失效 Redis 缓存，且缓存操作置于事务提交之后（java-guide 禁止事务内操作 Redis）；缓存失败回源 DB 的降级路径需测试覆盖。
5. **事务边界**: 发帖一次写 4 表（post + post_image + 可见性两表）需同一事务（@Transactional(rollbackFor = Exception.class)）；删标签级联软删（C13）、mock-data 批量生成（防长事务，分批 ≤500 提交）均需明确事务切分；文件 IO（占位图/上传）必须移出事务。
6. **Cursor 分页稳定性**: 同秒多条帖子需 create_time DESC + id DESC 双字段排序保证稳定分页（C8）；cursor 为 Base64 明文编码，需校验解码失败/伪造输入的防御处理（外部输入不可信）。
7. **双向好友一致性**: 对称双记录（A→B 与 B→A，C11）必须同事务成对写入，任何单条缺失会导致 Feed 单向可见；mock-data 与后续任何好友写入路径都要保证对称。
8. **本地图片存储**: 上传路径可配置（禁止硬编码）；静态映射 /images/** 需防路径穿越（fileName 白名单字符校验）；本地文件夹无备份/扩展性（PRD §7 明确接受，架构实验室定位）。
9. **软删除联动**: 帖子软删后 Feed / 详情 / listUserPosts / 可见性判断各查询路径都要过滤 is_del = 1（mysql-guide 每查询带软删条件）；图片文件是否物理删除需设计定稿（建议保留，最小改动）。
10. **模拟数据幂等与规模**: generate 默认追加、clear=true 清空重建（C15），重复生成幂等性需明确（先查重/清空语义）；10 万级用户扩展时批量插入与索引设计需评估。
11. **_appId 与当前用户解析**: 全部 API 首参 _appId + Cookie mockUserId（C3）的解析统一在 web 层拦截/工具方法处理，避免每个 Controller 重复实现造成口径不一。

---
*Change: 20260920-social-timeline-mvp（phase-dir: 无，日常单变更开发，产物落 change 根目录）*
*Generated: 2026-09-20（graph-query 降级模式）*
