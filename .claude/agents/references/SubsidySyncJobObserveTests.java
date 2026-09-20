// 模板样例【观测核对型 + 实时重算型】：查 dev 存量数据，只读 + log 观测 + 软校验计数
// 仅参考写法，包名/类名/业务语义按真实项目替换，禁止直接拷贝
package cn.com.example.activity.provider;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.annotation.Resource;
import javax.sql.DataSource;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import cn.com.example.activity.provider.dao.domain.autosuper.ActivityCarModel;
import cn.com.example.activity.provider.dao.mapper.autosuper.ActivityCarModelMapper;
import cn.com.example.gateway.HttpGateway;
import cn.com.example.gateway.apis.MarketingToolApi;
import cn.com.example.gateway.protocol.base.HttpProtocol;

/**
 * 同步补贴Job实际运行测试（连 dev 环境，手动触发）
 * 写法要点：全量 Job 直跑与落表核对均为观测形态——只读查库 log 输出 + 结构性规则软校验，
 * 违规计数 + warn + 汇总，不做硬断言（dev 存量数据波动不打红）
 */
@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest(classes = ProviderApplication.class)
@ActiveProfiles("job-dev")
public class SubsidySyncJobObserveTests {

    static {
        System.setProperty("env", "DEV");
    }

    @Resource
    private ISyncSubsidyService syncSubsidyService;

    @Resource
    private ActivityCarModelMapper activityCarModelMapper;

    @Resource
    @Qualifier("autosuperDataSource")
    private DataSource autosuperDataSource;

    @Resource
    private MarketingToolApi marketingToolApi;

    /**
     * 实际运行Job（等价 SyncSubsidyJob.doRun）：全量城市×专题车系拉补贴落表
     * 口径说明：全量 Job 属观测形态入口，真实落表不回滚；跑完用下方 verify 方法核对落表结果
     */
    @Test
    public void runSyncSubsidyJob() {
        syncSubsidyService.process();
    }

