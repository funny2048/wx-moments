#!/usr/bin/env python3
"""
生成 Markdown 评审报告（8 章节）

用法：
    python3 generate_report.py <extract_dir> <output_path>

输入（extract_dir 下）：
    - profile.json
    - merged-questions.json
    - clarifications.json

输出：
    - final-report.md（8 章节）
"""

import json
import sys
from pathlib import Path
from datetime import datetime
from collections import defaultdict

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
    "数据 SLA": ["指标定义", "数据源", "更新频率", "数据质量", "性能 SLA", "血缘影响", "历史数据", "数据导出", "数据准确性", "数据监控", "口径一致性", "T+1", "实时"],
}

DIMENSION_WEIGHTS = {
    "背景与目标": 1.5, "用户场景": 1.0, "业务规则": 1.5,
    "交互设计": 1.0, "技术约束": 1.0, "验收标准": 1.0,
    "范围边界": 0.75, "影响范围": 0.75, "合规与风险": 0.5,
    "数据 SLA": 1.0,
}

STATUS_EMOJI = {
    "answered": "✅ 已回答",
    "deferred": "⚠️ 待补充",
    "doc_has_it": "ℹ️ 文档已有",
    "skipped": "⏭️ 已跳过",
}


def load_inputs(extract_dir: str) -> dict:
    """加载 3 份 JSON"""
    d = Path(extract_dir)
    return {
        "profile": json.loads((d / "profile.json").read_text(encoding="utf-8")),
        "merged": json.loads((d / "merged-questions.json").read_text(encoding="utf-8")),
        "clarif": json.loads((d / "clarifications.json").read_text(encoding="utf-8")),
    }


def calc_dashboard(merged: list) -> dict:
    """计算 9 维度仪表盘"""
    dashboard = {}
    for dim, sub_dims in DIMENSION_MAP.items():
        findings = [q for q in merged if q["dimension"] in sub_dims]
        if not findings:
            status, bar = "完整", "##########"
        elif any(f["severity"] == "🔴必答" for f in findings):
            status, bar = "缺失", "----------"
        else:
            status, bar = "部分", "#####-----"
        dashboard[dim] = {"status": status, "bar": bar, "count": len(findings)}
    return dashboard


def calc_score(dashboard: dict) -> tuple:
    """综合评分，满分动态（10 或 11，看是否包含数据 SLA 维度）
    返回 (score, max_score)"""
    score = 0.0
    max_score = sum(DIMENSION_WEIGHTS.values())
    for dim, info in dashboard.items():
        w = DIMENSION_WEIGHTS[dim]
        if info["status"] == "完整":
            score += w
        elif info["status"] == "部分":
            score += w * 0.5
    return round(score, 1), round(max_score, 1)


def calc_readiness(merged: list, clarif: dict) -> tuple:
    """计算上会就绪度"""
    critical_qs = [q for q in merged if q["severity"] == "🔴必答"]
    total = len(critical_qs)
    if total == 0:
        return "🟢", "通过", "无必答问题"

    clarif_map = {c["question_id"]: c for c in clarif.get("clarifications", [])}
    status_count = defaultdict(int)
    for q in critical_qs:
        c = clarif_map.get(q["id"])
        status_count[c["response_status"] if c else "deferred"] += 1

    answered = status_count["answered"]
    deferred = status_count["deferred"]
    skipped = status_count["skipped"]

    if skipped == total:
        return "🔴", "打回重写", "所有必答问题被跳过"
    if answered >= total * 0.8:
        return "🟢", "通过", f"{answered}/{total} 必答已澄清"
    if answered >= total * 0.5:
        return "🟡", "有条件通过", f"{answered}/{total} 必答已澄清，{deferred} 待补充"
    return "🔴", "打回重写", f"仅 {answered}/{total} 必答已澄清"


def render_section_1(profile: dict) -> str:
    """需求画像"""
    rt = profile["requirement_type"]
    df = profile.get("decomposition_framework", {})
    essence = profile.get("brd_essence", "未识别")
    type_str = rt.get("primary", "未识别")
    if rt.get("secondary"):
        type_str += f"（+ {rt['secondary']}）"
    defects = df.get("common_defects", ["无明确信号"])

    lines = [
        "## 一、需求画像\n",
        f"**业务本质（一句话）：**\n{essence}\n",
        f"**拆解框架：**\n{df.get('matrix', '未识别')}\n",
        "**常见缺陷信号：**",
    ]
    for d in defects:
        lines.append(f"- {d}")
    lines.append(f"\n**类型置信度：** {rt.get('confidence', '中')}\n")
    return "\n".join(lines)


