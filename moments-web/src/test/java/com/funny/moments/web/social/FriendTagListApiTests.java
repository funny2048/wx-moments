package com.funny.moments.web.social;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

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
import org.springframework.transaction.annotation.Transactional;

import com.funny.moments.common.utils.DateUtils;
import com.funny.moments.service.social.cache.FriendIdsCacheManager;
import com.funny.moments.service.social.cache.FriendTagCacheManager;
import com.funny.moments.web.social.support.SocialTestSupport;

import lombok.extern.slf4j.Slf4j;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * 标签列表接口集成测试（入口5：GET /api/friend-tags，TC025-TC028）。
 * 写法要点：基线 A 种子五人组；TC026 以直插重复绑定模拟并发窗口残留，验证展示侧去重兜底。
 *
 * @Author: tester（阶段六）
 * @Date: 2026-09-21
 */
@Slf4j
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
public class FriendTagListApiTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private FriendIdsCacheManager friendIdsCacheManager;

    @Autowired
    private FriendTagCacheManager friendTagCacheManager;

    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(dataSource);
        SocialTestSupport.seedBaselineA(jdbc);
    }

    @AfterEach
    void tearDown() {
        SocialTestSupport.cleanupBaseline(jdbc);
        friendIdsCacheManager.evictAll();
        friendTagCacheManager.evictAll();
    }

    /**
     * TC025 主干：标签列表与 friendCount。
     * 规则1 张三标签列表含 同学(friendCount=1)/同事(1)/朋友(1)（COUNT(DISTINCT) 口径）
     * 规则2 createdTimeStr 匹配 yyyy-MM-dd HH:mm:ss
     */
    @Test
    public void listTagsWithFriendCount() throws Exception {
        MvcResult result = performGet("/api/friend-tags?_appId=it-test&viewerId=" + SocialTestSupport.ZHANGSAN);
        assertEquals(0, SocialTestSupport.codeOf(result), "标签列表应 code=0");

        List<Map<String, Object>> data = SocialTestSupport.dataListOf(result);
        assertEquals(3, data.size(), "张三应恰有 3 个标签");
        Map<String, Map<String, Object>> byName = SocialTestSupport.byTagName(data);
        assertEquals(1, SocialTestSupport.toInt(byName.get("同学").get("friendCount")), "同学 friendCount");
        assertEquals(1, SocialTestSupport.toInt(byName.get("同事").get("friendCount")), "同事 friendCount");
        assertEquals(1, SocialTestSupport.toInt(byName.get("朋友").get("friendCount")), "朋友 friendCount");
        for (Map<String, Object> tag : data) {
            assertTrue(DateUtils.isValidDateHHMMSS(String.valueOf(tag.get("createdTimeStr"))),
                    "createdTimeStr 格式合法：" + tag.get("createdTimeStr"));
        }
    }

    /**
     * TC026 并发残留展示兜底：直插重复绑定 (12,王五) 第二条 is_del=0 后，计数与展示均不虚高。
     * 规则1 friendCount 仍=1（COUNT(DISTINCT friend_user_id)）
     * 规则2 好友列表王五 tagNames=[同事,朋友] 无重复（Stream.distinct）
     * 规则3 DB 核对：该组合 is_del=0 残留 2 行（造数还原并发窗口）
     */
    @Test
    public void duplicatedBindingDisplayDedup() throws Exception {
        jdbc.update("INSERT INTO friend_tag_relation (tag_id, friend_user_id, is_del) VALUES (?, ?, 0)",
                SocialTestSupport.TAG_COLLEAGUE, SocialTestSupport.WANGWU);

        MvcResult tagResult = performGet("/api/friend-tags?_appId=it-test&viewerId=" + SocialTestSupport.ZHANGSAN);
        assertEquals(0, SocialTestSupport.codeOf(tagResult), "标签列表应 code=0");
        Map<String, Map<String, Object>> byName = SocialTestSupport.byTagName(SocialTestSupport.dataListOf(tagResult));
        assertEquals(1, SocialTestSupport.toInt(byName.get("同事").get("friendCount")),
                "重复绑定残留下 friendCount 仍应为 1（COUNT DISTINCT）");

        MvcResult friendResult = performGet("/api/friends?_appId=it-test&viewerId=" + SocialTestSupport.ZHANGSAN);
        Map<Long, Map<String, Object>> byId = SocialTestSupport.byUserId(SocialTestSupport.dataListOf(friendResult));
        List<String> tagNames = SocialTestSupport.tagNamesOf(byId.get(SocialTestSupport.WANGWU));
        assertEquals(List.of("同事", "朋友"), tagNames, "tagNames 应去重无重复");

        Long residual = jdbc.queryForObject(
                "SELECT COUNT(*) FROM friend_tag_relation WHERE tag_id = ? AND friend_user_id = ? AND is_del = 0",
                Long.class, SocialTestSupport.TAG_COLLEAGUE, SocialTestSupport.WANGWU);
        assertEquals(2, residual == null ? 0 : residual.intValue(), "DB 核对：残留双行 is_del=0 在场");
    }

    /**
     * TC027 空标签用户：钱七 → 空集。
     * 规则1 code=0 且 data=[]
     */
    @Test
    public void listTagsEmptyUser() throws Exception {
        MvcResult result = performGet("/api/friend-tags?_appId=it-test&viewerId=" + SocialTestSupport.QIANQI);
        assertEquals(0, SocialTestSupport.codeOf(result), "空标签用户应 code=0");
        assertEquals(0, SocialTestSupport.dataListOf(result).size(), "空标签用户应返回空数组");
    }

    /**
     * TC028 viewer 缺失/非法：无 viewer → 1003；viewerId=abc → 1001（横切引用）。
     */
    @Test
    public void listTagsViewerInvalid() throws Exception {
        MvcResult r1 = performGet("/api/friend-tags?_appId=it-test");
        assertEquals(1003, SocialTestSupport.codeOf(r1), "viewer 缺失应 1003");
        MvcResult r2 = performGet("/api/friend-tags?_appId=it-test&viewerId=abc");
        assertEquals(1001, SocialTestSupport.codeOf(r2), "viewerId 非法应 1001");
    }

    /** GET 请求统一入口 */
    private MvcResult performGet(String url) throws Exception {
        return mockMvc.perform(get(url).accept(MediaType.APPLICATION_JSON)).andReturn();
    }
}
