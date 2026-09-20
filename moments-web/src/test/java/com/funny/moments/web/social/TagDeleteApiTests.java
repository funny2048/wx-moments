package com.funny.moments.web.social;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
 * 删除标签接口集成测试（入口8：DELETE /api/friend-tags/{tagId}，TC046-TC054）。
 * 写法要点：级联软删两表 + 可见性表不动核对（C13）；TC047 隐私自然失效走帖子详情端点（入口13）；
 * TC053 并发用例 NOT_SUPPORTED 真实提交 + @AfterEach 清理。
 *
 * @Author: tester（阶段六）
 * @Date: 2026-09-21
 */
@Slf4j
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
public class TagDeleteApiTests {

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
     * TC046 主干：级联软删（C13 两表级联 + 可见性表不动）。
     * 规则1 code=0
     * 规则2 DB 核对：friend_tag(同事).is_del=1；friend_tag_relation tag_id=12 全部 is_del=1
     * 规则3 DB 核对：post_visibility_tag 中 P1/P2 的 tag_id=12 行 is_del 仍=0（不级联，自然失效）
     */
    @Test
    public void deleteTagCascadeSoftDelete() throws Exception {
        MvcResult result = deleteTag(SocialTestSupport.TAG_COLLEAGUE, SocialTestSupport.ZHANGSAN);
        assertEquals(0, SocialTestSupport.codeOf(result), "删除标签应成功，resp=" + SocialTestSupport.bodyUtf8(result));

        assertEquals(1, isDelOf("friend_tag", SocialTestSupport.TAG_COLLEAGUE), "标签行 is_del=1");
        Long relationDeleted = jdbc.queryForObject(
                "SELECT COUNT(*) FROM friend_tag_relation WHERE tag_id = ? AND is_del = 1", Long.class,
                SocialTestSupport.TAG_COLLEAGUE);
        assertEquals(1, relationDeleted == null ? 0 : relationDeleted.intValue(), "绑定行级联 is_del=1");
        Long relationAlive = jdbc.queryForObject(
                "SELECT COUNT(*) FROM friend_tag_relation WHERE tag_id = ? AND is_del = 0", Long.class,
                SocialTestSupport.TAG_COLLEAGUE);
        assertEquals(0, relationAlive == null ? 0 : relationAlive.intValue(), "无残留有效绑定");

        Long visibilityAlive = jdbc.queryForObject(
                "SELECT COUNT(*) FROM post_visibility_tag WHERE tag_id = ? AND is_del = 0", Long.class,
                SocialTestSupport.TAG_COLLEAGUE);
        assertEquals(2, visibilityAlive == null ? 0 : visibilityAlive.intValue(),
                "P1/P2 可见性行不级联仍 is_del=0");
    }

    /**
     * TC047 删标签后隐私自然失效（explore 规则⑥全语义，复用 TC046 前置动作，本方法自含）。
     * 规则1 删"同事"后：王五看 P1 → 1011（标签条件失效且无其他命中）
     * 规则2 李四看 P1 → code=0（指定好友命中仍可见）
     * 规则3 王五看 P2 → code=0（type=4 排除条件失效 → 变可见）
     */
    @Test
    public void deleteTagPrivacyNaturalExpiry() throws Exception {
        MvcResult deleteResult = deleteTag(SocialTestSupport.TAG_COLLEAGUE, SocialTestSupport.ZHANGSAN);
        assertEquals(0, SocialTestSupport.codeOf(deleteResult), "删除标签应成功");

        MvcResult wangwuP1 = performGet("/api/posts/" + SocialTestSupport.POST_P1
                + "?_appId=it-test&viewerId=" + SocialTestSupport.WANGWU);
        assertEquals(1011, SocialTestSupport.codeOf(wangwuP1), "标签命中失效后王五看 P1 应 1011");

        MvcResult lisiP1 = performGet("/api/posts/" + SocialTestSupport.POST_P1
                + "?_appId=it-test&viewerId=" + SocialTestSupport.LISI);
        assertEquals(0, SocialTestSupport.codeOf(lisiP1), "指定好友命中不受删标签影响，李四看 P1 应可见");

        MvcResult wangwuP2 = performGet("/api/posts/" + SocialTestSupport.POST_P2
                + "?_appId=it-test&viewerId=" + SocialTestSupport.WANGWU);
        assertEquals(0, SocialTestSupport.codeOf(wangwuP2), "排除条件失效后王五看 P2 应变可见");
    }

