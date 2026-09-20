package com.funny.moments.social;

import java.util.List;
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
 * 入口14 并发用例 TC117（幂等-并发删除）：并发 3 笔删 P1 = 作者×2 + 李四×1（越权并发混合）。
 * 做什么：验证越权请求恒 1012（在帖仍存活时先行发出保证存在性校验通过）+ 作者两笔竞态可接受路径 + 终态断言。
 * 口径说明（frozen TC117 + 通用并发口径）：竞态窗口不写死单一响应组合——作者 2 笔可接受 {0,1010} 或 {0,0}
 * （先查后改窗口第二笔 UPDATE 0 行命中仍返回成功），终态断言为主（is_del=1、无数据损坏）；
 * 多线程请求各自独立事务，故不走方法事务回滚，@BeforeEach 已提交基线（9 亿段保留 id）
 * + @AfterEach 物理清理保留段（任意失败路径均执行，同 @Sql AFTER 口径）。
 */
@Slf4j
class PostDeleteConcurrentTests extends TimelineBatch2TestBase {

    /** 已提交基线：前置清障（历史中断遗留）+ 直插保留段基线（子线程可见）。 */
    @BeforeEach
    void seedCommittedBaseline() {
        insertBaselineA();
    }

    /** DB 清理兜底：断言失败/任意异常路径均执行（仅触碰保留段与自造标记）。 */
    @AfterEach
    void scrubCommittedResidue() {
        cleanupCommittedArtifacts();
    }

    /**
     * TC117 幂等-并发删除。
     * 规则1 李四（非作者）笔恒 1012（帖存活时发出，存在性校验通过后卡在归属校验）；
     * 规则2 作者 2 笔可接受路径 {code=0, 1010} 或 {0, 0}；
     * 规则3 终态断言：P1.is_del=1、post_image/post_visibility 行无损坏（行数与 is_del 保持）。
     */
    @Test
    void tc117ConcurrentDeleteAuthorRacePlusOverwrite() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        ConcurrentLinkedQueue<Integer> authorCodes = new ConcurrentLinkedQueue<>();
        try {
            // 规则1：越权笔先行单独发出（帖仍存活 → 存在性通过 → 恒卡归属校验 1012）
            MvcResult overwrite = doDelete("/api/posts/" + P1_PART, "viewerId", String.valueOf(U_LI));
            assertEquals(1012, codeOf(overwrite), "李四删张三帖应恒 1012");
            assertEquals(0, jdbcTemplate.queryForObject(
                    "SELECT is_del FROM `post` WHERE id = ?", Integer.class, P1_PART), "越权失败不改变 is_del");

            // 规则2：作者两笔并发（对齐起跑制造先查后改竞态窗口）
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            for (int i = 0; i < 2; i++) {
                pool.submit(() -> {
                    ready.countDown();
                    try {
                        start.await();
                        MvcResult result = doDelete("/api/posts/" + P1_PART, "viewerId", String.valueOf(U_ZHANG));
                        authorCodes.add(codeOf(result));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } catch (Exception e) {
                        log.error("并发删帖线程异常", e);
                    }
                });
            }
            ready.await();
            start.countDown();
            long awaitStart = System.currentTimeMillis();
            while (authorCodes.size() < 2 && System.currentTimeMillis() - awaitStart < 30_000L) {
                Thread.sleep(50L);
            }
            assertEquals(2, authorCodes.size(), "作者两笔均应返回");
            List<Integer> codes = List.copyOf(authorCodes);
            assertTrue(codes.contains(0), "作者至少一笔成功，实际=" + codes);
            assertTrue(codes.stream().allMatch(code -> code == 0 || code == 1010),
                    "作者笔仅可接受 0/1010，实际=" + codes);

            // 规则3：终态断言
            assertEquals(1, jdbcTemplate.queryForObject(
                    "SELECT is_del FROM `post` WHERE id = ?", Integer.class, P1_PART), "终态 is_del=1（单次变更）");
            assertEquals(1, countOf(jdbcTemplate,
                    "SELECT COUNT(*) FROM `post_image` WHERE post_id=? AND is_del=0", P1_PART),
                    "P1 图片行无损坏");
            assertEquals(1, countOf(jdbcTemplate,
                    "SELECT COUNT(*) FROM `post_visibility_tag` WHERE post_id=? AND is_del=0", P1_PART),
                    "P1 可见性标签行无损坏");
        } finally {
            pool.shutdownNow(); // DB 清理统一由 @AfterEach 兜底（任意失败路径均执行）
        }
    }
}
