package com.funny.moments.social;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import lombok.extern.slf4j.Slf4j;

/**
 * 入口12 POST /api/posts（发布朋友圈-4 表事务，T060）触发型集成测试：TC083-TC094、TC096、TC097。
 * 做什么：MockMvc 入口级触发发布接口（不 mock 内部依赖），@Transactional 回滚 + JdbcTemplate 查库硬断言。
 * 口径说明：基线/断言全部使用 9 亿段保留 id（红绿终跑根治迁移）；并发用例 TC095 在
 * {@link PostCreateConcurrentTests}；DB 核对一律按本用例 postId 圈定作用域（不数全局行，免 mock 存量串扰）。
 */
@Slf4j
@Transactional
class PostCreateTests extends TimelineBatch2TestBase {

    private static final String PATH = "/api/posts";

    @BeforeEach
    void seedBaseline() {
        insertBaselineA();
    }

    /** 张三名下有效帖计数（本事务内确定性口径；保留段隔离，与 dev mock 存量无关）。 */
    private int countUserPosts() {
        return countOf(jdbcTemplate, "SELECT COUNT(*) FROM `post` WHERE user_id=? AND is_del=0", U_ZHANG);
    }

    /** 基线帖图片行数（P0 两图 + P1 一图 = 3，作用域圈定基线六帖）。 */
    private int countBaselineImages() {
        return countOf(jdbcTemplate,
                "SELECT COUNT(*) FROM `post_image` WHERE post_id IN (?,?,?,?,?,?) AND is_del=0",
                P0_PUBLIC, P1_PART, P2_EXCLUDE, P3_PRIVATE, P4_PART_EMPTY, P5_EXCLUDE_EMPTY);
    }

    /** 基线帖可见性标签行数（P1/P2 各 1 = 2，作用域圈定基线六帖）。 */
    private int countBaselineVisTags() {
        return countOf(jdbcTemplate,
                "SELECT COUNT(*) FROM `post_visibility_tag` WHERE post_id IN (?,?) AND is_del=0", P1_PART, P2_EXCLUDE);
    }

    /**
     * TC083 主干-type=3 全参四表落库。
     * 规则1 全字段有效等价类：code=0，visibilityType=3/visibilityTypeStr=部分可见/status=1，imageUrls 顺序与入参一致；
     * 规则2 4 表同事务落库：post +1 行(user_id=张三,visibility_type=3,status=1,is_del=0)、post_image +2 行(sort=1,2 顺序一致)、
     * post_visibility_tag +1 行(帖,同事标签)、post_visibility_user +1 行(帖,李四)。
     */
    @Test
    void tc083CreatePostType3FourTables() throws Exception {
        String body = String.format(
                "{\"content\":\"今天天气不错\",\"imageUrls\":[\"/images/it_c1.png\",\"/images/it_c2.png\"],"
                        + "\"visibilityType\":3,\"visibilityTagIds\":[%d],\"visibilityUserIds\":[%d]}",
                TAG_COLLEAGUE, U_LI);
        MvcResult result = doPostJson(PATH, body, "viewerId", String.valueOf(U_ZHANG));

        assertEquals(0, codeOf(result), "全参有效应成功");
        long postId = longAt(result, "$.data.postId");
        assertTrue(postId > 0, "postId 应非空");
        assertEquals(3L, longAt(result, "$.data.visibilityType"));
        assertEquals("部分可见", strAt(result, "$.data.visibilityTypeStr"));
        assertEquals(1L, longAt(result, "$.data.status"));
        assertEquals(List.of("/images/it_c1.png", "/images/it_c2.png"), strListAt(result, "$.data.imageUrls"),
                "出参 imageUrls 顺序应与入参一致");

        // 规则2：4 表 DB 核对
        assertEquals(1, countOf(jdbcTemplate, "SELECT COUNT(*) FROM `post` WHERE id=? AND user_id=? "
                + "AND content='今天天气不错' AND visibility_type=3 AND status=1 AND is_del=0", postId, U_ZHANG),
                "post 应落库");
        List<String> imageUrls = jdbcTemplate.queryForList(
                "SELECT image_url FROM `post_image` WHERE post_id=? AND is_del=0 ORDER BY sort", String.class, postId);
        assertEquals(List.of("/images/it_c1.png", "/images/it_c2.png"), imageUrls, "图片应按 sort=1,2 落库");
        assertEquals(1, countOf(jdbcTemplate,
                "SELECT COUNT(*) FROM `post_visibility_tag` WHERE post_id=? AND tag_id=? AND is_del=0",
                postId, TAG_COLLEAGUE));
        assertEquals(1, countOf(jdbcTemplate,
                "SELECT COUNT(*) FROM `post_visibility_user` WHERE post_id=? AND user_id=? AND is_del=0",
                postId, U_LI));
        log.info("TC083 通过，postId={}", postId);
    }

