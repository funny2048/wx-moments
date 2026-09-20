# 任务清单: 20260920-social-timeline-mvp

> 基于模板：`workflow/daily/templates/tasks-template-simple.md` v1.0
> 关联技术设计：`openspec/changes/20260920-social-timeline-mvp/design.md`
> 生成时间：2026-09-20
> 任务总数：17 | 可并行组：9 组
> 修订：2026-09-20 design-review-1 回炉——B1（Feed 双锚点/空页组合/前端停止规则，T080/T110）、B2（越界统一抛 1001，T010/T070/T080）、B3（viewerId 强校验，T100）+ 建议级 11 条落对应 task（映射表见 design.md §14）；任务数/依赖/并行组不变
> 修订：2026-09-21 design-review-2 回炉——NB1（MethodArgumentTypeMismatchException→1001 承载，T100）、NB2（FriendTagRelationMapper+selectTagNamesByOwnerAndFriends 归属前移 T020，T030 改追加）+ 建议级 8 条落对应 task（映射表见 design.md §14 R2 行）；任务数/依赖/并行组不变
> 修订：2026-09-21 design-review-3 终审熔断后按**主 agent 代行人工裁决**（留痕 workflow-state）落地——裁决方案①（FriendTagMapper 建文件前移 T020 仅含 selectByTagId，selectFriendIdsByTag 归 T020，T030 两 Mapper 改追加，T020 tagId 分支四者自洽）+ 裁决追加（HttpMessageNotReadableException→1001，T100）+ review-3 建议①③④（design §6.3 补 countFriendsByTagIds 与归属标注 / In DTO x4 / T010 死代码注释与 T002 COLLATE）；映射见 design.md §14 R3 行；任务数/依赖/并行组不变（17 task / 9 组）

## 文档信息

| 字段 | 内容 |
|------|------|
| 关联 change-dir | openspec/changes/20260920-social-timeline-mvp |
| 关联 tech-design | design.md（§2 架构 / §3 接口 / §4 数据模型 / §5 流程 / §6 分层） |
| 关联 sql | sql.md（8 表 DDL，同源落 dao 资源目录） |
| 验证命令 | 每任务完成跑 `mvn test-compile -pl moments-web -am`（联动编译五模块）；mapper XML 变更后必跑 `find moments-dao/src/main/resources/mapper -name '*.xml' -exec xmllint --noout {} \;` |

## 任务拆分规则

- 一个 task = 一个功能纵向切片（Mapper+Service+Controller 同 task，铁律）或一层铺底（L0/L1）
- 阶段二唯一可拆边界是**功能之间**；内部功能（可见性判断无 Controller）与横切装配（A 类支撑）按功能单元成 task
- 同文件跨 task 追加（如 PostController 在 T060 建类、T070 追加方法）通过 Depends 串行保证，禁止并行

---

## 阶段一：基础铺底

| # | Layer | 标题 | Depends | 预估 | 状态 |
|---|-------|------|---------|------|------|
| T001 | L0 | 一期 4 表 DDL（user/friendship/friend_tag/friend_tag_relation） | — | 0.5h | ✅ |
| T002 | L0 | 二期 4 表 DDL（post/post_image/post_visibility_user/post_visibility_tag） | — | 0.5h | ✅ |
| T003 | L0 | 枚举 x4 + 错误码 SocialErrorCode + 缓存/正则常量追加 | — | 1.5h | ✅ |
| T004 | L1 | Entity：8 张表 DO（moments-dao） | T001, T002 | 2h | ✅ |
| T005 | L1 | In DTO x4（moments-client model.in，R3-建议3 修正数字） | T003 | 1h | ✅ |
| T006 | L1 | Out DTO x9（moments-client model.out） | T003, T004 | 1.5h | ✅ |

## 阶段二：功能纵向

| # | 功能 | Layer | 标题 | Depends | 预估 | 状态 |
|---|------|-------|------|---------|------|------|
| T010 | 用户查询 | L2-L4 | 用户列表/详情（Mapper+Service+Controller） | T004, T006 | 2h | ✅ |
| T020 | 好友查询 | L2-L4 | 我的好友/详情/好友ID缓存（Mapper+Service+Controller+CacheManager） | T010 | 3h | ✅ |
| T030 | 标签管理 | L2-L4 | 标签 CRUD+绑定/解绑+级联+标签缓存（Mapper x2+Service+Controller） | T020 | 4h | ✅ |
| T040 | 图片 | L2-L4 | 本地图片上传+占位图池（Service+Controller，无 Mapper） | T003 | 3h | ✅ |
| T060 | 发帖 | L2-L4 | 发布朋友圈 4 表事务（PostMapper+PostImageMapper+可见性insert+Service+Controller） | T010, T030 | 3.5h | ✅ |
| T050 | 可见性判断 | L2-L3 | canView 统一判断（可见性两 Mapper 查询+VisibilityService，内部功能无 Controller） | T060 | 2.5h | ✅ |
| T070 | 帖子读写 | L2-L4 | 帖子详情/软删/按用户查（Service 扩展+Controller 扩展） | T050 | 3h | ✅ |
| T080 | Feed | L2-L4 | Cursor 分页 Feed（selectFeedPage+Service+Controller） | T050, T020 | 4h | ✅ |
| T090 | 模拟数据 | L2-L4 | 批量生成模拟数据（Service+Controller，复用各 Mapper） | T060, T040 | 4h | ✅ |
| T100 | 横切装配 | L4 | CurrentUserResolver+BizExceptionAdvice+SchemaInitRunner+WebConfig 静态映射 | T002, T003 | 2h | ✅ |
| T110 | 前端页面 | — | 9 个静态页（8 业务页+index）+css/js（样式贴近微信朋友圈） | T070, T080, T090, T040 | 6h | ✅ |

> T050/T060 编号说明：发帖（T060）先于可见性（T050）实施——canView 依赖 T060 建立的 PostMapper.selectById；编号保持功能十位段与 design §2.2/§6 对应，执行顺序以 Depends 为准。

## 并行组

| 组 | task 列表 | 说明 |
|----|-----------|------|
| G1 | T001, T002, T003 | L0 无依赖，可并行 |
| G2 | T004, T005, T006 | L1 依赖 G1，内部可并行 |
| G3 | T010, T040, T100 | 互不依赖可并行（T010 依赖 T004/T006；T040 依赖 T003；T100 依赖 T002/T003） |
| G4 | T020 | after T010 |
| G5 | T030 | after T020 |
| G6 | T060 | after T030（与 T040/T100 可并行） |
| G7 | T050 → T070；T090 | T050 after T060；T070 after T050；T090 after T060+T040（与 T050 链并行） |
| G8 | T080 | after T050+T020（与 T070/T090 并行） |
| G9 | T110 | after T070+T080+T090（全部 API 就绪后做页面） |

---

## 任务详细

### T001 · L0 · 一期 4 表 DDL

**功能**: 基础铺底
**Depends**: —
**预估**: 0.5h

**描述**: 按 sql.md §1（1-4 号表）编写 user/friendship/friend_tag/friend_tag_relation 建表 SQL，落盘 dao 资源 schema 文件（与 T002 共用同一文件，先建骨架再追加，串行无冲突——T001 先创建文件）。

**涉及文件**:
- `moments-dao/src/main/resources/schema/social_timeline.sql`（新建）

**伪代码**: 完整 DDL 见 sql.md §1（1-4），要点：

```sql
CREATE TABLE IF NOT EXISTS `user` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键（userId）',
  `nickname` VARCHAR(64) NOT NULL DEFAULT '' COMMENT '昵称（允许重名）',
  `avatar` VARCHAR(255) NOT NULL DEFAULT '' COMMENT '头像URL',
  `gender` TINYINT(2) NOT NULL DEFAULT 0 COMMENT '性别：0-未知 1-男 2-女',
  `city` VARCHAR(32) NOT NULL DEFAULT '' COMMENT '城市',
  `created_stime` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `modified_stime` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  `is_del` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否删除 0-正常 1-删除',
  PRIMARY KEY (`id`), INDEX `idx_nickname` (`nickname`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用户表';
-- friendship：user_id+friend_user_id，UNIQUE INDEX uniq_user_friend(user_id, friend_user_id)（对称双记录防重）
-- friend_tag：user_id（创建人 C5）+ tag_name，INDEX idx_user(user_id)（不建 DB 唯一键，Service 查重）
-- friend_tag_relation：tag_id+friend_user_id，INDEX idx_tag(tag_id)、idx_friend(friend_user_id)
```

