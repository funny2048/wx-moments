# 自动化报告: {changeId}

> 示例占位说明：本模板中的 `{占位符}` 处使用前需替换为本项目业务术语。
> 生成时间: {date}
> 报告类型: M9 归档自动化报告

## 本次需求

- **变更ID**: {changeId}
- **Phase 总数**: {N}
- **需求简述**: {从顶层 prd.md 提取}

### 各 Phase 核心需求

| Phase | 核心需求 |
|-------|----------|
| 01-xxx | {从 design.md 提取} |
| ... | ... |

---

## 核心指标

- **Git 地址**: {gitUrl}
- **基线 Commit**: {baseCommit}
- **变更 MD 文件数**: {N} 个
- **代码总增行数**: +{N}
- **代码总删行数**: -{N}
- **代码净增行数**: {N}

---

## 变更统计（跨所有 Phase 汇总）

> 统计口径：已跟踪变更 + 未跟踪新文件

| 统计项 | 数值 | 说明 |
|--------|------|------|
| 已跟踪改动文件数 | {N} 个 | `git diff --stat HEAD` |
| 未跟踪新文件数 | {N} 个 | `git ls-files --others` |
| 改动文件数（总计） | {N} 个 | 以上两项之和 |
| 已跟踪新增行数 | +{N} | `git diff --numstat HEAD` |
| 已跟踪删除行数 | -{N} | `git diff --numstat HEAD` |
| 新增文档文件数 | {N} 个 | `find openspec/changes/{changeId} -name "*.md"` |

### 按模块分布

| 模块 | 新增文件 | 修改文件 | 净增行数 |
|------|----------|----------|----------|
| {repo-abbrev}-{module-1} | {N} | {N} | {N} |
| {repo-abbrev}-{module-2} | {N} | {N} | {N} |
| {repo-abbrev}-{module-3} | {N} | {N} | {N} |
| {repo-abbrev}-{module-4} | {N} | {N} | {N} |

---

## 参考的文档

| 文档 | 所属 Phase | 用途 |
|------|-----------|------|
| prd.md | 顶层 | 原始产品需求文档 |
| domain.md | 各 Phase | 领域模型锚定 |
| design.md | 各 Phase | 技术设计方案 |
| ... | ... | ... |

---

## 输出的文档

### 顶层文档

| 文档 | 说明 |
|------|------|
| workflow-state.md | 工作流状态机 |
| integration-summary.md | M8 集成确认汇总 |
| archive.md | M9 归档报告 |
| auto-report.md | 本自动化报告 |
| ... | ... |

### 各 Phase 输出文档

| Phase | 文档数 | 核心输出 |
|-------|--------|----------|
| 01-xxx | {N} | domain.md, design.md, implementation.md, acceptance.md, ... |
| ... | ... | ... |

---

## 验收结果汇总

| Phase | 验收状态 | 说明 |
|-------|----------|------|
| 01-xxx | PASS / CONDITIONAL | {说明} |
| ... | ... | ... |

---

## 发布建议

**结论**: {PASS / 有条件发布}

- {发布范围说明}
- {遗留事项说明}

---

*报告生成时间: {date}*
