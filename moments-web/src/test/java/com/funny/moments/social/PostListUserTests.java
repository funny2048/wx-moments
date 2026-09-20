package com.funny.moments.social;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import lombok.extern.slf4j.Slf4j;

/**
 * 入口15 GET /api/posts?userId=（指定用户帖子列表-limit 结果上限，T070）触发型集成测试：TC119-TC126，
 * 隐私矩阵-路径②列表过滤。做什么：按冻结用例断言排序（C8 同秒 id 倒序）、隐私投影（速查表口径）、limit 语义与参数校验。
 * 口径说明：基线/断言全部使用 9 亿段保留 id（红绿终跑根治迁移，与 dev mock 存量解耦）；
 * 隐私投影=李四[P0,P1,P2,P5]、王五[P0,P1,P5]、赵六[P0,P5]、钱七[P0,P2,P5]（与详情/Feed 三路径一致）；
 * tc119/tc122 断言前按基线六帖集合过滤（诊断防线，相对顺序语义不变）。
 */
@Slf4j
@Transactional
class PostListUserTests extends TimelineBatch2TestBase {

    @BeforeEach
    void seedBaseline() {
        insertBaselineA();
    }

    private List<Long> listIds(String... params) throws Exception {
        MvcResult result = doGet("/api/posts", params);
        assertEquals(0, codeOf(result), "列表请求应成功");
        return longListAt(result, "$.data[*].postId");
    }

    /**
     * TC119 主干-作者全量与排序（createTime DESC + 同秒 id DESC，C8）。
     * 规则1 张三视角看自己全部 6 帖，顺序 [P0, P2, P1, P3, P4, P5]
     * （10:00:01 > 同秒 P2>P1 > 09:59:59 > 09:59:58 > 09:59:57）；
     * 规则2 每条 userId 恒=张三。
     */
    @Test
    void tc119AuthorFullListOrdering() throws Exception {
        MvcResult result = doGet("/api/posts", "userId", String.valueOf(U_ZHANG),
                "viewerId", String.valueOf(U_ZHANG));
        assertEquals(0, codeOf(result));
        List<Long> ids = filterBaselinePosts(longListAt(result, "$.data[*].postId"));
        assertEquals(List.of(P0_PUBLIC, P2_EXCLUDE, P1_PART, P3_PRIVATE, P4_PART_EMPTY, P5_EXCLUDE_EMPTY), ids,
                "createTime DESC + 同秒 id DESC（基线帖相对顺序）");
        List<Long> authorIds = longListAt(result, "$.data[*].userId");
        assertTrue(authorIds.stream().allMatch(id -> id == U_ZHANG), "列表元素 userId 恒等于入参 userId");
    }

    /**
     * TC120 隐私投影-命中视角（规则①② 列表路径，B1 修正口径）。
     * 规则1 李四=[P0,P1,P2,P5]（P1 指定好友命中可见；P3 私密/P4 空集被滤）；
     * 规则2 王五=[P0,P1,P5]（P1 标签命中可见；P2 同事命中被排除）。
     */
    @Test
    void tc120PrivacyProjectionHitViewers() throws Exception {
        List<Long> liIds = listIds("userId", String.valueOf(U_ZHANG), "viewerId", String.valueOf(U_LI));
        assertEquals(List.of(P0_PUBLIC, P2_EXCLUDE, P1_PART, P5_EXCLUDE_EMPTY), liIds, "李四投影=[P0,P1,P2,P5]");

        List<Long> wangIds = listIds("userId", String.valueOf(U_ZHANG), "viewerId", String.valueOf(U_WANG));
        assertEquals(List.of(P0_PUBLIC, P1_PART, P5_EXCLUDE_EMPTY), wangIds, "王五投影=[P0,P1,P5]");
    }

    /**
     * TC121 隐私投影-无命中与非好友（规则②④ 列表路径，B1 修正口径）。
     * 规则1 赵六=[P0,P5]（P2 指定排除名单命中 → 不可见；P1 无命中不可见）；
     * 规则2 钱七（非好友）=[P0,P2,P5]（无命中投影：公开+type4 无命中+type4 空集）。
     */
    @Test
    void tc121PrivacyProjectionNoHitAndNonFriend() throws Exception {
        List<Long> zhaoIds = listIds("userId", String.valueOf(U_ZHANG), "viewerId", String.valueOf(U_ZHAO));
        assertEquals(List.of(P0_PUBLIC, P5_EXCLUDE_EMPTY), zhaoIds, "赵六投影=[P0,P5]，不含 P2");

        List<Long> qianIds = listIds("userId", String.valueOf(U_ZHANG), "viewerId", String.valueOf(U_QIAN));
        assertEquals(List.of(P0_PUBLIC, P2_EXCLUDE, P5_EXCLUDE_EMPTY), qianIds, "钱七直达投影=[P0,P2,P5]");
    }

