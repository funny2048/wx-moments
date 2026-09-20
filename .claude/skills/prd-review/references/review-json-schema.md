# 4 角色审查 JSON Schema

> 本文档定义阶段 2 的 4 个 subagent 输出格式。所有角色必须严格遵循。

## 文件命名

| 角色 | 文件名 |
|------|--------|
| 产品总监 | `{WORK_DIR}/reviews/pd.json` |
| 技术 Leader | `{WORK_DIR}/reviews/tl.json` |
| 前端开发 | `{WORK_DIR}/reviews/fd.json` |
| 测试工程师 | `{WORK_DIR}/reviews/qa.json` |
| 技术 Leader（改动量评估） | `{WORK_DIR}/reviews/scale.json` |

> `{WORK_DIR}` = 任务参数 `work-dir` 指定的工作目录（独立使用默认 `/tmp/prd-extract/`）

## ID 前缀

| 角色 | ID 前缀 |
|------|--------|
| 产品总监 | `PD-` |
| 技术 Leader | `TL-` |
| 前端开发 | `FD-` |
| 测试工程师 | `QA-` |

---

## scale.json（tech-leader 专属，改动量评估）

```json
{
  "tier": "trivial | small | standard",
  "touch_points": {
    "entries": ["涉及入口：API/Job/MQ"],
    "tables": ["涉及表"],
    "modules": ["涉及模块"],
    "flags": { "state_machine": false, "idempotency": false, "transaction": false, "contract_change": false }
  },
  "estimated_files": "≤3 | ≤10 | >10",
  "evidence": "判断依据，引用 PRD 原文"
}
```

| tier | 条件（改动量分级依据） |
|------|------|
| trivial | 单入口 + 预估 ≤3 文件 + 四 flags 全 false |
| small | 预估 ≤10 文件 + 四 flags 全 false（入口可多个） |
| standard | 任一不满足（跨域写、契约/DDL 变更、状态机、幂等、事务新增） |

**merge_reviews.py 不消费本文件**（只读 pd/tl/fd/qa.json）；scale.json 仅作改动量初判信号随报告呈现

---

## 完整 Schema

```json
{
  "role": "产品总监 | 技术 Leader | 前端开发 | 测试工程师",
  "agent_version": "1.0",
  "overall": "🔴打回 | 🟡有条件通过 | 🟢通过",
  "reviewed_at": "2026-06-15T10:30:00",
  "type_specific_hints_applied": [
    "审查 SPEC 是否定义",
    "量化目标的推导逻辑"
  ],
  "findings": [
    {
      "id": "PD-001",
      "dimension": "痛点真实性",
      "severity": "🔴必答",
      "question": "月均 9000 单的数据是怎么测算的？",
      "why": "数据来源不清则效率提升目标无法验证",
      "success_criteria": "提供最近 3 个月订单量报表，标注口径",
      "evidence_from_doc": "BRD 第 2.2 节：'月均订单量约 9000'"
    }
  ],
  "findings_count": {
    "🔴必答": 2,
    "🟡建议": 3,
    "🟢可选": 1
  },
  "summary": "整体评价 2-3 句话"
}
```

---

## 字段说明

### 顶层字段

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `role` | string | ✅ | 角色中文名 |
| `agent_version` | string | ✅ | 固定 "1.0" |
| `overall` | string | ✅ | 三档之一 |
| `reviewed_at` | string | ✅ | ISO 时间戳 |
| `type_specific_hints_applied` | array | ✅ | 应用的类型专属提示（来自 profile.json） |
| `findings` | array | ✅ | 发现列表，1-10 个 |
| `findings_count` | object | ✅ | 按严重程度统计 |
| `summary` | string | ✅ | 整体评价 |

### findings 字段

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `id` | string | ✅ | `<前缀>-<3位序号>`，如 `PD-001` |
| `dimension` | string | ✅ | 维度名（参考通用清单 6 维度） |
| `severity` | string | ✅ | `🔴必答` / `🟡建议` / `🟢可选` |
| `question` | string | ✅ | 具体质疑（含场景/数据） |
| `why` | string | ✅ | 为什么重要（一句话） |
| `success_criteria` | string | ✅ | 拿到什么算清楚（不是"为什么重要"） |
| `evidence_from_doc` | string | ✅ | BRD 原文位置，未提及则标"BRD 未提及" |

---

## severity 三档定义

| 严重程度 | 含义 | 阶段 3 处理 |
|---------|------|-----------|
| 🔴 必答 | 不答不能上会 | 逐个 AskUserQuestion 澄清 |
| 🟡 建议 | 应该补充 | 批量展示，用户多选回答 |
| 🟢 可选 | 锦上添花 | 不交互，直接进报告"建议优化"区 |

## overall 判定规则

| 状态 | 条件 |
|------|------|
| 🔴 打回 | ≥ 3 个 🔴 findings |
| 🟡 有条件通过 | 1-2 个 🔴 findings |
| 🟢 通过 | 0 个 🔴 findings |

---

## 维度名参考（4 角色通用 + 类型专属）

### 产品总监通用维度

`业务背景`、`痛点真实性`、`目标合理性`、`ROI`、`优先级`、`上线衡量`

### 技术 Leader 通用维度

`范围边界`、`技术约束`、`异常路径`、`兼容影响`、`描述清晰度`、`优先级合理性`

### 前端开发通用维度

`空状态`、`加载反馈`、`操作路径`、`异常状态`、`信息层级`、`多端适配`

### 测试工程师通用维度

`验收标准`、`边界条件`、`回归范围`、`数据一致性`、`权限控制`、`版本兼容`

### 类型专属维度

从 `profile.json` 的 `decomposition_framework.common_defects` 提取，
作为本次审查的额外维度名（如 `SPEC定义`、`Bad Case 分类`、`降级方案` 等）。

---

## 校验规则（merge_reviews.py 会校验）

```python
def validate(review_json):
    assert review_json["role"] in ["产品总监", "技术 Leader", "前端开发", "测试工程师"]
    assert review_json["overall"] in ["🔴打回", "🟡有条件通过", "🟢通过"]
    assert 1 <= len(review_json["findings"]) <= 10
    
    for f in review_json["findings"]:
        assert f["severity"] in ["🔴必答", "🟡建议", "🟢可选"]
        assert all(key in f for key in [
            "id", "dimension", "severity", "question", 
            "why", "success_criteria", "evidence_from_doc"
        ])
    
    # findings 必须按 severity 排序
    severity_order = {"🔴必答": 0, "🟡建议": 1, "🟢可选": 2}
    for i in range(len(review_json["findings"]) - 1):
        curr = severity_order[review_json["findings"][i]["severity"]]
        next_ = severity_order[review_json["findings"][i+1]["severity"]]
        assert curr <= next_
```