**验收标准**:
- [ ] 4 表全含通用 4 字段；索引 pk_/uniq_/idx_ 命名；字段全 COMMENT
- [ ] CREATE TABLE IF NOT EXISTS 幂等
- [ ] **与 sql.md §1（1-4）逐字一致（含 `COLLATE = utf8mb4_general_ci`，R2-建议4：防 8.0 目标环境默认落 0900 排序规则）**
- [ ] dev 库手工执行通过（或待 T100 Runner 验证）

---

### T002 · L0 · 二期 4 表 DDL

**功能**: 基础铺底
**Depends**: —
**预估**: 0.5h

**描述**: 按 sql.md §1（5-8 号表）追加 post/post_image/post_visibility_user/post_visibility_tag 建表 SQL 到 schema 文件。

**涉及文件**:
- `moments-dao/src/main/resources/schema/social_timeline.sql`（追加）

**伪代码**: 完整 DDL 见 sql.md §1（5-8），要点：

```sql
CREATE TABLE IF NOT EXISTS `post` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键（postId）',
  `user_id` BIGINT NOT NULL DEFAULT 0 COMMENT '作者用户ID',
  `content` VARCHAR(2000) NOT NULL DEFAULT '' COMMENT '文字内容（≤2000字）',
  `visibility_type` TINYINT(2) NOT NULL DEFAULT 1 COMMENT '可见范围：1-公开 2-私密 3-部分可见 4-不给谁看',
  `status` TINYINT(2) NOT NULL DEFAULT 1 COMMENT '业务状态：1-正常 0-删除',
  `created_stime` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '发布时间',
  `modified_stime` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  `is_del` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否删除 0-正常 1-删除',
  PRIMARY KEY (`id`), INDEX `idx_user_created` (`user_id`, `created_stime`), INDEX `idx_created_stime` (`created_stime`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='朋友圈帖子表';
-- post_image：post_id+image_url+sort(1-9)，INDEX idx_post(post_id)
-- post_visibility_user：post_id+user_id，UNIQUE INDEX uniq_post_user(post_id, user_id)
-- post_visibility_tag：post_id+tag_id，UNIQUE INDEX uniq_post_tag(post_id, tag_id)
```

**验收标准**:
- [ ] 同 T001 规范检查；与 sql.md §1（5-8）逐字一致（含 `COLLATE = utf8mb4_general_ci`，R2-建议4）

---

### T003 · L0 · 枚举 + 错误码 + 常量

**功能**: 基础铺底
**Depends**: —
**预估**: 1.5h

**描述**: 按 design §3（错误码）/§4（枚举值域）/§8（缓存 key）新增枚举与错误码，向既有常量类追加 key/正则（只追加不动既有行）。

**涉及文件**:
- `moments-common/src/main/java/com/funny/moments/common/enums/GenderEnum.java`（新建）
- `moments-common/src/main/java/com/funny/moments/common/enums/VisibilityTypeEnum.java`（新建）
- `moments-common/src/main/java/com/funny/moments/common/enums/PostStatusEnum.java`（新建）
- `moments-common/src/main/java/com/funny/moments/common/enums/SocialErrorCode.java`（新建）
- `moments-common/src/main/java/com/funny/moments/common/consts/MockDataConsts.java`（新建：默认 8 标签名/分布权重/昵称池/城市池）
- `moments-common/.../consts/CacheKeyConsts.java`（追加缓存 key 常量）
- `moments-common/.../consts/PatternConsts.java`（追加正则常量）

**伪代码**:

```java
// VisibilityTypeEnum.java —— 完整枚举结构（byCode 反查，禁魔法数字）
public enum VisibilityTypeEnum {
    PUBLIC(1, "公开"), PRIVATE(2, "私密"), PART_VISIBLE(3, "部分可见"), EXCLUDE(4, "不给谁看");
    private final Integer code; private final String desc;
    VisibilityTypeEnum(Integer code, String desc) { this.code = code; this.desc = desc; }
    public Integer getCode() { return code; } public String getDesc() { return desc; }
    public static VisibilityTypeEnum byCode(Integer code) {
        return Stream.of(values()).filter(e -> e.getCode().equals(code)).findFirst().orElse(null); }
}
// GenderEnum: UNKNOWN(0,"未知")/MALE(1,"男")/FEMALE(2,"女") + byCode
// PostStatusEnum: NORMAL(1,"正常")/DELETED(0,"删除") + byCode

// SocialErrorCode.java —— 实现 com.funny.framework.core.exception.ErrorCode（错误码>1000 业务段）
public enum SocialErrorCode implements ErrorCode {
    PARAM_INVALID(1001, "参数校验失败"), USER_NOT_FOUND(1002, "用户不存在"),
    CURRENT_USER_REQUIRED(1003, "未选择当前用户"), TAG_NOT_FOUND(1004, "标签不存在"),
    TAG_NAME_DUPLICATED(1005, "标签名重复"), NOT_TAG_OWNER(1006, "非标签归属人"),
    FRIEND_REQUIRED(1007, "仅可对好友打标签"), TAG_ALREADY_BOUND(1008, "标签已绑定该好友"),
    POST_NOT_FOUND(1010, "帖子不存在或已删除"), POST_NO_PERMISSION(1011, "无权查看该帖子"),
    NOT_POST_AUTHOR(1012, "仅作者可删除该帖子"), IMAGE_FORMAT_UNSUPPORTED(1020, "图片格式不支持"),
    IMAGE_SIZE_EXCEEDED(1021, "图片超过5MB"), IMAGE_COUNT_EXCEEDED(1022, "单帖图片超过9张"),
    CURSOR_INVALID(1030, "分页游标非法"), MOCK_CONFIG_INVALID(1040, "模拟数据配置非法");
    private final int code; private final String message;
    // 构造 + getCode() + getMessage()
}

// CacheKeyConsts 追加（%s 风格）
public static final String CACHE_MOMENTS_FRIEND_IDS = "moments:frd:%s:%s";   // {fver}:{userId}
public static final String CACHE_MOMENTS_FRIEND_TAGS = "moments:ftag:%s:%s:%s"; // {ftagver}:{ownerId}:{friendId}
public static final String CACHE_MOMENTS_FRIEND_VER = "moments:fver";        // 好友缓存全局版本
public static final String CACHE_MOMENTS_FTAG_VER = "moments:ftagver";       // 标签缓存全局版本

// PatternConsts 追加（预编译 static final，禁方法体内 compile）
public static final Pattern IMAGE_FILE_NAME = Pattern.compile("^[A-Za-z0-9._-]+$");
public static final Pattern FEED_CURSOR = Pattern.compile("^\\d{13}:\\d{1,18}$");  // R2-建议7：id 段限 18 位，防 Long.parseLong 溢出落 code=100

// MockDataConsts：DEFAULT_TAG_NAMES(家人/亲戚/同事/同学/朋友/球友/客户/其他)、
// TAG_WEIGHTS(同事25/同学20/朋友25/家人5/亲戚5/球友10/客户5/其他5)、SURNAME_POOL/ GIVEN_NAME_POOL/ CITY_POOL
```

**验收标准**:
- [ ] 枚举覆盖设计全部值域且 byCode 可反查；错误码与 api.md §1.6 完全一致
- [ ] 常量只追加不修改既有行
- [ ] `mvn test-compile -pl moments-common -am` 通过

---

### T004 · L1 · Entity：8 张表 DO

**功能**: 基础铺底
**Depends**: T001, T002
**预估**: 2h

**描述**: 按 design §4.1 建 8 个 DO（手写 getter/setter 禁 Lombok；主键 Long；String setter trim；is_del 加 @TableLogic）。

**涉及文件**:
- `moments-dao/src/main/java/com/funny/moments/dao/entity/`：UserDO / FriendshipDO / FriendTagDO / FriendTagRelationDO / PostDO / PostImageDO / PostVisibilityUserDO / PostVisibilityTagDO.java（8 个新建）

**伪代码**:

