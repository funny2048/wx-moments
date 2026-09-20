# Candidates（S2 去重后，#1..#16）

去重规则：file+line 相同且主张相同 → 合并（lens 并集、证据取更完整者、severity 取最高）。
合并记录：
- #1 = L1-b2#C1 + L2-b2#D2 + L3#G3（FriendTagServiceImpl evictAll 无降级）
- #2 = L1-b3#E1 + L3#G2（MockDataController 破坏性接口）
- #3 = L1-b3#E2 + L3#G1（写接口无签名认证）

| # | file | line | lens | sev | claim |
|---|------|------|------|-----|-------|
| 1 | moments-service/.../impl/FriendTagServiceImpl.java | 97/117/141/172/182 | L1∪L2∪L3 | HIGH | 标签 5 写路径直调 evictAll() 无 Redis 异常降级，违背 C12 弱依赖定稿；createTag/deleteTag 已提交却报错（重试伪 1005/1004）；同变更读路径与 MockData.evictCaches 均有 catch 佐证为遗漏而非取舍 |
| 2 | moments-web/.../controller/social/MockDataController.java | 37 | L1∪L3 | HIGH | clear=true 软删 8 表全量数据无鉴权/签名/@Profile 守卫（同变更 SchemaInitRunner 有 @Profile("dev")，口径不一致）；MAX_USER_COUNT=100000 单请求长时批量写 |
| 3 | moments-web/.../controller/social/PostController.java | 47 | L1∪L3 | HIGH | C 端写接口（发帖/删帖/标签/上传）无 apiKey+timestamp+sign 签名，SignVerifyInterceptor 仅拦 /leads/**；身份靠明文 viewerId 可伪造（design §6.1 A3/B3 定稿实验室 mock，豁免需人工确认） |
| 4 | moments-web/.../support/CurrentUserResolver.java | 44 | L1 | MEDIUM | 写操作归属校验身份基准取自客户端可控 viewerId/mockUserId，构成水平越权面（design §6.1 已文档化为实验室机制，预期豁免） |
| 5 | moments-dao/.../schema/social_timeline.sql | 28 | L2 | MEDIUM | uniq_user_friend 未含 is_del，与软删互斥：软删残留 pair 永久占位，同 pair 重建整批插入失败回滚 |
| 6 | moments-dao/.../mapper/FriendTagMapper.xml | 71 | L2 | MEDIUM | 全项目 6 处 foreach IN 无空集合 XML 防护，纯靠 Service 前置拦截，漏一处即 BadSqlGrammar 500（FriendTagMapper 71/85、FriendTagRelation 32、UserMapper 37、PostImage 48、PostMapper 49） |
| 7 | moments-service/.../impl/MockDataServiceImpl.java | 377 | L2 | MEDIUM | generateTags tagId 回读时序错位：ownerBuffer 满 500 回读时 buffer 仍有未落库标签行，映射永久缺失 → 绑定/type3-4 名单静默缺失，接口不报错（userCount>500 触发，默认 100） |
| 8 | moments-service/.../impl/UserServiceImpl.java | 60 | L1 | MEDIUM | listUsers pageNo 无上界校验，(no-1)*size int 溢出为负 → LIMIT 负偏移 SQL 报错 500；深分页全表扫描 |
| 9 | moments-service/.../impl/LocalImageServiceImpl.java | 81 | L1 | MEDIUM | upload 仅校验扩展名+大小，不校验文件内容（魔数/Content-Type）即落盘对外静态目录 /images/ |
| 10 | moments-dao/.../mapper/PostMapper.xml | 31 | L3 | MEDIUM | selectUserPosts/selectFeedPage 硬编码 status=1 / visibility_type!=2 裸魔法数字，PostStatusEnum/VisibilityTypeEnum 未在 SQL 消费且无注释 |
| 11 | moments-dao/.../schema/social_timeline.sql | 68 | L1 | MEDIUM | post 表裸用 status（TINYINT(2)），违反 mysql-guide xx_status 命名；同表 visibility_type 已规范，status 唯一裸名 |
| 12 | moments-dao/.../entity/UserDO.java | 21 | L1 | LOW | 8 DO 主键 Long 与 java-guide §2.4 Integer 冲突，但与 mysql-guide bigint 模板自洽（规范互斥需口径裁决） |
| 13 | moments-dao/.../schema/social_timeline.sql | 42 | L1 | LOW | idx_user/idx_tag/idx_friend/idx_post 索引名截断字段后缀，同文件命名口径不一 |
| 14 | moments-dao/.../mapper/FriendTagRelationMapper.xml | 36 | L2 | LOW | selectTagNamesByOwnerAndFriends GROUP BY 含 tag_id 与 Javadoc 去重口径不符，并发同名标签输出重复名 |
| 15 | moments-service/.../impl/PostServiceImpl.java | 103 | L1 | LOW | createPost ~103 行 / getFeed ~104 行超 §12 方法体 ≤50 行建议 |
| 16 | moments-web/.../static/assets/app.js | 57 | L2 | LOW | getQueryParam 畸形百分号编码抛未捕获 URIError，页面脚本初始化中断（自伤型） |

severity 分布：HIGH ×3（#1/#2/#3）、MEDIUM ×8（#4-#11）、LOW ×5（#12-#16）；finder 覆盖：L1×3 + L2×3 + L3×1 + L4×1（L4 零候选）。
