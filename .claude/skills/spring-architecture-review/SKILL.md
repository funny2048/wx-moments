---
name: spring-architecture-review
description: >
  Spring Boot 分层架构审查。检查 Controller/Service/DAO 分层是否合规，
  Controller 是否包含业务逻辑、Service 是否过胖、跨层调用是否违规。
  当用户说"架构审查"、"分层检查"、"spring-review"、"架构合规"时触发。
---

# spring-architecture-review：Spring 分层架构审查

## 为什么需要这个 Skill

Spring Boot 项目最常见的"脏问题"就是分层混乱：Controller 写业务逻辑、
Service 过胖职责不清、DAO 层包含业务判断。这类问题在 AI 连续补代码时会越滚越大。
本 Skill 提供独立、可重复的分层架构审查。

## 审查规则

### 规则1：Controller 层只做参数校验和编排
**违规模式**：
- Controller 方法体超过 30 行
- Controller 中包含 if/else 业务分支判断（参数校验除外）
- Controller 直接调用 Mapper/DAO
- Controller 中有 @Transactional 注解

### 规则2：Service 层职责单一
**违规模式**：
- 单个 Service 类超过 500 行
- 一个 Service 方法超过 80 行
- Service 中直接操作 HttpServletRequest/Response
- 一个 Service 注入了超过 8 个依赖

### 规则3：DAO 层纯数据访问
**违规模式**：
- Mapper/DAO 中包含业务逻辑（if/else、循环拼接）
- DAO 方法名不表达数据操作语义

### 规则4：依赖方向正确
**合规方向**：Controller → Service → DAO
**违规模式**：
- Service 层调用 Controller
- DAO 层调用 Service
- 循环依赖

## 工作流程

1. 扫描所有 @RestController/@Controller 注解的类
2. 对每个 Controller 执行规则1检查
3. 扫描所有 @Service 注解的类，执行规则2检查
4. 扫描所有 Mapper/DAO 接口和实现，执行规则3检查
5. 分析 import 依赖关系，执行规则4检查
6. 生成审查报告

## 输出格式
```
## 分层架构审查报告

### 违规统计
| 规则 | 违规数 | 严重程度 |
|------|--------|----------|

### 违规详情
#### Controller 违规
- {类名}.{方法名}()：{违规描述}
  - 位置：{文件路径}:{行号}
  - 建议：{修复建议}

#### Service 违规
...

#### DAO 违规
...

#### 依赖方向违规
...
```
