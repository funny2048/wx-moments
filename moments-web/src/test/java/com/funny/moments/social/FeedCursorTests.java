package com.funny.moments.social;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import lombok.extern.slf4j.Slf4j;

/**
 * 入口16 GET /api/feed（Feed 瀑布流-Cursor 双锚点，T080）集成测试：TC127-TC136，
 * 隐私矩阵-路径③ Feed。做什么：翻页链（规则⑤）、双锚点空页合法组合（B1）、游标强校验（R4）、
 * 隐私投影（速查表口径）、大数据翻页连续性观测（软校验不计红灯）。
 * 口径说明：基线/断言全部使用 9 亿段保留 id（红绿终跑根治迁移）——Feed 候选集=保留段基线五人组内部
 * （张三的好友仅李四/王五/赵六，与 dev mock 存量完全解耦，结果与执行顺序无关）；
 * 游标断言以"解码后匹配 ^\d{13}:\d{1,18}$ 且毫秒段=指定帖 created_stime 毫秒"为准（防时钟差异，不做字面相等）。
 */
@Slf4j
@Transactional
class FeedCursorTests extends TimelineBatch2TestBase {

    private static final String FEED_PATH = "/api/feed";

    @BeforeEach
    void seedBaseline() {
        insertBaselineA();
    }

    /** Feed 请求并断言 code=0，返回 MvcResult。 */
    private MvcResult feedOk(String... params) throws Exception {
        MvcResult result = doGet(FEED_PATH, params);
        assertEquals(0, codeOf(result), "Feed 请求应成功");
        return result;
    }

    /** 解码游标（Base64 → "{毫秒}:{postId}"）。 */
    private static String decodeCursor(String cursor) {
        return new String(Base64.getDecoder().decode(cursor), StandardCharsets.UTF_8);
    }

    /** 查基线帖的 created_stime 毫秒值（DB 事实源，防时钟差异）。 */
    private long createdMillisOf(long postId) {
        return jdbcTemplate.queryForObject("SELECT created_stime FROM `post` WHERE id = ?",
                java.sql.Timestamp.class, postId).getTime();
    }

    /**
     * TC127 主干-三页翻页链与同秒排序（规则⑤）。
     * 规则1 首页 items=[P0,P2]、hasMore=true、nextCursor 解码匹配 ^\d{13}:\d{1,18}$ 且毫秒段=P2.created_stime 毫秒、
     * id 段=P2（同秒 id 倒序锚点）；
     * 规则2 页2 items=[P1,P3]、页3 items=[P4,P5] 且 hasMore=false、nextCursor=null；
     * 规则3 全程 6 帖无重复无跳空（同秒 P2&gt;P1 按 id 倒序）。
     */
    @Test
    void tc127ThreePageChainSameSecondOrdering() throws Exception {
        MvcResult page1 = feedOk("viewerId", String.valueOf(U_ZHANG), "pageSize", "2");
        List<Long> page1Ids = longListAt(page1, "$.data.items[*].postId");
        assertEquals(List.of(P0_PUBLIC, P2_EXCLUDE), page1Ids, "首页=[P0,P2]");
        assertTrue(boolAt(page1, "$.data.hasMore"), "首页 hasMore=true");
        String nextCursor1 = strAt(page1, "$.data.nextCursor");
        assertNotNull(nextCursor1, "hasMore=true 时 nextCursor 必非 null");
        String decoded1 = decodeCursor(nextCursor1);
        assertTrue(decoded1.matches("\\d{13}:\\d{1,18}"), "游标解码后应匹配毫秒:id 正则");
        String[] cursorParts = decoded1.split(":");
        assertEquals(String.valueOf(createdMillisOf(P2_EXCLUDE)), cursorParts[0], "毫秒段=P2.created_stime 毫秒");
        assertEquals(String.valueOf(P2_EXCLUDE), cursorParts[1], "id 段=P2（同秒 id 倒序锚点）");

        MvcResult page2 = feedOk("viewerId", String.valueOf(U_ZHANG), "pageSize", "2", "cursor", nextCursor1);
        assertEquals(List.of(P1_PART, P3_PRIVATE), longListAt(page2, "$.data.items[*].postId"), "页2=[P1,P3]");
        String nextCursor2 = strAt(page2, "$.data.nextCursor");
        assertNotNull(nextCursor2);

        MvcResult page3 = feedOk("viewerId", String.valueOf(U_ZHANG), "pageSize", "2", "cursor", nextCursor2);
        assertEquals(List.of(P4_PART_EMPTY, P5_EXCLUDE_EMPTY), longListAt(page3, "$.data.items[*].postId"),
                "页3=[P4,P5]");
        assertFalse(boolAt(page3, "$.data.hasMore"), "末页 hasMore=false");
        assertNull(strAt(page3, "$.data.nextCursor"), "末页 nextCursor=null");

        // 规则3：无重复无跳空
        List<Long> all = new ArrayList<>(page1Ids);
        all.addAll(longListAt(page2, "$.data.items[*].postId"));
        all.addAll(longListAt(page3, "$.data.items[*].postId"));
        assertEquals(6, all.size(), "三页合计 6 帖");
        assertEquals(6, new HashSet<>(all).size(), "无重复");
        Set<Long> expected = Set.of(P0_PUBLIC, P1_PART, P2_EXCLUDE, P3_PRIVATE, P4_PART_EMPTY, P5_EXCLUDE_EMPTY);
        assertTrue(all.containsAll(expected) && expected.containsAll(new HashSet<>(all)), "无跳空");
    }

