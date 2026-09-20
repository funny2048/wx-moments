#!/usr/bin/env python3
"""
DAO Generator Finder — 确定性探测项目中的 DAO 层代码生成工具（executor 步骤「DAO 层代码生成」专用）。

按优先级探测两种工具：
  1. code_generator : 源码里的 CodeGenerator 类（改表名后直接运行该 Java 类）
  2. lemon_jar      : lemon-generator.jar（java -jar <jar> <config> -t <表名>）

两者皆无 → mode=none（executor 唯一允许跳过生成的情形）。
探测结果固定由本脚本输出，executor 禁止用 Grep/自拟搜索代替。

用法: python3 find_dao_generator.py [project_root] [--json]

参数:
  project_root    项目根目录，默认当前目录
  --json          以 JSON 输出（agent 消费推荐）

退出码: 0=探测完成（含 mode=none）；1=project_root 不存在；2=参数错误
"""

import argparse
import json
import os
import re
import sys

# 确定性遍历：跳过构建产物/依赖/IDE 目录，目录按名排序保证多候选时顺序稳定
EXCLUDE_DIRS = {
    ".git", ".svn", ".idea", ".vscode", "target", "build", "out",
    "dist", "node_modules", "__pycache__", ".claude",
}

PACKAGE_RE = re.compile(r"^\s*package\s+([\w.]+)\s*;", re.MULTILINE)


def iter_project_files(root: str):
    for dirpath, dirnames, filenames in os.walk(root):
        dirnames[:] = sorted(d for d in dirnames if d not in EXCLUDE_DIRS)
        for name in sorted(filenames):
            yield os.path.join(dirpath, name)


def rel(root: str, path: str) -> str:
    return os.path.relpath(path, root)


def read_fqcn(java_path: str) -> str:
    """从 Java 文件头部读 package，拼出全限定类名；无 package 时退化为裸类名。"""
    try:
        with open(java_path, encoding="utf-8", errors="replace") as f:
            head = f.read(4096)
    except OSError:
        return "CodeGenerator"
    m = PACKAGE_RE.search(head)
    return f"{m.group(1)}.CodeGenerator" if m else "CodeGenerator"


def module_of(root: str, path: str) -> str:
    """相对路径首段即 Maven 模块；首段为 src 视为单模块工程（module 为空）。"""
    first = os.path.relpath(path, root).split(os.sep, 1)[0]
    return "" if first == "src" else first


def find_code_generators(root: str) -> list[dict]:
    return [
        {
            "path": rel(root, p),
            "fqcn": read_fqcn(p),
            "module": module_of(root, p),
        }
        for p in iter_project_files(root)
        if os.path.basename(p) == "CodeGenerator.java"
    ]


def find_lemon_jar(root: str) -> dict | None:
    for p in iter_project_files(root):
        if os.path.basename(p) != "lemon-generator.jar":
            continue
        config = os.path.join(os.path.dirname(p), "config.properties")
        return {
            "jar": rel(root, p),
            "config": rel(root, config) if os.path.exists(config) else "",
            "config_exists": os.path.exists(config),
        }
    return None


def detect(root: str) -> dict:
    candidates = find_code_generators(root)
    if candidates:
        return {"mode": "code_generator", "candidates": candidates}
    jar = find_lemon_jar(root)
    if jar:
        result = {"mode": "lemon_jar", **jar}
        if jar["config_exists"]:
            result["command"] = f"java -jar {jar['jar']} {jar['config']} -t <表名>"
        return result
    return {"mode": "none"}


def print_plain(result: dict):
    mode = result["mode"]
    print(f"mode={mode}")
    if mode == "code_generator":
        for i, c in enumerate(result["candidates"], 1):
            print(f"candidate_{i}_path={c['path']}")
            print(f"candidate_{i}_fqcn={c['fqcn']}")
            print(f"candidate_{i}_module={c['module']}")
    elif mode == "lemon_jar":
        print(f"jar={result['jar']}")
        print(f"config={result['config']}")
        print(f"config_exists={str(result['config_exists']).lower()}")
        if "command" in result:
            print(f"command={result['command']}")
        else:
            print("error=config_missing（config.properties 与 jar 不同目录，停下报告，禁止自建配置）")
    else:
        print("hint=未找到 CodeGenerator 类与 lemon-generator.jar，允许跳过工具生成，须在变更摘要记录")


def main():
    parser = argparse.ArgumentParser(description="确定性探测 DAO 层代码生成工具")
    parser.add_argument("project_root", nargs="?", default=".", help="项目根目录，默认当前目录")
    parser.add_argument("--json", action="store_true", help="以 JSON 输出")
    args = parser.parse_args()

    root = os.path.abspath(args.project_root)
    if not os.path.isdir(root):
        print(f"错误: 项目根目录不存在: {root}", file=sys.stderr)
        sys.exit(1)

    result = detect(root)
    if args.json:
        print(json.dumps(result, ensure_ascii=False, indent=2))
    else:
        print_plain(result)
    sys.exit(0)


if __name__ == "__main__":
    main()
