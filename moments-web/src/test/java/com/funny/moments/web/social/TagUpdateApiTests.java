package com.funny.moments.web.social;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.funny.moments.service.social.cache.FriendIdsCacheManager;
import com.funny.moments.service.social.cache.FriendTagCacheManager;
import com.funny.moments.web.social.support.SocialTestSupport;

import lombok.extern.slf4j.Slf4j;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * 修改标签接口集成测试（入口7：PUT /api/friend-tags/{tagId}，TC037-TC045）。
 * 写法要点：写操作 @Transactional 回滚 + 查库断言（tag_name/created_stime 不变核对）；
 * TC044 并发用例 NOT_SUPPORTED 真实提交 + @AfterEach 清理。
 *
 * @Author: tester（阶段六）
 * @Date: 2026-09-21
 */
@Slf4j
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
public class TagUpdateApiTests {

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
     * TC037 主干改名：最小更新语义。
     * 规则1 code=0；data.tagName=老同学、friendCount=1
     * 规则2 DB 核对：tag_name='老同学'；created_stime 不变（仅 tag_name/modified_stime 变）
     */
    @Test
    public void updateTagNameMain() throws Exception {
        String createdBefore = tagNameOf(SocialTestSupport.TAG_CLASSMATE);
        String createdStimeBefore = createdStimeOf(SocialTestSupport.TAG_CLASSMATE);

        MvcResult result = putTag(SocialTestSupport.TAG_CLASSMATE, "老同学", SocialTestSupport.ZHANGSAN);
        assertEquals(0, SocialTestSupport.codeOf(result), "改名应成功，resp=" + SocialTestSupport.bodyUtf8(result));
        assertEquals("老同学", SocialTestSupport.dataMapOf(result).get("tagName"), "出参 tagName");
        assertEquals(1, SocialTestSupport.toInt(SocialTestSupport.dataMapOf(result).get("friendCount")),
                "出参 friendCount（同学仍绑李四）");
        assertEquals("老同学", tagNameOf(SocialTestSupport.TAG_CLASSMATE), "DB 核对：tag_name 已更新");
        assertEquals(createdStimeBefore, createdStimeOf(SocialTestSupport.TAG_CLASSMATE),
                "DB 核对：created_stime 不变");
        assertEquals("同学", createdBefore, "前置自检：基线名为同学");
    }

    /**
     * TC038 参数无效合并：tagId=0/abc（body 合法）+ body tagName=""/17 字。
     * 规则1 四笔均 code=1001
     */
    @Test
    public void updateTagInvalidParams() throws Exception {
        MvcResult r1 = putTag(0, "合法名", SocialTestSupport.ZHANGSAN);
        assertEquals(1001, SocialTestSupport.codeOf(r1), "tagId=0 应 1001");
        MvcResult r2 = putRaw("/api/friend-tags/abc?_appId=it-test&viewerId=" + SocialTestSupport.ZHANGSAN,
                "{\"tagName\":\"合法名\"}");
        assertEquals(1001, SocialTestSupport.codeOf(r2), "tagId=abc 应 1001（TypeMismatch 承载）");
        MvcResult r3 = putTag(SocialTestSupport.TAG_CLASSMATE, "", SocialTestSupport.ZHANGSAN);
        assertEquals(1001, SocialTestSupport.codeOf(r3), "tagName 空串应 1001");
        MvcResult r4 = putTag(SocialTestSupport.TAG_CLASSMATE, "01234567890123456", SocialTestSupport.ZHANGSAN);
        assertEquals(1001, SocialTestSupport.codeOf(r4), "tagName 17 字应 1001");
    }

    /**
     * TC039 标签不存在 → 1004。
     * 口径说明：以 max(id)+100000 替代字面 999999（必然不存在，语义等同）。
     */
    @Test
    public void updateTagNotFound() throws Exception {
        long nonExisting = SocialTestSupport.nonExistingId(jdbc, "friend_tag");
        MvcResult result = putTag(nonExisting, "x", SocialTestSupport.ZHANGSAN);
        assertEquals(1004, SocialTestSupport.codeOf(result), "标签不存在应 1004");
    }

    /**
     * TC040 非归属人修改（水平越权）：李四改张三的标签。
     * 规则1 code=1006；DB 核对 tag_name 不变
     */
    @Test
    public void updateTagNotOwner() throws Exception {
        MvcResult result = putTag(SocialTestSupport.TAG_CLASSMATE, "hack", SocialTestSupport.LISI);
        assertEquals(1006, SocialTestSupport.codeOf(result), "非归属人应 1006");
        assertEquals("同学", tagNameOf(SocialTestSupport.TAG_CLASSMATE), "DB 核对：越权改名不生效");
    }

    /**
     * TC041 新名与其他标签重名：同学 → 同事 → 1005。
     * 规则1 code=1005；DB 核对不变
     */
    @Test
    public void updateTagNameConflicts() throws Exception {
        MvcResult result = putTag(SocialTestSupport.TAG_CLASSMATE, "同事", SocialTestSupport.ZHANGSAN);
        assertEquals(1005, SocialTestSupport.codeOf(result), "与他标签重名应 1005");
        assertEquals("同学", tagNameOf(SocialTestSupport.TAG_CLASSMATE), "DB 核对：重名改名不生效");
    }

