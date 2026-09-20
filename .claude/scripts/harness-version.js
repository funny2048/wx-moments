#!/usr/bin/env node
/**
 * harness-version.js — harness 版本比对与烙印(零依赖 Node,Windows/macOS 一致)
 *
 * 子命令:
 *   check                       比对 harness-version.json(最新) vs 项目 .harness-manifest.json(当前),输出差异与建议动作
 *   stamp  <skill-name>         init 收尾烙印知识产出口径版本(写入 manifest.knowledge)
 *   adopt  <skill-name>         存量项目手动烙印(确认知识为当前口径时使用,效果同 stamp)
 *
 * harness 仓定位:env HARNESS_ROOT → ~/.harness/meta.json 的 repoPath,失败退出码 2
 * 退出码:0=无需动作 / 1=有落后或未纳管(供脚本化判定) / 2=环境错误
 * manifest 结构:
 *   { "installed": { "at", "tier", "workflow-assets", "repo" },
 *     "knowledge": { "domain-harness-init": {...}|null, "java-harness-init": {...}|null } }
 * 约束:本脚本是 manifest 唯一合法写入方之一(另一为 project_install.py,且其只写 installed 组)
 */

"use strict";

const fs = require("fs");
const os = require("os");
const path = require("path");

const MANIFEST_NAME = ".harness-manifest.json";
const VERSION_NAME = "harness-version.json";

// ---------- 基础工具 ----------

function fail(msg, code) {
  console.error("[harness-version] " + msg);
  process.exit(code === undefined ? 2 : code);
}

function info(msg) {
  console.log("[harness-version] " + msg);
}

function findHarnessRoot() {
  if (process.env.HARNESS_ROOT) {
    const p = process.env.HARNESS_ROOT;
    if (fs.existsSync(path.join(p, VERSION_NAME))) return p;
    fail("HARNESS_ROOT 指向的目录缺 " + VERSION_NAME + ": " + p);
  }
  const metaPath = path.join(os.homedir(), ".harness", "meta.json");
  if (!fs.existsSync(metaPath)) {
    fail("未找到 ~/.harness/meta.json,无法定位 harness 仓库;请先注册(env HARNESS_ROOT 或 meta.json repoPath)");
  }
  let repoPath;
  try {
    repoPath = JSON.parse(fs.readFileSync(metaPath, "utf8")).repoPath;
  } catch (e) {
    fail("meta.json 解析失败: " + e.message);
  }
  if (!repoPath || !fs.existsSync(path.join(repoPath, VERSION_NAME))) {
    fail("meta.json repoPath 无效或缺少 " + VERSION_NAME + ": " + repoPath);
  }
  return repoPath;
}

function loadSpec(harnessRoot) {
  try {
    return JSON.parse(fs.readFileSync(path.join(harnessRoot, VERSION_NAME), "utf8"));
  } catch (e) {
    fail("harness-version.json 读取/解析失败: " + e.message);
  }
}

function loadManifest(projectRoot, optional) {
  const p = path.join(projectRoot, MANIFEST_NAME);
  if (!fs.existsSync(p)) {
    if (optional) return null;
    return null;
  }
  try {
    return JSON.parse(fs.readFileSync(p, "utf8"));
  } catch (e) {
    fail("项目 manifest 解析失败(" + p + "): " + e.message);
  }
}

// semver 三段比较:返回 -1/0/1(a<b / a==b / a>b),非三段格式按字符串比较兜底
function cmpVersion(a, b) {
  const pa = String(a || "").split(".");
  const pb = String(b || "").split(".");
  if (pa.length === 3 && pb.length === 3 && pa.every(x => /^\d+$/.test(x)) && pb.every(x => /^\d+$/.test(x))) {
    for (let i = 0; i < 3; i++) {
      const d = parseInt(pa[i], 10) - parseInt(pb[i], 10);
      if (d !== 0) return d < 0 ? -1 : 1;
    }
    return 0;
  }
  return a === b ? 0 : (a < b ? -1 : 1);
}

