#!/usr/bin/env python3
"""
File List Generator — 扫描项目 Java/XML 文件，按注解和命名规则分类，产出领域分析 Excel。

用法: python3 generate-file-list.py <project_root> [output_dir]

产出:
  - <output_dir>/file-list.xlsx       人工审核用 Excel
  - <output_dir>/file-hash.json       文件指纹缓存

Excel 列: 文件名 | 文件后缀 | 文件分类
"""

import hashlib
import json
import os
import re
import sys
from datetime import datetime
from pathlib import Path

try:
    from openpyxl import Workbook
    from openpyxl.styles import Font, Alignment, PatternFill, Border, Side
    from openpyxl.utils import get_column_letter
except ImportError:
    print("错误: 需要安装 openpyxl — pip3 install openpyxl", file=sys.stderr)
    sys.exit(1)

# ── Excel 样式 ──

HEADERS = ["文件名", "文件后缀", "文件分类", "包路径", "代码行数"]
COL_WIDTHS = [45, 10, 15, 45, 10]

HEADER_FILL = PatternFill(start_color="4472C4", end_color="4472C4", fill_type="solid")
HEADER_FONT = Font(bold=True, size=11, color="FFFFFF")
THIN_BORDER = Border(
    left=Side(style="thin"), right=Side(style="thin"),
    top=Side(style="thin"), bottom=Side(style="thin"),
)
ALT_FILL = PatternFill(start_color="F2F2F2", end_color="F2F2F2", fill_type="solid")
CENTER = Alignment(horizontal="center", vertical="center")
LEFT_CENTER = Alignment(vertical="center")

# ── 分类规则 ──

TYPE_PRIORITY = [
    "Controller", "Job", "Consumer", "MapperJava", "ServiceImpl", "Entity",
]

# 注解一律带 \b 词边界 — 防止 @MapperScan 误归 MapperJava、@ComponentScan 误归 ServiceImpl、
# @ControllerAdvice 误归 Controller（funny-cloud 复盘：启动类 @MapperScan 被误归 MapperJava）
ANNOTATION_RULES = {
    "Controller": [r"@RestController\b", r"@Controller\b"],
    "Job": [r"@XxlJob\b", r"@JobRunner\b", r"@Scheduled\b"],
    "Consumer": [r"@RocketMQMessageListener\b", r"@RabbitListener\b", r"@KafkaListener\b"],
    "MapperJava": [r"@Mapper\b", r"@Repository\b"],
    "ServiceImpl": [r"@Service\b", r"@Component\b", r"@Extension\b"],
    "Entity": [r"@Table\b", r"@TableName\b", r"@Id\b"],
}

# 基础设施文件名模式 — 命中即 Other，不走注解匹配
INFRA_NAME_PATTERNS = [
    r".*Config(uration)?\.java$",
    r".*Generator\.java$",
    r".*Constants?\.java$",
]

# 工具类文件名模式 — 命中即 Util
UTIL_NAME_PATTERNS = [
    r".*Util\.java$",
    r".*Utils\.java$",
    r".*Helper\.java$",
]

# 测试类目录模式 — 命中即 Test（覆盖 Maven src/test/java 与约定 src/main/test）
TEST_PATH_PATTERNS = [
    r".*/test/.*\.java$",
]

# 脚手架/示例项目判定 — 包路径任一段 或 类名任一驼峰词命中即视为 demo（S2 预判 + S3 排除用）
# 口径须与 query-file-list.py 的 --exclude-demo 保持一致
DEMO_PACKAGE_SEGMENTS = {"demo", "example", "sample", "test"}


def camel_split(name: str) -> list[str]:
    """PascalCase 驼峰拆词: DemoController → ['Demo', 'Controller']; TestPayJob → ['Test', 'Pay', 'Job']"""
    return re.findall(r"[A-Z]+(?![a-z])|[A-Z][a-z0-9]*", name)


def is_demo_name(package: str, simple_name: str) -> bool:
    segs = {s.lower() for s in package.split(".") if s}
    words = {w.lower() for w in camel_split(simple_name)}
    return bool((segs | words) & DEMO_PACKAGE_SEGMENTS)

