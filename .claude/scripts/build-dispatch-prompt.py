#!/usr/bin/env python3
"""
Dispatch Prompt Builder — S7 Worker prompt 确定性生成器

消除"主 Agent 盲填 dispatch-protocol.md 模板变量"的设计矛盾：
主 Agent 不读模板、不手工拼装，执行本脚本，stdout 输出整段
可直接作为 Domain Extractor L12 Worker 的启动 prompt。

用法: python3 build-dispatch-prompt.py <project_root> --domain <id> --log-dir <path> [选项]

选项:
  --template <path>   覆盖模板位置。默认依次查找:
                      ~/.claude/skills/domain-harness-init/refer/dispatch-protocol.md
                      ~/.harness/meta.json repoPath 下同名文件

数据源:
  - knowledge/domain-init-log-*/s4-domain-analysis.json   域→文件归属（7 类分组）
  - knowledge/entry-methods.json                          入口方法清单（entry_methods_total 对账基准）

退出码: 0 成功; 1 参数/数据错误（域不存在、s4 缺失、模板缺失）
"""

import json
import os
import sys
from glob import glob

# s4 file_type → 模板 7 类列表变量的映射（口径与 query-file-list.py FILE_TYPES 一致）
TYPE_GROUPS = {
    "controllers_list": ("Controller",),
    "jobs_list": ("Job",),
    "consumers_list": ("Consumer",),
    "dtos_list": ("Dto",),
    "services_list": ("ServiceInterface", "ServiceImpl"),
    "mappers_list": ("MapperJava", "MapperXml"),
    "entities_list": ("Entity",),
}

TEMPLATE_DEFAULT = os.path.join(
    os.path.expanduser("~"), ".claude", "skills",
    "domain-harness-init", "refer", "dispatch-protocol.md",
)


def find_latest_analysis(project_root):
    logs = sorted(glob(os.path.join(
        project_root, "knowledge", "domain-init-log-*", "s4-domain-analysis.json")))
    return logs[-1] if logs else None


def locate_template(explicit):
    candidates = []
    if explicit:
        candidates.append(explicit)
    candidates.append(TEMPLATE_DEFAULT)
    meta = os.path.join(os.path.expanduser("~"), ".harness", "meta.json")
    if os.path.isfile(meta):
        try:
            with open(meta, encoding="utf-8") as f:
                repo = json.load(f).get("repoPath", "")
            if repo:
                candidates.append(os.path.join(
                    repo, "skills", "domain-harness-init", "refer", "dispatch-protocol.md"))
        except Exception:
            pass
    for c in candidates:
        if c and os.path.isfile(c):
            return c
    return None


def extract_template_block(text):
    """取 md 中第一个 ``` 围栏内的模板正文"""
    lines = text.splitlines()
    inside, buf = False, []
    for ln in lines:
        if not inside and ln.strip().startswith("```"):
            inside = True
            continue
        if inside and ln.strip().startswith("```"):
            break
        if inside:
            buf.append(ln)
    return "\n".join(buf)


def load_domain_records(data, domain):
    records, available = [], []
    for d in data.get("candidate_domains", []):
        did = d.get("domain_id", "")
        available.append(did)
        if did != domain:
            continue
        for f in d.get("files", []):
            records.append({
                "file_name": f.get("file_name", ""),
                "file_type": f.get("file_type", ""),
            })
    return records, available


def simple_class_name(file_name):
    """FQN 或 Xxx.java → 类名（用于匹配 entry-methods.json 的 class_name）"""
    last = file_name.split(".")[-1] if "." in file_name else file_name
    return last[:-5] if last.endswith(".java") else last


def entry_methods_total(project_root, entry_class_names):
    """按域内入口类名聚合 entry-methods.json 方法总数（Worker 对账基准）；缺文件返回 None"""
    path = os.path.join(project_root, "knowledge", "entry-methods.json")
    if not os.path.isfile(path):
        return None
    try:
        with open(path, encoding="utf-8") as f:
            entries = json.load(f)
    except Exception:
        return None
    wanted = set(entry_class_names)
    total = 0
    for e in entries:
        if e.get("class_name") in wanted:
            total += len(e.get("methods", []) or [])
    return total


