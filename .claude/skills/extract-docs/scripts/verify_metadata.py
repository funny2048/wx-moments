#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
verify_metadata.py — extract-docs 防假知识门禁

用法:
    python3 .claude/skills/extract-docs/scripts/verify_metadata.py knowledge/_manual/<域>/<短英文名>.json
    加 --skip-md 可在 md 尚未生成时只预检 json 事实

退出码: 0 = 通过(允许 SKIP/WARN) / 1 = 存在 FAIL，禁止交付

校验分级:
    FAIL  有验证手段但查无实据(疑似编造) / 结构缺失 / md 违规
    SKIP  无验证手段(fragments 缺分片、projects.json 缺失) —— 需人工补证或在文档声明
    WARN  非阻断提醒(如新域未登记 domains.json)

跨平台: 源码根一律由 knowledge/projects.json 的 path+dir 解析，禁止硬编码盘符；
        文本搜索 rg -> grep -> python 纯遍历三级降级，缺谁都能跑。
"""
import argparse
import fnmatch
import json
import os
import re
import subprocess
import sys

RESULTS = []

SKIP_DIRS = {'.git', 'node_modules', 'target', 'dist', '.idea', 'graphify-out'}
INCLUDES = ('*.java', '*.xml', '*.yml', '*.yaml', '*.properties', '*.vue', '*.ts', '*.js')
COUNTER_RE = re.compile(
    r'API\s*(\d+)\s*·\s*Job\s*(\d+)\s*·\s*表\s*(\d+)\s*·\s*Redis\s*(\d+)\s*·\s*Apollo\s*(\d+)\s*·\s*MQ\s*(\d+)')


def rec(level, cat, msg):
    RESULTS.append((level, cat, msg))


def find_workspace_root(start):
    d = os.path.abspath(start)
    for _ in range(8):
        if os.path.isfile(os.path.join(d, 'knowledge', 'manifest.json')):
            return d
        nd = os.path.dirname(d)
        if nd == d:
            return None
        d = nd
    return None


def jload(path):
    with open(path, 'r', encoding='utf-8') as f:
        return json.load(f)


def _match_any(name):
    return any(fnmatch.fnmatch(name, pat) for pat in INCLUDES)


def _iter_files(roots):
    for root in roots:
        for dirpath, dirs, files in os.walk(root):
            dirs[:] = [x for x in dirs if x not in SKIP_DIRS]
            for fn in files:
                if _match_any(fn):
                    yield os.path.join(dirpath, fn)


def grep_literal(pattern, roots):
    """字面子串搜索。True=命中 False=未命中 None=无可用搜索工具且遍历失败。"""
    if not roots:
        return None
    for builder in (_rg_cmd, _grep_cmd):
        cmd = builder(pattern, roots)
        if cmd is None:
            continue
        try:
            p = subprocess.run(cmd, capture_output=True, text=True, errors='ignore')
            if p.returncode in (0, 1):  # 0=命中 1=未命中；>=2=工具报错，换下一个
                return bool((p.stdout or '').strip())
            continue
        except (FileNotFoundError, OSError):
            continue
    # python 兜底
    try:
        for f in _iter_files(roots):
            try:
                with open(f, 'r', encoding='utf-8', errors='ignore') as fh:
                    if pattern in fh.read():
                        return True
            except OSError:
                continue
        return False
    except Exception:
        return None


def _rg_cmd(pattern, roots):
    cmd = ['rg', '-l', '-F', '--no-messages']
    for inc in INCLUDES:
        cmd += ['-g', inc]
    return cmd + [pattern] + list(roots)


def _grep_cmd(pattern, roots):
    cmd = ['grep', '-rlFs']
    for inc in INCLUDES:
        cmd += ['--include', inc]
    return cmd + ['--', pattern] + list(roots)


def find_source_file(roots, filename):
    for f in _iter_files(roots):
        if os.path.basename(f) == filename:
            return f
    return None


def read_text(path):
    try:
        with open(path, 'r', encoding='utf-8', errors='ignore') as fh:
            return fh.read()
    except OSError:
        return ''


def app_source_root_map(ws):
    """projects.json -> {key: 源码根}。文件缺失/解析失败返回 None 并记 SKIP。"""
    pj = os.path.join(ws, 'knowledge', 'projects.json')
    if not os.path.isfile(pj):
        rec('SKIP', 'env', 'knowledge/projects.json 缺失，源码兜底校验全部跳过（请按 projects.example.json 补齐）')
        return None
    try:
        return {p.get('key'): os.path.join(p.get('path', ''), p.get('dir', ''))
                for p in jload(pj).get('projects', [])}
    except Exception as e:
        rec('SKIP', 'env', 'projects.json 解析失败: %s' % e)
        return None


def check_verdict(cat, ok_label, miss_label, means, found):
    """means=是否有验证手段; found=是否命中。"""
    if found:
        rec('PASS', cat, ok_label)
    elif not means:
        rec('SKIP', cat, miss_label + '（无验证手段，需人工补证）')
    else:
        rec('FAIL', cat, miss_label + '（疑似编造，删掉或补真实证据）')


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('metadata', help='模块元数据 json 路径')
    ap.add_argument('--skip-md', action='store_true', help='md 未生成时的 json 预检')
    args = ap.parse_args()

    try:
        meta = jload(args.metadata)
    except Exception as e:
        print('FAIL 无法解析 %s: %s' % (args.metadata, e))
        sys.exit(1)

    ws = find_workspace_root(args.metadata) or find_workspace_root(os.getcwd())
    if not ws:
        print('FAIL 找不到工作区根（需存在 knowledge/manifest.json）')
        sys.exit(1)

    # ---- 1. 结构与 evidence ----
    for k in ('module', 'domain', 'doc', 'apps', 'mq', 'third_party_apis',
              'entries', 'redis_keys', 'apollo_keys', 'tables'):
        if k not in meta:
            rec('FAIL', 'schema', '缺顶层字段 %s' % k)
    entries = meta.get('entries') or {}
    for k in ('apis', 'jobs'):
        if k not in entries:
            rec('FAIL', 'schema', '缺 entries.%s' % k)

    def ev_check(items, label):
        for i, it in enumerate(items):
            if not isinstance(it, dict) or not str(it.get('evidence', '')).strip():
                rec('FAIL', 'evidence', '%s[%d] 缺 evidence（须为 源码相对路径:行号 或 fragments nid）' % (label, i))

    ev_check(meta.get('apps', []), 'apps')
    ev_check(meta.get('mq', []), 'mq')
    ev_check(meta.get('third_party_apis', []), 'third_party_apis')
    ev_check(entries.get('apis', []), 'entries.apis')
    ev_check(entries.get('jobs', []), 'entries.jobs')
    ev_check(meta.get('redis_keys', []), 'redis_keys')
    ev_check(meta.get('apollo_keys', []), 'apollo_keys')
    ev_check(meta.get('tables', []), 'tables')

    # ---- 2. apps 合法性（manifest）----
    mpath = os.path.join(ws, 'knowledge', 'manifest.json')
    mkeys = set()
    try:
        mkeys = {p.get('key') for p in jload(mpath).get('projects', [])}
    except Exception as e:
        rec('SKIP', 'env', 'manifest.json 解析失败: %s' % e)
    app_keys = []
    for a in meta.get('apps', []):
        k = a.get('key')
        app_keys.append(k)
        if mkeys and k not in mkeys:
            rec('FAIL', 'apps', 'app key=%s 不在 manifest.json projects 中' % k)

    # ---- 3. 域登记（新域应在 P4 登记，未登记先 WARN）----
    dpath = os.path.join(ws, 'knowledge', '_manual', 'domains.json')
    try:
        dkeys = {d.get('key') for d in jload(dpath).get('domains', [])}
        if meta.get('domain') not in dkeys:
            rec('WARN', 'domain', 'domain=%s 未登记 domains.json（新域须在收尾阶段补 INDEX.md + 登记）' % meta.get('domain'))
    except Exception as e:
        rec('WARN', 'domain', 'domains.json 解析失败: %s' % e)

    # ---- 4. fragments 索引 ----
    frag = {}
    for k in set(app_keys):
        fp = os.path.join(ws, 'knowledge', 'fragments', '%s.json' % k)
        if os.path.isfile(fp):
            try:
                frag[k] = jload(fp)
            except Exception:
                rec('WARN', 'env', 'fragments/%s.json 解析失败' % k)
        else:
            rec('WARN', 'env', 'fragments/%s.json 不存在，该应用走源码兜底' % k)

    # 源码根一次性解析（所有源码兜底校验共用）
    root_of = {}
    _srcmap = app_source_root_map(ws)
    if _srcmap:
        for k in sorted({x for x in app_keys if x}):
            r = _srcmap.get(k)
            if r and os.path.isdir(r):
                root_of[k] = r
            else:
                rec('WARN', 'apps', 'projects.json 无 key=%s 或目录不存在，该应用源码校验跳过' % k)
    all_roots = list(root_of.values())

    def roots_for(app):
        return [root_of[app]] if app in root_of else []

    # ---- 5. entries.apis：fragments -> 源码双层 ----
    for i, api in enumerate(entries.get('apis', [])):
        app, ctrl, method, path = api.get('app'), api.get('controller'), api.get('method'), api.get('path')
        tag = 'apis[%d] %s.%s %s' % (i, ctrl, method, path)
        hit, means = False, bool(frag.get(app))
        f = frag.get(app) or {}
        for dom in f.get('apisByDomain', []):
            for a2 in dom.get('apis', []):
                t, m2 = a2.get('title', ''), a2.get('meta', '')
                if ctrl and ctrl == a2.get('controller') and (not method or t.endswith('.' + method)):
                    hit = True
                    break
                if path and m2 and (m2 == path or m2.endswith(path) or path.endswith(m2)):
                    hit = True
                    break
            if hit:
                break
        if not hit:
            roots = roots_for(app)
            if roots:
                means = True
                src = find_source_file(roots, '%s.java' % ctrl) if ctrl else None
                if src:
                    txt = read_text(src)
                    ok_method = (not method) or (method in txt)
                    tail = (path or '').rstrip('/').split('/')[-1] if path else ''
                    ok_path = (not tail) or (tail in txt)
                    hit = ok_method and ok_path
        check_verdict('apis', tag + ' 图谱/源码实证通过', tag + ' 查无实据', means, hit)

    # ---- 6. entries.jobs ----
    for i, job in enumerate(entries.get('jobs', [])):
        app, cls, method = job.get('app'), job.get('class'), job.get('method')
        tag = 'jobs[%d] %s.%s' % (i, cls, method)
        hit, means = False, bool(frag.get(app))
        for j2 in (frag.get(app) or {}).get('jobs', []):
            if cls and cls == j2.get('title'):
                hit = True
                break
        if not hit:
            roots = roots_for(app)
            if roots:
                means = True
                src = find_source_file(roots, '%s.java' % cls) if cls else None
                if src:
                    txt = read_text(src)
                    hit = (not method) or (method in txt)
        check_verdict('jobs', tag + ' 图谱/源码实证通过', tag + ' 查无实据', means, hit)

    # ---- 7. tables：tables.json -> 源码兜底 ----
    tmap = set()
    tnid_values = set()
    tname2nid = {}
    tpath = os.path.join(ws, 'knowledge', 'fragments', 'tables.json')
    try:
        _raw = jload(tpath).get('tableNameToNid', {})
        tmap = {k.lower() for k in _raw}
        tnid_values = set(_raw.values())
        tname2nid = {k.lower(): v for k, v in _raw.items()}
    except Exception as e:
        rec('WARN', 'env', 'tables.json 解析失败: %s' % e)
    for i, tb in enumerate(meta.get('tables', [])):
        name, app = tb.get('name'), tb.get('app')
        tag = 'tables[%d] %s' % (i, name)
        hit, means = bool(tmap) and name.lower() in tmap, bool(tmap)
        if not hit:
            roots = roots_for(app)
            if roots:
                means = True
                hit = grep_literal(name, roots)
        check_verdict('tables', tag + ' 索引/源码实证通过', tag + ' 查无实据', means, hit)

    # ---- 8. mq / redis / apollo：源码字面量 ----
    for i, m in enumerate(meta.get('mq', [])):
        name = m.get('name', '')
        roots = roots_for(m.get('app')) if m.get('app') else all_roots
        found = grep_literal(name, roots) if name else None
        check_verdict('mq', 'mq[%d] %s 源码实证' % (i, name), 'mq[%d] %s 源码未出现' % (i, name),
                      bool(roots), bool(found))
    for i, r in enumerate(meta.get('redis_keys', [])):
        pat = str(r.get('pattern', ''))
        probe = pat.split('*')[0] or max(pat.split(':'), key=len)  # 取静态前缀，无前缀取最长段
        roots = roots_for(r.get('app')) if r.get('app') else all_roots
        found = grep_literal(probe, roots) if probe else None
        check_verdict('redis', 'redis[%d] %s 源码实证' % (i, pat), 'redis[%d] 静态前缀 %s 源码未出现' % (i, probe),
                      bool(roots), bool(found))
    for i, a in enumerate(meta.get('apollo_keys', [])):
        key = str(a.get('key', ''))
        roots = roots_for(a.get('app')) if a.get('app') else all_roots
        found = grep_literal(key, roots) if key else None
        check_verdict('apollo', 'apollo[%d] %s 源码实证' % (i, key), 'apollo[%d] %s 源码未出现' % (i, key),
                      bool(roots), bool(found))

    # ---- 8.5 evidence 锚真伪：nid 存在性+指向一致性；源码锚可定位+行号不越界 ----
    # 背景：曾实测表名校验通过、evidence 里的假 nid（t-90，实际 t-0）照样带 PASS 上线——锚本身必须核真。
    jobs_nids = {k: {j.get('nid') for j in f.get('jobs', [])} for k, f in frag.items()}
    apis_nids = {k: {a.get('nid') for d in f.get('apisByDomain', []) for a in d.get('apis', [])}
                 for k, f in frag.items()}
    app_alt = '|'.join(sorted([k for k in mkeys if k], key=len, reverse=True))
    nid_re = re.compile(r'\b([ja])-(%s)-(\d+)\b' % app_alt) if app_alt else None
    t_re = re.compile(r'\bt-(\d+)\b')
    src_re = re.compile(r'([\w./\\\-]+\.(?:java|xml|vue|ts|js|yml|yaml|properties|sql))(?!\w)(?:[:：](\d+))?')

    _root_files_cache = {}

    def root_files(root):
        if root not in _root_files_cache:
            _root_files_cache[root] = [f.replace('\\', '/') for f in _iter_files([root])]
        return _root_files_cache[root]

    def check_src_anchor(path_str, lineno, ctx):
        norm = path_str.replace('\\', '/')
        found = None
        cand = os.path.join(ws, norm)
        if os.path.isfile(cand):
            found = cand
        if not found:
            for r in all_roots:
                p = os.path.join(r, norm)
                if os.path.isfile(p):
                    found = p
                    break
        if not found:
            # evidence 常省略 maven 模块/包前缀（如 service/dsp/X.java 实际在
            # source/tuan-activity-provider/src/main/java/**/service/dsp/ 下），按路径尾部匹配
            tail = '/' + norm
            for r in all_roots:
                for f in root_files(r):
                    if f.endswith(tail):
                        found = f
                        break
                if found:
                    break
        if not found:
            if '/' in norm:
                rec('FAIL', 'evidence', '%s: 源码锚 %s 无法定位（工作区/本模块应用源码均不存在，疑似虚构路径）'
                    % (ctx, path_str))
            else:
                rec('WARN', 'evidence', '%s: 裸文件名 %s 在本模块应用源码内未找到（跨应用锚请写完整相对路径）'
                    % (ctx, path_str))
            return
        if lineno:
            n = read_text(found).count('\n') + 1
            if int(lineno) > n:
                rec('FAIL', 'evidence', '%s: 源码锚 %s 行号 %s 越界（文件共 %d 行）' % (ctx, path_str, lineno, n))

    def scan_evidence(ev, ctx):
        if not ev:
            return
        if nid_re:
            for m2 in nid_re.finditer(ev):
                pref, app, num = m2.groups()
                nid = '%s-%s-%s' % (pref, app, num)
                pool = jobs_nids.get(app) if pref == 'j' else apis_nids.get(app)
                if pool is None:
                    rec('SKIP', 'evidence', '%s: fragments/%s.json 未加载，nid=%s 无法核验' % (ctx, app, nid))
                elif nid not in pool:
                    rec('FAIL', 'evidence', '%s: nid=%s 在 fragments/%s.json 不存在（假锚）' % (ctx, nid, app))
        for m2 in t_re.finditer(ev):
            nid = 't-' + m2.group(1)
            if tnid_values and nid not in tnid_values:
                rec('FAIL', 'evidence', '%s: nid=%s 不在 tables.json（假锚）' % (ctx, nid))
        for m2 in src_re.finditer(ev):
            check_src_anchor(m2.group(1), m2.group(2), ctx)

    for tb in meta.get('tables', []):
        ev = str(tb.get('evidence', ''))
        scan_evidence(ev, 'tables %s' % tb.get('name'))
        anchors = ['t-' + x for x in t_re.findall(ev)]
        if len(anchors) == 1 and tnid_values:
            nl = str(tb.get('name', '')).lower()
            if nl in tname2nid and tname2nid[nl] != anchors[0]:
                rec('FAIL', 'evidence', 'tables %s: evidence 锚 %s 与该表实际 nid %s 不符（指错了对象）'
                    % (tb.get('name'), anchors[0], tname2nid[nl]))
    for job in entries.get('jobs', []):
        ev = str(job.get('evidence', ''))
        scan_evidence(ev, 'jobs %s' % job.get('class'))
        if nid_re:
            ms = [x for x in nid_re.finditer(ev) if x.group(1) == 'j']
            if len(ms) == 1:
                app, num = ms[0].group(2), ms[0].group(3)
                pool = (frag.get(app) or {}).get('jobs', [])
                hit = next((j for j in pool if j.get('nid') == 'j-%s-%s' % (app, num)), None)
                if hit is not None and job.get('class') and hit.get('title') != job.get('class'):
                    rec('FAIL', 'evidence', 'jobs %s: evidence 锚 j-%s-%s 实际指向 %s（指错了对象）'
                        % (job.get('class'), app, num, hit.get('title')))
    for api in entries.get('apis', []):
        ev = str(api.get('evidence', ''))
        scan_evidence(ev, 'apis %s.%s' % (api.get('controller'), api.get('method')))
        if nid_re:
            ms = [x for x in nid_re.finditer(ev) if x.group(1) == 'a']
            if len(ms) == 1:
                app, num = ms[0].group(2), ms[0].group(3)
                pool = [a for d in (frag.get(app) or {}).get('apisByDomain', []) for a in d.get('apis', [])]
                hit = next((a for a in pool if a.get('nid') == 'a-%s-%s' % (app, num)), None)
                if hit is not None and api.get('controller') and hit.get('controller') != api.get('controller'):
                    rec('FAIL', 'evidence', 'apis %s: evidence 锚 a-%s-%s 实际指向 %s（指错了对象）'
                        % (api.get('controller'), app, num, hit.get('controller')))
    for a in meta.get('apps', []):
        scan_evidence(str(a.get('evidence', '')), 'apps %s' % a.get('key'))
    for m in meta.get('mq', []):
        scan_evidence(str(m.get('evidence', '')), 'mq %s' % m.get('name'))
    for r in meta.get('redis_keys', []):
        scan_evidence(str(r.get('evidence', '')), 'redis %s' % r.get('pattern'))
    for a in meta.get('apollo_keys', []):
        scan_evidence(str(a.get('evidence', '')), 'apollo %s' % a.get('key'))
    for t in meta.get('third_party_apis', []):
        scan_evidence(str(t.get('evidence', '')), 'third_party %s' % t.get('bean'))

    # ---- 9. md 校验 ----
    if not args.skip_md:
        doc = meta.get('doc', '')
        doc_path = doc if os.path.isabs(doc) else os.path.join(ws, doc)
        if not doc or not os.path.isfile(doc_path):
            rec('FAIL', 'doc', 'doc 文件不存在: %s' % doc)
            md = ''
        else:
            md = read_text(doc_path)
            if os.path.getsize(doc_path) > 2 * 1024 * 1024:
                rec('WARN', 'doc', '文档超过 2MB，检查是否异常')
        if md:
            for ln_no, line in enumerate(md.splitlines(), 1):
                if re.match(r'^#{4,}\s', line):
                    rec('FAIL', 'doc', '第 %d 行出现 4 级及以下标题（规范：只允许 1/2/3 级）' % ln_no)
            h1 = len(re.findall(r'^# ', md, re.M))
            if h1 != 1:
                rec('WARN', 'doc', '一级标题数量=%d（规范：恰好 1 个）' % h1)
            for kw in ('概述', '知识清单', '测试问题'):
                if kw not in md:
                    rec('FAIL', 'doc', '缺少必备部分关键词「%s」' % kw)
            for dia, label in (('flowchart', '业务流程图'), ('erDiagram', 'ER图'), ('sequenceDiagram', '时序图')):
                if dia not in md and not (dia == 'flowchart' and 'graph ' in md):
                    rec('FAIL', 'doc', '缺少必备 mermaid 图: %s(%s)' % (label, dia))
            # 禁用语法：mermaid 11+ 已整体移除 C4 原生语法族（2026-09-14 于 11.13.0 实测 Parse error）
            for bm in re.findall(r'```mermaid\n(.*?)```', md, re.S):
                head = bm.strip().split('\n')[0].strip()
                if head in ('C4Context', 'C4Container', 'C4Dynamic', 'C4Deployment'):
                    rec('FAIL', 'doc', 'mermaid 块使用已移除的 %s 原生语法（11+ 渲染必炸），按模板改 flowchart 等价视图' % head)
            feats = meta.get('features') or {}
            if feats.get('state_machine') and 'stateDiagram' not in md:
                rec('FAIL', 'doc', 'features.state_machine=true 但缺 stateDiagram')
            if 'C4' not in md:
                rec('WARN', 'doc', '未检出 C4 架构图（若 C1 仅 1 个系统可豁免，请在文档说明）')
            m2c = COUNTER_RE.search(md)
            if not m2c:
                rec('FAIL', 'doc', '知识清单缺「清单计数」对账行（模板第五部分固定格式）')
            else:
                expect = (len(entries.get('apis', [])), len(entries.get('jobs', [])),
                          len(meta.get('tables', [])), len(meta.get('redis_keys', [])),
                          len(meta.get('apollo_keys', [])), len(meta.get('mq', [])))
                got = tuple(int(x) for x in m2c.groups())
                if expect != got:
                    rec('FAIL', 'doc', '清单计数与 json 不一致: md=%s 实际json=%s（禁止声明与事实脱节）' % (got, expect))

    # ---- 汇总 ----
    n = {'FAIL': 0, 'SKIP': 0, 'WARN': 0, 'PASS': 0}
    for lv, _c, _m in RESULTS:
        n[lv] = n.get(lv, 0) + 1
    print('┌──────────────┬──────────┐')
    print('│ 校验结果     │ 数量     │')
    print('├──────────────┼──────────┤')
    for lv in ('PASS', 'FAIL', 'WARN', 'SKIP'):
        print('│ %-12s │ %d' % (lv, n[lv]) + ' ' * (6 - len(str(n[lv]))) + '│')
    print('└──────────────┴──────────┘')
    for lv, cat, msg in RESULTS:
        if lv != 'PASS':
            print('[%s][%s] %s' % (lv, cat, msg))
    if n['FAIL']:
        print('结论: FAIL —— 存在疑似编造/结构缺失，修复后重跑；禁止带 FAIL 交付')
        sys.exit(1)
    print('结论: PASS' + ('（含 SKIP 项需人工补证）' if n['SKIP'] else ''))
    sys.exit(0)


if __name__ == '__main__':
    main()
