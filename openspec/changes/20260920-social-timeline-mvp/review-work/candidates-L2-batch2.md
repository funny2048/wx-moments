# finder=L2 batch-2（业务层）
```json
[
  {"file":"moments-service/src/main/java/com/funny/moments/service/social/impl/MockDataServiceImpl.java","line":377,"lens":"L2","severity":"MEDIUM","claim":"generateTags 的 tagId 回读时序错位：ownerBuffer 满 500 触发 readBackTagIds 回读 DB 时，buffer 中尚未 batchInsert 的标签行查不到，这部分 owner 的标签映射永久缺失","evidence":{"trigger":"userCount>500（默认 100 即触发）。标签行 buffer flush 条件 buffer.size()+tagsPerUser>500（L361），ownerBuffer 每 user +1（L374）、满 500 即回读（L376-378），两节奏不同步：触发回读时 buffer 仍有未落库行（tagsPerUser=8 时约最后 4 个 user 的 32 行仍在内存）","failure":"selectByOwnerIds（L398）查不到仍在 buffer 的行且 ownerBuffer.clear()（L403）后不再重读 → 这些 owner 的 tagName→tagId 映射永久缺失 → generateBindings 对其 continue 静默跳过（L422-424）、type=3/4 帖子标签名单为空（L555-558）→ 批量生成结果静默缺绑定/缺可见性名单，接口不报错"}},
  {"file":"moments-service/src/main/java/com/funny/moments/service/social/impl/FriendTagServiceImpl.java","line":172,"lens":"L2","severity":"HIGH","claim":"标签全部写路径（createTag/updateTag/bindUser/unbindUser/deleteTag）直调 evictAll() 且无 Redis 异常降级，与设计 C12 弱依赖声明直接矛盾","evidence":{"trigger":"Redis 不可用时调用任一标签写接口。L97/L117/L172/L182 直调 evictAll()，FriendTagCacheManager L101-103 redisClient.incr 无 try-catch；对比 MockDataServiceImpl.evictCaches（L255-262）有 try-catch 降级示范、两个 cache manager 读路径均有 catch，证明是遗漏而非设计取舍","failure":"① Redis 故障 → 5 个标签写接口全部 500，违背 C12 弱依赖声明；② createTag 的 insert 先于 evictAll 执行，异常时标签已落库但客户端收到失败，重试触发 1005 重名误报；③ deleteTag afterCommit 回调在事务提交后抛异常，已删成功却报错，重试报 1004"}}
]
```
补充（非候选）：FriendshipServiceImpl.getFriendDetail 未校验 friendUserId 与 viewerId 好友关系，因 api.md 不在本 batch 无法确证是否设计开放查询；cache manager sadd+expire 非原子残留 key 不被读取。