```java
@TableName("post")
public class PostDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String content;          // setter: this.content = content == null ? null : content.trim();
    private Integer visibilityType;  // tinyint→Integer（CodeGenerator 既有转换约定）
    private Integer status;
    @TableLogic
    private Integer isDel;
    // java.util.Date createdStime/modifiedStime + 全字段手写 getter/setter
}
// 其余 7 DO 同构：UserDO(nickname/avatar/gender/city)、FriendshipDO(userId/friendUserId)、
// FriendTagDO(userId/tagName)、FriendTagRelationDO(tagId/friendUserId)、PostImageDO(postId/imageUrl/sort)、
// PostVisibilityUserDO(postId/userId)、PostVisibilityTagDO(postId/tagId)
```

**验收标准**:
- [ ] 字段与 sql.md 一一对应；包装类型；@TableLogic 软删
- [ ] `mvn test-compile -pl moments-dao -am` 通过

---

### T005 · L1 · In DTO x4

**功能**: 基础铺底
**Depends**: T003
**预估**: 1h

**描述**: 按 design §3.2/§6.4 建入参 DTO（三层项目 XxxIn 后缀，手写 getter/setter，字段包装类型）。

**涉及文件**:
- `moments-client/src/main/java/com/funny/moments/model/in/`：TagCreateIn / TagEditIn / MockDataGenerateIn / PostCreateIn.java（4 个新建；用户/好友/Feed 为散参无 In DTO）

**伪代码**:

```java
// PostCreateIn.java
public class PostCreateIn {
    private String content;                    // 可选，≤2000
    private List<String> imageUrls;            // 可选，≤9，顺序即 sort
    private Integer visibilityType;            // 必填 1-4
    private List<Long> visibilityTagIds;       // type=3/4 生效
    private List<Long> visibilityUserIds;      // type=3/4 生效
    // 手写 getter/setter
}
// TagCreateIn/TagEditIn: tagName(String)
// MockDataGenerateIn: userCount/avgFriendsPerUser/extraTagsPerUser/postCount(Integer) + clear(Boolean)
```

**验收标准**:
- [ ] 与 design §3.2 字段/类型/校验一致；无 In/Req 混用
- [ ] `mvn test-compile -pl moments-client -am` 通过

---

### T006 · L1 · Out DTO x9

**功能**: 基础铺底
**Depends**: T003, T004
**预估**: 1.5h

**描述**: 按 design §3.2 建出参 DTO（含 xxTypeStr/xxTimeStr 组装字段；List 无值给空数组禁 null）。

**涉及文件**:
- `moments-client/src/main/java/com/funny/moments/model/out/`：UserPageOut / UserOut / FriendOut / FriendDetailOut / TagOut / MockDataGenerateOut / PostDetailOut / FeedOut / ImageUploadOut.java（9 个新建）

**伪代码**:

```java
// PostDetailOut.java
public class PostDetailOut {
    private Long postId; private Long userId; private String nickname; private String avatar;
    private String content; private List<String> imageUrls = new ArrayList<>();
    private Integer visibilityType; private String visibilityTypeStr;
    private Integer status; private String createTimeStr;
    // 手写 getter/setter
}
// FeedOut: List<FeedItemOut> items（内部类或复用 PostDetailOut 子集）+ String nextCursor + Boolean hasMore
// UserPageOut: Long total + List<UserOut> users
// FriendOut/FriendDetailOut: UserOut 字段 + List<String> tagNames
// TagOut: Long tagId + String tagName + Integer friendCount + String createdTimeStr
// MockDataGenerateOut/ImageUploadOut: 见 design §3.2.11/§3.2.17
```

**验收标准**:
- [ ] 与 api.md §3 字段说明一一对应（含数组子字段）
- [ ] `mvn test-compile -pl moments-client -am` 通过

---

### T010 · [用户查询] · L2-L4 · 用户列表/详情

**功能**: 用户查询（design §3.2.1/§3.2.2，G1）
**Depends**: T004, T006
**预估**: 2h

**描述**: 用户域完整通路：UserMapper（XML）+ IUserService/UserServiceImpl + UserController（GET /api/users 分页、GET /api/users/{userId} 详情、selectByUserIds 供组装）。

**涉及文件**:
- `moments-dao/src/main/java/com/funny/moments/dao/mapper/UserMapper.java`（新建）
- `moments-dao/src/main/resources/mapper/UserMapper.xml`（新建）
- `moments-service/src/main/java/com/funny/moments/service/social/IUserService.java`（新建）
- `moments-service/src/main/java/com/funny/moments/service/social/impl/UserServiceImpl.java`（新建）
- `moments-web/src/main/java/com/funny/moments/web/controller/social/UserController.java`（新建）

**伪代码**:

```java
// UserMapper.java —— extends BaseMapper<UserDO>，自定义方法全 @Param
List<UserDO> selectPageList(@Param("offset") Integer offset, @Param("pageSize") Integer pageSize);
List<UserDO> selectByUserIds(@Param("userIds") List<Long> userIds);
Long countAll();
int batchInsert(@Param("list") List<UserDO> list);   // 供 T090 复用
int logicDeleteAll();
```

```xml
<!-- UserMapper.xml 核心SQL（全 #{} 占位 + is_del=0 + 禁裸 <） -->
<select id="selectByUserIds" resultType="...UserDO">
  SELECT id, nickname, avatar, gender, city, created_stime FROM user
  WHERE is_del = 0 AND id IN <foreach collection="userIds" item="id" open="(" separator="," close=")">#{id}</foreach>
</select>
<insert id="batchInsert">INSERT INTO user (nickname, avatar, gender, city) VALUES
  <foreach collection="list" item="u" separator=",">(#{u.nickname}, #{u.avatar}, #{u.gender}, #{u.city})</foreach></insert>
```

```java
// UserServiceImpl —— @Service + @Slf4j，只读无事务
public UserPageOut listUsers(Integer pageNo, Integer pageSize) {
    // B2 统一策略：缺省才取默认（pageNo=1/pageSize=20）；显式传入 pageNo<1 或 pageSize<1 或 >500 → BizException(PARAM_INVALID)
    //   （非数字由绑定层 MethodArgumentTypeMismatchException 承载转 1001，Service 层见不到，R3-建议4 删除死代码注释）
    // → total=countAll、list=selectPageList → UserOut 转换（genderStr/gender byCode）
}
public UserOut getUser(Long userId) {
    // userId null/<=0 → BizException(PARAM_INVALID)；selectById（@TableLogic 过滤）null → BizException(USER_NOT_FOUND)
}
public List<UserOut> listByUserIds(List<Long> ids) { // CollectionUtils.isEmpty → new ArrayList<>() }
```

```java
// UserController —— @RestController @RequestMapping("/api/users")，首参 _appId + 首行日志
@GetMapping
public ApiResult<UserPageOut> listUsers(String _appId, Integer pageNo, Integer pageSize) {
    log.info("UserController.listUsers appId={}, pageNo={}, pageSize={}", _appId, pageNo, pageSize);
    return ApiResult.succ(userService.listUsers(pageNo, pageSize));
}
@GetMapping("/{userId}")
public ApiResult<UserOut> getUser(String _appId, @PathVariable("userId") Long userId) { ... }
```

**验收标准**:
- [ ] 三层打通：GET /api/users、/api/users/{userId} 可调通；1002 分支可复现
- [ ] XML 通过 xmllint；`mvn test-compile -pl moments-web -am` 通过

---

### T020 · [好友查询] · L2-L4 · 我的好友/详情/好友ID缓存

**功能**: 好友查询（design §3.2.3/§3.2.4，G2；C11/C12）
**Depends**: T010
**预估**: 3h

**描述**: 好友域通路 + 好友 ID 集合 Redis 缓存组件（Feed 高频读铺垫）。FriendshipMapper + **FriendTagMapper（本 task 自建，R3 裁决方案①前移：仅含 selectByTagId，支撑 tagId 过滤归属校验）** + **FriendTagRelationMapper（本 task 自建，R2-NB2 前移：含 tagNames 组装与按标签查好友方法，保证 T020 按清单+Depends 可独立编译交付）** + FriendIdsCacheManager + IFriendshipService + FriendshipController（GET /api/friends?tagId= 可选、GET /api/friends/{userId}）。

