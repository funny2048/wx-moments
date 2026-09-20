# 页面级测试报告 — 20260920-social-timeline-mvp

> 执行时间：2026-09-21 02:41–02:47（Playwright MCP，真实应用：`java -jar moments-web.jar --spring.profiles.active=dev`，http://localhost:8081/api）
> 截图证据：`page-evidence/01-11 *.png`（11 张）

## 环境

- 应用：真实 Tomcat（非 MockMvc），端口 8081 + context-path `/api`（控制器自带 `/api` 前缀 → 实际端点 `/api/api/...`，app.js BASE 自适应）
- 数据：页面测试前经 `POST /api/api/mock-data`（clear=true）重建——100 用户 / 625 对好友（1250 条记录 ≥ PRD 1000+）/ 800 标签 / 1612 绑定 / 231ms

## 用例与结果（全部 PASS）

| # | 页面/场景 | 验证内容 | 结果 | 证据 |
|---|----------|---------|------|------|
| 1 | index.html 导航 | 9 页入口互达 | ✓ | 01-index.png |
| 2 | Feed · 作者视角 A(周桂681) | 5 条隐私标记帖全可见（作者最高优先级）+ 删除按钮 | ✓ | 02-feed-author-A.png |
| 3 | Feed · B(唐芳711, 同事标签) | 恰见 P0+P1（标签命中部分可见）；P2(排除同事)/P3(私密)/P5(空集) 不可见 | ✓ | 03-feed-B-taghit.png |
| 4 | Feed · C(吴斌695, 指定好友) | 恰见 P0+P1+P2（指定好友命中 + 未被排除） | ✓ | 04-feed-C-userhit.png |
| 5 | Feed · D(王娟774, 被点名排除) | 仅见 P0（P2 被指定好友排除命中） | ✓ | 05-feed-D-excluded.png |
| 6 | friends.html | 15 位好友 + tagNames chips + 标签过滤（同事=6 人，含唐芳、排除吴斌/王娟） | ✓ | 06-friends.png |
| 7 | friend-detail.html?userId=吴斌 | 基础信息 + 我打的标签(同学/朋友) + 朋友圈区块（空态与 API 一致：0==0） | ✓ | 07-friend-detail.png |
| 8 | tags.html | 标签列表 + 新建标签（UI 创建"E2E测试标签"成功，测试后已删） | ✓ | （流程证据） |
| 9 | friend-tags.html | 选好友→点标签绑定（二次确认 confirm）→ toast 绑定成功 → API 复核 tagNames 含新标签 → 解绑还原 | ✓ | 08-friend-tags-bind.png |
| 10 | post-create.html | 文字计数 0/2000 + 选图上传（真实文件 /images/{uuid}.png）+ 预览 1/9 + 公开发表 → 跳转 Feed 置顶展示带图帖 | ✓ | 09-post-create-published.png |
| 11 | users.html | 100 用户分页 1/5，翻页第 2 页 ✓，"切换为当前用户"入口 | ✓ | 10-users-p2.png |
| 12 | mock-data.html | 4 配置项（含范围提示 1-100000/0-500/0-20/0-10000）+ clear 警告条；生成路径已由 curl E2E 验证（本页不重复触发防污染场景数据） | ✓ | 11-mock-data.png |
| 13 | 控制台 | 本会话 9 页面导航 0 error 0 warning（历史 8899 端口错误属 T110 executor 旧会话） | ✓ | — |

## 隐私矩阵四视角汇总（页面级）

| 视角 | P0 公开 | P1 部分可见(标签同事+指定吴斌) | P2 不给谁看(同事+王娟) | P3 私密 | P5 空集部分可见 |
|------|--------|--------|--------|--------|--------|
| A 作者 | ✓见 | ✓见 | ✓见 | ✓见 | ✓见 |
| B 唐芳(同事) | ✓见 | ✓见(标签命中) | ✗不见(被排除) | ✗ | ✗ |
| C 吴斌(指定) | ✓见 | ✓见(指定命中) | ✓见(无命中) | ✗ | ✗ |
| D 王娟(被排除) | ✓见 | ✗(无命中) | ✗不见(被排除) | ✗ | ✗ |

四视角页面渲染与 API 投影、explore.md 语义闭环规则①②③④完全一致。

## 附带安全实测（真实容器）

路径穿越 4 变体（`../`、`%2e%2e/`、`..%2f`、双编码）：无任何文件字节泄露（`..%2f` 被 Tomcat 400，其余返回 JSON 错误信封 code=100）——R6 安全实质成立（详见 workflow-state 裁决9）。

## 遗留与说明

- dev 库保留 E2E 场景数据（E2E-P0/P1/P2/P3/P5 + E2E-UP 标记帖）供晨间人工复核，含 100 用户模拟数据集
- 实际访问入口：`http://localhost:8081/api/index.html`（应用已停止，晨间可用 `java -jar moments-web/target/moments-web.jar --spring.profiles.active=dev` 复启）