    /**
     * TC048 tagId 无效：0 / abc → 均 1001（NB1）。
     */
    @Test
    public void deleteTagInvalidId() throws Exception {
        MvcResult r1 = deleteTag(0, SocialTestSupport.ZHANGSAN);
        assertEquals(1001, SocialTestSupport.codeOf(r1), "tagId=0 应 1001");
        MvcResult r2 = performDelete("/api/friend-tags/abc?_appId=it-test&viewerId=" + SocialTestSupport.ZHANGSAN);
        assertEquals(1001, SocialTestSupport.codeOf(r2), "tagId=abc 应 1001");
    }

    /**
     * TC049 标签不存在 → 1004。
     * 口径说明：以 max(id)+100000 替代字面 999999（必然不存在）。
     */
    @Test
    public void deleteTagNotFound() throws Exception {
        long nonExisting = SocialTestSupport.nonExistingId(jdbc, "friend_tag");
        MvcResult result = deleteTag(nonExisting, SocialTestSupport.ZHANGSAN);
        assertEquals(1004, SocialTestSupport.codeOf(result), "标签不存在应 1004");
    }

    /**
     * TC050 非归属人删除（水平越权）→ 1006 且 DB 不变。
     */
    @Test
    public void deleteTagNotOwner() throws Exception {
        MvcResult result = deleteTag(SocialTestSupport.TAG_CLASSMATE, SocialTestSupport.LISI);
        assertEquals(1006, SocialTestSupport.codeOf(result), "非归属人删除应 1006");
        assertEquals(0, isDelOf("friend_tag", SocialTestSupport.TAG_CLASSMATE), "DB 核对：标签未被删");
    }

    /**
     * TC051 缓存失效一致性：预热读后删标签，再读好友列表即时反映（读路径回源新值）。
     * 规则1 预热 GET friends（王五 tagNames 含"朋友"）→ DELETE 朋友标签 → 再 GET 王五 tagNames 不再含"朋友"
     * 口径说明：断言通道为接口回读；tagNames 组装走 DB 直查，deleteTag 的 INCR ftagver
     * 走 afterCommit（回滚事务内不触发，不影响本断言通道）。
     */
    @Test
    public void deleteTagCacheConsistency() throws Exception {
        MvcResult warm = performGet("/api/friends?_appId=it-test&viewerId=" + SocialTestSupport.ZHANGSAN);
        assertEquals(0, SocialTestSupport.codeOf(warm), "预热读应成功");
        assertTrue(SocialTestSupport.tagNamesOf(
                        SocialTestSupport.byUserId(SocialTestSupport.dataListOf(warm)).get(SocialTestSupport.WANGWU))
                .contains("朋友"), "预热时王五 tagNames 含朋友");

        MvcResult deleteResult = deleteTag(SocialTestSupport.TAG_FRIEND, SocialTestSupport.ZHANGSAN);
        assertEquals(0, SocialTestSupport.codeOf(deleteResult), "删除朋友标签应成功");

        MvcResult after = performGet("/api/friends?_appId=it-test&viewerId=" + SocialTestSupport.ZHANGSAN);
        assertEquals(0, SocialTestSupport.codeOf(after), "删后再读应成功");
        assertTrue(!SocialTestSupport.tagNamesOf(
                        SocialTestSupport.byUserId(SocialTestSupport.dataListOf(after)).get(SocialTestSupport.WANGWU))
                        .contains("朋友"), "删标签后 tagNames 即时不含朋友");
    }

