# finder=L2 batch-1（数据层+契约层）
```json
[
  {"file":"moments-dao/src/main/resources/schema/social_timeline.sql","line":28,"lens":"L2","severity":"MEDIUM","claim":"uniq_user_friend 唯一索引未含 is_del，与软删模式互斥：软删后的好友对永久占位，同 pair 重建（或 mock 追加模式 pair 去重漏历史组合）整批插入失败回滚","evidence":{"trigger":"同一 (user_id, friend_user_id) 组合在 is_del=1 残留行存在时再次执行 FriendshipMapper.batchInsert（logicDeleteAll 仅置 is_del=1；batchInsert 注释自述 DB uniq_user_friend 兜底防重即唯一键为实际生效约束）","failure":"DuplicateKeyException → 多行 VALUES 单语句整批回滚 mockDataGenerate 全量失败；软删行永不释放 pair 重建路径永久阻断（post_visibility_user/tag 同模式，仅因 post_id 新自增 id 暂未触发）"}},
  {"file":"moments-dao/src/main/resources/mapper/FriendTagMapper.xml","line":71,"lens":"L2","severity":"MEDIUM","claim":"全项目 6 处 foreach IN 均无空集合 XML 防护（无 <if test=...size>0>），完全依赖 Service 前置拦截，漏拦截任一处即运行时 SQL 语法异常","evidence":{"trigger":"最常态路径：从未建过标签的用户请求标签列表（selectByUserId 空 → countFriendsByTagIds 收到空 tagIds）；无好友用户请求好友列表（selectFriendIds 空 → selectTagNamesByOwnerAndFriends 空 friendIds）。6 处：FriendTagMapper.xml:71/85、FriendTagRelationMapper.xml:32、UserMapper.xml:37、PostImageMapper.xml:48、PostMapper.xml:49","failure":"生成 IN () → BadSqlGrammarException 接口 500；同文件 selectUserPosts:32 对 cursorTime 用了 <if> 守护说明模式存在但 IN 处未用"}},
  {"file":"moments-dao/src/main/resources/mapper/FriendTagRelationMapper.xml","line":36,"lens":"L2","severity":"LOW","claim":"selectTagNamesByOwnerAndFriends 的 GROUP BY 含 r.tag_id，与接口 Javadoc 声称的 GROUP BY (friend_user_id, tag_name) 去重口径不符：并发同名标签导致 tagNames 输出重复标签名","evidence":{"trigger":"并发 createTag 同 owner 同 tagName 绕过 1005 查重 → 同 owner 两个同名有效标签且均绑定同一好友","failure":"按标签 id 分组同一好友命中两行 (friendUserId,同名) → FriendOut.tagNames 重复名，Javadoc 承诺的防并发残留防护失效"}}
]
```
补充（非候选）：batchInsert @Param("list")+keyProperty 回填在 MyBatis<3.5.0 有已知缺陷——本地为 mybatis-plus 3.5.x 栈正常，证据不足未列。
