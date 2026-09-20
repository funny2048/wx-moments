# 任务清单模板

> 基于技术详细设计 `tech-design-simple.md` 拆分
> 原则：L0/L1 水平铺底，L2+ 按功能纵向贯穿

## 文档信息

| 字段 | 内容 |
|------|------|
| 关联 phase-dir | |
| 关联 tech-design | `openspec/changes/{变更ID}/tech-design-simple.md` |
| 生成时间 | YYYY-MM-DD |

---

## 任务拆分规则

### 拆分粒度

- **一个 task = 1~4 小时可完成的最小可验证单元**
- 完成后可编译验证
- 禁止"写完整个模块"这种大 task，按文件/方法级别拆分

### 执行策略

```
阶段一：基础铺底（水平，全部做完）
  L0: DDL + 枚举 + 常量 + 错误码
  L1: Entity + DTO + VO

阶段二：按功能纵向贯穿（L2→L3→L4 一条线）
  功能A: Mapper(SQL) → Service(逻辑) → Controller(接口)  ← 可编译验证
  功能B: Mapper(SQL) → Service(逻辑) → Controller(接口)  ← 可编译验证
  功能C: ...
```

**好处**：每完成一个功能就有一条从 DB 到接口的完整通路，可立即编译验证。

### 依赖标记

- 每个 task 标注 `Depends`（前置 task 编号）
- L0/L1 内无依赖的 task 可并行
- 功能纵向切片内 L2→L3→L4 严格顺序
- 不同功能之间无依赖的可并行

---

## 任务总览

### 阶段一：基础铺底

| # | Layer | 标题 | Depends | 预估 | 状态 |
|---|-------|------|---------|------|------|
| T001 | L0 | 创建 t_xxx 表 DDL | — | 0.5h | ⬜ |
| T002 | L0 | 新增状态枚举 + 错误码常量 | — | 0.5h | ⬜ |
| T003 | L1 | Entity: XxxEntity | T001 | 0.5h | ⬜ |
| T004 | L1 | DTO: XxxCreateDTO / XxxUpdateDTO / XxxQueryDTO | T001 | 0.5h | ⬜ |
| T005 | L1 | VO: XxxVO / XxxListVO | T001 | 0.5h | ⬜ |

### 阶段二：功能纵向

| # | 功能 | Layer | 标题 | Depends | 预估 | 状态 |
|---|------|-------|------|---------|------|------|
| T010 | 创建XX | L2 | Mapper: 创建相关 SQL | T003, T004 | 1h | ⬜ |
| T011 | 创建XX | L3 | Service: createXxx | T010 | 1.5h | ⬜ |
| T012 | 创建XX | L4 | Controller: POST /api/v1/xxx | T011 | 0.5h | ⬜ |
| T020 | 查询XX | L2 | Mapper: 查询相关 SQL | T003, T005 | 1h | ⬜ |
| T021 | 查询XX | L3 | Service: queryXxx / listXxx | T020 | 1h | ⬜ |
| T022 | 查询XX | L4 | Controller: GET /api/v1/xxx/list | T021 | 0.5h | ⬜ |
| T030 | 更新XX | L2 | Mapper: 更新相关 SQL | T003, T004 | 0.5h | ⬜ |
| T031 | 更新XX | L3 | Service: updateXxx | T030 | 1h | ⬜ |
| T032 | 更新XX | L4 | Controller: PUT /api/v1/xxx/{id} | T031 | 0.5h | ⬜ |

### 并行组

| 组 | task 列表 | 说明 |
|----|-----------|------|
| A | T001, T002 | L0 无依赖，可并行 |
| B | T003, T004, T005 | L1 依赖 A 组，内部可并行 |
| C | T010→T011→T012 | 创建功能纵向 |
| D | T020→T021→T022 | 查询功能纵向（可与 C 并行） |
| E | T030→T031→T032 | 更新功能纵向（可与 C/D 并行） |

---

## 任务详细

> 每个 task 必须包含以下 6 个字段：Depends、预估、描述、涉及文件、伪代码、验收标准。

### T001 · L0 · 创建 t_xxx 表 DDL

**Depends**: —
**预估**: 0.5h

**描述**:
根据 tech-design.md §4.1，编写建表 SQL。

**涉及文件**:
- `sql/t_xxx.sql`（新建）

**伪代码**:
```sql
CREATE TABLE t_xxx (
  id             BIGINT(20)   NOT NULL AUTO_INCREMENT COMMENT '主键',
  field_a        VARCHAR(100) NOT NULL DEFAULT ''      COMMENT '字段A',
  status         TINYINT      NOT NULL DEFAULT 0       COMMENT '状态：0-草稿 1-生效 2-失效',
  created_stime  DATETIME              DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  modified_stime DATETIME              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  is_del         TINYINT(1)   NOT NULL DEFAULT 0       COMMENT '是否删除 0-正常 1-删除',
  PRIMARY KEY (pk_id),
  INDEX idx_field_a (field_a),
  UNIQUE INDEX uniq_xxx (field_a, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='XX表';
```

**验收标准**:
- [ ] 表包含 4 通用字段（id, created_stime, modified_stime, is_del）
- [ ] 字段命名 snake_case
- [ ] 索引命名 pk_ / idx_ / uniq_ 规范
- [ ] 通过 mysql-guide / sqlserver-guide 规范检查
- [ ] `mvn test-compile -pl {模块} -am` 通过

---

### T002 · L0 · 新增状态枚举 + 错误码常量

