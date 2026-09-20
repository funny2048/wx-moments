package com.funny.moments.social;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import com.funny.moments.Application;
import com.funny.moments.service.social.cache.FriendIdsCacheManager;
import com.funny.moments.service.social.cache.FriendTagCacheManager;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import lombok.extern.slf4j.Slf4j;

/**
 * 批次2 集成测试公共基座（入口12-17：发帖/帖子详情/软删/按用户查/Feed/图片上传，TC083-TC143）。
 * 环境与口径（design §13.1 + tester 回滚策略）：
 * <ul>
 * <li>dev profile 全量上下文（8 表已由 SchemaInitRunner 建立），MockMvc 入口级触发，不 mock 内部依赖</li>
 * <li>触发型用例：方法级 @Transactional 回滚；基线 A（种子五人组 + P0-P5）测试方法内直插 SQL
 *     （精确主键 + created_stime 可控，不走业务接口）</li>
 * <li><b>基线 id 保留段（红绿终跑根治修复）</b>：dev 库存在真实 mock 用户/好友/帖子（低段自增 id），
 *     基线五人组/标签/六帖全部固定使用 9 亿段高位 id（与 part1 SocialTestSupport 同段同值对齐，
 *     mock 自增 id 永远到不了该段）——测试结果与执行顺序、dev 存量数据完全解耦；
 *     清理/断言只触碰保留段与自造标记，绝不动 dev 存量（此前"作者维度 sweep"补丁已随迁移删除）</li>
 * <li>并发用例（@Transactional 管不到多线程）：不走方法事务，直插已提交基线 + @BeforeEach/@AfterEach
 *     物理清理保留段（任意失败路径均执行）</li>
 * <li>Redis 共享中间件：@BeforeEach INCR 双版本号使旧缓存 key 全部失效（读路径强制回源本事务数据，
 *     免并行测试串台）；Redis 不可用时静默降级（C12 弱依赖回源 DB，不阻断测试）</li>
 * </ul>
 */
@Slf4j
@SpringBootTest(classes = Application.class)
@AutoConfigureMockMvc
@ActiveProfiles("dev")
public abstract class TimelineBatch2TestBase {

    /** 日志追踪参数（api.md §1.2：不参与鉴权） */
    protected static final String APP_ID = "moments-web";

    // ===== 种子五人组（测试数据基线 A，9 亿段保留 id；与 part1 SocialTestSupport 同段同值）=====
    /** 张三（用例 id=1）：标签归属人 / 六帖作者 */
    protected static final long U_ZHANG = 900000001L;
    /** 李四（用例 id=2）：张三好友，绑定"同学"，P1 指定可见 */
    protected static final long U_LI = 900000002L;
    /** 王五（用例 id=3）：张三好友，绑定"同事"+"朋友"，P2 标签排除命中 */
    protected static final long U_WANG = 900000003L;
    /** 赵六（用例 id=4）：张三好友，无标签，P2 指定排除命中 */
    protected static final long U_ZHAO = 900000004L;
    /** 钱七（用例 id=5）：与张三非好友，无标签无帖 */
    protected static final long U_QIAN = 900000005L;
    /** 孙九（批次2 自造：无帖无好友新用户，TC129；段错开 part1 的 900001xxx 专段） */
    protected static final long U_SUN = 900002006L;

    // ===== 张三的标签（均归属 U_ZHANG）=====
    protected static final long TAG_CLASSMATE = 900000011L;
    protected static final long TAG_COLLEAGUE = 900000012L;
    protected static final long TAG_FRIEND = 900000013L;
    /** 李四的标签（TC091 非归属校验用，批次2 自造段） */
    protected static final long TAG_LI_OWN = 900002021L;

    // ===== 基线六帖（作者均为张三，visibilityType 见 test-cases.md 测试数据基线）=====
    protected static final long P0_PUBLIC = 900000104L;
    protected static final long P1_PART = 900000101L;
    protected static final long P2_EXCLUDE = 900000102L;
    protected static final long P3_PRIVATE = 900000100L;
    protected static final long P4_PART_EMPTY = 900000099L;
    protected static final long P5_EXCLUDE_EMPTY = 900000098L;
    /** P6 已删帖（TC110 前置自含直插，保留段顺延） */
    protected static final long P6_DELETED = 900000105L;
    /** TC123 放大补偿造帖起始 id（9 帖 + 1 旧公开帖 = 900002200-900002209，批次2 自造段） */
    protected static final long TC123_POST_BASE = 900002200L;

