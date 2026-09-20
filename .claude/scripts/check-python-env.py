#!/usr/bin/env python3
"""harness 环境自检 — 为流水线选定唯一 Python 解释器

合格标准（三项全过才算 PASS）:
  1. 版本 >= 3.10（业务脚本使用 PEP 604 `X | Y` 类型标注，3.9 会 TypeError）
  2. pyexpat 真实解析通过（ET.fromstring）
  3. openpyxl xlsx 读写回环通过（写 Excel 不触发 expat、读才触发，
     仅 import openpyxl 查不出 expat 损坏）

输出: 候选明细表 + 末行 `PYTHON_CMD=<绝对路径>`（供 [INSTALL-RETURN] python_cmd 使用）
      --json 输出结构化结果
退出码: 0 存在合格解释器; 1 全部不合格（附安装指引）

自身约束: 纯标准库 + 3.6+ 语法，可被任意解释器引导（含 expat 损坏 / 3.9 环境），
探测在子进程中进行，单候选崩溃不影响整体
"""
import json
import os
import shutil
import subprocess
import sys

MIN_VERSION = (3, 10)

PROBE_PAYLOAD = """import sys, xml.etree.ElementTree as ET
assert sys.version_info >= (3, 10), 'version %d.%d < 3.10' % sys.version_info[:2]
ET.fromstring('<a/>')
from io import BytesIO
from openpyxl import Workbook, load_workbook
b = BytesIO()
Workbook().save(b)
b.seek(0)
load_workbook(b)
"""


def candidate_paths():
    """按优先级生成去重后的候选解释器路径（当前解释器最优先）"""
    names = [
        sys.executable,
        "python3", "python3.14", "python3.13", "python3.12", "python3.11", "python3.10",
    ]
    abs_paths = [
        "/opt/homebrew/bin/python3", "/opt/homebrew/bin/python3.14",
        "/opt/homebrew/bin/python3.13", "/opt/homebrew/bin/python3.12",
        "/usr/local/bin/python3", "/usr/local/bin/python3.12",
        os.path.expanduser("~/.local/bin/python3"), os.path.expanduser("~/.local/bin/python3.12"),
        "/usr/bin/python3",
    ]
    seen, found = set(), []
    for item in names + abs_paths:
        path = item if os.path.isabs(item) else shutil.which(item)
        if not path:
            continue
        real = os.path.realpath(path)
        if real in seen:
            continue
        seen.add(real)
        found.append(path)
    return found


def probe(path):
    """在子进程中探测单个解释器，返回 (ok, 失败原因, 版本串)"""
    try:
        ver = subprocess.run(
            [path, "--version"], capture_output=True, text=True, timeout=20
        ).stdout.strip()
        r = subprocess.run(
            [path, "-c", PROBE_PAYLOAD], capture_output=True, text=True, timeout=60
        )
    except Exception as exc:  # OSError / TimeoutExpired
        return False, "无法执行: %s" % exc, ""
    if r.returncode == 0:
        return True, "版本+pyexpat+openpyxl 全通过", ver
    lines = [ln for ln in (r.stderr or "").strip().splitlines() if ln.strip()]
    tail = lines[-1] if lines else "未知错误(退出码 %d)" % r.returncode
    if "< 3.10" in tail:
        reason = "版本过低(<3.10)"
    elif "expat" in tail:
        reason = "pyexpat 损坏(XML 解析不可用)"
    elif "No module named" in tail:
        reason = "缺少依赖: %s" % tail.split("No module named")[-1].strip(" '")
    else:
        reason = tail[:120]
    return False, reason, ver


def main():
    use_json = "--json" in sys.argv[1:]
    results, winner = [], None
    for path in candidate_paths():
        ok, reason, ver = probe(path)
        results.append({"path": path, "version": ver.replace("Python ", ""), "ok": ok, "reason": reason})
        if ok and winner is None:
            winner = path

    if use_json:
        print(json.dumps(
            {"python_cmd": winner or "", "min_version": ".".join(map(str, MIN_VERSION)),
             "candidates": results},
            ensure_ascii=False, indent=2,
        ))
    else:
        for it in results:
            print("[%s] %-52s %-9s %s" % ("PASS" if it["ok"] else "FAIL", it["path"], it["version"], it["reason"]))
        print()
        if winner:
            print("PYTHON_CMD=%s" % winner)
            print("自检通过: 版本>=3.10 + pyexpat 真实解析 + openpyxl xlsx 读写回环")
        else:
            print("PYTHON_CMD=")
            print("自检失败: 无合格解释器，安装指引:")
            print("  1) 安装 Python >= 3.10（推荐 3.12）: brew install python@3.12 或 pyenv install 3.12")
            print("  2) 为其安装 openpyxl: <上述python> -m pip install openpyxl（brew python 需加 --break-system-packages）")
            print("  3) 重新执行本脚本")
    sys.exit(0 if winner else 1)


if __name__ == "__main__":
    main()
