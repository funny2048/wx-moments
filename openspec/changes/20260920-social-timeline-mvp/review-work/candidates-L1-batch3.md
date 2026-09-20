# finder=L1 batch-3（接入层+静态页）
```json
[
  {"file":"moments-web/src/main/java/com/funny/moments/web/controller/social/MockDataController.java","line":37,"lens":"L1","severity":"HIGH","claim":"破坏性写接口（clear=true 软删 8 表全量业务数据）无任何权限/角色校验且无 @Profile 环境守卫，与同变更 SchemaInitRunner 的 @Profile(\"dev\") 守卫口径不一致（java-guide §9）","evidence":{"trigger":"任意未认证调用方直接 POST /api/mock-data，body 含 clear=true + userCount 等","failure":"8 张表全部用户/好友/标签/帖子数据被软删重建；接口未限定 dev profile，非 dev 环境部署后同样可被任意调用"}},
  {"file":"moments-web/src/main/java/com/funny/moments/web/controller/social/PostController.java","line":47,"lens":"L1","severity":"MEDIUM","claim":"新增 C 端写接口（发帖/删帖/标签 CRUD/绑定/图片上传）均无 apiKey+timestamp+sign 签名认证，签名拦截器仅注册 /leads/**，/api/** 写接口不在覆盖内（java-guide §2.1；design 文档化为实验室 MVP mock 模式，是否豁免由 verifier 复核）","evidence":{"trigger":"直接 POST /api/posts 或 DELETE /api/friend-tags/{tagId}，不携带任何签名参数","failure":"请求直达业务逻辑，无防篡改/防重放，签名认证机制完全旁路"}},
  {"file":"moments-web/src/main/java/com/funny/moments/web/support/CurrentUserResolver.java","line":44,"lens":"L1","severity":"MEDIUM","claim":"写操作归属校验的身份基准取自客户端可控的 viewerId query 参数 / mockUserId Cookie，任意调用方可声明任意 userId 通过仅作者可删(1012)/标签归属(1006) 校验，构成水平越权面（java-guide §9；design §6.1 A3/B3 已明确为实验室用户切换机制，预期豁免由 verifier 判定）","evidence":{"trigger":"非作者调用方发起 DELETE /api/posts/{postId}?viewerId={作者id}","failure":"冒充作者身份通过归属校验，删除/篡改他人帖子与标签数据"}}
]
```
补充（非候选）：CORS test.funny.com 存量不在 diff；multipart 6MB/12MB 硬编码有 Stage6 裁决4 豁免留痕。
