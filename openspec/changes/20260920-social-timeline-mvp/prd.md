# Social Timeline PRD

## 1. 项目概述

### 1.1 项目定位

Social Timeline 是一个**社交 Feed 系统架构实验室项目**，通过模拟微信朋友圈核心业务，逐步实现社交关系、朋友圈、隐私权限和 Feed 信息流。

项目重点是验证和演进不同 Feed 架构方案，而非完整复刻微信。

### 1.2 项目目标

分三期实现：

| 阶段 | 核心目标                    |
| -- | ----------------------- |
| 一期 | 模拟微信好友关系及好友标签           |
| 二期 | 实现朋友圈图文发布、隐私权限、Feed 瀑布流 |
| 三期 | 研究 Feed 架构及高并发场景        |

## 2. 一期：好友关系

### 2.1 用户

系统提供基础用户数据。

用户字段：

* userId
* nickname
* avatar
* gender
* city
* createTime

不实现注册、登录等完整用户体系。

### 2.2 好友关系

用户之间存在双向好友关系。

```text
User A ←→ User B
```

一期不实现真实好友添加流程，所有好友关系通过模拟数据生成。

### 2.3 好友标签

用户可以创建好友标签，并将好友归类到一个或多个标签。

默认标签：

* 家人
* 亲戚
* 同事
* 同学
* 朋友
* 球友
* 客户
* 其他

一个好友可以拥有多个标签。

例如：

```text
张三
├── 同事
└── 朋友

李四
├── 同学
└── 球友
```

### 2.4 标签管理

支持：

* 创建标签
* 修改标签
* 删除标签
* 给好友添加标签
* 移除好友标签
* 按标签查看好友

### 2.5 模拟数据

系统提供批量初始化能力。

支持配置：

```text
用户数量
好友数量
标签数量
```

最低要求：

```text
1000+ 好友关系
```

支持后续扩展：

```text
1000
5000
10000
50000
100000
```

模拟数据应尽量符合真实社交关系特征，而非完全随机。

例如：

```text
同事       25%
同学       20%
朋友       25%
家人        5%
亲戚        5%
兴趣好友   15%
其他        5%
```

一个好友可以同时属于多个标签。

### 2.6 一期页面

* 用户列表
* 我的好友
* 好友详情
* 标签管理
* 好友标签管理
* 模拟数据生成

## 3. 二期：朋友圈

### 3.1 朋友圈发布

用户可以发布朋友圈。

支持：

* 纯文字
* 纯图片
* 文字 + 图片

单条朋友圈最多 9 张图片。

暂不实现：

* 视频
* 链接
* 音乐
* 位置
* @好友
* 内容审核

### 3.2 朋友圈数据

朋友圈包含：

```text
postId
userId
content
visibilityType
createTime
updateTime
status
```

图片单独存储：

```text
postId
imageUrl
sort
```

### 3.3 隐私权限

朋友圈支持 4 种可见范围。

| 类型 | 名称   | 说明           |
| -- | ---- | ------------ |
| 1  | 公开   | 所有人可见        |
| 2  | 私密   | 仅自己可见        |
| 3  | 部分可见 | 指定标签或指定好友可见  |
| 4  | 不给谁看 | 指定标签或指定好友不可见 |

### 3.4 公开

```text
visibilityType = 1
```

所有用户均可查看。

### 3.5 私密

```text
visibilityType = 2
```

只有朋友圈作者本人可以查看。

### 3.6 部分可见

```text
visibilityType = 3
```

可以选择：

* 好友标签
* 指定好友
* 标签 + 指定好友

例如：

```text
部分可见：

同事
大学同学
张三
李四
```

用户满足任意一个条件即可查看。

```text
标签匹配 OR 好友匹配
```

### 3.7 不给谁看

```text
visibilityType = 4
```

可以选择：

* 好友标签
* 指定好友
* 标签 + 指定好友

例如：

```text
不给谁看：

同事
王五
赵六
```

命中任意排除条件的用户不可查看。

```text
标签匹配 OR 好友匹配
        ↓
      不可见
```

### 3.8 隐私判断

系统提供统一权限判断：

```text
canView(userId, postId)
```

判断规则：

