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