    /** 基线六帖 id 集合（列表/Feed 投影断言的外部残留隔离过滤用，断言语义=基线帖相对顺序不变） */
    protected static final Set<Long> BASELINE_POST_IDS =
            Set.of(P0_PUBLIC, P1_PART, P2_EXCLUDE, P3_PRIVATE, P4_PART_EMPTY, P5_EXCLUDE_EMPTY);

    /**
     * 批次2 自造帖内容标记前缀（已提交用例的进程中断遗留清障用）：按标记物理清除，
     * 仅触碰本批自造行，不涉及 dev 存量数据。
     */
    private static final String[] SELF_POST_MARKERS = {"IT-CONC-POST-", "IT-TC128-HIDE-", "IT-CONC-DELETE-"};

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    private FriendIdsCacheManager friendIdsCacheManager;

    @Autowired
    private FriendTagCacheManager friendTagCacheManager;

    /** 图片根目录（与 LocalImageServiceImpl/WebConfig 注入同一 key，R2-建议3） */
    @Value("${moments.image.root-path:${user.home}/moments-data/images}")
    protected String imageRootPath;

    /**
     * 每个用例开始前失效社交双缓存（INCR 全局版本号 = evictAll）：保证读路径回源本用例事务内数据，
     * 免受前序用例/并行测试回填的串台；Redis 不可用时降级（Service 侧本就回源 DB）。
     */
    @BeforeEach
    void isolateSharedCaches() {
        try {
            friendIdsCacheManager.evictAll();
            friendTagCacheManager.evictAll();
        } catch (Exception e) {
            log.warn("测试前置缓存失效失败（Redis 不可用，读路径将回源 DB）: {}", e.getMessage());
        }
    }

    // ===== 基线 A 直插（保留段精确主键，created_stime 可控）=====

    /**
     * 清障批次2 保留 id 段（仅本套件保留段：user 900000001-900000005/900002006、
     * friend_tag 900000011/12/13/900002021、post 900000098-900000105 与 900002200-900002209 及子表行）。
     * 事务内调用时回滚后原数据原样恢复；已提交调用（并发用例）作为 @BeforeEach/@AfterEach 清理复用。
     * 绝不触碰 dev 存量（低段自增 id）数据。
     */
    protected void clearBaselineIdSpace() {
        jdbcTemplate.update("DELETE FROM `post_visibility_user` WHERE post_id BETWEEN 900000098 AND 900000105 "
                + "OR post_id BETWEEN 900002200 AND 900002209 "
                + "OR user_id IN (900000001, 900000002, 900000003, 900000004, 900000005, 900002006)");
        jdbcTemplate.update("DELETE FROM `post_visibility_tag` WHERE post_id BETWEEN 900000098 AND 900000105 "
                + "OR post_id BETWEEN 900002200 AND 900002209 "
                + "OR tag_id IN (900000011, 900000012, 900000013, 900002021)");
        jdbcTemplate.update("DELETE FROM `post_image` WHERE post_id BETWEEN 900000098 AND 900000105 "
                + "OR post_id BETWEEN 900002200 AND 900002209");
        jdbcTemplate.update("DELETE FROM `post` WHERE id BETWEEN 900000098 AND 900000105 "
                + "OR id BETWEEN 900002200 AND 900002209");
        jdbcTemplate.update("DELETE FROM `friend_tag_relation` WHERE tag_id IN (900000011, 900000012, 900000013, 900002021) "
                + "OR friend_user_id IN (900000001, 900000002, 900000003, 900000004, 900000005, 900002006)");
        jdbcTemplate.update("DELETE FROM `friend_tag` WHERE id IN (900000011, 900000012, 900000013, 900002021)");
        jdbcTemplate.update("DELETE FROM `friendship` WHERE user_id IN (900000001, 900000002, 900000003, 900000004, 900000005, 900002006) "
                + "AND friend_user_id IN (900000001, 900000002, 900000003, 900000004, 900000005, 900002006)");
        jdbcTemplate.update("DELETE FROM `user` WHERE id IN (900000001, 900000002, 900000003, 900000004, 900000005, 900002006)");
    }

