# 评审报告 8 章节模板

> 本文档是 `generate_report.py` 的输出参考。
> 脚本读取 3 份 JSON（profile + merged-questions + clarifications）后按此模板拼接。

---

## 完整模板

```markdown
# 需求评审报告

**需求名称：** {{从画像提取或文档第一行}}
**评审日期：** {{YYYY-MM-DD}}
**需求类型：** {{primary}}{% if secondary %}（+ {{secondary}}）{% endif %}
**上会就绪度：** {{🟢通过 / 🟡有条件通过 / 🔴打回}}

---

## 一、需求画像

**业务本质（一句话）：**
{{profile.brd_essence}}

**拆解框架：**
{{profile.decomposition_framework.matrix}}

**常见缺陷信号：**
{% for defect in profile.decomposition_framework.common_defects %}
- {{defect}}
{% endfor %}

---

## 二、PRD 健康度仪表盘

| 维度 | 状态 | 角色发现数 |
|------|------|----------|
{% for dim, info in dashboard %}
| {{dim}} | [{{info.bar}}] {{info.status}} | {{info.count}} |
{% endfor %}

**综合评分：** {{score}}/10
**最大风险项：** {{max_risk_dim}}（{{max_risk_count}} 个发现）

---

## 三、整体结论

**上会就绪度：** {{readiness_emoji}} {{readiness_label}}

**判断依据：**
- 🔴 必答问题 {{critical_total}} 个，已回答 {{answered}} 个，待补充 {{deferred}} 个
{% if readiness == "🟡" %}
- 建议 {{critical_total}} 个 🔴 全部澄清后再上会
- 或者明确告知评审方"以下 {{deferred}} 个问题待补充"
{% endif %}
{% if readiness == "🔴" %}
- 不建议上会，必答问题未澄清比例过高
{% endif %}

---

## 四、问题清单（按角色分组）

### 4.1 产品总监发现（{{pd_count}} 个）

**整体判定：** {{pd_overall}}

| ID | 维度 | 严重 | 问题 | 澄清状态 |
|----|------|------|------|---------|
{% for f in pd_findings %}
| {{f.id}} | {{f.dimension}} | {{f.severity}} | {{f.question}} | {{f.clarif_status}} |
{% endfor %}

### 4.2 技术 Leader 发现（{{tl_count}} 个）
（同上格式）

### 4.3 前端开发发现（{{fd_count}} 个）
（同上格式）

### 4.4 测试工程师发现（{{qa_count}} 个）
（同上格式）

---

## 五、澄清记录汇总

**澄清统计：**
- ✅ 已回答：{{answered}} 个（{{answered_pct}}%）
- ⚠️ 待补充：{{deferred}} 个（{{deferred_pct}}%）
- ℹ️ 文档已有：{{doc_has_it}} 个（{{doc_has_it_pct}}%）
- ⏭️ 已跳过：{{skipped}} 个（{{skipped_pct}}%）

**待补充清单（上会前需处理）：**
{% for c in deferred_list %}
{{loop.index}}. {{c.question_id}} · {{c.dimension}} · "{{c.question}}"
   - 用户备注：{{c.deferred_reason}}
{% endfor %}

---

## 六、必须补充的内容（上会前必做）

{% for action in must_do %}
{{loop.index}}. **{{action.title}}**：{{action.detail}}
{% endfor %}

---

## 七、建议优化的内容（非必须但加分）

{% for suggestion in nice_to_have %}
{{loop.index}}. {{suggestion}}
{% endfor %}

---

## 八、上会 Tips

基于本次评审，给你几个正式评审时的建议：

{% for tip in top_tips %}
{{loop.index}}. **{{tip.role}}** 可能会问 {{tip.topic}}：{{tip.advice}}
{% endfor %}
```

---

## 章节产物来源映射

| 章节 | 数据来源 |
|------|---------|
| 一、需求画像 | `profile.json` |
| 二、健康度仪表盘 | `merged-questions.json` 按 9 维度聚合 |
| 三、整体结论 | `merged-questions.json` + `clarifications.json` 计算 |
| 四、问题清单 | `merged-questions.json` 按角色分组 |
| 五、澄清记录 | `clarifications.json` |
| 六、必须补充 | severity=🔴 且 status=deferred/skipped 的问题 |
| 七、建议优化 | severity=🟢 的问题 |
| 八、上会 Tips | severity=🔴 的问题 + 类型专属缺陷 |