**涉及文件**:
- `moments-dao/.../dao/mapper/FriendshipMapper.java` + `resources/mapper/FriendshipMapper.xml`（新建）
- `moments-dao/.../dao/mapper/FriendTagMapper.java` + `resources/mapper/FriendTagMapper.xml`（**新建，R3 裁决方案①前移**：本 task 仅实现 selectByTagId（校验标签存在与归属），其余方法 T030 追加）
- `moments-dao/.../dao/mapper/FriendTagRelationMapper.java` + `resources/mapper/FriendTagRelationMapper.xml`（**新建，R2-NB2 前移**：本 task 实现 selectTagNamesByOwnerAndFriends + selectFriendIdsByTag（tagId 过滤分支用），其余方法 T030 追加）
- `moments-service/.../service/social/cache/FriendIdsCacheManager.java`（新建）
- `moments-service/.../service/social/IFriendshipService.java` + `impl/FriendshipServiceImpl.java`（新建）
- `moments-web/.../web/controller/social/FriendshipController.java`（新建）

**伪代码**:

```java
// FriendshipMapper —— selectFriendIds(userId) / existsFriendship 返回 Long（计数）
//   / batchInsert(对称双记录) / logicDeleteAll()；全 SQL 带 is_del=0
List<Long> selectFriendIds(@Param("userId") Long userId);   // WHERE user_id=#{userId} AND is_del=0
Long existsFriendship(@Param("userId") Long userId, @Param("friendUserId") Long friendUserId);
// FriendTagMapper（R3 裁决方案①前移，本 task 仅此一个方法）：selectByTagId(tagId)
//   → WHERE id=#{tagId} AND is_del=0（返回 FriendTagDO，null=1004，user_id!=viewer=1004）
// FriendTagRelationMapper（本 task 两个方法）：
//   selectFriendIdsByTag(tagId) → SELECT friend_user_id FROM friend_tag_relation
//     WHERE tag_id=#{tagId} AND is_del=0（tagId 过滤分支数据源）
```

```java
// FriendIdsCacheManager —— @Component，注入框架 RedisClient（try-catch 全包，异常回源 C12）
public Set<Long> get(Long userId) {
    try {
        String ver = redisClient.get(CACHE_MOMENTS_FRIEND_VER);            // null→"0"
        String key = format(CACHE_MOMENTS_FRIEND_IDS, ver, userId);
        Set<String> members = redisClient.smembers(key);
        if (CollectionUtils.isNotEmpty(members)) return 过滤哨兵"0"后转Long集合;  // 建议8：哨兵"0"占位=有效空集缓存
        Set<Long> dbIds = mapper.selectFriendIds(userId);                  // 回源
        sadd(key, dbIds.isEmpty() ? new String[]{"0"} : ids数组);          // 建议8：空集也回填哨兵"0"，防每请求穿透 DB
        expire(key, 3600);
        return dbIds;
    } catch (Exception e) { log.warn("friendIds cache fail, userId={}", userId, e); return mapper.selectFriendIds(userId); }
}
public void evictAll() { redisClient.incr(CACHE_MOMENTS_FRIEND_VER); }     // mockData 后调用（事务外）
```

```java
// FriendshipServiceImpl
public List<FriendOut> listFriends(Long viewerId, Long tagId) {
    // tagId 非空 → FriendTagMapper.selectByTagId 校验归属(1004，R3 裁决：本 task 自建方法)
    //            → relationMapper.selectFriendIdsByTag(tagId)（R3 裁决：本 task 自建方法）
    // 否则 friendIdsCache.get(viewerId) → userMapper.selectByUserIds 批量组装
    // tagNames：列表页按 (viewer, friendIds) 批量——本 task 自建（R2-NB2 前移）：
    //   FriendTagRelationMapper.selectTagNamesByOwnerAndFriends(ownerId, friendIds)
    //   SQL：SELECT r.friend_user_id, t.tag_name FROM friend_tag_relation r
    //        JOIN friend_tag t ON t.id = r.tag_id AND t.user_id = #{ownerId} AND t.is_del = 0
    //        WHERE r.is_del = 0 AND r.friend_user_id IN (...)（GROUP BY friend_user_id, tag_name 防同名标签多行）
    //   组装时 tagNames 做 Stream().distinct() 去重（建议4：并发绑定窗口的展示侧兜底）
}
public FriendDetailOut getFriendDetail(Long viewerId, Long friendUserId) { // userMapper 校验 1002 + 单好友标签组装 }
```

```java
// FriendshipController —— @RequestMapping("/api/friends")；viewer=CurrentUserResolver.requireUser(request)（T100 前可暂用内联 Cookie 解析，T100 落地后替换为统一工具）
@GetMapping
public ApiResult<List<FriendOut>> getFriends(String _appId, @RequestParam(required = false) Long tagId, HttpServletRequest request);
@GetMapping("/{userId}")
public ApiResult<FriendDetailOut> getFriendDetail(String _appId, @PathVariable Long userId, HttpServletRequest request);
```

**验收标准**:
- [ ] 双向好友查询正确（对称两条记录视角一致）；tagId 过滤与归属校验（1004）可复现
- [ ] **按本 task 涉及文件 + Depends（仅 T010 链）可独立编译交付**（R2-NB2：tagNames 组装不依赖 T030 的任何产物）
- [ ] 缓存命中/miss 回源/Redis 异常回源三路径日志可见；无好友用户二次请求命中哨兵空集缓存（不穿透 DB，建议 8）
- [ ] `mvn test-compile -pl moments-web -am` 通过；xmllint 通过

---

### T030 · [标签管理] · L2-L4 · 标签 CRUD+绑定/解绑+级联

**功能**: 标签管理（design §3.2.5-§3.2.10，G3；C5/C13）
**Depends**: T020
**预估**: 4h

**描述**: 标签域 6 端点全通路 + 标签绑定缓存组件 + 删除级联软删事务。

**涉及文件**:
- `moments-dao/.../dao/mapper/FriendTagMapper.java` + XML（**追加**：T020 已建文件（R3 裁决方案①），本 task 补齐其余方法）
- `moments-dao/.../dao/mapper/FriendTagRelationMapper.java` + XML（**追加**：T020 已建文件，本 task 补齐其余方法——R2-NB2）
- `moments-service/.../service/social/cache/FriendTagCacheManager.java`（新建）
- `moments-service/.../service/social/IFriendTagService.java` + `impl/FriendTagServiceImpl.java`（新建）
- `moments-web/.../web/controller/social/FriendTagController.java`（新建）

**伪代码**:

```java
// FriendTagMapper（追加，R3 裁决方案①：selectByTagId 已在 T020 建好）：
//   selectByUserId/updateTagName(只设id+tagName的新实体)/updateIsDel(tagId)/batchInsert/logicDeleteAll
//   + countFriendsByTagIds(tagIds)：friendCount 统计——COUNT(DISTINCT friend_user_id)（R2-建议8：计数与 tagNames 去重口径一致，
//     并发残留两条绑定记录时不虚高）
// FriendTagRelationMapper（追加，R2-NB2：selectTagNamesByOwnerAndFriends + selectFriendIdsByTag 已在 T020 建好）：
//   selectByTagAndUser/selectTagIdsOfFriend(join friend_tag 限定 owner)/insert/
//   updateIsDelByTagId/updateIsDelByTagAndUser/batchInsert/logicDeleteAll
// selectTagIdsOfFriend 核心 SQL（2 表 join）：
SELECT r.tag_id FROM friend_tag_relation r
JOIN friend_tag t ON t.id = r.tag_id AND t.user_id = #{ownerId} AND t.is_del = 0
WHERE r.friend_user_id = #{friendUserId} AND r.is_del = 0
```

```java
// FriendTagCacheManager —— 同 T020 范式（含 R2-建议2 哨兵对称）：
//   getTagIds(ownerId, friendId)：key 含全局版本 ftagver；miss 回源 selectTagIdsOfFriend；
//     空 tagIds 同样回填哨兵成员 "0"（无标签 viewer 是 mock 常态，不回填则 canView 的 tagHit 每帖穿透 DB；
//     tagId 恒>0 与哨兵无冲突，读取时过滤哨兵）/ evictAll()=INCR ftagver
```

