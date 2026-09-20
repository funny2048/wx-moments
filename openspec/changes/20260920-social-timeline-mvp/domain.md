# 领域匹配文档 — Social Timeline MVP

文档信息

| 项目 | 内容 |
|------|------|
| 需求名称 | Social Timeline MVP（一期好友关系 + 二期朋友圈隐私 Feed） |
| 创建日期 | 2026-09-20 |
| 文档状态 | 已确认（AI 自主代行，待用户追认） |
| 确认人 | funny2048（目标指令授权自主推进） |
| 确认时间 | 2026-09-20 23:00 前后（目标下达当夜） |

一、涉及的已有领域及功能

| 领域 | 功能 | 模块 | Controller | 方法 |
|------|------|------|-------------------------|------|
| （无） | 本项目为绿地项目，`knowledge/domain/` 不存在（知识抽取未初始化），moments-* 模块仅含公司脚手架 infra 代码（Health/Base/FeatureTest/File/Apollo/Security 控制器），无任何社交业务领域 | — | — | — |

**匹配摘要**

| 触达 | 领域 | 功能 | 入口 | 置信度 | 匹配依据 |
|-------|------|------|------|--------|---------|
| user/friendship/friend_tag/post/feed/image/mock-data | 全部为新增领域，无已有领域可匹配 | — | 全部新增 Controller | 高 | `knowledge/indexs/domain-index.md` 不存在；扫描 moments-web/controller 仅见 infra 脚手架；openspec/specs 为空 |
| infra-文件上传 | 已有（不匹配本次需求） | FileController | upload | 排除 | FileServiceImpl 走公司 FileClient 对象存储，PRD §7 要求本地文件夹存储，需新实现 LocalImageService |

**图谱查询输入**

| ID | 查询种子 | 必读文件 | 风险提示 |
|----|---------|---------|---------|
| 20260920-social-timeline-mvp | 好友关系 / 标签 / 朋友圈 / 隐私权限 / Feed（预期全部为新增链路，无已有链路） | `openspec/changes/20260920-social-timeline-mvp/prd.md`（含澄清补充 C1-C17）、`moments-web/src/main/java/com/funny/moments/web/controller/`（仅 infra）、`moments-dao/src/main/java/com/funny/moments/dao/CodeGenerator.java` | ⚠️ graphify 图谱不存在（新项目），graph-query 将走降级模式；链路基于领域文档推导 |

**已排除领域**

| 领域 | 排除原因 |
|------|---------|
| infra-文件上传（FileController/FileServiceImpl） | 实现绑定公司 FileClient 对象存储，与 PRD 本地文件夹存储要求不符；且其允许 pdf/zip 等类型与图片场景不符。本次新建本地图片服务，不动已有 infra 代码（最小改动） |
| infra-签名/安全（SecurityController/DemoSignConfig） | PRD §8 明确不做登录验证；签名拦截器仅拦 `/leads/**`，与本需求无交集 |
| infra-配置中心（ApolloController） | 与本需求无交集 |

二、新增的领域或功能

| 领域 | 功能 | 模块 | 说明 |
|------|------|------|------|
| user | 模拟用户数据 | web/service/dao | userId/nickname/avatar/gender/city/createTime；无注册登录；页面：用户列表 |
| friendship | 好友关系 | web/service/dao | 双向好友（存两条对称记录）；页面：我的好友、好友详情 |
| friend-tag | 好友标签 | web/service/dao | 标签 CRUD、好友-标签绑定/解绑、按标签查好友；标签归属创建人；页面：标签管理、好友标签管理 |
| mock-data | 模拟数据生成 | web/service/dao | 可配置用户数/好友数/标签数批量初始化；1000+ 好友关系；按 PRD §2.5 真实社交分布（同事25%/同学20%/朋友25%/家人5%/亲戚5%/兴趣15%/其他5%）；页面：模拟数据生成 |
| post | 朋友圈发布 | web/service/dao | 纯文字/纯图/图文，最多 9 图；图片单独存储带 sort；软删除；页面：发布页 |
| visibility | 隐私权限 | service/dao | 4 种可见范围（公开/私密/部分可见/不给谁看），标签 OR 好友匹配；canView(userId, postId) 统一判断 |
| feed | 朋友圈 Feed | web/service/dao | 自己+好友帖子，权限过滤，createTime DESC，Cursor 分页（不.Offset）；页面：朋友圈瀑布流 |
| image | 图片上传 | web/service | 本地文件夹存储 + 静态映射访问；jpg/png/gif/webp ≤5MB；模拟占位图生成 |

三、跨模块交互

| 交互关系 | 源模块 | 目标模块 | 触发场景 | 数据流向 |
|---------|--------|---------|---------|---------|
| 前端页面调用 | frontend 静态页 | moments-web Controller | 页面交互（用户列表/好友/标签/发圈/Feed） | 页面 → REST API（同源，Cookie 携带 mockUserId） |
| 分层调用 | moments-web | moments-service | 所有 API 请求 | Controller → Service → Dao（三层规范） |
| 数据访问 | moments-dao | MySQL 8.0 | 全部业务读写 | Mapper XML → user/friendship/friend_tag/friend_tag_relation/post/post_image/post_visibility_user/post_visibility_tag 8 张新表 |
| 缓存 | moments-service | Redis | canView 高频判断 | 好友 ID 集合缓存 + 标签缓存，变更失效，失败回源 DB |
| 静态资源 | moments-web | 本地图片文件夹 | 图片上传后访问 | /images/{fileName} 静态映射 → 磁盘读取 |