---

## 健康度仪表盘 10 维度映射

```python
DIMENSION_MAP = {
    "背景与目标": ["业务背景", "痛点真实性", "目标合理性", "ROI", "优先级", "上线衡量"],
    "用户场景": ["场景示例", "用户角色", "用户画像"],
    "业务规则": ["业务规则", "字段定义", "状态流转", "权限控制"],
    "交互设计": ["空状态", "加载反馈", "操作路径", "异常状态", "信息层级", "多端适配"],
    "技术约束": ["技术约束", "范围边界", "描述清晰度", "SPEC定义", "降级方案"],
    "验收标准": ["验收标准", "边界条件", "回归范围", "测试用例"],
    "范围边界": ["范围边界", "描述清晰度", "优先级合理性", "颗粒度"],
    "影响范围": ["兼容影响", "数据一致性", "版本兼容", "数据迁移"],
    "合规与风险": ["合规", "风险", "降级方案", "兜底方案"],
    "数据 SLA": ["指标定义", "数据源", "更新频率", "数据质量", "性能 SLA", "血缘影响", "历史数据", "数据导出", "数据准确性", "数据监控", "口径一致性", "T+1", "实时"]
}
```

> 第 10 维"数据 SLA" 专为**数据产品 / BI 类**需求设计，其他类型需求该维度通常为完整状态（无相关 findings）。

## 三档评级规则

| 状态 | 条件 | 进度条 |
|------|------|--------|
| 完整 | 该维度 0 个 findings | `[##########]` |
| 部分 | 只有 🟡 或 🟢 findings | `[#####-----]` |
| 缺失 | 有 🔴 必答 findings | `[----------]` |

## 上会就绪度计算

```python
def calc_readiness(merged, clarifications):
    critical_qs = [q for q in merged["questions"] if q["severity"] == "🔴必答"]
    total = len(critical_qs)
    
    if total == 0:
        return "🟢", "通过", "无必答问题"
    
    status_count = count_by_status(critical_qs, clarifications)
    answered_ratio = status_count["answered"] / total
    
    if status_count["skipped"] == total:
        return "🔴", "打回重写", "所有必答问题被跳过"
    elif answered_ratio >= 0.8:
        return "🟢", "通过", f"{status_count['answered']}/{total} 必答已澄清"
    elif answered_ratio >= 0.5:
        return "🟡", "有条件通过", f"{status_count['answered']}/{total} 必答已澄清，{status_count['deferred']} 待补充"
    else:
        return "🔴", "打回重写", f"仅 {status_count['answered']}/{total} 必答已澄清"
```

---

## 上会 Tips 生成规则

对每个 🔴 必答问题，生成一条 tip：

```
{{role}} 可能会问 {{dimension}}：{{question}}
建议：{{success_criteria}}
```

取前 3 条最关键的（按 severity + 类型缺陷排序）。

---

## 综合评分计算

```python
DIMENSION_WEIGHTS = {
    "背景与目标": 1.5, "用户场景": 1.0, "业务规则": 1.5,
    "交互设计": 1.0, "技术约束": 1.0, "验收标准": 1.0,
    "范围边界": 0.75, "影响范围": 0.75, "合规与风险": 0.5,
    "数据 SLA": 1.0  # 满分 11 分
}

def calc_score(dashboard):
    """满分动态：11 分（含数据 SLA 维度）"""
    score = 0
    max_score = sum(DIMENSION_WEIGHTS.values())  # 11.0
    for dim, info in dashboard.items():
        if info["status"] == "完整":
            score += DIMENSION_WEIGHTS[dim]
        elif info["status"] == "部分":
            score += DIMENSION_WEIGHTS[dim] * 0.5
        # 缺失不加分
    
    return round(score, 1), round(max_score, 1)
```

> 评分说明：满分 11 分（含数据 SLA）。对于非数据产品类需求，数据 SLA 维度通常为"完整"（无 findings），相当于满分 10 分基础上的额外加分。