    /**
     * TC052 幂等-重复请求（含已删态再删，与"不存在"同分支合并）。
     * 规则1 第 1 次 code=0、第 2 次 code=1004（删标签非幂等，双击第二次报错属预期）
     * 规则2 DB 核对：is_del 终态=1（单次变更）
     */
    @Test
    public void deleteTagRepeatRequest() throws Exception {
        MvcResult first = deleteTag(SocialTestSupport.TAG_CLASSMATE, SocialTestSupport.ZHANGSAN);
        assertEquals(0, SocialTestSupport.codeOf(first), "首次删除应成功");
        MvcResult second = deleteTag(SocialTestSupport.TAG_CLASSMATE, SocialTestSupport.ZHANGSAN);
        assertEquals(1004, SocialTestSupport.codeOf(second), "已删态再删应 1004（与不存在同分支）");
        assertEquals(1, isDelOf("friend_tag", SocialTestSupport.TAG_CLASSMATE), "DB 核对：is_del 单次置 1");
    }

    /**
     * TC053 幂等-并发：并发 2 笔 DELETE 同一有效标签。
     * 规则1 可接受路径：{code=0, 1004} 或 {0,0}（先查后改窗口两笔均过校验、第二笔 UPDATE 0 行）
     * 规则2 终态断言：DB(标签).is_del=1、无数据损坏
     * 口径说明：@Transactional 管不到并发线程，NOT_SUPPORTED 真实提交 + @AfterEach 清理。
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void deleteTagConcurrent() throws Exception {
        java.util.List<Integer> codes = new CopyOnWriteArrayList<>();
        int threads = 2;
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    codes.add(SocialTestSupport.codeOf(deleteTag(SocialTestSupport.TAG_CLASSMATE,
                            SocialTestSupport.ZHANGSAN)));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (Exception e) {
                    log.warn("并发删除执行异常", e);
                }
            });
        }
        ready.await();
        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(60, TimeUnit.SECONDS), "并发请求应全部完成");

        log.info("TC053 并发删除观察：codes={}", codes);
        boolean pathAccepted = (codes.contains(0) && codes.contains(1004))
                || (codes.stream().filter(c -> c == 0).count() == 2L);
        assertTrue(pathAccepted, "可接受路径 {0,1004} 或 {0,0}，实际=" + codes);
        assertEquals(1, isDelOf("friend_tag", SocialTestSupport.TAG_CLASSMATE), "终态：is_del=1");
    }

    /**
     * TC054 幂等-失败重试：1004 失败后换有效标签重试成功。
     */
    @Test
    public void deleteTagFailThenRetry() throws Exception {
        long nonExisting = SocialTestSupport.nonExistingId(jdbc, "friend_tag");
        MvcResult fail = deleteTag(nonExisting, SocialTestSupport.ZHANGSAN);
        assertEquals(1004, SocialTestSupport.codeOf(fail), "删除不存在标签应 1004");
        MvcResult retry = deleteTag(SocialTestSupport.TAG_FRIEND, SocialTestSupport.ZHANGSAN);
        assertEquals(0, SocialTestSupport.codeOf(retry), "重试删除有效标签应成功");
        assertEquals(1, isDelOf("friend_tag", SocialTestSupport.TAG_FRIEND), "DB 核对：重试删除生效");
    }

    private MvcResult deleteTag(long tagId, long viewerId) throws Exception {
        return performDelete("/api/friend-tags/" + tagId + "?_appId=it-test&viewerId=" + viewerId);
    }

    private MvcResult performDelete(String url) throws Exception {
        return mockMvc.perform(delete(url).accept(MediaType.APPLICATION_JSON)).andReturn();
    }

    private MvcResult performGet(String url) throws Exception {
        return mockMvc.perform(get(url).accept(MediaType.APPLICATION_JSON)).andReturn();
    }

    /** 指定表指定主键的 is_del（-1=行不存在） */
    private int isDelOf(String table, long id) {
        Integer isDel = jdbc.queryForObject("SELECT is_del FROM " + table + " WHERE id = ?", Integer.class, id);
        return isDel == null ? -1 : isDel;
    }
}
