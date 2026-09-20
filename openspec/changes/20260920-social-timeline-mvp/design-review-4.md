# design-review-4: 20260920-social-timeline-mvp

> 审查人：adversarial-reviewer（mode: design，第 4 轮）
> 审查对象：design.md / api.md / tasks.md / sql.md（review-3 终审熔断后按主 agent 代行人工裁决落地的版本，修复映射 design §14 R3 行 x5）
> 验证基准：design-review-3.md（阻塞 1 + 建议 4）+ workflow-state.md「Stage 3 熔断与人工裁决记录」段（裁决原文）
> 需求基准：prd.md（C1-C17）+ explore.md（D1-D7）
> 生成时间：2026-09-21
> 轮次性质：**收敛性复核（裁决落地验证 + 回归扫描）**——非发散重审。判定纪律：仅「裁决未落地 / 落地失真 / 本轮改动引入回归」计阻塞级；历史 26+1 条已闭环条目不再重审；纯优化建议归建议级且不影响 PASS。
> 输入说明：knowledge/domain/service-hot.md 不存在（绿地项目），跳过

## 结论

- **通过（阻塞级 0 条，待澄清清单为空）**
- 结论标准对照：阻塞级 = 0 且待澄清清单为空 → **PASS**。裁决 4 项全部如实落地、无失真；回归扫描 5 个面（T020/T030 一致性、DAG、错误码表、api 接口数、行为契约）均无新引入矛盾。阶段三对抗验证出口条件达成。

---

## 一、裁决落地逐项验证表

> 裁决原文来源：workflow-state.md「Stage 3 熔断与人工裁决记录」4 项（阻塞修复方案① / HttpMessageNotReadableException→1001 / 建议①③④ / §14 R3 映射）。

| # | 裁决项 | 裁决原文要点 | 落点（逐处核验） | 验证结论 |
|---|--------|-------------|----------------|---------|
| 1 | **阻塞修复方案①**（review-3 阻塞 1） | FriendTagMapper 最小集（selectByTagId）前移 T020，T030 改追加；与 RelationMapper 已确立的前移模式一致；验收标准/DAG/契约零变动 | ① tasks T020 描述+涉及文件+伪代码三处均标「R3 裁决方案①前移：本 task 仅实现 selectByTagId，其余方法 T030 追加」，listFriends tagId 分支两个调用（FriendTagMapper.selectByTagId 校验 1004 / relationMapper.selectFriendIdsByTag 数据源）均标注"本 task 自建方法"；② tasks T030 两 Mapper 涉及文件改「**追加**：T020 已建文件」，伪代码明示"selectByTagId 已在 T020 建好"并给出追加清单；③ design §6.3 两 Mapper 的 selectByTagId / selectFriendIdsByTag 均带「R3 裁决方案①：归属 T020」标注；④ T020 验收两条不再互斥——tagId 分支全部方法在 T020 自建文件清单内，「涉及文件 + Depends（仅 T010 链）+ 伪代码 + 验收」四者自洽；⑤ DAG 零变动（T020 Depends 仍仅 T010、T030 Depends T020、G1-G9 并行组不变）；⑥ api.md 接口 3（tagId 可选过滤 + 1004 归属校验）契约未动 | **✅ 如实落地**。方法集拆分完整性交叉验证：FriendTagMapper 真源 7 方法 = T020 建 1（selectByTagId）+ T030 追加 6（selectByUserId/updateTagName/updateIsDel/batchInsert/countFriendsByTagIds/logicDeleteAll），无重叠无遗漏；FriendTagRelationMapper 真源 9 方法 = T020 建 2（selectTagNamesByOwnerAndFriends + selectFriendIdsByTag）+ T030 追加 7（selectByTagAndUser/selectTagIdsOfFriend/insert/updateIsDelByTagId/updateIsDelByTagAndUser/batchInsert/logicDeleteAll），无重叠无遗漏。与 RelationMapper 前移模式一致性成立（同一"建文件归 T020、T030 追加其余"形态） |
| 2 | **裁决追加：HttpMessageNotReadableException→1001**（review-3 建议 2 方案一） | 补 handler → 1001，与 TypeMismatch handler 同模式，统一 JSON body 非法入参行为 | ① design §6.1：BizExceptionAdvice 由"两类异常"扩为「三类异常（A10 + NB1 定稿 + R3 裁决追加）」，③ 明确 HttpMessageNotReadableException（Jackson 反序列化失败）→ 统一转 1001，注明"与 ② 同模式"；② design §9 异常场景表新增对应行（R3 裁决追加标注）；③ tasks T100 伪代码含完整 handler（@ExceptionHandler + log.warn 不回传 e 细节防内部结构泄露 + buildFailure(1001)，与 handleTypeMismatch 同构）；④ tasks T100 验收新增「POST /api/posts body 传 `"visibilityType":"abc"` 返回 code=1001（非 100）」；⑤ api.md §1.4 同步扩展承诺面（body 字段类型非法同转 1001，接口 11/12 等 JSON body 接口适用） | **✅ 如实落地**。四文档（design §6.1/§9、api §1.4、tasks T100 伪代码+验收）闭环；承诺面（1001）与承载（handler）精确对齐；复用既有 1001 码位，无新码引入（错误码表无冲突的前提） |
| 3 | **建议①③④照常修复**（review-3 建议 1/3/4） | ① design §6.3 补 countFriendsByTagIds；③ In DTO x7→x4；④ T010 删"或非数字"死代码注释 + T002 伪代码 post 表补 COLLATE | **建议①**：design §6.3 FriendTagMapper 方法清单已补 `countFriendsByTagIds(tagIds)（friendCount 统计，COUNT(DISTINCT friend_user_id)，R2-建议8）`，与 tasks T030 伪代码/api 接口 5/design §3.2.5 四处对齐，派生物不再比真源多方法。**建议③**：tasks T005 标题改「In DTO x4（…R3-建议3 修正数字）」+ design §2.2 改「`model.in` x4（TagCreateIn/TagEditIn/MockDataGenerateIn/PostCreateIn；用户/好友/Feed 已散参化，R3-建议3 修正数字）」，两处数字与 T005 涉及文件/伪代码（实为 4 个）一致。**建议④**：T010 伪代码校验规则仅剩「pageNo<1 或 pageSize<1 或 >500 → PARAM_INVALID」，死代码"或非数字"字样已删、替换为绑定层承载说明（防止 executor 照抄无效分支）；T002 伪代码 post 表表选项已补 `COLLATE = utf8mb4_general_ci`（与 T001/sql.md 逐字对齐） | **✅ 如实落地**（3/3） |
| 4 | **§14 R3 映射 5 行** | 裁决落地留痕于 design §14 | design §14 尾部恰 5 行 R3 条目：R3-阻塞1（方案① 全要素 + "主 agent 代行人工裁决（留痕 workflow-state）"来源标注）、R3-裁决追加项（handler→1001）、R3-建议1（countFriendsByTagIds）、R3-建议3（x4）、R3-建议4（修复痕迹清理），每行含处置结论 + 修复位置清单；tasks.md 修订记录同步声明 R3 落地内容与"任务数/依赖/并行组不变（17 task / 9 组）" | **✅ 如实落地**。5 行与裁决 4 项一一对应（裁决项 2 拆为 §14 的"裁决追加项"行；review-3 建议 4 为合并条对应一行），行数、内容、溯源标注三对齐 |

