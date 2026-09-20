#!/usr/bin/env python3
"""
Summary — S8 汇总：文件覆盖率 + L1/L2 产出统计 + 不确定项聚合。

用法: python3 summary.py <project_root> [--log-dir <path>] [--output <path>]

输入:
  - knowledge/file-list.xlsx                    S2 全量文件清单
  - <log_dir>/s7-{domain}-file.json             S7 每域 AI 探索的文件列表
  - knowledge/domain/api/*.md + service/*.md    S7 产出的 L1/L2 文档

产出:
  - <log_dir>/summary.md   汇总报告（Markdown）

报告内容:
  - 文件覆盖率（已分析 / 全量）
  - 按文件分类的覆盖统计
  - 每域 L1/L2 文档数
  - 不确定项聚合（uncertainties）
  - 推荐后续动作
"""

import json
import os
import sys
from collections import defaultdict
from glob import glob

try:
    from openpyxl import load_workbook
except ImportError:
    print("错误: 需要安装 openpyxl — pip3 install openpyxl", file=sys.stderr)
    sys.exit(1)


def find_latest_log_dir(project_root: str) -> str | None:
    knowledge_dir = os.path.join(project_root, "knowledge")
    if not os.path.isdir(knowledge_dir):
        return None
    candidates = [d for d in glob(os.path.join(knowledge_dir, "domain-init-log-*"))
                  if os.path.isdir(d) and os.path.basename(d) != "domain-init-log-manual"]
    if not candidates:
        return None
    return max(candidates, key=os.path.getmtime)


def count_file_list(project_root: str) -> dict:
    path = os.path.join(project_root, "knowledge", "file-list.xlsx")
    if not os.path.exists(path):
        return {"total": 0, "by_type": {}, "exists": False}
    wb = load_workbook(path, read_only=True, data_only=True)
    ws = wb.active
    rows = list(ws.iter_rows(values_only=True))
    if not rows or len(rows) < 2:
        return {"total": 0, "by_type": {}, "exists": True}
    header = [str(c).strip() if c else "" for c in rows[0]]
    type_idx = header.index("文件分类") if "文件分类" in header else 2
    by_type = defaultdict(int)
    total = 0
    for row in rows[1:]:
        if not any(row):
            continue
        t = str(row[type_idx] or "未知").strip() if type_idx < len(row) else "未知"
        by_type[t] += 1
        total += 1
    return {"total": total, "by_type": dict(by_type), "exists": True}


def collect_s7(log_dir: str) -> tuple[list[dict], list[dict]]:
    files = []
    uncertainties = []
    seen = set()  # (domain, file_name) 去重，避免 functional_index 重复入口多计

    def _add(domain: str, fn: str):
        if not fn:
            return
        key = (domain, fn)
        if key not in seen:
            seen.add(key)
            files.append({"domain": domain, "file_name": fn})

    for f in sorted(glob(os.path.join(log_dir, "s7-*-file.json"))):
        try:
            with open(f, "r", encoding="utf-8") as fp:
                data = json.load(fp)
        except Exception:
            continue
        domain = data.get("domain", "")
        # 兼容旧 schema(fileList) + 新 schema(functional_index.entry_file)
        for fn in data.get("fileList", []):
            _add(domain, fn)
        for fi in data.get("functional_index", []):
            if isinstance(fi, dict):
                _add(domain, fi.get("entry_file"))
        for u in data.get("uncertainties", []):
            u.setdefault("domain", domain)
            uncertainties.append(u)

    return files, uncertainties


def count_docs(project_root: str) -> dict:
    api_dir = os.path.join(project_root, "knowledge", "domain", "api")
    svc_dir = os.path.join(project_root, "knowledge", "domain", "service")
    api = sorted(glob(os.path.join(api_dir, "*.md"))) if os.path.isdir(api_dir) else []
    svc = sorted(glob(os.path.join(svc_dir, "*.md"))) if os.path.isdir(svc_dir) else []
    return {"api": [os.path.basename(p) for p in api], "service": [os.path.basename(p) for p in svc]}


