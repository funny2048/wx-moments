# Arch Review: 20260920-social-timeline-mvp

> 评审人：architect-reviewer（阶段七架构审查，与 code-reviewer 并行）
> 依据：design.md（冻结版，含 §14 修复映射）× 阶段六代码变更 89 文件（review-scale.txt）
> 证据采样：web 层 5 Controller + 4 横切件全文、service 层 3 核心实现 + 1 缓存组件全文、PostMapper.xml 全文、其余经批量合规扫描（${} / 注解 SQL / QueryWrapper / Lombok / 逆向依赖 / is_del 覆盖）

## 整体结论
- 状态：**PASS**
- BLOCKER 数：**0**

## 严重问题（必须修复）
无。

## 建议改进
1. **design.md §2.2/§6.1 vs WebConfig/BizExceptionAdvice** — 实现含三处超出 design 字面声明的扩展，均有代码注释留痕但冻结基线未回写：
   - BizExceptionAdvice 实际处理四类异常（design §6.1 声明三类；第 4 类 `MaxUploadSizeExceededException`→1021 为 Stage 6 裁决8 追加）
   - WebConfig 除 addResourceHandlers 外新增 `multipartConfigElement` @Bean（Stage 6 裁决4；design §2.2 称"仅追加 addResourceHandlers"）
   - 建议：归档前将 Stage 6 裁决4/8 补录 design.md §14 修复映射表，保持真源完整。三处扩展方向正确（承载 api.md 契约、不改分层、不引新依赖），不构成阻塞。
2. **design §3.1 #7/#8/#10 vs java-guide 2.1** — 修改/删除端点使用 `@PutMapping`/`@DeleteMapping`，java-guide 偏好"新方法优先 GET/POST"（checklist 禁 PUT/PATCH，未禁 DELETE）。PUT 端点为冻结设计明确定义且实现忠于设计，属设计与公司规范的基线分歧而非实现偏差；如需对齐可后续统一改 POST。
3. **FeedServiceImpl §5.4 分支四** — 设计伪代码输出判定为三分支，实现亦三分支且完备（collected>size / scanEnd / 其余），但伪代码首行 `collected.size() > pageSize` 与实现 `> size` 的截断边界在 `collected.size() == size` 时归入分支二/三（items=collected 恰满页）——语义等价（下一页从扫描锚点续扫，canView 幂等不重复），仅提示阅读时注意，无需改动。

## 做得好的地方
- **分层零违规**：Service 层无 HttpServletRequest、DAO/client 无逆向依赖、Controller 无业务逻辑；唯一 Pom 新增（service→client）有注释留痕，web→starter-test 为 test scope 且排除日志冲突有详注。
- **B1 双锚点算法逐分支落地**（FeedServiceImpl）：扫描进度锚点/页末锚点、`items=[] && hasMore=true && nextCursor≠null` 合法组合、放大有界（3 批×3 倍）均与 §5.4 伪代码一一对应。
- **事务纪律**：deleteTag INCR 走 afterCommit 回调（含无事务上下文防御分支）；createPost 4 表 @Transactional(rollbackFor) 事务内无 Redis/文件 IO；MockData 自注入代理规避 this 直调 AOP 失效（R11），分批小事务 + clear 后先失效缓存的恢复语义均落地。
- **DAO 规范全绿**：8 个 XML 零 `${}`、零注解 SQL、每 select 带 `is_del=0`（16 处覆盖 19 个 select，含 update 语句）、`&lt;` 转义、foreach 批量；DO 手写 getter/setter 无 Lombok。
- **缓存设计完整**：空集哨兵 "0" 防穿透、全局版本号失效、Redis 异常降级回源（C12）三件套在两个 CacheManager 对称实现。
- **配置防分裂**：图片根目录双 @Value 默认值逐字一致（§6.2 核对要求被执行），静态映射与落盘目录不分裂。
- **可观测性落地**：Controller 首行日志全覆盖、Feed 过滤统计（候选/可见/批次/hasMore）、缓存 hit/miss/降级日志、异常 advice 分级记录，与 §10 逐条对应。

## 9 项范围结论速览
| # | 范围 | 结论 |
|---|------|------|
| 1 | 分层架构 | PASS（三层严格、方向正确、无跨层） |
| 2 | 数据流 | PASS（DO 不出 DAO、Out 出参带中文标识/时间串、无 PII） |
| 3 | 接口设计 | PASS（ApiResult 统一、错误码枚举、_appId 首参；PUT 见建议 2） |
| 4 | 依赖管理 | PASS（新增 2 处均必要且有留痕、无循环依赖、按功能域分包） |
| 5 | 可扩展性 | PASS（配置外化、枚举驱动、canView 单实现、缓存版本机制） |
| 6 | 安全合规 | PASS（§7.3 填齐含豁免留痕；红线 4 项逐条核过：写接口归属校验/无 ${}/无敏感日志/权限声明） |
| 7 | 性能可见性 | PASS（§7.1 填齐；N+1 批量组装、索引声明齐、无全局锁） |
| 8 | 可观测性 | PASS（§10 填齐；traceId/告警以实验室定位显式声明不接入并留档 §12） |
| 9 | 实现一致性 | PASS（89 文件全落 §2.2/§6 声明范围、7 Controller/8 Service/8 Mapper/2 缓存件与设计一一对应；三处扩展见建议 1） |

## 摘要（供主 Agent 汇总）
架构审查 PASS，BLOCKER 0。分层/数据流/接口/依赖/安全/性能/可观测 8 项全绿：三层零违规、依赖方向干净、DAO 规范全量合规（无 ${}、is_del 全覆盖）、B1 双锚点与 afterCommit 缓存失效等三轮对抗修复项均忠实落地，§7.1/§7.3/§10 三个基线 section 填齐且实现对应。建议 3 条均为文档级：Stage 6 裁决4/8（multipart @Bean、第 4 类异常 handler）补录 design.md §14；PUT 端点为冻结设计与 java-guide GET/POST 偏好的基线分歧（实现忠于设计，非阻塞）；Feed 截断边界语义等价说明。