```text
作者本人
    ↓
   可见

公开
    ↓
   可见

私密
    ↓
   不可见

部分可见
    ↓
匹配标签/好友
    ↓
 可见 / 不可见

不给谁看
    ↓
匹配标签/好友
    ↓
不可见 / 可见
```

### 3.9 朋友圈 Feed

用户进入朋友圈后，可以查看自己有权限查看的朋友圈。

排序规则：

```text
createTime DESC
```

Feed 展示：

* 用户头像
* 用户昵称
* 文字内容
* 图片
* 发布时间
* 可见范围内的朋友圈

### 3.10 Feed 瀑布流

采用分页加载。

第一版采用 Cursor 分页：

```http
GET /api/feed?cursor={cursor}&pageSize=20
```

不采用传统 Page + Offset 分页。

返回：

```json
{
  "items": [],
  "nextCursor": "xxx",
  "hasMore": true
}
```

### 3.11 Feed 查询逻辑

基础版本：

```text
当前用户
    ↓
查询好友关系
    ↓
获取好友朋友圈
    ↓
权限过滤
    ↓
按时间倒序
    ↓
Cursor 分页
    ↓
返回 Feed
```

## 4. 数据模型

### 4.1 核心实体

```text
User
  │
  ├── Friendship
  │
  ├── FriendTag
  │      │
  │      └── FriendTagRelation
  │
  └── Post
          │
          ├── PostImage
          │
          ├── PostVisibilityUser
          │
          └── PostVisibilityTag
```

### 4.2 核心表

```text
user
friendship

friend_tag
friend_tag_relation

post
post_image

post_visibility_user
post_visibility_tag
```

## 5. 二期 API

### 好友

```http
GET /api/friends
GET /api/friends/{userId}
GET /api/friends?tagId={tagId}
```

### 标签

```http
GET    /api/friend-tags
POST   /api/friend-tags
PUT    /api/friend-tags/{tagId}
DELETE /api/friend-tags/{tagId}

POST   /api/friend-tags/{tagId}/users/{userId}
DELETE /api/friend-tags/{tagId}/users/{userId}
```

### 朋友圈

```http
POST   /api/posts
GET    /api/posts/{postId}
DELETE /api/posts/{postId}
```

### Feed

```http
GET /api/feed
```

### 图片

```http
POST /api/images/upload
```

## 7. 技术架构

- 服务端本期只使用 mysql 8.0 + redis 实现，不使用其他中间价，图片文件存储使用存储本地一个文件夹的方式。
- 前端架构不限，但是要页面样式要贴近微信朋友圈样式即可。

## 8. 项目边界

### 本项目暂不实现

* 注册登录
* 好友申请
* 好友验证
* 内容审核
* 视频朋友圈
* 点赞
* 评论
* 转发
* 收藏
* 消息通知
* 推荐算法
* 广告
* 商业化能力

这些功能不属于当前架构实验的核心范围。

## 9. 版本目标

### Phase 1 完成真实社交关系数据模拟。

```text
User
 ↓
Friendship
 ↓
Friend Tag
 ↓
Mock Data
```

### Phase 2 完成朋友圈核心业务和隐私权限。

```text
Friendship
 ↓
Post
 ↓
Privacy
 ↓
Feed
```

---

## 澄清补充（自主决策留痕，待用户追认）

> ⚠️ **决策模式说明**：用户于 2026-09-20 夜间下达目标后休息，授权 AI 自主完成需求（"明天早上我来检查工作结果"）。以下含糊点由 AI 按"项目定位=架构实验室、无真实用户数据"逐条自主决策，**每条附决策依据**，明早由用户追认或否决。