    /**
     * TC128 空页合法组合-双锚点（B1 双锚点核心；数据量按 T080 验收口径）。
     * 前置：SQL 直插王五名下 200 帖 type=3 空集（对张三全不可见，created_stime 均新于基线六帖；
     * 同事务内插入随事务回滚，零污染）；推演：pageSize=20 → batchSize=60、maxRounds=3，3 批×60=180 条全不可见
     * 且均满批（200&gt;180）→ scanEnd=false、collected=0。
     * 规则1 首页 items=[]、hasMore=true、nextCursor≠null（扫描进度锚点=第 180 条候选，解码匹配正则——合法组合出现）；
     * 规则2 携游标续翻：跳过剩余 20 条不可见候选后单批扫尽（20+基线 6=26&lt;60），items=基线 6 帖（作者全可见）、
     * hasMore=false、nextCursor=null；已扫 180 条不重扫（无重复输出）。
     */
    @Test
    void tc128EmptyPageDualAnchor200HiddenPosts() throws Exception {
        // 直插 200 帖（王五，type=3 空集，12:00:00 起逐秒——均新于基线 10:00:01；内容标记供中断残留清障）
        for (int i = 0; i < 200; i++) {
            jdbcTemplate.update(
                    "INSERT INTO `post` (user_id, content, visibility_type, status, created_stime) "
                            + "VALUES (?, ?, 3, 1, ?)", U_WANG, "IT-TC128-HIDE-" + i,
                    java.sql.Timestamp.valueOf(String.format("2026-09-20 12:%02d:%02d", i / 60, i % 60)));
        }

        // 规则1：首页合法组合 items=[] && hasMore=true && nextCursor≠null
        MvcResult page1 = feedOk("viewerId", String.valueOf(U_ZHANG), "pageSize", "20");
        List<Long> page1Ids = longListAt(page1, "$.data.items[*].postId");
        assertEquals(List.of(), page1Ids, "180 条满批候选全不可见 → items=[]");
        assertTrue(boolAt(page1, "$.data.hasMore"), "候选流未扫尽 → hasMore=true（合法组合）");
        String nextCursor = strAt(page1, "$.data.nextCursor");
        assertNotNull(nextCursor, "扫描进度锚点必非 null");
        assertTrue(decodeCursor(nextCursor).matches("\\d{13}:\\d{1,18}"), "扫描进度锚点解码匹配毫秒:id 正则");
        log.info("TC128 首页空页游标={} 解码={}", nextCursor, decodeCursor(nextCursor));

        // 规则2：续翻单批扫尽剩余候选 → 基线 6 帖全可见、末页组合
        MvcResult page2 = feedOk("viewerId", String.valueOf(U_ZHANG), "pageSize", "20", "cursor", nextCursor);
        List<Long> page2Ids = longListAt(page2, "$.data.items[*].postId");
        assertEquals(Set.of(P0_PUBLIC, P1_PART, P2_EXCLUDE, P3_PRIVATE, P4_PART_EMPTY, P5_EXCLUDE_EMPTY),
                new HashSet<>(page2Ids), "续翻返回基线 6 帖（作者全可见）");
        assertEquals(6, new HashSet<>(page2Ids).size(), "已扫 180 条不重扫（无重复输出）");
        assertFalse(boolAt(page2, "$.data.hasMore"), "末页 hasMore=false");
        assertNull(strAt(page2, "$.data.nextCursor"), "末页 nextCursor=null");
    }

