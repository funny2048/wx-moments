# S0 分批 — 20260920-social-timeline-mvp

- 基线: HEAD (fdb6b58)（全部改动为工作区未提交变更：5 修改 + 84 新增主代码）
- **新增文件为 untracked，`git diff HEAD` 不可见，直接 Read 文件本身即为 diff 全量**；修改文件用 `git diff HEAD -- {file}`
- 总 diff 行数: 14,832（主代码 ~9.1k + 测试 ~5k）/ 主代码 89 文件 / 测试 20 文件
- diff-hash: `da13eabc4d08`（tracked diff hash + untracked 内容 hash 组合）
- fast=false → 慢路径：(L1,L2)×3 batch + L3×1 + L4×1 = 8 finder（硬顶）

## batch-1 数据层+契约层（47 文件, ~3.2k 行）
- moments-common: CacheKeyConsts(M), PatternConsts(M), MockDataConsts, GenderEnum, PostStatusEnum, SocialErrorCode, VisibilityTypeEnum
- moments-client: model/in/{MockDataGenerateIn,PostCreateIn,TagCreateIn,TagEditIn}, model/out/{FeedOut,FriendDetailOut,FriendOut,ImageUploadOut,MockDataGenerateOut,PostDetailOut,TagOut,UserOut,UserPageOut}
- moments-dao: dto/{FriendTagNameDTO,TagFriendCountDTO}; entity/{FriendshipDO,FriendTagDO,FriendTagRelationDO,PostDO,PostImageDO,PostVisibilityTagDO,PostVisibilityUserDO,UserDO}; mapper/{Friendship,FriendTag,FriendTagRelation,PostImage,Post,PostVisibilityTag,PostVisibilityUser,User}Mapper.java; resources/mapper/*.xml ×8; resources/schema/social_timeline.sql

## batch-2 业务层（19 文件, ~2.7k 行）
- moments-service: pom.xml(M); service/social/*.java 接口 ×10; impl/{Feed,Friendship,FriendTag,LocalImage,MockData,Post,User,Visibility}ServiceImpl.java; cache/{FriendIdsCacheManager,FriendTagCacheManager}.java

## batch-3 接入层+静态页（25 文件, ~3.2k 行）
- moments-web: pom.xml(M); config/{BizExceptionAdvice,SchemaInitRunner}.java, WebConfig.java(M); web/controller/social/{Feed,Friendship,FriendTag,Image,MockData,Post,User}Controller.java; web/support/CurrentUserResolver.java; static/assets/{app.css,app.js}; static/*.html ×9

## dropped（披露）
- 测试代码 20 文件（~5k 行）：按 源码>配置>测试 优先级 + 测试边界（测试质量归阶段六红绿循环），不纳入 L1/L2 深审；编译性已有证据（mvn test 143/143 全绿 ×2 连跑一致）
- `.playwright-mcp/`：非交付物（页面测试运行时产物），排除
- 每批次行数超 2k 指引值：3-batch 硬顶优先，finder 按"只看 diff/新文件全量、不通读全仓库"控制输入