    /**
     * 批次2 自造帖残留清障（已提交并发用例的进程中断遗留）：
     * 按内容标记物理删除自造帖（含子表行）。标记内容仅本批产生，与 dev 存量无关。
     */
    protected void scrubBatch2Residue() {
        for (String marker : SELF_POST_MARKERS) {
            List<Long> residueIds = jdbcTemplate.queryForList(
                    "SELECT id FROM `post` WHERE content LIKE ?", Long.class, marker + "%");
            for (Long postId : residueIds) {
                jdbcTemplate.update("DELETE FROM `post_visibility_user` WHERE post_id = ?", postId);
                jdbcTemplate.update("DELETE FROM `post_visibility_tag` WHERE post_id = ?", postId);
                jdbcTemplate.update("DELETE FROM `post_image` WHERE post_id = ?", postId);
                jdbcTemplate.update("DELETE FROM `post` WHERE id = ?", postId);
            }
        }
    }

    /**
     * 直插基线 A（explore 七 Q1 种子数据，test-cases.md「测试数据基线」同源，id 全部为 9 亿段保留值）：
     * 张三/李四/王五/赵六/钱七；好友对称双记录 (张三,李四)(张三,王五)(张三,赵六)，钱七与张三非好友；
     * 标签 同学/同事/朋友（张三的）；绑定 (同学,李四)/(同事,王五)/(朋友,王五)，赵六无标签；
     * P0(type1,10:00:01,2图)/P1(type3,10:00:00,标签[同事]+好友[李四],1图)/P2(type4,10:00:00,标签[同事]+排除[赵六])
     * /P3(type2,09:59:59)/P4(type3 空集,09:59:58)/P5(type4 空集,09:59:57)。
     */
    protected void insertBaselineA() {
        scrubBatch2Residue();
        clearBaselineIdSpace();
        String day = "2026-09-20 ";
        // 种子五人组（gender：1 男 2 女 0 未知）
        jdbcTemplate.update("INSERT INTO `user` (id, nickname, avatar, gender, city, created_stime) VALUES (?,?,?,?,?,?)",
                U_ZHANG, "张三", "/images/ph_avatar_01.png", 1, "杭州", Timestamp.valueOf(day + "09:00:00"));
        jdbcTemplate.update("INSERT INTO `user` (id, nickname, avatar, gender, city, created_stime) VALUES (?,?,?,?,?,?)",
                U_LI, "李四", "/images/ph_avatar_02.png", 1, "上海", Timestamp.valueOf(day + "09:00:01"));
        jdbcTemplate.update("INSERT INTO `user` (id, nickname, avatar, gender, city, created_stime) VALUES (?,?,?,?,?,?)",
                U_WANG, "王五", "/images/ph_avatar_03.png", 2, "北京", Timestamp.valueOf(day + "09:00:02"));
        jdbcTemplate.update("INSERT INTO `user` (id, nickname, avatar, gender, city, created_stime) VALUES (?,?,?,?,?,?)",
                U_ZHAO, "赵六", "/images/ph_avatar_04.png", 1, "广州", Timestamp.valueOf(day + "09:00:03"));
        jdbcTemplate.update("INSERT INTO `user` (id, nickname, avatar, gender, city, created_stime) VALUES (?,?,?,?,?,?)",
                U_QIAN, "钱七", "/images/ph_avatar_05.png", 2, "深圳", Timestamp.valueOf(day + "09:00:04"));
        // 好友关系（对称双记录，C11）：钱七与张三非好友
        Timestamp friendTime = Timestamp.valueOf(day + "09:30:00");
        long[][] friendPairs = {{U_ZHANG, U_LI}, {U_LI, U_ZHANG}, {U_ZHANG, U_WANG}, {U_WANG, U_ZHANG},
                {U_ZHANG, U_ZHAO}, {U_ZHAO, U_ZHANG}};
        for (long[] pair : friendPairs) {
            jdbcTemplate.update("INSERT INTO `friendship` (user_id, friend_user_id, created_stime) VALUES (?,?,?)",
                    pair[0], pair[1], friendTime);
        }
        // 张三的标签
        Timestamp tagTime = Timestamp.valueOf(day + "09:40:00");
        jdbcTemplate.update("INSERT INTO `friend_tag` (id, user_id, tag_name, created_stime) VALUES (?,?,?,?)",
                TAG_CLASSMATE, U_ZHANG, "同学", tagTime);
        jdbcTemplate.update("INSERT INTO `friend_tag` (id, user_id, tag_name, created_stime) VALUES (?,?,?,?)",
                TAG_COLLEAGUE, U_ZHANG, "同事", tagTime);
        jdbcTemplate.update("INSERT INTO `friend_tag` (id, user_id, tag_name, created_stime) VALUES (?,?,?,?)",
                TAG_FRIEND, U_ZHANG, "朋友", tagTime);
        // 标签绑定：(同学,李四)/(同事,王五)/(朋友,王五)，赵六无标签
        Timestamp bindTime = Timestamp.valueOf(day + "09:45:00");
        jdbcTemplate.update("INSERT INTO `friend_tag_relation` (tag_id, friend_user_id, created_stime) VALUES (?,?,?)",
                TAG_CLASSMATE, U_LI, bindTime);
        jdbcTemplate.update("INSERT INTO `friend_tag_relation` (tag_id, friend_user_id, created_stime) VALUES (?,?,?)",
                TAG_COLLEAGUE, U_WANG, bindTime);
        jdbcTemplate.update("INSERT INTO `friend_tag_relation` (tag_id, friend_user_id, created_stime) VALUES (?,?,?)",
                TAG_FRIEND, U_WANG, bindTime);
        // 基线六帖（status 均 1 正常）
        insertBaselinePost(P0_PUBLIC, 1, "P0 公开帖：今天天气不错", "10:00:01");
        insertBaselinePost(P1_PART, 3, "P1 部分可见：同事标签与李四可见", "10:00:00");
        insertBaselinePost(P2_EXCLUDE, 4, "P2 不给谁看：同事标签与赵六不可见", "10:00:00");
        insertBaselinePost(P3_PRIVATE, 2, "P3 私密帖", "09:59:59");
        insertBaselinePost(P4_PART_EMPTY, 3, "P4 部分可见空集", "09:59:58");
        insertBaselinePost(P5_EXCLUDE_EMPTY, 4, "P5 不给谁看空集", "09:59:57");
        // P0 两图（sort=1,2）、P1 一图，其余 0
        jdbcTemplate.update("INSERT INTO `post_image` (post_id, image_url, sort) VALUES (?,?,?)",
                P0_PUBLIC, "/images/it_p0_a.png", 1);
        jdbcTemplate.update("INSERT INTO `post_image` (post_id, image_url, sort) VALUES (?,?,?)",
                P0_PUBLIC, "/images/it_p0_b.png", 2);
        jdbcTemplate.update("INSERT INTO `post_image` (post_id, image_url, sort) VALUES (?,?,?)",
                P1_PART, "/images/it_p1_a.png", 1);
        // 可见性名单：P1→标签[同事]+好友[李四]；P2→标签[同事]+排除[赵六]
        jdbcTemplate.update("INSERT INTO `post_visibility_tag` (post_id, tag_id) VALUES (?,?)", P1_PART, TAG_COLLEAGUE);
        jdbcTemplate.update("INSERT INTO `post_visibility_user` (post_id, user_id) VALUES (?,?)", P1_PART, U_LI);
        jdbcTemplate.update("INSERT INTO `post_visibility_tag` (post_id, tag_id) VALUES (?,?)", P2_EXCLUDE, TAG_COLLEAGUE);
        jdbcTemplate.update("INSERT INTO `post_visibility_user` (post_id, user_id) VALUES (?,?)", P2_EXCLUDE, U_ZHAO);
    }