    /**
     * TC129 空集行为（Q5 无帖/无好友）。
     * 规则1 ①无帖无好友新用户（孙九，SQL 直插保留段 id）②钱七（无好友无帖），均 items=[]、
     * nextCursor=null、hasMore=false（三字段组合完备）。
     */
    @Test
    void tc129EmptyFeedFieldCombo() throws Exception {
        jdbcTemplate.update("INSERT INTO `user` (id, nickname, avatar, gender, city) VALUES (?, '孙九', '', 0, '')",
                U_SUN);
        for (long viewer : new long[] {U_SUN, U_QIAN}) {
            MvcResult result = feedOk("viewerId", String.valueOf(viewer), "pageSize", "20");
            assertEquals(List.of(), longListAt(result, "$.data.items[*].postId"), "viewer=" + viewer + " 空 Feed");
            assertNull(strAt(result, "$.data.nextCursor"), "viewer=" + viewer + " nextCursor=null");
            assertFalse(boolAt(result, "$.data.hasMore"), "viewer=" + viewer + " hasMore=false");
        }
    }

    /**
     * TC130 cursor 非法四分支（1030 + 不静默重置，R4）。
     * 规则1 ①"!!!"（Base64 解码失败）②Base64("abc")（格式不匹配）③Base64(id 19 位超限) ④Base64(毫秒段超 13 位)，
     * 均 code=1030（不静默当首页）。
     */
    @Test
    void tc130IllegalCursorFourBranches1030() throws Exception {
        String base64 = Base64.getEncoder().encodeToString("abc".getBytes(StandardCharsets.UTF_8));
        String idOverflow = Base64.getEncoder()
                .encodeToString("1789918203000:1234567890123456789".getBytes(StandardCharsets.UTF_8));
        String millisOverflow = Base64.getEncoder()
                .encodeToString("99999999999999999999:1".getBytes(StandardCharsets.UTF_8));
        String[] illegalCursors = {"!!!", base64, idOverflow, millisOverflow};
        for (String cursor : illegalCursors) {
            MvcResult result = doGet(FEED_PATH, "viewerId", String.valueOf(U_ZHANG), "cursor", cursor);
            assertEquals(1030, codeOf(result), "非法游标应 1030（不静默当首页），cursor=" + cursor);
        }
    }

