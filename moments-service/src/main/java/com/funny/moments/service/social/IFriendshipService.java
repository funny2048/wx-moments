package com.funny.moments.service.social;

import java.util.List;
import java.util.Set;

import com.funny.moments.model.out.FriendDetailOut;
import com.funny.moments.model.out.FriendOut;

/**
 * 好友查询 Service（design §3.2.3/§3.2.4/§6.2，T020）：只读无事务；
 * listFriendIds 走 FriendIdsCacheManager 缓存（Feed 高频读铺垫）。
 *
 * @Author: funny2048
 * @Date: 2026-09-21
 */
public interface IFriendshipService {

    /**
     * 我的好友列表（api.md 接口3）：tagId 传入时按标签过滤（先校验标签归属），否则全量好友。
     *
     * @param viewerId 当前用户 ID（恒 >0，入口已解析）
     * @param tagId    标签过滤（可选，null=全部好友；<=0 抛 1001）
     * @return 好友列表（无好友返回空列表；含 tagNames 组装，顺序按标签 id）
     */
    List<FriendOut> listFriends(Long viewerId, Long tagId);

    /**
     * 好友详情（api.md 接口4）：好友基础信息 + 我给其打的标签。
     *
     * @param viewerId      当前用户 ID
     * @param friendUserId  好友用户 ID，null 或 <=0 抛 1001
     * @return 好友详情；用户不存在（含已删除）抛 1002
     */
    FriendDetailOut getFriendDetail(Long viewerId, Long friendUserId);

    /**
     * 当前用户的好友 ID 集合（design §6.2）：走 Redis 缓存，miss/异常回源 DB。
     *
     * @param viewerId 当前用户 ID
     * @return 好友 ID 集合（无好友返回空集合，禁 null）
     */
    Set<Long> listFriendIds(Long viewerId);
}
