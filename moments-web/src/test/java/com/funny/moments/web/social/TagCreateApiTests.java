package com.funny.moments.web.social;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 创建标签接口集成测试（入口6：POST /api/friend-tags，TC029-TC036）。
 * 写法要点：写操作 @Transactional 回滚 + 查库断言（行数/is_del/tag_name 落库核对）；
 * TC034 并发用例 @Transactional 管不到其他线程，走 NOT_SUPPORTED + 基线提交 + @AfterEach 按主键清理。
 *
 * @Author: tester（阶段六）
 * @Date: 2026-09-21
 */
@Slf4j
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
public class TagCreateApiTests {

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
        // TC034（非事务）基线提交后的兜底清理；事务用例回滚后为空操作
        jdbc.update("DELETE FROM friend_tag WHERE user_id = ?", SocialTestSupport.ZHANGSAN);
        SocialTestSupport.cleanupBaseline(jdbc);
        friendIdsCacheManager.evictAll();
        friendTagCacheManager.evictAll();
    }

    /**
     * TC029 主干创建：trim 语义 + 16 字上界 + DB 落库。
     * 规则1 ①"  球友  " → code=0 且 data.tagName=球友（trim 后落库）
     * 规则2 ②恰 16 字标签名 → 正常创建
     * 规则3 DB 核对：friend_tag 各 +1 行 (user_id=张三, is_del=0)
     */
    @Test
    public void createTagTrimAndSixteenCharBound() throws Exception {
        MvcResult r1 = postTag("  球友  ");
        assertEquals(0, SocialTestSupport.codeOf(r1), "带空格标签应创建成功，resp=" + SocialTestSupport.bodyUtf8(r1));
        assertEquals("球友", SocialTestSupport.dataMapOf(r1).get("tagName"), "tagName 应为 trim 后值");
        assertEquals(1, countTag("球友"), "DB 核对：球友落库 1 行");

        String sixteenChars = "一二三四五六七八九十一二三四五六";
        assertEquals(16, sixteenChars.length(), "前置自检：恰 16 字");
        MvcResult r2 = postTag(sixteenChars);
        assertEquals(0, SocialTestSupport.codeOf(r2), "16 字上界应创建成功");
        assertEquals(1, countTag(sixteenChars), "DB 核对：16 字标签落库 1 行");
    }

    /**
     * TC030 tagName 无效合并：null / "" / 纯空格 / trim 后 17 字。
     * 规则1 四笔均 code=1001
     * 规则2 DB 核对：friend_tag 0 新增（失败无残留）
     */
    @Test
    public void createTagInvalidName() throws Exception {
        int before = countUserTags();
        String[] invalidBodies = {"{\"tagName\":null}", "{\"tagName\":\"\"}", "{\"tagName\":\"   \"}",
                "{\"tagName\":\"01234567890123456\"}"};
        for (String body : invalidBodies) {
            MvcResult result = postTagRaw(body);
            assertEquals(1001, SocialTestSupport.codeOf(result),
                    "无效 tagName 应 1001，body=" + body + "，resp=" + SocialTestSupport.bodyUtf8(result));
        }
        assertEquals(before, countUserTags(), "DB 核对：无效创建不落任何行");
    }

    /**
     * TC031 同名重复：张三已有"同学" → 1005。
     * 规则1 code=1005；DB 核对 is_del=0 范围 0 新增
     */
    @Test
    public void createTagDuplicatedName() throws Exception {
        int before = countTag("同学");
        MvcResult result = postTag("同学");
        assertEquals(1005, SocialTestSupport.codeOf(result), "同名标签应 1005");
        assertEquals(before, countTag("同学"), "DB 核对：查重拦截不新增");
    }

    /**
     * TC032 软删后同名重建：软删不占查重语义（前置自含：创建→接口删除→重建）。
     * 规则1 重建 code=0
     * 规则2 DB 核对：(张三,'球友') 合计 2 行——旧行 is_del=1、新行 is_del=0（查重仅 is_del=0 范围）
     */
    @Test
    public void createTagAfterSoftDeleted() throws Exception {
        MvcResult create = postTag("球友");
        assertEquals(0, SocialTestSupport.codeOf(create), "首次创建应成功");
        long tagId = SocialTestSupport.toLong(SocialTestSupport.dataMapOf(create).get("tagId"));
        MvcResult delete = mockMvc.perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/friend-tags/" + tagId + "?_appId=it-test&viewerId=" + SocialTestSupport.ZHANGSAN)
                        .accept(MediaType.APPLICATION_JSON)).andReturn();
        assertEquals(0, SocialTestSupport.codeOf(delete), "接口删除应成功");

        MvcResult recreate = postTag("球友");
        assertEquals(0, SocialTestSupport.codeOf(recreate), "软删同名不占查重，重建应成功");
        assertEquals(1, countTagByDel("球友", 1), "DB 核对：旧行 is_del=1 保留");
        assertEquals(1, countTagByDel("球友", 0), "DB 核对：新行 is_del=0 恰 1 行");
    }

    /**
     * TC033 幂等-重复请求：同一 tagName 连续提交 2 次（查重拦截语义幂等）。
     * 规则1 第 1 次 code=0、第 2 次 code=1005
     * 规则2 DB 核对：is_del=0 的 (张三,'队友') 恰 1 行
     */
    @Test
    public void createTagRepeatRequest() throws Exception {
        MvcResult first = postTag("队友");
        assertEquals(0, SocialTestSupport.codeOf(first), "首次创建应成功");
        MvcResult second = postTag("队友");
        assertEquals(1005, SocialTestSupport.codeOf(second), "重复创建应 1005 拦截");
        assertEquals(1, countTagByDel("队友", 0), "DB 核对：同名有效标签恰 1 行");
    }

    /**
     * TC034 幂等-并发：并发 2 笔同名创建（design §5.6 规则 5 留痕窗口）。
     * 规则1 可接受路径：{code=0, 1005} 或极端窗口 {0,0}（查重与 INSERT 间无锁）
     * 规则2 终态断言：is_del=0 同名行 ≤2 且列表/好友页接口正常返回（展示侧口径不重复破坏结构）
     * 口径说明：@Transactional 管不到并发线程，本方法 NOT_SUPPORTED 走真实提交，@AfterEach 清理。
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void createTagConcurrent() throws Exception {
        List<Integer> codes = new CopyOnWriteArrayList<>();
        runConcurrent(2, () -> codes.add(SocialTestSupport.codeOf(postTag("队友"))));

        long validRows = countTagByDel("队友", 0);
        log.info("TC034 并发创建观察：codes={}, 同名 is_del=0 行数={}", codes, validRows);
        assertTrue(validRows >= 1 && validRows <= 2, "终态：同名有效行应 ∈[1,2]，实际=" + validRows);
        boolean pathAccepted = (codes.contains(0) && codes.contains(1005))
                || (codes.stream().filter(c -> c == 0).count() == 2L);
        assertTrue(pathAccepted, "可接受路径 {0,1005} 或 {0,0}，实际=" + codes);

        MvcResult tagList = mockMvc.perform(get("/api/friend-tags?_appId=it-test&viewerId="
                        + SocialTestSupport.ZHANGSAN).accept(MediaType.APPLICATION_JSON)).andReturn();
        assertEquals(0, SocialTestSupport.codeOf(tagList), "并发创建后列表接口应正常");
        MvcResult friendList = mockMvc.perform(get("/api/friends?_appId=it-test&viewerId="
                        + SocialTestSupport.ZHANGSAN).accept(MediaType.APPLICATION_JSON)).andReturn();
        assertEquals(0, SocialTestSupport.codeOf(friendList), "并发创建后好友页接口应正常");
    }

    /**
     * TC035 幂等-失败重试：失败可重试且结果确定。
     * 规则1 ①同名"同学" 1005 → ②改"老同学" code=0 → ③再"同学" 仍 1005，无残留
     */
    @Test
    public void createTagFailThenRetry() throws Exception {
        MvcResult fail = postTag("同学");
        assertEquals(1005, SocialTestSupport.codeOf(fail), "同名应 1005");
        MvcResult retry = postTag("老同学");
        assertEquals(0, SocialTestSupport.codeOf(retry), "改重试应成功");
        MvcResult again = postTag("同学");
        assertEquals(1005, SocialTestSupport.codeOf(again), "再次同名仍应 1005（结果确定）");
        assertEquals(1, countTagByDel("老同学", 0), "DB 核对：老同学恰 1 行");
        assertEquals(1, countTagByDel("同学", 0), "DB 核对：同学仍恰 1 行");
    }

    /**
     * TC036 viewer 缺失 → 1003 且 DB 0 行（横切引用）。
     */
    @Test
    public void createTagViewerMissing() throws Exception {
        int before = countUserTags();
        MvcResult result = postTagRawTo("/api/friend-tags?_appId=it-test", "{\"tagName\":\"x\"}");
        assertEquals(1003, SocialTestSupport.codeOf(result), "viewer 缺失应 1003");
        assertEquals(before, countUserTags(), "DB 核对：viewer 缺失不落库");
    }

    /** 以张三视角创建标签 */
    private MvcResult postTag(String tagName) throws Exception {
        return postTagRawTo("/api/friend-tags?_appId=it-test&viewerId=" + SocialTestSupport.ZHANGSAN,
                "{\"tagName\":\"" + tagName + "\"}");
    }

    private MvcResult postTagRaw(String body) throws Exception {
        return postTagRawTo("/api/friend-tags?_appId=it-test&viewerId=" + SocialTestSupport.ZHANGSAN, body);
    }

    private MvcResult postTagRawTo(String url, String body) throws Exception {
        return mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON)
                .content(body.getBytes(StandardCharsets.UTF_8)).accept(MediaType.APPLICATION_JSON)).andReturn();
    }

    /** 并发执行 N 笔请求（对齐起跑，参照 OrderCreateTriggerTests 并发写法）；动作允许抛受检异常（单笔失败不中断对齐） */
    private void runConcurrent(int threads, ConcurrentAction action) throws Exception {
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    action.run();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (Exception e) {
                    log.warn("并发请求执行异常", e);
                }
            });
        }
        ready.await();
        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(60, java.util.concurrent.TimeUnit.SECONDS), "并发请求应全部完成");
    }

    /** (张三, tagName, is_del) 行数 */
    private int countTagByDel(String tagName, int isDel) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM friend_tag WHERE user_id = ? AND tag_name = ? AND is_del = ?",
                Long.class, SocialTestSupport.ZHANGSAN, tagName, isDel);
        return count == null ? 0 : count.intValue();
    }

    /** 可抛受检异常的并发动作（MvcResult 取回链路声明 throws Exception） */
    @FunctionalInterface
    interface ConcurrentAction {

        void run() throws Exception;
    }

    private int countTag(String tagName) {
        return countTagByDel(tagName, 0);
    }

    /** 张三有效标签总数 */
    private int countUserTags() {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM friend_tag WHERE user_id = ? AND is_del = 0",
                Long.class, SocialTestSupport.ZHANGSAN);
        return count == null ? 0 : count.intValue();
    }
}
