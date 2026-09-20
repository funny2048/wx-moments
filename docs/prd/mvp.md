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


