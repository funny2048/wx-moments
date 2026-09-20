package com.funny.moments.web.social;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.funny.moments.dao.entity.FriendshipDO;
import com.funny.moments.dao.mapper.FriendshipMapper;
import com.funny.moments.service.social.cache.FriendIdsCacheManager;
import com.funny.moments.service.social.cache.FriendTagCacheManager;
import com.funny.moments.web.social.support.SocialTestSupport;

import lombok.extern.slf4j.Slf4j;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 绑定好友标签接口集成测试（入口9：POST /api/friend-tags/{tagId}/users/{userId}，TC055-TC064）。
 * 写法要点：写操作 @Transactional 回滚 + 查库断言；TC063 并发 NOT_SUPPORTED 真实提交 + @AfterEach 清理。
 *
 * @Author: tester（阶段六）
 * @Date: 2026-09-21
 */
@Slf4j
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
public class TagBindApiTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private FriendshipMapper friendshipMapper;

    @Autowired
    private FriendIdsCacheManager friendIdsCacheManager;

    @Autowired
    private FriendTagCacheManager friendTagCacheManager;

    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(dataSource);
        SocialTestSupport.seedBaselineA(jdbc);
    }

    @AfterEach
    void tearDown() {
        SocialTestSupport.cleanupBaseline(jdbc);
        friendIdsCacheManager.evictAll();
        friendTagCacheManager.evictAll();
    }

    /**
     * TC055 主干绑定：赵六（无标签）绑定"同学"。
     * 规则1 code=0
     * 规则2 DB 核对：friend_tag_relation +1 行 (tag_id=同学, friend_user_id=赵六, is_del=0)
     */
    @Test
    public void bindUserToTag() throws Exception {
        MvcResult result = bind(SocialTestSupport.TAG_CLASSMATE, SocialTestSupport.ZHAOLIU, SocialTestSupport.ZHANGSAN);
        assertEquals(0, SocialTestSupport.codeOf(result), "绑定应成功，resp=" + SocialTestSupport.bodyUtf8(result));
        assertEquals(1, countRelation(SocialTestSupport.TAG_CLASSMATE, SocialTestSupport.ZHAOLIU, 0),
                "DB 核对：绑定行落库恰 1 行");
    }

    /**
     * TC056 参数无效合并：tagId=0 / tagId=abc / userId=0 / userId=abc。
     * 规则1 四笔均 code=1001（路径参数承载 NB1）
     */
    @Test
    public void bindInvalidParams() throws Exception {
        MvcResult r1 = bind(0, SocialTestSupport.ZHAOLIU, SocialTestSupport.ZHANGSAN);
        assertEquals(1001, SocialTestSupport.codeOf(r1), "tagId=0 应 1001");
        MvcResult r2 = performPost("/api/friend-tags/abc/users/" + SocialTestSupport.ZHAOLIU
                + "?_appId=it-test&viewerId=" + SocialTestSupport.ZHANGSAN, null);
        assertEquals(1001, SocialTestSupport.codeOf(r2), "tagId=abc 应 1001");
        MvcResult r3 = bind(SocialTestSupport.TAG_CLASSMATE, 0, SocialTestSupport.ZHANGSAN);
        assertEquals(1001, SocialTestSupport.codeOf(r3), "userId=0 应 1001");
        MvcResult r4 = performPost("/api/friend-tags/" + SocialTestSupport.TAG_CLASSMATE + "/users/abc"
                + "?_appId=it-test&viewerId=" + SocialTestSupport.ZHANGSAN, null);
        assertEquals(1001, SocialTestSupport.codeOf(r4), "userId=abc 应 1001");
    }

    /**
     * TC057 标签不存在/非归属（1004+1006 合并）。
     * 规则1 不存在 tagId → 1004；李四用张三标签 → 1006
     * 规则2 DB 核对：均 0 行落库
     */
    @Test
    public void bindTagNotFoundOrNotOwner() throws Exception {
        long nonExisting = SocialTestSupport.nonExistingId(jdbc, "friend_tag");
        MvcResult r1 = bind(nonExisting, SocialTestSupport.ZHAOLIU, SocialTestSupport.ZHANGSAN);
        assertEquals(1004, SocialTestSupport.codeOf(r1), "标签不存在应 1004");
        MvcResult r2 = bind(SocialTestSupport.TAG_CLASSMATE, SocialTestSupport.ZHAOLIU, SocialTestSupport.LISI);
        assertEquals(1006, SocialTestSupport.codeOf(r2), "非归属标签应 1006");
        assertEquals(0, countRelation(SocialTestSupport.TAG_CLASSMATE, SocialTestSupport.ZHAOLIU, 0),
                "DB 核对：失败不落库");
    }

    /**
     * TC058 目标用户不存在：1002 先于 1007（B3 裁决：用户存在性校验先于好友关系）。
     * 规则1 用户不存在且必非好友 → code=1002
     */
    @Test
    public void bindUserNotFound() throws Exception {
        long nonExistingUser = SocialTestSupport.nonExistingId(jdbc, "user");
        MvcResult result = bind(SocialTestSupport.TAG_CLASSMATE, nonExistingUser, SocialTestSupport.ZHANGSAN);
        assertEquals(1002, SocialTestSupport.codeOf(result),
                "用户不存在应 1002（存在性校验先于好友关系，B3 裁决），userId=" + nonExistingUser);
    }

    /**
     * TC059 非好友绑定：钱七存在但非张三好友 → 1007。
     * 规则1 code=1007；DB 核对 0 行
     */
    @Test
    public void bindNonFriendUser() throws Exception {
        MvcResult result = bind(SocialTestSupport.TAG_CLASSMATE, SocialTestSupport.QIANQI, SocialTestSupport.ZHANGSAN);
        assertEquals(1007, SocialTestSupport.codeOf(result), "仅可对好友打标签应 1007");
        assertEquals(0, countRelation(SocialTestSupport.TAG_CLASSMATE, SocialTestSupport.QIANQI, 0),
                "DB 核对：非好友绑定不落库");
    }

    /**
     * TC060 重复绑定：INSERT 前查重 → 1008（前置自含：先绑定成功再重复）。
     * 规则1 第 1 次 code=0、第 2 次 code=1008
     * 规则2 DB 核对：(同学,赵六) is_del=0 仍恰 1 行
     */
    @Test
    public void bindUserRepeat() throws Exception {
        MvcResult first = bind(SocialTestSupport.TAG_CLASSMATE, SocialTestSupport.ZHAOLIU, SocialTestSupport.ZHANGSAN);
        assertEquals(0, SocialTestSupport.codeOf(first), "首次绑定应成功");
        MvcResult second = bind(SocialTestSupport.TAG_CLASSMATE, SocialTestSupport.ZHAOLIU, SocialTestSupport.ZHANGSAN);
        assertEquals(1008, SocialTestSupport.codeOf(second), "重复绑定应 1008");
        assertEquals(1, countRelation(SocialTestSupport.TAG_CLASSMATE, SocialTestSupport.ZHAOLIU, 0),
                "DB 核对：组合有效行仍 1 行");
    }

    /**
     * TC061 缓存失效一致性：绑定后回读好友页即时可见。
     * 规则1 预热读（王五 tagNames 不含"同事"）→ 绑定 (同事,王五) → 再读王五 tagNames 即时含"同事"
     * 口径说明：用例前置的 (12,3) 在基线 A 中已绑定（TC065 消费该态），为保证"未绑定→绑定"语义，
     * 本方法先经接口解绑自造前置（前置自含，不依赖用例间顺序）；tagNames 走 DB 直查，回滚事务内即时可见。
     */
    @Test
    public void bindUserCacheConsistency() throws Exception {
        MvcResult unbind = mockMvc.perform(delete("/api/friend-tags/" + SocialTestSupport.TAG_COLLEAGUE + "/users/"
                        + SocialTestSupport.WANGWU + "?_appId=it-test&viewerId=" + SocialTestSupport.ZHANGSAN)
                .accept(MediaType.APPLICATION_JSON)).andReturn();
        assertEquals(0, SocialTestSupport.codeOf(unbind), "前置：接口解绑 (同事,王五) 应成功");

        MvcResult warm = performGet("/api/friends?_appId=it-test&viewerId=" + SocialTestSupport.ZHANGSAN);
        assertEquals(0, SocialTestSupport.codeOf(warm), "预热读应成功");
        assertFalse(SocialTestSupport.tagNamesOf(
                        SocialTestSupport.byUserId(SocialTestSupport.dataListOf(warm)).get(SocialTestSupport.WANGWU))
                .contains("同事"), "前置自检：解绑后预热读不含同事");

        MvcResult bindResult = bind(SocialTestSupport.TAG_COLLEAGUE, SocialTestSupport.WANGWU, SocialTestSupport.ZHANGSAN);
        assertEquals(0, SocialTestSupport.codeOf(bindResult), "重新绑定应成功");

        MvcResult after = performGet("/api/friends?_appId=it-test&viewerId=" + SocialTestSupport.ZHANGSAN);
        assertEquals(0, SocialTestSupport.codeOf(after), "绑定后再读应成功");
        assertTrue(SocialTestSupport.tagNamesOf(
                        SocialTestSupport.byUserId(SocialTestSupport.dataListOf(after)).get(SocialTestSupport.WANGWU))
                .contains("同事"), "绑定后 tagNames 即时含同事");
    }

    /**
     * TC062 幂等-重复请求：同一绑定二连发。
     * 规则1 第 1 次 code=0、第 2 次 code=1008
     * 规则2 DB 核对：(朋友,赵六) is_del=0 组合恰 1 行
     */
    @Test
    public void bindUserRepeatRequest() throws Exception {
        MvcResult first = bind(SocialTestSupport.TAG_FRIEND, SocialTestSupport.ZHAOLIU, SocialTestSupport.ZHANGSAN);
        assertEquals(0, SocialTestSupport.codeOf(first), "首次绑定应成功");
        MvcResult second = bind(SocialTestSupport.TAG_FRIEND, SocialTestSupport.ZHAOLIU, SocialTestSupport.ZHANGSAN);
        assertEquals(1008, SocialTestSupport.codeOf(second), "二连发应 1008");
        assertEquals(1, countRelation(SocialTestSupport.TAG_FRIEND, SocialTestSupport.ZHAOLIU, 0),
                "DB 核对：组合恰 1 行");
    }

    /**
     * TC063 幂等-并发：并发 2 笔绑定 (朋友,赵六)（design §5.6 规则 5 并发窗口）。
     * 规则1 可接受路径：{code=0, 1008} 或极端窗口 {0,0} 落 2 行 is_del=0
     * 规则2 终态断言：展示侧 tagNames 去重（好友页赵六无重复标签名）+ friendCount=COUNT(DISTINCT) 不虚高
     * 口径说明：@Transactional 管不到并发线程，NOT_SUPPORTED 真实提交 + @AfterEach 清理。
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void bindUserConcurrent() throws Exception {
        List<Integer> codes = new CopyOnWriteArrayList<>();
        int threads = 2;
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    codes.add(SocialTestSupport.codeOf(bind(SocialTestSupport.TAG_FRIEND,
                            SocialTestSupport.ZHAOLIU, SocialTestSupport.ZHANGSAN)));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (Exception e) {
                    log.warn("并发绑定执行异常", e);
                }
            });
        }
        ready.await();
        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(60, TimeUnit.SECONDS), "并发请求应全部完成");

        long validRows = countRelation(SocialTestSupport.TAG_FRIEND, SocialTestSupport.ZHAOLIU, 0);
        log.info("TC063 并发绑定观察：codes={}, (朋友,赵六) is_del=0 行数={}", codes, validRows);
        assertTrue(validRows >= 1 && validRows <= 2, "终态：有效行 ∈[1,2]，实际=" + validRows);

        MvcResult friendList = performGet("/api/friends?_appId=it-test&viewerId=" + SocialTestSupport.ZHANGSAN);
        assertEquals(0, SocialTestSupport.codeOf(friendList), "并发后好友页应正常");
        List<String> tagNames = SocialTestSupport.tagNamesOf(
                SocialTestSupport.byUserId(SocialTestSupport.dataListOf(friendList)).get(SocialTestSupport.ZHAOLIU));
        long distinctCount = tagNames.stream().distinct().count();
        assertEquals(distinctCount, tagNames.size(), "展示侧 tagNames 应去重无重复：" + tagNames);

        MvcResult tagList = performGet("/api/friend-tags?_appId=it-test&viewerId=" + SocialTestSupport.ZHANGSAN);
        Map<String, Object> friendTag = SocialTestSupport.byTagName(SocialTestSupport.dataListOf(tagList)).get("朋友");
        // 不虚高口径：friendCount 应等于 DB COUNT(DISTINCT friend_user_id)（基线王五已绑"朋友"，
        // 并发窗口允许 is_del=0 重复行，但计数与 tagNames 均按去重口径展示，不随重复行虚高）
        Long distinctFriends = jdbc.queryForObject(
                "SELECT COUNT(DISTINCT friend_user_id) FROM friend_tag_relation WHERE tag_id = ? AND is_del = 0",
                Long.class, SocialTestSupport.TAG_FRIEND);
        int expectedCount = distinctFriends == null ? 0 : distinctFriends.intValue();
        assertEquals(expectedCount, SocialTestSupport.toInt(friendTag.get("friendCount")),
                "friendCount=COUNT(DISTINCT) 并发残留不虚高（期望去重值=" + expectedCount + "）");
    }

    /**
     * TC064 幂等-失败重试：失败不落库，SQL 补好友关系后重试成功。
     * 规则1 ①绑钱七（非好友）→ 1007 且 DB 0 行
     * 规则2 ②SQL 直插对称好友关系后重试同一请求 → code=0 且 DB +1 行
     * 口径说明：补关系走 Mapper 直插（SQL 层造数，不走业务接口 bindUser）；纯 JdbcTemplate
     * 插入不清 MyBatis 会话一级缓存，重试的 existsFriendship 同参查询会命中旧缓存误报 1007。
     */
    @Test
    public void bindUserFailThenRetry() throws Exception {
        MvcResult fail = bind(SocialTestSupport.TAG_CLASSMATE, SocialTestSupport.QIANQI, SocialTestSupport.ZHANGSAN);
        assertEquals(1007, SocialTestSupport.codeOf(fail), "非好友应 1007");
        assertEquals(0, countRelation(SocialTestSupport.TAG_CLASSMATE, SocialTestSupport.QIANQI, 0),
                "DB 核对：失败 0 行");

        // SQL 补好友关系（对称双记录，C11），与本事务同生命周期回滚。
        // 渠道口径：经 Mapper 直插（SQL 层造数，不走业务接口）——纯 JdbcTemplate 插入不会刷新
        // 测试事务内 MyBatis 会话级一级缓存，重试的 existsFriendship 同参 SELECT 会命中缓存返回旧值 0；
        // Mapper 写语句会清空会话缓存，保证重试读到新插入的好友关系
        FriendshipDO forward = new FriendshipDO();
        forward.setUserId(SocialTestSupport.ZHANGSAN);
        forward.setFriendUserId(SocialTestSupport.QIANQI);
        FriendshipDO backward = new FriendshipDO();
        backward.setUserId(SocialTestSupport.QIANQI);
        backward.setFriendUserId(SocialTestSupport.ZHANGSAN);
        friendshipMapper.batchInsert(List.of(forward, backward));

        MvcResult retry = bind(SocialTestSupport.TAG_CLASSMATE, SocialTestSupport.QIANQI, SocialTestSupport.ZHANGSAN);
        assertEquals(0, SocialTestSupport.codeOf(retry), "补好友关系后重试应成功");
        assertEquals(1, countRelation(SocialTestSupport.TAG_CLASSMATE, SocialTestSupport.QIANQI, 0),
                "DB 核对：重试落库 1 行");
    }

    private MvcResult bind(long tagId, long userId, long viewerId) throws Exception {
        return performPost("/api/friend-tags/" + tagId + "/users/" + userId + "?_appId=it-test&viewerId=" + viewerId,
                null);
    }

    private MvcResult performPost(String url, String body) throws Exception {
        var builder = post(url).accept(MediaType.APPLICATION_JSON);
        if (body != null) {
            builder.contentType(MediaType.APPLICATION_JSON).content(body.getBytes());
        }
        return mockMvc.perform(builder).andReturn();
    }

    private MvcResult performGet(String url) throws Exception {
        return mockMvc.perform(get(url).accept(MediaType.APPLICATION_JSON)).andReturn();
    }

    /** (tagId, friendUserId, is_del) 行数 */
    private long countRelation(long tagId, long friendUserId, int isDel) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM friend_tag_relation WHERE tag_id = ? AND friend_user_id = ? AND is_del = ?",
                Long.class, tagId, friendUserId, isDel);
        return count == null ? 0L : count;
    }
}
