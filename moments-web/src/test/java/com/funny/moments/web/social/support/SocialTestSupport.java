package com.funny.moments.web.social.support;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 社交域集成测试公用支撑（design §13.1 测试基座）。
 *
 * <p>承载两块内容：
 * <ul>
 * <li>基线 A 种子五人组（test-cases.md「测试数据基线」）：@Transactional 用例内直插 SQL
 *     （不走业务接口，可控 created_stime）；非事务用例（并发/缓存类）同样调用本方法，
 *     数据自动提交并由 {@link #cleanupBaseline} 按业务主键清理（MessageConsumeAsyncTests 的
 *     BEFORE 造数 / AFTER 清理口径，等价实现）。</li>
 * <li>MockMvc 响应解析工具：统一按 UTF-8 取响应体（防 MockHttpServletResponse 默认
 *     ISO-8859-1 导致中文断言乱码），数值取数兜 null。</li>
 * </ul>
 *
 * <p>id 口径说明：dev 库已有 35 用户模拟数据（自增 id 低段），为避免与存量及历史软删行冲突，
 * 基线固定使用 9 亿段高位 id（mock 批量生成的自增 id 永远到不了该段），与用例中
 * 张三(1)/李四(2)/王五(3)/赵六(4)/钱七(5)、标签 11/12/13、帖子 98-104 语义一一对应。
 *
 * @Author: tester（阶段六）
 * @Date: 2026-09-21
 */
public final class SocialTestSupport {

    // ==== 基线 A · 用户（五人组）====
    /** 张三（用例 id=1）：标签归属人 / 六帖作者 */
    public static final long ZHANGSAN = 900000001L;
    /** 李四（用例 id=2）：张三好友，绑定"同学"，P1 指定可见 */
    public static final long LISI = 900000002L;
    /** 王五（用例 id=3）：张三好友，绑定"同事"+"朋友"，P2 标签排除命中 */
    public static final long WANGWU = 900000003L;
    /** 赵六（用例 id=4）：张三好友，无标签，P2 指定排除命中 */
    public static final long ZHAOLIU = 900000004L;
    /** 钱七（用例 id=5）：与张三非好友，无标签无帖 */
    public static final long QIANQI = 900000005L;

    /** 用户版基线专用 id（入口2 用例自造数据，与五人组段错开） */
    public static final long USER_LISI_FOR_DETAIL = 900001002L;
    /** 用户版基线专用 id（软删用例） */
    public static final long USER_QIANQI_FOR_SOFTDEL = 900001005L;

    // ==== 基线 A · 标签（均归属张三）====
    /** 同学（用例 id=11）：绑定李四 */
    public static final long TAG_CLASSMATE = 900000011L;
    /** 同事（用例 id=12）：绑定王五；P1/P2 可见性引用 */
    public static final long TAG_COLLEAGUE = 900000012L;
    /** 朋友（用例 id=13）：绑定王五 */
    public static final long TAG_FRIEND = 900000013L;

    // ==== 基线 A · 帖子（均为张三发布，P0-P5）====
    /** P0（用例 id=104，10:00:01，type=1 公开，文+2图） */
    public static final long POST_P0 = 900000104L;
    /** P1（用例 id=101，10:00:00，type=3 部分可见：标签[同事]+好友[李四]，1图） */
    public static final long POST_P1 = 900000101L;
    /** P2（用例 id=102，10:00:00，type=4 不给谁看：标签[同事]+好友[赵六]） */
    public static final long POST_P2 = 900000102L;
    /** P3（用例 id=100，09:59:59，type=2 私密） */
    public static final long POST_P3 = 900000100L;
    /** P4（用例 id=99，09:59:58，type=3 部分可见空集） */
    public static final long POST_P4 = 900000099L;
    /** P5（用例 id=98，09:59:57，type=4 不给谁看空集） */
    public static final long POST_P5 = 900000098L;

    /** 基线用户集合（防御清理/插入用） */
    private static final Long[] USERS = {ZHANGSAN, LISI, WANGWU, ZHAOLIU, QIANQI};

    /** 基线标签集合 */
    private static final Long[] TAGS = {TAG_CLASSMATE, TAG_COLLEAGUE, TAG_FRIEND};

    /** 基线帖子集合 */
    private static final Long[] POSTS = {POST_P0, POST_P1, POST_P2, POST_P3, POST_P4, POST_P5};

    /** 基线数据业务时间（与用例基线同源，秒精度 DATETIME） */
    private static final String BASE_TIME = "2026-09-20 09:00:00";

    private SocialTestSupport() {
    }

    /**
     * 直插基线 A（五人组 + 对称好友 + 三标签 + 绑定 + P0-P5 帖及图片/可见性两表）。
     * 先按业务主键防御性 DELETE 再 INSERT：@Transactional 用例中两者随事务回滚无痕；
     * 非事务用例（并发/缓存）中保证可重复执行（上轮异常残留不阻塞本轮造数）。
     */
    public static void seedBaselineA(JdbcTemplate jdbc) {
        // 防御清理：仅触碰本基线的 9 亿段高位 id / 基线用户对，不涉及 dev 存量数据
        cleanupBaseline(jdbc);

        // 1 用户五人组（张三/李四/王五/赵六/钱七）
        jdbc.update("INSERT INTO user (id, nickname, avatar, gender, city, created_stime, is_del) "
                + "VALUES (?, ?, ?, ?, ?, ?, 0)", ZHANGSAN, "张三", "/images/ph_avatar_01.png", 1, "杭州", BASE_TIME);
        jdbc.update("INSERT INTO user (id, nickname, avatar, gender, city, created_stime, is_del) "
                + "VALUES (?, ?, ?, ?, ?, ?, 0)", LISI, "李四", "/images/ph_avatar_02.png", 1, "上海", BASE_TIME);
        jdbc.update("INSERT INTO user (id, nickname, avatar, gender, city, created_stime, is_del) "
                + "VALUES (?, ?, ?, ?, ?, ?, 0)", WANGWU, "王五", "/images/ph_avatar_03.png", 2, "北京", BASE_TIME);
        jdbc.update("INSERT INTO user (id, nickname, avatar, gender, city, created_stime, is_del) "
                + "VALUES (?, ?, ?, ?, ?, ?, 0)", ZHAOLIU, "赵六", "/images/ph_avatar_04.png", 1, "广州", BASE_TIME);
        jdbc.update("INSERT INTO user (id, nickname, avatar, gender, city, created_stime, is_del) "
                + "VALUES (?, ?, ?, ?, ?, ?, 0)", QIANQI, "钱七", "/images/ph_avatar_05.png", 2, "深圳", BASE_TIME);

        // 2 好友关系：对称双记录 (1,2)(2,1)(1,3)(3,1)(1,4)(4,1)；钱七与张三非好友（C11）
        jdbc.update("INSERT INTO friendship (user_id, friend_user_id, created_stime, is_del) "
                + "VALUES (?, ?, ?, 0)", ZHANGSAN, LISI, BASE_TIME);
        jdbc.update("INSERT INTO friendship (user_id, friend_user_id, created_stime, is_del) "
                + "VALUES (?, ?, ?, 0)", LISI, ZHANGSAN, BASE_TIME);
        jdbc.update("INSERT INTO friendship (user_id, friend_user_id, created_stime, is_del) "
                + "VALUES (?, ?, ?, 0)", ZHANGSAN, WANGWU, BASE_TIME);
        jdbc.update("INSERT INTO friendship (user_id, friend_user_id, created_stime, is_del) "
                + "VALUES (?, ?, ?, 0)", WANGWU, ZHANGSAN, BASE_TIME);
        jdbc.update("INSERT INTO friendship (user_id, friend_user_id, created_stime, is_del) "
                + "VALUES (?, ?, ?, 0)", ZHANGSAN, ZHAOLIU, BASE_TIME);
        jdbc.update("INSERT INTO friendship (user_id, friend_user_id, created_stime, is_del) "
                + "VALUES (?, ?, ?, 0)", ZHAOLIU, ZHANGSAN, BASE_TIME);

        // 3 标签（均归属张三）：同学(11)/同事(12)/朋友(13)
        jdbc.update("INSERT INTO friend_tag (id, user_id, tag_name, created_stime, is_del) "
                + "VALUES (?, ?, ?, ?, 0)", TAG_CLASSMATE, ZHANGSAN, "同学", BASE_TIME);
        jdbc.update("INSERT INTO friend_tag (id, user_id, tag_name, created_stime, is_del) "
                + "VALUES (?, ?, ?, ?, 0)", TAG_COLLEAGUE, ZHANGSAN, "同事", BASE_TIME);
        jdbc.update("INSERT INTO friend_tag (id, user_id, tag_name, created_stime, is_del) "
                + "VALUES (?, ?, ?, ?, 0)", TAG_FRIEND, ZHANGSAN, "朋友", BASE_TIME);

        // 4 绑定：(11,李四)/(12,王五)/(13,王五)——赵六无标签
        jdbc.update("INSERT INTO friend_tag_relation (tag_id, friend_user_id, created_stime, is_del) "
                + "VALUES (?, ?, ?, 0)", TAG_CLASSMATE, LISI, BASE_TIME);
        jdbc.update("INSERT INTO friend_tag_relation (tag_id, friend_user_id, created_stime, is_del) "
                + "VALUES (?, ?, ?, 0)", TAG_COLLEAGUE, WANGWU, BASE_TIME);
        jdbc.update("INSERT INTO friend_tag_relation (tag_id, friend_user_id, created_stime, is_del) "
                + "VALUES (?, ?, ?, 0)", TAG_FRIEND, WANGWU, BASE_TIME);

        // 5 帖子 P0-P5（张三六帖，created_stime 秒级可控）
        jdbc.update("INSERT INTO post (id, user_id, content, visibility_type, status, created_stime, is_del) "
                + "VALUES (?, ?, ?, 1, 1, ?, 0)", POST_P0, ZHANGSAN, "P0 公开帖：今天天气不错", "2026-09-20 10:00:01");
        jdbc.update("INSERT INTO post (id, user_id, content, visibility_type, status, created_stime, is_del) "
                + "VALUES (?, ?, ?, 3, 1, ?, 0)", POST_P1, ZHANGSAN, "P1 部分可见：同事标签与李四", "2026-09-20 10:00:00");
        jdbc.update("INSERT INTO post (id, user_id, content, visibility_type, status, created_stime, is_del) "
                + "VALUES (?, ?, ?, 4, 1, ?, 0)", POST_P2, ZHANGSAN, "P2 不给谁看：同事标签与赵六", "2026-09-20 10:00:00");
        jdbc.update("INSERT INTO post (id, user_id, content, visibility_type, status, created_stime, is_del) "
                + "VALUES (?, ?, ?, 2, 1, ?, 0)", POST_P3, ZHANGSAN, "P3 私密帖", "2026-09-20 09:59:59");
        jdbc.update("INSERT INTO post (id, user_id, content, visibility_type, status, created_stime, is_del) "
                + "VALUES (?, ?, ?, 3, 1, ?, 0)", POST_P4, ZHANGSAN, "P4 部分可见空集", "2026-09-20 09:59:58");
        jdbc.update("INSERT INTO post (id, user_id, content, visibility_type, status, created_stime, is_del) "
                + "VALUES (?, ?, ?, 4, 1, ?, 0)", POST_P5, ZHANGSAN, "P5 不给谁看空集", "2026-09-20 09:59:57");

        // 6 帖子图片：P0 两图（sort=1,2）、P1 一图，其余 0
        jdbc.update("INSERT INTO post_image (post_id, image_url, sort, is_del) VALUES (?, ?, 1, 0)",
                POST_P0, "/images/ph_content_01.png");
        jdbc.update("INSERT INTO post_image (post_id, image_url, sort, is_del) VALUES (?, ?, 2, 0)",
                POST_P0, "/images/ph_content_02.png");
        jdbc.update("INSERT INTO post_image (post_id, image_url, sort, is_del) VALUES (?, ?, 1, 0)",
                POST_P1, "/images/ph_content_03.png");

        // 7 可见性：P1→标签[12]+好友[李四]；P2→标签[12]+好友[赵六]
        jdbc.update("INSERT INTO post_visibility_tag (post_id, tag_id, is_del) VALUES (?, ?, 0)", POST_P1, TAG_COLLEAGUE);
        jdbc.update("INSERT INTO post_visibility_tag (post_id, tag_id, is_del) VALUES (?, ?, 0)", POST_P2, TAG_COLLEAGUE);
        jdbc.update("INSERT INTO post_visibility_user (post_id, user_id, is_del) VALUES (?, ?, 0)", POST_P1, LISI);
        jdbc.update("INSERT INTO post_visibility_user (post_id, user_id, is_del) VALUES (?, ?, 0)", POST_P2, ZHAOLIU);
    }

    /**
     * 按业务主键清理基线 A（含软删行与并发残留行）。
     * 仅删除本基线 9 亿段高位 id 相关行，@Transactional 用例回滚后为空操作。
     */
    public static void cleanupBaseline(JdbcTemplate jdbc) {
        String userIds = joinIds(USERS);
        String tagIds = joinIds(TAGS);
        String postIds = joinIds(POSTS);
        jdbc.update("DELETE FROM post_visibility_tag WHERE post_id IN (" + postIds + ") OR tag_id IN (" + tagIds + ")");
        jdbc.update("DELETE FROM post_visibility_user WHERE post_id IN (" + postIds + ") OR user_id IN (" + userIds + ")");
        jdbc.update("DELETE FROM post_image WHERE post_id IN (" + postIds + ")");
        jdbc.update("DELETE FROM post WHERE id IN (" + postIds + ")");
        jdbc.update("DELETE FROM friend_tag_relation WHERE tag_id IN (" + tagIds + ") OR friend_user_id IN (" + userIds + ")");
        jdbc.update("DELETE FROM friend_tag WHERE id IN (" + tagIds + ") OR user_id IN (" + userIds + ")");
        jdbc.update("DELETE FROM friendship WHERE user_id IN (" + userIds + ") OR friend_user_id IN (" + userIds + ")");
        jdbc.update("DELETE FROM user WHERE id IN (" + userIds + ")");
    }

    /**
     * 计算指定表当前最大自增 id + 100000，作为"必然不存在"的业务 id
     * （比用例字面 999999 更稳：不受 dev 库未来数据增长影响）。
     */
    public static long nonExistingId(JdbcTemplate jdbc, String table) {
        Long maxId = jdbc.queryForObject("SELECT COALESCE(MAX(id), 0) FROM " + table, Long.class);
        return (maxId == null ? 0L : maxId) + 100000L;
    }

    // ==== 响应解析工具（UTF-8 防中文乱码 + 数值兜 null）====

    /** 按 UTF-8 读取响应体（MockHttpServletResponse 默认字符集非 UTF-8，中文断言必须走字节） */
    public static String bodyUtf8(MvcResult result) {
        return new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
    }

    /** 解析响应体为 JsonPath 文档 */
    public static DocumentContext json(MvcResult result) {
        return JsonPath.parse(bodyUtf8(result));
    }

    /** 读取响应 code（ApiResult 信封） */
    public static int codeOf(MvcResult result) {
        return toInt(json(result).read("$.code"));
    }

    /** data 为对象数组时读取（data=null/缺失 返回空列表而非 null，禁 null 原则） */
    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> dataListOf(MvcResult result) {
        Object data = json(result).read("$.data");
        if (data == null) {
            return new ArrayList<>();
        }
        return (List<Map<String, Object>>) data;
    }

    /** data 为对象时读取 */
    public static Map<String, Object> dataMapOf(MvcResult result) {
        return json(result).read("$.data");
    }

    /** 用户列表分页出参读取：data 为 UserPageOut 对象（{total, users}），数组在 data.users */
    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> userListOf(MvcResult result) {
        Object users = json(result).read("$.data.users");
        if (users == null) {
            return new ArrayList<>();
        }
        return (List<Map<String, Object>>) users;
    }

    /** 列表按 userId 字段转 map（好友/用户列表组装断言用） */
    public static Map<Long, Map<String, Object>> byUserId(List<Map<String, Object>> rows) {
        Map<Long, Map<String, Object>> map = new HashMap<>(rows.size() * 2);
        for (Map<String, Object> row : rows) {
            map.put(toLong(row.get("userId")), row);
        }
        return map;
    }

    /** 列表按 tagName 字段转 map（标签列表断言用） */
    public static Map<String, Map<String, Object>> byTagName(List<Map<String, Object>> rows) {
        Map<String, Map<String, Object>> map = new HashMap<>(rows.size() * 2);
        for (Map<String, Object> row : rows) {
            map.put(String.valueOf(row.get("tagName")), row);
        }
        return map;
    }

    /** 读取行内 tagNames 字段（禁 null 返回空列表） */
    @SuppressWarnings("unchecked")
    public static List<String> tagNamesOf(Map<String, Object> row) {
        Object tagNames = row.get("tagNames");
        if (tagNames == null) {
            return new ArrayList<>();
        }
        return (List<String>) tagNames;
    }

    public static int toInt(Object value) {
        return value == null ? 0 : ((Number) value).intValue();
    }

    public static long toLong(Object value) {
        return value == null ? 0L : ((Number) value).longValue();
    }

    private static String joinIds(Long[] ids) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < ids.length; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(ids[i]);
        }
        return builder.toString();
    }
}
