# Pipeline: {phase-dir} {Phase名称}

> 基于 domain.md 与代码分析，生成 P2 图谱链路文档。
> 生成日期: {date}

## 一、核心调用链总览

```
┌─────────────────────────────────────────────────────────────────┐
│                         Controller 层入口                          │
├──────────────┬──────────────┬──────────────┬────────────────────┤
│{Controller1} │{Controller2} │{Controller3} │{Controller4}       │
│Controller    │Controller    │Controller    │                    │
└──────┬───────┴──────┬───────┴──────┬───────┴────────┬───────────┘
       │              │              │                │
       ▼              ▼              ▼                ▼
┌─────────────────────────────────────────────────────────────────┐
│                         Service 层处理                            │
├──────────────┬──────────────┬──────────────┬────────────────────┤
│{Service1}    │{Service2}    │{Service3}    │{Service4}          │
│ServiceImpl   │ServiceImpl   │ServiceImpl   │Service             │
└──────┬───────┴──────┬───────┴──────┬───────┴────────┬───────────┘
       │              │              │                │
       ▼              ▼              ▼                ▼
┌─────────────────────────────────────────────────────────────────┐
│                          Mapper / DAO 层                          │
├──────────────┬──────────────┬──────────────┬────────────────────┤
│{Mapper1}     │{Mapper2}     │{Mapper3}     │{Mapper4}           │
│Mapper        │Mapper        │Mapper        │Mapper              │
└──────────────┴──────────────┴──────────────┴────────────────────┘
```

<!-- 说明：根据实际 Controller/Service/Mapper 数量调整列数，删除空列 -->

## 二、节点详情

### 2.1 {Controller1} 入口节点

| 方法 | 路由 | 涉及 FR | 当前行为 |
|------|------|---------|---------|
| `{methodName1}` | {HTTP方法} {路由路径} | {FR-xxx} | {调用链简要描述} |
| `{methodName2}` | {HTTP方法} {路由路径} | {FR-xxx} | {调用链简要描述} |

**{ServiceImpl}.{methodName} 调用链：**
```
{methodName} → @Transactional("{transactionManager}")
  ├─ {mapperMethod1}({params}) // {SQL操作说明}
  ├─ {mapperMethod2}({params}) // {操作说明}
  └─ {serviceMethod}({params}) // {操作说明}
```

<!-- 为每个入口节点重复 2.N 小节 -->

### 2.N {ControllerN} 入口节点

| 方法 | 路由 | 涉及 FR | 当前行为 |
|------|------|---------|---------|
| `{methodName}` | {HTTP方法} {路由路径} | {FR-xxx} | {调用链简要描述} |

**{ServiceImpl}.{methodName} 调用链：**
```
{methodName} → @Transactional("{transactionManager}")
  ├─ {mapperMethod1}({params}) // {SQL操作说明}
  └─ {mapperMethod2}({params}) // {操作说明}
```

## 三、边关系

| 起点 | 终点 | 关系 | 数据流 |
|------|------|------|--------|
| {Controller}.{method} | {ServiceImpl}.{method} | CALLS | {传递的关键参数} |
| {ServiceImpl}.{method} | {Mapper}.{method} | CALLS | {传递的关键参数} |
| {ServiceImplA}.{method} | {ServiceImplB}.{method} | CALLS | {跨 Service 调用的参数} |

<!-- 说明：每行描述一条调用边，包含 Controller→Service、Service→Mapper、跨 Service 调用 -->

## 四、社区聚类

### 社区1: {社区名称}（{FR-xxx}）
- {Controller}.{method} → {ServiceImpl}
- {Controller}.{method} → {Service}
- **数据表**: {表名1} ({关键字段}), {表名2}

<!-- 每个功能需求或业务领域形成一个社区 -->

### 社区N: {社区名称}（{FR-xxx}）
- {调用链描述}
- **数据表**: {表名}（{状态说明}）

## 五、数据流汇总

### 当前 {核心表名} 表字段现状
| 字段 | 类型 | 说明 | 使用场景 |
|------|------|------|---------|
| {field1} | {type1} | {说明} | {涉及 FR} |
| {field2} | {type2} | {说明} | {涉及 FR} |
| {field_new} | - | ❌ 不存在，需新增 | {涉及 FR} |

<!-- 对每个涉及改动的表，列出字段现状和需新增的字段 -->

### 当前 {ServiceImpl} 审核状态现状
- {现状描述，如"save 时自动设置 auditStatus=AUDIT_PASS"}
- {需变更说明，如"需改为保存时设置 auditStatus=WAIT_AUDIT"}
- {新增需求说明}

## 六、改动影响范围

| 文件 | 所在模块 | 改动类型 | 涉及 FR |
|------|---------|---------|---------|
| {ServiceImpl}.java | {模块名} | 新增/扩展/复用 | {FR-xxx} |
| {Mapper}.xml | {模块名} | 新增/扩展/复用 | {FR-xxx} |
| {Mapper}.java | {模块名} | 新增/扩展/复用 | {FR-xxx} |
| {Controller}.java | {模块名} | 新增/扩展/复用 | {FR-xxx} |
| {Enum}.java | {模块名} | 复用 | {FR-xxx} |

## 七、风险点

1. **{风险类别1}**: {具体描述，如"新增字段需检查是否影响现有查询（如 WhereCondition、selectByCondition）"}
2. **{风险类别2}**: {具体描述，如"事务边界问题，需在同一事务内完成"}
3. **{风险类别3}**: {具体描述，如"级联性能风险，需分批处理"}
4. **{跨Phase依赖}**: {涉及依赖其他Phase产物的说明，需确认接口和数据模型}

---
*Phase: {phase-dir}*
*Generated: {date}*
