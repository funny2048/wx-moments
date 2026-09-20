#!/usr/bin/env python3
"""
合并 4 份角色审查 JSON，去重排序输出 merged-questions.json

用法：
    python3 merge_reviews.py <reviews_dir> <output_path>

输入：
    reviews_dir/
    ├── pd.json
    ├── tl.json
    ├── fd.json
    └── qa.json

输出：
    merged-questions.json
"""

import json
import sys
from pathlib import Path
from collections import defaultdict
from datetime import datetime

SEVERITY_ORDER = {"🔴必答": 0, "🟡建议": 1, "🟢可选": 2}
ROLE_FILES = ["pd.json", "tl.json", "fd.json", "qa.json"]


def load_reviews(reviews_dir: str) -> list:
    """加载 4 份 reviews JSON，合并所有 findings"""
    all_findings = []
    for role_file in ROLE_FILES:
        fp = Path(reviews_dir) / role_file
        if not fp.exists():
            print(f"⚠️  跳过缺失文件：{fp}", file=sys.stderr)
            continue
        data = json.loads(fp.read_text(encoding="utf-8"))
        role_name = data.get("role", role_file.replace(".json", ""))
        for f in data.get("findings", []):
            f["role"] = role_name
            f["_source_file"] = role_file
            all_findings.append(f)
    return all_findings


def merge_by_dimension(findings: list) -> tuple:
    """按 dimension 聚类，同 dimension 多角色时合并"""
    grouped = defaultdict(list)
    for f in findings:
        grouped[f["dimension"]].append(f)

    merged = []
    duplicates = 0
    for dim, items in grouped.items():
        if len(items) == 1:
            merged.append(items[0])
        else:
            duplicates += len(items) - 1
            merged.append(_merge_multi_findings(items))
    return merged, duplicates


def _merge_multi_findings(items: list) -> dict:
    """合并同 dimension 的多个 findings"""
    severities = [it["severity"] for it in items]
    max_severity = min(severities, key=lambda s: SEVERITY_ORDER[s])

    if all(it["question"] == items[0]["question"] for it in items):
        # 完全相同的问题，合并角色
        return {
            **items[0],
            "role": " / ".join(sorted({it["role"] for it in items})),
            "severity": max_severity,
        }
    else:
        # 不同问题，保留 questions 数组
        return {
            "dimension": items[0]["dimension"],
            "severity": max_severity,
            "role": " / ".join(sorted({it["role"] for it in items})),
            "questions": [it["question"] for it in items],
            "why": " | ".join(sorted({it.get("why", "") for it in items})),
            "success_criteria": items[0].get("success_criteria", ""),
            "evidence_from_doc": items[0].get("evidence_from_doc", ""),
            "_merged_from": [it["id"] for it in items],
        }


def sort_and_id(merged: list) -> list:
    """按 severity 排序，分配全局 ID"""
    merged.sort(key=lambda x: SEVERITY_ORDER.get(x["severity"], 99))
    for i, q in enumerate(merged, 1):
        q["id"] = f"Q{i:03d}"
    return merged


def count_by(items: list, key: str) -> dict:
    """按字段统计"""
    counter = defaultdict(int)
    for it in items:
        counter[it.get(key, "未知")] += 1
    return dict(counter)


def merge(reviews_dir: str, output_path: str) -> dict:
    """主函数"""
    all_findings = load_reviews(reviews_dir)
    if not all_findings:
        raise SystemExit("❌ 没有有效的 findings，请检查 reviews 目录")

    merged, duplicates = merge_by_dimension(all_findings)
    merged = sort_and_id(merged)

    output = {
        "generated_at": datetime.now().isoformat(timespec="seconds"),
        "stats": {
            "total_findings": len(merged),
            "raw_findings": len(all_findings),
            "duplicates_merged": duplicates,
            "by_severity": count_by(merged, "severity"),
            "by_role": count_by(all_findings, "role"),
        },
        "questions": merged,
    }

    Path(output_path).write_text(
        json.dumps(output, ensure_ascii=False, indent=2),
        encoding="utf-8"
    )

    print(f"✅ 合并完成：{len(merged)} 个问题（去重 {duplicates} 个）")
    print(f"   🔴 {output['stats']['by_severity'].get('🔴必答', 0)} 个")
    print(f"   🟡 {output['stats']['by_severity'].get('🟡建议', 0)} 个")
    print(f"   🟢 {output['stats']['by_severity'].get('🟢可选', 0)} 个")
    print(f"   输出：{output_path}")
    return output


if __name__ == "__main__":
    if len(sys.argv) < 3:
        print("用法: python3 merge_reviews.py <reviews_dir> <output_path>")
        sys.exit(1)
    merge(sys.argv[1], sys.argv[2])