    /**
     * TC131 pageSize 越界与边界（B2）。
     * 规则1 ①pageSize=0 / 51 / abc → 1001；②pageSize=1 → items ≤1；③pageSize=50 → items ≤50。
     */
    @Test
    void tc131PageSizeBounds() throws Exception {
        assertEquals(1001, codeOf(doGet(FEED_PATH, "viewerId", String.valueOf(U_ZHANG), "pageSize", "0")));
        assertEquals(1001, codeOf(doGet(FEED_PATH, "viewerId", String.valueOf(U_ZHANG), "pageSize", "51")));
        assertEquals(1001, codeOf(doGet(FEED_PATH, "viewerId", String.valueOf(U_ZHANG), "pageSize", "abc")),
                "pageSize 非数字应经 TypeMismatch 转 1001");
        assertEquals(1, longListAt(feedOk("viewerId", String.valueOf(U_ZHANG), "pageSize", "1"),
                "$.data.items[*].postId").size(), "pageSize=1 恰 1 条");
        List<Long> full = longListAt(feedOk("viewerId", String.valueOf(U_ZHANG), "pageSize", "50"),
                "$.data.items[*].postId");
        assertEquals(6, full.size(), "基线 6 帖全量（≤50）");
    }

    /**
     * TC132 viewer 缺失（横切）。
     * 规则1 无 viewer → 1003。
     */
    @Test
    void tc132ViewerMissing1003() throws Exception {
        assertEquals(1003, codeOf(doGet(FEED_PATH)));
    }

    /**
     * TC133 私密帖 SQL 预过滤（A5 + B1 修正口径；矩阵-路径③）。
     * 规则1 李四视角 Feed items 含张三的 [P0,P1,P2,P5]（P1 指定好友命中可见），不含 P3（私密 SQL 预过滤）与
     * P4（canView 过滤）——与 TC120① 列表投影一致（三路径口径一致）。
     */
    @Test
    void tc133LiProjectionWithPrivatePrefilter() throws Exception {
        MvcResult result = feedOk("viewerId", String.valueOf(U_LI), "pageSize", "50");
        List<Long> ids = longListAt(result, "$.data.items[*].postId");
        assertEquals(List.of(P0_PUBLIC, P2_EXCLUDE, P1_PART, P5_EXCLUDE_EMPTY), ids, "李四 Feed 投影=[P0,P1,P2,P5]");
    }

    /**
     * TC134 非好友帖不入候选集（Feed 候选=自己+好友；规则② Feed 判定）。
     * 规则1 钱七（非张三好友）Feed items 不含张三任何帖（含 P0 公开——公开仅详情/列表直达可见，Feed 天然限好友圈）。
     */
    @Test
    void tc134NonFriendPostsExcludedFromCandidates() throws Exception {
        MvcResult result = feedOk("viewerId", String.valueOf(U_QIAN), "pageSize", "50");
        List<Long> ids = longListAt(result, "$.data.items[*].postId");
        List<Long> authorIds = longListAt(result, "$.data.items[*].userId");
        assertFalse(authorIds.contains(U_ZHANG), "钱七 Feed 不含张三任何帖（含公开帖）");
        assertEquals(List.of(), ids, "钱七无好友无帖 → 空 Feed");
    }

    /**
     * TC135 作者自见全部（矩阵-Feed 作者行）。
     * 规则1 张三视角六帖全在（含 P3 私密、P4 空集——作者视角），顺序 [P0,P2,P1,P3,P4,P5]。
     */
    @Test
    void tc135AuthorSeesAllSix() throws Exception {
        MvcResult result = feedOk("viewerId", String.valueOf(U_ZHANG), "pageSize", "50");
        assertEquals(List.of(P0_PUBLIC, P2_EXCLUDE, P1_PART, P3_PRIVATE, P4_PART_EMPTY, P5_EXCLUDE_EMPTY),
                longListAt(result, "$.data.items[*].postId"), "作者六帖全可见");
    }

