# 技术详细设计文档

> 示例占位说明：本模板中的 `{占位符}` 处使用前需替换为本项目业务术语。
> 模板版本：v1.0
> 适用于：{平台/系统名称}各模块
> **全新项目模式**：仓库无已有代码时，标注 `[新增]`/`[复用]` 处全为 `[新增]`；§2.2 / §4.2 / §8 等含"修改/复用"语义的章节见对应处的全新项目提示。

## 文档信息

| 字段 | 内容 |
|------|------|
| 文档编号 | `TDD-{模块缩写}-{序号}` |
| 模块名称 | 例：{repo-abbrev}-{module} |
| 功能名称 | 例：{功能点描述} |
| 关联 PRD | 链接或编号 |
| 作者 | |
| 审阅人 | |
| 创建日期 | YYYY-MM-DD |
| 最后更新 | YYYY-MM-DD |
| 状态 | 草稿 / 评审中 / 已确认 |

---

## 1. 概述

### 1.1 背景

> 简述需求背景，为什么要做这个功能，解决什么问题。

### 1.2 目标

> 功能目标列表，与 PRD 的 FR 一一对应。

### 1.3 范围

| 范围 | 说明 |
|------|------|
| 包含 | 列出本次设计覆盖的功能点 |
| 不包含 | 明确排除的功能点，避免歧义 |

### 1.4 术语与缩写

| 术语 | 说明 |
|------|------|
| FR | Functional Requirement |
| NFR | Non-Functional Requirement |
| | |

---

## 2. 架构设计

### 2.1 系统上下文

> 本功能涉及哪些服务/模块，用简图描述调用关系。

```
┌──────────┐    HTTP     ┌──────────┐    MQ     ┌──────────┐
│  Client  │ ──────────► │  {API}   │ ────────► │ {Worker} │
└──────────┘             └──────────┘           └──────────┘
```

---

## 3. 接口设计

> **派生说明**：本章节填完后，tech-designer-simple 会自动派生 `api.md`（前端交付用）。本节为真源，api.md 禁止独立编辑。

### 3.1 接口总览

| # | Method | URL | 说明 | 权限 |
|---|--------|-----|------|------|
| 1 | POST | `/api/v1/xxx` | 创建XX | 需登录 |
| 2 | GET | `/api/v1/xxx/{id}` | 查询XX详情 | 需登录 |
| 3 | GET | `/api/v1/xxx/list` | 查询XX列表 | 需登录 |
| 4 | PUT | `/api/v1/xxx/{id}` | 更新XX | 需登录 |
| 5 | DELETE | `/api/v1/xxx/{id}` | 删除XX | 需登录+管理员 |

### 3.2 接口详细设计

#### 3.2.1 创建XX — `POST /api/v1/xxx`

**请求参数：**

```json
{
  "requestId": "string, 必填, 幂等键",
  "fieldA": "string, 必填, 说明",
  "fieldB": 0,
  "fieldC": {
    "nestedField": "string, 选填"
  }
}
```

| 字段 | 类型 | 必填 | 校验规则 | 说明 |
|------|------|------|----------|------|
| requestId | String | 是 | UUID格式 | 幂等键 |
| fieldA | String | 是 | 1~50字符 | 名称 |
| fieldB | Integer | 是 | ≥0 | 数量 |
| fieldC | Object | 否 | — | 扩展信息 |

**响应参数：**

```json
{
  "code": 0,
  "msg": "success",
  "data": {
    "id": 123
  }
}
```

**异常码：**

| 异常码 | 说明 | 触发条件 |
|--------|------|----------|
| 400001 | 参数校验失败 | fieldA 为空 |
| 409001 | 重复创建 | 同名XX已存在 |

---

## 4. 数据模型设计

### 4.1 新增表

#### `t_xxx` — XX表

| 字段 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| id | bigint(20) | AUTO_INCREMENT | 主键 |
| field_a | varchar(100) | '' | 字段A |
| field_b | int | 0 | 字段B |
| status | tinyint | 0 | 状态：0-草稿 1-生效 2-失效 |
| created_stime | datetime | CURRENT_TIMESTAMP | 创建时间 |
| modified_stime | datetime | CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP | 修改时间 |
| is_del | tinyint(1) | 0 | 是否删除 0-正常 1-删除 |

**索引：**

| 索引名 | 类型 | 字段 | 说明 |
|--------|------|------|------|
| pk_id | PRIMARY | id | 主键索引 |
| idx_field_a | NORMAL | field_a | 按名称查询 |
| uniq_xxx | UNIQUE | field_a, field_b | 唯一约束 |

### 4.2 修改表

> 全新项目模式：仓库无已有表时，本节直接标注「无修改表（全新建设）」并删除下方表格。

| 表名 | 变更类型 | 字段 | 说明 |
|------|----------|------|------|
| t_existing | ADD COLUMN | new_field varchar(50) DEFAULT '' | 新增字段 |

> ⚠️ 已有表新增列禁止使用 NOT NULL（会锁表），如需 NOT NULL DEFAULT() 联系 DBA。

### 4.3 ER 关系

```
t_xxx 1 ──── N t_yyy
t_yyy 1 ── N t_zzz
```

---

## 5. 核心流程设计

### 5.1 主流程

> 用时序图或伪代码描述核心业务流程。

