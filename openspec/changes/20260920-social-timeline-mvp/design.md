# 技术详细设计文档 — Social Timeline MVP

> 基于模板：`workflow/daily/templates/technical-design-template-simple.md` v1.0
> 关联 prd：`openspec/changes/20260920-social-timeline-mvp/prd.md`
> 生成时间：2026-09-20

## 文档信息

| 字段 | 内容 |
|------|------|
| 文档编号 | TDD-ST-001 |
| 模块名称 | moments（social-timeline） |
| 功能名称 | 一期好友关系 + 二期朋友圈隐私 Feed MVP |
| 关联 PRD | openspec/changes/20260920-social-timeline-mvp/prd.md（含澄清 C1-C17）+ explore.md（D1-D7） |
| 作者 | tech-designer-simple（AI） |
| 审阅人 | adversarial-reviewer（阶段三对抗）→ 用户（阶段五追认） |
| 创建日期 | 2026-09-20 |
| 最后更新 | 2026-09-20 |
| 状态 | 评审中 |

---

## 1. 概述

### 1.1 背景

Social Timeline 是社交 Feed 系统架构实验室项目（PRD §1.1）。本次变更一次性交付 MVP 全量：一期（用户/好友关系/好友标签/模拟数据 + 6 个管理页面，PRD §2）+ 二期（朋友圈图文发布/4 种隐私可见范围/统一 canView/Cursor 分页 Feed 瀑布流，PRD §3）。技术约束：MySQL 8.0 + Redis + 本地文件夹图片存储（PRD §7）；前端原生 HTML/CSS/JS 静态页同源托管（C2）。

**仓库现状**：moments-* 五模块仅含公司脚手架 infra 代码（FileController/SecurityController/ApolloController/FeatureTestController 等），无任何社交业务代码；`frontend/` 目录为空。**业务代码无复用基线**（全新建设）；但框架基座复用充分，见 §1.4。

### 1.2 目标