    /**
     * TC042 改为自身现名：查重排除自身语义。
     * 规则1 code=0（自身同名非重复）；DB 核对 tag_name 不变
     */
    @Test
    public void updateTagToOwnName() throws Exception {
        MvcResult result = putTag(SocialTestSupport.TAG_CLASSMATE, "同学", SocialTestSupport.ZHANGSAN);
        assertEquals(0, SocialTestSupport.codeOf(result), "改为自身现名应成功");
        assertEquals("同学", tagNameOf(SocialTestSupport.TAG_CLASSMATE), "DB 核对：tag_name 不变");
    }

    /**
     * TC043 幂等-重复请求：同一改名请求重放 2 次。
     * 规则1 均 code=0；DB 核对终值唯一（重放不产生额外效果）
     */
    @Test
    public void updateTagRepeatRequest() throws Exception {
        MvcResult first = putTag(SocialTestSupport.TAG_CLASSMATE, "重放A", SocialTestSupport.ZHANGSAN);
        assertEquals(0, SocialTestSupport.codeOf(first), "首次改名应成功");
        MvcResult second = putTag(SocialTestSupport.TAG_CLASSMATE, "重放A", SocialTestSupport.ZHANGSAN);
        assertEquals(0, SocialTestSupport.codeOf(second), "重放应同样成功");
        assertEquals("重放A", tagNameOf(SocialTestSupport.TAG_CLASSMATE), "DB 核对：终值唯一");
    }

    /**
     * TC044 幂等-并发：并发 2 笔分别改"名A"与"名B"（后写覆盖）。
     * 规则1 DB 终值为 名A 或 名B 之一，无损坏/无空值
     * 口径说明：@Transactional 管不到并发线程，NOT_SUPPORTED 真实提交 + @AfterEach 清理。
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void updateTagConcurrent() throws Exception {
        java.util.List<Integer> codes = new CopyOnWriteArrayList<>();
        runConcurrent(() -> codes.add(SocialTestSupport.codeOf(
                putTag(SocialTestSupport.TAG_CLASSMATE, "名A", SocialTestSupport.ZHANGSAN))), () -> codes.add(
                SocialTestSupport.codeOf(putTag(SocialTestSupport.TAG_CLASSMATE, "名B",
                        SocialTestSupport.ZHANGSAN))));

        String finalName = tagNameOf(SocialTestSupport.TAG_CLASSMATE);
        log.info("TC044 并发改名观察：codes={}, 终值={}", codes, finalName);
        assertNotNull(finalName, "终值不应为空");
        assertTrue("名A".equals(finalName) || "名B".equals(finalName), "终值应为 名A/名B 之一，实际=" + finalName);
        assertTrue(codes.stream().allMatch(c -> c == 0), "两笔改名均应成功，codes=" + codes);
    }

    /**
     * TC045 幂等-失败重试：1005 失败后修正重试成功。
     * 规则1 ①改"同事" 1005 → ②修正为"新同学" code=0
     */
    @Test
    public void updateTagFailThenRetry() throws Exception {
        MvcResult fail = putTag(SocialTestSupport.TAG_CLASSMATE, "同事", SocialTestSupport.ZHANGSAN);
        assertEquals(1005, SocialTestSupport.codeOf(fail), "重名应 1005");
        MvcResult retry = putTag(SocialTestSupport.TAG_CLASSMATE, "新同学", SocialTestSupport.ZHANGSAN);
        assertEquals(0, SocialTestSupport.codeOf(retry), "修正重试应成功");
        assertEquals("新同学", tagNameOf(SocialTestSupport.TAG_CLASSMATE), "DB 核对：终值生效");
    }

    /** PUT 改名（tagId 为 long 时走本重载） */
    private MvcResult putTag(long tagId, String tagName, long viewerId) throws Exception {
        return putRaw("/api/friend-tags/" + tagId + "?_appId=it-test&viewerId=" + viewerId,
                "{\"tagName\":\"" + tagName + "\"}");
    }

    private MvcResult putRaw(String url, String body) throws Exception {
        MockHttpServletRequestBuilder builder = put(url).contentType(MediaType.APPLICATION_JSON)
                .content(body.getBytes(StandardCharsets.UTF_8)).accept(MediaType.APPLICATION_JSON);
        return mockMvc.perform(builder).andReturn();
    }

    /** 两任务对齐起跑并发执行（OrderCreateTriggerTests 并发写法）；动作允许抛受检异常 */
    private void runConcurrent(ConcurrentAction actionA, ConcurrentAction actionB) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        pool.submit(() -> {
            ready.countDown();
            try {
                start.await();
                actionA.run();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                log.warn("并发动作A执行异常", e);
            }
        });
        pool.submit(() -> {
            ready.countDown();
            try {
                start.await();
                actionB.run();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                log.warn("并发动作B执行异常", e);
            }
        });
        ready.await();
        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(60, TimeUnit.SECONDS), "并发请求应全部完成");
    }

    private String tagNameOf(long tagId) {
        return jdbc.queryForObject("SELECT tag_name FROM friend_tag WHERE id = ?", String.class, tagId);
    }

    private String createdStimeOf(long tagId) {
        return jdbc.queryForObject("SELECT created_stime FROM friend_tag WHERE id = ?", String.class, tagId);
    }

    /** 可抛受检异常的并发动作（MvcResult 取回链路声明 throws Exception） */
    @FunctionalInterface
    interface ConcurrentAction {

        void run() throws Exception;
    }
}