def render_section_2(dashboard: dict, score: float, max_score: float) -> str:
    """健康度仪表盘"""
    max_risk_dim = max(dashboard.items(), key=lambda x: x[1]["count"])[0]
    max_risk_count = dashboard[max_risk_dim]["count"]

    lines = [
        "## 二、PRD 健康度仪表盘\n",
        "| 维度 | 状态 | 角色发现数 |",
        "|------|------|----------|",
    ]
    for dim, info in dashboard.items():
        lines.append(f"| {dim} | [{info['bar']}] {info['status']} | {info['count']} |")
    lines.append(f"\n**综合评分：** {score}/{max_score}")
    lines.append(f"**最大风险项：** {max_risk_dim}（{max_risk_count} 个发现）\n")
    return "\n".join(lines)


def render_section_3(emoji: str, label: str, reason: str, merged: list, clarif: dict) -> str:
    """整体结论"""
    critical_qs = [q for q in merged if q["severity"] == "🔴必答"]
    total = len(critical_qs)
    clarif_map = {c["question_id"]: c for c in clarif.get("clarifications", [])}
    answered = sum(1 for q in critical_qs if clarif_map.get(q["id"], {}).get("response_status") == "answered")
    deferred = sum(1 for q in critical_qs if clarif_map.get(q["id"], {}).get("response_status") == "deferred")

    lines = [
        "## 三、整体结论\n",
        f"**上会就绪度：** {emoji} {label}\n",
        "**判断依据：**",
        f"- 🔴 必答问题 {total} 个，已回答 {answered} 个，待补充 {deferred} 个",
        f"- {reason}",
    ]
    if emoji == "🟡":
        lines.append(f"- 建议 {total} 个 🔴 全部澄清后再上会")
        lines.append(f"- 或者明确告知评审方\"以下 {deferred} 个问题待补充\"")
    elif emoji == "🔴":
        lines.append("- 不建议上会，必答问题未澄清比例过高\n")
    return "\n".join(lines)


def render_section_4(merged: list, clarif: dict) -> str:
    """问题清单（按角色分组）"""
    role_groups = defaultdict(list)
    for q in merged:
        role_groups[q["role"]].append(q)

    clarif_map = {c["question_id"]: c for c in clarif.get("clarifications", [])}
    role_overall = {"产品总监": "🟡", "技术 Leader": "🟡", "前端开发": "🟡", "测试工程师": "🟡"}

    lines = ["## 四、问题清单（按角色分组）"]
    for role, qs in role_groups.items():
        lines.append(f"\n### 4.{list(role_groups.keys()).index(role)+1} {role} 发现（{len(qs)} 个）\n")
        lines.append("| ID | 维度 | 严重 | 问题 | 澄清状态 |")
        lines.append("|----|------|------|------|---------|")
        for q in qs:
            clarif_status = "未澄清"
            c = clarif_map.get(q["id"])
            if c:
                clarif_status = STATUS_EMOJI.get(c["response_status"], "未澄清")
            qtext = q.get("question") or " / ".join(q.get("questions", []))
            qtext = qtext.replace("|", "\\|")[:50]
            lines.append(f"| {q['id']} | {q['dimension']} | {q['severity']} | {qtext} | {clarif_status} |")
    return "\n".join(lines)


def render_section_5(clarif: dict) -> str:
    """澄清记录汇总"""
    clarifs = clarif.get("clarifications", [])
    total = len(clarifs)
    if total == 0:
        return "## 五、澄清记录汇总\n\n本次评审无澄清记录。\n"

    status_count = defaultdict(int)
    for c in clarifs:
        status_count[c["response_status"]] += 1

    lines = [
        "## 五、澄清记录汇总\n",
        "**澄清统计：**",
        f"- ✅ 已回答：{status_count['answered']} 个（{status_count['answered']*100//total}%）",
        f"- ⚠️ 待补充：{status_count['deferred']} 个（{status_count['deferred']*100//total}%）",
        f"- ℹ️ 文档已有：{status_count['doc_has_it']} 个（{status_count['doc_has_it']*100//total}%）",
        f"- ⏭️ 已跳过：{status_count['skipped']} 个（{status_count['skipped']*100//total}%）\n",
    ]

    deferred_list = [c for c in clarifs if c["response_status"] == "deferred"]
    if deferred_list:
        lines.append("**待补充清单（上会前需处理）：**")
        for i, c in enumerate(deferred_list, 1):
            reason = c.get("deferred_reason") or c.get("user_response") or "无备注"
            lines.append(f"{i}. {c['question_id']} · {c.get('dimension', '?')} · \"{c['question']}\"")
            lines.append(f"   - 用户备注：{reason}")
    return "\n".join(lines)