```java
// FriendTagServiceImpl
public TagOut createTag(Long viewerId, TagCreateIn in) {
    // tagName trim 1-16 校验(1001)；selectByUserId 内查重同名(1005)；insert（默认 8 标签外的自定义标签入口）
}
public TagOut updateTag(Long viewerId, Long tagId, TagEditIn in) {
    // selectByTagId → null(1004) / user_id!=viewer(1006) / 新名查重(1005)
    // 新建只含 id+tagName 的实体调用 update（java-guide 2.3）
}
@Transactional(rollbackFor = Exception.class)
public void deleteTag(Long viewerId, Long tagId) {
    // 校验同上 → friendTagMapper.updateIsDel(tagId) + relationMapper.updateIsDelByTagId(tagId)（C13 级联）
    // 缓存失效（建议3 定稿）：TransactionSynchronizationManager.registerSynchronization(afterCommit 回调内 evictAll)
    // —— 禁止"方法尾"直接调用（=提交前=事务内操作 Redis，违反 java-guide §5）
}
public void bindUser(Long viewerId, Long tagId, Long userId) {
    // 标签归属校验(1004/1006) → userMapper 存在性(1002) → friendshipMapper.existsFriendship(viewer,userId)=0(1007)
    //   → selectByTagAndUser 查重(1008) → insert → 缓存 evictAll（事务外，单表写无事务注解）
    //   （Stage 4 审查裁决：1002 先于 1007，与 design §3.2.9 异常码序一致）
}
public void unbindUser(Long viewerId, Long tagId, Long userId) {
    // 归属校验 → updateIsDelByTagAndUser（0 行命中幂等成功）→ 缓存 evictAll
}
```

```java
// FriendTagController —— @RequestMapping("/api/friend-tags")，6 端点（GET/POST/PUT/DELETE + 两级路径绑定），首行日志
```

**验收标准**:
- [ ] 6 端点全通；1004/1005/1006/1007/1008 分支可复现
- [ ] 标签缓存空集哨兵回填生效（无标签 viewer 二次 canView 不穿透 DB，R2-建议2）；friendCount 为 COUNT(DISTINCT) 结果（R2-建议8）
- [ ] 删标签级联软删绑定（DB 断言 relation is_del=1）；post_visibility_tag 不动
- [ ] 事务内无 Redis；`mvn test-compile`/xmllint 通过

---

### T040 · [图片] · L2-L4 · 本地图片上传+占位图池

**功能**: 图片（design §3.2.17，G8；C16/C17/D5）
**Depends**: T003
**预估**: 3h

**描述**: 本地图片存储 Service（无 Mapper）：上传校验+UUID 落盘；占位图池生成（供 mock 数据与页面默认头像）。

**涉及文件**:
- `moments-service/.../service/social/ILocalImageService.java` + `impl/LocalImageServiceImpl.java`（新建；rootPath 由 **`@Value("${moments.image.root-path:${user.home}/moments-data/images}")`** 字段注入——R2-建议3：单一 @Value 承载，**不建 @ConfigurationProperties 类**（其字段 Java 默认值不解析 `${...}` 占位符，会与 WebConfig @Value 分裂致上传 404）；与 T100 WebConfig 的默认值字符串逐字一致，绝对路径理由见建议9）
- `moments-web/.../web/controller/social/ImageController.java`（新建）

**伪代码**:

```java
// LocalImageServiceImpl —— @Service @Slf4j，无事务无 Mapper
@Value("${moments.image.root-path:${user.home}/moments-data/images}")   // R2-建议3：@Value 单一承载，嵌套默认值可解析
private String rootPath;
public ImageUploadOut upload(MultipartFile file) {
    // 1 空文件(1020)；2 取原始文件名扩展名 → 小写 ∈ {jpg,jpeg,png,gif,webp}(1020)
    // 3 file.getSize() > 5*1024*1024 → 1021
    // 4 fileName = UUID.randomUUID().toString().replace("-","") + "." + ext（服务端生成，防覆盖防穿越）
    // 5 Files.createDirectories(rootPath) + Files.write(rootPath.resolve(fileName), file.getBytes())（IO 异常 log.error+上抛系统码）
    // 6 return imageUrl = "/images/" + fileName
}
public List<String> ensurePlaceholderPool() {
    // 幂等：目标文件已存在跳过；头像 ph_avatar_01..20（200×200）+ 内容 ph_content_01..20（600×600）
    // 纯色 PNG：new BufferedImage(size,size,TYPE_INT_RGB) → Graphics2D fillColor（随机 pastel 色）→ ImageIO.write
}
```

```java
// ImageController —— @RequestMapping("/api/images")
@PostMapping("/upload")
public ApiResult<ImageUploadOut> upload(String _appId, @RequestParam("file") MultipartFile file) {
    log.info("ImageController.upload appId={}, originalName={}, size={}", _appId, file.getOriginalFilename(), file.getSize());
    return ApiResult.succ(localImageService.upload(file));
}
```

