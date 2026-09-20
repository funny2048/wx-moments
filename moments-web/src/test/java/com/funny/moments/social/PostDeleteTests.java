package com.funny.moments.social;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import lombok.extern.slf4j.Slf4j;

/**
 * 入口14 DELETE /api/posts/{postId}（删除帖子-软删，T070）触发型集成测试：TC111-TC116、TC118。
 * 做什么：作者软删后按 DB 硬断言核对（is_del=1 且 status 快照保留、子表不级联、图片文件保留 D7）。
 * 口径说明：基线/断言全部使用 9 亿段保留 id（红绿终跑根治迁移）；并发用例 TC117 在
 * {@link PostDeleteConcurrentTests}；DB 核对按本用例 postId 圈定作用域；
 * TC111 图片文件保留采用"真实上传→发帖→删帖→文件仍在磁盘"链路验证（文件 IO 不受事务回滚，finally 清理）。
 */
@Slf4j
@Transactional
class PostDeleteTests extends TimelineBatch2TestBase {

    @BeforeEach
    void seedBaseline() {
        insertBaselineA();
    }

    /** 直查 post 原始行字段（绕过 @TableLogic，供 is_del/status 快照断言）。 */
    private int rawPostInt(long postId, String column) {
        return jdbcTemplate.queryForObject("SELECT " + column + " FROM `post` WHERE id = ?", Integer.class, postId);
    }

    /**
     * TC111 主干-作者软删（C7 状态快照语义 + 图片保留）。
     * 规则1 code=0 data=null；
     * 规则2 post(P0).is_del=1 且 status 仍=1（业务态快照保留）；
     * 规则3 post_image 行不动（不级联）、基线可见性行不变（作用域圈定）、图片文件仍在磁盘（D7）。
     */
    @Test
    void tc111AuthorSoftDeleteKeepsSnapshotAndImages() throws Exception {
        // 前置：真实上传一张图片（验证删帖不物理删文件）
        Path uploadedFile = null;
        try {
            MockMultipartFile file = new MockMultipartFile("file", "it_del_keep.png", "image/png", new byte[1024]);
            MvcResult upload = doUpload(uploadBuilder().file(file));
            assertEquals(0, codeOf(upload), "前置上传应成功");
            String imageUrl = strAt(upload, "$.data.imageUrl");
            uploadedFile = Paths.get(imageRootPath, imageUrl.substring("/images/".length()));

            MvcResult result = doDelete("/api/posts/" + P0_PUBLIC, "viewerId", String.valueOf(U_ZHANG));
            assertEquals(0, codeOf(result), "作者软删应成功");
            assertTrue(dataIsNull(result), "data 应为 null");

            // 规则2：is_del=1 且 status 快照保留
            assertEquals(1, rawPostInt(P0_PUBLIC, "is_del"), "软删应置 is_del=1");
            assertEquals(1, rawPostInt(P0_PUBLIC, "status"), "status 应保留业务态快照（C7）");

            // 规则3：子表不级联 + 图片文件保留（可见性行按基线两帖圈定作用域）
            assertEquals(2, countOf(jdbcTemplate,
                    "SELECT COUNT(*) FROM `post_image` WHERE post_id=? AND is_del=0", P0_PUBLIC),
                    "图片行不级联软删");
            assertEquals(2, countOf(jdbcTemplate,
                    "SELECT COUNT(*) FROM `post_visibility_tag` WHERE post_id IN (?,?) AND is_del=0",
                    P1_PART, P2_EXCLUDE), "基线可见性标签行不变（作用域圈定）");
            assertTrue(Files.exists(uploadedFile), "删帖后图片文件应保留（D7）");
        } finally {
            if (uploadedFile != null) {
                Files.deleteIfExists(uploadedFile);
            }
        }
    }

