package com.funny.moments.web.social;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import jakarta.servlet.http.Cookie;

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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import com.funny.moments.common.utils.DateUtils;
import com.funny.moments.service.social.cache.FriendIdsCacheManager;
import com.funny.moments.service.social.cache.FriendTagCacheManager;
import com.funny.moments.web.social.support.SocialTestSupport;

import lombok.extern.slf4j.Slf4j;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 好友域接口集成测试（入口3：GET /api/friends，TC009-TC019；入口4：GET /api/friends/{userId}，TC020-TC024）。
 * viewerId 横切维度（query/Cookie/优先级/强校验）在本类全维度覆盖（TC009-TC014），其余接口引用不重复展开。
 * 写法要点：基线 A 种子五人组 @BeforeEach 直插（事务内回滚无痕）；MockMvc 入口级直调。
 *
 * @Author: tester（阶段六）
 * @Date: 2026-09-21
 */
@Slf4j
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
public class FriendshipApiTests {

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
        // 非事务残留兜底清理（本类全事务回滚，此处为空操作）；缓存版本 INCR 使测试写入的 Redis key 自然失效
        SocialTestSupport.cleanupBaseline(jdbc);
        friendIdsCacheManager.evictAll();
        friendTagCacheManager.evictAll();
    }

    /**
     * TC009 主干：好友列表与 tagNames 组装（有效等价类 + 多标签好友 + 无标签好友）。
     * 规则1 张三视角 data 含李四(tagNames=[同学])、王五(tagNames=[同事,朋友] 按标签 id 序)、赵六(tagNames=[])
     * 规则2 不含钱七（非好友）
     */
    @Test
    public void listFriendsWithTagNames() throws Exception {
        MvcResult result = performGet("/api/friends?_appId=it-test&viewerId=" + SocialTestSupport.ZHANGSAN);
        assertEquals(0, SocialTestSupport.codeOf(result), "好友列表应 code=0");

        Map<Long, Map<String, Object>> byId = SocialTestSupport.byUserId(SocialTestSupport.dataListOf(result));
        assertEquals(3, byId.size(), "张三应恰有 3 个好友（李四/王五/赵六）");
        assertEquals(List.of("同学"), SocialTestSupport.tagNamesOf(byId.get(SocialTestSupport.LISI)), "李四标签");
        assertEquals(List.of("同事", "朋友"), SocialTestSupport.tagNamesOf(byId.get(SocialTestSupport.WANGWU)),
                "王五标签按标签 id 序");
        assertEquals(List.of(), SocialTestSupport.tagNamesOf(byId.get(SocialTestSupport.ZHAOLIU)), "赵六无标签为空数组");
        assertFalse(byId.containsKey(SocialTestSupport.QIANQI), "非好友钱七不应出现");
    }

    /**
     * TC010 Cookie 视角生效：无 viewerId 参数时取 Cookie mockUserId。
     * 规则1 Cookie=张三 → 返回同 TC009 的张三视角好友集合
     */
    @Test
    public void listFriendsByCookie() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/friends?_appId=it-test")
                .cookie(new Cookie("mockUserId", String.valueOf(SocialTestSupport.ZHANGSAN)))
                .accept(MediaType.APPLICATION_JSON)).andReturn();
        assertEquals(0, SocialTestSupport.codeOf(result), "Cookie 视角应 code=0");
        Map<Long, Map<String, Object>> byId = SocialTestSupport.byUserId(SocialTestSupport.dataListOf(result));
        assertEquals(3, byId.size(), "Cookie 张三视角应 3 好友");
        assertTrue(byId.containsKey(SocialTestSupport.LISI), "应含李四");
        assertTrue(byId.containsKey(SocialTestSupport.ZHAOLIU), "应含赵六");
    }

    /**
     * TC011 query viewerId 优先于 Cookie：两者同时在场以参数为准。
     * 规则1 viewerId=张三 + Cookie=李四 → 返回张三视角（3 人），非李四视角（1 人张三）
     */
    @Test
    public void viewerParamOverridesCookie() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/friends?_appId=it-test&viewerId=" + SocialTestSupport.ZHANGSAN)
                .cookie(new Cookie("mockUserId", String.valueOf(SocialTestSupport.LISI)))
                .accept(MediaType.APPLICATION_JSON)).andReturn();
        assertEquals(0, SocialTestSupport.codeOf(result), "参数覆盖视角应 code=0");
        Map<Long, Map<String, Object>> byId = SocialTestSupport.byUserId(SocialTestSupport.dataListOf(result));
        assertEquals(3, byId.size(), "query 覆盖后应为张三视角 3 好友（李四视角仅张三 1 人）");
        assertTrue(byId.containsKey(SocialTestSupport.ZHAOLIU), "张三视角应含赵六（李四视角不含）");
    }

    /**
     * TC012 viewer 缺失：无参数无 Cookie → 1003。
     */
    @Test
    public void viewerMissing() throws Exception {
        MvcResult result = performGet("/api/friends?_appId=it-test");
        assertEquals(1003, SocialTestSupport.codeOf(result), "viewer 缺失应 1003");
    }

    /**
     * TC013 viewerId 显式非法-强校验（B3 四类合并）：abc / 0 / -1 / 19 位超 Long。
     * 规则1 合法 Cookie 在场仍均返回 1001，不静默回退 Cookie（显式参数错误显式暴露）
     */
    @Test
    public void viewerParamInvalidNoFallback() throws Exception {
        String[] invalidValues = {"abc", "0", "-1", "1234567890123456789"};
        for (String value : invalidValues) {
            MvcResult result = mockMvc.perform(get("/api/friends?_appId=it-test&viewerId=" + value)
                    .cookie(new Cookie("mockUserId", String.valueOf(SocialTestSupport.ZHANGSAN)))
                    .accept(MediaType.APPLICATION_JSON)).andReturn();
            assertEquals(1001, SocialTestSupport.codeOf(result),
                    "非法 viewerId 应 1001 不回退 Cookie，value=" + value
                            + "，resp=" + SocialTestSupport.bodyUtf8(result));
        }
    }

    /**
     * TC014 Cookie 脏值-宽松处理：mockUserId=xyz 视为未选择。
     * 规则1 无 viewerId 参数 + Cookie 脏值 → 1003（宽松不报 1001）
     */
    @Test
    public void cookieDirtyValueTreatedAsMissing() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/friends?_appId=it-test")
                .cookie(new Cookie("mockUserId", "xyz"))
                .accept(MediaType.APPLICATION_JSON)).andReturn();
        assertEquals(1003, SocialTestSupport.codeOf(result), "Cookie 脏值应视为未选择返回 1003");
    }

    /**
     * TC015 tagId 过滤命中：仅返回标签下好友，且 tagNames 仍为全量（过滤分支不缩水）。
     * 规则1 tagId=同事 → data 仅王五
     * 规则2 王五 tagNames 仍为全量 [同事,朋友]（建议6：tagNames 语义=该好友全量标签）
     */
    @Test
    public void listFriendsFilteredByTag() throws Exception {
        MvcResult result = performGet("/api/friends?_appId=it-test&viewerId=" + SocialTestSupport.ZHANGSAN
                + "&tagId=" + SocialTestSupport.TAG_COLLEAGUE);
        assertEquals(0, SocialTestSupport.codeOf(result), "tagId 过滤应 code=0");
        List<Map<String, Object>> data = SocialTestSupport.dataListOf(result);
        assertEquals(1, data.size(), "同事标签应仅命中王五");
        assertEquals(SocialTestSupport.WANGWU, SocialTestSupport.toLong(data.get(0).get("userId")), "命中好友为王五");
        assertEquals(List.of("同事", "朋友"), SocialTestSupport.tagNamesOf(data.get(0)),
                "过滤视图下 tagNames 应保持全量不缩水");
    }

    /**
     * TC016 tagId 过滤无命中：合法标签零绑定 → 空集非报错。
     * 规则1 创建"空组"标签（零绑定）后按其过滤 → code=0 且 data=[]
     */
    @Test
    public void listFriendsFilterNoBinding() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/api/friend-tags?_appId=it-test&viewerId="
                        + SocialTestSupport.ZHANGSAN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tagName\":\"空组\"}".getBytes())).andReturn();
        assertEquals(0, SocialTestSupport.codeOf(createResult), "创建空组标签应成功");
        long emptyTagId = SocialTestSupport.toLong(SocialTestSupport.dataMapOf(createResult).get("tagId"));

        MvcResult result = performGet("/api/friends?_appId=it-test&viewerId=" + SocialTestSupport.ZHANGSAN
                + "&tagId=" + emptyTagId);
        assertEquals(0, SocialTestSupport.codeOf(result), "合法标签零绑定应 code=0 而非报错");
        assertEquals(0, SocialTestSupport.dataListOf(result).size(), "零绑定标签过滤应返回空集");
    }

    /**
     * TC017 tagId 参数无效：0 / abc。
     * 规则1 均返回 1001（非数字走 TypeMismatch 承载，NB1）
     */
    @Test
    public void tagIdInvalid() throws Exception {
        MvcResult r1 = performGet("/api/friends?_appId=it-test&viewerId=" + SocialTestSupport.ZHANGSAN + "&tagId=0");
        assertEquals(1001, SocialTestSupport.codeOf(r1), "tagId=0 应 1001");
        MvcResult r2 = performGet("/api/friends?_appId=it-test&viewerId=" + SocialTestSupport.ZHANGSAN + "&tagId=abc");
        assertEquals(1001, SocialTestSupport.codeOf(r2), "tagId=abc 应 1001");
    }

    /**
     * TC018 tagId 不存在/非归属（1004 两分支合并）。
     * 规则1 张三用不存在 tagId → 1004
     * 规则2 李四用张三的标签（同事）→ 1004（水平越权防护）
     */
    @Test
    public void tagIdNotFoundOrNotOwner() throws Exception {
        long nonExistingTag = SocialTestSupport.nonExistingId(jdbc, "friend_tag");
        MvcResult r1 = performGet("/api/friends?_appId=it-test&viewerId=" + SocialTestSupport.ZHANGSAN
                + "&tagId=" + nonExistingTag);
        assertEquals(1004, SocialTestSupport.codeOf(r1), "标签不存在应 1004");

        MvcResult r2 = performGet("/api/friends?_appId=it-test&viewerId=" + SocialTestSupport.LISI
                + "&tagId=" + SocialTestSupport.TAG_COLLEAGUE);
        assertEquals(1004, SocialTestSupport.codeOf(r2), "非归属标签应 1004");
    }

    /**
     * TC019 无好友视角：钱七 → 空集。
     * 规则1 code=0 且 data=[]（禁 null）
     */
    @Test
    public void listFriendsEmptyView() throws Exception {
        MvcResult result = performGet("/api/friends?_appId=it-test&viewerId=" + SocialTestSupport.QIANQI);
        assertEquals(0, SocialTestSupport.codeOf(result), "无好友视角应 code=0");
        List<Map<String, Object>> data = SocialTestSupport.dataListOf(result);
        assertNotNull(data, "data 禁 null");
        assertEquals(0, data.size(), "无好友应返回空数组");
    }

    /**
     * TC020 主干：好友详情含标签。
     * 规则1 王五详情 userId/nickname 正确、tagNames=[同事,朋友]、createTimeStr 合法
     */
    @Test
    public void getFriendDetailWithTags() throws Exception {
        MvcResult result = performGet("/api/friends/" + SocialTestSupport.WANGWU
                + "?_appId=it-test&viewerId=" + SocialTestSupport.ZHANGSAN);
        assertEquals(0, SocialTestSupport.codeOf(result), "好友详情应 code=0");
        Map<String, Object> data = SocialTestSupport.dataMapOf(result);
        assertEquals(SocialTestSupport.WANGWU, SocialTestSupport.toLong(data.get("userId")), "userId");
        assertEquals("王五", data.get("nickname"), "nickname");
        assertEquals(List.of("同事", "朋友"), SocialTestSupport.tagNamesOf(data), "tagNames 按标签 id 序");
        assertTrue(DateUtils.isValidDateHHMMSS(String.valueOf(data.get("createTimeStr"))), "createTimeStr 格式合法");
    }

    /**
     * TC021 好友详情 userId 无效合并：0 / abc。
     * 规则1 均返回 1001（NB1 路径参数承载）
     */
    @Test
    public void getFriendDetailInvalidUserId() throws Exception {
        MvcResult r1 = performGet("/api/friends/0?_appId=it-test&viewerId=" + SocialTestSupport.ZHANGSAN);
        assertEquals(1001, SocialTestSupport.codeOf(r1), "userId=0 应 1001");
        MvcResult r2 = performGet("/api/friends/abc?_appId=it-test&viewerId=" + SocialTestSupport.ZHANGSAN);
        assertEquals(1001, SocialTestSupport.codeOf(r2), "userId=abc 应 1001");
    }

    /**
     * TC022 好友详情目标用户不存在 → 1002。
     */
    @Test
    public void getFriendDetailUserNotFound() throws Exception {
        long nonExisting = SocialTestSupport.nonExistingId(jdbc, "user");
        MvcResult result = performGet("/api/friends/" + nonExisting
                + "?_appId=it-test&viewerId=" + SocialTestSupport.ZHANGSAN);
        assertEquals(1002, SocialTestSupport.codeOf(result), "用户不存在应 1002");
    }

    /**
     * TC023 查询非好友用户：读不禁止语义。
     * 规则1 钱七非张三好友 → code=0、基础信息返回、tagNames=[]（接口不校验好友关系）
     */
    @Test
    public void getFriendDetailNonFriendUser() throws Exception {
        MvcResult result = performGet("/api/friends/" + SocialTestSupport.QIANQI
                + "?_appId=it-test&viewerId=" + SocialTestSupport.ZHANGSAN);
        assertEquals(0, SocialTestSupport.codeOf(result), "查询非好友用户应 code=0（读不禁止）");
        Map<String, Object> data = SocialTestSupport.dataMapOf(result);
        assertEquals(SocialTestSupport.QIANQI, SocialTestSupport.toLong(data.get("userId")), "userId");
        assertEquals("钱七", data.get("nickname"), "nickname");
        assertEquals(List.of(), SocialTestSupport.tagNamesOf(data), "无绑定 tagNames 为空数组");
    }

    /**
     * TC024 好友详情 viewer 缺失 → 1003（横切引用）。
     */
    @Test
    public void getFriendDetailViewerMissing() throws Exception {
        MvcResult result = performGet("/api/friends/" + SocialTestSupport.WANGWU + "?_appId=it-test");
        assertEquals(1003, SocialTestSupport.codeOf(result), "viewer 缺失应 1003");
    }

    /** GET 请求统一入口 */
    private MvcResult performGet(String url) throws Exception {
        MockHttpServletRequestBuilder builder = get(url).accept(MediaType.APPLICATION_JSON);
        return mockMvc.perform(builder).andReturn();
    }
}