    /** 直插单条基线帖（user_id 恒张三，status=1，created_stime 精确到秒）。 */
    protected void insertBaselinePost(long postId, int visibilityType, String content, String timeOfDay) {
        jdbcTemplate.update(
                "INSERT INTO `post` (id, user_id, content, visibility_type, status, created_stime) VALUES (?,?,?,?,?,?)",
                postId, U_ZHANG, content, visibilityType, 1, Timestamp.valueOf("2026-09-20 " + timeOfDay));
    }

    /**
     * 并发用例兜底清理（任意失败路径均执行）：由已提交用例的 @BeforeEach/@AfterEach 调用，
     * 物理删除批次2 全部自造帖残留（按标记，覆盖跨用例/历史中断遗留）+ 保留 id 段。
     * 绝不触碰 dev 存量数据。
     */
    protected void cleanupCommittedArtifacts() {
        scrubBatch2Residue();
        clearBaselineIdSpace();
    }

    // ===== MockMvc 请求辅助（入口级触发，统一带 _appId）=====

    /** GET 请求（params 形如 {"viewerId","900000001"} 的键值对）。 */
    protected MvcResult doGet(String path, String... params) throws Exception {
        return mockMvc.perform(withParams(get(path), params)).andExpect(status().isOk()).andReturn();
    }

