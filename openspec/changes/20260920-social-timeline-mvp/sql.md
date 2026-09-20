# SQL 变更脚本 — Social Timeline MVP

> 基于技术设计：`openspec/changes/20260920-social-timeline-mvp/design.md` §4
> 生成时间：2026-09-20（design-review-1 回炉修订：补 5.7/8.0 兼容性标注 + 建议 4 唯一键说明）
> 数据库：**兼容 MySQL 5.7.44（本地 dev 实际实例，brew 服务）与 8.0（PRD 目标环境）** / InnoDB / utf8mb4 + utf8mb4_general_ci / 事务隔离随实例
> 变更类型：**全新建设**（8 张新建表，无修改表、无删除表）

## 0. 兼容性约束（A11，主会话 2026-09-20 追加）

DDL 双版本兼容规则（已逐条核对下方脚本）：

| 约束 | 说明 |
|------|------|
| 字符集/排序规则 | 统一 `utf8mb4` + `utf8mb4_general_ci`；**禁用 8.0 专有 `utf8mb4_0900_ai_ci`**（5.7 无法解析） |
| DEFAULT 值 | 仅允许字面量与 `CURRENT_TIMESTAMP`（`DEFAULT CURRENT_TIMESTAMP` / `ON UPDATE CURRENT_TIMESTAMP` 为 5.7/8.0 共同支持的标准形式）；**无 DEFAULT 表达式函数**（8.0 特性） |
| 索引 | 无函数索引（8.0 特性）、无 invisible index；全部普通/唯一 BTree 索引 |
| 约束 | **无 CHECK 约束**（5.7 解析但忽略、8.0 强制执行，行为不一致，直接不用）；无外键（规范亦禁止） |
| 类型 | `TINYINT(2)/TINYINT(1)` 显示宽度两版本均合法（8.0 仅废弃告警）；`DATETIME`/`BIGINT`/`VARCHAR` 无版本差异 |

## 0.1 执行方式（D1 决策）

| 通道 | 说明 |
|------|------|
| 自动（主通道，dev） | 下列脚本同源落盘 `moments-dao/src/main/resources/schema/social_timeline.sql`，由 `SchemaInitRunner`（@Profile("dev")，ApplicationRunner + ScriptUtils）在应用启动时幂等执行（`CREATE TABLE IF NOT EXISTS`，可重复执行） |
| 手工（备查） | 人工在 dev 库执行下列脚本同样幂等；生产/其他环境不存在（实验室项目） |

规范符合性：表/字段 snake_case、小写、无数字开头；全表含 4 通用字段（id bigint PK AUTO_INCREMENT / created_stime / modified_stime / is_del）；字段全 COMMENT，枚举列全部值:含义映射；索引命名 pk_ / uniq_ / idx_；无外键/检查约束；二值字段仅 is_del（既定约定例外）；状态/类型字段带前缀语义名（visibility_type / status 属 post 业务既定 PRD 字段，status 保留 PRD 命名）；每新表 1-3 个二级索引。

## 1. 建表 DDL（8 张）

```sql
-- =============================================================
-- 1. user 用户表（PRD §2.1）
-- =============================================================
CREATE TABLE IF NOT EXISTS `user` (
  `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键（userId）',
  `nickname`       VARCHAR(64)  NOT NULL DEFAULT ''      COMMENT '昵称（允许重名）',
  `avatar`         VARCHAR(255) NOT NULL DEFAULT ''      COMMENT '头像URL（/images/{fileName}）',
  `gender`         TINYINT(2)   NOT NULL DEFAULT 0       COMMENT '性别：0-未知 1-男 2-女',
  `city`           VARCHAR(32)  NOT NULL DEFAULT ''      COMMENT '城市',
  `created_stime`  DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `modified_stime` DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  `is_del`         TINYINT(1)   NOT NULL DEFAULT 0       COMMENT '是否删除 0-正常 1-删除',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_nickname` (`nickname`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '用户表';

-- =============================================================
-- 2. friendship 好友关系表（PRD §2.2；双向关系两条对称记录，C11）
-- =============================================================
CREATE TABLE IF NOT EXISTS `friendship` (
  `id`              BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id`         BIGINT      NOT NULL DEFAULT 0      COMMENT '关系一方用户ID',
  `friend_user_id`  BIGINT      NOT NULL DEFAULT 0      COMMENT '关系另一方用户ID（与user_id对称成对存储）',
  `created_stime`   DATETIME    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `modified_stime`  DATETIME    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  `is_del`          TINYINT(1)  NOT NULL DEFAULT 0      COMMENT '是否删除 0-正常 1-删除',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uniq_user_friend` (`user_id`, `friend_user_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '好友关系表';