def render_section_6_7_8(merged: list, clarif: dict, profile: dict) -> str:
    """六、必须补充；七、建议优化；八、上会 Tips"""
    clarif_map = {c["question_id"]: c for c in clarif.get("clarifications", [])}

    must_do = []
    nice_to_have = []
    tips = []

    for q in merged:
        c = clarif_map.get(q["id"])
        status = c["response_status"] if c else "deferred"
        qtext = q.get("question") or " / ".join(q.get("questions", []))

        if q["severity"] == "🔴必答" and status in ("deferred", "skipped"):
            must_do.append({
                "title": f"{q['dimension']}：{qtext[:40]}",
                "detail": q.get("success_criteria", "")
            })
        elif q["severity"] == "🟢可选":
            nice_to_have.append(f"{q['dimension']}：{qtext[:40]}")

        if q["severity"] == "🔴必答":
            tips.append({
                "role": q["role"],
                "topic": q["dimension"],
                "advice": q.get("success_criteria", "")
            })

    defects = profile.get("decomposition_framework", {}).get("common_defects", [])
    for d in defects[:2]:
        tips.append({"role": "类型加码", "topic": d, "advice": "提前准备应对策略"})

    lines = ["## 六、必须补充的内容（上会前必做）\n"]
    if must_do:
        for i, a in enumerate(must_do, 1):
            lines.append(f"{i}. **{a['title']}**：{a['detail']}")
    else:
        lines.append("✅ 所有 🔴 必答问题已澄清，无必须补充项。")

    lines.append("\n## 七、建议优化的内容（非必须但加分）\n")
    if nice_to_have:
        for i, s in enumerate(nice_to_have, 1):
            lines.append(f"{i}. {s}")
    else:
        lines.append("本次评审无 🟢 可选建议。")

    lines.append("\n## 八、上会 Tips\n")
    lines.append("基于本次评审，给你几个正式评审时的建议：\n")
    for i, t in enumerate(tips[:5], 1):
        lines.append(f"{i}. **{t['role']}** 可能会问 {t['topic']}：{t['advice']}")
    return "\n".join(lines)


def generate_report(extract_dir: str, output_path: str) -> None:
    """主函数"""
    data = load_inputs(extract_dir)
    profile, merged, clarif = data["profile"], data["merged"]["questions"], data["clarif"]

    dashboard = calc_dashboard(merged)
    score, max_score = calc_score(dashboard)
    emoji, label, reason = calc_readiness(merged, clarif)

    rt = profile["requirement_type"]
    type_str = rt.get("primary", "未识别")
    if rt.get("secondary"):
        type_str += f"（+ {rt['secondary']}）"

    header = [
        "# 需求评审报告\n",
        f"**评审日期：** {datetime.now().strftime('%Y-%m-%d')}",
        f"**需求类型：** {type_str}",
        f"**上会就绪度：** {emoji} {label}",
        f"**综合评分：** {score}/{max_score}\n",
        "---\n",
    ]

    sections = [
        render_section_1(profile),
        render_section_2(dashboard, score, max_score),
        render_section_3(emoji, label, reason, merged, clarif),
        render_section_4(merged, clarif),
        render_section_5(clarif),
        render_section_6_7_8(merged, clarif, profile),
    ]

    content = "\n".join(header + sections)
    Path(output_path).write_text(content, encoding="utf-8")

    print(f"✅ 报告生成完成：{output_path}")
    print(f"   上会就绪度：{emoji} {label}")
    print(f"   综合评分：{score}/{max_score}")
    print(f"   报告字数：{len(content)} 字符")


if __name__ == "__main__":
    if len(sys.argv) < 3:
        print("用法: python3 generate_report.py <extract_dir> <output_path>")
        sys.exit(1)
    generate_report(sys.argv[1], sys.argv[2])
