package com.funny.moments.social;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import lombok.extern.slf4j.Slf4j;

/**
 * 入口13 GET /api/posts/{postId}（帖子详情-canView，T070）触发型集成测试：TC098-TC110，
 * 隐私矩阵主战场-路径①详情直达（design §5.2 canView 判定链 + explore 规则①-④）。
 * 做什么：基线 A（P0-P5 + 种子五人组，9 亿段保留 id）直插后，按冻结用例逐视角请求详情断言业务码与出参。
 * 口径说明：投影断言以 test-cases.md「隐私投影速查表」为唯一口径（三路径一致）；保留段隔离 dev mock 存量。
 */
@Slf4j
@Transactional
class PostDetailPrivacyTests extends TimelineBatch2TestBase {

    @BeforeEach
    void seedBaseline() {
        insertBaselineA();
    }

    /** 请求详情并断言业务码。 */
    private int getPostCode(long postId, long viewerId) throws Exception {
        MvcResult result = doGet("/api/posts/" + postId, "viewerId", String.valueOf(viewerId));
        return codeOf(result);
    }

    /**
     * TC098 主干-作者视角看 P1。
     * 规则1 作者最高优先级（Q4）：张三看自己的 type=3 帖 code=0；
     * 规则2 全字段出参：visibilityType=3/visibilityTypeStr=部分可见/status=1/imageUrls 按 sort 升序/createTimeStr 合法。
     */
    @Test
    void tc098AuthorViewP1FullFields() throws Exception {
        MvcResult result = doGet("/api/posts/" + P1_PART, "viewerId", String.valueOf(U_ZHANG));
        assertEquals(0, codeOf(result), "作者对自己帖子必可见");
        assertEquals(P1_PART, longAt(result, "$.data.postId"));
        assertEquals(U_ZHANG, longAt(result, "$.data.userId"));
        assertEquals(3L, longAt(result, "$.data.visibilityType"));
        assertEquals("部分可见", strAt(result, "$.data.visibilityTypeStr"));
        assertEquals(1L, longAt(result, "$.data.status"));
        assertEquals(java.util.List.of("/images/it_p1_a.png"), strListAt(result, "$.data.imageUrls"),
                "图片按 sort 升序");
        assertTrue(strAt(result, "$.data.createTimeStr").matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}"),
                "createTimeStr 应为 yyyy-MM-dd HH:mm:ss");
        assertNotNull(strAt(result, "$.data.nickname"), "作者昵称组装非空");
    }

    /**
     * TC099 P0 公开帖-非好友直达（规则② type=1 语义）。
     * 规则1 公开帖对所有人可见：钱七（非张三好友）详情直达 code=0。
     */
    @Test
    void tc099PublicPostNonFriendDirectAccess() throws Exception {
        assertEquals(0, getPostCode(P0_PUBLIC, U_QIAN), "公开帖对非好友也应可见（详情直达场景）");
    }

    /**
     * TC100 P3 私密帖-全视角合并（规则③）。
     * 规则1 李四/王五/赵六/钱七四视角请求 P3 均 1011；
     * 规则2 作者豁免：张三看自己的私密帖 code=0。
     */
    @Test
    void tc100PrivatePostAllViewersMerged() throws Exception {
        assertEquals(1011, getPostCode(P3_PRIVATE, U_LI), "李四不可见");
        assertEquals(1011, getPostCode(P3_PRIVATE, U_WANG), "王五不可见");
        assertEquals(1011, getPostCode(P3_PRIVATE, U_ZHAO), "赵六不可见");
        assertEquals(1011, getPostCode(P3_PRIVATE, U_QIAN), "钱七不可见");
        assertEquals(0, getPostCode(P3_PRIVATE, U_ZHANG), "作者对私密帖也可见");
    }

    /**
     * TC101 P1 部分可见-命中双路径（规则①）。
     * 规则1 ①王五（标签"同事"命中）②李四（指定好友命中）请求 P1 均 code=0（tagHit OR userHit）。
     */
    @Test
    void tc101PartVisibleDualHitPaths() throws Exception {
        assertEquals(0, getPostCode(P1_PART, U_WANG), "王五标签命中应可见");
        assertEquals(0, getPostCode(P1_PART, U_LI), "李四指定好友命中应可见");
    }

    /**
     * TC102 P1 部分可见-无命中（规则① 反例）。
     * 规则1 ①赵六（无标签且不在名单）②钱七（非好友）请求 P1 均 1011。
     */
    @Test
    void tc102PartVisibleMissMerged() throws Exception {
        assertEquals(1011, getPostCode(P1_PART, U_ZHAO), "赵六无命中不可见");
        assertEquals(1011, getPostCode(P1_PART, U_QIAN), "钱七无命中不可见");
    }

    /**
     * TC103 P4 部分可见-空集（规则④ C6 上半）。
     * 规则1 李四/王五/赵六/钱七视角请求 P4 均 1011（空集 tagHit/userHit 皆 false ⇒ 不可见）。
     */
    @Test
    void tc103PartVisibleEmptyListInvisible() throws Exception {
        assertEquals(1011, getPostCode(P4_PART_EMPTY, U_LI));
        assertEquals(1011, getPostCode(P4_PART_EMPTY, U_WANG));
        assertEquals(1011, getPostCode(P4_PART_EMPTY, U_ZHAO));
        assertEquals(1011, getPostCode(P4_PART_EMPTY, U_QIAN));
    }

    /**
     * TC104 P2 不给谁看-命中排除（规则②）。
     * 规则1 ①王五（标签"同事"命中）②赵六（指定排除名单命中）请求 P2 均 1011。
     */
    @Test
    void tc104ExcludeHitInvisibleMerged() throws Exception {
        assertEquals(1011, getPostCode(P2_EXCLUDE, U_WANG), "王五标签命中被排除");
        assertEquals(1011, getPostCode(P2_EXCLUDE, U_ZHAO), "赵六指定命中被排除");
    }

    /**
     * TC105 P2 不给谁看-无命中可见（规则② 正例）。
     * 规则1 ①李四（无命中）②钱七（非好友直达）请求 P2 均 code=0。
     */
    @Test
    void tc105ExcludeMissVisible() throws Exception {
        assertEquals(0, getPostCode(P2_EXCLUDE, U_LI), "李四无命中应可见");
        assertEquals(0, getPostCode(P2_EXCLUDE, U_QIAN), "钱七非好友无命中应可见（详情直达）");
    }

    /**
     * TC106 P5 不给谁看-空集（规则④ C6 下半）。
     * 规则1 李四/王五/赵六请求 P5 均 code=0（空集 ⇒ 等同公开）。
     */
    @Test
    void tc106ExcludeEmptyEqualsPublic() throws Exception {
        assertEquals(0, getPostCode(P5_EXCLUDE_EMPTY, U_LI));
        assertEquals(0, getPostCode(P5_EXCLUDE_EMPTY, U_WANG));
        assertEquals(0, getPostCode(P5_EXCLUDE_EMPTY, U_ZHAO));
    }

    /**
     * TC107 postId 无效（无效合并，NB1 承载）。
     * 规则1 ①/api/posts/0（≤0）②/api/posts/abc（非数字，TypeMismatch 承载）均 1001。
     */
    @Test
    void tc107InvalidPostIdMerged1001() throws Exception {
        assertEquals(1001, getPostCode(0L, U_ZHANG), "postId=0 应 1001");
        MvcResult abc = doGet("/api/posts/abc", "viewerId", String.valueOf(U_ZHANG));
        assertEquals(1001, codeOf(abc), "postId 非数字应经 TypeMismatch 转 1001");
    }

    /**
     * TC108 帖子不存在/已删（1010 双分支）。
     * 规则1 ①不存在 999999 → 1010；②对已软删帖（本用例内先经接口删除 P0）请求 → 1010。
     */
    @Test
    void tc108PostNotExistAndDeletedMerged1010() throws Exception {
        assertEquals(1010, getPostCode(999999L, U_ZHANG), "不存在应 1010");
        MvcResult deleteResult = doDelete("/api/posts/" + P0_PUBLIC, "viewerId", String.valueOf(U_ZHANG));
        assertEquals(0, codeOf(deleteResult), "前置：作者软删 P0");
        assertEquals(1010, getPostCode(P0_PUBLIC, U_ZHANG), "已删帖应 1010");
    }

    /**
     * TC109 viewer 缺失（横切引用）。
     * 规则1 无 viewerId 参数与 Cookie 请求 P1 → 1003。
     */
    @Test
    void tc109ViewerMissing1003() throws Exception {
        MvcResult result = doGet("/api/posts/" + P1_PART);
        assertEquals(1003, codeOf(result));
    }

    /**
     * TC110 已删帖作者本人也不可见（软删全路径 R7-详情路径，前置自含）。
     * 规则1 SQL 直插 P6（张三帖 id=保留段 900000105, type=1, is_del=1）后张三请求 → 1010
     * （is_del 过滤先于作者判定）。
     */
    @Test
    void tc110DeletedPostInvisibleToAuthor() throws Exception {
        jdbcTemplate.update("INSERT INTO `post` (id, user_id, content, visibility_type, status, is_del) "
                + "VALUES (?, ?, 'P6 已删帖', 1, 1, 1)", P6_DELETED, U_ZHANG);
        assertEquals(1010, getPostCode(P6_DELETED, U_ZHANG), "已删帖对作者也应 1010");
    }
}
