package com.funny.moments.web.social;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

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

import com.funny.framework.redis.RedisClient;
import com.funny.moments.common.consts.CacheKeyConsts;
import com.funny.moments.service.social.cache.FriendIdsCacheManager;
import com.funny.moments.service.social.cache.FriendTagCacheManager;
import com.funny.moments.web.social.support.SocialTestSupport;

import lombok.extern.slf4j.Slf4j;
import redis.clients.jedis.Jedis;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 模拟数据接口集成测试（入口11：POST /api/mock-data，TC073-TC082）。
 * 写法要点：generate 内部分批提交的写操作在 @Transactional 测试事务内统一回滚（连 clear 的 8 表软删一并回滚，
 * 不破坏 dev 存量数据）；观测型断言（分布容差）软校验不计红灯；
 * TC082① 并发用例 @Transactional 管不到并发线程，NOT_SUPPORTED 真实提交 + 按"造数 id 区间"清理
 * （MessageConsumeAsyncTests 的 AFTER 按业务主键清理口径：区间内仅含本用例新生成的自增 id 行）。
 *
 * @Author: tester（阶段六）
 * @Date: 2026-09-21
 */
@Slf4j
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
public class MockDataApiTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private RedisClient<Jedis> redisClient;

    @Autowired
    private FriendIdsCacheManager friendIdsCacheManager;

    @Autowired
    private FriendTagCacheManager friendTagCacheManager;

    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(dataSource);
    }

    @AfterEach
    void tearDown() {
        friendIdsCacheManager.evictAll();
        friendTagCacheManager.evictAll();
    }

    /**
     * TC073 主干：全缺省默认配置 + 统计出参 + DB 核对。
     * 规则1 code=0；userCount=100、tagCount=800（默认 8×100）、postCount=0、imagePoolSize=40、
     * friendshipRecords=friendshipPairs×2（C11 对称双记录）
     * 规则2 DB 核对：user +100、friend_tag +800、post +0（事务内可见，随回滚还原）
     * 口径说明：用例前置"空库"与共享 dev 存量互斥，断言改为"增量=生成数"（语义等同且更强）。
     */
    @Test
    public void generateDefaultConfig() throws Exception {
        long userBefore = countAlive("user");
        long tagBefore = countAlive("friend_tag");
        long postBefore = countAlive("post");

        MvcResult result = postMockData("{}");
        assertEquals(0, SocialTestSupport.codeOf(result), "全缺省生成应成功，resp=" + SocialTestSupport.bodyUtf8(result));
        Map<String, Object> data = SocialTestSupport.dataMapOf(result);
        assertEquals(100, SocialTestSupport.toInt(data.get("userCount")), "默认 userCount=100");
        assertEquals(800, SocialTestSupport.toInt(data.get("tagCount")), "默认 tagCount=8×100");
        assertEquals(0, SocialTestSupport.toInt(data.get("postCount")), "默认 postCount=0 不生成帖子");
        assertEquals(40, SocialTestSupport.toInt(data.get("imagePoolSize")), "占位图池 20+20=40");
        int pairs = SocialTestSupport.toInt(data.get("friendshipPairs"));
        int records = SocialTestSupport.toInt(data.get("friendshipRecords"));
        assertEquals(records, pairs * 2, "friendshipRecords 应=对数×2");

        assertEquals(userBefore + 100, countAlive("user"), "DB 核对：user +100");
        assertEquals(tagBefore + 800, countAlive("friend_tag"), "DB 核对：friend_tag +800");
        assertEquals(postBefore, countAlive("post"), "DB 核对：post 0 新增");
    }

    /**
     * TC074 1000+ 好友关系验收：对称性/自环/防重三断言（R5/C11）。
     * 规则1 code=0 且 friendship 表本批记录 ≈1000：容差 ±15% 观测软校验（不计红灯），<850 判红灯复核
     * 规则2 DB 断言：①∀记录(A,B) 存在对称 (B,A) ②无 A=B 自环 ③无 (user_id,friend_user_id) 重复
     * 口径说明：结构性断言取"本批生成行"（id > 生成前 max id），不受 dev 存量数据影响。
     */
    @Test
    public void generateFriendshipSymmetry() throws Exception {
        long friendshipMaxBefore = maxId("friendship");

        MvcResult result = postMockData("{\"userCount\":100,\"avgFriendsPerUser\":10}");
        assertEquals(0, SocialTestSupport.codeOf(result), "生成应成功");
        Map<String, Object> data = SocialTestSupport.dataMapOf(result);
        int records = SocialTestSupport.toInt(data.get("friendshipRecords"));

        // 观测软校验：≈1000 ±15%（avg±3 抖动），<850 硬失败转人工复核
        log.info("TC074 观测：本批 friendship 记录数={}（期望≈1000，容差±15%，观测不计红灯）", records);
        assertTrue(records >= 850, "本批好友关系记录低于 850 下限（" + records + "），判红灯转人工复核");

        Long asymmetric = jdbc.queryForObject(
                "SELECT COUNT(*) FROM friendship f LEFT JOIN friendship b "
                        + "ON b.user_id = f.friend_user_id AND b.friend_user_id = f.user_id AND b.is_del = 0 "
                        + "WHERE f.is_del = 0 AND f.id > ? AND b.id IS NULL", Long.class, friendshipMaxBefore);
        assertEquals(0L, asymmetric == null ? 0L : asymmetric, "规则①：∀(A,B) 必存在对称 (B,A)");

        Long selfLoop = jdbc.queryForObject(
                "SELECT COUNT(*) FROM friendship WHERE user_id = friend_user_id AND is_del = 0 AND id > ?",
                Long.class, friendshipMaxBefore);
        assertEquals(0L, selfLoop == null ? 0L : selfLoop, "规则②：无自环记录");

        Long duplicated = jdbc.queryForObject(
                "SELECT COUNT(*) FROM (SELECT user_id, friend_user_id FROM friendship "
                        + "WHERE is_del = 0 AND id > ? GROUP BY user_id, friend_user_id HAVING COUNT(*) > 1) d",
                Long.class, friendshipMaxBefore);
        assertEquals(0L, duplicated == null ? 0L : duplicated, "规则③：无重复 (user_id, friend_user_id)");
    }

    /**
     * TC075 标签分布权重容差（explore 规则⑦，观测型软校验，不计红灯）。
     * 规则1 主标签权重 同事25/同学20/朋友25/家人5/亲戚5/球友10/客户5/其他5，各 ±5pp 容差（软校验）
     * 规则2 另存在多标签好友样本 >0（30% 次标签机制，软校验）
     * 口径说明：绑定落库后无法区分主/次标签（同一张表），分母采用响应 bindingCount（全部绑定数，
     * 含 30% 次标签）——该口径下含次标签的计数与权重期望收敛一致；分母口径差异与违规计数均记入映射备注。
     */
    @Test
    public void generateTagDistributionObserve() throws Exception {
        long relationMaxBefore = maxId("friend_tag_relation");
        long tagMaxBefore = maxId("friend_tag");

        MvcResult result = postMockData("{\"userCount\":100,\"avgFriendsPerUser\":10}");
        assertEquals(0, SocialTestSupport.codeOf(result), "生成应成功");
        int bindingCount = SocialTestSupport.toInt(SocialTestSupport.dataMapOf(result).get("bindingCount"));
        assertTrue(bindingCount > 0, "应产生绑定数据供观测");

        Map<String, Double> expected = new HashMap<>(16);
        expected.put("同事", 0.25);
        expected.put("同学", 0.20);
        expected.put("朋友", 0.25);
        expected.put("家人", 0.05);
        expected.put("亲戚", 0.05);
        expected.put("球友", 0.10);
        expected.put("客户", 0.05);
        expected.put("其他", 0.05);

        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT t.tag_name AS tagName, COUNT(*) AS cnt FROM friend_tag_relation r "
                        + "JOIN friend_tag t ON t.id = r.tag_id AND t.is_del = 0 "
                        + "WHERE r.is_del = 0 AND r.id > ? AND t.id > ? GROUP BY t.tag_name",
                relationMaxBefore, tagMaxBefore);

        int violations = 0;
        long totalObserved = 0;
        for (Map<String, Object> row : rows) {
            String tagName = String.valueOf(row.get("tagName"));
            int cnt = row.get("cnt") == null ? 0 : ((Number) row.get("cnt")).intValue();
            totalObserved += cnt;
            Double expect = expected.get(tagName);
            if (expect == null) {
                log.warn("TC075 观测到权重表外标签：{} x{}（不计红灯，需人工复核）", tagName, cnt);
                continue;
            }
            double actual = (double) cnt / bindingCount;
            if (Math.abs(actual - expect) > 0.05) {
                violations++;
                log.warn("TC075 标签[{}]占比 {}% 超出期望 {}% ±5pp（观测软校验不计红灯）",
                        tagName, String.format("%.2f", actual * 100), expect * 100);
            } else {
                log.info("TC075 标签[{}]占比 {}%（期望 {}%，±5pp 内）", tagName,
                        String.format("%.2f", actual * 100), expect * 100);
            }
        }
        log.info("TC075 分布观测汇总：bindingCount={}，观测绑定行={}，超容差项={}（软校验，不计红灯）",
                bindingCount, totalObserved, violations);

        Long multiTagFriends = jdbc.queryForObject(
                "SELECT COUNT(*) FROM (SELECT r.friend_user_id FROM friend_tag_relation r "
                        + "JOIN friend_tag t ON t.id = r.tag_id AND t.is_del = 0 "
                        + "WHERE r.is_del = 0 AND r.id > ? AND t.id > ? "
                        + "GROUP BY r.friend_user_id HAVING COUNT(DISTINCT r.tag_id) > 1) m",
                Long.class, relationMaxBefore, tagMaxBefore);
        if (multiTagFriends == null || multiTagFriends == 0) {
            log.warn("TC075 未观测到多标签好友样本（30% 次标签机制，概率性为 0，软校验不计红灯）");
        } else {
            log.info("TC075 多标签好友样本数={}（30% 次标签机制生效）", multiTagFriends);
        }
    }

    /**
     * TC076 clear=true 清空重建：8 表旧数据全部软删 + 缓存版本失效 + 新数据生成。
     * 规则1 code=0；DB 逐表断言：旧业务数据（含 dev 存量）is_del=1、is_del=0 恰为新生成数据
     * 规则2 缓存失效：moments:fver 版本号 INCR（clear 段完成后即失效，建议7 中断恢复语义）
     * 规则3 预热过的旧视角再查好友列表返回空（读路径回源新数据，非旧缓存值）
     * 口径说明：clear 在测试事务内执行并随回滚还原——dev 存量数据在用例结束后完好（事务隔离下断言有效）。
     */
    @Test
    public void generateWithClear() throws Exception {
        Long minExistingUser = jdbc.queryForObject(
                "SELECT MIN(id) FROM user WHERE is_del = 0", Long.class);
        long fverBefore = versionOf(CacheKeyConsts.CACHE_MOMENTS_FRIEND_VER);
        if (minExistingUser != null) {
            MvcResult warm = performGet("/api/friends?_appId=it-test&viewerId=" + minExistingUser);
            assertEquals(0, SocialTestSupport.codeOf(warm), "旧视角预热读应成功，viewerId=" + minExistingUser);
        }

        MvcResult result = postMockData("{\"userCount\":10,\"avgFriendsPerUser\":5,\"clear\":true}");
        assertEquals(0, SocialTestSupport.codeOf(result), "clear 重建应成功");
        Map<String, Object> data = SocialTestSupport.dataMapOf(result);
        assertEquals(10, SocialTestSupport.toInt(data.get("userCount")), "新数据 10 用户");
        int records = SocialTestSupport.toInt(data.get("friendshipRecords"));
        int bindingCount = SocialTestSupport.toInt(data.get("bindingCount"));

        assertEquals(10L, countAlive("user"), "8 表清空后 user 恰为新 10 人");
        if (minExistingUser != null) {
            Integer oldIsDel = jdbc.queryForObject("SELECT is_del FROM user WHERE id = ?",
                    Integer.class, minExistingUser);
            assertEquals(1, oldIsDel == null ? 0 : oldIsDel, "旧业务用户（id 最小存量）应 is_del=1");
        }
        assertEquals(records, countAlive("friendship"), "friendship 有效行=本批记录数");
        assertEquals(80L, countAlive("friend_tag"), "friend_tag 恰为新 10 人 ×8 标签");
        assertEquals(bindingCount, (int) countAlive("friend_tag_relation"), "relation 有效行=本批绑定数");
        assertEquals(0L, countAlive("post"), "post 无有效行");
        assertEquals(0L, countAlive("post_image"), "post_image 无有效行");
        assertEquals(0L, countAlive("post_visibility_tag"), "post_visibility_tag 无有效行");
        assertEquals(0L, countAlive("post_visibility_user"), "post_visibility_user 无有效行");

        long fverAfter = versionOf(CacheKeyConsts.CACHE_MOMENTS_FRIEND_VER);
        assertTrue(fverAfter > fverBefore,
                "moments:fver 应 INCR 失效（before=" + fverBefore + ", after=" + fverAfter + "）");

        if (minExistingUser != null) {
            MvcResult reread = performGet("/api/friends?_appId=it-test&viewerId=" + minExistingUser);
            assertEquals(0, SocialTestSupport.codeOf(reread), "旧视角再读应 code=0");
            assertEquals(0, SocialTestSupport.dataListOf(reread).size(), "旧视角（已软删）回源新数据应为空");
        }
    }

    /**
     * TC077 追加模式：clear 缺省 false，旧数据保留叠加。
     * 规则1 code=0；user 总数=旧 N（is_del=0 保留）+ 新 10
     * 规则2 存量最小 id 用户仍 is_del=0（旧数据不被软删）
     */
    @Test
    public void generateAppendMode() throws Exception {
        long userBefore = countAlive("user");
        Long minExistingUser = jdbc.queryForObject(
                "SELECT MIN(id) FROM user WHERE is_del = 0", Long.class);

        MvcResult result = postMockData("{\"userCount\":10}");
        assertEquals(0, SocialTestSupport.codeOf(result), "追加生成应成功");
        assertEquals(userBefore + 10, countAlive("user"), "追加语义：旧数据保留 + 新 10");

        if (minExistingUser != null) {
            Integer oldIsDel = jdbc.queryForObject("SELECT is_del FROM user WHERE id = ?",
                    Integer.class, minExistingUser);
            assertEquals(0, oldIsDel == null ? 1 : oldIsDel, "旧用户追加模式下应仍有效");
        }
    }

    /**
     * TC078 配置越界（1040 全字段合并）：4 字段×2 越界=8 笔。
     * 规则1 八笔均 code=1040（模拟配置专用码，区别于 1001）
     * 规则2 DB 核对：user 0 写入
     */
    @Test
    public void generateConfigOutOfRange() throws Exception {
        long userBefore = countAlive("user");
        String[] invalidBodies = {
                "{\"userCount\":0}", "{\"userCount\":100001}",
                "{\"avgFriendsPerUser\":-1}", "{\"avgFriendsPerUser\":501}",
                "{\"extraTagsPerUser\":-1}", "{\"extraTagsPerUser\":21}",
                "{\"postCount\":-1}", "{\"postCount\":10001}"};
        for (String body : invalidBodies) {
            MvcResult result = postMockData(body);
            assertEquals(1040, SocialTestSupport.codeOf(result),
                    "配置越界应 1040，body=" + body + "，resp=" + SocialTestSupport.bodyUtf8(result));
        }
        assertEquals(userBefore, countAlive("user"), "DB 核对：越界请求 0 写入");
    }

    /**
     * TC079 postCount 帖子生成：type 分布 + 可见性两表无放回抽样。
     * 规则1 code=0；DB 核对：post +50（本批）
     * 规则2 type 分布≈40/20/20/20（分母=本批生成帖子总数 50，容差 ±15pp，观测软校验不计红灯）
     * 规则3 可见性两表无 (post_id,tag_id)/(post_id,user_id) 重复（shuffle+subList 无放回，R2-建议5）
     */
    @Test
    public void generatePostsTypeDistribution() throws Exception {
        long postBefore = countAlive("post");
        long postMaxBefore = maxId("post");

        MvcResult result = postMockData("{\"userCount\":20,\"avgFriendsPerUser\":5,\"postCount\":50}");
        assertEquals(0, SocialTestSupport.codeOf(result), "帖子生成应成功");
        assertEquals(50, SocialTestSupport.toInt(SocialTestSupport.dataMapOf(result).get("postCount")),
                "出参 postCount=50");
        assertEquals(postBefore + 50, countAlive("post"), "DB 核对：post +50");

        // 观测软校验：type 分布（分母=本批 50 帖）
        Map<Integer, Double> expected = Map.of(1, 0.40, 2, 0.20, 3, 0.20, 4, 0.20);
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT visibility_type AS vt, COUNT(*) AS cnt FROM post WHERE is_del = 0 AND id > ? "
                        + "GROUP BY visibility_type", postMaxBefore);
        long generated = 0;
        for (Map<String, Object> row : rows) {
            generated += SocialTestSupport.toLong(row.get("cnt"));
        }
        assertEquals(50L, generated, "本批帖数应为 50");
        for (Map<String, Object> row : rows) {
            int type = SocialTestSupport.toInt(row.get("vt"));
            int cnt = SocialTestSupport.toInt(row.get("cnt"));
            double actual = (double) cnt / generated;
            if (Math.abs(actual - expected.get(type)) > 0.15) {
                log.warn("TC079 type={} 占比 {}% 超期望 {}% ±15pp（50 小样本观测软校验不计红灯）",
                        type, String.format("%.2f", actual * 100), expected.get(type) * 100);
            } else {
                log.info("TC079 type={} 占比 {}%（期望 {}%，±15pp 内）", type,
                        String.format("%.2f", actual * 100), expected.get(type) * 100);
            }
        }

        Long dupTag = jdbc.queryForObject(
                "SELECT COUNT(*) FROM (SELECT post_id, tag_id FROM post_visibility_tag WHERE is_del = 0 "
                        + "GROUP BY post_id, tag_id HAVING COUNT(*) > 1) d", Long.class);
        assertEquals(0L, dupTag == null ? 0L : dupTag, "规则③：post_visibility_tag 无重复");
        Long dupUser = jdbc.queryForObject(
                "SELECT COUNT(*) FROM (SELECT post_id, user_id FROM post_visibility_user WHERE is_del = 0 "
                        + "GROUP BY post_id, user_id HAVING COUNT(*) > 1) d", Long.class);
        assertEquals(0L, dupUser == null ? 0L : dupUser, "规则③：post_visibility_user 无重复");
    }

    /**
     * TC080 body 类型非法（R3 承载）：userCount 传字符串。
     * 规则1 code=1001（Jackson 反序列化失败经 Advice 转 1001，非框架 100）
     */
    @Test
    public void generateBodyTypeInvalid() throws Exception {
        MvcResult result = postMockData("{\"userCount\":\"abc\"}");
        assertEquals(1001, SocialTestSupport.codeOf(result),
                "body 类型非法应 1001，resp=" + SocialTestSupport.bodyUtf8(result));
    }

    /**
     * TC081 幂等-重复请求：追加=合法重复语义（C15）。
     * 规则1 同参 {"userCount":50} 二连发均 code=0
     * 规则2 user 累计 +100（追加语义幂等口径：合法重复，非去重）
     */
    @Test
    public void generateRepeatAppend() throws Exception {
        long userBefore = countAlive("user");
        MvcResult first = postMockData("{\"userCount\":50}");
        assertEquals(0, SocialTestSupport.codeOf(first), "第一次生成应成功");
        MvcResult second = postMockData("{\"userCount\":50}");
        assertEquals(0, SocialTestSupport.codeOf(second), "第二次生成应成功（追加语义）");
        assertEquals(userBefore + 100, countAlive("user"), "追加幂等口径：累计 +100");
    }

    /**
     * TC082① 幂等-并发：并发 2 笔 {"userCount":50}（分批事务独立提交）。
     * 规则1 两笔均 code=0（各自分批提交无死锁）
     * 规则2 user 累计 +100，统计各自正确
     * 口径说明：@Transactional 管不到并发线程，NOT_SUPPORTED 真实提交；
     * 清理按"造数 id 区间"（id > 各表生成前 max id 即本用例新增行，等价按业务主键清理口径）在 finally 执行；
     * TC082② 中断恢复（kill -9 + clear 重跑）为用例声明的手工验证项留痕，不设自动化方法。
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void generateConcurrent() throws Exception {
        long userBefore = countAlive("user");
        Map<String, Long> maxIds = snapshotMaxIds();
        CopyOnWriteArrayList<Integer> codes = new CopyOnWriteArrayList<>();
        try {
            int threads = 2;
            CountDownLatch ready = new CountDownLatch(threads);
            CountDownLatch start = new CountDownLatch(1);
            ExecutorService pool = Executors.newFixedThreadPool(threads);
            for (int i = 0; i < threads; i++) {
                pool.submit(() -> {
                    ready.countDown();
                    try {
                        start.await();
                        codes.add(SocialTestSupport.codeOf(postMockData("{\"userCount\":50}")));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } catch (Exception e) {
                        log.warn("并发生成执行异常", e);
                    }
                });
            }
            ready.await();
            start.countDown();
            pool.shutdown();
            assertTrue(pool.awaitTermination(120, TimeUnit.SECONDS), "并发生成应全部完成");

            log.info("TC082① 并发生成观察：codes={}", codes);
            assertTrue(codes.stream().allMatch(c -> c == 0), "两笔并发均应成功，codes=" + codes);
            assertEquals(userBefore + 100, countAlive("user"), "并发两笔累计 +100 用户");
        } finally {
            cleanupByIdRange(maxIds);
        }
    }

    // ==== 工具方法 ====

    private MvcResult postMockData(String body) throws Exception {
        return mockMvc.perform(post("/api/mock-data?_appId=it-test")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body.getBytes(StandardCharsets.UTF_8))
                .accept(MediaType.APPLICATION_JSON)).andReturn();
    }

    private MvcResult performGet(String url) throws Exception {
        return mockMvc.perform(get(url).accept(MediaType.APPLICATION_JSON)).andReturn();
    }

    /** 指定表 is_del=0 行数 */
    private long countAlive(String table) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE is_del = 0", Long.class);
        return count == null ? 0L : count;
    }

    /** 指定表当前最大自增 id（无行返回 0） */
    private long maxId(String table) {
        Long max = jdbc.queryForObject("SELECT COALESCE(MAX(id), 0) FROM " + table, Long.class);
        return max == null ? 0L : max;
    }

    /** 8 表生成前 max id 快照（TC082① 造数区间清理锚点） */
    private Map<String, Long> snapshotMaxIds() {
        Map<String, Long> maxIds = new HashMap<>(16);
        maxIds.put("user", maxId("user"));
        maxIds.put("friendship", maxId("friendship"));
        maxIds.put("friend_tag", maxId("friend_tag"));
        maxIds.put("friend_tag_relation", maxId("friend_tag_relation"));
        maxIds.put("post", maxId("post"));
        maxIds.put("post_image", maxId("post_image"));
        maxIds.put("post_visibility_user", maxId("post_visibility_user"));
        maxIds.put("post_visibility_tag", maxId("post_visibility_tag"));
        return maxIds;
    }

    /**
     * 按造数 id 区间清理（仅删 id 大于快照的行=本用例新生成行，不触碰 dev 存量）；
     * 从表先行，外键语义 cleanliness（表间无物理外键，习惯序）。
     */
    private void cleanupByIdRange(Map<String, Long> maxIds) {
        long userMax = maxIds.get("user");
        long tagMax = maxIds.get("friend_tag");
        long postMax = maxIds.get("post");
        jdbc.update("DELETE FROM post_visibility_tag WHERE id > ? OR post_id > ?",
                maxIds.get("post_visibility_tag"), postMax);
        jdbc.update("DELETE FROM post_visibility_user WHERE id > ? OR post_id > ?",
                maxIds.get("post_visibility_user"), postMax);
        jdbc.update("DELETE FROM post_image WHERE id > ? OR post_id > ?", maxIds.get("post_image"), postMax);
        jdbc.update("DELETE FROM post WHERE id > ?", postMax);
        jdbc.update("DELETE FROM friend_tag_relation WHERE id > ? OR tag_id > ? OR friend_user_id > ?",
                maxIds.get("friend_tag_relation"), tagMax, userMax);
        jdbc.update("DELETE FROM friend_tag WHERE id > ? OR user_id > ?", tagMax, userMax);
        jdbc.update("DELETE FROM friendship WHERE id > ? OR user_id > ? OR friend_user_id > ?",
                maxIds.get("friendship"), userMax, userMax);
        jdbc.update("DELETE FROM user WHERE id > ?", userMax);
        log.info("TC082① 造数区间清理完成（userMax={}, tagMax={}, postMax={}）", userMax, tagMax, postMax);
    }

    /** Redis 版本 key 当前值（缺省 0） */
    private long versionOf(String key) {
        String value = redisClient.get(key);
        return value == null ? 0L : Long.parseLong(value);
    }
}