    /**
     * TC084 纯文字 type=1 + 2000 字上界。
     * 规则1 最简形态：纯文字 type=1 创建成功；
     * 规则2 content 恰 2000 字（有效上界）同样成功且 DB 按原长落库（CHAR_LENGTH 直查，断言长度而非行数）；
     * 规则3 最小落库：post 各 +1，post_image/post_visibility_tag/post_visibility_user 对新帖均 +0。
     */
    @Test
    void tc084TextOnlyAnd2000UpperBound() throws Exception {
        MvcResult plain = doPostJson(PATH, "{\"content\":\"纯文字\",\"visibilityType\":1}",
                "viewerId", String.valueOf(U_ZHANG));
        assertEquals(0, codeOf(plain), "纯文字帖应成功");
        long plainPostId = longAt(plain, "$.data.postId");

        String content2000 = "字".repeat(2000);
        MvcResult boundary = doPostJson(PATH,
                String.format("{\"content\":\"%s\",\"visibilityType\":1}", content2000),
                "viewerId", String.valueOf(U_ZHANG));
        assertEquals(0, codeOf(boundary), "恰 2000 字应为有效上界");
        long boundaryPostId = longAt(boundary, "$.data.postId");

        // 规则2：DB 直查 content 实际长度（CHAR_LENGTH）
        Integer actualLength = jdbcTemplate.queryForObject(
                "SELECT CHAR_LENGTH(content) FROM `post` WHERE id=? AND visibility_type=1 AND is_del=0",
                Integer.class, boundaryPostId);
        assertEquals(2000, actualLength == null ? -1 : actualLength, "2000 字帖应按原长落库（无截断）");
        // 规则3：最小落库核对（新帖 0 图 0 可见性）
        assertEquals(2, countUserPosts() - 6, "基线 6 帖之外应新增 2 帖");
        assertEquals(0, countOf(jdbcTemplate, "SELECT COUNT(*) FROM `post_image` WHERE post_id IN (?,?)",
                plainPostId, boundaryPostId), "纯文字帖不应有图片行");
        assertEquals(0, countOf(jdbcTemplate, "SELECT COUNT(*) FROM `post_visibility_tag` WHERE post_id IN (?,?)",
                plainPostId, boundaryPostId), "type=1 不应落可见性标签");
        assertEquals(0, countOf(jdbcTemplate, "SELECT COUNT(*) FROM `post_visibility_user` WHERE post_id IN (?,?)",
                plainPostId, boundaryPostId), "type=1 不应落可见性好友");
    }

    /**
     * TC085 纯图 type=2 九图边界。
     * 规则1 图上限边界：9 张合法 /images/ 路径（恰上限）创建成功；
     * 规则2 纯图形态：type=2 落库 post_image +9 行（sort=1..9）、可见性两表 +0。
     */
    @Test
    void tc085ImageOnlyNineImagesBoundary() throws Exception {
        List<String> urls = IntStream.rangeClosed(1, 9).mapToObj(i -> "/images/it_nine_" + i + ".png")
                .collect(Collectors.toList());
        String urlJson = urls.stream().map(u -> "\"" + u + "\"").collect(Collectors.joining(","));
        MvcResult result = doPostJson(PATH, String.format("{\"imageUrls\":[%s],\"visibilityType\":2}", urlJson),
                "viewerId", String.valueOf(U_ZHANG));

        assertEquals(0, codeOf(result), "恰 9 图应为有效上界");
        long postId = longAt(result, "$.data.postId");
        assertEquals(1, countOf(jdbcTemplate,
                "SELECT COUNT(*) FROM `post` WHERE id=? AND visibility_type=2 AND content='' AND is_del=0", postId),
                "纯图帖 content 落空串");
        List<Integer> sorts = jdbcTemplate.queryForList(
                "SELECT sort FROM `post_image` WHERE post_id=? AND is_del=0 ORDER BY sort", Integer.class, postId);
        assertEquals(List.of(1, 2, 3, 4, 5, 6, 7, 8, 9), sorts, "9 图应按 sort=1..9 落库");
        assertEquals(0, countOf(jdbcTemplate, "SELECT COUNT(*) FROM `post_visibility_tag` WHERE post_id=?", postId));
        assertEquals(0, countOf(jdbcTemplate, "SELECT COUNT(*) FROM `post_visibility_user` WHERE post_id=?", postId));
    }