**验收标准**:
- [ ] jpg/png/gif/webp 可传，txt/bat 拒 1020，>5MB 拒 1021；返回 URL 可经 /images/** 访问（T100 映射就绪后联验）
- [ ] 占位图池 40 文件幂等生成
- [ ] `mvn test-compile -pl moments-web -am` 通过

---

### T060 · [发帖] · L2-L4 · 发布朋友圈（4 表事务）

**功能**: 发帖（design §3.2.12/§5.1，G5；C7/C17）
**Depends**: T010, T030
**预估**: 3.5h

**描述**: PostMapper/PostImageMapper + 可见性两 Mapper（写入部分）+ PostServiceImpl.createPost + PostController.createPost。

**涉及文件**:
- `moments-dao/.../dao/mapper/PostMapper.java`（新建，本 task 实现 insert/selectById/updateIsDel/selectUserPosts/batchInsert/logicDeleteAll；selectFeedPage 留 T080 追加）
- `moments-dao/.../dao/mapper/PostImageMapper.java` + `PostVisibilityUserMapper.java` + `PostVisibilityTagMapper.java`（新建；可见性两 Mapper 本 task 实现 batchInsert/logicDeleteAll，查询留 T050）
- 对应 4 个 XML（新建）
- `moments-service/.../service/social/IPostService.java`（新建：4 方法签名，本 task 实现 createPost）+ `impl/PostServiceImpl.java`（新建）
- `moments-web/.../web/controller/social/PostController.java`（新建：本 task 实现 createPost 端点）

**伪代码**:

```java
// PostMapper 核心：selectById（WHERE id=#{id} AND is_del=0）
//   selectUserPosts(userId, cursorTime, cursorId, limit)——带游标两参（T070 放大补偿扫描复用，建议5）：
//     WHERE user_id=#{userId} AND is_del=0 AND status=1 AND <if cursor 非空>游标条件（同 selectFeedPage）</if>
//     ORDER BY created_stime DESC, id DESC LIMIT #{limit}
// batchInsert：(user_id, content, visibility_type, status) VALUES foreach 多行
```

```java
// PostServiceImpl
@Transactional(rollbackFor = Exception.class)
public PostDetailOut createPost(Long viewerId, PostCreateIn in) {
    // 1 校验（任一失败抛 BizException，事务回滚）：
    //   visibilityType 非空且 byCode!=null（1001）；content trim 后长度<=2000（1001）
    //   imageUrls 非空时 size<=9，超限抛 1022；content 与 imageUrls 不得同空（1001）
    //   type=3/4 时：visibilityTagIds 非空 → 逐个 friendTagMapper.selectByTagId 存在(1004)且归属 viewer(1006)
    //               visibilityUserIds 非空 → userMapper.selectByUserIds 全命中(1002)
    // 2 postDO 组装（status=PostStatusEnum.NORMAL）→ postMapper.insert → 回填自增 id
    // 3 图片：postImageMapper.batchInsert（sort=1..n 按 imageUrls 顺序）
    // 4 type∈{3,4} 且 tagIds 非空 → postVisibilityTagMapper.batchInsert（去重 Stream.distinct）
    // 5 type∈{3,4} 且 userIds 非空 → postVisibilityUserMapper.batchInsert（去重）
    // 6 事务内无 Redis/文件 IO；返回组装 PostDetailOut（作者=userMapper.getUser）
}
```

```java
// PostController —— @RequestMapping("/api/posts")
@PostMapping
public ApiResult<PostDetailOut> createPost(String _appId, @RequestBody PostCreateIn in, HttpServletRequest request) {
    log.info("PostController.createPost appId={}, in={}", _appId, JSON.toJSONString(in));
    return ApiResult.succ(postService.createPost(CurrentUserResolver.requireUser(request), in));
}
```

**验收标准**:
- [ ] 纯文/纯图/图文三形态可发；4 表同事务落库（DB 断言）；非法参数回滚无残留
- [ ] 事务内无中间件调用；`mvn test-compile`/xmllint 通过

---

### T050 · [可见性判断] · L2-L3 · canView 统一判断（内部功能）

**功能**: 可见性判断（design §5.2，G6；C6）——无 Controller（被帖子/Feed 调用的内部 Service）
**Depends**: T060
**预估**: 2.5h

**描述**: 可见性两 Mapper 追加查询方法 + VisibilityServiceImpl 实现 canView 单一实现。

**涉及文件**:
- `moments-dao/.../dao/mapper/PostVisibilityUserMapper.java` + `PostVisibilityTagMapper.java`（追加 selectUserIdsByPostId / selectTagIdsByPostId + XML）
- `moments-service/.../service/social/IVisibilityService.java` + `impl/VisibilityServiceImpl.java`（新建）

**伪代码**:

```java
// Mapper 追加
List<Long> selectUserIdsByPostId(@Param("postId") Long postId);  // WHERE post_id=# AND is_del=0
List<Long> selectTagIdsByPostId(@Param("postId") Long postId);
```

```java
// VisibilityServiceImpl —— @Service @Slf4j（只读，无事务）
public boolean canView(Long viewerId, Long postId) {
    PostDO post = postMapper.selectById(postId);
    if (Objects.isNull(post)) return false;
    if (Objects.equals(post.getUserId(), viewerId)) return true;              // 作者最高优先级
    Integer type = post.getVisibilityType();
    if (VisibilityTypeEnum.PUBLIC.getCode().equals(type)) return true;
    if (VisibilityTypeEnum.PRIVATE.getCode().equals(type)) return false;
    boolean hit = tagHit(post, viewerId) || userHit(post, viewerId);          // 标签 OR 好友匹配
    if (VisibilityTypeEnum.PART_VISIBLE.getCode().equals(type)) return hit;   // 空集→false（C6）
    return !hit;                                                              // EXCLUDE：空集→true（C6）
}
private boolean tagHit(PostDO post, Long viewerId) {
    List<Long> postTagIds = postVisibilityTagMapper.selectTagIdsByPostId(post.getId());
    if (CollectionUtils.isEmpty(postTagIds)) return false;
    Set<Long> viewerTagIds = friendTagCacheManager.getTagIds(post.getUserId(), viewerId); // 作者给观众打的标签（缓存）
    return postTagIds.stream().anyMatch(viewerTagIds::contains);
}
private boolean userHit(PostDO post, Long viewerId) {
    return postVisibilityUserMapper.selectUserIdsByPostId(post.getId()).contains(viewerId);
}
```

**验收标准**:
- [ ] explore Q1-Q5 全矩阵语义正确（作者/公开/私密/部分可见含空集/不给谁看含空集）
- [ ] 缓存命中与回源路径可用；`mvn test-compile`/xmllint 通过

---

### T070 · [帖子读写] · L2-L4 · 帖子详情/软删/按用户查

**功能**: 帖子读写（design §3.2.13-§3.2.15，G6/G7 前置）
**Depends**: T050
**预估**: 3h

**描述**: PostServiceImpl 追加 getPost/deletePost/listUserPosts + PostController 追加 3 端点（复用 T060 的 Mapper 与 T050 的 canView）。

**涉及文件**:
- `moments-service/.../service/social/impl/PostServiceImpl.java`（追加 3 方法）
- `moments-web/.../web/controller/social/PostController.java`（追加 3 端点）

**伪代码**:

```java
// PostServiceImpl 追加
public PostDetailOut getPost(Long viewerId, Long postId) {
    PostDO post = postMapper.selectById(postId);   // null → 1010
    if (!visibilityService.canView(viewerId, postId)) throw new BizException(POST_NO_PERMISSION); // 1011
    return 组装（postImageMapper.selectByPostId 按 sort + 作者信息 + xxTypeStr/xxTimeStr）;
}
public void deletePost(Long viewerId, Long postId) {
    PostDO post = postMapper.selectById(postId);   // null → 1010
    if (!Objects.equals(post.getUserId(), viewerId)) throw new BizException(NOT_POST_AUTHOR);      // 1012
    postMapper.updateIsDel(postId);                // 新建只含 id 的实体；单表软删无事务注解；图片文件保留（D7）
}
public List<PostDetailOut> listUserPosts(Long viewerId, Long targetUserId, Integer limit) {
    // userMapper 校验存在(1002)；limit 缺省 100，显式越界(<1||>500)抛 1001（B2）
    // 建议5 定稿：limit=结果上限，扫描按 Feed 同口径放大补偿——
    //   循环最多 3 批、批大小 limit*3：selectUserPosts(targetUserId, scanTime, scanId, batchSize)
    //   （复用 Feed 的游标条件，方法签名含游标两参）→ filter canView → collected 累加；
    //   batch.size() < batchSize 或 collected >= limit 即止 → 取前 limit 条组装
    //   （避免"先截断后过滤"漏掉截断点之后的旧公开帖）
    // 批量取图（selectByPostIds）→ 组装
}
```

```java
// PostController 追加
@GetMapping("/{postId}")   public ApiResult<PostDetailOut> getPost(String _appId, @PathVariable Long postId, HttpServletRequest request);
@DeleteMapping("/{postId}") public ApiResult<Void> deletePost(String _appId, @PathVariable Long postId, HttpServletRequest request);
@GetMapping                 public ApiResult<List<PostDetailOut>> listUserPosts(String _appId, @RequestParam Long userId,
                            @RequestParam(required = false) Integer limit, HttpServletRequest request);  // /api/posts?userId=
```

**验收标准**:
- [ ] 1010/1011/1012 分支可复现；软删后详情/列表均不可见（is_del 过滤）
- [ ] `mvn test-compile -pl moments-web -am` 通过

---

### T080 · [Feed] · L2-L4 · Cursor 分页 Feed

**功能**: Feed（design §3.2.16/§5.4，G7；C4/C8/C12/D3/R1/R4）
**Depends**: T050, T020
**预估**: 4h

**描述**: PostMapper 追加 selectFeedPage + PostImageMapper 追加 selectByPostIds + FeedService/FeedController（Cursor 解码 + 批次放大过滤）。

**涉及文件**:
- `moments-dao/.../dao/mapper/PostMapper.java`（追加 selectFeedPage）+ XML
- `moments-dao/.../dao/mapper/PostImageMapper.java`（追加 selectByPostIds）+ XML
- `moments-service/.../service/social/IFeedService.java` + `impl/FeedServiceImpl.java`（新建）
- `moments-web/.../web/controller/social/FeedController.java`（新建）

**伪代码**:

```java
// PostMapper.selectFeedPage —— 核心 SQL（XML 内 < 写成 &lt;）
List<PostDO> selectFeedPage(@Param("userIds") List<Long> userIds, @Param("viewerId") Long viewerId,
                            @Param("cursorTime") Date cursorTime, @Param("cursorId") Long cursorId, @Param("limit") Integer limit);
SELECT id, user_id, content, visibility_type, status, created_stime FROM post
WHERE is_del = 0 AND status = 1
  AND user_id IN <foreach .../>
  AND (visibility_type != 2 OR user_id = #{viewerId})                       <!-- 私密帖非作者预过滤 -->
  <if test="cursorTime != null">AND (created_stime &lt; #{cursorTime}
      OR (created_stime = #{cursorTime} AND id &lt; #{cursorId}))</if>      <!-- C8 双字段游标 -->
ORDER BY created_stime DESC, id DESC LIMIT #{limit}
```

```java
// FeedServiceImpl —— @Service @Slf4j（只读）
public FeedOut getFeed(Long viewerId, String cursor, Integer pageSize) {
    // 1 pageSize 缺省 20，显式越界(<1||>50 或非数字)抛 1001（B2）
    // 2 cursor 解码：非空 → try{Base64.getDecoder().decode}catch(IllegalArgumentException)→1030（建议6：decode 异常同码）
    //   → new String(UTF_8) → FEED_CURSOR matcher（^\d{13}:\d{1,18}$，R2-建议7：id 段限 18 位）校验失败也 1030（R4 不静默）
    //   → split(":") → try{millis/id parseLong}catch(NumberFormatException)→1030（R2-建议7 双保险：防溢出落 code=100）
    //     → cursorTime=new Date(millis)、cursorId
    // 3 friendIds = friendIdsCacheManager.get(viewerId)（空集有哨兵缓存，见 T020）；candidateIds = viewerId + friendIds（去重）
    // 4 取数循环（B1 双锚点）：batchSize=pageSize*3，maxRounds=3，scanTime/scanId 初始=入参 cursor 值
    //    while (rounds<3 && collected.size()<pageSize):
    //      batch = postMapper.selectFeedPage(candidateIds, viewerId, scanTime, scanId, batchSize)
    //      batch.isEmpty() → scanEnd=true break
    //      scanTime/scanId = batch 末条 (created_stime, id)   ← 扫描进度锚点前移（无论有无可见帖）
    //      collected += batch.filter(canView)
    //      batch.size() < batchSize → scanEnd=true break；否则 rounds++
    // 5 输出判定（B1 核心）：
    //    collected > pageSize → items=前 pageSize、hasMore=true、nextCursor=items 末条锚点（页末锚点）
    //    collected ≤ pageSize 且 scanEnd → items=collected、hasMore=false、nextCursor=null
    //    collected ≤ pageSize 且 !scanEnd → items=collected（可为空集）、hasMore=true、
    //      nextCursor=(scanTime,scanId) 扫描进度锚点 Base64 ← items=[] && hasMore=true && nextCursor≠null 合法组合
    // 6 批量组装：postImageMapper.selectByPostIds + userMapper.selectByUserIds（防 N+1）→ FeedItemOut
}
```

```java
// FeedController —— @RequestMapping("/api/feed")
@GetMapping
public ApiResult<FeedOut> getFeed(String _appId, @RequestParam(required = false) String cursor,
        @RequestParam(required = false) Integer pageSize, HttpServletRequest request) {
    log.info("FeedController.getFeed appId={}, cursor={}, pageSize={}", _appId, cursor, pageSize);
    return ApiResult.succ(feedService.getFeed(CurrentUserResolver.requireUser(request), cursor, pageSize));
}
```

**验收标准**:
- [ ] explore Q1 规则⑤走查通过：同秒 id 倒序、nextCursor 逐页无重复无跳空、末页 hasMore=false
- [ ] B1 场景可复现且不死循环：构造连续 ≥9×pageSize 条对 viewer 不可见候选 → 返回 items=[]、hasMore=true、nextCursor≠null（扫描进度锚点），携带该 cursor 下翻不重扫；连续 3 空页前端停止（T110）
- [ ] 1030 非法游标拒绝（含非法 Base64 串如 `!!!` 的 decode 异常，建议 6）；私密帖不出现在他人 Feed；无好友时仅自己帖子
- [ ] `mvn test-compile`/xmllint 通过

---

### T090 · [模拟数据] · L2-L4 · 批量生成模拟数据

**功能**: 模拟数据（design §3.2.11/§5.5，G4；C15/C16/A7/A8/A9/R5/R11）
**Depends**: T060, T040
**预估**: 4h

**描述**: MockDataService（clear 事务 + 分批事务生成 + 缓存失效）+ Controller；复用各域 Mapper 的 batchInsert/logicDeleteAll 与占位图池。

**涉及文件**:
- `moments-service/.../service/social/IMockDataService.java` + `impl/MockDataServiceImpl.java`（新建）
- `moments-web/.../web/controller/social/MockDataController.java`（新建）
- 既有 8 个 Mapper（只读复用，无新方法；若缺 selectTagNames 类组装方法在此追加）

**伪代码**:

```java
// MockDataServiceImpl —— @Service @Slf4j
public MockDataGenerateOut generate(MockDataGenerateIn in) {
    // 1 配置校验（userCount 1-100000 / avgFriends 0-500 / extraTags 0-20 / postCount 0-10000）→ 1040
    // 2 clear=true（建议7 修订）：8 表 logicDeleteAll 按**表逐个独立小事务**提交（clearTable(i) 各自带 @Transactional，
    //   非 8 表合一事务，对齐 R11 防长事务）；clear 段完成后立即（事务外）执行双 INCR 失效缓存——
    //   保证后续生成中断时旧缓存已失效，不残留"已清空数据"
    // 3（事务外）imageUrls = localImageService.ensurePlaceholderPool()（头像/内容图池）
    // 4 分批生成（每批 500，独立事务方法 batches(List) 提交，R11）：
    //   用户：nickname=SURNAME_POOL[rnd]+GIVEN_NAME_POOL[rnd]，gender/city 随机，avatar=头像池循环
    //   好友：每人 k=avgFriends±3（clamp>=0）；pair "minId:maxId" HashSet 去重防自环防重；
    //         对称双记录 A→B+B→A 同批写入（R5，uniq_user_friend 兜底 catch 重复跳过）
    //   标签：每人 DEFAULT_TAG_NAMES 8 条 + extraTags 条（"自定义标签N"）
    //   绑定：每 (owner,friend) 按 TAG_WEIGHTS 加权抽 1 主标签；30% 概率再抽不重复次标签（多标签语义）
    //   帖子（postCount>0）：随机用户 1-2 帖凑数；type 分布 1:40%/2:20%/3:20%/4:20%（ThreadLocalRandom）；
    //         图 1-9 张取内容图池；type=3/4 抽作者自己的标签/好友写可见性两表——抽样**无放回**：
    //         Collections.shuffle(候选) 后 subList(0, n)（R2-建议5：防有放回重复撞 uniq_post_tag/
    //         uniq_post_user 唯一键致整批事务回滚、mock 中途中断）
    //   批间 log.info 进度（批序/累计/耗时）
    // 5 完成后（事务外）：friendIdsCacheManager.evictAll() + friendTagCacheManager.evictAll()
    // 6 return 统计 Out（elapsedMs 等）
}
```

```java
// MockDataController —— @RequestMapping("/api/mock-data")
@PostMapping
public ApiResult<MockDataGenerateOut> generate(String _appId, @RequestBody MockDataGenerateIn in) {
    log.info("MockDataController.generate appId={}, in={}", _appId, JSON.toJSONString(in));
    return ApiResult.succ(mockDataService.generate(in));
}
```

**验收标准**:
- [ ] 默认配置生成 100 用户×10 好友 → friendship 记录 ≈1000 条且全对称（DB 断言）
- [ ] 标签分布抽样误差在统计容差内（同事~25% 等）；clear=true 后旧数据全软删
- [ ] 全程无长事务（每批独立提交）；`mvn test-compile` 通过

---

### T100 · [横切装配] · L4 · 视角解析/异常处理/DDL Runner/静态映射

**功能**: 横切装配（design §6.1/A3/A6/A10/R6）
**Depends**: T002, T003
**预估**: 2h

**描述**: web 层四个支撑件：CurrentUserResolver、BizExceptionAdvice、SchemaInitRunner、WebConfig 追加 /images/** 静态映射（唯一修改的既有文件）。

**涉及文件**:
- `moments-web/src/main/java/com/funny/moments/web/support/CurrentUserResolver.java`（新建）
- `moments-web/src/main/java/com/funny/moments/config/BizExceptionAdvice.java`（新建）
- `moments-web/src/main/java/com/funny/moments/config/SchemaInitRunner.java`（新建）
- `moments-web/src/main/java/com/funny/moments/config/WebConfig.java`（修改：仅追加 addResourceHandlers + @Value 注入图片目录）

**伪代码**:

```java
// CurrentUserResolver —— 静态工具，复用 CookieUtils.getCookieValueByName
public static Long resolve(HttpServletRequest request) {
    String viewerId = request.getParameter("viewerId");           // 显式覆盖优先（A3，避免与 /api/posts?userId= 冲突）
    if (StringUtils.isNotBlank(viewerId)) {                       // B3 强校验：非法抛 1001，不静默回退 Cookie
        if (!viewerId.matches("^\\d{1,18}$")) throw new BizException(SocialErrorCode.PARAM_INVALID);
        try { Long v = Long.valueOf(viewerId);
               if (v <= 0) throw new BizException(SocialErrorCode.PARAM_INVALID);   // 防 user_id=0 脏数据穿透
               return v;
        } catch (NumberFormatException e) { throw new BizException(SocialErrorCode.PARAM_INVALID); } // 超 Long 范围
    }
    String cookieVal = CookieUtils.getCookieValueByName(request, "mockUserId");   // Cookie 脏值宽松：视为未选择
    if (StringUtils.isNotBlank(cookieVal) && cookieVal.matches("^\\d{1,18}$")) {
        try { Long v = Long.valueOf(cookieVal); return v > 0 ? v : null; } catch (NumberFormatException e) { return null; }
    }
    return null;
}
public static Long requireUser(HttpServletRequest request) {
    Long viewer = resolve(request);
    if (Objects.isNull(viewer)) throw new BizException(SocialErrorCode.CURRENT_USER_REQUIRED);
    return viewer;
}

// BizExceptionAdvice —— @RestControllerAdvice @Order(HIGHEST_PRECEDENCE)（仿 SignExceptionAdvice）
@ExceptionHandler(BizException.class)
public ApiResult<Void> handleBizException(BizException e) {
    log.warn("业务异常 code={}, msg={}", e.getCode(), e.getMessage());
    return ApiResult.buildFailure(e.getCode(), e.getMessage());
}
// R2-NB1：非数字数值参数的 1001 承载——Spring 绑定层抛出（到不了 Service 校验），必须在此拦截，
// 否则落框架 GlobalExceptionAdvice 兜底 code=100，违背 api.md "含非数字→1001" 承诺
@ExceptionHandler(MethodArgumentTypeMismatchException.class)
public ApiResult<Void> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
    log.warn("参数类型绑定失败 name={}, value={}, targetType={}", e.getName(), e.getValue(),
            e.getRequiredType() == null ? null : e.getRequiredType().getSimpleName());
    return ApiResult.buildFailure(SocialErrorCode.PARAM_INVALID.getCode(), SocialErrorCode.PARAM_INVALID.getMessage());
}
// R3 裁决追加：JSON 请求体字段类型非法（Jackson 反序列化失败）同模式转 1001，
// 防 POST/PUT body 数值字段传字符串落 code=100 与阶段四用例口径分裂
@ExceptionHandler(HttpMessageNotReadableException.class)
public ApiResult<Void> handleBodyNotReadable(HttpMessageNotReadableException e) {
    log.warn("请求体解析失败 msg={}", e.getMessage());   // 不回传 e 细节，避免泄露内部结构
    return ApiResult.buildFailure(SocialErrorCode.PARAM_INVALID.getCode(), SocialErrorCode.PARAM_INVALID.getMessage());
}

// SchemaInitRunner —— @Profile("dev") + ApplicationRunner（D1）
// ScriptUtils.executeSqlScript(new ClassPathResource("schema/social_timeline.sql")，连接取 DataSource)
// 失败 log.error 含表名并抛出（fail-fast）；幂等可重复启动

// WebConfig 追加（不动既有 CORS/拦截器）
@Value("${moments.image.root-path:${user.home}/moments-data/images}") private String imageRootPath;  // 建议9：绝对路径默认
@Override
public void addResourceHandlers(ResourceHandlerRegistry registry) {
    registry.addResourceHandler("/images/**")
            .addLocations("file:" + imageRootPath + "/")   // 末尾斜杠必须（目录语义）
            .resourceChain(true)
            .addResolver(new FileNameSafeResourceResolver()); // 内部类：PathResourceResolver，文件名 IMAGE_FILE_NAME 白名单不符 → null(404)，防穿越 R6
}
```

**验收标准**:
- [ ] dev 启动自动建 8 表（重复启动幂等）；BizException 透传 code/msg（{"code":1003,...}）
- [ ] **R2-NB1：`GET /api/users?pageNo=abc` 返回 code=1001（非 100）；路径参数非数字（如 /api/users/xx）同 1001**
- [ ] **R3 裁决追加：`POST /api/posts` body 传 `"visibilityType":"abc"` 返回 code=1001（非 100）**
- [ ] /images/{白名单文件} 可访问、/images/../application.yml 拒绝；既有 /test/**、CORS 不受影响
- [ ] `mvn test-compile -pl moments-web -am` 通过

---

### T110 · [前端页面] · 静态页 x9（8 业务页 + index 导航）

**功能**: 前端页面（design §1.2 G9/§13.2；PRD §2.6/§3.9；C2/C3）
**Depends**: T070, T080, T090, T040
**预估**: 6h

**描述**: 原生 HTML/CSS/JS（无构建链）静态页，moments-web `resources/static` 托管；Feed 页样式贴近微信朋友圈（白底卡片流/头像+昵称+时间/九宫格图）；全站共用顶部用户切换器（写 Cookie mockUserId 后刷新）。

**涉及文件**:
- `moments-web/src/main/resources/static/`：`index.html`（导航）+ `users.html`（用户列表，含"切换为当前用户"）+ `friends.html`（我的好友+标签过滤）+ `friend-detail.html`（好友详情+其朋友圈）+ `tags.html`（标签管理 CRUD）+ `friend-tags.html`（好友标签管理绑定/解绑）+ `mock-data.html`（生成器表单，clear 二次确认）+ `post-create.html`（发布：文字+图片上传+可见范围四选+标签/好友多选）+ `feed.html`（朋友圈瀑布流滚动加载）
- `moments-web/src/main/resources/static/assets/app.css` + `app.js`（共用：fetch 封装/ApiResult 处理/用户切换器/Toast）（新建）

**伪代码**:

```javascript
// app.js 核心（原生 JS，无依赖）
async function api(path, options = {}) {
    const res = await fetch(path, { credentials: 'same-origin',
        headers: { 'Content-Type': 'application/json' }, ...options });
    const body = await res.json();
    if (body.code !== 0) { showToast(body.msg); throw new Error(body.msg); }  // 统一错误出口
    return body.data;
}
// 用户切换器：GET /api/users?pageSize=500 → <select>；change → document.cookie='mockUserId='+id+';path=/' → location.reload()
// feed.html：window.addEventListener('scroll', 底部触发 loadMore())
//   let cursor = null, hasMore = true, emptyPageCount = 0;
//   async function loadMore() { const data = await api('/api/feed?pageSize=20' + (cursor ? '&cursor='+cursor : ''));
//     appendCards(data.items); cursor = data.nextCursor; hasMore = data.hasMore;
//     emptyPageCount = data.items.length === 0 ? emptyPageCount + 1 : 0;          // B1 前端停止规则：
//     if (emptyPageCount >= 3) { hasMore = false; showToast('暂无更多内容'); } }  //   连续 3 次空页即停止（即使 hasMore=true）
//   帖子卡片：头像+昵称+content+九宫格 grid（1/2/3 列自适应 1-9 图）+createTimeStr
// 写操作按钮（发帖/删帖/绑定）：提交即 disabled + loading，完成/失败恢复（建议1：删帖非幂等 1010，防双击）
// post-create.html：图片 <input type=file multiple> 逐张 POST /api/images/upload（校验 jpg/png/gif/webp/5MB/9 张）
//   → imageUrls 收集预览 → 可见范围 radio 4 选 → type=3/4 时加载我的标签/好友多选 → POST /api/posts
// mock-data.html：表单四项+clear checkbox（勾选时 confirm 二次确认）→ POST /api/mock-data → 展示统计
```

**验收标准**:
- [ ] 9 页可从 index 导航互达；9 宫格（1/2/4/9 图）布局正确；滚动加载翻页正常
- [ ] 切换用户后好友/标签/Feed 视角随 Cookie 变化（隐私验证可操作）
- [ ] 无控制台报错；图片经 /images/** 正常渲染；纯静态无构建产物入库

---

## 统计

- 总 task 数：17
- 阶段一（L0+L1）：6 个（T001-T006）
- 阶段二功能数：11 个（用户/好友/标签/图片/发帖/可见性/帖子读写/Feed/模拟数据/横切装配/前端页面；其中可见性为内部功能无 Controller、横切与前端为支撑功能）
- 可并行功能组：9 组（G1-G9，见并行组表）
- 预估总工时：43.5h

## 拆分自检（tech-designer 3-4）

- [x] 每个 task 溯源 design.md 章节（各 task 描述标注 §x.x）
- [x] 阶段一 L0+L1 全部做完（DDL 完整 SQL/枚举完整结构/DO+DTO 结构伪代码齐全）
- [x] 阶段二每个功能 Mapper+Service+Controller 同 task（唯一例外：可见性判断为内部功能无 Controller 层可并；横切装配/前端页面为非 CRUD 支撑功能）——不存在任何"仅 xxx-Mapper / 仅 xxx-Service / 仅 xxx-Controller"孤立 task
- [x] 每个 task 有 Depends + 涉及文件 + 伪代码（三层覆盖）+ 验收标准
- [x] 依赖无环（G1→G2→G3→…→G9 单向 DAG；T060→T050→T070 链说明已注明）
- [x] 总 task 数 17 ∈ [8, 25]