    /** POST JSON 请求。 */
    protected MvcResult doPostJson(String path, String jsonBody, String... params) throws Exception {
        return mockMvc.perform(withParams(post(path), params).contentType(MediaType.APPLICATION_JSON).content(jsonBody))
                .andExpect(status().isOk()).andReturn();
    }

    /** DELETE 请求。 */
    protected MvcResult doDelete(String path, String... params) throws Exception {
        return mockMvc.perform(withParams(delete(path), params)).andExpect(status().isOk()).andReturn();
    }

    /** multipart 上传请求。 */
    protected MvcResult doUpload(MockMultipartHttpServletRequestBuilder builder) throws Exception {
        return mockMvc.perform(builder.param("_appId", APP_ID)).andExpect(status().isOk()).andReturn();
    }

    /** multipart 上传请求构造器（/api/images/upload）。 */
    protected static MockMultipartHttpServletRequestBuilder uploadBuilder() {
        return multipart("/api/images/upload");
    }

    private static MockHttpServletRequestBuilder withParams(MockHttpServletRequestBuilder builder, String... params) {
        builder.param("_appId", APP_ID);
        for (int i = 0; i + 1 < params.length; i += 2) {
            builder.param(params[i], params[i + 1]);
        }
        return builder;
    }

    // ===== 响应/DB 断言辅助 =====

    /** 响应体字符串（UTF-8；UTF-8 恒被支持，受检异常转运行时简化调用方）。 */
    protected static String bodyOf(MvcResult result) {
        try {
            return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        } catch (java.io.UnsupportedEncodingException e) {
            throw new IllegalStateException("UTF-8 charset unavailable", e);
        }
    }

    /** 响应业务码（$.code）。 */
    protected static int codeOf(MvcResult result) {
        return JsonPathUtils.readInt(bodyOf(result), "$.code");
    }

    /** 响应 data 内数值字段。 */
    protected static long longAt(MvcResult result, String jsonPath) {
        return JsonPathUtils.readNumber(bodyOf(result), jsonPath).longValue();
    }

    /** 响应 data 内数值数组（如 $.data[*].postId / $.data.items[*].postId）。 */
    protected static List<Long> longListAt(MvcResult result, String jsonPath) {
        List<Number> raw = JsonPathUtils.readNumberList(bodyOf(result), jsonPath);
        List<Long> values = new ArrayList<>(raw.size());
        for (Number number : raw) {
            values.add(number.longValue());
        }
        return values;
    }

    /** 响应 data 内字符串字段。 */
    protected static String strAt(MvcResult result, String jsonPath) {
        return JsonPathUtils.readString(bodyOf(result), jsonPath);
    }

    /** 响应 data 内布尔字段（如 $.data.hasMore）。 */
    protected static boolean boolAt(MvcResult result, String jsonPath) {
        return Boolean.TRUE.equals(JsonPathUtils.readNullable(bodyOf(result), jsonPath));
    }

    /** 响应 data 内字符串数组（如 $.data.imageUrls）。 */
    protected static List<String> strListAt(MvcResult result, String jsonPath) {
        return JsonPathUtils.readStringList(bodyOf(result), jsonPath);
    }

    /** 响应 data 是否为 null（删除类接口 data=null 断言用；路径缺失也按 null 处理）。 */
    protected static boolean dataIsNull(MvcResult result) {
        return JsonPathUtils.readNullable(bodyOf(result), "$.data") == null;
    }

    /** SQL COUNT 兜 null 取整。 */
    protected static int countOf(JdbcTemplate jdbcTemplate, String sql, Object... args) {
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, args);
        return count == null ? 0 : count;
    }

    /**
     * 基线帖隔离过滤（诊断防线）：从响应列表中剔除基线六帖之外的外部帖（保留段迁移后正常应为空，
     * 出现即说明自造残留泄漏——warn 记录 postId 供人工复核来源），仅对基线帖断言相对顺序
     * （断言语义不变——顺序口径仍是 createTime DESC + 同秒 id DESC）。
     */
    protected static List<Long> filterBaselinePosts(List<Long> ids) {
        List<Long> foreign = ids.stream()
                .filter(id -> !BASELINE_POST_IDS.contains(id))
                .collect(java.util.stream.Collectors.toList());
        if (!foreign.isEmpty()) {
            log.warn("列表/Feed 响应中检测到基线外帖子（自造残留泄漏，断言已隔离，请复核来源）：{}", foreign);
        }
        return ids.stream().filter(BASELINE_POST_IDS::contains).collect(java.util.stream.Collectors.toList());
    }
}