# 基础设施包路径模式 — 命中即 Other
INFRA_PACKAGE_PATTERNS = [
    r".*/config/.*\.java$",
]

DTO_PATTERNS = [
    r".*DTO\.java$", r".*Dto\.java$",
    r".*VO\.java$", r".*Vo\.java$",
    r".*Param\.java$",
    r".*Request\.java$", r".*Response\.java$",
    r".*Cmd\.java$", r".*Event\.java$",
    r".*Query\.java$",
    r".*Form\.java$",
    r".*Bo\.java$",
]

ENTITY_FILE_PATTERNS = [r".*Entity\.java$", r".*DO\.java$", r".*PO\.java$"]
ENTITY_PATH_PATTERNS = [
    r".*/entity/.*\.java$", r".*/model/.*\.java$",
    r".*/domain/.*\.java$", r".*/po/.*\.java$", r".*/do/.*\.java$",
]

MAPPER_PATH_PATTERNS = [r".*/mapper/.*\.java$", r".*/dao/.*\.java$"]

TYPE_ORDER = {
    "Entity": 0, "Controller": 1, "ServiceInterface": 2, "ServiceImpl": 3,
    "MapperJava": 4, "MapperXml": 5, "Consumer": 6, "Job": 7, "Dto": 8,
    "Test": 9, "Util": 10, "Other": 11,
}

# MyBatis / iBatis XML 识别
MYBATIS_DOCTYPE = r"mybatis\.org//DTD Mapper"
IBATIS_DOCTYPE = r"ibatis\.apache\.org//DTD SQL Map"

LANGUAGE_EXTENSIONS = {
    ".java": "Java",
    ".py": "Python",
    ".go": "Go",
    ".c": "C/C++",
    ".cpp": "C/C++",
    ".cc": "C/C++",
    ".cxx": "C/C++",
    ".h": "C/C++",
    ".hpp": "C/C++",
}

SKIP_DIRS = {
    ".git", "node_modules", "build", "target", "dist", "__pycache__",
    ".gradle", ".mvn", ".idea", "venv", ".venv", "vendor", "third_party",
    "bin", "out", ".settings",
}

# 敏感文件模式 — 命中即跳过 Excel/hash 输出，避免分享 Excel 时泄漏包路径与类名。
# 借鉴 graphify detect.py 的 _SENSITIVE_PATTERNS，针对 Java 项目调整。
SENSITIVE_PATTERNS = [
    re.compile(r'(^|[\\/])\.env(\.|$)', re.IGNORECASE),
    re.compile(r'(^|[\\/])application[-\w]*-prod\.ya?ml$', re.IGNORECASE),
    re.compile(r'(^|[\\/])application[-\w]*-secret\.ya?ml$', re.IGNORECASE),
    re.compile(r'\.(pem|key|p12|pfx|jks|cert|crt|der|p8)$', re.IGNORECASE),
    re.compile(r'(credential|secret|passwd|password|private_?key)', re.IGNORECASE),
    re.compile(r'(id_rsa|id_dsa|id_ecdsa|id_ed25519)(\.pub)?$'),
    re.compile(r'(\.netrc|\.pgpass|\.htpasswd)$', re.IGNORECASE),
    re.compile(r'(aws_credentials|gcloud_credentials|service\.account)', re.IGNORECASE),
]


def is_sensitive(filepath: str) -> bool:
    """Return True if this file likely contains secrets and should be skipped."""
    name = Path(filepath).name
    if any(p.search(name) for p in SENSITIVE_PATTERNS):
        return True
    # 完整路径也查一次（application-prod.yml 这类按路径匹配）
    return any(p.search(filepath) for p in SENSITIVE_PATTERNS)


# .harnessignore 支持 — 借鉴 graphify _load_graphifyignore
_HARNESSIGNORE_NAME = ".harnessignore"