**Depends**: —
**预估**: 0.5h

**描述**:
根据 tech-design.md §5.2 状态机 + §3.2 异常码，新增枚举类和常量类。

**涉及文件**:
- `src/.../enums/XxxStatusEnum.java`（新建）
- `src/.../constants/XxxErrorCode.java`（新建）

**伪代码**:
```java
// XxxStatusEnum.java
public enum XxxStatusEnum {
    DRAFT(0, "草稿"),
    ACTIVE(1, "生效"),
    OFFLINE(2, "已下线"),
    EXPIRED(3, "已结束");
    private final int code;
    private final String desc;
    // getter...
}

// XxxErrorCode.java
public class XxxErrorCode {
    public static final int PARAM_INVALID = 400001;
    public static final int DUPLICATE_CREATE = 409001;
}
```

**验收标准**:
- [ ] 枚举覆盖状态机所有状态
- [ ] 错误码非常量拼接，无 magic number
- [ ] `mvn test-compile -pl {模块} -am` 通过

---

### T003 · L1 · Entity: XxxEntity

**Depends**: T001
**预估**: 0.5h

**描述**:
根据 tech-design.md §4.1 表结构，创建 MyBatis-Plus 实体类。

**涉及文件**:
- `src/.../entity/XxxEntity.java`（新建）

**伪代码**:
```java
@Data
@TableName("t_xxx")
public class XxxEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String fieldA;
    private Integer status;
    @TableField(fill = FieldFill.INSERT)
    private Date createdStime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date modifiedStime;
    private Integer isDel;
}
```

**验收标准**:
- [ ] 字段与表结构一一对应
- [ ] `@TableName` / `@TableId` / `@TableField` 注解正确
- [ ] `mvn test-compile -pl {模块} -am` 通过

---

### T010 · [创建XX] · L2 · Mapper: 创建相关 SQL

**功能**: 创建XX
**Depends**: T003, T004
**预估**: 1h

**描述**:
根据 tech-design.md §6.3，创建「创建」功能所需的 Mapper 接口方法和 XML SQL。

**涉及文件**:
- `src/.../mapper/XxxMapper.java`（新建 — 添加创建相关方法）
- `src/.../mapper/xml/XxxMapper.xml`（新建 — 添加 insert SQL）

**伪代码**:
```java
// XxxMapper.java — 创建相关方法
public interface XxxMapper extends BaseMapper<XxxEntity> {
    // MyBatis-Plus BaseMapper 已提供 insertSelective，无需额外方法
    // 如需唯一性校验查询：
    XxxEntity selectByFieldA(@Param("fieldA") String fieldA);
}
```

**验收标准**:
- [ ] 继承 BaseMapper<XxxEntity>
- [ ] 自定义查询使用 Mapper XML
- [ ] `mvn test-compile -pl {模块} -am` 通过

---

### T011 · [创建XX] · L3 · Service: createXxx

**功能**: 创建XX
**Depends**: T010
**预估**: 1.5h

**描述**:
根据 tech-design.md §5.1 创建流程，实现创建业务逻辑。

**涉及文件**:
- `src/.../service/XxxService.java`（新建/修改）
- `src/.../service/impl/XxxServiceImpl.java`（新建/修改）

**伪代码**:
```java
@Transactional(rollbackFor = Exception.class)
public Long createXxx(XxxCreateDTO dto) {
    // 1. 幂等校验
    String lockKey = "create:xxx:" + dto.getRequestId();
    if (!redissonLock.tryLock(lockKey, 5, SECONDS)) {
        throw new BizException(DUPLICATE_REQUEST);
    }
    try {
        // 2. 业务校验
        XxxEntity exists = xxxMapper.selectByFieldA(dto.getFieldA());
        if (exists != null) { throw new BizException(DUPLICATE_CREATE); }

        // 3. DTO → Entity
        XxxEntity entity = new XxxEntity();
        entity.setFieldA(dto.getFieldA());
        entity.setStatus(XxxStatusEnum.DRAFT.getCode());

        // 4. 插入
        xxxMapper.insertSelective(entity);
        return entity.getId();
    } finally {
        redissonLock.unlock(lockKey);
    }
}
```

**验收标准**:
- [ ] 幂等：requestId + Redisson 分布式锁
- [ ] 事务注解正确，事务内无 RPC/MQ/Redis
- [ ] `mvn test-compile -pl {模块} -am` 通过

---

### T012 · [创建XX] · L4 · Controller: POST /api/v1/xxx

**功能**: 创建XX
**Depends**: T011
**预估**: 0.5h

**描述**:
暴露创建接口，参数校验 + 调用 Service。

**涉及文件**:
- `src/.../controller/XxxController.java`（新建/修改）

**伪代码**:
```java
@RestController
@RequestMapping("/api/v1/xxx")
public class XxxController {
    @PostMapping
    public Result<Long> createXxx(@RequestBody @Valid XxxCreateDTO dto) {
        log.info("createXxx param={}", JSON.toJSONString(dto));
        return Result.success(xxxService.createXxx(dto));
    }
}
```

**验收标准**:
- [ ] `mvn test-compile -pl {模块} -am` 通过
- [ ] 创建功能端到端可调通

---

## 统计

- 总 task 数：{N}
- 阶段一（L0+L1）：{n} 个
- 阶段二功能数：{K} 个，每功能 3 个 task（L2+L3+L4）
- 可并行功能组：{P} 组
- 预估总工时：{X}h
