# <短英文名>.json 元数据 Schema

extract-docs 的事实唯一来源。md 第五部分（知识清单）由本 json 渲染，**禁止两边各自手写**（防「文档写 16 Job 实为 15」的声明与事实脱节）。

产出路径：`knowledge/_manual/<domain>/<短英文名>.json`（与同名 .md 并排；**文件名不带 module- 前缀**，zhudian 域历史文件带前缀系旧约定）。

## 字段定义

| 字段 | 必填 | 类型 | 说明 | 校验方式 |
|---|---|---|---|---|
| `module` | ✅ | string | 模块业务名（中文口径，如「商广剩余流量对接」） | 非空 |
| `domain` | ✅ | string | 所属业务域，须为 `knowledge/_manual/domains.json` 的 key；新域须在收尾阶段登记 | domains.json 存在性（未登记=WARN） |
| `doc` | ✅ | string | 同名 md 相对工作区根的路径 | 文件存在 |
| `generated_at` | ✅ | string | 生成日期 `YYYY-MM-DD` | — |
| `summary` | ✅ | string | 一句话概述，须与 md 第一部分「这是什么系统」首句一致 | — |
| `apps` | ✅ | array | 涉及应用清单 `{key, role, evidence}`，key 须在 manifest.json | manifest 存在性=FAIL |
| `mq` | ✅ | array | 可空。`{type: rabbitmq\|kafka, name, direction: producer\|consumer, app, usage, evidence}` | name 源码字面量=FAIL |
| `third_party_apis` | ✅ | array | 可空。`{bean, provider, via, capabilities[], evidence}`，via 通常为 gateway | evidence 非空 |
| `entries.apis` | ✅ | array | 可空。`{app, controller, method, http, path, role, evidence}` | fragments apis / 源码 Controller 类双层=FAIL |
| `entries.jobs` | ✅ | array | 可空。`{app, class, method, cron, role, evidence}` | fragments jobs / 源码类文件双层=FAIL |
| `redis_keys` | ✅ | array | 可空。`{pattern, app, usage, evidence}`，动态段用 `*` | 静态前缀源码字面量=FAIL |
| `apollo_keys` | ✅ | array | 可空。`{key, app, usage, evidence}` | key 源码字面量=FAIL |
| `tables` | ✅ | array | 可空。`{name, app, access: R\|W\|RW, evidence}`；跨应用同表拆多条（每应用一条各标 access） | tables.json / 源码兜底=FAIL |
| `features` | ⭕ | object | `{state_machine: bool, approval_flow: bool}`，为 true 时 md 必须有对应 stateDiagram | md 图存在性 |

「可空」= 该类别确实没有也要保留空数组 `[]`（明确声明"无"，区别于"没查"）。

## evidence 规则（防假知识的锚）

每一条目必须有 `evidence`，二选一：

1. **fragments 锚**：`fragments nid`，如 `j-activity-37`、`a-activity-0`——表示该事实可在知识图谱分片中复核
2. **源码锚**：`相对路径:行号`（相对该应用源码根），如 `src/main/java/.../DspJob.java:88`

禁止只写类名不带定位、禁止写"见代码"这类不可复核的占位。

门禁会核验锚真伪：nid 锚查**存在性+指向一致性**（写 t-90 而该表实际是 t-0 会被拦）；源码锚查**可定位+行号不越界**。源码锚路径可省略 maven 模块/包前缀（脚本按路径尾部匹配，`service/dsp/Xxx.java` 能命中 `source/tuan-activity-provider/src/main/java/**/service/dsp/Xxx.java`），但禁止通配符（`Dsp*Mapper.xml` 这类写法锚不住）。

## 清单计数对账行（md 第五部分固定格式）

md 知识清单小节开头必须有这一行，脚本用它和 json 数组长度对账：

```markdown
> 清单计数：API 12 · Job 3 · 表 5 · Redis 4 · Apollo 2 · MQ 2 —— 与同名 .json 逐项对账
```

顺序固定 `API · Job · 表 · Redis · Apollo · MQ`，数字与对应数组 `entries.apis / entries.jobs / tables / redis_keys / apollo_keys / mq` 长度一致。`third_party_apis` 不进计数（归属第二部分依赖表）。

## 示例（节选，完整示例见冒烟产物）

```json
{
  "module": "会员积分（虚构示例）",
  "domain": "demo",
  "doc": "knowledge/_manual/demo/points-demo.md",
  "generated_at": "2026-09-14",
  "summary": "把用户在专题页的互动行为按规则换算积分，通过 PointApi（gateway，虚构积分中台）发放并落地抵扣。",
  "apps": [
    {"key": "activity", "role": "积分规则计算与发放JOB", "evidence": "fragments/activity.json"}
  ],
  "mq": [
    {"type": "rabbitmq", "name": "demo.point.issued", "direction": "producer", "app": "activity",
     "usage": "积分发放成功后广播", "evidence": "service/point/PointMqProducer.java:30"}
  ],
  "third_party_apis": [
    {"bean": "PointApi", "provider": "积分中台（虚构）", "via": "gateway",
     "capabilities": ["发放积分", "查询积分明细"], "evidence": "src/main/java/.../PointApi.java"}
  ],
  "entries": {
    "apis": [
      {"app": "portal-web", "controller": "PointController", "method": "getMyPoints", "http": "GET",
       "path": "/rapi/v1/point/my", "role": "C端积分查询", "evidence": "a-portal-web-xx"}
    ],
    "jobs": [
      {"app": "activity", "class": "PointExpireRemindJob", "method": "execute", "cron": "0 0 9 * * ?",
       "role": "积分到期提醒", "evidence": "j-activity-xx"}
    ]
  },
  "redis_keys": [
    {"pattern": "tuan:point:balance:*", "app": "activity", "usage": "积分余额缓存", "evidence": "PointRedisKey.java:12"}
  ],
  "apollo_keys": [
    {"key": "tuan.point.enabled", "app": "activity", "usage": "积分功能总开关", "evidence": "ApolloConfig.java:20"}
  ],
  "tables": [
    {"name": "PointRecord", "app": "activity", "access": "RW", "evidence": "t-xx"}
  ],
  "features": {"state_machine": false, "approval_flow": false}
}
```

> 示例是**完全虚构**的 demo 域（现实工作区没有这个模块），刻意不借用任何真实业务——避免"示例里有 MQ 所以我的模块也该有"的先入为主。真实采集时一切以 fragments + 源码为准，查无实据的条目禁止写入。
