# finder=L1 batch-1（数据层+契约层）
```json
[
  {"file":"moments-dao/src/main/resources/schema/social_timeline.sql","line":68,"lens":"L1","severity":"MEDIUM","claim":"post 表裸用字段名 status（TINYINT(2)），违反 mysql-guide 状态/二值字段命名规范，且无法按任一解读自洽","evidence":{"trigger":"mysql-guide 规定非二值状态字段必须用带前缀语义名 xx_status 禁止裸用 status；当前定义 status TINYINT(2) NOT NULL DEFAULT 1，既缺表前缀（应为 post_status）又非二值状态动词命名","failure":"绿地建表窗口内不改，后续 DDL 变更需 ALTER TABLE + 全链路（PostDO.status、PostStatusEnum、PostMapper.xml 两处 status=1、PostDetailOut.status）联动；同表 visibility_type 已按 xx_type 规范命名，status 成为同表唯一裸名状态字段"}},
  {"file":"moments-dao/src/main/java/com/funny/moments/dao/entity/UserDO.java","line":21,"lens":"L1","severity":"LOW","claim":"8 个 DO 主键均为 Long，java-guide §2.4 要求主键 Integer——但与 mysql-guide 通用4字段模板（id bigint）规则冲突","evidence":{"trigger":"java-guide §2.4 明文主键 id 统一 Integer；但 mysql-guide 通用4字段模板为 id bigint(20)，本变更 schema 8 表主键均为 BIGINT AUTO_INCREMENT，DO 侧 Long 与 DDL 一致（UserDO 类注释自述主键 Long 为有意决策）","failure":"两份规范条文互斥，规范执行口径需明确定夺，避免后续模块各自选型"}},
  {"file":"moments-dao/src/main/resources/schema/social_timeline.sql","line":42,"lens":"L1","severity":"LOW","claim":"普通索引名截断字段后缀，不完全符合 mysql-guide idx_字段名 命名规则","evidence":{"trigger":"friend_tag.idx_user 对应 user_id（line 42）、friend_tag_relation.idx_tag 对应 tag_id（line 56）、idx_friend 对应 friend_user_id（line 57）、post_image.idx_post 对应 post_id（line 89）；同文件 friendship.uniq_user_friend、post.idx_user_created 按完整字段名命名","failure":"同文件内索引命名口径不统一，按字段名反查索引需人工映射"}}
]
```
note: 候选 3 条未超上限；Lombok 命中均为存量文件不报；mapper XML xmllint 全通过。