    /**
     * TC122 limit 截断与边界（结果上限语义）。
     * 规则1 ①limit=2 返回过滤后前 2 条；②limit=1 返回 1 条；③limit=500 全量（≤500）。
     * 口径说明：断言前按基线六帖集合过滤（诊断防线；保留段隔离后正常应为无过滤命中）。
     */
    @Test
    void tc122LimitTruncationAndBounds() throws Exception {
        assertEquals(List.of(P0_PUBLIC, P2_EXCLUDE),
                filterBaselinePosts(listIds("userId", String.valueOf(U_ZHANG), "viewerId", String.valueOf(U_ZHANG),
                        "limit", "2")),
                "limit=2 取过滤后前 2 条（基线帖相对顺序）");
        assertEquals(List.of(P0_PUBLIC),
                filterBaselinePosts(listIds("userId", String.valueOf(U_ZHANG), "viewerId", String.valueOf(U_ZHANG),
                        "limit", "1")),
                "limit=1 取 1 条（基线帖相对顺序）");
        assertEquals(6,
                filterBaselinePosts(listIds("userId", String.valueOf(U_ZHANG), "viewerId", String.valueOf(U_ZHANG),
                        "limit", "500")).size(),
                "limit=500 全量 6 帖");
    }

    /**
     * TC123 放大补偿-截断点后旧公开帖不漏（建议5 定稿）。
     * 前置：张三 10 帖——前 9 帖对李四全不可见（type=3 空集，时间新于基线），第 10 帖旧公开帖（时间旧于基线）。
     * 规则1 userId=张三&viewerId=李四&limit=5 → data 含第 10 帖公开帖（limit×3×3 批扫描补偿，先截断后过滤不漏帖）。
     */
    @Test
    void tc123ScanCompensationNotMissOldPublicPost() throws Exception {
        // 造数：9 帖 type=3 空集（11:00 起逐秒，新于基线全部）+ 第 10 帖 type=1 旧公开帖（08:00，旧于基线全部）
        // 使用保留段显式 id（900002200-900002209），随事务回滚
        for (int i = 0; i < 9; i++) {
            jdbcTemplate.update(
                    "INSERT INTO `post` (id, user_id, content, visibility_type, status, created_stime) "
                            + "VALUES (?,?,?,3,1,?)", TC123_POST_BASE + i, U_ZHANG, "IT-TC123-HIDE-" + i,
                    java.sql.Timestamp.valueOf(String.format("2026-09-20 11:00:%02d", i)));
        }
        jdbcTemplate.update("INSERT INTO `post` (id, user_id, content, visibility_type, status, created_stime) "
                + "VALUES (?,?, 'IT-TC123-OLD-PUBLIC', 1, 1, '2026-09-20 08:00:00')",
                TC123_POST_BASE + 9, U_ZHANG);

        List<Long> ids = listIds("userId", String.valueOf(U_ZHANG), "viewerId", String.valueOf(U_LI), "limit", "5");
        assertEquals(5, ids.size(), "limit=5 应返回 5 条");
        assertTrue(ids.contains(TC123_POST_BASE + 9), "截断点之后的旧公开帖不漏（放大补偿），实际=" + ids);
    }

    /**
     * TC124 参数无效合并（1001×6 合并）。
     * 规则1 ①userId 缺失 ②userId=0 / userId=abc ③limit=0 / limit=501 / limit=abc，均 code=1001。
     */
    @Test
    void tc124InvalidParamsMerged1001() throws Exception {
        assertEquals(1001, codeOf(doGet("/api/posts", "viewerId", String.valueOf(U_ZHANG))), "userId 缺失应 1001");
        assertEquals(1001, codeOf(doGet("/api/posts", "userId", "0", "viewerId", String.valueOf(U_ZHANG))),
                "userId=0 应 1001");
        assertEquals(1001, codeOf(doGet("/api/posts", "userId", "abc", "viewerId", String.valueOf(U_ZHANG))),
                "userId 非数字应经 TypeMismatch 转 1001");
        assertEquals(1001, codeOf(doGet("/api/posts", "userId", String.valueOf(U_ZHANG),
                "viewerId", String.valueOf(U_ZHANG), "limit", "0")), "limit=0 应 1001");
        assertEquals(1001, codeOf(doGet("/api/posts", "userId", String.valueOf(U_ZHANG),
                "viewerId", String.valueOf(U_ZHANG), "limit", "501")), "limit=501 应 1001");
        assertEquals(1001, codeOf(doGet("/api/posts", "userId", String.valueOf(U_ZHANG),
                "viewerId", String.valueOf(U_ZHANG), "limit", "abc")), "limit 非数字应 1001");
    }

    /**
     * TC125 目标用户不存在（1002）。
     * 规则1 userId=999999 → 1002。
     */
    @Test
    void tc125TargetUserNotExist1002() throws Exception {
        assertEquals(1002, codeOf(doGet("/api/posts", "userId", "999999", "viewerId", String.valueOf(U_ZHANG))));
    }

    /**
     * TC126 空集与横切。
     * 规则1 ①钱七（存在但无帖）userId=钱七 → code=0 data=[]；②无 viewer → 1003。
     */
    @Test
    void tc126EmptyListAndViewerMissing() throws Exception {
        assertEquals(0, countOf(jdbcTemplate, "SELECT COUNT(*) FROM `post` WHERE user_id=? AND is_del=0", U_QIAN),
                "前置：钱七无帖");
        MvcResult result = doGet("/api/posts", "userId", String.valueOf(U_QIAN), "viewerId", String.valueOf(U_ZHANG));
        assertEquals(0, codeOf(result));
        assertEquals(List.of(), longListAt(result, "$.data[*].postId"), "无帖用户返回空数组禁 null");

        assertEquals(1003, codeOf(doGet("/api/posts", "userId", String.valueOf(U_ZHANG))), "无 viewer 应 1003");
    }
}