    /**
     * TC086 type=1/2 携带可见性列表静默忽略（建议10 定稿）。
     * 规则1 type=1 携带 visibilityTagIds（含不存在的 999999）与 visibilityUserIds 不校验不报错（code=0）；
     * 规则2 不落库：可见性两表对该帖 +0 行。
     */
    @Test
    void tc086VisibilityListSilentlyIgnoredForType1() throws Exception {
        MvcResult result = doPostJson(PATH,
                String.format("{\"content\":\"x\",\"visibilityType\":1,\"visibilityTagIds\":[999999],"
                        + "\"visibilityUserIds\":[%d]}", U_LI),
                "viewerId", String.valueOf(U_ZHANG));
        assertEquals(0, codeOf(result), "type=1 携带可见性列表应静默忽略不报错");
        long postId = longAt(result, "$.data.postId");
        assertEquals(0, countOf(jdbcTemplate, "SELECT COUNT(*) FROM `post_visibility_tag` WHERE post_id=?", postId),
                "静默忽略=不落库");
        assertEquals(0, countOf(jdbcTemplate, "SELECT COUNT(*) FROM `post_visibility_user` WHERE post_id=?", postId),
                "静默忽略=不落库");
    }

    /**
     * TC087 参数无效合并（1001×5 合并）。
     * 规则1 ①visibilityType 缺失 ②=0 ③=5 ④content 纯空格+无图（trim 后同判同空）⑤trim 后 2001 字，均 code=1001；
     * 规则2 失败无残留：张三名下 post 0 新增。
     */
    @Test
    void tc087InvalidParamsMerged1001() throws Exception {
        String content2001 = " " + "字".repeat(2001);
        String[] invalidBodies = {
                "{\"content\":\"a\"}",                                             // ① visibilityType 缺失
                "{\"content\":\"a\",\"visibilityType\":0}",                        // ② 0
                "{\"content\":\"a\",\"visibilityType\":5}",                        // ③ 5
                "{\"content\":\"   \",\"visibilityType\":1}",                      // ④ 纯空格+无图（trim 后同空）
                String.format("{\"content\":\"%s\",\"visibilityType\":1}", content2001) // ⑤ trim 后 2001 字
        };
        int before = countUserPosts();
        for (String body : invalidBodies) {
            MvcResult result = doPostJson(PATH, body, "viewerId", String.valueOf(U_ZHANG));
            assertEquals(1001, codeOf(result),
                    "无效入参应统一 1001，body=" + body.substring(0, Math.min(40, body.length())));
        }
        assertEquals(before, countUserPosts(), "全部失败不应有 post 新增");
        assertEquals(6, countUserPosts(), "基线 6 帖不变");
    }

    /**
     * TC088 body 类型非法（R3 承载）。
     * 规则1 visibilityType 传字符串（"abc"）由 Jackson 反序列化失败抛 HttpMessageNotReadableException，
     * 经 BizExceptionAdvice 统一转 1001（非框架兜底 100）。
     */
    @Test
    void tc088BodyTypeIllegalTranslatesTo1001() throws Exception {
        MvcResult result = doPostJson(PATH, "{\"visibilityType\":\"abc\",\"content\":\"x\"}",
                "viewerId", String.valueOf(U_ZHANG));
        assertEquals(1001, codeOf(result), "body 字段类型非法应转 1001 而非 100");
    }

    /**
     * TC089 十图超限（1022）。
     * 规则1 10 张合法路径超单帖上限 → 1022；
     * 规则2 事务无残留：张三名下 post 0 新增、基线图片行不变、基线可见性行不变（作用域圈定基线六帖）。
     */
    @Test
    void tc089TenImagesExceedLimit1022() throws Exception {
        String urlJson = IntStream.rangeClosed(1, 10).mapToObj(i -> "\"/images/it_ten_" + i + ".png\"")
                .collect(Collectors.joining(","));
        MvcResult result = doPostJson(PATH, String.format("{\"imageUrls\":[%s],\"visibilityType\":1}", urlJson),
                "viewerId", String.valueOf(U_ZHANG));
        assertEquals(1022, codeOf(result), "10 图应 1022");

        assertEquals(6, countUserPosts(), "post 0 新增");
        assertEquals(3, countBaselineImages(), "基线图片行不变（P0 两图+P1 一图）");
        assertEquals(2, countBaselineVisTags(), "基线可见性标签行不变（P1/P2 各 1）");
    }

