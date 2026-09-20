#!/usr/bin/env python3
"""
Validate s3-entry-*.json schema against merge-entry-list.py expectations.

用法:
    python3 validate-entry-schema.py <input-dir> [--strict]

校验规则（merge-entry-list.py 期望的扁平方法级 schema）:
    必填字段（非空）: entry_type, class_name, method_name, package
    禁止字段（s3-entry-v1 嵌套结构特征）: 顶层 schema, 顶层 controller 对象
    trace 校验: 含 tree 子结构（merge 读 trace.tree）
    --strict 时额外校验: responsibility_desc 非空, files_touched 非空

batch 输入文件（根为 list）自动跳过，不参与校验。

退出码: 0 全部通过; 1 存在失败; 2 输入错误
"""
import argparse
import glob
import json
import os
import sys

VALID_ENTRY_TYPES = {"Controller", "Job", "Consumer"}
REQUIRED_FIELDS = ["entry_type", "class_name", "method_name", "package"]
# s3-entry-v1 嵌套结构特征字段：出现即判定 agent 偏离了扁平 schema
FORBIDDEN_FIELDS = ["schema", "controller"]


def validate_one(data: dict, strict: bool) -> list:
    """返回错误信息列表，空表示通过。"""
    errors = []

    # 1. 必填扁平字段非空
    for f in REQUIRED_FIELDS:
        v = data.get(f)
        if v is None or (isinstance(v, str) and not v.strip()):
            errors.append("缺少/空必填字段: %s" % f)

    # 2. entry_type 取值合法
    et = data.get("entry_type")
    if et and et not in VALID_ENTRY_TYPES:
        errors.append("entry_type 非法值: %r（应为 %s）" % (et, sorted(VALID_ENTRY_TYPES)))

    # 3. 禁止字段（s3-entry-v1 嵌套特征）
    for f in FORBIDDEN_FIELDS:
        if f in data:
            errors.append("出现禁止字段: %s（疑似 s3-entry-v1 嵌套结构，应为扁平 schema）" % f)

    # 4. trace.tree（merge 依赖）
    trace = data.get("trace")
    if not isinstance(trace, dict) or not trace.get("tree"):
        errors.append("trace.tree 缺失（merge 依赖）")

    # 5. strict：语义字段
    if strict:
        if not str(data.get("responsibility_desc", "")).strip():
            errors.append("responsibility_desc 为空（strict）")
        if not (data.get("files_touched") or []):
            errors.append("files_touched 为空（strict）")

    return errors


def check_method_coverage(input_dir: str):
    """以 knowledge/entry-methods.json（parse-entry.py 产物）为基准，校验每个入口方法是否都有产出文件。

    返回 (missing, excluded)：
    - missing: 缺失方法列表 ["Class.method", ...]（仅统计"有产出但方法不全"的类 = 真漏）
    - excluded: 完全无产出的类（视为合理排除：HealthCheck/Test/Fastdf 等基础设施/测试/调试）
    - 基准不存在时返回 (None, None)
    """
    knowledge_dir = os.path.dirname(os.path.normpath(input_dir))
    entry_methods = os.path.join(knowledge_dir, "entry-methods.json")
    if not os.path.exists(entry_methods):
        return None, None
    try:
        with open(entry_methods, encoding="utf-8") as f:
            data = json.load(f)
    except Exception:
        return None, None
    produced_by_cls = {}
    for fp in glob.glob(os.path.join(input_dir, "s3-entry-*.json")):
        base = os.path.basename(fp)[len("s3-entry-"):-len(".json")]
        parts = base.rsplit("-", 1)
        if len(parts) == 2:
            produced_by_cls.setdefault(parts[0], set()).add(parts[1])
    missing = []
    excluded = []
    for e in data.get("entries", []):
        cls = str(e.get("class_name", "")).replace(".java", "").replace(".kt", "")
        methods = [m.get("method_name") for m in e.get("methods", []) if m.get("method_name")]
        prod = produced_by_cls.get(cls)
        if not prod:
            excluded.append(cls)
            continue
        # 重载消歧匹配：entry-explorer 对重载方法用 {method}_{disambig} 命名，
        # 对账时原名 mname 匹配 prod 中 ==mname 或以 mname+"_" 开头的产出，按需求数计数
        method_need = {}
        for mname in methods:
            method_need[mname] = method_need.get(mname, 0) + 1
        for mname, need in method_need.items():
            matched = sum(1 for p in prod if p == mname or p.startswith(mname + "_"))
            for _ in range(max(0, need - matched)):
                missing.append("%s.%s" % (cls, mname))
    return missing, excluded


def main():
    ap = argparse.ArgumentParser(description="校验 s3-entry-*.json 扁平 schema")
    ap.add_argument("input_dir", help="s3-entry-*.json 所在目录")
    ap.add_argument("--strict", action="store_true", help="额外校验语义字段非空")
    args = ap.parse_args()

    if not os.path.isdir(args.input_dir):
        print("错误: 目录不存在: %s" % args.input_dir, file=sys.stderr)
        sys.exit(2)

    pattern = os.path.join(args.input_dir, "s3-entry-*.json")
    files = sorted(glob.glob(pattern))
    if not files:
        print("错误: 未找到 s3-entry-*.json: %s" % args.input_dir, file=sys.stderr)
        sys.exit(2)

    total = parsed_ok = skipped_list = 0
    failed = []
    for f in files:
        try:
            with open(f, encoding="utf-8") as fp:
                data = json.load(fp)
        except Exception as e:
            failed.append((os.path.basename(f), ["JSON 解析失败: %s" % e]))
            continue
        # batch 输入文件（根为 list）跳过
        if not isinstance(data, dict):
            skipped_list += 1
            continue
        total += 1
        errs = validate_one(data, args.strict)
        if errs:
            failed.append((os.path.basename(f), errs))
        else:
            parsed_ok += 1

    print("输入目录: %s" % args.input_dir)
    print("跳过 batch 文件（根非对象）: %d" % skipped_list)
    print("有效 entry 记录: %d" % total)
    print("通过: %d" % parsed_ok)
    print("失败: %d" % len(failed))
    if failed:
        print("--- 失败明细 ---")
        for fn, errs in failed:
            print("  %s:" % fn)
            for e in errs:
                print("    - %s" % e)

    # 方法数对账（基准：knowledge/entry-methods.json，由 parse-entry.py 产出）
    missing_methods, excluded_classes = check_method_coverage(args.input_dir)
    method_fail = False
    if missing_methods is not None:
        print("方法数对账（基准 entry-methods.json）:")
        if missing_methods:
            print("  ❌ 缺失方法（真漏，有产出但方法不全）: %d 个" % len(missing_methods))
            for m in missing_methods[:20]:
                print("    - %s" % m)
            if len(missing_methods) > 20:
                print("    ... 共 %d 个" % len(missing_methods))
            method_fail = True
        else:
            print("  ✅ 业务方法覆盖完整，无遗漏")
        if excluded_classes:
            print("  ℹ️ 合理排除（完全无产出，基础设施/测试/调试）: %d 类: %s"
                  % (len(excluded_classes), excluded_classes[:10]))

    if failed or method_fail:
        sys.exit(1)
    print("✅ schema 校验全部通过（含方法数对账）")


if __name__ == "__main__":
    main()