def render_markdown(file_stats, s7_files, s7_uncertainties, docs, log_dir) -> str:
    by_type_total = file_stats.get("by_type", {})
    # 覆盖率只算入口文件（Controller + Job + Consumer），非入口源码不计
    entry_total = (by_type_total.get("Controller", 0)
                   + by_type_total.get("Job", 0)
                   + by_type_total.get("Consumer", 0))
    analyzed = len(s7_files)  # collect_s7 已只收 functional_index.entry_file（入口）
    coverage = (analyzed / entry_total * 100) if entry_total else 0

    by_type_analyzed = defaultdict(int)
    analyzed_set = {f["file_name"] for f in s7_files}

    by_domain = defaultdict(int)
    for f in s7_files:
        by_domain[f["domain"]] += 1

    lines = []
    lines.append("# 领域知识初始化汇总报告\n")
    lines.append(f"日志目录: `{log_dir}`\n")

    lines.append("## 1. 入口文件覆盖率（Controller + Job + Consumer）\n")
    lines.append(f"- 入口文件全量: **{entry_total}**")
    lines.append(f"- 已抽取入口: **{analyzed}**")
    lines.append(f"- 覆盖率: **{coverage:.1f}%**\n")

    lines.append("## 2. 按文件分类统计\n")
    lines.append("| 文件分类 | 全量 | 覆盖率 |")
    lines.append("|------|------|--------|")
    if by_type_total:
        for t in sorted(by_type_total):
            lines.append(f"| {t} | {by_type_total[t]} | - |")
    else:
        lines.append("| (无 file-list.xlsx) | - | - |")
    lines.append("")

    lines.append("## 3. 每域文件归属（S7 产出）\n")
    if by_domain:
        lines.append("| 域 | 文件数 |")
        lines.append("|----|--------|")
        for d in sorted(by_domain):
            lines.append(f"| {d} | {by_domain[d]} |")
    else:
        lines.append("(未发现 s7-*-file.json)")
    lines.append("")

    lines.append("## 4. L1/L2 文档产出\n")
    lines.append(f"- L1 API 文档: **{len(docs['api'])}** 个")
    for n in docs["api"]:
        lines.append(f"  - {n}")
    lines.append(f"- L2 Service 文档: **{len(docs['service'])}** 个")
    for n in docs["service"]:
        lines.append(f"  - {n}")
    lines.append("")

    lines.append("## 5. 不确定项聚合\n")
    if s7_uncertainties:
        lines.append(f"共 **{len(s7_uncertainties)}** 条不确定项：\n")
        for u in s7_uncertainties:
            domain = u.get("domain", "")
            category = u.get("category", "")
            description = u.get("description", "") or u.get("reason", "")
            lines.append(f"- [{domain}] **{category}** — {description}")
    else:
        lines.append("无不确定项 ✅")
    lines.append("")

    lines.append("## 6. 推荐后续动作\n")
    actions = []
    if coverage < 80:
        actions.append(f"- ⚠ 覆盖率 {coverage:.1f}% 低于 80%，建议检查未分析文件（特别是 Entity/Mapper）")
    if s7_uncertainties:
        actions.append(f"- ⚠ {len(s7_uncertainties)} 条不确定项需人工确认")
    if not docs["api"] and not docs["service"]:
        actions.append("- ⚠ 未产出 L1/L2 文档，建议重跑 S7")
    if not actions:
        actions.append("- ✅ 全部完成，无需后续动作")
    for a in actions:
        lines.append(a)
    lines.append("")

    return "\n".join(lines)


def main():
    if len(sys.argv) < 2:
        print(__doc__, file=sys.stderr)
        sys.exit(1)

    project_root = os.path.abspath(sys.argv[1])
    log_dir = None
    output = None

    args = sys.argv[2:]
    i = 0
    while i < len(args):
        if args[i] == "--log-dir" and i + 1 < len(args):
            log_dir = args[i + 1]
            i += 2
        elif args[i] == "--output" and i + 1 < len(args):
            output = args[i + 1]
            i += 2
        else:
            print(f"未知参数: {args[i]}", file=sys.stderr)
            sys.exit(1)

    if not log_dir:
        log_dir = find_latest_log_dir(project_root)
    if not log_dir:
        print("错误: 未找到 domain-init-log-* 目录", file=sys.stderr)
        sys.exit(2)

    file_stats = count_file_list(project_root)
    s7_files, s7_uncertainties = collect_s7(log_dir)
    docs = count_docs(project_root)

    md = render_markdown(file_stats, s7_files, s7_uncertainties, docs, log_dir)

    output_path = output or os.path.join(log_dir, "summary.md")
    os.makedirs(os.path.dirname(output_path), exist_ok=True)
    with open(output_path, "w", encoding="utf-8") as f:
        f.write(md)

    by_type_total = file_stats.get("by_type", {})
    entry_total = (by_type_total.get("Controller", 0) + by_type_total.get("Job", 0) + by_type_total.get("Consumer", 0))
    coverage = (len(s7_files) / entry_total * 100) if entry_total else 0
    print(f"汇总报告: {output_path}")
    print(f"入口文件覆盖率(Controller+Job+Consumer): {coverage:.1f}% ({len(s7_files)}/{entry_total})")
    print(f"L1 文档: {len(docs['api'])} 个 | L2 文档: {len(docs['service'])} 个")
    print(f"不确定项: {len(s7_uncertainties)} 条")


if __name__ == "__main__":
    main()
