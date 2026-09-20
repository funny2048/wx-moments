#!/usr/bin/env python3
"""
Init Log Dir — 创建本次领域抽取流水线的统一日志目录。

格式: knowledge/domain-init-log-YYYYMMDD-HHMMSS/（无横线、带秒，字典序=时间序）。
整个 S2-S8 流水线共用同一个 log_dir，由主 Agent 在 S2 步骤0 调用本脚本创建。
幂等：同一秒内重复调用复用已存在目录（os.makedirs exist_ok=True）。

用法:
    python3 init-log-dir.py <project_root>
输出:
    打印 log_dir 绝对路径，供主 Agent 用 $(...) 捕获后全流水线复用。
"""
import os
import sys
import time


def main() -> None:
    if len(sys.argv) < 2:
        print("用法: python3 init-log-dir.py <project_root>", file=sys.stderr)
        sys.exit(1)

    project_root = os.path.abspath(sys.argv[1])
    knowledge_dir = os.path.join(project_root, "knowledge")
    os.makedirs(knowledge_dir, exist_ok=True)

    timestamp = time.strftime("%Y%m%d-%H%M%S")
    log_dir = os.path.join(knowledge_dir, f"domain-init-log-{timestamp}")
    os.makedirs(log_dir, exist_ok=True)

    print(log_dir)


if __name__ == "__main__":
    main()
