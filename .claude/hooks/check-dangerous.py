#!/usr/bin/env python3
"""危险操作检测钩子 (PreToolUse) — 在执行 Bash 命令前检测并拦截危险操作。"""

import sys
import json
import re

DANGEROUS_PATTERNS = [
    ("rm -rf", "删除目录及其内容"),
    ("rm -fr", "删除目录及其内容"),
    ("dd if=", "磁盘写入操作"),
    ("mkfs", "格式化磁盘"),
    ("shutdown", "关机命令"),
    ("reboot", "重启命令"),
    ("init 0", "关机命令"),
    ("init 6", "重启命令"),
    (":(){ :|:& };:", "Fork 炸弹"),
    ("> /dev/sd", "直接写入磁盘设备"),
    ("chmod -R 777", "递归修改权限为 777"),
    ("chown -R.*root", "递归修改所有者为 root"),
    ("git push.*--force", "强制推送"),
    ("git push.*-f", "强制推送"),
    ("DROP DATABASE", "删除数据库"),
    ("DROP TABLE", "删除表"),
    ("TRUNCATE", "清空表数据"),
    ("DELETE FROM", "删除数据"),
]


def main():
    raw = sys.stdin.read()
    if not raw.strip():
        print("{}")
        return

    try:
        data = json.loads(raw)
    except json.JSONDecodeError:
        print("{}")
        return

    tool_name = data.get("tool_name", "")
    command = data.get("tool_input", {}).get("command", "")

    if tool_name != "Bash" or not command:
        print("{}")
        return

    for pattern, desc in DANGEROUS_PATTERNS:
        if re.search(pattern, command, re.IGNORECASE):
            msg = (
                f"⚠️ 危险操作已被拦截: {desc}\n"
                f"命令: {command}\n"
                f"如需执行，请手动在终端运行"
            )
            print(json.dumps({"block": True, "message": msg}, ensure_ascii=False), flush=True)
            # exit 2 = 拒绝操作，将错误信息反馈给 Claude 让其选择更安全的替代方案
            sys.exit(2)

    print("{}")


if __name__ == "__main__":
    main()
