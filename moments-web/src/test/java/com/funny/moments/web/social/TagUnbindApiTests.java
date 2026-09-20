package com.funny.moments.web.social;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

import com.funny.moments.service.social.cache.FriendIdsCacheManager;
import com.funny.moments.service.social.cache.FriendTagCacheManager;
import com.funny.moments.web.social.support.SocialTestSupport;

import lombok.extern.slf4j.Slf4j;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * 解绑好友标签接口集成测试（入口10：DELETE /api/friend-tags/{tagId}/users/{userId}，TC065-TC072）。
 * 写法要点：解绑幂等语义（0 行命中不报错）；TC071 并发 NOT_SUPPORTED 真实提交 + @AfterEach 清理。
 *
 * @Author: tester（阶段六）
 * @Date: 2026-09-21
 */
@Slf4j
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
public class TagUnbindApiTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DataSource dataSource;

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
     * TC065 主干解绑：解绑已绑定组合 (同事,王五)。
     * 规则1 code=0
     * 规则2 DB 核对：friend_tag_relation(同事,王五).is_del=1（行保留软删）
     */
    @Test
    public void unbindUserFromTag() throws Exception {
        MvcResult result = unbind(SocialTestSupport.TAG_COLLEAGUE, SocialTestSupport.WANGWU, SocialTestSupport.ZHANGSAN);
        assertEquals(0, SocialTestSupport.codeOf(result), "解绑应成功，resp=" + SocialTestSupport.bodyUtf8(result));
        assertEquals(1, countRelation(SocialTestSupport.TAG_COLLEAGUE, SocialTestSupport.WANGWU, 1),
                "DB 核对：绑定行 is_del=1");
        assertEquals(0, countRelation(SocialTestSupport.TAG_COLLEAGUE, SocialTestSupport.WANGWU, 0),
                "DB 核对：无有效绑定残留");
    }

    /**
     * TC066 幂等语义核心：未绑定解绑也成功（建议1 唯一幂等口径）。
     * 规则1 ①从未绑定的 (同学,赵六) 解绑 → code=0 且 DB 无变化
     * 规则2 ②已解绑组合 (同事,王五) 再解绑一次 → code=0（0 行命中不报错）
     */
    @Test
    public void unbindIdempotentWhenNotBound() throws Exception {
        MvcResult neverBound = unbind(SocialTestSupport.TAG_CLASSMATE, SocialTestSupport.ZHAOLIU,
                SocialTestSupport.ZHANGSAN);
        assertEquals(0, SocialTestSupport.codeOf(neverBound), "未绑定组合解绑应幂等成功");
        assertEquals(0, countRelation(SocialTestSupport.TAG_CLASSMATE, SocialTestSupport.ZHAOLIU, 0),
                "DB 核对：未绑定组合无任何行产生");

        MvcResult first = unbind(SocialTestSupport.TAG_COLLEAGUE, SocialTestSupport.WANGWU, SocialTestSupport.ZHANGSAN);
        assertEquals(0, SocialTestSupport.codeOf(first), "已绑定组合解绑应成功");
        MvcResult again = unbind(SocialTestSupport.TAG_COLLEAGUE, SocialTestSupport.WANGWU, SocialTestSupport.ZHANGSAN);
        assertEquals(0, SocialTestSupport.codeOf(again), "已解绑组合再解绑应仍 code=0（幂等）");
        assertEquals(1, countRelation(SocialTestSupport.TAG_COLLEAGUE, SocialTestSupport.WANGWU, 1),
                "DB 核对：仅 1 行软删，无额外变化");
    }

    /**
     * TC067 解绑后重绑：软删不占键，新 INSERT 语义。
     * 规则1 解绑 (同事,王五) → 重绑 → code=0
     * 规则2 DB 核对：旧行 is_del=1 保留、新行 is_del=0 且 id 不同（无 DB 唯一键阻挡合法重绑）
     */
    @Test
    public void rebindAfterUnbind() throws Exception {
        MvcResult unbindResult = unbind(SocialTestSupport.TAG_COLLEAGUE, SocialTestSupport.WANGWU,
                SocialTestSupport.ZHANGSAN);
        assertEquals(0, SocialTestSupport.codeOf(unbindResult), "解绑应成功");
        Long oldRowId = jdbc.queryForObject(
                "SELECT id FROM friend_tag_relation WHERE tag_id = ? AND friend_user_id = ? AND is_del = 1",
                Long.class, SocialTestSupport.TAG_COLLEAGUE, SocialTestSupport.WANGWU);

        MvcResult bindResult = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/friend-tags/" + SocialTestSupport.TAG_COLLEAGUE + "/users/"
                                + SocialTestSupport.WANGWU + "?_appId=it-test&viewerId=" + SocialTestSupport.ZHANGSAN)
                .accept(MediaType.APPLICATION_JSON)).andReturn();
        assertEquals(0, SocialTestSupport.codeOf(bindResult), "解绑后重绑应成功");

        assertEquals(1, countRelation(SocialTestSupport.TAG_COLLEAGUE, SocialTestSupport.WANGWU, 1),
                "DB 核对：旧行 is_del=1 保留");
        assertEquals(1, countRelation(SocialTestSupport.TAG_COLLEAGUE, SocialTestSupport.WANGWU, 0),
                "DB 核对：新行 is_del=0 恰 1 行");
        Long newRowId = jdbc.queryForObject(
                "SELECT id FROM friend_tag_relation WHERE tag_id = ? AND friend_user_id = ? AND is_del = 0",
                Long.class, SocialTestSupport.TAG_COLLEAGUE, SocialTestSupport.WANGWU);
        assertNotEquals(oldRowId, newRowId, "新行 id 应与旧行不同（新 INSERT 非复活旧行）");
    }

    /**
     * TC068 参数与归属无效合并。
     * 规则1 tagId=0 / userId=abc → 1001；tagId 不存在 → 1004；非归属 → 1006
     */
    @Test
    public void unbindInvalidParamsAndOwnership() throws Exception {
        MvcResult r1 = unbind(0, SocialTestSupport.WANGWU, SocialTestSupport.ZHANGSAN);
        assertEquals(1001, SocialTestSupport.codeOf(r1), "tagId=0 应 1001");
        MvcResult r2 = performDelete("/api/friend-tags/" + SocialTestSupport.TAG_COLLEAGUE + "/users/abc"
                + "?_appId=it-test&viewerId=" + SocialTestSupport.ZHANGSAN);
        assertEquals(1001, SocialTestSupport.codeOf(r2), "userId=abc 应 1001");
        long nonExisting = SocialTestSupport.nonExistingId(jdbc, "friend_tag");
        MvcResult r3 = unbind(nonExisting, SocialTestSupport.WANGWU, SocialTestSupport.ZHANGSAN);
        assertEquals(1004, SocialTestSupport.codeOf(r3), "tagId 不存在应 1004");
        MvcResult r4 = unbind(SocialTestSupport.TAG_CLASSMATE, SocialTestSupport.WANGWU, SocialTestSupport.LISI);
        assertEquals(1006, SocialTestSupport.codeOf(r4), "非归属标签应 1006");
    }

    /**
     * TC069 缓存失效一致性：解绑后回读好友页即时反映。
     * 规则1 预热读（王五 tagNames 含"同事"）→ 解绑 (同事,王五) → 再读王五 tagNames 即时不含"同事"
     */
    @Test
    public void unbindUserCacheConsistency() throws Exception {
        MvcResult warm = performGet("/api/friends?_appId=it-test&viewerId=" + SocialTestSupport.ZHANGSAN);
        assertEquals(0, SocialTestSupport.codeOf(warm), "预热读应成功");
        assertTrue(SocialTestSupport.tagNamesOf(
                        SocialTestSupport.byUserId(SocialTestSupport.dataListOf(warm)).get(SocialTestSupport.WANGWU))
                .contains("同事"), "预热时王五 tagNames 含同事");

        MvcResult unbindResult = unbind(SocialTestSupport.TAG_COLLEAGUE, SocialTestSupport.WANGWU,
                SocialTestSupport.ZHANGSAN);
        assertEquals(0, SocialTestSupport.codeOf(unbindResult), "解绑应成功");

        MvcResult after = performGet("/api/friends?_appId=it-test&viewerId=" + SocialTestSupport.ZHANGSAN);
        assertEquals(0, SocialTestSupport.codeOf(after), "解绑后再读应成功");
        assertFalse(SocialTestSupport.tagNamesOf(
                        SocialTestSupport.byUserId(SocialTestSupport.dataListOf(after)).get(SocialTestSupport.WANGWU))
                        .contains("同事"), "解绑后 tagNames 即时不含同事");
    }

    /**
     * TC070 幂等-重复请求：同一解绑请求二连发。
     * 规则1 均 code=0；DB 核对单次效果（仅 1 行软删）
     */
    @Test
    public void unbindRepeatRequest() throws Exception {
        MvcResult first = unbind(SocialTestSupport.TAG_COLLEAGUE, SocialTestSupport.WANGWU, SocialTestSupport.ZHANGSAN);
        MvcResult second = unbind(SocialTestSupport.TAG_COLLEAGUE, SocialTestSupport.WANGWU, SocialTestSupport.ZHANGSAN);
        assertEquals(0, SocialTestSupport.codeOf(first), "第一次解绑应成功");
        assertEquals(0, SocialTestSupport.codeOf(second), "第二次解绑应幂等成功");
        assertEquals(1, countRelation(SocialTestSupport.TAG_COLLEAGUE, SocialTestSupport.WANGWU, 1),
                "DB 核对：单次软删效果");
        assertEquals(0, countRelation(SocialTestSupport.TAG_COLLEAGUE, SocialTestSupport.WANGWU, 0),
                "DB 核对：无有效行残留");
    }

    /**
     * TC071 幂等-并发：并发 2 笔同一解绑。
     * 规则1 均 code=0（或其中 1 笔 0 行命中幂等成功）；终态 is_del=1
     * 口径说明：@Transactional 管不到并发线程，NOT_SUPPORTED 真实提交 + @AfterEach 清理。
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void unbindConcurrent() throws Exception {
        CopyOnWriteArrayList<Integer> codes = new CopyOnWriteArrayList<>();
        int threads = 2;
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    codes.add(SocialTestSupport.codeOf(unbind(SocialTestSupport.TAG_COLLEAGUE,
                            SocialTestSupport.WANGWU, SocialTestSupport.ZHANGSAN)));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (Exception e) {
                    log.warn("并发解绑执行异常", e);
                }
            });
        }
        ready.await();
        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(60, TimeUnit.SECONDS), "并发请求应全部完成");

        log.info("TC071 并发解绑观察：codes={}", codes);
        assertTrue(codes.stream().allMatch(c -> c == 0), "解绑幂等：两笔均应 code=0，codes=" + codes);
        assertEquals(1, countRelation(SocialTestSupport.TAG_COLLEAGUE, SocialTestSupport.WANGWU, 1),
                "终态：绑定行 is_del=1");
        assertEquals(0, countRelation(SocialTestSupport.TAG_COLLEAGUE, SocialTestSupport.WANGWU, 0),
                "终态：无有效行残留");
    }

    /**
     * TC072 幂等-失败重试：越权失败 → 修正 viewer 重试成功。
     * 规则1 ①李四解绑张三标签 → 1006；②修正为张三重试 → code=0（(同学,赵六) 从未绑定，幂等成功口径）
     */
    @Test
    public void unbindFailThenRetry() throws Exception {
        MvcResult fail = unbind(SocialTestSupport.TAG_CLASSMATE, SocialTestSupport.ZHAOLIU, SocialTestSupport.LISI);
        assertEquals(1006, SocialTestSupport.codeOf(fail), "非归属解绑应 1006");
        MvcResult retry = unbind(SocialTestSupport.TAG_CLASSMATE, SocialTestSupport.ZHAOLIU, SocialTestSupport.ZHANGSAN);
        assertEquals(0, SocialTestSupport.codeOf(retry), "修正 viewer 重试应成功");
        assertEquals(0, countRelation(SocialTestSupport.TAG_CLASSMATE, SocialTestSupport.ZHAOLIU, 0),
                "DB 核对：无绑定行产生");
    }

    private MvcResult unbind(long tagId, long userId, long viewerId) throws Exception {
        return performDelete("/api/friend-tags/" + tagId + "/users/" + userId + "?_appId=it-test&viewerId=" + viewerId);
    }

    private MvcResult performDelete(String url) throws Exception {
        return mockMvc.perform(delete(url).accept(MediaType.APPLICATION_JSON)).andReturn();
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