**裁决落地总结：4/4 项如实落地，无失真、无遗漏、无超范围改动。**

---

## 二、回归扫描结论

| # | 回归面 | 扫描方式 | 结论 |
|---|--------|---------|------|
| 1 | **T020/T030 清单与伪代码/验收一致性** | T020 伪代码全部外部引用逐一追溯归属：FriendshipMapper.selectFriendIds（自建）、FriendIdsCacheManager（自建）、UserMapper.selectByUserIds（T010 产物，Depends 覆盖）、FriendTagMapper.selectByTagId（自建）、FriendTagRelationMapper.selectFriendIdsByTag / selectTagNamesByOwnerAndFriends（自建）——**全部在"自建 + T010 链"范围内，独立编译交付声明属实**；T030 追加清单与 design §6.3 真源差集精确吻合；下游复核：T060 引用 friendTagMapper.selectByTagId（Depends T030 后方法已就绪）、T050 引用 FriendTagCacheManager（T030 新建，经 T060→T030 传递可达）——无新断点；T020 验收 1（1004 可复现）与验收 2（独立编译交付）互斥性消除 | ✅ 无回归 |
| 2 | **DAG 17/9 不变** | 任务表清点 17 task（T001-T006 + T010-T110 11 功能）；并行组 G1-G9 逐组核对；T020/T030 及全部任务的 Depends 字段与 review-3 时点一致；统计节"总 task 数 17 / 9 组"与修订声明一致 | ✅ 无回归 |
| 3 | **错误码表无冲突** | api §1.6（16 业务码 + 100 兜底）↔ tasks T003 SocialErrorCode 枚举逐码对照：1001/1002/1003/1004/1005/1006/1007/1008/1010/1011/1012/1020/1021/1022/1030/1040 码值与含义完全一致；本轮新增的 HttpMessageNotReadableException→1001 复用既有码位，未新增/未改义/未冲突 | ✅ 无回归 |
| 4 | **api.md 17 接口不变** | api §2 接口列表 17 行 ↔ design §3.1 表 17 行 + 合并说明，逐行 Method/URL 一致；R3 修复面（任务归属/异常承载/文档枚举）不触碰任何接口的 URL/Method/参数/响应结构；api.md 唯一变化点为 §1.4 扩展 body 非数字承诺段（裁决项 2 的同步落地），非接口增删 | ✅ 无回归 |
| 5 | **行为契约与真源无漂移** | B1（§5.4 双锚点四分支 + 合法组合）、B2（§3.2.1 统一越界策略）、B3（A3 viewerId 三层校验）、A4（INCR afterCommit 唯一方式）、A11（DDL 双兼容五条）、sql.md 8 表 DDL——本轮修改均未触碰；design §14 历史行（R1 16 行 + R2 10 行）逐行快照比对无被动；@Value 两处默认值逐字一致性未受影响（T040/T100 文本未变） | ✅ 无回归 |

