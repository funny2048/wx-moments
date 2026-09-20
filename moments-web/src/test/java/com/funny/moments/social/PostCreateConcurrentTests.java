package com.funny.moments.social;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import lombok.extern.slf4j.Slf4j;

/**
 * 入口12 并发用例 TC095（幂等-并发发帖）：并发 5 笔同作者不同内容。
 * 做什么：验证并发发帖各帖独立落库，图片/可见性行 post_id 归属正确无串帖（事务隔离）。
 * 口径说明：@Transactional 管不到多线程（子线程请求各自开独立事务），故不走方法事务回滚——
 * @BeforeEach 直插已提交基线（9 亿段保留 id）+ @AfterEach 物理清理保留段与自造标记行
 * （任意失败路径均执行，写法同样例 MessageConsumeAsyncTests 的 @Sql AFTER）。
 */
@Slf4j
class PostCreateConcurrentTests extends TimelineBatch2TestBase {

    private static final String PATH = "/api/posts";
    private static final String CONTENT_PREFIX = "IT-CONC-POST-";
    private static final int THREADS = 5;

    /** 已提交基线：前置清障（历史中断遗留）+ 直插 9 亿段保留段基线（子线程可见）。 */
    @BeforeEach
    void seedCommittedBaseline() {
        insertBaselineA();
    }

    /** DB 清理兜底：断言失败/任意异常路径均执行，杜绝 committed 残留污染后续用例（仅触碰保留段与自造标记）。 */
    @AfterEach
    void scrubCommittedResidue() {
        cleanupCommittedArtifacts();
    }

    /**
     * TC095 幂等-并发发帖。
     * 规则1 并发 5 笔同作者（张三）不同内容均 code=0；
     * 规则2 DB post +5；每帖图片/可见性行 post_id 归属正确无串帖（join 逐帖核对）。
     * 口径说明：终态断言为主（DB 行数与归属），竞态窗口内响应均为独立事务提交。
     */
    @Test
    void tc095ConcurrentCreateFivePosts() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        ConcurrentLinkedQueue<Integer> codes = new ConcurrentLinkedQueue<>();
        try {
            CountDownLatch ready = new CountDownLatch(THREADS);
            CountDownLatch start = new CountDownLatch(1);
            for (int i = 0; i < THREADS; i++) {
                final int index = i;
                pool.submit(() -> {
                    ready.countDown();
                    try {
                        start.await();
                        String body = String.format(
                                "{\"content\":\"%s%d\",\"imageUrls\":[\"/images/it_conc_%d.png\"],"
                                        + "\"visibilityType\":3,\"visibilityTagIds\":[%d],\"visibilityUserIds\":[%d]}",
                                CONTENT_PREFIX, index, index, TAG_COLLEAGUE, U_LI);
                        MvcResult result = doPostJson(PATH, body, "viewerId", String.valueOf(U_ZHANG));
                        codes.add(codeOf(result));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } catch (Exception e) {
                        log.error("并发发帖线程异常 index={}", index, e);
                    }
                });
            }
            ready.await();
            start.countDown();
            long awaitStart = System.currentTimeMillis();
            while (codes.size() < THREADS && System.currentTimeMillis() - awaitStart < 30_000L) {
                Thread.sleep(50L);
            }
            assertEquals(THREADS, codes.size(), "5 笔并发请求均应返回");
            codes.forEach(code -> assertEquals(0, code, "并发发帖均应成功"));

            // 规则2：终态 DB 核对——5 帖、每帖恰 1 图 1 标签 1 好友行且归属自身
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT p.id, p.content, i.image_url FROM `post` p "
                            + "LEFT JOIN `post_image` i ON i.post_id = p.id AND i.is_del = 0 "
                            + "WHERE p.content LIKE ? AND p.is_del = 0 AND p.user_id = ?", CONTENT_PREFIX + "%", U_ZHANG);
            assertEquals(THREADS, rows.size(), "并发应落 5 帖");
            for (int i = 0; i < THREADS; i++) {
                String expectedContent = CONTENT_PREFIX + i;
                String expectedImage = "/images/it_conc_" + i + ".png";
                boolean matched = rows.stream().anyMatch(row ->
                        expectedContent.equals(row.get("content")) && expectedImage.equals(row.get("image_url")));
                assertTrue(matched, "第 " + i + " 帖图片归属应正确（content=" + expectedContent + "）");
            }
            assertEquals(THREADS, countOf(jdbcTemplate,
                    "SELECT COUNT(*) FROM `post_visibility_tag` t INNER JOIN `post` p ON p.id = t.post_id "
                            + "WHERE p.content LIKE ? AND t.tag_id = ?", CONTENT_PREFIX + "%", TAG_COLLEAGUE),
                    "每帖可见性标签恰 1 行");
            assertEquals(THREADS, countOf(jdbcTemplate,
                    "SELECT COUNT(*) FROM `post_visibility_user` u INNER JOIN `post` p ON p.id = u.post_id "
                            + "WHERE p.content LIKE ? AND u.user_id = ?", CONTENT_PREFIX + "%", U_LI),
                    "每帖可见性好友恰 1 行");
        } finally {
            pool.shutdownNow(); // DB 清理统一由 @AfterEach 兜底（任意失败路径均执行）
        }
    }
}