-- =============================================================
-- 3. friend_tag 好友标签表（PRD §2.3；标签归属创建人，C5）
-- =============================================================
CREATE TABLE IF NOT EXISTS `friend_tag` (
  `id`             BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键（tagId）',
  `user_id`        BIGINT      NOT NULL DEFAULT 0      COMMENT '标签创建人用户ID',
  `tag_name`       VARCHAR(32) NOT NULL DEFAULT ''     COMMENT '标签名（默认8标签：家人/亲戚/同事/同学/朋友/球友/客户/其他）',
  `created_stime`  DATETIME    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `modified_stime` DATETIME    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  `is_del`         TINYINT(1)  NOT NULL DEFAULT 0      COMMENT '是否删除 0-正常 1-删除',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_user` (`user_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '好友标签表';

-- =============================================================
-- 4. friend_tag_relation 标签-好友绑定表（PRD §2.3/§2.4）
-- =============================================================
CREATE TABLE IF NOT EXISTS `friend_tag_relation` (
  `id`              BIGINT     NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tag_id`          BIGINT     NOT NULL DEFAULT 0      COMMENT '标签ID（friend_tag.id）',
  `friend_user_id`  BIGINT     NOT NULL DEFAULT 0      COMMENT '被打标签的好友用户ID',
  `created_stime`   DATETIME   DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `modified_stime`  DATETIME   DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  `is_del`          TINYINT(1) NOT NULL DEFAULT 0      COMMENT '是否删除 0-正常 1-删除',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_tag` (`tag_id`) USING BTREE,
  INDEX `idx_friend` (`friend_user_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '标签-好友绑定表';

-- =============================================================
-- 5. post 朋友圈帖子表（PRD §3.2；C7/D4）
-- =============================================================
CREATE TABLE IF NOT EXISTS `post` (
  `id`              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键（postId）',
  `user_id`         BIGINT        NOT NULL DEFAULT 0      COMMENT '作者用户ID',
  `content`         VARCHAR(2000) NOT NULL DEFAULT ''     COMMENT '文字内容（≤2000字）',
  `visibility_type` TINYINT(2)    NOT NULL DEFAULT 1      COMMENT '可见范围：1-公开 2-私密 3-部分可见 4-不给谁看',
  `status`          TINYINT(2)    NOT NULL DEFAULT 1      COMMENT '业务状态：1-正常 0-删除（软删走is_del）',
  `created_stime`   DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '发布时间（Feed排序键之一）',
  `modified_stime`  DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  `is_del`          TINYINT(1)    NOT NULL DEFAULT 0      COMMENT '是否删除 0-正常 1-删除',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_user_created` (`user_id`, `created_stime`) USING BTREE,
  INDEX `idx_created_stime` (`created_stime`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '朋友圈帖子表';

-- =============================================================
-- 6. post_image 帖子图片表（PRD §3.2；图片单独存储带排序）
-- =============================================================
CREATE TABLE IF NOT EXISTS `post_image` (
  `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `post_id`        BIGINT       NOT NULL DEFAULT 0      COMMENT '所属帖子ID（post.id）',
  `image_url`      VARCHAR(255) NOT NULL DEFAULT ''     COMMENT '图片URL（/images/{fileName}）',
  `sort`           INT          NOT NULL DEFAULT 0      COMMENT '排序号：1-9（单帖最多9图）',
  `created_stime`  DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `modified_stime` DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  `is_del`         TINYINT(1)   NOT NULL DEFAULT 0      COMMENT '是否删除 0-正常 1-删除',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_post` (`post_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '帖子图片表';

-- =============================================================
-- 7. post_visibility_user 帖子指定好友表（PRD §3.6/§3.7）
-- =============================================================
CREATE TABLE IF NOT EXISTS `post_visibility_user` (
  `id`             BIGINT     NOT NULL AUTO_INCREMENT COMMENT '主键',
  `post_id`        BIGINT     NOT NULL DEFAULT 0      COMMENT '所属帖子ID（post.id）',
  `user_id`        BIGINT     NOT NULL DEFAULT 0      COMMENT '指定可见/不可见用户ID（type=3可见白名单/type=4排除名单）',
  `created_stime`  DATETIME   DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `modified_stime` DATETIME   DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  `is_del`         TINYINT(1) NOT NULL DEFAULT 0      COMMENT '是否删除 0-正常 1-删除',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uniq_post_user` (`post_id`, `user_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '帖子指定好友表';

-- =============================================================
-- 8. post_visibility_tag 帖子指定标签表（PRD §3.6/§3.7；删标签不级联，C13）
-- =============================================================
CREATE TABLE IF NOT EXISTS `post_visibility_tag` (
  `id`             BIGINT     NOT NULL AUTO_INCREMENT COMMENT '主键',
  `post_id`        BIGINT     NOT NULL DEFAULT 0      COMMENT '所属帖子ID（post.id）',
  `tag_id`         BIGINT     NOT NULL DEFAULT 0      COMMENT '指定可见/不可见标签ID（friend_tag.id，标签软删后按tagId匹配自然失效）',
  `created_stime`  DATETIME   DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `modified_stime` DATETIME   DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  `is_del`         TINYINT(1) NOT NULL DEFAULT 0      COMMENT '是否删除 0-正常 1-删除',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uniq_post_tag` (`post_id`, `tag_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '帖子指定标签表';
```

## 2. 索引设计说明

| 表 | 索引 | 类型 | 支撑查询 |
|----|------|------|---------|
| user | idx_nickname | NORMAL | 用户列表昵称检索（预留） |
| friendship | uniq_user_friend | UNIQUE (user_id, friend_user_id) | selectFriendIds（前缀 user_id 点查）+ 对称双记录防重兜底 |
| friend_tag | idx_user | NORMAL | 标签列表/归属校验（user_id 点查） |
| friend_tag_relation | idx_tag / idx_friend | NORMAL | 按标签查好友 / canView 按 friend_user_id 反查标签（join 驱动） |
| post | idx_user_created | NORMAL (user_id, created_stime) | listUserPosts / selectFeedPage IN 列表 ref 访问 |
| post | idx_created_stime | NORMAL | Feed 时间序扫描（排序键前缀） |
| post_image | idx_post | NORMAL | 按帖子批量取图（ORDER BY sort） |
| post_visibility_user | uniq_post_user | UNIQUE | selectUserIdsByPostId 点查 + 发布防重插 |
| post_visibility_tag | uniq_post_tag | UNIQUE | selectTagIdsByPostId 点查 + 发布防重插 |

不建 DB 唯一约束说明（design §4.1 + review-1 建议 4 裁决）：friend_tag / friend_tag_relation 的业务唯一性（同用户同名标签、同标签同好友绑定）由 Service 层在 `is_del = 0` 范围内查重保证——软删记录长期占用 DB 唯一键会导致合法操作被误拒，典型场景：**解绑（软删）后重绑走 INSERT 新记录，`uniq(tag_id, friend_user_id)` 会挡住合法重绑**（软删旧行占键）；`INSERT IGNORE` 同样会静默吞掉重绑的合法写入。并发绑定窗口（查重与 INSERT 间无锁）由**展示组装侧 Stream.distinct() 去重兜底 + 前端防抖**（design §5.6 规则 5），实验室规模接受，生产化可加 DistributedLock。

## 3. 数据初始化（模拟数据，业务链路承担）

种子数据不走 SQL 脚本：`POST /api/mock-data` 生成（用户/好友/标签/绑定/可选帖子，design §5.5），分批 batchInsert。默认 8 标签由该链路按用户落库（D2）。

## 4. 回滚脚本

不提供 DROP/TRUNCATE（规范禁止）。实验室回滚 = 代码 git revert；数据清理按软删规范手工执行（带 WHERE 条件的 UPDATE，例如 `UPDATE post SET is_del = 1 WHERE is_del = 0;`——与 `POST /api/mock-data {"clear":true}` 的清空语义一致）。

## 5. 自检清单

- [x] 8 表全含 4 通用字段（id/created_stime/modified_stime/is_del）
- [x] 字段全 COMMENT，枚举字段（gender/visibility_type/status）列全值:含义
- [x] 索引命名 pk_/uniq_/idx_；每表 1-3 个二级索引
- [x] 无外键、无检查约束、无 DROP/TRUNCATE/无 WHERE 的 UPDATE
- [x] CREATE TABLE IF NOT EXISTS 幂等（Runner 可重复执行）
- [x] 主键 DB bigint（C9）
- [x] **双版本兼容（A11）：全表 utf8mb4_general_ci；无 utf8mb4_0900_ai_ci、无 DEFAULT 表达式函数、无函数索引、无 CHECK 约束**（review-1 回炉新增）
