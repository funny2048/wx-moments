#!/usr/bin/env node
'use strict';

/**
 * openspec 环境前置检查 — daily 工作流启动门槛（安装/初始化/更新三查）
 *
 * 消费方: workflow/daily/WORKFLOW.md「环境前置检查」章节，主 agent 每次工作流启动前
 *         （含中断恢复重入）执行；不属于 domain-harness-init（其产物全在 knowledge/，
 *         无 openspec 依赖，且一次性初始化后不再触发，兜不住每次启动的 update）
 *
 * 用法:
 *   node .claude/scripts/openspec-check.js [project_root]   // 缺省取 CLAUDE_PROJECT_DIR 或 cwd
 *
 * 检查链与退出码（主 agent 按码分流，禁止自行发挥）:
 *   1. CLI 安装: openspec 不可用 → 自动 npm install -g @fission-ai/openspec 并复验
 *      npm 缺失 / 安装失败 / 复验失败 → exit 1（阻断工作流；node/npm 均不可用时提示先装 Node.js）
 *   2. 项目初始化: <root>/openspec/ 目录缺失 → exit 2（阻断，提示 openspec init --tools claude；
 *      禁止工作流自行 init——init 会写 AGENTS.md/CLAUDE.md 指令块，属安装期职责）
 *   3. 更新: openspec update 刷新 instruction files → 失败 exit 3（不阻断，提示后继续）
 *   全部通过 → exit 0
 *
 * 跨平台说明（Windows/macOS 行为一致，零 npm 依赖）:
 *   - PATH 兜底解析：openspec 经 npm 全局装在 nvm node bin（posix）或 %APPDATA%\npm（win32），
 *     干净子进程可能探不到；探测与执行统一基于"原始 PATH + 兜底目录"的增强 PATH
 *   - win32 下 npm 全局包是 .cmd 垫片，spawn 必须启用 shell；子命令参数全为常量，无注入面
 *   - 交互挂起防护：安装与 update 均忽略 stdio，需要交互时快速失败而非挂起
 */

const { spawnSync } = require('child_process');
const fs = require('fs');
const os = require('os');
const path = require('path');

const IS_WIN = process.platform === 'win32';
const PROJECT_DIR = path.resolve(process.argv[2] || process.env.CLAUDE_PROJECT_DIR || process.cwd());

const log = (msg) => console.log(`[openspec-check] ${msg}`);

/** 版本目录名 → 可比较的数字元组（v20.10.1 → [20,10,1]，非法名排最后） */
function versionKey(name) {
  const m = /^\D*(\d+)\.(\d+)\.(\d+)/.exec(name);
  return m ? m.slice(1).map(Number) : [0, 0, 0];
}

/** PATH 之外的兜底可执行目录：posix 取 nvm 各版本 node bin（新版本优先），win32 取 %APPDATA%\npm */
function extraDirs() {
  const dirs = [];
  if (!IS_WIN) {
    const nvmDir = process.env.NVM_DIR || path.join(os.homedir(), '.nvm');
    const versionsDir = path.join(nvmDir, 'versions', 'node');
    try {
      fs.readdirSync(versionsDir)
        .sort((a, b) => {
          const ka = versionKey(a);
          const kb = versionKey(b);
          return (kb[0] - ka[0]) || (kb[1] - ka[1]) || (kb[2] - ka[2]);
        })
        .forEach((v) => dirs.push(path.join(versionsDir, v, 'bin')));
    } catch (_) { /* nvm 不存在，忽略 */ }
  }
  if (IS_WIN && process.env.APPDATA) {
    dirs.push(path.join(process.env.APPDATA, 'npm'));
  }
  return dirs;
}

/** 增强目录表：原始 PATH 目录在前，兜底目录在后（探测与 spawn 共用同一口径） */
function searchDirs() {
  const pathDirs = (process.env.PATH || '').split(path.delimiter).filter(Boolean);
  return pathDirs.concat(extraDirs());
}

/** 在增强目录表中探测可执行文件，返回绝对路径或 null（win32 兼容 .cmd/.exe/.bat 垫片） */
function findCmd(name) {
  const exts = IS_WIN ? ['', '.cmd', '.exe', '.bat'] : [''];
  for (const dir of searchDirs()) {
    for (const ext of exts) {
      const p = path.join(dir, name + ext);
      try {
        if (fs.statSync(p).isFile()) return p;
      } catch (_) { /* 不存在，继续 */ }
    }
  }
  return null;
}

/** 以裸命令名 spawn（避免绝对路径带空格在 shell 模式下的引号问题），env 注入增强 PATH */
function run(cmd, args, opts = {}) {
  return spawnSync(cmd, args, {
    shell: IS_WIN,
    encoding: 'utf8',
    ...opts,
    env: { ...process.env, PATH: (process.env.PATH || '') + path.delimiter + extraDirs().join(path.delimiter) },
  });
}

// --- 1. CLI 安装检查（未装自动装）---
if (!findCmd('openspec')) {
  log('openspec 未安装，自动安装 npm 全局包 @fission-ai/openspec ...');
  if (!findCmd('npm')) {
    log('ERROR: npm 不可用——openspec 为 npm 包，请先安装 Node.js（https://nodejs.org）后重试');
    process.exit(1);
  }
  const install = run('npm', ['install', '-g', '@fission-ai/openspec'], { stdio: 'ignore' });
  if (install.error || install.status !== 0) {
    log(`ERROR: npm install -g @fission-ai/openspec 失败（exit ${install.status}）`);
    process.exit(1);
  }
  if (!findCmd('openspec')) {
    log('ERROR: 安装后仍未探到 openspec 可执行文件');
    process.exit(1);
  }
}
const ver = run('openspec', ['--version']);
const verStr = String(ver.stdout || ver.stderr || '').trim();
if (ver.error || ver.status !== 0 || !verStr) {
  log('ERROR: openspec --version 复验失败（安装未生效）');
  process.exit(1);
}
log(`CLI 就绪: openspec ${verStr}`);

// --- 2. 项目初始化检查（只查不建）---
if (!fs.existsSync(path.join(PROJECT_DIR, 'openspec'))) {
  log(`ERROR: ${PROJECT_DIR} 未初始化（缺 openspec/ 目录），请执行 openspec init --tools claude 或重跑 harness 安装脚本后重试`);
  process.exit(2);
}

// --- 3. 更新 instruction files（失败不阻断）---
const update = run('openspec', ['update'], { cwd: PROJECT_DIR, stdio: 'ignore' });
if (!update.error && update.status === 0) {
  log(`openspec update 完成，环境就绪: ${PROJECT_DIR}`);
  process.exit(0);
}
log('WARN: openspec update 失败（CLI 与项目可用，降级继续），可人工执行 openspec update 排查');
process.exit(3);
