package com.funny.moments.service.social.cache;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.funny.framework.redis.RedisClient;
import com.funny.moments.common.consts.CacheKeyConsts;
import com.funny.moments.dao.mapper.FriendshipMapper;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import redis.clients.jedis.Jedis;

/**
 * 好友 ID 集合 Redis 缓存组件（design §5.6 规则8/§6.2，T020；C12 弱依赖）：
 * key = moments:frd:{fver}:{userId}（Redis Set，TTL 1h），全局版本号失效（evictAll=INCR moments:fver）。
 *
 * <p>空集哨兵（建议8）：无好友用户 miss 回源后回填哨兵成员 "0"（userId 恒&gt;0 与真实 ID 不冲突），
 * 防无好友用户每请求穿透 DB；读取时过滤哨兵，仅含哨兵视为有效空集缓存。
 * Redis 异常全 catch（log.warn）回源 DB，不上抛（Feed 可用性优先）。
 *
 * @Author: funny2048
 * @Date: 2026-09-21
 */
@Slf4j
@Component
public class FriendIdsCacheManager {

    /** 空集哨兵成员："0" 占位=有效空集缓存（真实 userId 恒 >0 不冲突） */
    private static final String EMPTY_SENTINEL = "0";

    /** 缓存 TTL：1 小时（design §8） */
    private static final int CACHE_TTL_SECONDS = 3600;

    /** 版本 key 缺省值：Redis 无版本记录时视为 "0" 版本 */
    private static final String DEFAULT_VERSION = "0";

    @Autowired
    private RedisClient<Jedis> redisClient;

    @Autowired
    private FriendshipMapper friendshipMapper;

    /**
     * 查询用户好友 ID 集合：命中返回（过滤哨兵）；miss 回源 DB 并回填（空集回填哨兵）；
     * Redis 异常 log.warn 后回源 DB（C12 弱依赖降级）。
     *
     * @param userId 用户 ID（恒 >0，由入口 B3 校验保证）
     * @return 好友 ID 集合（无好友返回空集合，禁 null）
     */
    public Set<Long> get(Long userId) {
        try {
            // 全局版本号：bind/unbind/deleteTag/mockData 后 INCR 使旧 key 自然失效（A4）
            String version = redisClient.get(CacheKeyConsts.CACHE_MOMENTS_FRIEND_VER);
            String key = String.format(CacheKeyConsts.CACHE_MOMENTS_FRIEND_IDS,
                    version == null ? DEFAULT_VERSION : version, userId);
            Set<String> members = redisClient.smembers(key);
            if (CollectionUtils.isNotEmpty(members)) {
                // 命中：过滤哨兵 "0" 后转 Long；仅含哨兵=有效空集缓存，返回空集合（不穿透 DB）
                Set<Long> hitIds = members.stream()
                        .filter(member -> !EMPTY_SENTINEL.equals(member))
                        .map(Long::valueOf)
                        .collect(Collectors.toSet());
                log.info("friendIds cache hit, key={}, memberCount={}", key, hitIds.size());
                return hitIds;
            }
            // miss：回源 DB；空集也回填哨兵 "0"（建议8：防无好友用户每请求穿透 DB）
            List<Long> dbIds = friendshipMapper.selectFriendIds(userId);
            log.info("friendIds cache miss, fallback to DB and refill, key={}, dbCount={}, emptySentinel={}",
                    key, dbIds.size(), dbIds.isEmpty());
            if (dbIds.isEmpty()) {
                redisClient.sadd(key, EMPTY_SENTINEL);
            } else {
                String[] idArray = dbIds.stream().map(String::valueOf).toArray(String[]::new);
                redisClient.sadd(key, idArray);
            }
            redisClient.expire(key, CACHE_TTL_SECONDS);
            return new HashSet<>(dbIds);
        } catch (Exception e) {
            // Redis 异常回源 DB（C12）：warn 含 userId 与异常对象，不上抛
            log.warn("friendIds cache fail, fallback to DB, userId={}", userId, e);
            return new HashSet<>(friendshipMapper.selectFriendIds(userId));
        }
    }

    /**
     * 全局版本失效（A4）：INCR moments:fver 使全部旧版本 key 自然过期，避免 SCAN 批删。
     * 供 mockData 后调用（事务外，design §5.5）。
     */
    public void evictAll() {
        redisClient.incr(CacheKeyConsts.CACHE_MOMENTS_FRIEND_VER);
    }
}
