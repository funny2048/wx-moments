# API 接口规格 — Social Timeline MVP

> ⚠️ 派生产物：禁止独立编辑，真源为 design.md §3 接口设计
> 基于模板：`workflow/_shared/api-spec-template.md` v1.0
> 关联技术设计：`openspec/changes/20260920-social-timeline-mvp/design.md`
> 生成时间：2026-09-20
> OpenAPI 版本：3.0.3

---

## 1. 通用约定

### 1.1 Base URL

| 环境 | Base URL |
|------|---------|
| 本地 dev | `http://localhost:{port}`（端口以 moments-web 启动日志 "Application Start Success! ... port = xxx" 为准，默认 8080；无 context-path） |

### 1.2 鉴权方式（C3：无登录体系，Cookie 视角机制）

| 项 | 约定 |
|----|------|
| 当前用户（viewer） | 服务端按优先级解析：**query 参数 `viewerId`** > **Cookie `mockUserId`**；两者皆缺失时，需要当前用户的接口返回业务码 1003。**viewerId 强校验（B3）**：显式传入的 viewerId 必须为 >0 的整数且不超过 Long 范围（≤18 位数字），非法（非纯数字 / ≤0 / 超范围）返回业务码 1001，**不会静默回退 Cookie**；Cookie 值非法（脏数据）视为未选择（1003） |
| 用户切换 | 前端用户切换器将选中 userId 写入 Cookie `mockUserId`（Path=/）；页面级测试/隐私验证用 `viewerId` 参数临时覆盖视角 |
| `_appId` | 所有接口第一个参数为 `String _appId`（无注解，query 传递，任意字符串，如 `moments-web`），仅日志追踪用途，不参与鉴权 |
| 签名认证 | 不实现（C10：实验室环境豁免） |

### 1.3 统一响应结构（ApiResult，moments 项目实际结构）