def _load_harnessignore(root: Path) -> list[tuple[Path, str]]:
    """Read .harnessignore from root **and ancestor directories** up to .git boundary."""
    patterns: list[tuple[Path, str]] = []
    current = root.resolve()
    while True:
        ignore_file = current / _HARNESSIGNORE_NAME
        if ignore_file.exists():
            for line in ignore_file.read_text(encoding="utf-8", errors="ignore").splitlines():
                line = line.strip()
                if line and not line.startswith("#"):
                    patterns.append((current, line))
        if (current / ".git").exists():
            break
        parent = current.parent
        if parent == current:
            break
        current = parent
    return patterns


def _is_ignored(filepath: str, root: Path, patterns: list[tuple[Path, str]]) -> bool:
    """Return True if path matches any .harnessignore pattern."""
    if not patterns:
        return False
    import fnmatch as _fnmatch
    p_obj = Path(filepath)
    for anchor, pattern in patterns:
        pat = pattern.strip("/")
        if not pat:
            continue
        try:
            rel_to_root = str(p_obj.relative_to(root)).replace(os.sep, "/")
        except ValueError:
            rel_to_root = ""
        try:
            rel_to_anchor = str(p_obj.relative_to(anchor)).replace(os.sep, "/")
        except ValueError:
            rel_to_anchor = ""
        for rel in (rel_to_root, rel_to_anchor):
            if not rel:
                continue
            if _fnmatch.fnmatch(rel, pat):
                return True
            if _fnmatch.fnmatch(p_obj.name, pat):
                return True
            if any(_fnmatch.fnmatch(part, pat) for part in rel.split("/")):
                return True
    return False

# ── 分类逻辑 ──


def is_interface(content: str) -> bool:
    return bool(re.search(r"^\s*public\s+interface\s+", content, re.MULTILINE))


def has_service_in_name(filepath: str) -> bool:
    name = Path(filepath).stem
    return "Service" in name or "service" in name.lower()


def extract_package(content: str) -> str:
    m = re.search(r"^\s*package\s+([\w.]+)\s*;", content, re.MULTILINE)
    return m.group(1) if m else ""


def classify_java(filepath: str, content: str) -> str:
    # 测试类最高优先级 — test 目录下的 Java 一律算 Test
    for pattern in TEST_PATH_PATTERNS:
        if re.match(pattern, filepath):
            return "Test"
    # 启动类前置拦截 — 归 Other，防止 @MapperScan/@ComponentScan 等聚合注解误归 Mapper/Service
    if re.search(r"@SpringBootApplication\b", content):
        return "Other"
    # 基础设施前置拦截 — 文件名或包路径命中即 Other，不走注解匹配
    name = Path(filepath).name
    for pattern in INFRA_NAME_PATTERNS:
        if re.match(pattern, name):
            return "Other"
    # 工具类前置拦截 — util/utils/helper 命名归 Util
    for pattern in UTIL_NAME_PATTERNS:
        if re.match(pattern, name):
            return "Util"
    for pattern in INFRA_PACKAGE_PATTERNS:
        if re.match(pattern, filepath):
            return "Other"

    if is_interface(content) and has_service_in_name(filepath):
        return "ServiceInterface"
    if is_interface(content):
        for pattern in MAPPER_PATH_PATTERNS:
            if re.match(pattern, filepath):
                return "MapperJava"
    for ftype in TYPE_PRIORITY:
        for pattern in ANNOTATION_RULES.get(ftype, []):
            if re.search(pattern, content):
                return ftype
    name = Path(filepath).name
    for pattern in DTO_PATTERNS:
        if re.match(pattern, name):
            return "Dto"
    for pattern in ENTITY_FILE_PATTERNS:
        if re.match(pattern, name):
            return "Entity"
    for pattern in ENTITY_PATH_PATTERNS:
        if re.match(pattern, filepath):
            return "Entity"
    return "Other"


def classify_xml(content: str) -> str:
    if re.search(MYBATIS_DOCTYPE, content):
        return "MapperXml"
    if re.search(IBATIS_DOCTYPE, content):
        return "MapperXml"
    return ""  # 非 Mapper XML 不收录


# ── 扫描 ──