// 取 changelog 中 (current, latest] 区间条目(即项目落后期间发生的变更)
function changelogBetween(entries, current, latest) {
  if (!Array.isArray(entries)) return [];
  return entries.filter(e => cmpVersion(e.v, current) > 0 && cmpVersion(e.v, latest) <= 0);
}

// 差距档位:比较区间两端(current→latest)首个不同的位——major 位异=MAJOR(3),minor 位异=MINOR(2),仅 patch 异=PATCH(1)
function gapLevel(current, latest) {
  const pa = String(current || "").split(".");
  const pb = String(latest || "").split(".");
  if (pa.length !== 3 || pb.length !== 3) return 3; // 非三段格式保守按 MAJOR
  for (let i = 0; i < 3; i++) {
    if (parseInt(pa[i], 10) !== parseInt(pb[i], 10)) return 3 - i;
  }
  return 0;
}

function nowIso() {
  return new Date().toISOString().replace(/\.\d+Z$/, "Z");
}

// ---------- check ----------

function cmdCheck(projectRoot) {
  const harnessRoot = findHarnessRoot();
  const spec = loadSpec(harnessRoot);
  const manifest = loadManifest(projectRoot);

  if (!manifest) {
    info("项目未纳管版本机制(" + MANIFEST_NAME + " 不存在)");
    info("处理:重跑 project_install.py 即可纳入,不影响现有代码与 knowledge/");
    process.exit(1);
  }

  const installed = manifest.installed || {};
  const knowledge = manifest.knowledge || {};
  const tier = installed.tier || "daily";
  const actions = [];
  let behind = 0;
  let knowledgeContractChange = false;

  console.log("=== harness 版本比对(harness=" + harnessRoot + ", tier=" + tier + ") ===");

  // 1) workflow-assets
  const wfLatest = (spec["workflow-assets"] || {})[tier];
  const wfCurrent = installed["workflow-assets"];
  if (!wfLatest) {
    info("workflow-assets[" + tier + "] 在 harness-version.json 中未定义,跳过该项");
  } else if (!wfCurrent) {
    info("workflow-assets: 未记录 → " + wfLatest + "   建议:重跑 project_install.py");
    behind++;
    actions.push("重跑 project_install.py");
  } else if (cmpVersion(wfCurrent, wfLatest) < 0) {
    const wfEntries = changelogBetween(
      ((spec.changelog || {})["workflow-assets"] || {})[tier] || [],
      wfCurrent, wfLatest);
    const contractHits = wfEntries.filter(e => e.impact === "knowledge-contract");
    knowledgeContractChange = knowledgeContractChange || contractHits.length > 0;
    behind++;
    const lines = wfEntries.map(e => "    - " + e.v + " " + e.note + (e.impact === "knowledge-contract" ? " [knowledge-contract]" : ""));
    info("workflow-assets: " + wfCurrent + " → " + wfLatest + " 落后");
    lines.forEach(l => console.log(l));
    actions.push("重跑 project_install.py(刷新 workflow/skills/agents/rules 资产)");
  } else {
    info("workflow-assets: " + wfCurrent + " ✓");
  }

  // 2) skills(知识产出口径)
  for (const name of Object.keys(spec.skills || {})) {
    const latest = spec.skills[name];
    const cur = knowledge[name];
    const label = name + ": ";
    if (cur === undefined || cur === null) {
      behind++;
      if (knowledgeContractChange) {
        info(label + "未初始化或口径未知(存量 null)   建议:运行 /" + name + " 初始化(流程区间含知识契约变更,重跑为必须项)");
      } else {
        info(label + "未初始化或口径未知(存量 null)   建议:确认口径新鲜则 adopt 手动烙印,否则运行 /" + name);
      }
      actions.push("/" + name + " 初始化或 adopt 烙印");
      continue;
    }
    const curV = typeof cur === "object" ? cur.version : cur;
    if (cmpVersion(curV, latest) < 0) {
      const entries = changelogBetween((spec.changelog || {})[name] || [], curV, latest);
      const level = gapLevel(curV, latest);
      behind++;
      info(label + curV + " → " + latest + " 落后");
      entries.forEach(e => console.log("    - " + e.v + " " + e.note));
      let advice;
      if (knowledgeContractChange || level === 3) {
        advice = "必须重跑 /" + name + (knowledgeContractChange ? "(区间含知识消费契约变更,增量补不齐)" : "(MAJOR 结构不兼容)");
      } else if (level === 2) {
        advice = "建议 /harness-update 增量同步(活跃项目)或重跑 /" + name;
      } else {
        advice = "PATCH 差距,无需动作";
        behind--; // PATCH 不计入需处理项
      }
      info("    建议: " + advice);
      if (level > 1 || knowledgeContractChange) actions.push(advice);
    } else {
      info(label + curV + " ✓");
    }
  }

  console.log("===");
  if (behind === 0) {
    info("全部口径一致,无需动作");
    process.exit(0);
  }
  info("建议动作(顺序执行):");
  actions.forEach((a, i) => console.log("  " + (i + 1) + ". " + a));
  process.exit(1);
}