    /**
     * TC090 imageUrls 前缀契约（裁决1）。
     * 规则1 每项须匹配 ^/images/[A-Za-z0-9._-]+$：①外链 http:// ②错误前缀 /static/ ③穿越式 /images/../etc.png，均 1001；
     * 规则2 失败无残留：post 0 新增、基线图片行不变。
     */
    @Test
    void tc090ImageUrlPrefixContract() throws Exception {
        String[] illegalUrls = {"http://evil.com/a.png", "/static/a.png", "/images/../etc.png"};
        for (String url : illegalUrls) {
            MvcResult result = doPostJson(PATH,
                    String.format("{\"imageUrls\":[\"%s\"],\"visibilityType\":1}", url),
                    "viewerId", String.valueOf(U_ZHANG));
            assertEquals(1001, codeOf(result), "前缀/白名单非法应 1001，url=" + url);
        }
        assertEquals(6, countUserPosts(), "post 0 新增");
        assertEquals(3, countBaselineImages(), "基线图片行不变");
    }

    /**
     * TC091 visibilityTagIds 校验（1004/1006 分列）。
     * 规则1 ①不存在的标签 999999 → 1004；②李四的标签（非张三归属）→ 1006；
     * 规则2 失败无残留：张三名下 post 0 新增、基线可见性标签行不变（作用域圈定）。
     */
    @Test
    void tc091VisibilityTagIdOwnershipCheck() throws Exception {
        // 前置：SQL 直插李四的标签（保留段自造 id）
        jdbcTemplate.update("INSERT INTO `friend_tag` (id, user_id, tag_name) VALUES (?, ?, '李四的标签')",
                TAG_LI_OWN, U_LI);
        MvcResult notExist = doPostJson(PATH,
                "{\"content\":\"x\",\"visibilityType\":3,\"visibilityTagIds\":[999999]}",
                "viewerId", String.valueOf(U_ZHANG));
        assertEquals(1004, codeOf(notExist), "不存在的标签应 1004");

        MvcResult notOwner = doPostJson(PATH,
                String.format("{\"content\":\"x\",\"visibilityType\":3,\"visibilityTagIds\":[%d]}", TAG_LI_OWN),
                "viewerId", String.valueOf(U_ZHANG));
        assertEquals(1006, codeOf(notOwner), "非归属标签应 1006（发布侧越权防护）");

        assertEquals(6, countUserPosts(), "失败无残留");
        assertEquals(2, countBaselineVisTags(), "基线可见性标签行不变（作用域圈定）");
    }

    /**
     * TC092 visibilityUserIds 含不存在用户。
     * 规则1 type=3 指定好友名单含 999999（不存在）→ 1002；张三名下 post 0 新增。
     */
    @Test
    void tc092VisibilityUserNotExist1002() throws Exception {
        MvcResult result = doPostJson(PATH,
                "{\"content\":\"x\",\"visibilityType\":3,\"visibilityUserIds\":[999999]}",
                "viewerId", String.valueOf(U_ZHANG));
        assertEquals(1002, codeOf(result), "指定好友不存在应 1002");
        assertEquals(6, countUserPosts(), "失败无残留");
    }

    /**
     * TC093 指定列表去重（distinct 落库）。
     * 规则1 visibilityTagIds=[同事,同事]、visibilityUserIds=[李四,李四] → code=0；
     * 规则2 DB 去重落库：post_visibility_tag 该帖恰 1 行、post_visibility_user 该帖恰 1 行。
     */
    @Test
    void tc093VisibilityListDistinctOnInsert() throws Exception {
        MvcResult result = doPostJson(PATH,
                String.format("{\"content\":\"x\",\"visibilityType\":3,\"visibilityTagIds\":[%d,%d],"
                        + "\"visibilityUserIds\":[%d,%d]}", TAG_COLLEAGUE, TAG_COLLEAGUE, U_LI, U_LI),
                "viewerId", String.valueOf(U_ZHANG));
        assertEquals(0, codeOf(result), "重复 id 应先去重再校验落库");
        long postId = longAt(result, "$.data.postId");
        assertEquals(1, countOf(jdbcTemplate,
                "SELECT COUNT(*) FROM `post_visibility_tag` WHERE post_id=? AND is_del=0", postId),
                "重复标签应去重为 1 行");
        assertEquals(1, countOf(jdbcTemplate,
                "SELECT COUNT(*) FROM `post_visibility_user` WHERE post_id=? AND is_del=0", postId),
                "重复好友应去重为 1 行");
    }