def scan_project(project_root: str) -> dict:
    """扫描 Java/XML 文件，分类并产出 records。

    返回:
      {
        "records": [...],            # 分类后的文件清单
        "skipped_sensitive": [...],  # 敏感文件路径列表（不进 Excel）
        "skipped_ignored": int,      # 被 .harnessignore 命中的文件数
      }
    """
    root = Path(project_root).resolve()
    records = []
    skipped_sensitive: list[str] = []
    skipped_ignored = 0

    ignore_patterns = _load_harnessignore(root)

    # 通用布局：放弃 src/main/java 硬路径假设，按扩展名 + SKIP_DIRS 过滤
    java_files: list[str] = []
    xml_files: list[str] = []
    for filepath in root.rglob("*"):
        if not filepath.is_file():
            continue
        parts = filepath.relative_to(root).parts
        if any(p in SKIP_DIRS for p in parts):
            continue
        if filepath.name.startswith("."):
            continue
        ext = filepath.suffix.lower()
        if ext == ".java":
            java_files.append(str(filepath))
        elif ext == ".xml":
            xml_files.append(str(filepath))

    # Java
    for filepath in sorted(set(java_files)):
        if _is_ignored(filepath, root, ignore_patterns):
            skipped_ignored += 1
            continue
        if is_sensitive(filepath):
            skipped_sensitive.append(filepath)
            continue
        try:
            content = Path(filepath).read_text(encoding="utf-8", errors="ignore")
        except Exception:
            continue
        ftype = classify_java(filepath, content)
        package = extract_package(content)
        stem = Path(filepath).stem
        fqn = f"{package}.{stem}" if package else stem
        file_hash = hashlib.md5(content.encode()).hexdigest()
        line_info = count_file_lines(content, "Java")
        records.append({
            "fqn": fqn,
            "package": package,
            "fileSuffix": "java",
            "fileType": ftype,
            "fileHash": file_hash,
            "lineCount": line_info["code"],
        })

    # XML（MyBatis / iBatis Mapper）
    for filepath in sorted(set(xml_files)):
        if _is_ignored(filepath, root, ignore_patterns):
            skipped_ignored += 1
            continue
        if is_sensitive(filepath):
            skipped_sensitive.append(filepath)
            continue
        try:
            content = Path(filepath).read_text(encoding="utf-8", errors="ignore")
        except Exception:
            continue
        ftype = classify_xml(content)
        if not ftype:
            continue
        stem = Path(filepath).stem
        file_hash = hashlib.md5(content.encode()).hexdigest()
        line_info = count_file_lines(content, "Java")
        # 契约: MapperXml 的 fqn 为文件名 stem、包路径恒为空 — XML 无包概念，不强行填值；
        # 下游需关联 Mapper 接口时按 stem 对齐 MapperJava 或读 XML 的 namespace 属性
        records.append({
            "fqn": stem,
            "package": "",
            "fileSuffix": "xml",
            "fileType": ftype,
            "fileHash": file_hash,
            "lineCount": line_info["code"],
        })

    records.sort(key=lambda r: (TYPE_ORDER.get(r["fileType"], 99), r["fqn"]))
    return {
        "records": records,
        "skipped_sensitive": skipped_sensitive,
        "skipped_ignored": skipped_ignored,
    }


# ── 代码行数统计 ──


def count_file_lines(content: str, lang: str) -> dict:
    lines = content.splitlines()
    total = len(lines)
    blank = sum(1 for l in lines if not l.strip())

    single_prefix = {"Java": "//", "Go": "//", "C/C++": "//", "Python": "#"}.get(lang, "")
    comment = 0
    in_block = False
    block_delim = None

    for line in lines:
        s = line.strip()
        if not s:
            continue
        if in_block:
            comment += 1
            if block_delim and block_delim in s:
                in_block = False
                block_delim = None
            continue
        # Python triple-quote docstrings
        if lang == "Python":
            matched = False
            for dq in ('"""', "'''"):
                if s.startswith(dq):
                    comment += 1
                    rest = s[len(dq):]
                    if dq not in rest or s == dq:
                        in_block = True
                        block_delim = dq
                    matched = True
                    break
            if matched:
                continue
        # Single-line comment
        if single_prefix and s.startswith(single_prefix):
            comment += 1
            continue
        # C-style block comment
        if lang != "Python" and s.startswith("/*"):
            comment += 1
            if "*/" not in s[2:]:
                in_block = True
                block_delim = "*/"
            continue

    code = total - blank - comment
    return {"total": total, "blank": blank, "comment": comment, "code": code}