    /**
     * TC112 删后三路径全不可见（R7 详情/Feed/列表）。
     * 规则1 前置（本用例内）作者软删 P0；
     * 规则2 ①详情 GET /api/posts/{P0} → 1010；②Feed items 不含 P0；③按用户列表不含 P0。
     */
    @Test
    void tc112DeletedPostInvisibleOnAllThreePaths() throws Exception {
        MvcResult deleteResult = doDelete("/api/posts/" + P0_PUBLIC, "viewerId", String.valueOf(U_ZHANG));
        assertEquals(0, codeOf(deleteResult), "前置：作者软删 P0");

        assertEquals(1010, codeOf(doGet("/api/posts/" + P0_PUBLIC, "viewerId", String.valueOf(U_ZHANG))),
                "详情路径应 1010");

        MvcResult feed = doGet("/api/feed", "viewerId", String.valueOf(U_ZHANG), "pageSize", "50");
        assertEquals(0, codeOf(feed));
        List<Long> feedIds = longListAt(feed, "$.data.items[*].postId");
        assertFalse(feedIds.contains(P0_PUBLIC), "Feed 不应含已删帖");

        MvcResult list = doGet("/api/posts", "userId", String.valueOf(U_ZHANG), "viewerId", String.valueOf(U_ZHANG));
        assertEquals(0, codeOf(list));
        List<Long> listIds = longListAt(list, "$.data[*].postId");
        assertFalse(listIds.contains(P0_PUBLIC), "按用户列表不应含已删帖");
    }

    /**
     * TC113 非作者删除（1012 越权）。
     * 规则1 李四删张三帖 P1 → 1012；DB is_del 不变（仍 0）。
     */
    @Test
    void tc113NonAuthorDelete1012() throws Exception {
        MvcResult result = doDelete("/api/posts/" + P1_PART, "viewerId", String.valueOf(U_LI));
        assertEquals(1012, codeOf(result), "非作者删除应 1012");
        assertEquals(0, rawPostInt(P1_PART, "is_del"), "DB is_del 不变");
    }

    /**
     * TC114 帖子不存在（1010）。
     * 规则1 DELETE /api/posts/999999 → 1010。
     */
    @Test
    void tc114DeleteNotExist1010() throws Exception {
        assertEquals(1010, codeOf(doDelete("/api/posts/999999", "viewerId", String.valueOf(U_ZHANG))));
    }

    /**
     * TC115 参数与横切无效合并。
     * 规则1 ①postId=0 / postId=abc → 1001；②无 viewer → 1003。
     */
    @Test
    void tc115InvalidPostIdAndViewerMerged() throws Exception {
        assertEquals(1001, codeOf(doDelete("/api/posts/0", "viewerId", String.valueOf(U_ZHANG))), "postId=0 应 1001");
        assertEquals(1001, codeOf(doDelete("/api/posts/abc", "viewerId", String.valueOf(U_ZHANG))),
                "postId 非数字应 1001");
        assertEquals(1003, codeOf(doDelete("/api/posts/" + P1_PART)), "无 viewer 应 1003");
    }

    /**
     * TC116 幂等-重复请求（含已删态再删同分支合并）。
     * 规则1 对 P5 二连删：第 1 次 code=0，第 2 次 code=1010（删帖非幂等，已删/不存在同分支）；
     * 规则2 DB is_del 单次置 1（终态）。
     */
    @Test
    void tc116DeleteTwiceSecondGets1010() throws Exception {
        MvcResult first = doDelete("/api/posts/" + P5_EXCLUDE_EMPTY, "viewerId", String.valueOf(U_ZHANG));
        assertEquals(0, codeOf(first), "第 1 次删除应成功");
        MvcResult second = doDelete("/api/posts/" + P5_EXCLUDE_EMPTY, "viewerId", String.valueOf(U_ZHANG));
        assertEquals(1010, codeOf(second), "已删态再删应 1010（非幂等语义，双击第二次报错属预期）");
        assertEquals(1, rawPostInt(P5_EXCLUDE_EMPTY, "is_del"), "is_del 终态=1");
    }

    /**
     * TC118 幂等-失败重试（1012 越权→修正重试闭环，B4 补齐）。
     * 规则1 ①李四删 P1（1012 越权失败）DB 不变；②修正 viewerId=张三 重试同一删除 → code=0，P1.is_del=1。
     */
    @Test
    void tc118OverwriteFailThenFixedRetry() throws Exception {
        MvcResult failed = doDelete("/api/posts/" + P1_PART, "viewerId", String.valueOf(U_LI));
        assertEquals(1012, codeOf(failed), "越权删除应 1012");
        assertEquals(0, rawPostInt(P1_PART, "is_del"), "失败后 DB 不变");

        MvcResult fixed = doDelete("/api/posts/" + P1_PART, "viewerId", String.valueOf(U_ZHANG));
        assertEquals(0, codeOf(fixed), "修正作者后重试应成功");
        assertEquals(1, rawPostInt(P1_PART, "is_del"), "重试后软删生效");
    }
}
