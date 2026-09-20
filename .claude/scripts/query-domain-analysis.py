#!/usr/bin/env python3
"""
Domain Analysis Query — 从 s4-domain-analysis.json 按条件查询域→文件映射（S5/S6/S7 输入）。

用法: python3 query-domain-analysis.py <project_root> [选项]

选项:
  --domain <id>             按域 ID 精确过滤。如: campaign
  --type <types>            按文件类型过滤，逗号分隔。如: Controller,Service
  --include-uncertain       包含不确定项（默认仅输出已归属）
  --packages                仅输出某域的包路径列表（去重）
  --limit <N>               限制输出条数
  --json                    以 JSON 格式输出

示例:
  python3 query-domain-analysis.py . --domain campaign --json
  python3 query-domain-analysis.py . --domain campaign --type Controller,Service
  python3 query-domain-analysis.py . --packages
  python3 query-domain-analysis.py . --include-uncertain
"""

import json
import os
import sys
from collections import defaultdict
from glob import glob


def find_latest_analysis(project_root: str) -> str | None:
    knowledge_dir = os.path.join(project_root, "knowledge")
    if not os.path.isdir(knowledge_dir):
        return None
    logs = sorted(glob(os.path.join(knowledge_dir, "domain-init-log-*", "s4-domain-analysis.json")))
    return logs[-1] if logs else None


def load_analysis(path: str) -> dict:
    with open(path, "r", encoding="utf-8") as f:
        return json.load(f)


def flatten_records(data: dict, include_uncertain: bool = False) -> list[dict]:
    records = []
    for d in data.get("candidate_domains", []):
        did = d.get("domain_id", "")
        dname = d.get("domain_name", "")
        for f in d.get("files", []):
            pkg = ".".join((f.get("file_name") or "").split(".")[:-1]) if "." in (f.get("file_name") or "") else ""
            records.append({
                "domain_id": did,
                "domain_name": dname,
                "file_name": f.get("file_name", ""),
                "file_type": f.get("file_type", ""),
                "responsibility_desc": f.get("responsibility_desc", ""),
                "package": pkg,
                "uncertain": False,
            })
    if include_uncertain:
        for u in data.get("uncertainties", []):
            records.append({
                "domain_id": "",
                "domain_name": "",
                "file_name": u.get("file_name", ""),
                "file_type": "",
                "responsibility_desc": u.get("reason", ""),
                "package": "",
                "uncertain": True,
                "candidate_domains": u.get("candidate_domains", []),
            })
    return records


def query(records, domain=None, types=None):
    results = []
    for r in records:
        if domain and r.get("domain_id") != domain:
            continue
        if types and r.get("file_type") not in types:
            continue
        results.append(r)
    return results


def print_table(records):
    by_domain = defaultdict(list)
    for r in records:
        key = r.get("domain_id") or "(未归属)"
        by_domain[key].append(r)
    if not by_domain:
        print("  (无匹配结果)")
        return
    for d in sorted(by_domain):
        items = by_domain[d]
        dname = items[0].get("domain_name", "") if d != "(未归属)" else ""
        header = f"--- {d}" + (f" ({dname})" if dname else "") + f" [{len(items)}] ---"
        print(f"\n{header}")
        by_type = defaultdict(list)
        for r in items:
            by_type[r.get("file_type") or "(未知)"].append(r)
        for t in sorted(by_type):
            print(f"  {t}:")
            for r in sorted(by_type[t], key=lambda x: x.get("file_name", "")):
                tag = " ⚠" if r.get("uncertain") else ""
                print(f"    - {r.get('file_name', '')}{tag}")


def print_packages(records, domain=None):
    pkgs = set()
    for r in records:
        if r.get("package"):
            pkgs.add(r["package"])
    if not pkgs:
        print("  (无包路径)")
        return
    label = f"域 {domain}" if domain else "全部"
    print(f"\n{label} 包路径 ({len(pkgs)}):")
    for p in sorted(pkgs):
        print(f"  - {p}")


def main():
    if len(sys.argv) < 2:
        print(__doc__, file=sys.stderr)
        sys.exit(1)

    project_root = os.path.abspath(sys.argv[1])
    domain = None
    types = None
    include_uncertain = False
    packages_only = False
    limit = None
    output_json = False

    args = sys.argv[2:]
    i = 0
    while i < len(args):
        if args[i] == "--domain" and i + 1 < len(args):
            domain = args[i + 1]
            i += 2
        elif args[i] == "--type" and i + 1 < len(args):
            types = [t.strip() for t in args[i + 1].split(",")]
            i += 2
        elif args[i] == "--include-uncertain":
            include_uncertain = True
            i += 1
        elif args[i] == "--packages":
            packages_only = True
            i += 1
        elif args[i] == "--limit" and i + 1 < len(args):
            limit = int(args[i + 1])
            i += 2
        elif args[i] == "--json":
            output_json = True
            i += 1
        else:
            print(f"未知参数: {args[i]}", file=sys.stderr)
            sys.exit(1)

    analysis_path = find_latest_analysis(project_root)
    if not analysis_path:
        print("错误: 未找到 s4-domain-analysis.json（请先执行 S4 领域识别）", file=sys.stderr)
        sys.exit(2)

    data = load_analysis(analysis_path)
    records = flatten_records(data, include_uncertain)
    results = query(records, domain, types)
    if limit:
        results = results[:limit]

    if packages_only:
        print_packages(results, domain)
        return

    if output_json:
        print(json.dumps(results, ensure_ascii=False, indent=2))
    else:
        src = f"来源: {os.path.relpath(analysis_path, project_root)}"
        print(f"查询结果: {len(results)} 条 | {src}" + (f" (限制 {limit})" if limit else ""))
        print("-" * 60)
        print_table(results)
        print("-" * 60)
        print(f"合计: {len(results)} 条")


if __name__ == "__main__":
    main()