def scan_project_lines(project_root: str) -> dict:
    root = Path(project_root).resolve()
    stats = {}

    for filepath in root.rglob("*"):
        if not filepath.is_file():
            continue
        parts = filepath.relative_to(root).parts
        if any(p in SKIP_DIRS for p in parts):
            continue
        ext = filepath.suffix.lower()
        lang = LANGUAGE_EXTENSIONS.get(ext)
        if not lang:
            continue
        try:
            content = filepath.read_text(encoding="utf-8", errors="ignore")
        except Exception:
            continue
        ls = count_file_lines(content, lang)
        if lang not in stats:
            stats[lang] = {"files": 0, "total": 0, "blank": 0, "comment": 0, "code": 0}
        s = stats[lang]
        s["files"] += 1
        for k in ("total", "blank", "comment", "code"):
            s[k] += ls[k]
    return stats


# ── Excel 生成 ──
def build_workbook(records: list[dict]) -> Workbook:
    wb = Workbook()
    ws = wb.active
    ws.title = "文件清单"

    for col_idx, header in enumerate(HEADERS, start=1):
        cell = ws.cell(row=1, column=col_idx, value=header)
        cell.font = HEADER_FONT
        cell.fill = HEADER_FILL
        cell.alignment = CENTER
        cell.border = THIN_BORDER

    for col_idx, width in enumerate(COL_WIDTHS, start=1):
        ws.column_dimensions[get_column_letter(col_idx)].width = width

    ws.freeze_panes = "A2"
    ws.auto_filter.ref = f"A1:{get_column_letter(len(HEADERS))}{len(records) + 1}"

    for row_idx, rec in enumerate(records, start=2):
        # 列顺序保持 [文件名, 文件后缀, 文件分类] 在前 3 列（向后兼容 query-file-list.py）
        # 新增列追加在末尾：包路径、代码行数
        values = [rec["fqn"], rec["fileSuffix"], rec["fileType"], rec.get("package", ""), rec.get("lineCount", 0)]
        for col_idx, val in enumerate(values, start=1):
            cell = ws.cell(row=row_idx, column=col_idx, value=val)
            cell.border = THIN_BORDER
            cell.alignment = CENTER if col_idx in (2, 3, 5) else LEFT_CENTER
        if row_idx % 2 == 0:
            for col_idx in range(1, len(HEADERS) + 1):
                ws.cell(row=row_idx, column=col_idx).fill = ALT_FILL

    return wb


# ── 主流程 ──


