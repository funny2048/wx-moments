# finder=L1 batch-2（业务层）
```json
[
  {"file":"moments-service/src/main/java/com/funny/moments/service/social/impl/FriendTagServiceImpl.java","line":141,"lens":"L1","severity":"HIGH","claim":"写路径缓存失效 evictAll() 无异常降级，deleteTag 的 afterCommit 回调内 Redis INCR 异常会向上传播，与既有 C12 弱依赖降级范式（MockDataServiceImpl.evictCaches / CacheManager.get 的 catch+warn）口径不一致","evidence":{"trigger":"Redis 不可用或抖动时调用 createTag/updateTag/bindUser/unbindUser（L97/L117/L172/L182）或 deleteTag（L141 afterCommit 回调内 evictAll，事务已提交）","failure":"DB 已成功提交但接口返回系统异常（重试 createTag 得伪 1005、重试 deleteTag 得伪 1004）；版本号未 INCR 旧缓存 stale 至 TTL 1h，违反项目自定 C12 Redis 异常降级 warn 后继续口径"}},
  {"file":"moments-service/src/main/java/com/funny/moments/service/social/impl/UserServiceImpl.java","line":60,"lens":"L1","severity":"MEDIUM","claim":"listUsers 的 pageNo 只校验下界 <1 未校验上界（java-guide §7.2），(no-1)*size 为 int 运算可溢出为负","evidence":{"trigger":"pageNo=2147483647、pageSize=500：校验通过（no>=1, size 1..500），(no-1)*size int 溢出为负传入 LIMIT offset","failure":"MySQL LIMIT 负偏移 SQL 报错接口 500；即便不溢出也产生深分页全表扫描；同批 FeedServiceImpl pageSize<=50 / PostServiceImpl limit<=500 均有上界，唯独 pageNo 缺失"}},
  {"file":"moments-service/src/main/java/com/funny/moments/service/social/impl/LocalImageServiceImpl.java","line":81,"lens":"L1","severity":"MEDIUM","claim":"upload 仅按 originalFilename 扩展名白名单+大小校验，未校验文件实际内容（魔数/Content-Type）即 getBytes() 落盘到 /images/ 静态目录（java-guide §7.4 外部输入不可信）","evidence":{"trigger":"上传任意 payload 改名 .jpg，扩展名在白名单、大小 <=5MB，校验全部通过","failure":"非图片内容以图片扩展名持久化进对外静态资源目录，形成脏数据与潜在存储型攻击面"}},
  {"file":"moments-service/src/main/java/com/funny/moments/service/social/impl/PostServiceImpl.java","line":103,"lens":"L1","severity":"LOW","claim":"createPost 约 103 行（L103-205）、FeedServiceImpl.getFeed 约 104 行（L87-190），超 java-guide §12 方法体<=50 行建议值 2 倍","evidence":{"trigger":"静态测量：createPost 含 9 个编号步骤单方法内联完成","failure":"可维护性下降：后续在写路径插入步骤需在百行方法内改动；建议级条款"}}
]
```
note: 4 条未达上限。不报项：pom 依赖内聚合理；deleteTag 非幂等、createTag 并发查重窗口为 design §5.6 规则5b / §4.1 显式裁决。
