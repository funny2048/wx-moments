#!/usr/bin/env python3
"""
Split Entry List — 将入口文件 JSON 拆分为批次文件（query-file-list.py 产出）。

用法: python3 scripts/split-entry-list.py <input.json> --batch-size <N> --output-dir <dir>

选项:
  --batch-size <N>    每批入口文件数，默认 5
  --output-dir <dir>  批次文件输出目录，默认当前目录

输入: query-file-list.py --json 的输出文件路径
输出: <dir>/s3-entry-batch-1.json, s3-entry-batch-2.json, ...

示例:
  python3 scripts/query-file-list.py . --type Controller,Job,Consumer --json > /tmp/entries.json
  python3 scripts/split-entry-list.py /tmp/entries.json --batch-size 10 --output-dir knowledge/domain-init-log-xxx
"""

import json
import os
import sys


def main():
    if len(sys.argv) < 2:
        print(__doc__, file=sys.stderr)
        sys.exit(1)

    input_path = os.path.abspath(sys.argv[1])
    batch_size = 5
    output_dir = "."

    args = sys.argv[2:]
    i = 0
    while i < len(args):
        if args[i] == "--batch-size" and i + 1 < len(args):
            batch_size = int(args[i + 1])
            i += 2
        elif args[i] == "--output-dir" and i + 1 < len(args):
            output_dir = args[i + 1]
            i += 2
        else:
            print(f"未知参数: {args[i]}", file=sys.stderr)
            sys.exit(1)

    with open(input_path, "r", encoding="utf-8") as f:
        entries = json.load(f)

    os.makedirs(output_dir, exist_ok=True)

    batches = [entries[i:i + batch_size] for i in range(0, len(entries), batch_size)]
    batch_files = []
    for idx, batch in enumerate(batches, 1):
        batch_path = os.path.join(output_dir, f"s3-entry-batch-{idx}.json")
        with open(batch_path, "w", encoding="utf-8") as f:
            json.dump(batch, f, ensure_ascii=False, indent=2)
        batch_files.append(batch_path)

    print(json.dumps({
        "total_entries": len(entries),
        "batch_size": batch_size,
        "total_batches": len(batches),
        "batch_files": batch_files,
    }, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
