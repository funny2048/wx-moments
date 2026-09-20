package com.funny.moments.service.social.cache;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.funny.framework.redis.RedisClient;
import com.funny.moments.common.consts.CacheKeyConsts;
import com.funny.moments.dao.mapper.FriendTagRelationMapper;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import redis.clients.jedis.Jedis;

/**
 * 标签绑定 Redis 缓存组件（design §5.6 规则8/§6.2，T030；C12 弱依赖）：
 * key = moments:ftag:{ftagver}:{ownerId}:{friendUserId}（Redis Set，TTL 1h），
 * 全局版本号失效（evictAll=INCR moments:ftagver）。
 *
 * <p>空集哨兵（R2-建议2，与好友缓存防御对称）：无标签 viewer 是 mock 常态，miss 回源后空集
 * 同样回填哨兵成员 "0"（tagId 恒&gt;0 与真实 ID 不冲突），防 canView 的 tagHit 每帖穿透 DB
 * 点查；读取时过滤哨兵，仅含哨兵视为有效空集缓存。Redis 异常全 catch（log.warn）回源 DB。
 *
 * <p>失效时机（design §5.6 规则8/建议3 定稿）：deleteTag 在 afterCommit 回调内 evictAll
 * （事务内禁 Redis）；bind/unbind 等单 DML 写方法无事务注解，写后顺序调用即事务外。
 *
 * @Author: funny2048
 * @Date: 2026-09-21
 */
@Slf4j
@Component
public class FriendTagCacheManager {

    /** 空集哨兵成员："0" 占位=有效空集缓存（真实 tagId 恒 >0 不冲突） */
    private static final String EMPTY_SENTINEL = "0";

    /** 缓存 TTL：1 小时（design §8） */
    private static final int CACHE_TTL_SECONDS = 3600;

    /** 版本 key 缺省值：Redis 无版本记录时视为 "0" 版本 */
    private static final String DEFAULT_VERSION = "0";

    @Autowired
    private RedisClient<Jedis> redisClient;

    @Autowired
    private FriendTagRelationMapper friendTagRelationMapper;

    /**
     * 查询 owner 给好友打的标签 ID 集合（canView tagHit 数据源）：命中返回（过滤哨兵）；
     * miss 回源 DB 并回填（空集回填哨兵）；Redis 异常 log.warn 后回源 DB（C12 降级）。
     *
     * @param ownerId       标签归属人（帖子作者）
     * @param friendUserId  好友用户 ID（观众）
     * @return 标签 ID 集合（无标签返回空集合，禁 null；并发残留重复行经 Set 去重）
     */
    public Set<Long> getTagIds(Long ownerId, Long friendUserId) {
        try {
            // 全局版本号：bind/unbind/deleteTag/mockData 后 INCR 使旧 key 自然失效（A4）
            String version = redisClient.get(CacheKeyConsts.CACHE_MOMENTS_FTAG_VER);
            String key = String.format(CacheKeyConsts.CACHE_MOMENTS_FRIEND_TAGS,
                    version == null ? DEFAULT_VERSION : version, ownerId, friendUserId);
            Set<String> members = redisClient.smembers(key);
            if (CollectionUtils.isNotEmpty(members)) {
                // 命中：过滤哨兵 "0" 后转 Long；仅含哨兵=有效空集缓存，返回空集合（不穿透 DB）
                Set<Long> hitIds = members.stream()
                        .filter(member -> !EMPTY_SENTINEL.equals(member))
                        .map(Long::valueOf)
                        .collect(Collectors.toSet());
                log.info("friendTagIds cache hit, key={}, tagCount={}", key, hitIds.size());
                return hitIds;
            }
            // miss：回源 DB；空集也回填哨兵 "0"（R2-建议2：防无标签 viewer 每帖穿透 DB 点查）
            List<Long> dbTagIds = friendTagRelationMapper.selectTagIdsOfFriend(ownerId, friendUserId);
            log.info("friendTagIds cache miss, fallback to DB and refill, key={}, dbCount={}, emptySentinel={}",
                    key, dbTagIds.size(), dbTagIds.isEmpty());
            if (dbTagIds.isEmpty()) {
                redisClient.sadd(key, EMPTY_SENTINEL);
            } else {
                String[] idArray = dbTagIds.stream().map(String::valueOf).toArray(String[]::new);
                redisClient.sadd(key, idArray);
            }
            redisClient.expire(key, CACHE_TTL_SECONDS);
            return new HashSet<>(dbTagIds);
        } catch (Exception e) {
            // Redis 异常回源 DB（C12）：warn 含 ownerId/friendUserId 与异常对象，不上抛
            log.warn("friendTagIds cache fail, fallback to DB, ownerId={}, friendUserId={}",
                    ownerId, friendUserId, e);
            return new HashSet<>(friendTagRelationMapper.selectTagIdsOfFriend(ownerId, friendUserId));
        }
    }

    /**
     * 全局版本失效（A4）：INCR moments:ftagver 使全部旧版本 key 自然过期。
     * 供 bind/unbind/deleteTag（afterCommit 回调）/mockData 后调用（事务外，design §5.6 规则8）。
     *
     * <p>弱依赖降级（design §9 C12 / code-review CONFIRMED HIGH #1）：INCR 失败仅 log.warn 不上抛
     * ——DB 写已提交（或事务已 commit），上抛会把成功写误报 5xx，重试再触发伪 1005/1004；
     * 旧版本 key 残留由 TTL 1h 自然过期兜底，与同文件读路径异常降级口径一致。
     */
    public void evictAll() {
        try {
            redisClient.incr(CacheKeyConsts.CACHE_MOMENTS_FTAG_VER);
        } catch (Exception e) {
            // Redis 抖动降级：不影响已提交的写结果，靠 key TTL 自然过期兜底
            log.warn("friendTagIds cache evict fail, skip INCR, fallback to TTL expiry", e);
        }
    }
}