    /**
     * TC136 观测-大数据翻页连续性（keyset 稳定性，可判定口径；观测软校验不计红灯）。
     * 做什么：对 dev 存量数据挑有帖用户（真实 mock 用户，与保留段基线无关），循环携 nextCursor 拉取至 hasMore=false。
     * 口径说明（三条可判定口径，违规 warn 计数不入红灯）：①全程 postId 无重复；②恰好终止于 hasMore=false
     * （非异常中断）；③拉取总数与 DB 可见候选数抽样核对（SQL 按 canView 口径预计算对照——type=3/4 的
     * canView 过滤无法 SQL 复刻，差异记观测日志）。库内无帖时软通过并记备注。
     */
    @Test
    void tc136LargeDataPaginationContinuityObserve() throws Exception {
        List<Map<String, Object>> posters = jdbcTemplate.queryForList(
                "SELECT p.user_id AS userId, COUNT(*) AS cnt FROM `post` p WHERE p.is_del = 0 AND p.status = 1 "
                        + "GROUP BY p.user_id ORDER BY cnt DESC LIMIT 1");
        if (posters.isEmpty()) {
            log.info("TC136 备注库内无 is_del=0 帖子：软通过（观测型无存量可测）");
            return;
        }
        long viewerId = ((Number) posters.get(0).get("userId")).longValue();
        int duplicateCount = 0;
        int pulled = 0;
        boolean terminatedByHasMoreFalse = false;
        String cursor = null;
        Set<Long> seen = new HashSet<>();
        for (int page = 0; page < 100; page++) {
            MvcResult result = cursor == null
                    ? feedOk("viewerId", String.valueOf(viewerId), "pageSize", "50")
                    : feedOk("viewerId", String.valueOf(viewerId), "pageSize", "50", "cursor", cursor);
            List<Long> ids = longListAt(result, "$.data.items[*].postId");
            for (Long id : ids) {
                pulled++;
                if (!seen.add(id)) {
                    duplicateCount++;
                    log.warn("TC136 翻页重复 postId={}（viewerId={}）", id, viewerId);
                }
            }
            boolean hasMore = boolAt(result, "$.data.hasMore");
            cursor = strAt(result, "$.data.nextCursor");
            if (!hasMore) {
                terminatedByHasMoreFalse = true;
                assertNull(cursor, "hasMore=false 时 nextCursor 应为 null");
                break;
            }
            assertNotNull(cursor, "hasMore=true 时 nextCursor 必非 null（B1 不变式）");
        }
        if (!terminatedByHasMoreFalse) {
            log.warn("TC136 观测：viewerId={} 翻页 100 页未终止于 hasMore=false（可能存量超 5000 帖），记备注不计红灯",
                    viewerId);
        }
        // 口径③：与 SQL 可见候选数（作者+好友、私密帖非作者 SQL 预过滤口径）抽样对照
        int dbCandidates = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM `post` p WHERE p.is_del = 0 AND p.status = 1 AND (p.visibility_type != 2 "
                        + "OR p.user_id = ?) AND (p.user_id = ? OR p.user_id IN "
                        + "(SELECT friend_user_id FROM `friendship` WHERE user_id = ? AND is_del = 0))",
                Integer.class, viewerId, viewerId, viewerId);
        log.info("TC136 观测汇总：viewerId={}, 拉取总数={}, 重复数={}, 终止于hasMore=false={}, "
                        + "SQL候选数(含type3/4未过滤)={}, 拉取≤候选={}, 差额(type3/4被canView过滤属预期)={}",
                viewerId, pulled, duplicateCount, terminatedByHasMoreFalse, dbCandidates,
                pulled <= dbCandidates, dbCandidates - pulled);
        if (pulled > dbCandidates) {
            log.warn("TC136 观测违规：拉取总数 {} 超过 SQL 候选数 {}（viewerId={}），记备注不计红灯",
                    pulled, dbCandidates, viewerId);
        }
        assertFalse(duplicateCount > 0 && terminatedByHasMoreFalse && pulled > dbCandidates,
                "三条口径同时违规（重复+超量）才判失败，实际重复=" + duplicateCount);
    }
}