def render_list(items):
    return "\n".join("- %s" % f for f in sorted(items)) if items else "(无)"


def main():
    args = sys.argv[1:]
    if not args:
        print(__doc__, file=sys.stderr)
        sys.exit(1)
    project_root = os.path.abspath(args[0])
    domain, log_dir, template_arg = None, None, None
    i = 1
    while i < len(args):
        if args[i] == "--domain" and i + 1 < len(args):
            domain = args[i + 1]
            i += 2
        elif args[i] == "--log-dir" and i + 1 < len(args):
            log_dir = args[i + 1]
            i += 2
        elif args[i] == "--template" and i + 1 < len(args):
            template_arg = args[i + 1]
            i += 2
        else:
            print("未知参数: %s" % args[i], file=sys.stderr)
            sys.exit(1)
    if not domain or not log_dir:
        print("错误: --domain 与 --log-dir 必填", file=sys.stderr)
        sys.exit(1)

    analysis_path = find_latest_analysis(project_root)
    if not analysis_path:
        print("错误: 未找到 s4-domain-analysis.json（请先执行 S4）", file=sys.stderr)
        sys.exit(1)
    with open(analysis_path, encoding="utf-8") as f:
        data = json.load(f)
    records, available = load_domain_records(data, domain)
    if not records:
        print("错误: 域 '%s' 不存在或无文件。可用域: %s" % (domain, ", ".join(available)), file=sys.stderr)
        sys.exit(1)

    groups = {k: [] for k in TYPE_GROUPS}
    unmatched = []
    for r in records:
        for var, types in TYPE_GROUPS.items():
            if r["file_type"] in types:
                groups[var].append(r["file_name"])
                break
        else:
            unmatched.append("%s(%s)" % (r["file_name"], r["file_type"] or "?"))
    if unmatched:
        print("[提示] %d 个文件类型未落入 7 类分组，未渲染: %s"
              % (len(unmatched), ", ".join(unmatched)), file=sys.stderr)

    entry_classes = [
        simple_class_name(f)
        for f in groups["controllers_list"] + groups["jobs_list"] + groups["consumers_list"]
    ]
    total = entry_methods_total(project_root, entry_classes)
    if total is None:
        print("[警告] entry-methods.json 缺失或不可读，entry_methods_total 输出 null"
              "（Worker 对账降级，须记 uncertainties）", file=sys.stderr)
    print("[提示] s4 产物无 domain_type 字段，默认 core（支撑域由 Worker 按 l12 规范自校验）",
          file=sys.stderr)

    template_path = locate_template(template_arg)
    if not template_path:
        print("错误: 未找到 dispatch-protocol.md 模板（可用 --template 指定）", file=sys.stderr)
        sys.exit(1)
    with open(template_path, encoding="utf-8") as f:
        block = extract_template_block(f.read())
    if not block:
        print("错误: 模板文件无 ``` 围栏正文: %s" % template_path, file=sys.stderr)
        sys.exit(1)

    # 逐变量替换；长占位符在前（{domain_type} 必须先于 {domain}，防前缀误替换）
    repl = {
        "{domain_type}": "core",
        "{domain}": domain,
        "{log_dir}": log_dir,
        "{N}": str(total) if total is not None else "null",
    }
    for var in TYPE_GROUPS:
        repl["{%s}" % var] = render_list(groups[var])
    out = block
    for k, v in repl.items():
        out = out.replace(k, v)
    print(out)

    counts = {var: len(groups[var]) for var in TYPE_GROUPS}
    print("[summary] domain=%s files=%d %s entry_methods_total=%s"
          % (domain, len(records), " ".join("%s=%d" % kv for kv in counts.items()),
             total if total is not None else "null"), file=sys.stderr)
    sys.exit(0)


if __name__ == "__main__":
    main()