| # | 目标 | PRD 溯源 |
|---|------|---------|
| G1 | 用户列表/详情查询（支撑页面与用户切换器） | §2.1、§2.6、C3 |
| G2 | 好友列表/好友详情/按标签查好友 | §2.2、§2.4、§2.6 |
| G3 | 标签 CRUD + 好友-标签绑定/解绑（含级联软删） | §2.3、§2.4、C5、C13 |
| G4 | 模拟数据批量生成（1000+ 好友关系、真实社交分布） | §2.5、C15、C16 |
| G5 | 朋友圈发布（纯文/纯图/图文，≤9 图，4 表同事务） | §3.1、§3.2、C17 |
| G6 | 4 种隐私可见范围 + 统一 canView 判断 | §3.3-§3.8、C6 |
| G7 | Cursor 分页 Feed 瀑布流（自己+好友，权限过滤） | §3.9-§3.11、C4、C8、C12 |
| G8 | 本地图片上传 + /images/** 静态访问 + 占位图 | §7、C16、C17 |
| G9 | 8+1 个前端静态页（样式贴近微信朋友圈） | §2.6、§3.9、C2 |
| G10 | 数据级（JUnit + dev 库断言）+ 页面级（Playwright）测试 | 用户目标（explore §二） |

### 1.3 范围

| 范围 | 说明 |
|------|------|
| 包含 | 8 个新 Controller 端点域（18 个接口）、8 个新 Service、8 张新表 + 8 Mapper、Redis 两类缓存、本地图片存储、静态资源映射、8+1 静态页、dev 幂等 DDL Runner、BizException 全局处理 |
| 不包含 | 注册登录/好友申请/内容审核/视频/点赞/评论/转发/收藏/通知/推荐/广告（PRD §8）；Offset 分页；三期高并发推拉架构；签名认证（C10 已决策豁免） |

### 1.4 术语与缩写

| 术语 | 说明 |
|------|------|
| FR/NFR | 功能/非功能需求 |
| canView | 统一隐私判断 viewer 对 post 的可见性（§3.8） |
| Cursor 分页 | 基于 (created_stime, id) 双字段游标的 keyset 分页（C8） |
| viewer | 当前视角用户（Cookie `mockUserId`，可被 `viewerId` 参数覆盖，C3） |
| 占位图 | 本地生成的纯色 PNG，用于模拟头像/帖子图（C16、D5） |

### 1.5 复用基线（源码检索结论）

**业务层仓库全新，无复用基线**；以下框架/脚手架能力直接复用（均已验证存在）：

| 复用项 | 路径 | 用途 |
|--------|------|------|
| ApiResult<T> | `com.funny.framework.core.result.ApiResult`（funny-framework-core 1.0.3） | 统一响应体 `{code, msg, data}`，`succ()/fail()/buildFailure(code,msg)` |
| BizException | `com.funny.framework.core.exception.BizException` | 业务异常 `(int code, String message)`，RuntimeException |
| RedisClient | `com.funny.framework.redis.RedisClient`（funny-framework-redis 1.0.3，starter 自动装配） | 缓存读写：`get/set/del/sadd/smembers/expire/incr` |
| MyBatis-Plus | funny-springboot-starter-dao（moments-dao 引入） | Mapper 继承 `BaseMapper<XxxDO>`，`@TableLogic` 软删 |
| MapperScan | [复用: moments-web/src/main/java/com/funny/moments/Application.java] `com.funny.moments.dao.mapper` 已扫描，XML 落 `moments-dao/src/main/resources/mapper/`（CodeGenerator pathInfo 既有约定） |
| CookieUtils | [复用: moments-web/src/main/java/com/funny/moments/utils/CookieUtils.java] `getCookieValueByName` 解析 mockUserId |
| DateUtils | [复用: moments-common/.../utils/DateUtils.java] `PATTERN`/`FORMAT` 常量（yyyy-MM-dd HH:mm:ss） |
| CacheKeyConsts | [复用: moments-common/.../consts/CacheKeyConsts.java] 追加缓存 key 常量（`%s` 占位风格） |
| PatternConsts | [复用: moments-common/.../consts/PatternConsts.java] 追加文件名白名单正则常量 |
| 异常处理范式 | [复用范式: moments-web/.../config/SignExceptionAdvice.java] 新增 BizExceptionAdvice 同款 `@RestControllerAdvice + @Order` |
| In/Out 包结构 | [复用: moments-client/src/main/java/com/funny/moments/model/in|out/] 三层项目 XxxIn/XxxOut 落 client 模块 |

**不改动**（最小 diff）：infra 全部控制器、FileServiceImpl（走 COS 对象存储，与 PRD §7 本地文件夹不符，见 domain.md 排除结论）、DemoSignConfig、ColaExtensionConfig。

---

## 2. 架构设计

### 2.1 系统上下文

```
浏览器（原生静态页 x9，classpath:/static 同源托管）
  │ REST JSON（Cookie: mockUserId）        │ GET /images/{fileName}
  ▼                                        ▼
moments-web                                本地图片文件夹（可配置，默认 ${user.home}/moments-data/images，建议 9）
  Controller x7（web.controller.social）    ▲ Files.write（上传/占位图）
  + WebConfig 追加 /images/** 映射          │
  + CurrentUserResolver / BizExceptionAdvice / SchemaInitRunner(dev)
  │ 进程内调用
  ▼
moments-service（service.social + service.social.impl）
  ServiceImpl x8 + 缓存组件 x2（FriendIdsCacheManager / FriendTagCacheManager）
  │            │
  │            └──▶ Redis（RedisClient）：好友ID集合 + 标签绑定，全局版本号失效，失败回源 DB（C12）
  ▼
moments-dao（dao.entity XxxDO x8 / dao.mapper XxxMapper x8 + resources/mapper/*.xml x8）
  │ MyBatis (JDBC)
  ▼
MySQL 8.0：user / friendship / friend_tag / friend_tag_relation /
           post / post_image / post_visibility_user / post_visibility_tag（8 张新建）
```

### 2.2 模块归属

| 模块 | 新增内容 | 修改内容 |
|------|---------|---------|
| moments-client | `model.in` x4（TagCreateIn/TagEditIn/MockDataGenerateIn/PostCreateIn；用户/好友/Feed 已散参化，R3-建议3 修正数字） / `model.out` x9（XxxIn/XxxOut） | 无 |
| moments-common | `enums` x4 + `SocialErrorCode`；`consts` CacheKeyConsts/PatternConsts 追加常量 | 追加常量（最小 diff，不动既有行） |
| moments-dao | `dao.entity` DO x8、`dao.mapper` Mapper x8、`resources/mapper/*.xml` x8、`resources/schema/social_timeline.sql`（Runner 数据源） | 无 |
| moments-service | `service.social` 接口 x8 + `impl` x8 + `cache` 组件 x2（FriendIdsCacheManager / FriendTagCacheManager）；图片根目录经 @Value 注入（R2-建议3，无独立配置类） | 无 |
| moments-web | `web.controller.social` Controller x7、`config/BizExceptionAdvice`、`config/SchemaInitRunner`、`web/support/CurrentUserResolver`、`resources/static` 页面 x9 | `config/WebConfig` 仅追加 `addResourceHandlers`（/images/** → file:{配置目录}，既有 CORS/拦截器不动）+ **编程式 multipart @Bean（MultipartConfigElement：maxFileSize=6MB / maxRequestSize=12MB，Stage 6 裁决4 补录：为 5MB 应用级校验留余量并拦超限请求体）** |

### 2.3 关键架构决策

| # | 决策 | 依据 |
|---|------|------|
| A1 | In/Out 落 moments-client（非 web/dto） | 模块定位"对外 API model"；与 SeriesIn/SeriesOut 既有结构一致 |
| A2 | Service 接口 `IXxxService` 放 `service.social`，避开既有 `service.infra` | 与 IFileService 命名/分包一致 |
| A3 | 当前用户解析：`viewerId` query 参数 > Cookie `mockUserId`，缺失抛 CURRENT_USER_REQUIRED；viewerId 显式传入时强校验：非纯数字 / ≤0 / 超 Long 范围（>18 位数字）一律抛 1001，**不静默回退 Cookie**（显式参数错误必须显式暴露）；Cookie 值非法（脏数据）则视为未选择（1003，宽松） | C3 定稿实现细节：覆盖参数命名 viewerId（非 userId），避免与 `GET /api/posts?userId=`（C14 目标作者）同名冲突；校验依据 java-guide 7.2（数值上下界同时校验）+ 防 user_id=0 脏数据落库 |
| A4 | Redis 缓存两类，全局版本号失效（`moments:fver` / `moments:ftagver` INCR） | C12；bind/unbind/deleteTag/mockData 后 INCR 全局版本使旧 key 自然失效，避免 SCAN 批删；低频管理操作的全量回源代价可接受（权衡说明：标签管理页连续绑定会多回源，实验室规模无感） |
| A5 | Feed 权限过滤：SQL 预过滤私密帖 + type=1 直通 + type=3/4 走统一 canView，放大取数 pageSize×3 最多 3 批 | R1；保证统一语义单实现（PRD §3.8）与页大小稳定性（§5.4） |
| A6 | DDL：`resources/schema/social_timeline.sql`（CREATE TABLE IF NOT EXISTS）+ `SchemaInitRunner`（@Profile("dev")，ScriptUtils） | D1 |
| A7 | 占位图为纯色 PNG（BufferedImage，无文字），头像 200×200 ×20、内容图 600×600 ×20 循环复用 | D5；避免字体依赖与海量小文件；10 万用户仅 40 个图片文件 |
| A8 | mock 数据严格按 PRD §2.5 三项配置（用户数/人均好友数/标签数），另加可选 `postCount`（默认 0） | postCount 为 C14 同性质最小扩展：页面级测试（用户目标）需要 Feed 有存量帖子可验隐私，标注待用户追认 |
| A9 | 社交分布权重映射到 8 默认标签：同事25/同学20/朋友25/家人5/亲戚5/球友10/客户5/其他5 | PRD §2.5 示例的"兴趣好友 15%"拆为球友10+客户5（8 默认标签无"兴趣好友"，球友/客户为兴趣类） |
| A10 | BizExceptionAdvice 新增（@RestControllerAdvice 处理 BizException → `ApiResult.buildFailure(code, msg)`） | 框架 GlobalExceptionAdvice 仅兜底 Exception→code 100，无法透传业务码；仿 SignExceptionAdvice 既有范式，不改既有文件 |
| A11 | **DDL 双版本兼容**：本机 dev 实际数据库为 **MySQL 5.7.44**（brew 服务，无 Docker、无 8.0 实例），PRD 的 mysql 8.0 为目标环境。DDL 必须同时兼容 5.7 与 8.0：字符集统一 `utf8mb4` + `utf8mb4_general_ci`（**禁 8.0 专有 `utf8mb4_0900_ai_ci`**）、无 DEFAULT 表达式函数、无函数索引、无 CHECK 约束 | 本地 dev 环境实测约束（2026-09-20 补充）；DDL 详见 sql.md（含兼容性标注），`DEFAULT CURRENT_TIMESTAMP` / `ON UPDATE CURRENT_TIMESTAMP` 为两版本共同支持的标准形式，可继续使用 |

---

## 3. 接口设计

> 真源章节，api.md 由本节派生。所有 Controller 方法**首参 `String _appId`（无注解）**（java-guide）。当前用户 viewer 由 CurrentUserResolver 从请求解析（A3），不占接口参数表（api.md 通用约定说明）。

### 3.1 接口总览（18 个）

| # | Method | URL | 说明 | 权限 |
|---|--------|-----|------|------|
| 1 | GET | `/api/users` | 用户列表（分页，支撑用户列表页与切换器） | 无 |
| 2 | GET | `/api/users/{userId}` | 用户详情 | 无 |
| 3 | GET | `/api/friends` | 我的好友列表（`tagId` 可选过滤，合并 PRD §5 的 friends 与 friends?tagId 两语义） | 需当前用户 |
| 4 | GET | `/api/friends/{userId}` | 好友详情（基础信息 + 我给其打的标签） | 需当前用户 |
| 5 | GET | `/api/friend-tags` | 我的标签列表（含每标签好友数） | 需当前用户 |
| 6 | POST | `/api/friend-tags` | 创建标签 | 需当前用户 |
| 7 | PUT | `/api/friend-tags/{tagId}` | 修改标签名 | 需当前用户（标签归属人） |
| 8 | DELETE | `/api/friend-tags/{tagId}` | 删除标签（级联软删绑定，C13） | 需当前用户（标签归属人） |
| 9 | POST | `/api/friend-tags/{tagId}/users/{userId}` | 给好友绑定标签 | 需当前用户（标签归属人 + 好友关系校验） |
| 10 | DELETE | `/api/friend-tags/{tagId}/users/{userId}` | 移除好友标签 | 需当前用户（标签归属人） |
| 11 | POST | `/api/mock-data` | 批量生成模拟数据 | 无（实验室工具接口） |
| 12 | POST | `/api/posts` | 发布朋友圈 | 需当前用户 |
| 13 | GET | `/api/posts/{postId}` | 帖子详情（canView 校验） | 需当前用户 |
| 14 | DELETE | `/api/posts/{postId}` | 软删除帖子（仅作者） | 需当前用户（作者） |
| 15 | GET | `/api/posts` | 指定用户帖子列表（`userId` 必填，canView 过滤，C14） | 需当前用户 |
| 16 | GET | `/api/feed` | Feed 瀑布流（Cursor 分页） | 需当前用户 |
| 17 | POST | `/api/images/upload` | 图片上传（multipart） | 无 |

> 合并说明：PRD §5 的 `GET /api/friends` 与 `GET /api/friends?tagId=` 合并为 #3 一个端点的可选参数（同 URL 同谓词，Spring 无法注册两个无条件映射；合并后前端语义不变）。实际 **17 个端点**，api.md 按 17 个接口派生。

### 3.2 接口详细设计

#### 3.2.1 用户列表 — `GET /api/users`

**请求参数（query）：**

| 字段 | 类型 | 必填 | 校验规则 | 说明 |
|------|------|------|----------|------|
| pageNo | Integer | 否 | 缺省默认 1；显式传入越界（<1 或非数字）抛 1001 | 页码 |
| pageSize | Integer | 否 | 缺省默认 20；显式传入越界（<1 或 >500 或非数字）抛 1001 | 每页条数（切换器场景传 500） |

> **全项目参数越界统一策略（B2 定稿）**：所有分页/limit 类参数——**缺省用默认值，显式传入越界一律抛 1001**（mock 配置类越界抛专用码 1040）。禁止"越界归默认"的宽松处理，避免规格与实现口径分裂。

**响应 data（UserPageOut）：**

```json
{ "total": 1000, "users": [ { "userId": 1, "nickname": "张三", "avatar": "/images/a1.png",
  "gender": 1, "genderStr": "男", "city": "杭州", "createTimeStr": "2026-09-20 10:00:00" } ] }
```

| 字段 | 类型 | 说明 |
|------|------|------|
| total | Long | 总用户数 |
| users[].userId | Long | 用户 ID |
| users[].nickname | String | 昵称（允许重名，D6） |
| users[].avatar | String | 头像 URL |
| users[].gender | Integer | 性别：0 未知 1 男 2 女 |
| users[].genderStr | String | 性别中文 |
| users[].city | String | 城市 |
| users[].createTimeStr | String | 注册时间（yyyy-MM-dd HH:mm:ss） |

**异常码：** 1001 参数校验失败

#### 3.2.2 用户详情 — `GET /api/users/{userId}`

**路径参数：** userId（Long，必填，>0）

**响应 data（UserOut）：** 同 3.2.1 users[] 元素结构（单对象）。

**异常码：** 1001 参数校验失败；1002 用户不存在

#### 3.2.3 我的好友列表 — `GET /api/friends`

**请求参数（query）：** tagId（Long，可选，>0；缺省=全部好友）

**响应 data（List<FriendOut>）：**

```json
[ { "userId": 2, "nickname": "李四", "avatar": "/images/a2.png", "gender": 1, "genderStr": "男",
    "city": "上海", "tagNames": ["同学"] } ]
```

| 字段 | 类型 | 说明 |
|------|------|------|
| [].userId/nickname/avatar/gender/genderStr/city | — | 同 UserOut |
| [].tagNames | List&lt;String&gt; | 当前用户给该好友打的标签名列表（无标签=空数组） |

**异常码：** 1001；1003 未选择当前用户；1004 标签不存在（tagId 传入时校验归属）

#### 3.2.4 好友详情 — `GET /api/friends/{userId}`

**路径参数：** userId（Long，必填）

**响应 data（FriendDetailOut）：** UserOut 全字段 + `tagNames`（同上）。好友注册时间即 createTimeStr。

**异常码：** 1001；1002 用户不存在；1003

#### 3.2.5 标签列表 — `GET /api/friend-tags`

**请求参数：** 无（当前用户即归属人）

**响应 data（List<TagOut>）：**

```json
[ { "tagId": 11, "tagName": "同事", "friendCount": 25, "createdTimeStr": "2026-09-20 10:00:00" } ]
```

| 字段 | 类型 | 说明 |
|------|------|------|
| [].tagId | Long | 标签 ID |
| [].tagName | String | 标签名 |
| [].friendCount | Integer | 该标签下好友数（COUNT(DISTINCT friend_user_id)，R2-建议8：与 tagNames 去重口径一致） |
| [].createdTimeStr | String | 创建时间 |

**异常码：** 1003

#### 3.2.6 创建标签 — `POST /api/friend-tags`

**请求体（TagCreateIn，JSON）：**

| 字段 | 类型 | 必填 | 校验规则 | 说明 |
|------|------|------|----------|------|
| tagName | String | 是 | trim 后 1-16 字符 | 标签名（同一用户内查重，软删范围内） |

**响应 data（TagOut）：** 新建标签（friendCount=0）。

**异常码：** 1001；1003；1005 标签名重复

#### 3.2.7 修改标签 — `PUT /api/friend-tags/{tagId}`

**请求体（TagEditIn）：** tagName（String，必填，trim 后 1-16 字符）

**响应 data（TagOut）：** 修改后标签。

**异常码：** 1001；1003；1004 标签不存在；1006 非标签归属人；1005 标签名重复

#### 3.2.8 删除标签 — `DELETE /api/friend-tags/{tagId}`

**请求参数：** 路径 tagId（Long，必填）

**响应 data：** `null`（ApiResult.succ()）。

**异常码：** 1001；1003；1004；1006

**副作用：** 事务内级联软删 friend_tag_relation（C13）；post_visibility_tag 不级联（按 tagId 匹配自然失效，C13）；事务提交后 INCR `moments:ftagver`（A4）。

#### 3.2.9 绑定好友标签 — `POST /api/friend-tags/{tagId}/users/{userId}`

**请求参数：** 路径 tagId、userId（Long，必填）

**响应 data：** `null`。

**异常码：** 1001；1003；1004；1006；1002 目标用户不存在；1007 仅可对好友打标签；1008 标签已绑定该好友

**幂等：** INSERT 前 SELECT 查重（java-guide 6.1）；重复绑定返回 1008。

#### 3.2.10 解绑好友标签 — `DELETE /api/friend-tags/{tagId}/users/{userId}`

**请求参数：** 路径 tagId、userId（Long，必填）

**响应 data：** `null`。

**异常码：** 1001；1003；1004；1006

**幂等：** 解绑不存在的绑定为幂等成功（UPDATE is_del=1 WHERE ... 命中 0 行不报错）。

#### 3.2.11 生成模拟数据 — `POST /api/mock-data`

**请求体（MockDataGenerateIn，JSON）：**

| 字段 | 类型 | 必填 | 校验规则 | 说明 |
|------|------|------|----------|------|
| userCount | Integer | 否 | 1-100000，默认 100 | 生成用户数 |
| avgFriendsPerUser | Integer | 否 | 0-500，默认 10 | 人均好友数（双向存储记录数=关系对数×2） |
| extraTagsPerUser | Integer | 否 | 0-20，默认 0 | 每用户额外自定义标签数（默认 8 标签之外） |
| postCount | Integer | 否 | 0-10000，默认 0 | 生成帖子数（A8 最小扩展，默认关闭） |
| clear | Boolean | 否 | 默认 false | true=先软删清空 8 张表业务数据再生成（页面二次确认） |

**响应 data（MockDataGenerateOut）：**

```json
{ "userCount": 100, "friendshipPairs": 520, "friendshipRecords": 1040, "tagCount": 800,
  "bindingCount": 743, "postCount": 0, "imagePoolSize": 40, "elapsedMs": 3520 }
```

| 字段 | 类型 | 说明 |
|------|------|------|
| userCount | Integer | 实际生成用户数 |
| friendshipPairs | Integer | 好友关系对数（A-B 算 1 对） |
| friendshipRecords | Integer | friendship 表记录数（=pairs×2，C11） |
| tagCount | Integer | 生成标签总数（含默认 8×用户数） |
| bindingCount | Integer | 标签绑定总数 |
| postCount | Integer | 生成帖子数 |
| imagePoolSize | Integer | 占位图池文件数 |
| elapsedMs | Long | 耗时（毫秒） |

**异常码：** 1040 模拟配置非法（含 DB 连接中断等失败原因为 100 系统码）

#### 3.2.12 发布朋友圈 — `POST /api/posts`

**请求体（PostCreateIn，JSON）：**

| 字段 | 类型 | 必填 | 校验规则 | 说明 |
|------|------|------|----------|------|
| content | String | 否 | trim 后 ≤2000 字符 | 文字内容 |
| imageUrls | List&lt;String&gt; | 否 | ≤9 项；每项前缀 `/images/` | 已上传图片 URL（顺序即 sort） |
| visibilityType | Integer | 是 | ∈{1,2,3,4} | 可见范围 |
| visibilityTagIds | List&lt;Long&gt; | 否 | type=3/4 时生效（去重）；type=1/2 时**静默忽略（不校验、不落库）** | 指定标签 |
| visibilityUserIds | List&lt;Long&gt; | 否 | type=3/4 时生效（去重）；type=1/2 时**静默忽略（不校验、不落库）** | 指定好友 |

**联合校验：** content 与 imageUrls 不得同时为空（纯空帖拒绝）。

**响应 data（PostDetailOut）：** 完整帖子（同 3.2.13 响应结构）。

**异常码：** 1001 参数校验失败；1022 单帖图片超过 9 张；1003；1004 标签不存在/1006 非标签归属人（visibilityTagIds 校验）；1002 指定好友不存在（visibilityUserIds 校验）

#### 3.2.13 帖子详情 — `GET /api/posts/{postId}`

**路径参数：** postId（Long，必填）

**响应 data（PostDetailOut）：**

```json
{ "postId": 101, "userId": 1, "nickname": "张三", "avatar": "/images/a1.png",
  "content": "今天天气不错", "imageUrls": ["/images/c1.png"],
  "visibilityType": 3, "visibilityTypeStr": "部分可见", "status": 1, "createTimeStr": "2026-09-20 10:00:00" }
```

| 字段 | 类型 | 说明 |
|------|------|------|
| postId | Long | 帖子 ID |
| userId/nickname/avatar | — | 作者信息（组装） |
| content | String | 文字内容 |
| imageUrls | List&lt;String&gt; | 图片 URL（按 sort 升序） |
| visibilityType | Integer | 1 公开 2 私密 3 部分可见 4 不给谁看 |
| visibilityTypeStr | String | 中文标识 |
| status | Integer | 1 正常 0 删除（C7） |
| createTimeStr | String | 发布时间 |

**异常码：** 1001；1003；1010 帖子不存在或已删除；1011 无权查看该帖子（canView 不过，语义走查 Q6）

#### 3.2.14 删除帖子 — `DELETE /api/posts/{postId}`

**路径参数：** postId（Long，必填）

**响应 data：** `null`。软删（is_del=1，status 保留业务态，C7）；图片文件保留（D7）。

**异常码：** 1001；1003；1010；1012 仅作者可删除

#### 3.2.15 指定用户帖子列表 — `GET /api/posts?userId=`

**请求参数（query）：**

| 字段 | 类型 | 必填 | 校验规则 | 说明 |
|------|------|------|----------|------|
| userId | Long | 是 | >0，非法抛 1001 | 目标作者（C14） |
| limit | Integer | 否 | 缺省默认 100；显式传入越界（<1 或 >500 或非数字）抛 1001 | **结果条数上限**（非扫描上限） |

**响应 data（List&lt;PostDetailOut&gt;）：** createTime DESC 排序，仅含当前用户 canView 可见的帖子。

**limit 语义定稿（建议 5 修复 + 建议 6 有界性留痕）**：limit 是**结果上限**而非扫描上限——服务端实现按 Feed 同口径放大补偿：以 `limit×3` 为批大小最多 3 批扫描候选并 canView 过滤，取过滤后前 limit 条返回；缓解"先截断后过滤"漏掉排在截断点之后的旧公开帖（如目标用户前 100 条全为对 viewer 不可见帖的场景）。**有界性**：补偿上限为 9×limit 条候选（单请求无翻页链），连续不可见超过 9×limit 时仍截断——与 Feed 的"3 空页停止"同族的有界权衡，实验室量级（mock 每用户 1-2 帖）不可达。

**异常码：** 1001；1003；1002 目标用户不存在

#### 3.2.16 Feed — `GET /api/feed`

**请求参数（query）：**

| 字段 | 类型 | 必填 | 校验规则 | 说明 |
|------|------|------|----------|------|
| cursor | String | 否 | Base64 解码失败（IllegalArgumentException，如 `!!!`）或解码后不匹配 `^\d{13}:\d{1,18}$`（建议 7：id 段限 18 位防 Long 溢出），或毫秒/id 段 `Long.parseLong` 溢出（try-catch 双保险），均抛 1030 | 分页游标（首页不传） |
| pageSize | Integer | 否 | 1-50，默认 20 | 每页条数（D3） |

**响应 data（FeedOut）：**

```json
{ "items": [ { "postId": 102, "userId": 1, "nickname": "张三", "avatar": "/images/a1.png",
    "content": "...", "imageUrls": [], "createTimeStr": "2026-09-20 10:00:00" } ],
  "nextCursor": "MTc4OTkxODIwMzAwMDoxMDI=", "hasMore": true }
```

| 字段 | 类型 | 说明 |
|------|------|------|
| items[].postId/userId/nickname/avatar/content/imageUrls/createTimeStr | — | 同 PostDetailOut（无 visibilityType，Feed 展示不需要） |
| nextCursor | String | **不透明游标**（建议 1 术语统一：对客户端不承诺内部语义）；`hasMore=true` 时**必非 null**——items 为空时指向已扫描候选流末条（扫描进度锚点），非空截断时为 items 末条（页末锚点）；`hasMore=false` 时为 null。§5.4 内部算法区分双锚点，对外契约仅承诺上述不变式 |
| hasMore | Boolean | 是否还有更多。`items=[] 且 hasMore=true` 为**合法组合**：本页扫描范围（≤3×pageSize×3 条候选）内无可见帖但候选流未扫尽，前端继续携带 nextCursor 翻页 |

**异常码：** 1001；1003；1030 游标非法（Base64 解码异常与格式校验失败同码）

**Cursor 语义（C8）：** cursor = Base64("{createdStime毫秒}:{postId}")；SQL 条件 `(created_stime &lt; #{ct} OR (created_stime = #{ct} AND id &lt; #{id}))`；排序 `created_stime DESC, id DESC`。**空页停止规则（前端，B1）**：连续 3 次 `items=[]`（累计扫描 ≈27×pageSize 条候选仍无可见帖）即停止加载并提示"暂无更多内容"，防极端全不可见流下的持续空页请求。

#### 3.2.17 图片上传 — `POST /api/images/upload`

**请求格式：** `multipart/form-data`，字段 `file`（MultipartFile，必填）。

**校验：** 扩展名 ∈ {jpg, jpeg, png, gif, webp}（按原始文件名判断，1020）；大小 ≤5MB（1021）。

**响应 data（ImageUploadOut）：**

```json
{ "imageUrl": "/images/8f3a...c2.png" }
```

| 字段 | 类型 | 说明 |
|------|------|------|
| imageUrl | String | 访问 URL（文件名=UUID.扩展，服务端生成，防覆盖/防穿越） |

**异常码：** 1020 图片格式不支持；1021 图片超过 5MB；1022 超过 9 张（发帖侧）

---

## 4. 数据模型设计

### 4.1 新增表（8 张，MySQL 5.7.44（本地 dev）/ 8.0（PRD 目标）双兼容 / InnoDB / utf8mb4 + utf8mb4_general_ci，A11）

> 通用字段全表必含：`id bigint AUTO_INCREMENT`、`created_stime datetime DEFAULT CURRENT_TIMESTAMP`、`modified_stime datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP`、`is_del tinyint(1) NOT NULL DEFAULT 0`。PRD 的 createTime/updateTime 映射为通用字段 created_stime/modified_stime（不另建列）；Feed 排序键即 (created_stime, id)。完整 DDL 见 sql.md（同源文件 `moments-dao/src/main/resources/schema/social_timeline.sql` 供 Runner 使用）。

#### `user` — 用户表

| 字段 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| id | bigint | AUTO_INCREMENT | 主键（userId） |
| nickname | varchar(64) | '' | 昵称（允许重名，D6） |
| avatar | varchar(255) | '' | 头像 URL（/images/xxx.png） |
| gender | tinyint(2) | 0 | 性别：0 未知 1 男 2 女 |
| city | varchar(32) | '' | 城市 |

索引：`pk_id`（PRIMARY）；`idx_nickname`（NORMAL，nickname，列表检索预留）。

#### `friendship` — 好友关系表（双向两条对称记录，C11）

| 字段 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| id | bigint | AUTO_INCREMENT | 主键 |
| user_id | bigint | — | 关系一方 |
| friend_user_id | bigint | — | 关系另一方 |

索引：`pk_id`；`uniq_user_friend`（UNIQUE，user_id+friend_user_id，防对称重复写入）。

#### `friend_tag` — 好友标签表（标签归属创建人，C5）

| 字段 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| id | bigint | AUTO_INCREMENT | 主键（tagId） |
| user_id | bigint | — | 标签创建人 |
| tag_name | varchar(32) | '' | 标签名 |

索引：`pk_id`；`idx_user`（NORMAL，user_id）。
**不建 DB 唯一键**（user_id+tag_name）：软删记录会占用唯一键导致同名重建失败；唯一性由 Service 层在 `is_del=0` 范围内查重保证（创建/改名时）。

#### `friend_tag_relation` — 标签-好友绑定表

| 字段 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| id | bigint | AUTO_INCREMENT | 主键 |
| tag_id | bigint | — | 标签 ID |
| friend_user_id | bigint | — | 被打标签的好友 |

索引：`pk_id`；`idx_tag`（NORMAL，tag_id，按标签查好友）；`idx_friend`（NORMAL，friend_user_id，canView 标签命中反查）。绑定查重由 Service 层（is_del=0 范围）保证，理由同上。

#### `post` — 朋友圈帖子表

| 字段 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| id | bigint | AUTO_INCREMENT | 主键（postId） |
| user_id | bigint | — | 作者 |
| content | varchar(2000) | '' | 文字内容（D4） |
| visibility_type | tinyint(2) | 1 | 可见范围：1 公开 2 私密 3 部分可见 4 不给谁看 |
| status | tinyint(2) | 1 | 业务状态：1 正常 0 删除（C7，软删走 is_del） |

索引：`pk_id`；`idx_user_created`（NORMAL，user_id+created_stime，listUserPosts）；`idx_created_stime`（NORMAL，created_stime，Feed 时间扫描）。

#### `post_image` — 帖子图片表

| 字段 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| id | bigint | AUTO_INCREMENT | 主键 |
| post_id | bigint | — | 所属帖子 |
| image_url | varchar(255) | '' | 图片 URL |
| sort | int | 0 | 排序号 1-9 |

索引：`pk_id`；`idx_post`（NORMAL，post_id）。

#### `post_visibility_user` — 帖子指定好友表

| 字段 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| id | bigint | AUTO_INCREMENT | 主键 |
| post_id | bigint | — | 所属帖子 |
| user_id | bigint | — | 指定可见/不可见用户 |

索引：`pk_id`；`uniq_post_user`（UNIQUE，post_id+user_id，发布链路无软删残留、防重复插入）。

#### `post_visibility_tag` — 帖子指定标签表

| 字段 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| id | bigint | AUTO_INCREMENT | 主键 |
| post_id | bigint | — | 所属帖子 |
| tag_id | bigint | — | 指定可见/不可见标签（删标签不级联，C13） |

索引：`pk_id`；`uniq_post_tag`（UNIQUE，post_id+tag_id）。

### 4.2 修改表

无修改表（全新建设）。

### 4.3 ER 关系

```
user 1 ──── N friendship（user_id；对称两条，friend_user_id 反向冗余）
user 1 ──── N friend_tag（user_id = 创建人）
friend_tag 1 ──── N friend_tag_relation（tag_id；friend_user_id → user）
user 1 ──── N post（user_id = 作者）
post 1 ──── N post_image（post_id，sort 1-9）
post 1 ──── N post_visibility_user（post_id + user_id）
post 1 ──── N post_visibility_tag（post_id + tag_id → friend_tag，逻辑外键）
```

---

## 5. 核心流程设计

### 5.1 发布朋友圈主流程（4 表同事务，R3）

```
Client          PostController         PostServiceImpl                Dao
  │ POST /api/posts   │                      │                        │
  │ ─────────────────►│ 日志入参 + viewer 解析 │                        │
  │                   │ createPost(in,viewer)│                        │
  │                   │ ────────────────────►│ @Transactional(rollbackFor=Exception.class)
  │                   │                      │ 1 校验：content≤2000 / 图≤9 / type∈1-4 /
  │                   │                      │   content与图非同空 / tagIds 归属+存在 /
  │                   │                      │   userIds 存在（异常即回滚）
  │                   │                      │ 2 post insert（status=1）          ──► post
  │                   │                      │ 3 post_image batchInsert（sort=1..n）──► post_image
  │                   │                      │ 4 type=3/4 且 tagIds 非空 → batchInsert ──► post_visibility_tag
  │                   │                      │ 5 type=3/4 且 userIds 非空 → batchInsert ──► post_visibility_user
  │                   │◄──── PostDetailOut ──│ 提交（无 Redis/文件 IO 在事务内）
```

### 5.2 canView 状态判定（统一权限判断，PRD §3.8）

```
canView(viewerId, postId)
  ├─ post = postMapper.selectById(postId)（is_del=0）→ null ⇒ false
  ├─ viewerId == post.userId ⇒ true（作者最高优先级）
  ├─ type=1 公开 ⇒ true
  ├─ type=2 私密 ⇒ false
  ├─ tagHit = post_visibility_tag(postId).tagIds ∩ 作者给 viewer 打的标签ID集合（缓存）≠ ∅
  ├─ userHit = post_visibility_user(postId).userIds.contains(viewerId)
  ├─ type=3 部分可见 ⇒ tagHit OR userHit（空集时两者皆 false ⇒ false，C6）
  └─ type=4 不给谁看 ⇒ !(tagHit OR userHit)（空集 ⇒ true 等同公开，C6）
```

被 PostServiceImpl（详情/列表）与 FeedServiceImpl（逐帖过滤）共用，**单实现**（A5）。

### 5.3 帖子生命周期状态机（C7）

```
            发布（createPost）
                 │
           ┌─────▼─────┐
           │  正常       │  status=1, is_del=0
           └─────┬─────┘
                 │ DELETE /api/posts/{postId}（仅作者，软删）
           ┌─────▼─────┐
           │  已删除    │  is_del=1（status 保留 1，业务态快照）
           └───────────┘
```

无其他状态流转（无草稿/审核，PRD §3.1）；已删帖在详情/Feed/列表全路径被 is_del=0 过滤（R7）。

### 5.4 Feed 查询流程（Cursor + 权限过滤，R1；B1 修订：双锚点语义）

> **双锚点（B1 定稿）**：区分「**页末锚点**」（items 末条，常规翻页）与「**扫描进度锚点**」（最后一批候选末条，items 不足/为空时延续扫描）。nextCursor 输出规则保证 `hasMore=true` 时**必非 null**，从根上消除"空页 + 无游标 → 前端退化首页请求 → 永久循环"。

```
getFeed(viewerId, cursor, pageSize)
  ├─ cursor 解码：null→无条件；非 null→try{Base64 decode}catch(IllegalArgumentException)→1030，
  │    解码串 ^\d{13}:\d+$ 校验失败也→1030（R4 不静默当首页；建议 6：decode 异常与格式校验同码）
  ├─ pageSize 校验：缺省 20；显式越界（<1 或 >50 或非数字）→ 1001（B2 统一策略）
  ├─ friendIds ← FriendIdsCacheManager.get(viewerId)（Redis Set；miss→friendship 回源→回填，TTL 1h；
  │    空好友集回填哨兵成员 "0" 防穿透，建议 8；异常→直接回源 DB，C12 弱依赖）
  ├─ candidateUserIds = [viewerId] + friendIds
  ├─ 取数循环：batchSize = pageSize×3，maxRounds = 3
  │    scanTime/scanId = 入参 cursor 解码值（首页为 null）        ← 扫描进度锚点初始化
  │    scanEnd = false; collected = []
  │    while (rounds < maxRounds && collected.size() < pageSize):
  │      batch = postMapper.selectFeedPage(candidateUserIds, viewerId, scanTime, scanId, batchSize)
  │        // WHERE user_id IN (...) AND is_del=0 AND status=1
  │        //   AND (visibility_type != 2 OR user_id = #{viewerId})   ← 私密帖非作者 SQL 预过滤
  │        //   AND cursor 条件（§3.2.16）
  │        // ORDER BY created_stime DESC, id DESC LIMIT batchSize
  │      if batch.isEmpty()    → scanEnd = true; break            ← 候选流扫尽
  │      scanTime, scanId = batch 末条 (created_stime, id)        ← 扫描进度锚点前移（无论本批有无可见帖）
  │      collected += batch 中 canView(viewerId, postId)=true 的帖子（type=1 直通，type=3/4 判定）
  │      if batch.size() < batchSize → scanEnd = true; break      ← 末批不满=候选流到尾
  │      rounds++                                                 ← 批取满但未集满 pageSize 则续批
  ├─ 输出判定（B1 核心，合法组合完备定义）：
  │    collected.size() > pageSize（截断发生）：
  │      items = collected 前 pageSize 条；hasMore = true
  │      nextCursor = items 末条锚点（页末锚点；被截断的可见帖下一页重扫、canView 幂等不重复输出）
  │    collected.size() ≤ pageSize：
  │      items = collected（**可能为空集**——3 批候选全不可见）
  │      scanEnd == true  → hasMore = false；nextCursor = null（真无更多；含无帖/无好友空集，Q5）
  │      scanEnd == false → hasMore = true；nextCursor = (scanTime, scanId) 扫描进度锚点 Base64
  │                          ← 合法组合 items=[] && hasMore=true && nextCursor≠null：翻页链延续，
  │                            已扫过的不可见候选不重扫（游标严格小于语义）
  ├─ PostImageMapper.selectByPostIds + UserMapper.selectByUserIds 批量组装（防 N+1）
  └─ 前端停止规则：连续 3 次 items=[] 即停止加载并提示"暂无更多内容"（§3.2.16）
```

### 5.5 模拟数据生成流程（§2.5 + C15 + R11）

```
generate(in)
  ├─ 校验配置范围（§3.2.11，非法抛 1040）
  ├─ clear=true（建议 7 修订）：8 表 logicDeleteAll 按**表逐个独立小事务**提交（非单一大事务；
  │    每表一条 UPDATE ... SET is_del=1 WHERE is_del=0 均带 WHERE，实验室万级数据单表秒级，
  │    表级事务将锁持有时间切到最小，对齐 R11 防长事务精神）
  │    → clear 完成即先执行一次缓存失效（INCR fver + INCR ftagver，事务外）：
  │      保证后续即便生成中断，旧缓存也已失效，不会读到已清空的旧好友/旧标签集合（中断恢复语义）
  ├─（事务外）LocalImageService.ensurePlaceholderPool()：头像 200×200×20 + 内容 600×600×20 纯色 PNG（D5/A7）
  ├─ 分批事务（每批 ≤500，批间提交防长事务，R11）：
  │    1 用户：昵称=姓氏池+名字池随机（中文常量池），gender/city 随机，avatar=头像池循环取
  │    2 好友：每用户按 avgFriendsPerUser±抖动 选目标，pair("min:max") Set 去重、防自环，
  │            对称双记录成对入批（R5）；关系对数 ≥1000 由入参保证（如 100 用户×10 好友）
  │    3 标签：每用户默认 8 标签（D2：家人/亲戚/同事/同学/朋友/球友/客户/其他）+ extraTagsPerUser 个自定义
  │    4 绑定：每个 (owner,friend) 按权重抽 1 主标签（同事25/同学20/朋友25/家人5/亲戚5/球友10/客户5/其他5，A9），
  │            30% 概率加抽 1 个不重复次标签（模拟一人多标签）
  │    5（postCount>0）帖子：随机用户 1-2 帖/人凑数，type 分布 1:40%/2:20%/3:20%/4:20%，
  │            图 1-9 张取内容图池；type=3/4 抽作者自己的标签/好友写可见性两表
  └─ 完成后：INCR moments:fver + INCR moments:ftagver（缓存全量失效，事务外）
```

### 5.6 关键业务规则

| # | 规则 | 说明 |
|---|------|------|
| 1 | canView 统一单实现 | VisibilityServiceImpl 唯一实现，Post/Feed 复用，禁止散落重复判断 |
| 2 | 标签归属 | 一切标签读写校验 friend_tag.user_id == 当前用户（水平越权防护，java-guide 9） |
| 3 | 删帖归属 | 仅 post.user_id == 当前用户可删（1012） |
| 4 | 级联软删 | 删标签 → 级联软删绑定（C13）；post_visibility_tag 不级联（自然失效） |
| 5 | 幂等-绑定 | bindUser 先 SELECT 查重再 INSERT（1008）；unbind 幂等（0 行命中不报错）。**并发窗口留痕（建议 4）**：查重与 INSERT 间无锁，极端并发/双击可落两条 is_del=0 绑定——① DB 唯一键不可行：解绑（软删）后重绑走 INSERT 新记录，`uniq(tag_id, friend_user_id)` 会挡住合法重绑（软删记录占键，§4.1 已说明）；② 兜底：**展示组装侧对 tagNames 做 Stream.distinct() 去重**（friend 查重查询取单条）**且标签列表 friendCount 统计用 `COUNT(DISTINCT friend_user_id)`**（建议 8：并发残留两条记录时计数与 tagNames 去重口径一致，不虚高），叠加前端按钮防抖；③ 实验室单人操作，窗口风险接受，生产化可加 DistributedLock（框架已有） |
| 5b | 删帖/删标签幂等口径（建议 1） | **均非幂等**：对已删/不存在目标统一返回 1010/1004（用户双击删除第二次报错属预期，前端按钮 loading 防双击）；仅解绑（unbind）为幂等成功（0 行命中不报错） |
| 6 | 幂等-对称好友 | batchInsert 前按 pair Set 去重 + DB uniq_user_friend 兜底 |
| 7 | 幂等-发帖 | 不做 requestId 幂等：帖子无业务唯一键、同内容多发是合法语义（微信同文可连发）、非资金操作；前端按钮防重复提交；风险留档 §12 |
| 8 | 缓存一致性 | 好友/标签变更（bind/unbind/deleteTag/mockData）后 INCR 对应全局版本；**事务性写操作的 INCR 定稿（建议 3）：仅允许 TransactionSynchronizationManager.registerSynchronization 的 afterCommit 回调内执行，禁止在事务方法内或方法返回前（提交前）调用**（方法尾=提交前=事务内操作 Redis，违反 java-guide §5）；读路径 miss/异常回源 DB（C12） |
| 9 | 事务内禁中间件 | Redis 操作全部在事务提交后（java-guide §5）；文件 IO（上传/占位图）全在事务外 |
| 10 | 软删全过滤 | 所有自定义 SQL 显式 `is_del = 0`；MP 自带方法依赖 DO 的 @TableLogic |

---

## 6. 分层实现设计

### 6.1 Controller 层（moments-web `web.controller.social`）

> 仅参数校验 + viewer 解析 + 编排；首参 String _appId；public 方法首行日志。GET 单参/双参接口直收，POST/PUT 体超 3 参用 In DTO。

| Controller | 方法（路由） | 签名要点 |
|------------|-------------|---------|
| UserController | listUsers（GET /api/users）、getUser（GET /api/users/{userId}） | `ApiResult<UserPageOut> listUsers(String _appId, Integer pageNo, Integer pageSize)` |
| FriendshipController | getFriends（GET /api/friends）、getFriendDetail（GET /api/friends/{userId}） | getFriends 含可选 tagId；viewer=CurrentUserResolver.requireUser(request) |
| FriendTagController | listTags / createTag / updateTag / deleteTag / bindUser / unbindUser（§3.1 #5-#10） | 写方法 @PostMapping/@PutMapping/@DeleteMapping |
| MockDataController | generate（POST /api/mock-data） | `@RequestBody MockDataGenerateIn` |
| PostController | createPost / getPost / deletePost / listUserPosts（§3.1 #12-#15） | createPost `@RequestBody PostCreateIn` |
| FeedController | getFeed（GET /api/feed） | cursor/pageSize query |
| ImageController | upload（POST /api/images/upload） | `@RequestParam("file") MultipartFile` |

**横切支撑（moments-web）**：
- `web.support.CurrentUserResolver`（静态工具，B3 修订）：`resolve(request)` 优先取 query `viewerId`（显式传入强校验：`^\d{1,18}$` 正则 + `Long.parseLong` 后 >0 + try-catch NumberFormatException → 非法一律抛 BizException(1001)，**不静默回退 Cookie**；合法则返回）；否则 Cookie `mockUserId`（值为 `^\d{1,18}$` 且 >0 才采纳，非法脏值视为未选择返回 null，宽松处理），复用 CookieUtils；`requireUser(request)`（resolve 为 null 抛 1003，A3）。
- `config.BizExceptionAdvice`：`@RestControllerAdvice @Order(HIGHEST_PRECEDENCE)` 处理四类异常（A10 + NB1 定稿 + R3 裁决追加 + Stage 6 裁决8 补录）——① `BizException` → `ApiResult.buildFailure(e.getCode(), e.getMessage())`；② **`MethodArgumentTypeMismatchException`（数值参数非数字，如 `pageNo=abc`，Spring 绑定层抛出根本到不了 Service 校验）→ 统一转 1001**——这是"显式越界（含非数字）抛 1001"承诺的承载机制（B2 口径的类型边界补齐），覆盖全部数值 query/路径参数（pageNo/pageSize/tagId/userId/limit/postId 等）；③ **`HttpMessageNotReadableException`（JSON 请求体字段类型非法，如 body `visibilityType:"abc"`，Jackson 反序列化抛出）→ 统一转 1001**（与 ② 同模式；R3 裁决追加，防阶段四按 query 同族逻辑给 body 字段设计"非法类型→1001"用例时与实现分裂）；④ **`MaxUploadSizeExceededException`（请求超过 multipart 容器上限，Spring 在进入 Controller 前抛出）→ 统一转 1021**（Stage 6 裁决8 补录：与 Service 层应用级 5MB 校验（1021）同码收口，防该异常落框架 code=100）。
- `config.SchemaInitRunner`：`@Profile("dev")` ApplicationRunner，ScriptUtils 执行 classpath:`schema/social_timeline.sql`（幂等 CREATE TABLE IF NOT EXISTS，D1/A6）。
- `config.WebConfig`（**唯一修改的既有文件**）：追加 `addResourceHandlers`——`/images/**` → `file:{moments.image.root-path}/`，resourceChain 挂 `FileNameSafeResourceResolver`（文件名白名单 `^[A-Za-z0-9._-]+$` 校验，不匹配返回 404，R6）。

### 6.2 Service 层（moments-service `service.social` + `impl`）

| 接口 | 方法 | 职责 | 事务 |
|------|------|------|------|
| IUserService | listUsers(pageNo,pageSize) / getUser(userId) / listByUserIds(ids) | 用户查询与批量组装 | 无（只读） |
| IFriendshipService | listFriends(viewerId, tagId) / getFriendDetail(viewerId, friendUserId) / listFriendIds(viewerId) | 好友查询；listFriendIds 走缓存 | 无（只读） |
| IFriendTagService | listTags(viewerId) / createTag(viewerId,in) / updateTag(viewerId,tagId,in) / deleteTag(viewerId,tagId) / bindUser(viewerId,tagId,userId) / unbindUser(viewerId,tagId,userId) | 标签 CRUD + 绑定管理 | deleteTag：@Transactional(rollbackFor)（级联 2 表），INCR 经 afterCommit 回调（建议 3 定稿）；create/update/bind/unbind：单表写无 @Transactional 注解（单 DML 自动提交原子，与 explore §5.1 "事务"标注口径差异留痕：行为等价，tester 事务断言以本设计为准，建议 11）；写操作后 INCR ftagver（非事务方法为顺序调用即事务外） |
| IVisibilityService | canView(viewerId, postId) | 统一隐私判断（§5.2） | 无（只读） |
| IPostService | createPost(viewerId,in) / getPost(viewerId,postId) / deletePost(viewerId,postId) / listUserPosts(viewerId,targetUserId,limit) | 帖子读写 | createPost：@Transactional(rollbackFor)（4 表）；deletePost：单表软删无事务 |
| IFeedService | getFeed(viewerId,cursor,pageSize) | Feed 组装（§5.4） | 无（只读） |
| ILocalImageService | upload(file) / ensurePlaceholderPool() | 本地文件写入 + 校验（C16/C17） | 无（无 DB） |
| IMockDataService | generate(in) | 模拟数据（§5.5） | clear 段一个事务；生成段分批事务（≤500/批） |

**缓存组件（service.social.cache，C12/A4）**：
- `FriendIdsCacheManager`：`get(userId)` → key `moments:frd:{fver}:{userId}`（Redis Set，TTL 3600s）；miss 回源 friendship 回填——**空好友集也回填哨兵成员 `"0"`**（建议 8：userId 恒 >0（B3 保证），`"0"` 不与真实 ID 冲突；读到仅含 `"0"` 的集合视为有效空集缓存返回空，防无好友用户每请求穿透到 DB；解析时过滤哨兵）；Redis 异常 log.warn 后回源；`evictAll()` = INCR `moments:fver`。
- `FriendTagCacheManager`：`getTagIds(ownerId, friendUserId)` → key `moments:ftag:{ftagver}:{ownerId}:{friendUserId}`；miss 回源 `selectTagIdsOfFriend`；**空 tagIds 同样回填哨兵成员 `"0"`（建议 2：与好友缓存防御对称——无标签 viewer 是 mock 常态，不回填则 canView 的 tagHit 每帖穿透 DB 点查；tagId 恒 >0 与哨兵无冲突，读取时过滤哨兵）**；Redis 异常回源；`evictAll()` = INCR `moments:ftagver`。
- key 常量落 moments-common CacheKeyConsts（`%s` 风格追加）。

**配置承载（建议 3 定稿：单一 @Value 机制）**：图片根目录统一由 **`@Value("${moments.image.root-path:${user.home}/moments-data/images}")`** 注入——LocalImageServiceImpl（T040）与 WebConfig（T100）两处各自注入同一 key + 同一嵌套默认值（**两处默认值字符串必须逐字一致，实现与评审各核对一次**）；绝对路径理由见建议 9（相对路径受工作目录漂移影响）。**不引入 `@ConfigurationProperties` 配置类**：其字段 Java 默认值不解析 `${...}` 占位符（Spring 仅对绑定来源值解析），字面量默认值会与 `@Value` 的可解析默认值分裂，导致落盘目录与静态映射目录不一致（上传成功但访问 404）——review-2 建议 3 教训留痕。环境变量/配置可覆盖（禁硬编码；yml 不强制修改，默认值兜底）。

### 6.3 DAO / Mapper 层（moments-dao）

> DO 手写 getter/setter（禁 Lombok）；主键 Long（C9）；String setter trim；Mapper 继承 BaseMapper&lt;XxxDO&gt;，全部 SQL 写 XML（`resources/mapper/`），自定义方法参数 @Param；每查询带 is_del=0；#{} 占位；XML 中 `&lt;` 转义；批量 INSERT 用 foreach separator=","。

| Mapper | 自定义方法（XML SQL） |
|--------|---------------------|
| UserMapper | selectPageList(offset,size) / selectByUserIds(ids) / batchInsert(list) / countAll() / logicDeleteAll() |
| FriendshipMapper | selectFriendIds(userId) / existsFriendship(userId,friendUserId) / batchInsert(list) / logicDeleteAll() |
| FriendTagMapper | **selectByTagId(tagId)（R3 裁决方案①：随 Mapper 建文件归属 T020，tagId 过滤归属校验）** / selectByUserId(userId) / updateTagName(entity 只设 id+tagName) / updateIsDel(tagId) / batchInsert(list) / countFriendsByTagIds(tagIds)（friendCount 统计，COUNT(DISTINCT friend_user_id)，R2-建议8） / logicDeleteAll() |
| FriendTagRelationMapper | selectByTagAndUser(tagId,friendUserId) / **selectFriendIdsByTag(tagId)（R3 裁决方案①：归属 T020，tagId 过滤数据源）** / selectTagIdsOfFriend(ownerId,friendUserId)（join friend_tag 限定 owner）/ **selectTagNamesByOwnerAndFriends(ownerId,friendIds)（好友列表 tagNames 批量组装，R2-NB2：随 Mapper 建文件归属 T020）** / insert(relation) / updateIsDelByTagId(tagId) / updateIsDelByTagAndUser(tagId,friendUserId) / batchInsert(list) / logicDeleteAll() |
| PostMapper | selectById(postId) / selectUserPosts(userId,limit) / selectFeedPage(userIds,viewerId,ct,id,limit) / updateIsDel(postId) / batchInsert(list) / logicDeleteAll() |
| PostImageMapper | selectByPostId(postId)（ORDER BY sort）/ selectByPostIds(postIds) / batchInsert(list) / logicDeleteAll() |
| PostVisibilityUserMapper | selectUserIdsByPostId(postId) / batchInsert(list) / logicDeleteAll() |
| PostVisibilityTagMapper | selectTagIdsByPostId(postId) / batchInsert(list) / logicDeleteAll() |

更新规范：软删/改名一律**新建只含主键与待更新字段的实体**再调用更新（java-guide 2.3）。

### 6.4 DTO / VO 设计（moments-client `model.in` / `model.out`）

| 类 | 用途 | 字段来源 |
|----|------|---------|
| UserPageOut | 用户列表分页 | total + List&lt;UserOut&gt; |
| UserOut / FriendOut / FriendDetailOut | 用户/好友出参 | user 表 + tagNames 组装 |
| TagOut | 标签出参 | friend_tag + COUNT 绑定 |
| TagCreateIn / TagEditIn | 标签写入 | 前端入参 |
| MockDataGenerateIn / MockDataGenerateOut | 模拟数据 | §3.2.11 |
| PostCreateIn | 发帖入参 | §3.2.12 |
| PostDetailOut | 帖子详情/列表元素 | post + post_image + user 组装 |
| FeedOut | Feed 包装 | items/nextCursor/hasMore |
| ImageUploadOut | 上传出参 | imageUrl |

出参规范：含 type/status 的带 xxTypeStr/xxStatusStr 中文（VisibilityTypeEnum/PostStatusEnum 反查）；Date 出参转 xxTimeStr（DateUtils.PATTERN）；包装类数值类型；List 字段无值返回空数组禁 null。

---

## 7. 非功能设计

### 7.1 性能

| 指标 | 目标 | 方案 |
|------|------|------|
| Feed 首屏 RT | < 300ms（1000+ 好友、万级帖子） | 好友 ID 集合 Redis 缓存（C12）+ 私密帖 SQL 预过滤 + 批量取图/取作者防 N+1 + (created_stime,id) 索引 |
| 列表查询 RT | < 200ms | 索引点查/覆盖查询 |
| 模拟数据 1000 关系生成 | < 10s | 分批 batchInsert（≤500/批，foreach 多行插入） |
| 扩展（10 万用户） | 三期研究范围 | 本期不优化（R10） |

### 7.2 并发与幂等

| 场景 | 方案 |
|------|------|
| 重复绑定标签 | SELECT 查重（is_del=0 范围）+ 返回 1008 |
| 重复生成模拟数据 | clear 语义幂等；追加模式为合法语义（C15） |
| 重复发帖 | 不做 requestId 幂等（§5.6 规则 7，留档 §12）；前端按钮防抖 |
| 缓存击穿/Redis 宕机 | 回源 DB 降级（C12 弱依赖），log.warn 记录 |

### 7.3 安全

| 场景 | 方案 |
|------|------|
| 水平越权 | 标签/帖子写操作校验归属（1006/1012）；canView 拦截读越权（1011）。**假设留痕（建议 2）**：`viewerId` 参数即身份、完全信任、无任何认证（C3/C10 实验室定位）——知情者可冒充任意 userId 发帖/删帖/管理标签，越权防线仅防"无意的错误视角"而非恶意冒充；此为实验室非生产形态的显式接受 |
| SQL 注入 | 全 SQL #{} 占位，禁 ${}；动态排序字段为常量拼接白名单 |
| 路径穿越 | 上传文件名服务端 UUID 生成；/images/** 静态解析器白名单 `^[A-Za-z0-9._-]+$`（R6） |
| Cursor 伪造 | Base64 解码 + 正则强校验，非法拒绝（1030，R4） |
| 上传滥用 | 扩展名白名单 + 5MB 上限（C17）。**频控：拒绝实现（建议 12 裁决）**——理由：实验室定位且无登录体系，无可靠的用户维度挂频控（viewerId 非凭证，见上）；IP 频控需引入新配置/状态与计数 key，对单机实验环境属过度设计；磁盘污染风险由本地环境可重置兜底（clear 语义 + 图片目录可整体删除，数据无生产价值）。生产化前需补频控+总量配额（§12 遗留 7） |
| 敏感数据 | 本项目无手机号/身份证等 PII（模拟昵称/城市），无需脱敏；不引入真实凭证（§13 红线遵守） |
| 签名认证 | 豁免（C10：无登录体系实验室环境，非 C/B 端真实用户写接口） |

---

## 8. 中间件依赖

| 中间件 | 用途 | 新增/复用 | 说明 |
|--------|------|-----------|------|
| MySQL | 8 张业务表存储 | 新增表（连接配置复用既有数据源） | funny-springboot-starter-dao + MyBatis-Plus；**dev 实例 5.7.44 / PRD 目标 8.0，DDL 双版本兼容（A11：utf8mb4_general_ci、无表达式默认值/函数索引/CHECK）** |
| Redis | 好友 ID 集合缓存 + 标签绑定缓存 | 复用 | 框架 RedisClient（Jedis）；key 规范 `moments:frd:{ver}:{userId}`、`moments:ftag:{ver}:{ownerId}:{friendUserId}`、版本 key `moments:fver`/`moments:ftagver`；TTL 3600s；弱依赖回源 |
| 本地文件系统 | 图片存储 | 新增 | moments.image.root-path（默认 ${user.home}/moments-data/images 绝对路径，建议 9） |
| Kafka/XXL-JOB/MQ | 不引入 | — | PRD §7 仅 mysql+redis；无异步任务（explore 5.1） |

---

## 9. 异常与降级

| 异常场景 | 处理方式 |
|----------|---------|
| 业务规则不满足 | BizException(SocialErrorCode) → BizExceptionAdvice → ApiResult{code=业务码, msg} |
| 数值参数非数字（类型绑定失败，NB1） | Spring 抛 MethodArgumentTypeMismatchException（到不了 Service 校验）→ BizExceptionAdvice 专用 handler 统一转 1001 |
| JSON 请求体字段类型非法（R3 裁决追加） | Jackson 抛 HttpMessageNotReadableException（POST/PUT body 数值字段传字符串等）→ BizExceptionAdvice 专用 handler 统一转 1001 |
| Redis 不可用 | 缓存组件 catch → log.warn → 回源 DB（Feed 仍可用，C12） |
| 图片目录不可写 | 上传抛 BizException(1020 系/系统码) + error 日志含路径 |
| cursor 非法 | 1030 拒绝（不静默重置首页，R4） |
| DDL Runner 失败 | dev 启动失败快速暴露（fail-fast），错误日志含表名 |
| mock 生成中途中断 | 已提交批次保留（追加语义可重跑；clear=true 重跑即重建）。**缓存一致性（建议 7）**：clear 段完成即先失效一次缓存（INCR 双版本），生成中断时旧缓存不会残留"已清空数据"；追加模式下中断只意味着新数据未进缓存（miss 回源可见），无 stale 风险 |
| 系统未捕获异常 | 框架 GlobalExceptionAdvice 兜底 code=100（不向前端泄露堆栈） |

---

## 10. 日志与监控

| 维度 | 要求 |
|------|------|
| 接口入参 | Controller public 方法首行 info（_appId + 关键参数；DTO 用 JSON 序列化） |
| 关键节点 | Feed 过滤统计（候选数/可见数/批次）、canView 拒绝（postId/viewerId debug 级）、mock 分批进度（批序/累计）info |
| 缓存 | miss/回源/降级 warn（含 key 与 userId） |
| 异常 | `log.error("xxx error, param={}", json, e)` 含上下文与异常对象；禁空 catch |
| 监控告警 | 实验室定位不接入告警；依赖启动日志与测试断言（留档 §12） |

---

## 11. 上线方案（实验室环境）

| 项 | 内容 |
|----|------|
| 前置条件 | 本地 MySQL（**实际 5.7.44，brew 服务，A11；DDL 双兼容 8.0 目标环境**）/ Redis 可用（连接走既有 application 配置，不改动）；JDK 21 |
| 发布顺序 | mvn 编译 → dev profile 启动（SchemaInitRunner 自动建 8 表）→ 浏览器访问 `http://localhost:{port}/index.html` → 模拟数据页生成 1000+ 好友关系 → 验证 Feed |
| 数据初始化 | DDL 幂等 Runner（dev）；完整脚本备查 sql.md |
| 回滚方案 | git revert 代码；表与数据保留（实验室无生产数据风险）；如需清理按软删规范手工 UPDATE |
| 风险确认 | release-risk.md 为空模板，无历史风险项（绿地） |

---

## 12. 遗留问题

| # | 问题 | 风险等级 | 说明 |
|---|------|---------|------|
| 1 | 发帖无 requestId 幂等 | 低 | 语义与依据见 §5.6 规则 7；对抗审查可挑战，若要求补则加 @DistributedLock（框架已有） |
| 2 | mock `postCount` 参数为 PRD 外最小扩展 | 低 | A8，待用户阶段五追认；默认 0 不触发，严格模式仅用 PRD 三项配置 |
| 3 | 社交分布"兴趣好友 15%"的标签映射（球友10+客户5） | 低 | A9，分布语义解释权待追认 |
| 4 | 10 万级用户性能 | 中 | R10，三期研究范围（本期验收 1000+） |
| 5 | C3 覆盖参数命名 viewerId（非 userId） | 低 | A3，与 C14 的目标 userId 冲突规避，语义不变待追认 |
| 6 | 删帖后图片文件不物理删除 | 低 | D7，磁盘回收留优化 |
| 7 | 上传接口无频控/总量配额 | 低（实验室） | 建议 12 拒绝实现留档（理由见 §7.3）；生产化前需补 |

---

## 13. 测试策略（用户目标：数据级 + 页面级）

### 13.1 数据级测试（JUnit + dev 库 mapper 断言）

- 位置：`moments-web/src/test/java`（dev profile 启动上下文，连 dev MySQL/Redis）。
- 触发型用例（自造数据 + @Transactional 回滚 + mapper DB 断言）：发帖 4 表落库断言、标签级联软删断言、绑定查重、模拟数据对称性与分布（抽样统计容差）、软删后全路径过滤。
- 观测型用例（dev 存量数据软校验）：Feed 分页连续性（nextCursor 逐页拉取无重复无跳空）、canView 全矩阵（explore Q1-Q5 数据示例直接转用例种子：张三/李四/王五/赵六/钱七 与 P1-P5 帖）。
- 缓存：失效路径（bind 后 INCR 版本 → 再读回源新值）与降级路径（Redis 异常 mock 回源）。
- 用例清单由阶段四 test-cases.md 冻结，映射 test-mapping.md。

### 13.2 页面级测试（Playwright 端到端）

- 前提：服务 dev 启动 + `POST /api/mock-data`（userCount=100, avgFriendsPerUser=10, postCount=50）。
- 场景脚本（对应用户目标，任务 #7）：①用户列表/切换视角（Cookie mockUserId 生效）②我的好友/按标签筛 ③标签 CRUD+绑定 ④发布页发 4 类可见范围帖 ⑤Feed 瀑布流滚动加载（hasMore/nextCursor）⑥视角切换验证隐私（P1-P5 矩阵：切到王五可见 P1、切到赵六不可见）⑦图片上传→发图帖→Feed 展示。
- 断言：页面 DOM 元素 + 后端 API 响应双通道（数据级断言兜底）。

---

## 14. 审查修复记录（design-review-1 回炉）

> 本轮修复对象：`design-review-1.md`（阻塞级 3 + 建议级 12）。修复原则：不扩大范围、不动 C1-C17/D1-D7 已定稿决策；建议级逐条接受修复或拒绝留痕。派生物 api.md / tasks.md / sql.md 已同步修改。

| review 条目 | 级别 | 处置 | 修复位置 |
|------------|------|------|---------|
| B1 Feed 空页死循环（3 批用尽可见=0 时 nextCursor 无从构造 → 前端退化首页请求永久循环） | 阻塞 | **接受**：双锚点语义（页末锚点/扫描进度锚点）重写算法，显式定义 `items=[] && hasMore=true && nextCursor≠null` 合法组合，前端连续 3 空页停止 | design §5.4（算法重写）、§3.2.16（nextCursor/hasMore 字段语义 + 停止规则）；api.md 接口16；tasks T080/T110 |
| B2 参数越界策略三处矛盾（1001 vs 越界归默认 vs 未定义） | 阻塞 | **接受**：全项目统一"缺省用默认值，显式越界抛 1001（mock 配置类 1040）"；接口15 limit 越界行为补齐定义 | design §3.2.1（统一策略声明）、§3.2.15；api.md 接口1/15、§1.6；tasks T010/T070/T080 |
| B3 viewerId 校验缺失（0 穿透产脏帖；超 Long 抛 NumberFormatException 落 code=100） | 阻塞 | **接受**：显式 viewerId 强校验 `^\d{1,18}$` + >0 + try-catch，非法抛 1001 不回退 Cookie；Cookie 脏值宽松视为未选择（1003） | design §2.3 A3、§6.1；api.md §1.2；tasks T100 |
| 建议1 删帖幂等表述矛盾 | 建议 | **接受**：统一"删帖/删标签非幂等（1010/1004），仅解绑幂等"，前端防双击 | design §5.6 规则 5b；api.md §1.5、接口14；tasks T110 |
| 建议2 viewerId 即身份无认证未留痕 | 建议 | **接受**：§7.3 越权行补显式假设留痕 | design §7.3 |
| 建议3 deleteTag INCR"或方法尾"违反事务内禁 Redis | 建议 | **接受**：删除"方法尾"选项，定稿 afterCommit 回调唯一方式 | design §5.6 规则 8、§6.2；tasks T030 |
| 建议4 并发绑定可落重复记录 | 建议 | **接受（组合方案）**：DB 唯一键不可行（软删占键挡合法重绑，§4.1）；定稿 Service 查重 + **展示组装侧 distinct 去重** + 前端防抖，并发窗口留痕 | design §5.6 规则 5；tasks T020/T030（组装去重） |
| 建议5 listUserPosts limit 先截断后过滤漏旧公开帖 | 建议 | **接受**：limit 定义为**结果上限**，扫描按 limit×3×3 批放大补偿（与 Feed 同口径） | design §3.2.15、§5（limit 语义定稿段）；api.md 接口15；tasks T070 |
| 建议6 Base64 decode 异常未 catch 转 1030 | 建议 | **接受**：try-catch IllegalArgumentException → 1030，与格式校验同码 | design §3.2.16、§5.4；api.md 接口16；tasks T080 |
| 建议7 mock clear 单事务违背 R11 + 中断缓存 stale | 建议 | **接受**：clear 改 8 表逐个独立小事务；clear 完成即先 INCR 失效缓存（中断恢复语义） | design §5.5、§9；tasks T090 |
| 建议8 空好友集不回填缓存（每请求穿透） | 建议 | **接受**：空集回填哨兵成员 "0"（userId 恒 >0 无冲突），读取过滤哨兵 | design §5.4、§6.2；tasks T020 |
| 建议9 图片目录相对路径受工作目录影响 | 建议 | **接受**：默认值改绝对路径 `${user.home}/moments-data/images` | design §6.2；tasks T040/T100 |
| 建议10 type=1/2 携带可见性列表"忽略须传空"自相矛盾 | 建议 | **接受**：定稿静默忽略（不校验、不落库，微信语义：选公开后选择器置灰） | design §3.2.12；api.md 接口12；tasks T060 |
| 建议11 explore/design bind 事务口径不一 | 建议 | **接受（design 侧留痕统一）**：explore.md 为已确认的阶段二产物不改；design §6.2 注明"单表写无 @Transactional（单 DML 原子），行为等价，tester 断言以本设计为准" | design §6.2 IFriendTagService 行 |
| 建议12 上传无频控 | 建议 | **拒绝（留痕）**：无登录体系无可靠用户维度挂频控（viewerId 非凭证）；IP 频控对单机实验室属过度设计；磁盘风险由环境可重置兜底；生产化前补（§12 遗留 7） | design §7.3、§12 |
| 环境补充约束（主会话 2026-09-20 追加） | 约束 | **已落**：dev 实际 MySQL 5.7.44，DDL 双兼容 5.7/8.0（utf8mb4_general_ci、无表达式默认值/函数索引/CHECK） | design §2.3 A11、§4.1、§8、§11；sql.md 头部兼容性标注 + §0 |
| R2-NB1 非数字→1001 无承载（Spring 绑定层抛 MethodArgumentTypeMismatchException 落 code=100） | 阻塞 | **接受**：BizExceptionAdvice 补 `@ExceptionHandler(MethodArgumentTypeMismatchException.class)` 统一转 1001（最小改动，A10 范式），覆盖全部数值 query/路径参数 | design §6.1、§9；api.md §1.4；tasks T100（伪代码+验收） |
| R2-NB2 T020 跨任务方法引用未声明依赖（selectTagNamesByOwnerAndFriends 属 T030） | 阻塞 | **接受**：方法与 FriendTagRelationMapper 建文件动作归属前移 T020（T020 自建该 Mapper 仅含此方法），T030 改为追加其余方法，Depends/验收同步 | tasks T020/T030（涉及文件+伪代码+验收） |
| R2-建议1 nextCursor 术语与双锚点冲突 | 建议 | **接受**：对外契约统一"不透明游标 + hasMore=true 必非 null"不变式表述，删除非空页绝对化为扫描进度锚点的说法 | design §3.2.16；api.md 接口16 |
| R2-建议2 标签缓存空集哨兵不对称（无标签 viewer 每帖穿透） | 建议 | **接受**：FriendTagCacheManager 空 tagIds 同样回填哨兵 "0"，读取过滤 | design §6.2；tasks T030 |
| R2-建议3 配置双机制分裂（@ConfigurationProperties 字段默认值不解析占位符） | 建议 | **接受**：删 ImageStorageProperties，统一 @Value 单一承载（两处默认值逐字一致并留核对要求） | design §6.2；tasks T040 |
| R2-建议4 T001/T002 DDL 摘要缺 COLLATE | 建议 | **接受**：伪代码表选项补 `COLLATE = utf8mb4_general_ci` + 验收加"与 sql.md 逐字一致（含 COLLATE）" | tasks T001/T002 |
| R2-建议5 mock 可见性抽样有放回可撞唯一键致中断 | 建议 | **接受**：抽样改 shuffle+subList（无放回）保证不重复 | tasks T090 |
| R2-建议6 listUserPosts 放大补偿绝对化表述 | 建议 | **接受**：补有界性留痕（连续不可见 >9×limit 仍截断，与 Feed 3 空页停止同族权衡，实验室不可达） | design §3.2.15 |
| R2-建议7 cursor id 段无位数上限可 Long 溢出落 100 | 建议 | **接受**：正则收紧 `^\d{13}:\d{1,18}$` + 解析 try-catch 双保险转 1030 | design §3.2.16；api.md 接口16；tasks T003/T080 |
| R2-建议8 friendCount 计数粒度未随 distinct 兜底 | 建议 | **接受**：friendCount 改 `COUNT(DISTINCT friend_user_id)` | design §5.6 规则 5；api.md 接口5；tasks T030 |
| R3-阻塞1 T020 tagId 过滤分支残留跨任务依赖（FriendTagMapper.selectByTagId + selectFriendIdsByTag 属 T030，R2-NB2 半闭环；回炉上限已用尽触发熔断） | 阻塞 | **接受·主 agent 代行人工裁决（留痕 workflow-state）方案①**：FriendTagMapper 建文件动作前移 T020（仅含 selectByTagId），FriendTagRelationMapper 前移方法集扩为 selectTagNamesByOwnerAndFriends + selectFriendIdsByTag；T030 两 Mapper 均改「追加其余方法」；T020 伪代码 tagId 分支引用全部自洽，验收两条（1004 可复现 + 仅依赖 T010 链可独立编译交付）不再互斥；Depends/并行组/DAG 不变（17 task / 9 组） | tasks T020（描述/涉及文件/伪代码/验收）、T030（涉及文件/伪代码）；design §6.3 两 Mapper 方法归属标注 |
| R3-裁决追加项 JSON body 字段类型非法落 code=100 | 约束 | **接受·主 agent 代行人工裁决追加**：BizExceptionAdvice 补 `HttpMessageNotReadableException` handler → 1001（与 TypeMismatch handler 同模式，采纳 review-3 建议 2 方案一），防阶段四 body 维度用例与实现分裂 | design §6.1（三类异常）、§9（新增行）；api.md §1.4；tasks T100（伪代码 + 验收 body 用例） |
| R3-建议1 design §6.3 FriendTagMapper 漏列 countFriendsByTagIds | 建议 | **接受**：方法清单补齐（派生物对齐真源） | design §6.3 |
| R3-建议3 In DTO x7 数字残留（实际 4） | 建议 | **接受**：改 x4 并注明散参化 | design §2.2；tasks T005（表行 + 标题） |
| R3-建议4 修复痕迹清理（T010 死代码注释 / T002 post 表缺 COLLATE） | 建议 | **接受**：删「或非数字」死代码字样（改由绑定层承载）；T002 伪代码补 COLLATE | tasks T010、T002 |
| S4-裁决（test-case-review-1 暴露的派生文档与真源不一致）·**来源：Stage 4 审查裁决（主 agent，留痕 workflow-state）** | 一致性 | **已落地**：① tasks T030 bindUser 校验顺序改为 1002 先于 1007（对齐 design §3.2.9 异常码序，仅同步伪代码）；② api.md 接口12 imageUrls 补前缀契约声明（`^/images/[A-Za-z0-9._-]+$` + 非法 1001，与 design §3.2.12/§7.3 既有规则逐字对齐） | tasks T030；api.md 接口12（参数表 + 异常码表） |
| S4-裁决2（test-case-review-2 附注勘误）·**来源：Stage 4 审查裁决（主 agent）** | 一致性 | **已落地**：api.md §1.6 汇总表 1011 行去掉触发接口 15（接口15 明细定稿为 canView 过滤静默不报错，1011 仅接口13 详情直达路径触发），汇总与明细一致 | api.md §1.6（1011 行，加澄清注） |
| S7-补录1（BizExceptionAdvice 第 4 类异常）·**来源：Stage 6 编排裁决8，阶段七 arch-review 建议补录** | 基线回写 | **已落码、基线已回写**：`MaxUploadSizeExceededException`（超过 multipart 容器上限，Spring 在 Controller 前抛出）→ 1021，与 Service 层应用级 5MB 校验同码收口，防落框架 code=100；§6.1 异常清单同步为四类 | design §6.1 |
| S7-补录2（WebConfig 编程式 multipart 配置）·**来源：Stage 6 编排裁决4，阶段七 arch-review 建议补录** | 基线回写 | **已落码、基线已回写**：WebConfig 增编程式 `MultipartConfigElement` @Bean（maxFileSize=6MB / maxRequestSize=12MB，为应用级 5MB 校验留余量并拦超限请求体）；§2.2 moments-web 修改列补一句说明 | design §2.2 |

---

> 自检（tech-designer 1-4，review-1 + review-2 + review-3 三轮后复检）：每条 FR（G1-G10 ↔ PRD §2/§3 全条目）在 §3/§5 有对应 ✓；8 表含 4 通用字段 ✓；索引 pk_/uniq_/idx_ 规范 ✓；事务内无 RPC/MQ/Redis/文件 IO ✓（INCR 定稿 afterCommit）；更新用"只设 id+待更新字段"实体 ✓；复用项标注路径、业务代码声明无复用基线 ✓；入参>3 用 DTO ✓；错误码枚举无 magic number（SocialErrorCode）✓；PII 无（§7.3）✓；幂等设计逐场景说明（§5.6/§7.2）✓；日志规范（§10）✓。**review-1：阻塞 3/3、建议 11 接受 1 拒绝留痕；review-2：阻塞 2/2、建议 8/8 接受；review-3：阻塞 1（R2-NB2 半闭环残留）+ 建议 4 中的 ①③④ 全部按主 agent 代行人工裁决落地（§14 R3 行，含裁决来源标注），T020 涉及文件+Depends+伪代码+验收四者自洽**；DDL 双版本兼容（A11）✓；四文档交叉一致性（接口 17/错误码/DDL/任务依赖）已复对 ✓。