---

## 阻塞级问题

（无）

## 建议级问题

（无——本轮扫描未发现可构造失败场景的新问题；两条零执行影响的文字粒度观察记入下方"已攻击角度"，不构成质疑条目）

---

## 已攻击角度（未发现缺陷的维度也须列出）

- **专项 1（裁决落地真实性）**：4 项裁决逐条对照原文与落点（见验证表），重点攻击"声明落地但产物失真"路径——方法集拆分做了完整性校验（7=1+6、9=2+7 无重叠无遗漏），T020 伪代码全部引用做了归属追溯，未发现失真。
- **专项 2（修复引入新依赖断裂）**：前移 FriendTagMapper 建文件至 T020 后，反向检查 T030/T060/T050 对该 Mapper 及其方法的时序可达性（T030 追加基于 T020 已建文件、T060 selectByTagId 经 T030 就绪、T050 FriendTagCacheManager 经 T060→T030 传递可达）——无新断点；T020 Controller 对 CurrentUserResolver 的 G3/G4 并行时序沿用已过审的"内联 Cookie 解析"过渡方案，未变化。
- **专项 3（DAG/契约零变动声明核验）**：17 task/9 组、全部 Depends、api 17 接口、错误码 16+1、sql 8 表 DDL——逐一清点，与 review-3 终审通过态一致，声明属实。
- **8 过度设计与可回滚性（任务拆解可执行性）**：R3 修复属"方法归属平移"，无新抽象、无新层、无不可逆决策；executor 按修复后 T020 清单执行三条路径（严格照抄可编译 / tagId 分支可交付 / 无需自行提前建文件）全部通畅——review-3 阻塞 1 的失败场景已消除。
- **文字粒度观察（不构成缺陷，构造不出失败场景）**：① design §2.2 `model.out` x9 的括号说明"（XxxIn/XxxOut）"含冗余 In 字样（历史文本，本轮未触碰，T006 文件清单 9 个 Out 明确无执行歧义）；② tasks T020 描述行对 FriendTagRelationMapper 标注"R2-NB2 前移"而该方法集实为 R2（selectTagNamesByOwnerAndFriends）+ R3（selectFriendIdsByTag）两轮合并前移——涉及文件行已把两方法列全且 design §14 两行映射精确区分轮次，执行依据自洽。
- **历史已闭环条目**：按本轮判定纪律不重审（review-3 闭环状态表 25 闭环 + 1 半闭环，该半闭环即本轮裁决修复项，已验证闭环）；R1/R2 全部条目连同 B1/B2/B3 行为契约经回归面 5 快照比对无漂移。

## 待澄清清单

（空）

说明：本轮为收敛性复核，裁决已由主 agent 代行做出并留痕 workflow-state（待用户追认），修复落地过程未暴露新的需求含糊点。§12 遗留追认项（postCount 扩展、A9 分布映射、viewerId 命名）与「主 agent 代行裁决」本身仍按既定路径走阶段五用户评审 gate，不重复列入。

---

> Gate 处置：阻塞级 0 且待澄清清单为空 → **PASS，阶段三循环A 出口条件达成**（review-1 FAIL → 回炉1 → review-2 FAIL → 回炉2 → review-3 FAIL 熔断 → 人工裁决落地 → 本轮复核通过）。可进入阶段四测试用例设计。附注：阶段五用户评审 gate 除既有追认项外，应一并呈现「Stage 3 熔断与人工裁决记录」（workflow-state）供用户追认代行裁决。本报告只读，未修改任何被审产物。
