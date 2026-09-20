---
name: sql-risk-review
description: >
  SQL 风险审查。检查 SQL 语句、MyBatis Mapper XML、批量更新操作的风险，
  包括：缺少 WHERE 条件的 UPDATE/DELETE、SELECT *、大表全表扫描、
  索引未命中等。当用户说"SQL审查"、"SQL风险"、"mapper检查"、"sql-review"时触发。
---

# sql-risk-review：SQL 风险审查

## 为什么需要这个 Skill

SQL 问题不是 bug，而是事故预告。一个没有 WHERE 的 UPDATE 能在秒级内毁掉整张表的数据。
本 Skill 是最后一道防线，在代码合并前自动检测 SQL 风险。

## 检查规则

### 规则1：UPDATE/DELETE 必须有 WHERE
**严重程度**：🔴 致命
**检查项**：
- MyBatis XML 中的 `<update>` 和 `<delete>` 标签
- Java 代码中的 UPDATE/DELETE SQL 拼接
- 特别注意动态 SQL：`<if>` 条件全部不满足时是否导致无条件更新

### 规则2：批量操作必须有 LIMIT
**严重程度**：🔴 致命
**检查项**：
- `IN (...)` 子句的元素数量（建议不超过 500）
- `UPDATE ... WHERE status = 0` 类的大范围更新
- 分页更新是否使用 LIMIT

### 规则3：禁止 SELECT *
**严重程度**：🟡 警告
**检查项**：
- Mapper XML 和注解中的 SELECT *
- MyBatis-Plus 的 selectList(null) 等全量查询

### 规则4：多数据源不跨库联表
**严重程度**：🔴 致命
**检查项**：
- SQL 中是否包含不同数据源的表名前缀
- Mapper XML 中的 JOIN 是否跨库

### 规则5：索引命中检查
**严重程度**：🟡 警告
**检查项**：
- WHERE 条件字段是否有索引（需结合表结构判断）
- LIKE '%keyword%' 前缀模糊查询
- OR 条件是否会导致索引失效

## 工作流程

1. 扫描 resources/mapper/ 下所有 XML 文件
2. 对每个 SQL 语句逐条执行规则检查
3. 扫描 Java 代码中的 @Select/@Update/@Delete 注解
4. 检查 MyBatis-Plus QueryWrapper 的使用
5. 生成风险报告

## 输出格式
```
## SQL 风险审查报告

### 风险统计
| 规则 | 违规数 | 严重程度 |
|------|--------|----------|
| UPDATE/DELETE 无 WHERE | 0 | 🔴 致命 |
| 批量操作无 LIMIT | 1 | 🔴 致命 |
| SELECT * | 3 | 🟡 警告 |
| 跨库联表 | 0 | 🔴 致命 |
| 索引风险 | 2 | 🟡 警告 |

### 致命风险（必须修复）
#### 🔴 {Mapper文件名}:{行号}
- SQL：`{sql片段}`
- 风险：{描述}
- 建议：{修复方案}

### 警告风险（建议修复）
#### 🟡 {文件名}:{行号}
- SQL：`{sql片段}`
- 风险：{描述}
- 建议：{修复方案}
```
