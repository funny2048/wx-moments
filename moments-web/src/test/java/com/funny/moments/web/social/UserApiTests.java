package com.funny.moments.web.social;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

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

import com.funny.moments.common.enums.GenderEnum;
import com.funny.moments.common.utils.DateUtils;
import com.funny.moments.web.social.support.SocialTestSupport;

import lombok.extern.slf4j.Slf4j;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * 用户域接口集成测试（入口1：GET /api/users；入口2：GET /api/users/{userId}，TC001-TC008）。
 * 写法要点：MockMvc 入口级直调（Controller→Service→Mapper 真实链路，不 mock 内部依赖）；
 * 自造数据 + @Transactional 回滚；断言含查库核对。
 *
 * @Author: tester（阶段六）
 * @Date: 2026-09-21
 */
@Slf4j
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
public class UserApiTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DataSource dataSource;

    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(dataSource);
    }

    /**
     * TC001 分页主干：分页参数全缺省（pageNo=1/pageSize=20）+ 出参字段完整性。
     * 规则1 code=0 且 total=库内用户总数（is_del=0，与查库一致）
     * 规则2 users 条数 = min(20, total)
     * 规则3 字段齐全：genderStr 与 gender 枚举映射一致、createTimeStr 匹配 yyyy-MM-dd HH:mm:ss、avatar /images/ 前缀
     * 口径说明：用例前置"基线 B（用户≥100）"在共享 dev 库不可强造（禁依赖存量/禁清库），
     * 自造 25 用户补足观察面；字段断言覆盖返回页全部行（dev 存量用户为 mock 生成，字段形态一致）。
     */
    @Test
    public void listUsersDefaultPage() throws Exception {
        // 自造 25 用户（性别 0/1/2 轮转，avatar /images/ 前缀），与本事务同生命周期回滚
        for (int i = 1; i <= 25; i++) {
            jdbc.update("INSERT INTO user (nickname, avatar, gender, city, is_del) VALUES (?, ?, ?, ?, 0)",
                    "IT用户" + String.format("%02d", i), "/images/it_avatar_" + String.format("%02d", i) + ".png",
                    i % 3, "杭州");
        }
        long totalInDb = SocialTestSupport.toLong(
                jdbc.queryForObject("SELECT COUNT(*) FROM user WHERE is_del = 0", Long.class));

        MvcResult result = performGet("/api/users?_appId=it-test");
        assertEquals(0, SocialTestSupport.codeOf(result), "分页缺省请求应 code=0，resp=" + SocialTestSupport.bodyUtf8(result));

        Map<String, Object> page = SocialTestSupport.dataMapOf(result);
        assertEquals(totalInDb, SocialTestSupport.toLong(page.get("total")), "total 应等于库内用户总数");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> users = (List<Map<String, Object>>) page.get("users");
        assertNotNull(users, "users 禁 null");
        assertEquals(Math.min(20, totalInDb), users.size(), "缺省 pageSize=20");
        for (Map<String, Object> user : users) {
            Integer gender = SocialTestSupport.toInt(user.get("gender"));
            GenderEnum genderEnum = GenderEnum.byCode(gender);
            assertNotNull(genderEnum, "gender 取值应合法，gender=" + gender);
            assertEquals(genderEnum.getDesc(), user.get("genderStr"), "genderStr 应与 gender 枚举映射一致");
            assertTrue(DateUtils.isValidDateHHMMSS(String.valueOf(user.get("createTimeStr"))),
                    "createTimeStr 应匹配 yyyy-MM-dd HH:mm:ss：" + user.get("createTimeStr"));
            assertTrue(String.valueOf(user.get("avatar")).startsWith("/images/"),
                    "avatar 应为 /images/ 前缀：" + user.get("avatar"));
        }
    }

    /**
     * TC002 pageSize 边界与超大页码：1/500 边界 + 空页合法。
     * 规则1 pageSize=500 → code=0 且条数 ≤500（=min(500,total)）
     * 规则2 pageSize=1 → 条数 ≤1
     * 规则3 pageNo=1000000 → code=0 且 users=[]（页码超界返回空页不报错）
     */
    @Test
    public void listUsersPageSizeBoundaryAndEmptyPage() throws Exception {
        long totalInDb = SocialTestSupport.toLong(
                jdbc.queryForObject("SELECT COUNT(*) FROM user WHERE is_del = 0", Long.class));

        MvcResult r1 = performGet("/api/users?_appId=it-test&pageNo=1&pageSize=500");
        assertEquals(0, SocialTestSupport.codeOf(r1), "pageSize=500 上界应成功");
        List<Map<String, Object>> users1 = SocialTestSupport.userListOf(r1);
        assertTrue(users1.size() <= 500, "条数应 ≤500");
        assertEquals((int) Math.min(500, totalInDb), users1.size(), "pageSize=500 应返回全量（≤500）");

        MvcResult r2 = performGet("/api/users?_appId=it-test&pageNo=1&pageSize=1");
        assertEquals(0, SocialTestSupport.codeOf(r2), "pageSize=1 下界应成功");
        assertTrue(SocialTestSupport.userListOf(r2).size() <= 1, "条数应 ≤1");

        MvcResult r3 = performGet("/api/users?_appId=it-test&pageNo=1000000&pageSize=20");
        assertEquals(0, SocialTestSupport.codeOf(r3), "超大页码应返回空页而非报错");
        assertEquals(0, SocialTestSupport.userListOf(r3).size(), "超大页码 users 应为空数组");
    }

    /**
     * TC003 分页参数无效合并：pageNo=0/-1/abc + pageSize=0/501/abc 共 6 笔。
     * 规则1 6 笔均 code=1001（含 NB1：非数字由 TypeMismatch 绑定层承载转 1001 而非框架 100）
     */
    @Test
    public void listUsersInvalidParams() throws Exception {
        String[] invalidUrls = {
                "/api/users?_appId=it-test&pageNo=0",
                "/api/users?_appId=it-test&pageNo=-1",
                "/api/users?_appId=it-test&pageNo=abc",
                "/api/users?_appId=it-test&pageSize=0",
                "/api/users?_appId=it-test&pageSize=501",
                "/api/users?_appId=it-test&pageSize=abc"};
        for (String url : invalidUrls) {
            MvcResult result = performGet(url);
            assertEquals(1001, SocialTestSupport.codeOf(result),
                    "无效分页参数应 1001，url=" + url + "，resp=" + SocialTestSupport.bodyUtf8(result));
        }
    }

    /**
     * TC004 空集行为：无可匹配数据时返回结构合法（code=0 / users=[] / 禁 null）。
     * 规则1 pageNo 超出数据范围 → code=0 且 users 为空数组非 null
     * 口径说明：用例前置"8 表无业务数据（clear 后）"与共享 dev 库存量数据互斥（禁止清空共享库），
     * 以"页码超界空页"承载空集契约；total=0 的字面断言需空表环境，已在 test-mapping-part1.md 备注。
     */
    @Test
    public void listUsersEmptyResultStructure() throws Exception {
        MvcResult result = performGet("/api/users?_appId=it-test&pageNo=1000000&pageSize=20");
        assertEquals(0, SocialTestSupport.codeOf(result), "空结果集应 code=0");
        Map<String, Object> page = SocialTestSupport.dataMapOf(result);
        assertNotNull(page, "data 禁 null");
        Object users = page.get("users");
        assertNotNull(users, "users 字段禁 null");
        assertEquals(0, ((List<?>) users).size(), "空结果集 users 应为空数组");
    }

    /**
     * TC005 主干详情：单对象全字段。
     * 规则1 code=0；userId/nickname/gender/genderStr/city 与造数一致
     * 规则2 createTimeStr 匹配 yyyy-MM-dd HH:mm:ss（造数可控时间 2026-09-20 09:00:00）
     */
    @Test
    public void getUserDetail() throws Exception {
        jdbc.update("INSERT INTO user (id, nickname, avatar, gender, city, created_stime, is_del) "
                + "VALUES (?, ?, ?, ?, ?, ?, 0)", SocialTestSupport.USER_LISI_FOR_DETAIL, "李四",
                "/images/ph_avatar_02.png", 1, "上海", "2026-09-20 09:00:00");

        MvcResult result = performGet("/api/users/" + SocialTestSupport.USER_LISI_FOR_DETAIL + "?_appId=it-test");
        assertEquals(0, SocialTestSupport.codeOf(result), "存在用户详情应 code=0");
        Map<String, Object> data = SocialTestSupport.dataMapOf(result);
        assertEquals(SocialTestSupport.USER_LISI_FOR_DETAIL, SocialTestSupport.toLong(data.get("userId")), "userId");
        assertEquals("李四", data.get("nickname"), "nickname");
        assertEquals(1, SocialTestSupport.toInt(data.get("gender")), "gender");
        assertEquals("男", data.get("genderStr"), "genderStr");
        assertEquals("上海", data.get("city"), "city");
        assertEquals("2026-09-20 09:00:00", data.get("createTimeStr"), "createTimeStr 与造数时间一致");
    }

    /**
     * TC006 userId 无效合并：0 / -1 / abc。
     * 规则1 均返回 1001（路径参数非数字走 TypeMismatch 承载，NB1）
     */
    @Test
    public void getUserInvalidUserId() throws Exception {
        String[] invalidPaths = {"/api/users/0?_appId=it-test", "/api/users/-1?_appId=it-test",
                "/api/users/abc?_appId=it-test"};
        for (String url : invalidPaths) {
            MvcResult result = performGet(url);
            assertEquals(1001, SocialTestSupport.codeOf(result),
                    "userId 无效应 1001，url=" + url + "，resp=" + SocialTestSupport.bodyUtf8(result));
        }
    }

    /**
     * TC007 用户不存在。
     * 规则1 不存在 userId → code=1002
     * 口径说明：用"当前最大 id+100000"替代字面 999999，防 dev 数据增长后撞真实 id（语义等同：必然不存在）。
     */
    @Test
    public void getUserNotFound() throws Exception {
        long nonExisting = SocialTestSupport.nonExistingId(jdbc, "user");
        MvcResult result = performGet("/api/users/" + nonExisting + "?_appId=it-test");
        assertEquals(1002, SocialTestSupport.codeOf(result), "不存在用户应 1002，userId=" + nonExisting);
    }

    /**
     * TC008 已软删用户：is_del=1 全过滤（含已删除语义）。
     * 规则1 软删用户查询 → code=1002
     * 规则2 DB 核对：该用户 is_del=1 行仍在（软删非物理删，事务回滚后恢复）
     */
    @Test
    public void getUserSoftDeleted() throws Exception {
        jdbc.update("INSERT INTO user (id, nickname, avatar, gender, city, created_stime, is_del) "
                + "VALUES (?, ?, ?, ?, ?, ?, 0)", SocialTestSupport.USER_QIANQI_FOR_SOFTDEL, "钱七",
                "/images/ph_avatar_05.png", 2, "深圳", "2026-09-20 09:00:00");
        jdbc.update("UPDATE user SET is_del = 1 WHERE id = ?", SocialTestSupport.USER_QIANQI_FOR_SOFTDEL);

        MvcResult result = performGet("/api/users/" + SocialTestSupport.USER_QIANQI_FOR_SOFTDEL + "?_appId=it-test");
        assertEquals(1002, SocialTestSupport.codeOf(result), "软删用户应视为不存在 1002");
        Integer isDel = jdbc.queryForObject("SELECT is_del FROM user WHERE id = ?",
                Integer.class, SocialTestSupport.USER_QIANQI_FOR_SOFTDEL);
        assertEquals(1, isDel == null ? 0 : isDel, "DB 核对：软删行保留且 is_del=1");
    }

    /** GET 请求统一入口（默认 UTF-8 断言走响应字节） */
    private MvcResult performGet(String url) throws Exception {
        MockHttpServletRequestBuilder builder = get(url).contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON);
        return mockMvc.perform(builder).andReturn();
    }
}