| # | 含糊点 | 决策 | 依据 |
|---|--------|------|------|
| C1 | 需求范围（一期还是全部） | 一期+二期全部实现（好友/标签/模拟数据/朋友圈/隐私/Feed），含 PRD §2.6 全部一期页面与二期 Feed 页 | 用户目标"完成这个需求"指向整份 mvp.md；PRD 自身含两期 |
| C2 | 前端技术选型 | 原生 HTML + CSS + 原生 JS（无构建链），由 moments-web 以静态资源托管，样式贴近微信朋友圈 | PRD §7"前端架构不限"；无 Node 构建依赖最稳妥，页面级测试直接可跑 |
| C3 | 当前用户机制（无登录体系） | 提供"用户切换"能力：页面顶部用户切换器，选中 userId 写入 Cookie（`mockUserId`），后端从 Cookie 解析当前用户；API 亦支持 `userId` 查询参数覆盖 | PRD 明确不做注册登录，但隐私权限判断必须有"当前用户"视角；页面级测试需切换视角验证隐私 |
| C4 | Feed 是否含自己的帖子 | 含。Feed = 自己的帖子 + 好友的帖子，经权限过滤后按 createTime DESC | 微信朋友圈语义（自己发的朋友圈出现在自己的 Feed）；PRD §3.9"查看自己有权限查看的朋友圈"包含本人帖子（作者本人可见） |
| C5 | 标签归属 | friend_tag 带 userId（标签属于标签创建人），可见性判断用"作者给好友打的标签" | PRD §2.3"用户可以创建好友标签"；微信语义标签是 per-user 的 |
| C6 | 可见范围空集行为 | visibilityType=3（部分可见）且指定范围为空 → 除作者外无人可见；visibilityType=4（不给谁看）且范围为空 → 等同公开 | 空集语义：无命中条件则按"条件不满足"处理 |
| C7 | post.status 语义 | 1=正常，0=已删除；`DELETE /api/posts/{postId}` 为软删除（is_del 置 1），status 保留业务态 | mysql-guide 软删除规范；PRD 数据模型含 status 字段 |
| C8 | Cursor 编码 | cursor = Base64("createTime:postId")，排序 `create_time DESC, id DESC` 双字段保证稳定分页 | 同秒多条帖子需唯一排序键 |
| C9 | 主键类型 | DB 侧 id bigint（mysql-guide 通用字段强制）；Java DO 侧 Long（包装类规范） | java-guide Integer 约定与 mysql-guide bigint 冲突，取数据安全侧（避免自增溢出）；依据核心执行原则-正确性优先 |
| C10 | 签名认证 | 不实现 apiKey+timestamp+sign 签名 | java-guide 签名要求针对"面向 C/B 端真实用户的敏感写接口"；本项目无登录体系、无真实用户、本地实验室环境，PRD §8 明确排除相关能力 |
| C11 | 好友关系存储 | 双向关系存两条记录（A→B 与 B→A），按 user_id 索引查询 | 查询简单高效（避免 OR 双列扫描）；设计阶段定稿 |
| C12 | Redis 用途 | 最小化：好友 ID 集合缓存 + 用户标签缓存（canView 高频调用），好友/标签变更时失效；不强依赖（缓存失败回源 DB） | PRD §7 要求 mysql+redis；避免过度设计，Feed 权限过滤是唯一高频读场景 |
| C13 | 删除标签的级联 | 软删 friend_tag 时级联软删 friend_tag_relation；post_visibility_tag 不级联（按 tagId 匹配自然失效） | 已删标签在可见性判断中匹配不到即不生效；保留帖子可见性配置历史 |
| C14 | API 最小扩展 | 增加 `GET /api/posts?userId={userId}`（查指定用户有权限可见的帖子列表） | 支撑一期页面"好友详情"展示其朋友圈；PRD API 清单未列但页面需要，属最小必要扩展 |
| C15 | 模拟数据幂等 | 生成 API 支持配置（用户数/人均好友数/标签数），重复生成前可选清空业务数据（默认追加，参数 clear=true 清空重建） | PRD §2.5"批量初始化能力"+可扩展规模；页面提供生成入口 |
| C16 | 图片访问 | 上传落本地文件夹（可配置路径），通过 `/images/{fileName}` 静态映射访问；生成 1x1~9 宫格占位图能力用于模拟数据 | PRD §7 图片本地文件夹存储；模拟朋友圈需要图片 |
| C17 | 图片格式与大小校验 | 仅 jpg/png/gif/webp，单张 ≤5MB，最多 9 张 | 防御性编码规范；PRD 单条最多 9 图 |

**待用户追认方式**：明早审查时逐条确认；任何否决项将按 WORKFLOW 回退规则处理（否决涉及需求/设计 → 回阶段三，涉及用例 → 回阶段四）。