// ---------- stamp / adopt ----------

function writeKnowledgeStamp(spec, projectRoot, skillName, mode) {
  const latest = (spec.skills || {})[skillName];
  if (!latest) {
    fail("未知 skill: " + skillName + "(可选: " + Object.keys(spec.skills || {}).join(", ") + ")");
  }
  const manifestPath = path.join(projectRoot, MANIFEST_NAME);
  let manifest = loadManifest(projectRoot);
  let created = false;
  if (!manifest) {
    manifest = { installed: {}, knowledge: {} };
    created = true;
  }
  manifest.installed = manifest.installed || {};
  manifest.knowledge = manifest.knowledge || {};

  if (mode === "adopt") {
    info("adopt 语义:声明项目 " + skillName + " 的知识即为当前口径(仅在你确认知识新鲜时使用)");
  }

  manifest.knowledge[skillName] = { "version": latest, "at": nowIso(), "by": mode };
  for (const name of Object.keys(spec.skills || {})) {
    if (!(name in manifest.knowledge)) manifest.knowledge[name] = null;
  }
  fs.writeFileSync(manifestPath, JSON.stringify(manifest, null, 2) + "\n", "utf8");
  info((created ? "已创建 " : "已更新 ") + MANIFEST_NAME + ": knowledge." + skillName + " = " + latest + " (by " + mode + ")");
  if (created) {
    info("提示:manifest 为本次新建,installed 组尚未烙印,建议重跑 project_install.py 补齐");
  }
}

// ---------- main ----------

function usage() {
  console.log("用法:");
  console.log("  node harness-version.js check [projectPath]");
  console.log("  node harness-version.js stamp <skill-name> [projectPath]");
  console.log("  node harness-version.js adopt <skill-name> [projectPath]");
  console.log("  <skill-name> ∈ {domain-harness-init, java-harness-init}(以 harness-version.json skills 键为准)");
}

function main() {
  const args = process.argv.slice(2);
  const cmd = args[0];
  if (!cmd || cmd === "-h" || cmd === "--help") {
    usage();
    process.exit(cmd ? 0 : 2);
  }
  if (cmd === "check") {
    cmdCheck(path.resolve(args[1] || process.cwd()));
    return;
  }
  if (cmd === "stamp" || cmd === "adopt") {
    if (!args[1]) {
      usage();
      fail("缺少 <skill-name>");
    }
    const harnessRoot = findHarnessRoot();
    const spec = loadSpec(harnessRoot);
    writeKnowledgeStamp(spec, path.resolve(args[2] || process.cwd()), args[1], cmd);
    return;
  }
  usage();
  fail("未知子命令: " + cmd);
}

main();