    /**
     * 抽查落表结果：取首个有数据的城市，逐行 log 输出观测字段
     * 观测型输出，不做硬断言
     */
    @Test
    public void verifySubsidyWritten() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(autosuperDataSource);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT activitySeriesId, subsidyType, subsidyAmount, specId, isShow "
                        + "FROM SubsidyDetail WITH (nolock) WHERE dataDimension=4 ORDER BY activitySeriesId LIMIT 50");
        log.info("落表抽查共 {} 行", rows.size());
        for (Map<String, Object> row : rows) {
            log.info("activitySeriesId={}, subsidyType={}, amount={}, specId={}, isShow={}",
                    row.get("activitySeriesId"), row.get("subsidyType"),
                    row.get("subsidyAmount"), row.get("specId"), row.get("isShow"));
        }
    }

    /**
     * 数据核对：三类补贴行（16=合计 / 17=地方 / 18=厂商）结构性规则软校验
     * 规则1 同一 activitySeriesId 的 16/17/18 三行 specId 应一致
     * 规则2 amount16 >= amount17 + amount18（纯 BUY 组合取等号）
     * 规则3 18 缺失/为 0 时需人工复核
     * 口径说明：违规计数 + warn + 末尾汇总，不做硬断言（存量数据受同步延迟影响）
     */
    @Test
    public void verifySubsidyRuleConsistency() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(autosuperDataSource);
        String sql = "WITH latest AS ("
                + "SELECT activitySeriesId, subsidyType, subsidyAmount, specId, isShow, "
                + "ROW_NUMBER() OVER (PARTITION BY activitySeriesId, subsidyType ORDER BY modified_stime DESC, id DESC) AS rn "
                + "FROM SubsidyDetail WITH (nolock) WHERE dataDimension=4 AND subsidyType IN (16,17,18)) "
                + "SELECT a.activitySeriesId AS activitySeriesId, a.seriesId AS seriesId, a.cityId AS cityId, "
                + "a.subsidyAmount AS amount16, b.subsidyAmount AS amount17, c.subsidyAmount AS amount18, "
                + "a.specId AS spec16, b.specId AS spec17, c.specId AS spec18 "
                + "FROM latest a "
                + "INNER JOIN latest b ON b.activitySeriesId=a.activitySeriesId AND b.subsidyType=17 AND b.rn=1 AND b.isShow=1 "
                + "LEFT JOIN latest c ON c.activitySeriesId=a.activitySeriesId AND c.subsidyType=18 AND c.rn=1 "
                + "WHERE a.subsidyType=16 AND a.rn=1 AND a.isShow=1 ORDER BY a.activitySeriesId";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);
        int specMismatch = 0;
        int sumViolation = 0;
        int missing18 = 0;
        for (Map<String, Object> row : rows) {
            int spec16 = toInt(row.get("spec16"));
            int spec17 = toInt(row.get("spec17"));
            int spec18 = toInt(row.get("spec18"));
            double amount16 = toDouble(row.get("amount16"));
            double amount17 = toDouble(row.get("amount17"));
            double amount18 = toDouble(row.get("amount18"));
            if (spec18 == 0 || row.get("amount18") == null) {
                missing18++;
                log.warn("activitySeriesId={} 无18记录，需人工复核", row.get("activitySeriesId"));
                continue;
            }
            if (spec16 != spec17 || spec16 != spec18) {
                specMismatch++;
                log.warn("activitySeriesId={} 三行specId不一致(16:{},17:{},18:{})，违反规则1",
                        row.get("activitySeriesId"), spec16, spec17, spec18);
            }
            if (amount16 + 0.5 < amount17 + amount18) {
                sumViolation++;
                log.warn("activitySeriesId={} 16({}) < 17({})+18({})，违反规则2",
                        row.get("activitySeriesId"), amount16, amount17, amount18);
            }
        }
        log.info("核对完成：共{}条，specId不一致{}条，16<17+18违规{}条，缺18记录{}条",
                rows.size(), specMismatch, sumViolation, missing18);
    }

    /**
     * 明细取证：拉指定 activitySeriesId 全部补贴行完整字段，用于分析违规成因
     */
    @Test
    public void dumpOneSeriesDetail() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(autosuperDataSource);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, subsidyType, subsidyAmount, specId, isShow, cityId, dataDimension, exjson, "
                        + "created_stime, modified_stime FROM SubsidyDetail WITH (nolock) "
                        + "WHERE activitySeriesId=? ORDER BY subsidyType, id", 1017920);
        for (Map<String, Object> row : rows) {
            log.info("row={}", row);
        }
    }

    /**
     * 实时重算比对：抽样调网关按 Job 同款口径重算，比对表内金额与 specId
     * 口径说明：独立抽样计数 + warn，不做硬断言（同步延迟亦会导致少量差异）
     */
    @Test
    public void verifyByRealtimeRecalc() throws Exception {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(autosuperDataSource);
        List<Map<String, Object>> samples = jdbcTemplate.queryForList(
                "WITH latest AS (SELECT activitySeriesId, activityId, seriesId, cityId, subsidyType, subsidyAmount, specId, "
                        + "ROW_NUMBER() OVER (PARTITION BY activitySeriesId, subsidyType ORDER BY modified_stime DESC, id DESC) AS rn "
                        + "FROM SubsidyDetail WITH (nolock) WHERE dataDimension=4 AND subsidyType=28) "
                        + "SELECT activitySeriesId, activityId, seriesId, cityId, subsidyAmount AS amount28, specId AS spec28 "
                        + "FROM latest WHERE rn=1 ORDER BY activitySeriesId LIMIT 15");
        int mismatch = 0;
        for (Map<String, Object> row : samples) {
            Thread.sleep(20); // 控频：外部网关 QPS 保护
            int activityId = toInt(row.get("activityId"));
            int seriesId = toInt(row.get("seriesId"));
            int cityId = toInt(row.get("cityId"));
            List<ActivityCarModel> carModels = activityCarModelMapper.getByActivityIdsAndSeriesId(Arrays.asList(activityId), seriesId);
            String specIds = carModels.stream().map(p -> String.valueOf(p.getSpecCode())).collect(Collectors.joining(","));
            HttpProtocol<SubsidyProtocal> protocol =
                    HttpGateway.execute(marketingToolApi.querySubsidy(cityId, seriesId, specIds));
            SubsidyDetailDto expect = syncSubsidyService.createMaxSubsidyDto(cityId, seriesId, protocol);
            boolean amountOk = toInt(row.get("amount28")) == expect.getAmount();
            boolean specOk = toInt(row.get("spec28")) == expect.getSpecId();
            if (!amountOk || !specOk) {
                mismatch++;
                log.warn("activitySeriesId={} 不一致：表(amount={},spec={}) vs 实时重算(amount={},spec={})",
                        row.get("activitySeriesId"), toInt(row.get("amount28")), toInt(row.get("spec28")),
                        expect.getAmount(), expect.getSpecId());
            } else {
                log.info("activitySeriesId={} 核对一致", row.get("activitySeriesId"));
            }
        }
        log.info("实时重算核对完成：抽查{}条，不一致{}条", samples.size(), mismatch);
    }

    private static int toInt(Object value) {
        return value == null ? 0 : ((Number) value).intValue();
    }

    private static double toDouble(Object value) {
        return value == null ? 0d : ((Number) value).doubleValue();
    }
}
