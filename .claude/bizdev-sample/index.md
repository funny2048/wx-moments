# bizdev-sample 索引(业务开发全链样例)

> 覆盖七个业务场景的全链风格样例。供 executor 编码前风格对齐:模仿分层结构、命名、日志、异常处理、DTO 包装与写操作安全惯用法,**不是可编译源码**;基础设施依赖均为占位形态(各文档「占位依赖对照表」)。

## 匹配规则(executor 必读)

1. 按 tasks.md / design.md 的功能特征打标签,与下表"特征标签"列匹配,读命中的 1-2 份样例
2. 复合需求叠加读(如"优惠券批量发放+导出进度" → campaign.md 的批量状态机 + export.md 的异步导出)
3. **项目内已有同类代码时,项目内代码优先**,样例只做底线(greenfield 或项目内无先例的模式)
4. 样例中的占位基础设施类(统一返回/登录态/分布式锁等)按各文档「占位依赖对照表」替换为本项目实际依赖,禁止照抄;开源依赖(MyBatis-Plus / PageHelper / Guava / Lombok 等)可直接引入
5. 命中不了任何特征标签时,按最接近的技术形态选基线(CRUD 类 → campaign.md)

## 模块映射表

| 业务模块 | 文件 | 特征标签(检索键) | 覆盖链路 |
|---|---|---|---|
| 广告计划管理 | [campaign.md](campaign.md) | CRUD+分页查询 / 状态机批量操作 / 幂等防重提交 / 操作限流 / 进程内事件 / 单条缓存+写后清理 | Controller→Service→Manager→Mapper 全链 + DO/In/Out/枚举/事件 |
| 后台导出 | [export.md](export.md) | 异步导出 / 提交即返回+记录落库 / job 轮询扫描 / 文件生成 / 状态回写 | Controller→Service→JobHandler→Mapper + 导出记录表 |
| 订单下单 | [order.md](order.md) | 提交事务 / MQ 收发 / 库存并发扣减 / requestId 幂等 / Manager 聚合多表 | Controller→Service→Manager(多表事务)→MQ + 库存服务 |
| 线索收集 | [leads.md](leads.md) | 表单收集 / 参数校验 / 幂等提交 / 落库即完结 | Controller→Service→Mapper 最简垂直链 |
| 报表开发 | [sales-report.md](sales-report.md) | 聚合 SQL / 多维度查询 / 图表 DTO(趋势/饼/柱) / 查询结果缓存 | Controller→Service→Manager(聚合)→Mapper + 图表 Out |
| 商品管理 | [goods.md](goods.md) | 商品 CRUD / 上下架 / 详情缓存 / 库存展示 | Controller→Service→Mapper + 详情缓存 |
| 商家资料修改审核 | [merchant-audit.md](merchant-audit.md) | 双表快照审核 / 提交-审核分离 / CAS 状态机 / 并发审核互斥 / 敏感数据脱敏 | 提交方+审核方双 Controller→Service→审核事务 Manager(双表写) |

## 选型速查(按任务形态)

- 新增一张业务表的后台管理页(增删改查+分页) → campaign.md
- 长耗时任务(导出/生成/批处理)需要异步化 → export.md
- 涉及下单/扣减/事务一致性/消息通知 → order.md
- 简单表单收集落地(线索/报名/反馈) → leads.md
- 统计报表/数据看板/图表接口 → sales-report.md
- 商品/内容管理+前台展示缓存 → goods.md
- 修改需人工审核才生效(资料/内容审批、变更留痕) → merchant-audit.md

## 维护说明

- 样例由 harness 统一维护;新增业务场景按同结构扩展(七段结构 + 占位依赖对照表 + 中性包名 `com.example.demo`)
- 本目录随 project_install.py 整目录安装到目标项目 `.claude/agents/bizdev-sample/`,不分档