```
Client           Controller         Service            Dao            MQ
  │                  │                  │                │              │
  │  POST /xxx       │                  │                │              │
  │ ────────────────►│                  │                │              │
  │                  │  参数校验         │                │              │
  │                  │  doAction()      │                │              │
  │                  │ ────────────────►│                │              │
  │                  │                  │  校验前置条件   │              │
  │                  │                  │ ──────────────►│              │
  │                  │                  │  执行业务操作   │              │
  │                  │                  │ ──────────────►│              │
  │                  │                  │  发送消息(事务外)│              │
  │                  │                  │ ────────────────────────────►│
  │                  │  return result   │                │              │
  │◄─────────────────│                  │                │              │
```

### 5.2 状态机

> 如涉及状态流转，用简图描述。

```
                    ┌──────────┐
                    │  草稿     │
                    │  (DRAFT)  │
                    └────┬─────┘
                         │ 提交
                    ┌────▼─────┐
            ┌───────│  生效     │───────┐
            │       │ (ACTIVE) │       │
            │ 下线   └──────────┘ 到期   │
       ┌────▼─────┐         ┌────▼─────┐
       │  已下线   │         │  已结束   │
       │(OFFLINE) │         │ (EXPIRED)│
       └──────────┘         └──────────┘
```

### 5.3 关键业务规则

| # | 规则 | 说明 |
|---|------|------|
| 1 | 幂等性 | 基于 requestId + Redisson 分布式锁 |
| 2 | 级联操作 | 例：{主实体下线} → 关联 {子实体} 全部下线 |
| 3 | 数据隔离 | 原有列表页需按新维度过滤 |
| 4 | | |

---

## 6. 分层实现设计

### 6.1 Controller 层

> 仅参数校验与编排，不含业务逻辑。入参 > 3 个必须用 DTO。

```java
// 示例签名
@PostMapping("/api/v1/xxx")
public Result<Long> createXxx(@RequestBody @Valid XxxCreateDTO dto) {
    log.info("createXxx param={}", JSON.toJSONString(dto));
    return Result.success(xxxService.createXxx(dto));
}
```

### 6.2 Service 层

| 方法 | 职责 | 事务 | 说明 |
|------|------|------|------|
| createXxx | 创建XX | @Transactional | 校验→插入 |
| updateXxx | 更新XX | @Transactional | 新建实体只设 id + 待更新字段 |
| deleteXxx | 删除XX | @Transactional | 逻辑删除 |

> ⚠️ 事务内禁止：RPC 调用、MQ 发送、Redis 操作。消息发送放事务外。

### 6.3 DAO / Mapper 层

> 复杂查询使用 Mapper 自定义 SQL，禁止 Service 层写 QueryWrapper。

| Mapper 方法 | SQL 类型 | Mapper XML | 说明 |
|-------------|----------|------------|------|
| insertSelective | INSERT | 是/否 | 选择性插入 |
| updateByIdSelective | UPDATE | 是/否 | 只更新非空字段 |
| selectByCondition | SELECT | 是 | 自定义条件查询 |

### 6.4 DTO / VO 设计

| DTO/VO | 用途 | 字段来源 |
|--------|------|----------|
| XxxCreateDTO | 创建请求 | 前端入参 |
| XxxUpdateDTO | 更新请求 | 前端入参 |
| XxxVO | 列表/详情响应 | 单表 / 多表组装 |
| XxxQueryDTO | 列表查询 | 前端入参 |

---

## 7. 非功能设计

### 7.1 性能

| 指标 | 目标 | 方案 |
|------|------|------|
| 列表查询 RT | < 200ms | 索引优化 + 分页 |
| 批量操作 | 1000条 < 3s | 分批提交，每批 200 条 |

### 7.2 并发与幂等

| 场景 | 方案 |
|------|------|
| 重复创建 | requestId + Redisson 分布式锁 |
| 并发更新 | 乐观锁（version 字段）或分布式锁 |

### 7.3 安全

| 场景 | 方案 |
|------|------|
| 越权访问 | 接口层校验 userId 归属 |
| 敏感数据 | 手机号脱敏 `137****0969` |
| SQL 注入 | MyBatis 参数绑定，禁止字符串拼接 |

---

## 8. 中间件依赖

> 全新项目模式：基础设施首次搭建，全部为「新增」；"新增/复用"列仍保留以备后续 phase 引用。

| 中间件 | 用途 | 新增/复用 | 说明 |
|--------|------|-----------|------|
| MySQL | 业务数据存储 | 新增表 | 指明数据源 |
| Redis | 缓存 + 分布式锁 | 复用 | key 命名规则 |
| Kafka | 消息推送 | 新增 Topic？ | Topic 名称 |
| XXL-JOB | 定时调度 | 新增任务？ | 任务名称 + cron |

---

## 9. 异常与降级

| 异常场景 | 处理方式 |
|----------|----------|
| DB 连接超时 | 返回友好提示 + 告警 |
| MQ 发送失败 | 本地落表 + 补偿任务重发 |
| 下游服务不可用 | 重试 3 次 + 降级返回 |
| 数据不一致 | 对账任务 + 告警 |

---

## 10. 日志与监控

| 维度 | 要求 |
|------|------|
| 接口入参 | Controller 第一行打印，参数 > 3 个用 DTO |
| 外部调用 | 记录 URL + 请求参数 + Header + 响应 |
| 关键节点 | 业务关键步骤 info 日志 |
| 异常日志 | `logger.error("xxx error, param={}", JSON.toJSONString(param), e)` |
| 监控告警 | 配置接口 RT、错误率告警阈值 |

---