```json
{
  "code": 0,
  "msg": "success",
  "data": { }
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `code` | `number` | 返回码。`0` 成功；`1001+` 业务错误（见 1.6）；`100` 系统异常兜底 |
| `msg` | `string` | 提示信息。成功 `"success"`；失败为可读错误描述 |
| `data` | `object` / `array` / `null` | 业务数据载体；无数据接口（删除/绑定等）成功时为 `null` |

### 1.4 分页参数约定

| 接口类型 | 约定 |
|---------|------|
| 用户列表（传统分页） | `pageNo`（缺省默认 1）+ `pageSize`（缺省默认 20，范围 1-500）；响应含 `total` |
| Feed（Cursor 分页，PRD §3.10） | `cursor`（首页不传；下页传上响应 `nextCursor`）+ `pageSize`（缺省默认 20，范围 1-50）；响应含 `nextCursor`/`hasMore`，**无 total** |
| 帖子列表（C14） | `limit`（缺省默认 100，范围 1-500，语义=**结果条数上限**，服务端放大补偿扫描），无分页页码 |

**参数越界统一策略（B2 定稿 + NB1 承载机制）**：分页/limit 类参数**缺省时使用默认值；显式传入越界（含非数字）一律返回 1001**。模拟数据配置类参数越界返回专用码 1040。不存在"越界归默认"的宽松处理。**非数字的承载机制**：数值参数非数字（如 `pageNo=abc`）由 Spring 绑定层抛 `MethodArgumentTypeMismatchException`（请求到不了业务校验），由全局 `BizExceptionAdvice` 专用 handler 统一转 1001（design §6.1/§9）——覆盖本表全部数值 query/路径参数（pageNo/pageSize/tagId/userId/limit/postId/size 等）；**JSON 请求体字段类型非法（如 body `"visibilityType":"abc"`、`"userCount":"abc"`）由 Jackson 抛 `HttpMessageNotReadableException`，经同 Advice 专用 handler 同样统一转 1001（R3 裁决追加，接口12/11 等 JSON body 接口适用）**。

### 1.5 幂等键约定

本需求不做 requestId 幂等（design §5.6 规则 7）：重复绑定返回 1008（查重拦截）；**幂等口径（建议 1 定稿）：仅解绑（接口10）幂等（绑定不存在也返回成功）；删帖（接口14）/删标签（接口8）对已删或不存在目标返回 1010/1004（非幂等，前端按钮 loading 防双击）**；模拟数据重复生成=追加（`clear=true` 为清空重建，页面需二次确认）。

### 1.6 通用错误码表（合并 design §3 各接口异常码去重）

| code | 含义 | 触发接口 |
|------|------|---------|
| 1001 | 参数校验失败（缺参/越界/格式非法/超 9 图等联合校验） | 全部 |
| 1002 | 用户不存在 | 2、4、9、12、15 |
| 1003 | 未选择当前用户（viewer 缺失） | 3-16（除 11、17） |
| 1004 | 标签不存在 | 5-10、12 |
| 1005 | 标签名重复 | 6、7 |
| 1006 | 非标签归属人（无权操作他人标签） | 7-10、12 |
| 1007 | 仅可对好友打标签 | 9 |
| 1008 | 标签已绑定该好友 | 9 |
| 1010 | 帖子不存在或已删除 | 13、14 |
| 1011 | 无权查看该帖子（canView 不过；接口15 为 canView 过滤静默不报错，不触发本码） | 13 |
| 1012 | 仅作者可删除该帖子 | 14 |
| 1020 | 图片格式不支持（仅 jpg/jpeg/png/gif/webp） | 17 |
| 1021 | 图片超过 5MB | 17 |
| 1022 | 单帖图片超过 9 张 | 12 |
| 1030 | 分页游标非法（Base64 解码/格式校验失败） | 16 |
| 1040 | 模拟数据配置非法 | 11 |
| 100 | 系统异常（框架兜底） | 全部 |

### 1.7 通用枚举值域

| 字段 | 取值 |
|------|------|
| `gender` / `genderStr` | `0`=未知、`1`=男、`2`=女 |
| `visibilityType` / `visibilityTypeStr` | `1`=公开、`2`=私密、`3`=部分可见、`4`=不给谁看 |
| `status` / `statusStr` 语义 | `1`=正常、`0`=删除（业务态快照，软删走 is_del，接口不外露 is_del） |

---

## 2. 接口列表（17 个）

| # | 接口名称 | Method | URL | 类型 | 分页 |
|---|---------|--------|-----|------|------|
| 1 | 用户列表 | `GET` | `/api/users` | 表格 | 是（pageNo/pageSize） |
| 2 | 用户详情 | `GET` | `/api/users/{userId}` | 对象 | 否 |
| 3 | 我的好友列表 | `GET` | `/api/friends` | 表格 | 否（tagId 可选过滤） |
| 4 | 好友详情 | `GET` | `/api/friends/{userId}` | 对象 | 否 |
| 5 | 标签列表 | `GET` | `/api/friend-tags` | 表格 | 否 |
| 6 | 创建标签 | `POST` | `/api/friend-tags` | 对象 | 否 |
| 7 | 修改标签 | `PUT` | `/api/friend-tags/{tagId}` | 对象 | 否 |
| 8 | 删除标签 | `DELETE` | `/api/friend-tags/{tagId}` | 空 | 否 |
| 9 | 绑定好友标签 | `POST` | `/api/friend-tags/{tagId}/users/{userId}` | 空 | 否 |
| 10 | 解绑好友标签 | `DELETE` | `/api/friend-tags/{tagId}/users/{userId}` | 空 | 否 |
| 11 | 生成模拟数据 | `POST` | `/api/mock-data` | 对象 | 否 |
| 12 | 发布朋友圈 | `POST` | `/api/posts` | 对象 | 否 |
| 13 | 帖子详情 | `GET` | `/api/posts/{postId}` | 对象 | 否 |
| 14 | 删除帖子 | `DELETE` | `/api/posts/{postId}` | 空 | 否 |
| 15 | 指定用户帖子列表 | `GET` | `/api/posts` | 表格 | limit 上限截断 |
| 16 | Feed 瀑布流 | `GET` | `/api/feed` | 表格 | 是（Cursor） |
| 17 | 图片上传 | `POST` | `/api/images/upload` | 对象 | 否 |

> 所有接口均另携带 `_appId` query 参数（见 1.2）；需要视角的接口支持 `viewerId` 覆盖（默认取 Cookie `mockUserId`）。

---

## 3. 接口详细

### 接口1：用户列表

| 项目 | 说明 |
|------|------|
| 接口名称 | 用户列表 |
| 请求方式 | `GET` |
| URL | `/api/users` |
| 用途 | 分页返回全部模拟用户（用户列表页 + 用户切换器数据源） |
| 所属模块 | 一期·用户 |
| 类型 | 表格（分页） |

#### 请求参数

| 参数 | 类型 | 必填 | 示例 | 说明 |
|---|---|---|---|---|
| `_appId` | `string` | 是 | `moments-web` | 调用方标识（日志追踪） |
| `pageNo` | `number` | 否 | `1` | 页码，缺省默认 `1`，从 1 开始；显式传入 <1 或非数字返回 1001 |
| `pageSize` | `number` | 否 | `20` | 每页条数，缺省默认 `20`，范围 1-500（切换器场景传 500）；显式越界返回 1001 |

#### 响应示例

```json
{
  "code": 0,
  "msg": "success",
  "data": {
    "total": 1000,
    "users": [
      { "userId": 1, "nickname": "张三", "avatar": "/images/ph_avatar_01.png",
        "gender": 1, "genderStr": "男", "city": "杭州", "createTimeStr": "2026-09-20 10:00:00" },
      { "userId": 2, "nickname": "李四", "avatar": "/images/ph_avatar_02.png",
        "gender": 1, "genderStr": "男", "city": "上海", "createTimeStr": "2026-09-20 10:00:01" },
      { "userId": 3, "nickname": "王五", "avatar": "/images/ph_avatar_03.png",
        "gender": 2, "genderStr": "女", "city": "北京", "createTimeStr": "2026-09-20 10:00:02" }
    ]
  }
}
```

#### 字段说明

| 字段 | 类型 | 说明 |
|---|---|---|
| `data.total` | `number` | 总用户数，单位：个 |
| `data.users` | `array` | 当前页用户数组 |
| `data.users[].userId` | `number` | 用户 ID |
| `data.users[].nickname` | `string` | 昵称（允许重名） |
| `data.users[].avatar` | `string` | 头像 URL（`/images/{fileName}`） |
| `data.users[].gender` | `枚举` | 性别。取值：`0`=未知、`1`=男、`2`=女 |
| `data.users[].genderStr` | `string` | 性别中文（未知/男/女） |
| `data.users[].city` | `string` | 城市 |
| `data.users[].createTimeStr` | `string` | 注册时间（yyyy-MM-dd HH:mm:ss） |

#### 异常码

| code | 触发条件 |
|------|---------|
| 1001 | pageNo/pageSize 越界或非数字 |

---

### 接口2：用户详情

| 项目 | 说明 |
|------|------|
| 接口名称 | 用户详情 |
| 请求方式 | `GET` |
| URL | `/api/users/{userId}` |
| 用途 | 查询单个用户基础信息（好友详情页头部） |
| 所属模块 | 一期·用户 |
| 类型 | 对象 |

#### 请求参数

| 参数 | 类型 | 必填 | 示例 | 说明 |
|---|---|---|---|---|
| `_appId` | `string` | 是 | `moments-web` | 调用方标识 |
| `userId`（路径） | `number` | 是 | `2` | 用户 ID，>0 |

#### 响应示例

```json
{
  "code": 0,
  "msg": "success",
  "data": {
    "userId": 2, "nickname": "李四", "avatar": "/images/ph_avatar_02.png",
    "gender": 1, "genderStr": "男", "city": "上海", "createTimeStr": "2026-09-20 10:00:01"
  }
}
```

#### 字段说明

| 字段 | 类型 | 说明 |
|---|---|---|
| `data.userId` | `number` | 用户 ID |
| `data.nickname` | `string` | 昵称 |
| `data.avatar` | `string` | 头像 URL |
| `data.gender` | `枚举` | 性别。取值：`0`=未知、`1`=男、`2`=女 |
| `data.genderStr` | `string` | 性别中文 |
| `data.city` | `string` | 城市 |
| `data.createTimeStr` | `string` | 注册时间（yyyy-MM-dd HH:mm:ss） |

#### 异常码

| code | 触发条件 |
|------|---------|
| 1001 | userId 缺失或 ≤0 |
| 1002 | 用户不存在（含已删除） |

---

### 接口3：我的好友列表

| 项目 | 说明 |
|------|------|
| 接口名称 | 我的好友列表（支持按标签过滤） |
| 请求方式 | `GET` |
| URL | `/api/friends` |
| 用途 | 当前用户视角的好友列表；`tagId` 传入时按标签筛选（合并 PRD §5 的两个 friends 语义） |
| 所属模块 | 一期·好友关系 |
| 类型 | 表格（全量返回，实验室规模不分页） |

#### 请求参数

| 参数 | 类型 | 必填 | 示例 | 说明 |
|---|---|---|---|---|
| `_appId` | `string` | 是 | `moments-web` | 调用方标识 |
| `tagId` | `number` | 否 | `11` | 标签过滤（>0；缺省=全部好友） |
| `viewerId` | `number` | 否 | `1` | 当前用户覆盖（默认取 Cookie `mockUserId`） |

#### 响应示例

```json
{
  "code": 0,
  "msg": "success",
  "data": [
    { "userId": 2, "nickname": "李四", "avatar": "/images/ph_avatar_02.png",
      "gender": 1, "genderStr": "男", "city": "上海", "tagNames": ["同学"] },
    { "userId": 3, "nickname": "王五", "avatar": "/images/ph_avatar_03.png",
      "gender": 2, "genderStr": "女", "city": "北京", "tagNames": ["同事", "朋友"] }
  ]
}
```

#### 字段说明

| 字段 | 类型 | 说明 |
|---|---|---|
| `data` | `array` | 好友数组（无好友时为 `[]`） |
| `data[].userId` | `number` | 好友用户 ID |
| `data[].nickname` | `string` | 昵称 |
| `data[].avatar` | `string` | 头像 URL |
| `data[].gender` | `枚举` | 性别。取值：`0`=未知、`1`=男、`2`=女 |
| `data[].genderStr` | `string` | 性别中文 |
| `data[].city` | `string` | 城市 |
| `data[].tagNames` | `string[]` | 当前用户给该好友打的标签名列表（顺序按标签 id，无标签为 `[]`） |

#### 异常码

| code | 触发条件 |
|------|---------|
| 1001 | tagId ≤0 或非数字 |
| 1003 | 未选择当前用户 |
| 1004 | tagId 对应标签不存在或不归属当前用户 |

---

### 接口4：好友详情

| 项目 | 说明 |
|------|------|
| 接口名称 | 好友详情 |
| 请求方式 | `GET` |
| URL | `/api/friends/{userId}` |
| 用途 | 好友基础信息 + 我给其打的标签（好友详情页；其朋友圈另调接口15） |
| 所属模块 | 一期·好友关系 |
| 类型 | 对象 |

#### 请求参数

| 参数 | 类型 | 必填 | 示例 | 说明 |
|---|---|---|---|---|
| `_appId` | `string` | 是 | `moments-web` | 调用方标识 |
| `userId`（路径） | `number` | 是 | `3` | 好友用户 ID |
| `viewerId` | `number` | 否 | `1` | 当前用户覆盖 |

#### 响应示例

```json
{
  "code": 0,
  "msg": "success",
  "data": {
    "userId": 3, "nickname": "王五", "avatar": "/images/ph_avatar_03.png",
    "gender": 2, "genderStr": "女", "city": "北京",
    "createTimeStr": "2026-09-20 10:00:02", "tagNames": ["同事", "朋友"]
  }
}
```

#### 字段说明

| 字段 | 类型 | 说明 |
|---|---|---|
| `data.userId` | `number` | 好友用户 ID |
| `data.nickname` / `data.avatar` / `data.city` | `string` | 基础信息 |
| `data.gender` | `枚举` | 性别。取值：`0`=未知、`1`=男、`2`=女 |
| `data.genderStr` | `string` | 性别中文 |
| `data.createTimeStr` | `string` | 注册时间（yyyy-MM-dd HH:mm:ss） |
| `data.tagNames` | `string[]` | 我给该好友打的标签名列表 |

#### 异常码

| code | 触发条件 |
|------|---------|
| 1001 | userId 缺失或 ≤0 |
| 1002 | 用户不存在 |
| 1003 | 未选择当前用户 |

---

### 接口5：标签列表

| 项目 | 说明 |
|------|------|
| 接口名称 | 我的好友标签列表 |
| 请求方式 | `GET` |
| URL | `/api/friend-tags` |
| 用途 | 当前用户的标签列表（含每标签好友数，标签管理页） |
| 所属模块 | 一期·好友标签 |
| 类型 | 表格 |

#### 请求参数

| 参数 | 类型 | 必填 | 示例 | 说明 |
|---|---|---|---|---|
| `_appId` | `string` | 是 | `moments-web` | 调用方标识 |
| `viewerId` | `number` | 否 | `1` | 当前用户覆盖 |

#### 响应示例

```json
{
  "code": 0,
  "msg": "success",
  "data": [
    { "tagId": 11, "tagName": "家人", "friendCount": 2, "createdTimeStr": "2026-09-20 10:00:00" },
    { "tagId": 12, "tagName": "同事", "friendCount": 25, "createdTimeStr": "2026-09-20 10:00:00" },
    { "tagId": 13, "tagName": "同学", "friendCount": 20, "createdTimeStr": "2026-09-20 10:00:00" }
  ]
}
```

#### 字段说明

| 字段 | 类型 | 说明 |
|---|---|---|
| `data` | `array` | 标签数组（无标签为 `[]`） |
| `data[].tagId` | `number` | 标签 ID |
| `data[].tagName` | `string` | 标签名 |
| `data[].friendCount` | `number` | 该标签下好友数（**按好友去重计数 COUNT(DISTINCT)**，与 tagNames 去重口径一致），单位：个 |
| `data[].createdTimeStr` | `string` | 创建时间（yyyy-MM-dd HH:mm:ss） |

#### 异常码

| code | 触发条件 |
|------|---------|
| 1003 | 未选择当前用户 |

---

### 接口6：创建标签

| 项目 | 说明 |
|------|------|
| 接口名称 | 创建好友标签 |
| 请求方式 | `POST` |
| URL | `/api/friend-tags` |
| 用途 | 当前用户创建标签（归属创建人，C5） |
| 所属模块 | 一期·好友标签 |
| 类型 | 对象 |

#### 请求参数（JSON 请求体 TagCreateIn）

| 参数 | 类型 | 必填 | 示例 | 说明 |
|---|---|---|---|---|
| `_appId` | `string` | 是 | `moments-web` | 调用方标识（query） |
| `tagName` | `string` | 是 | `球友` | 标签名，trim 后 1-16 字符 |
| `viewerId` | `number` | 否 | `1` | 当前用户覆盖（query） |

#### 响应示例

```json
{
  "code": 0,
  "msg": "success",
  "data": { "tagId": 21, "tagName": "球友", "friendCount": 0, "createdTimeStr": "2026-09-20 23:30:00" }
}
```

#### 字段说明

| 字段 | 类型 | 说明 |
|---|---|---|
| `data.tagId` | `number` | 新建标签 ID |
| `data.tagName` | `string` | 标签名 |
| `data.friendCount` | `number` | 好友数，固定 `0`，单位：个 |
| `data.createdTimeStr` | `string` | 创建时间（yyyy-MM-dd HH:mm:ss） |

#### 异常码

| code | 触发条件 |
|------|---------|
| 1001 | tagName 为空或 trim 后超 16 字符 |
| 1003 | 未选择当前用户 |
| 1005 | 当前用户已存在同名标签（is_del=0 范围内查重） |

---

### 接口7：修改标签

| 项目 | 说明 |
|------|------|
| 接口名称 | 修改好友标签 |
| 请求方式 | `PUT` |
| URL | `/api/friend-tags/{tagId}` |
| 用途 | 修改标签名（仅标签创建人） |
| 所属模块 | 一期·好友标签 |
| 类型 | 对象 |

#### 请求参数（JSON 请求体 TagEditIn）

| 参数 | 类型 | 必填 | 示例 | 说明 |
|---|---|---|---|---|
| `_appId` | `string` | 是 | `moments-web` | 调用方标识（query） |
| `tagId`（路径） | `number` | 是 | `21` | 标签 ID |
| `tagName` | `string` | 是 | `篮球队友` | 新标签名，trim 后 1-16 字符 |
| `viewerId` | `number` | 否 | `1` | 当前用户覆盖（query） |

#### 响应示例

```json
{
  "code": 0,
  "msg": "success",
  "data": { "tagId": 21, "tagName": "篮球队友", "friendCount": 6, "createdTimeStr": "2026-09-20 23:30:00" }
}
```

#### 字段说明

| 字段 | 类型 | 说明 |
|---|---|---|
| `data.tagId` | `number` | 标签 ID |
| `data.tagName` | `string` | 修改后标签名 |
| `data.friendCount` | `number` | 该标签当前好友数，单位：个 |
| `data.createdTimeStr` | `string` | 创建时间（yyyy-MM-dd HH:mm:ss） |

#### 异常码

| code | 触发条件 |
|------|---------|
| 1001 | tagName 为空/超长，或 tagId ≤0 |
| 1003 | 未选择当前用户 |
| 1004 | 标签不存在（含已删除） |
| 1005 | 新名与当前用户其他标签重复 |
| 1006 | 当前用户非该标签创建人 |

---

### 接口8：删除标签

| 项目 | 说明 |
|------|------|
| 接口名称 | 删除好友标签 |
| 请求方式 | `DELETE` |
| URL | `/api/friend-tags/{tagId}` |
| 用途 | 软删标签并级联软删其全部好友绑定（C13）；帖子可见性配置不级联（自然失效） |
| 所属模块 | 一期·好友标签 |
| 类型 | 空 |

#### 请求参数

| 参数 | 类型 | 必填 | 示例 | 说明 |
|---|---|---|---|---|
| `_appId` | `string` | 是 | `moments-web` | 调用方标识 |
| `tagId`（路径） | `number` | 是 | `21` | 标签 ID |
| `viewerId` | `number` | 否 | `1` | 当前用户覆盖 |

#### 响应示例

```json
{ "code": 0, "msg": "success", "data": null }
```

#### 字段说明

| 字段 | 类型 | 说明 |
|---|---|---|
| `data` | `null` | 无业务数据 |

#### 异常码

| code | 触发条件 |
|------|---------|
| 1001 | tagId ≤0 |
| 1003 | 未选择当前用户 |
| 1004 | 标签不存在 |
| 1006 | 非标签创建人 |

---

### 接口9：绑定好友标签

| 项目 | 说明 |
|------|------|
| 接口名称 | 给好友添加标签 |
| 请求方式 | `POST` |
| URL | `/api/friend-tags/{tagId}/users/{userId}` |
| 用途 | 将好友归入标签（校验好友关系 + 查重防重复绑定） |
| 所属模块 | 一期·好友标签 |
| 类型 | 空 |

#### 请求参数

| 参数 | 类型 | 必填 | 示例 | 说明 |
|---|---|---|---|---|
| `_appId` | `string` | 是 | `moments-web` | 调用方标识 |
| `tagId`（路径） | `number` | 是 | `12` | 标签 ID（须归属当前用户） |
| `userId`（路径） | `number` | 是 | `3` | 好友用户 ID（须为当前用户的好友） |
| `viewerId` | `number` | 否 | `1` | 当前用户覆盖 |

#### 响应示例

```json
{ "code": 0, "msg": "success", "data": null }
```

#### 字段说明

| 字段 | 类型 | 说明 |
|---|---|---|
| `data` | `null` | 无业务数据 |

#### 异常码

| code | 触发条件 |
|------|---------|
| 1001 | tagId/userId ≤0 |
| 1003 | 未选择当前用户 |
| 1004 | 标签不存在 |
| 1006 | 非标签创建人 |
| 1002 | 目标用户不存在 |
| 1007 | 目标用户不是当前用户的好友 |
| 1008 | 该好友已绑定此标签（is_del=0 范围查重） |

---

### 接口10：解绑好友标签

| 项目 | 说明 |
|------|------|
| 接口名称 | 移除好友标签 |
| 请求方式 | `DELETE` |
| URL | `/api/friend-tags/{tagId}/users/{userId}` |
| 用途 | 将好友移出标签（幂等：绑定不存在也返回成功） |
| 所属模块 | 一期·好友标签 |
| 类型 | 空 |

#### 请求参数

| 参数 | 类型 | 必填 | 示例 | 说明 |
|---|---|---|---|---|
| `_appId` | `string` | 是 | `moments-web` | 调用方标识 |
| `tagId`（路径） | `number` | 是 | `12` | 标签 ID |
| `userId`（路径） | `number` | 是 | `3` | 好友用户 ID |
| `viewerId` | `number` | 否 | `1` | 当前用户覆盖 |

#### 响应示例

```json
{ "code": 0, "msg": "success", "data": null }
```

#### 字段说明

| 字段 | 类型 | 说明 |
|---|---|---|
| `data` | `null` | 无业务数据 |

#### 异常码

| code | 触发条件 |
|------|---------|
| 1001 | tagId/userId ≤0 |
| 1003 | 未选择当前用户 |
| 1004 | 标签不存在 |
| 1006 | 非标签创建人 |

---

### 接口11：生成模拟数据

| 项目 | 说明 |
|------|------|
| 接口名称 | 批量生成模拟数据 |
| 请求方式 | `POST` |
| URL | `/api/mock-data` |
| 用途 | 按配置批量初始化用户/好友关系/标签（/可选帖子），符合真实社交分布（§2.5） |
| 所属模块 | 一期·模拟数据 |
| 类型 | 对象 |

#### 请求参数（JSON 请求体 MockDataGenerateIn）

| 参数 | 类型 | 必填 | 示例 | 说明 |
|---|---|---|---|---|
| `_appId` | `string` | 是 | `moments-web` | 调用方标识（query） |
| `userCount` | `number` | 否 | `100` | 生成用户数，默认 `100`，范围 1-100000 |
| `avgFriendsPerUser` | `number` | 否 | `10` | 人均好友数，默认 `10`，范围 0-500（好友关系对数≈userCount×该值/2，表记录数=对数×2） |
| `extraTagsPerUser` | `number` | 否 | `0` | 每用户额外自定义标签数（默认 8 标签之外），默认 `0`，范围 0-20 |
| `postCount` | `number` | 否 | `0` | 生成帖子数（A8 最小扩展，页面级测试用），默认 `0` 不生成，范围 0-10000 |
| `clear` | `boolean` | 否 | `false` | `true`=先软删清空 8 张表业务数据再生成（页面须二次确认）；默认 `false` 追加 |

#### 响应示例

```json
{
  "code": 0,
  "msg": "success",
  "data": {
    "userCount": 100, "friendshipPairs": 520, "friendshipRecords": 1040,
    "tagCount": 800, "bindingCount": 743, "postCount": 0,
    "imagePoolSize": 40, "elapsedMs": 3520
  }
}
```

#### 字段说明

| 字段 | 类型 | 说明 |
|---|---|---|
| `data.userCount` | `number` | 实际生成用户数，单位：个 |
| `data.friendshipPairs` | `number` | 好友关系对数（A-B 计 1 对），单位：对 |
| `data.friendshipRecords` | `number` | friendship 表写入记录数（=对数×2），单位：条 |
| `data.tagCount` | `number` | 生成标签总数（含默认 8×用户数），单位：个 |
| `data.bindingCount` | `number` | 标签绑定关系总数，单位：条 |
| `data.postCount` | `number` | 生成帖子数，单位：条 |
| `data.imagePoolSize` | `number` | 占位图池文件数（头像 20 + 内容图 20），单位：个 |
| `data.elapsedMs` | `number` | 生成耗时，单位：毫秒 |

#### 异常码

| code | 触发条件 |
|------|---------|
| 1040 | 配置越界（userCount/avgFriendsPerUser/extraTagsPerUser/postCount 超范围） |

---

### 接口12：发布朋友圈

| 项目 | 说明 |
|------|------|
| 接口名称 | 发布朋友圈 |
| 请求方式 | `POST` |
| URL | `/api/posts` |
| 用途 | 发布纯文字/纯图/图文朋友圈（≤9 图，4 种可见范围，4 表同事务） |
| 所属模块 | 二期·朋友圈发布 |
| 类型 | 对象 |

#### 请求参数（JSON 请求体 PostCreateIn）

| 参数 | 类型 | 必填 | 示例 | 说明 |
|---|---|---|---|---|
| `_appId` | `string` | 是 | `moments-web` | 调用方标识（query） |
| `content` | `string` | 否 | `"今天天气不错"` | 文字内容，trim 后 ≤2000 字符 |
| `imageUrls` | `string[]` | 否 | `["/images/c1.png"]` | 已上传图片 URL，顺序即展示排序（sort=1..n），≤9 张；**每项须匹配 `^/images/[A-Za-z0-9._-]+$`（前缀 `/images/` + 文件名白名单，与 design §3.2.12/§7.3 一致），非法项返回 1001** |
| `visibilityType` | `枚举` | 是 | `3` | 可见范围。取值：`1`=公开、`2`=私密、`3`=部分可见、`4`=不给谁看 |
| `visibilityTagIds` | `number[]` | 否 | `[12]` | 指定标签 ID（type=3/4 生效）；**type=1/2 时静默忽略：不校验、不落库，传非空列表也不报错（建议 10 定稿）** |
| `visibilityUserIds` | `number[]` | 否 | `[2]` | 指定好友用户 ID（type=3/4 生效）；type=1/2 时同上静默忽略 |
| `viewerId` | `number` | 否 | `1` | 当前用户覆盖（query，作者=当前用户） |

联合校验：`content` 与 `imageUrls` 不得同时为空（纯空帖拒绝）。

#### 响应示例

```json
{
  "code": 0,
  "msg": "success",
  "data": {
    "postId": 101, "userId": 1, "nickname": "张三", "avatar": "/images/ph_avatar_01.png",
    "content": "今天天气不错", "imageUrls": ["/images/c1.png"],
    "visibilityType": 3, "visibilityTypeStr": "部分可见",
    "status": 1, "createTimeStr": "2026-09-20 23:31:00"
  }
}
```

#### 字段说明

| 字段 | 类型 | 说明 |
|---|---|---|
| `data.postId` | `number` | 新帖 ID |
| `data.userId` | `number` | 作者用户 ID |
| `data.nickname` | `string` | 作者昵称 |
| `data.avatar` | `string` | 作者头像 URL |
| `data.content` | `string` | 文字内容 |
| `data.imageUrls` | `string[]` | 图片 URL 数组（按 sort 升序，顺序与请求 imageUrls 一致） |
| `data.visibilityType` | `枚举` | 可见范围。取值：`1`=公开、`2`=私密、`3`=部分可见、`4`=不给谁看 |
| `data.visibilityTypeStr` | `string` | 可见范围中文 |
| `data.status` | `枚举` | 业务状态。取值：`1`=正常、`0`=删除 |
| `data.createTimeStr` | `string` | 发布时间（yyyy-MM-dd HH:mm:ss） |

#### 异常码

| code | 触发条件 |
|------|---------|
| 1001 | visibilityType 缺失/非 1-4；content 与 imageUrls 同空；content 超 2000 字；imageUrls 任一项不匹配 `^/images/[A-Za-z0-9._-]+$` |
| 1022 | imageUrls 超过 9 张 |
| 1003 | 未选择当前用户 |
| 1004 / 1006 | visibilityTagIds 含不存在或不归属当前用户的标签 |
| 1002 | visibilityUserIds 含不存在用户 |

---

### 接口13：帖子详情

| 项目 | 说明 |
|------|------|
| 接口名称 | 朋友圈帖子详情 |
| 请求方式 | `GET` |
| URL | `/api/posts/{postId}` |
| 用途 | 查看单条帖子（经 canView 统一权限校验，PRD §3.8） |
| 所属模块 | 二期·朋友圈 |
| 类型 | 对象 |

#### 请求参数

| 参数 | 类型 | 必填 | 示例 | 说明 |
|---|---|---|---|---|
| `_appId` | `string` | 是 | `moments-web` | 调用方标识 |
| `postId`（路径） | `number` | 是 | `101` | 帖子 ID |
| `viewerId` | `number` | 否 | `3` | 当前用户覆盖（隐私验证视角） |

#### 响应示例

```json
{
  "code": 0,
  "msg": "success",
  "data": {
    "postId": 101, "userId": 1, "nickname": "张三", "avatar": "/images/ph_avatar_01.png",
    "content": "今天天气不错", "imageUrls": ["/images/c1.png"],
    "visibilityType": 3, "visibilityTypeStr": "部分可见",
    "status": 1, "createTimeStr": "2026-09-20 23:31:00"
  }
}
```

#### 字段说明

| 字段 | 类型 | 说明 |
|---|---|---|
| `data.postId` | `number` | 帖子 ID |
| `data.userId` | `number` | 作者用户 ID |
| `data.nickname` | `string` | 作者昵称 |
| `data.avatar` | `string` | 作者头像 URL |
| `data.content` | `string` | 文字内容 |
| `data.imageUrls` | `string[]` | 图片 URL 数组（按 sort 升序） |
| `data.visibilityType` | `枚举` | 可见范围。取值：`1`=公开、`2`=私密、`3`=部分可见、`4`=不给谁看 |
| `data.visibilityTypeStr` | `string` | 可见范围中文 |
| `data.status` | `枚举` | 业务状态。取值：`1`=正常、`0`=删除 |
| `data.createTimeStr` | `string` | 发布时间（yyyy-MM-dd HH:mm:ss） |

#### 异常码

| code | 触发条件 |
|------|---------|
| 1001 | postId ≤0 |
| 1003 | 未选择当前用户 |
| 1010 | 帖子不存在或已删除 |
| 1011 | 当前用户无权查看（canView=false，如私密帖非作者、部分可见未命中） |

---

### 接口14：删除帖子

| 项目 | 说明 |
|------|------|
| 接口名称 | 删除朋友圈（软删） |
| 请求方式 | `DELETE` |
| URL | `/api/posts/{postId}` |
| 用途 | 作者软删自己的帖子（is_del=1，status 保留；图片文件保留） |
| 所属模块 | 二期·朋友圈 |
| 类型 | 空 |

#### 请求参数

| 参数 | 类型 | 必填 | 示例 | 说明 |
|---|---|---|---|---|
| `_appId` | `string` | 是 | `moments-web` | 调用方标识 |
| `postId`（路径） | `number` | 是 | `101` | 帖子 ID |
| `viewerId` | `number` | 否 | `1` | 当前用户覆盖 |

#### 响应示例

```json
{ "code": 0, "msg": "success", "data": null }
```

#### 字段说明

| 字段 | 类型 | 说明 |
|---|---|---|
| `data` | `null` | 无业务数据 |

#### 异常码

| code | 触发条件 |
|------|---------|
| 1001 | postId ≤0 |
| 1003 | 未选择当前用户 |
| 1010 | 帖子不存在或已删除 |
| 1012 | 当前用户非作者（越权删除） |

---

### 接口15：指定用户帖子列表

| 项目 | 说明 |
|------|------|
| 接口名称 | 指定用户的朋友圈列表（C14） |
| 请求方式 | `GET` |
| URL | `/api/posts` |
| 用途 | 查看目标用户的帖子（当前用户视角 canView 过滤，好友详情页"他的朋友圈"） |
| 所属模块 | 二期·朋友圈 |
| 类型 | 表格（limit 截断，无页码） |

#### 请求参数

| 参数 | 类型 | 必填 | 示例 | 说明 |
|---|---|---|---|---|
| `_appId` | `string` | 是 | `moments-web` | 调用方标识 |
| `userId` | `number` | 是 | `1` | 目标作者用户 ID（与 viewerId 语义区分：本参数是被查看者） |
| `limit` | `number` | 否 | `100` | **结果条数上限**（非扫描上限：服务端按 limit×3 最多 3 批放大扫描并 canView 过滤，保证截断点之后的旧公开帖不漏，建议 5 定稿），缺省默认 `100`，范围 1-500，显式越界返回 1001 |
| `viewerId` | `number` | 否 | `3` | 当前用户覆盖 |

#### 响应示例

```json
{
  "code": 0,
  "msg": "success",
  "data": [
    {
      "postId": 102, "userId": 1, "nickname": "张三", "avatar": "/images/ph_avatar_01.png",
      "content": "周末爬山", "imageUrls": ["/images/c5.png", "/images/c6.png"],
      "visibilityType": 1, "visibilityTypeStr": "公开",
      "status": 1, "createTimeStr": "2026-09-20 22:00:00"
    }
  ]
}
```

#### 字段说明

| 字段 | 类型 | 说明 |
|---|---|---|
| `data` | `array` | 帖子数组（createTime DESC；无可 见帖子为 `[]`） |
| `data[].postId` | `number` | 帖子 ID |
| `data[].userId` | `number` | 作者用户 ID（恒等于入参 userId） |
| `data[].nickname` | `string` | 作者昵称 |
| `data[].avatar` | `string` | 作者头像 URL |
| `data[].content` | `string` | 文字内容 |
| `data[].imageUrls` | `string[]` | 图片 URL 数组（按 sort 升序） |
| `data[].visibilityType` | `枚举` | 可见范围。取值：`1`=公开、`2`=私密、`3`=部分可见、`4`=不给谁看 |
| `data[].visibilityTypeStr` | `string` | 可见范围中文 |
| `data[].status` | `枚举` | 业务状态。取值：`1`=正常、`0`=删除 |
| `data[].createTimeStr` | `string` | 发布时间（yyyy-MM-dd HH:mm:ss） |

#### 异常码

| code | 触发条件 |
|------|---------|
| 1001 | userId 缺失/≤0，或 limit 越界 |
| 1003 | 未选择当前用户 |
| 1002 | 目标用户不存在 |

---

### 接口16：Feed 瀑布流

| 项目 | 说明 |
|------|------|
| 接口名称 | 朋友圈 Feed（Cursor 分页） |
| 请求方式 | `GET` |
| URL | `/api/feed` |
| 用途 | 当前用户的时间线：自己 + 好友的帖子，权限过滤，createTime DESC + id DESC，Cursor 滚动加载（PRD §3.9-§3.11） |
| 所属模块 | 二期·Feed |
| 类型 | 表格（Cursor 分页） |

#### 请求参数

| 参数 | 类型 | 必填 | 示例 | 说明 |
|---|---|---|---|---|
| `_appId` | `string` | 是 | `moments-web` | 调用方标识 |
| `cursor` | `string` | 否 | `MTc4OTkxODIwMzAwMDoxMDI=` | 分页游标；**首页不传**；后续传上页响应 `nextCursor`。Base64 解码失败（如 `!!!`）、解码后不匹配 `^\d{13}:\d{1,18}$`（id 段限 18 位）、或毫秒/id 段超出 Long 范围，均返回 1030（不静默重置首页） |
| `pageSize` | `number` | 否 | `20` | 每页条数，默认 `20`，范围 1-50 |
| `viewerId` | `number` | 否 | `1` | 当前用户覆盖（隐私验证切换视角） |

#### 响应示例

```json
{
  "code": 0,
  "msg": "success",
  "data": {
    "items": [
      {
        "postId": 102, "userId": 1, "nickname": "张三", "avatar": "/images/ph_avatar_01.png",
        "content": "周末爬山", "imageUrls": ["/images/c5.png", "/images/c6.png"],
        "createTimeStr": "2026-09-20 22:00:00"
      },
      {
        "postId": 101, "userId": 2, "nickname": "李四", "avatar": "/images/ph_avatar_02.png",
        "content": "新办公室入驻", "imageUrls": [],
        "createTimeStr": "2026-09-20 21:00:00"
      }
    ],
    "nextCursor": "MTc4OTkxODIwMzAwMDoxMDE=",
    "hasMore": true
  }
}
```

#### 字段说明

| 字段 | 类型 | 说明 |
|---|---|---|
| `data.items` | `array` | 当前页帖子数组（无帖子为 `[]`） |
| `data.items[].postId` | `number` | 帖子 ID |
| `data.items[].userId` | `number` | 作者用户 ID（自己或好友） |
| `data.items[].nickname` | `string` | 作者昵称 |
| `data.items[].avatar` | `string` | 作者头像 URL |
| `data.items[].content` | `string` | 文字内容 |
| `data.items[].imageUrls` | `string[]` | 图片 URL 数组（按 sort 升序，最多 9 张） |
| `data.items[].createTimeStr` | `string` | 发布时间（yyyy-MM-dd HH:mm:ss） |
| `data.nextCursor` | `string` | 下一页游标（**不透明值**：客户端原样回传即可，不承诺内部语义）；`hasMore=true` 时**必非 null**——items 为空时指向已扫描候选流末条（翻页链延续、已扫过的不可见帖不重扫），非空截断时为 items 末条；`hasMore=false` 时为 `null` |
| `data.hasMore` | `boolean` | 是否还有更多（false 时停止滚动加载）。**`items=[] 且 hasMore=true` 为合法组合**：本页扫描范围（≤3×pageSize×3 条候选）内无可见帖但候选流未扫尽，请继续携带 nextCursor 请求下一页 |

排序与游标语义：`created_stime DESC, id DESC` 双字段（同秒按 id 倒序，C8）；空 Feed（无帖/无好友/候选流扫尽）返回 `items=[], nextCursor=null, hasMore=false`。**前端停止规则（B1）**：连续 3 次收到 `items=[]`（即使 hasMore=true）即停止加载并提示"暂无更多内容"，防极端全不可见候选流下的持续空页请求。

#### 异常码

| code | 触发条件 |
|------|---------|
| 1001 | pageSize 越界（<1 或 >50） |
| 1003 | 未选择当前用户 |
| 1030 | cursor 非法（Base64 解码失败、解码后不匹配 `^\d{13}:\d{1,18}$`、或数值段超 Long 范围；不静默重置首页） |

---

### 接口17：图片上传

| 项目 | 说明 |
|------|------|
| 接口名称 | 本地图片上传 |
| 请求方式 | `POST` |
| URL | `/api/images/upload` |
| 用途 | 上传图片到服务器本地文件夹（可配置目录），返回 `/images/{fileName}` 访问 URL（发帖前置步骤） |
| 所属模块 | 二期·图片 |
| 类型 | 对象 |

#### 请求参数（multipart/form-data）

| 参数 | 类型 | 必填 | 示例 | 说明 |
|---|---|---|---|---|
| `_appId` | `string` | 是 | `moments-web` | 调用方标识（query） |
| `file` | `file` | 是 | `photo.jpg` | 图片文件；扩展名限 jpg/jpeg/png/gif/webp；大小 ≤5MB |

#### 响应示例

```json
{
  "code": 0,
  "msg": "success",
  "data": { "imageUrl": "/images/8f3a1b2c4d5e6f708192a3b4c5d6e7f8.png" }
}
```

#### 字段说明

| 字段 | 类型 | 说明 |
|---|---|---|
| `data.imageUrl` | `string` | 访问 URL（文件名=服务端生成 UUID+扩展名，防覆盖；经 `/images/**` 静态映射访问） |

#### 异常码

| code | 触发条件 |
|------|---------|
| 1020 | 文件缺失、无扩展名或扩展名不在白名单 |
| 1021 | 文件大小超过 5MB |

---

## 4. 自检清单（api-spec-template §7 对齐）

- [x] 所有接口四段式齐全（项目说明表/请求参数表/响应示例/字段说明表）
- [x] 响应示例字段与字段说明一一对应；数组全部展开 `xxx[].yyy`；`string[]` 基础数组单行标注对应关系
- [x] 分页接口：接口1 含 pageNo/pageSize + total；接口16 含 Cursor 三件套；接口15 为 limit 截断特例（已声明）
- [x] 列表数组命名统一（`users` / `items` / data 直返数组三种，均在字段说明声明）
- [x] 可枚举字段（gender/visibilityType/status）全部列出取值及含义
- [x] 带单位字段标注单位（个/对/条/毫秒）
- [x] 响应信封全文统一 ApiResult{code,msg,data}
- [x] 与 design.md §3 一致：17 接口一一对应；错误码/字段类型/必填/校验规则一致；§1.6 已合并去重