    /**
     * TC094 幂等-重复请求（非幂等=合法连发语义，design §5.6 规则7）。
     * 规则1 同一 body 二连发均 code=0（同内容多发合法，无业务唯一键）；
     * 规则2 DB post +2，每帖图片/可见性行 post_id 归属正确无交叉。
     */
    @Test
    void tc094RepeatCreateIsLegalDoublePost() throws Exception {
        String body = String.format(
                "{\"content\":\"连发测试\",\"imageUrls\":[\"/images/it_dup.png\"],"
                        + "\"visibilityType\":3,\"visibilityTagIds\":[%d],\"visibilityUserIds\":[%d]}",
                TAG_COLLEAGUE, U_LI);
        MvcResult first = doPostJson(PATH, body, "viewerId", String.valueOf(U_ZHANG));
        MvcResult second = doPostJson(PATH, body, "viewerId", String.valueOf(U_ZHANG));
        assertEquals(0, codeOf(first), "第 1 笔应成功");
        assertEquals(0, codeOf(second), "第 2 笔连发也应成功（非幂等合法语义）");
        long firstPostId = longAt(first, "$.data.postId");
        long secondPostId = longAt(second, "$.data.postId");
        assertTrue(firstPostId != secondPostId, "两帖 postId 不同");

        assertEquals(8, countUserPosts(), "基线 6 + 新增 2");
        assertEquals(1, countOf(jdbcTemplate,
                "SELECT COUNT(*) FROM `post_image` WHERE post_id=? AND image_url='/images/it_dup.png'", firstPostId),
                "第 1 帖图片归属正确");
        assertEquals(1, countOf(jdbcTemplate,
                "SELECT COUNT(*) FROM `post_image` WHERE post_id=? AND image_url='/images/it_dup.png'", secondPostId),
                "第 2 帖图片归属正确");
        assertEquals(1, countOf(jdbcTemplate, "SELECT COUNT(*) FROM `post_visibility_tag` WHERE post_id=?", firstPostId));
        assertEquals(1, countOf(jdbcTemplate, "SELECT COUNT(*) FROM `post_visibility_user` WHERE post_id=?", firstPostId));
        assertEquals(1, countOf(jdbcTemplate, "SELECT COUNT(*) FROM `post_visibility_tag` WHERE post_id=?", secondPostId));
        assertEquals(1, countOf(jdbcTemplate, "SELECT COUNT(*) FROM `post_visibility_user` WHERE post_id=?", secondPostId));
    }

    /**
     * TC096 幂等-失败重试（校验失败后修复重试 + 原子性）。
     * 规则1 ①type=3 visibilityTagIds=[999999] → 1004 且 4 表 0 残留（事务原子）；
     * 规则2 ②修正为 [同事] 重试 → code=0 且 4 表完整落库。
     */
    @Test
    void tc096FailedThenFixedRetryAtomic() throws Exception {
        MvcResult failed = doPostJson(PATH,
                String.format("{\"content\":\"重试测试\",\"imageUrls\":[\"/images/it_retry.png\"],"
                        + "\"visibilityType\":3,\"visibilityTagIds\":[999999]}"),
                "viewerId", String.valueOf(U_ZHANG));
        assertEquals(1004, codeOf(failed), "不存在的标签应 1004");
        assertEquals(6, countUserPosts(), "失败后 post 0 残留（事务原子）");
        assertEquals(3, countBaselineImages(), "失败后基线图片行不变（0 残留）");
        assertEquals(2, countBaselineVisTags(), "失败后基线可见性标签行不变（0 残留）");

        MvcResult retried = doPostJson(PATH,
                String.format("{\"content\":\"重试测试\",\"imageUrls\":[\"/images/it_retry.png\"],"
                        + "\"visibilityType\":3,\"visibilityTagIds\":[%d]}", TAG_COLLEAGUE),
                "viewerId", String.valueOf(U_ZHANG));
        assertEquals(0, codeOf(retried), "修正后重试应成功");
        long postId = longAt(retried, "$.data.postId");
        assertEquals(1, countOf(jdbcTemplate, "SELECT COUNT(*) FROM `post_image` WHERE post_id=?", postId));
        assertEquals(1, countOf(jdbcTemplate, "SELECT COUNT(*) FROM `post_visibility_tag` WHERE post_id=?", postId));
    }

    /**
     * TC097 viewer 缺失（横切引用）。
     * 规则1 合法 body 无 viewerId 参数与 Cookie → 1003；张三名下 post 0 新增。
     */
    @Test
    void tc097ViewerMissing1003() throws Exception {
        MvcResult result = doPostJson(PATH, "{\"content\":\"x\",\"visibilityType\":1}");
        assertEquals(1003, codeOf(result), "无视角应 1003");
        assertEquals(6, countUserPosts(), "viewer 缺失不落库");
    }
}