def main():
    if len(sys.argv) < 2:
        print("用法: python3 generate-file-list.py <project_root> [output_dir]", file=sys.stderr)
        sys.exit(1)

    project_root = sys.argv[1]
    output_dir = sys.argv[2] if len(sys.argv) > 2 else os.path.join(project_root, "knowledge")

    if not os.path.isdir(project_root):
        print(f"错误: 项目根目录不存在: {project_root}", file=sys.stderr)
        sys.exit(1)

    os.makedirs(output_dir, exist_ok=True)

    # ── 代码行数统计（先于 Excel，确保始终执行） ──
    line_stats = scan_project_lines(project_root)

    scan_result = scan_project(project_root)
    records = scan_result["records"]
    skipped_sensitive = scan_result["skipped_sensitive"]
    skipped_ignored = scan_result["skipped_ignored"]

    if not records:
        print("警告: 未扫描到任何 Java 文件", file=sys.stderr)
    else:
        output_path = os.path.join(output_dir, "file-list.xlsx")
        wb = build_workbook(records)
        wb.save(output_path)

        # 写出 file-hash.json
        hash_data = {
            "generateTime": datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
            "files": [
                {"fileName": r["fqn"], "fileHash": r["fileHash"]}
                for r in records
            ],
        }
        hash_path = os.path.join(output_dir, "file-hash.json")
        with open(hash_path, "w", encoding="utf-8") as f:
            json.dump(hash_data, f, ensure_ascii=False, indent=2)

        type_counts = {}
        for r in records:
            type_counts[r["fileType"]] = type_counts.get(r["fileType"], 0) + 1

        print("=" * 60)
        print("File List Generator — 扫描完成")
        print("=" * 60)
        print(f"项目根目录: {os.path.abspath(project_root)}")
        print(f"输出文件:   {os.path.abspath(output_path)}")
        print("-" * 60)
        print(f"{'类型':<20} {'文件数':>6}")
        print("-" * 60)
        total = 0
        for ftype in sorted(type_counts.keys(), key=lambda x: TYPE_ORDER.get(x, 99)):
            count = type_counts[ftype]
            print(f"{ftype:<20} {count:>6}")
            total += count
        print("-" * 60)
        print(f"{'合计':<20} {total:>6}")
        print("=" * 60)

    # 项目类型预判 — 入口类包路径 demo/example/sample/test 占比（供 S2 主 Agent 读取）
    entry_types = {"Controller", "Job", "Consumer"}
    entry_records = [r for r in records if r["fileType"] in entry_types and r["fileSuffix"] == "java"]
    if entry_records:
        demo_hits = [
            r for r in entry_records
            if is_demo_name(r.get("package", ""), r["fqn"].rsplit(".", 1)[-1])
        ]
        ratio = len(demo_hits) * 100 // len(entry_records)
        scaffold_hint = "yes" if ratio >= 80 else "no"
        print()
        print(f"[项目类型预判] 入口类包路径或类名命中 demo/example/sample/test: "
              f"{len(demo_hits)}/{len(entry_records)} = {ratio}%")
        print(f"SCAFFOLD_HINT={scaffold_hint}")
        if scaffold_hint == "yes":
            print("  疑似脚手架/示例项目，可跳过域抽取（S3-S7），由用户在 S2 确认")
    else:
        print("SCAFFOLD_HINT=no")

    # 敏感文件 / .harnessignore 跳过统计
    if skipped_sensitive:
        print()
        print(f"[安全] 跳过 {len(skipped_sensitive)} 个敏感文件（未输出到 Excel）：")
        for f in skipped_sensitive:
            print(f"  - {f}")
    if skipped_ignored > 0:
        print()
        print(f"[.harnessignore] 跳过 {skipped_ignored} 个文件（命中 .harnessignore 规则）")

    # ── 代码行数统计输出 ──
    if line_stats:
        print()
        print("=" * 70)
        print("代码行数统计")
        print("=" * 70)
        header = f"{'语言':<10} {'文件数':>8} {'总行数':>10} {'空行':>8} {'注释行':>10} {'有效代码行':>12}"
        print(header)
        print("-" * 70)
        sum_files = sum_total = sum_blank = sum_comment = sum_code = 0
        for lang in sorted(line_stats.keys()):
            s = line_stats[lang]
            print(f"{lang:<10} {s['files']:>8} {s['total']:>10} {s['blank']:>8} {s['comment']:>10} {s['code']:>12}")
            sum_files += s["files"]
            sum_total += s["total"]
            sum_blank += s["blank"]
            sum_comment += s["comment"]
            sum_code += s["code"]
        print("-" * 70)
        print(f"{'合计':<10} {sum_files:>8} {sum_total:>10} {sum_blank:>8} {sum_comment:>10} {sum_code:>12}")
        print("=" * 70)

        LOC_THRESHOLD = 50000
        print()
        if sum_code >= LOC_THRESHOLD:
            print(f"[建议] 有效代码总行数 {sum_code:,} >= {LOC_THRESHOLD:,}，建议使用【半自动抽取流程】（分批次人工确认）")
        else:
            print(f"[建议] 有效代码总行数 {sum_code:,} < {LOC_THRESHOLD:,}，建议使用【全自动抽取流程】")


if __name__ == "__main__":
    main()
