# 业务样例:报表开发(sales-report)

> 本文件是 executor 编码前的风格对齐参照,不是可编译源码;基础设施依赖为占位形态(见「占位依赖对照表」),裁剪处以 `……(省略)` 标注。

## 匹配特征

- 主标签:`聚合 SQL` / `多维度查询` / `图表 DTO(趋势/饼/柱)` / `查询结果缓存`
- 适用任务示例:数据看板/报表中心(明细表 + 趋势折线 + 地区柱状 + 品类饼图)、按月份范围的多维度聚合统计查询、T+1 刷数报表的查询缓存与写后失效、导出用全量明细查询

## 全链组成

```
Controller → Service(接口 + Impl,查询缓存在此层) → Mapper(接口 + XML);写入另走 Manager(事务层) → Mapper
```

纯读链路查询直连 Mapper 不绕 Manager;写入(初始化/导入/修正)走 Manager 事务方法。涉及文件(包 `com.example.demo` 下):

| 层 | 文件 |
|---|---|
| 入口 | controller/SalesReportController.java |
| 服务 | service/salereport/ISalesReportService.java、service/salereport/impl/SalesReportServiceImpl.java |
| 事务 | manager/ISalesReportManager.java、manager/impl/SalesReportManagerImpl.java |
| 数据 | dao/entity/SalesReportDO.java、dao/mapper/SalesReportMapper.java、resources/mapper/common/SalesReportMapper.xml |
| DTO | dto/in/SalesReportQueryIn.java、dto/out/SalesReportOut.java、SalesTrendOut.java、SalesRegionBarOut.java、SalesSeriesPieOut.java |
| 常量 | consts/BaseCacheKeyConsts.java(CACHE_SALES_REPORT 相关 key) |

## 占位依赖对照表

样例代码中的基础设施类均为**占位**(类名保留、包路径已省略),编码时必须替换为本项目实际等价物,禁止照抄;开源类可直接引入:

| 占位类 | 代表角色 | 编码时 |
|---|---|---|
| `ApiResult` | 统一返回包装 | 替换为本项目统一返回包装,保持 `succ / fail / buildFailure(code, msg)` 三态用法 |
| `ReturnCode` | 错误码枚举 | 替换为项目内错误码枚举,仅需 SUCCESS/FAIL 判等能力 |
| `RedisClient` | Redis 封装 | 替换为 RedisTemplate 封装,需具备 `get / setex(key, value, seconds) / del(String[])` 三个能力 |
| `CacheKeyConsts` | 缓存 TTL 常量(FIVE_MINUTE_SECOND) | 替换为项目内 TTL 常量,无则就地写 300 |
| `com.baomidou.mybatisplus.*`(IService/ServiceImpl/BaseMapper/@TableName/@TableId/@TableField/@TableLogic) | 开源·MyBatis-Plus | 可直接引入;纯 MyBatis 项目退化为生成的基础 Mapper |
| `com.github.pagehelper.*`(PageHelper/PageInfo) | 开源·PageHelper 物理分页 | 可直接引入 |
| `com.alibaba.fastjson`(JSON/TypeReference) | 开源·fastjson 序列化 | 可直接引入;或换 Jackson + TypeReference 等价物 |
| lombok / commons-lang3 / commons-collections4 / spring-beans / jakarta.annotation | 开源·工具与规范注解 | 直接引入 |

## 风格要点(看什么)

**分层契约**
1. Controller 查询接口固定四段:接参(GET 表单绑定 QueryIn,不写 @RequestBody)→ info 日志(参数 JSON 化,带 _appId)→ `checkQueryParam` 非 SUCCESS 直接透传 check → `ApiResult.succ(service.xxx())`;只读链路 Controller 不写 try-catch,异常兜底下沉到 Service 层
2. 校验独立成 `service.checkQueryParam(in)`,只校验"参数能组成合法查询"(入参 null、startMonth > endMonth),逐条不合法即 `return ApiResult.buildFailure(code, msg)` 不抛异常;不校验数据存在性——查不到就是空报表,不是错误
3. 报表域 Manager 只承担写入:batchInsert(单批上限 500 常量校验,超限抛 IllegalArgumentException 由调用方分批)与单条 update;读查询直连 Mapper 不绕 Manager;Manager 方法一律 `@Transactional(rollbackFor = Exception.class)`
4. ServiceImpl 继承 `ServiceImpl<Mapper, DO>` 并实现接口,同时注入 Mapper 做聚合查询;缓存读写全放 Service 层,事务层禁 Redis

**多维度查询入参**
5. 所有查询端点共用一个 QueryIn,维度分三类:精确月份 statMonth(与范围互斥,语义写进字段注释)、月份范围 startMonth/endMonth(含边界)、维度编码 regionCode/seriesCode;分页默认值 pageNum=1/pageSize=10 内置在 In 里
6. 月份用 Integer yyyyMM 数值类型,范围比较直接数值大小(SQL `>=`/`<=`,XML 写 `&gt;=` `&lt;=`),不引入字符串日期解析;XML 逐字段 `<if>` 判空拼 where,明细查询排序固定 `stat_month asc, region_code asc, series_code asc`,保证列表与导出顺序稳定
7. 缓存开关独立成 query 参数 `enableCache`(`defaultValue = "1"`)从 Controller 透传到 Service,排障时传 0 强制回源,不必清 Redis

**聚合 SQL 写法**
8. 一图一 SQL 一独立 resultMap:趋势 `group by stat_month`、地区柱状 `group by region_code, region_name`、车系饼图 `group by series_code, series_name, brand_name`;分组列连同冗余 name 列一起 group,兼容 ONLY_FULL_GROUP_BY
9. 聚合函数按展示语义选:量用 `sum(sales_volume)`、强度用 `round(avg(transaction_price), 2)`;饼图只要占比分母 total_volume 不带均价;聚合别名 total_volume/avg_price 与 resultMap column 一一对应
10. 每张图的 where 只保留对该图有意义的维度,分组维度自身不作为过滤条件:趋势不滤 statMonth、柱状不滤 regionCode、饼图不滤 seriesCode;排序即图表语义——趋势按时间列 asc(折线左→右),柱状/饼图按 total_volume desc(高→低);所有手写 SQL 带 `is_del = 0`

**图表 DTO 形态(趋势/饼/柱)**
11. 一图一 Out 类,按"业务 + 图型"命名 XxxTrendOut / XxxRegionBarOut / XxxSeriesPieOut;字段 = 分组维度列(编码 + 名称)+ 聚合值;聚合值类型 sum 出来的用 Long、avg 用 BigDecimal,不沿用 DO 的 Integer
12. 图表 Out 只放画图直接要的字段,不带分页/审计/格式化字段;统一 `@Getter @Setter + Serializable + serialVersionUID`
13. 明细 Out 与 DO 同构 + 衍生展示字段(transactionPriceW 万元均价);DO→Out 用 BeanUtils.copyProperties 打底再手工补衍生字段,除法前先判 null(`divide(WAN, 2, RoundingMode.HALF_UP)`)

**查询结果缓存**
14. key 按全维度拼接 `sales_report:图表:statMonth:startMonth:endMonth:regionCode:seriesCode` 5 个 `%s`,空值统一 `ALL` 占位(buildKey 里 null→ALL、blank→ALL),参数组合与 key 一一对应、可枚举可反拼
15. 统一泛型 `cacheGet(key, loader, TypeReference)`:miss → loader 回源 → setex 回写 TTL 5 分钟;hit → JSON 反序列化返回;Redis 任何异常 catch 住降级 loader 直读,只记 warn 不影响业务可用性
16. 分页明细不走缓存(分页参数颗粒度太细命中率低),只缓存全量明细 + 三类聚合结果;T+1 刷数场景 TTL 5 分钟覆盖一个浏览 session 即可,数据更新后自然收敛
17. 写后清理 `evictCacheByStatMonth(statMonth)`:按统计月份反拼两类 key——当月精确查询(statMonth 维)+ 恰好只覆盖当月的范围查询(start=end=statMonth 维)——四张图 × 2 key 一次批量 del;其余维度组合靠 TTL 收敛,不做模糊 scan
18. 清缓存接口暴露在 Service 上、由数据写入方在事务提交后调用(接口 javadoc 写明"事务内禁止操作 Redis");清理失败仅记 error 不抛

**通用纪律**
19. Service 每个查询方法 try-catch 兜底返回空对象(`new PageInfo<>()` / `new ArrayList<>()`),报表接口宁可用空数据也不出错误页;异常分支日志必须带异常对象 e
20. 批量插入用多行 VALUES `<foreach separator=",">`,is_del 直接写死 0 不在参数里传;缓存 key 模板与 TTL 集中在常量类,注释写明参数顺序与空值占位约定

## 代码

> 基础设施类(ApiResult/ReturnCode/RedisClient 等)的 import 已省略,按文首对照表替换为本项目等价类;业务包 `com.example.demo` 对应你项目自己的业务包;XML 的 `<if>` 条件压缩为单行(语义与源码一致)。

### Controller

```java
package com.example.demo.controller;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.alibaba.fastjson.JSON;
import com.example.demo.dto.in.SalesReportQueryIn;
import com.example.demo.dto.out.SalesReportOut;
import com.example.demo.service.salereport.ISalesReportService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/sales-report")
@Slf4j
public class SalesReportController {

    @Resource
    private ISalesReportService salesReportService;

    @GetMapping("/list")
    public ApiResult<List<SalesReportOut>> list(String _appId, HttpServletRequest request,
                                                SalesReportQueryIn queryParam,
                                                @RequestParam(value = "enableCache", defaultValue = "1") Integer enableCache) {
        log.info("SalesReportController.list appId={}, param={}, enableCache={}", _appId, JSON.toJSONString(queryParam), enableCache);
        ApiResult check = salesReportService.checkQueryParam(queryParam);
        if (!ReturnCode.SUCCESS.getValue().equals(check.getCode())) {
            return check;
        }
        return ApiResult.succ(salesReportService.queryList(queryParam, enableCache));
    }

    // ……(省略 /listByPage:GET 分页明细,无 enableCache、返回 PageInfo,其余同构;/trend /regionBar /seriesPie:与 /list 完全同构,差异仅返回 Out 类型与所调 service 方法)
}
```

### Service 接口

```java
package com.example.demo.service.salereport;

import java.util.List;
import com.baomidou.mybatisplus.extension.service.IService;
import com.example.demo.dao.entity.SalesReportDO;
import com.example.demo.dto.in.SalesReportQueryIn;
import com.example.demo.dto.out.SalesReportOut;
import com.example.demo.dto.out.SalesTrendOut;
import com.github.pagehelper.PageInfo;

public interface ISalesReportService extends IService<SalesReportDO> {

    /** 分页查原始明细(不走缓存) */
    PageInfo<SalesReportOut> listByPage(SalesReportQueryIn queryParam);
    /** 全量查原始明细(无分页,导出/小数据量用,可缓存) */
    List<SalesReportOut> queryList(SalesReportQueryIn queryParam, Integer enableCache);

    /** 月度销量+成交价趋势(折线图) */
    List<SalesTrendOut> trend(SalesReportQueryIn queryParam, Integer enableCache);

    // ……(省略 regionBar / seriesPie:与 trend 同构,仅换返回 Out 类型)
    /** 入参校验 */
    ApiResult checkQueryParam(SalesReportQueryIn queryParam);

    /** 数据写入后按统计月份清理聚合缓存(供写入方在事务提交后调用,事务内禁止操作 Redis);覆盖当月精确与恰好只覆盖当月的范围查询两类 key,其余维度组合靠 TTL 收敛 */
    void evictCacheByStatMonth(Integer statMonth);
}
```

### ServiceImpl(裁剪版)

```java
package com.example.demo.service.salereport.impl;

// ……(标准 import 区:java.util/math、spring BeanUtils/@Service、jakarta @Resource、lombok @Slf4j、commons 工具、pagehelper;基础设施占位类按文首对照表替换)
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.demo.consts.BaseCacheKeyConsts;

/**
 * 销量报表 Service。缓存策略(事务层禁 Redis,缓存全在 Service 层事务外):
 * - listByPage 明细分页不走缓存(分页参数颗粒度太细命中率低);
 * - queryList / trend / regionBar / seriesPie 按 5 维拼 key,TTL 5 分钟,T+1 刷数下足够覆盖一个浏览 session 且能在数据更新后自然收敛。
 */
@Service
@Slf4j
public class SalesReportServiceImpl extends ServiceImpl<SalesReportMapper, SalesReportDO>
        implements ISalesReportService {

    private static final BigDecimal WAN = new BigDecimal("10000");
    private static final String ALL = "ALL";

    @Resource
    private SalesReportMapper salesReportMapper;
    @Resource
    private RedisClient redisClient;

    // ……(省略 checkQueryParam:入参 null 与 startMonth>endMonth 两条校验,逐条 buildFailure 返回,写法同 campaign 域;
    //      listByPage:PageHelper.startPage → 查 DO → PageInfo 元信息交换 → setList(convert(doList)),异常兜底返 new PageInfo<>())

    /** 全量明细:缓存三件套 buildKey + loader + cacheGet,缓存的是 DO、出口再 convert */
    @Override
    public List<SalesReportOut> queryList(SalesReportQueryIn queryParam, Integer enableCache) {
        try {
            String cacheKey = buildKey(BaseCacheKeyConsts.CACHE_SALES_REPORT_LIST, queryParam);
            Supplier<List<SalesReportDO>> loader = () -> salesReportMapper.selectSalesReportList(queryParam);
            boolean useCache = enableCache != null && enableCache == 1;
            List<SalesReportDO> doList = useCache
                    ? cacheGet(cacheKey, loader, new TypeReference<List<SalesReportDO>>() {})
                    : loader.get();
            return convert(doList);
        } catch (Exception e) {
            log.error("SalesReportServiceImpl.queryList 异常 queryParam={}", JSON.toJSONString(queryParam), e);
            return new ArrayList<>();
        }
    }

    // ……(省略 trend / regionBar / seriesPie:与 queryList 同构,差异是 loader 直接返回聚合 Out
    //      (Mapper 已按 resultMap 映射成 Out,无 convert 步骤)、换缓存 key 常量与 TypeReference 泛型)

    /** 统一缓存读:miss → loader 回源 → 回写 TTL;hit → 反序列化;任何异常退化 loader 直读 */
    private <T> T cacheGet(String key, Supplier<T> loader, TypeReference<T> typeRef) {
        try {
            String cached = redisClient.get(key);
            if (StringUtils.isNotBlank(cached)) {
                return JSON.parseObject(cached, typeRef);
            }
            T fresh = loader.get();
            if (fresh != null) {
                redisClient.setex(key, JSON.toJSONString(fresh), CacheKeyConsts.FIVE_MINUTE_SECOND);
            }
            return fresh;
        } catch (Exception e) {
            log.warn("SalesReportServiceImpl.cacheGet 缓存降级直读 key={}", key, e);
            return loader.get();
        }
    }

    /** key 全维度拼接,null/blank 统一 ALL 占位,保证参数组合与 key 一一对应 */
    private String buildKey(String template, SalesReportQueryIn p) {
        return String.format(template,
                p.getStatMonth() == null ? ALL : p.getStatMonth(),
                p.getStartMonth() == null ? ALL : p.getStartMonth(),
                p.getEndMonth() == null ? ALL : p.getEndMonth(),
                StringUtils.isBlank(p.getRegionCode()) ? ALL : p.getRegionCode(),
                StringUtils.isBlank(p.getSeriesCode()) ? ALL : p.getSeriesCode());
    }

    /** DO→Out:copyProperties 打底 + 手工补万元衍生字段,除法前先判 null */
    private List<SalesReportOut> convert(List<SalesReportDO> doList) {
        List<SalesReportOut> result = new ArrayList<>();
        if (CollectionUtils.isEmpty(doList)) {
            return result;
        }
        for (SalesReportDO src : doList) {
            SalesReportOut vo = new SalesReportOut();
            BeanUtils.copyProperties(src, vo);
            if (src.getTransactionPrice() != null) {
                vo.setTransactionPriceW(src.getTransactionPrice().divide(WAN, 2, RoundingMode.HALF_UP));
            }
            result.add(vo);
        }
        return result;
    }

    @Override
    public void evictCacheByStatMonth(Integer statMonth) {
        if (statMonth == null) {
            return;
        }
        List<String> keys = new ArrayList<>();
        String[] templates = {BaseCacheKeyConsts.CACHE_SALES_REPORT_LIST, BaseCacheKeyConsts.CACHE_SALES_REPORT_TREND,
                BaseCacheKeyConsts.CACHE_SALES_REPORT_REGION_BAR, BaseCacheKeyConsts.CACHE_SALES_REPORT_SERIES_PIE};
        for (String template : templates) {
            keys.add(String.format(template, statMonth, ALL, ALL, ALL, ALL));       // 精确查当月
            keys.add(String.format(template, ALL, statMonth, statMonth, ALL, ALL)); // 范围恰好只覆盖当月
        }
        try {
            redisClient.del(keys.toArray(new String[0]));
        } catch (Exception e) {
            log.error("SalesReportServiceImpl.evictCacheByStatMonth 清理缓存失败 statMonth={}", statMonth, e);
        }
    }
}
```

### Manager(接口 + 事务实现)

```java
public interface ISalesReportManager {
    /** 批量入库(初始化/导入用) */
    int batchInsert(List<SalesReportDO> doList);
    /** 单条更新(按 id + 待更新字段) */
    int update(SalesReportDO updateDO);
}

@Service
@Slf4j
public class SalesReportManagerImpl implements ISalesReportManager {

    /** 单批插入上限,超过由调用方分批 */
    private static final int BATCH_LIMIT = 500;
    @Autowired
    private SalesReportMapper salesReportMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int batchInsert(List<SalesReportDO> doList) {
        if (CollectionUtils.isEmpty(doList)) {
            return 0;
        }
        if (doList.size() > BATCH_LIMIT) {
            throw new IllegalArgumentException("单批插入不能超过" + BATCH_LIMIT + "条,请分批提交");
        }
        return salesReportMapper.batchInsert(doList);
    }
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int update(SalesReportDO updateDO) {
        return salesReportMapper.updateById(updateDO);
    }
}
```

### Mapper(接口 + XML)

```java
@Mapper
public interface SalesReportMapper extends BaseMapper<SalesReportDO> {

    /** 按条件查原始记录 */
    List<SalesReportDO> selectSalesReportList(@Param("queryParam") SalesReportQueryIn queryParam);

    /** 批量插入(多行 VALUES,单批建议 ≤500 条) */
    int batchInsert(@Param("doList") List<SalesReportDO> doList);

    /** 月度销量+成交价趋势(按 stat_month 聚合) */
    List<SalesTrendOut> selectSalesTrend(@Param("queryParam") SalesReportQueryIn queryParam);

    /** 地区销量汇总(柱状图) */
    List<SalesRegionBarOut> selectRegionSalesBar(@Param("queryParam") SalesReportQueryIn queryParam);

    /** 车系销量占比(饼图) */
    List<SalesSeriesPieOut> selectSeriesSalesPie(@Param("queryParam") SalesReportQueryIn queryParam);
}
```

```xml
<mapper namespace="com.example.demo.dao.mapper.SalesReportMapper">
    <resultMap id="BaseResultMap" type="com.example.demo.dao.entity.SalesReportDO">
        <id column="id" property="id"/>
        <result column="stat_month" property="statMonth"/>
        <!-- ……(省略:region/series/brand 名称编码、销量、均价、审计时间等 9 列映射,生成工具产出) -->
        <result column="is_del" property="isDel"/>
    </resultMap>
    <!-- 聚合查询各自独立 resultMap,聚合别名 total_volume / avg_price 直映射 Out 字段 -->
    <resultMap id="TrendMap" type="com.example.demo.dto.out.SalesTrendOut">
        <result column="stat_month" property="statMonth"/>
        <result column="total_volume" property="totalVolume"/>
        <result column="avg_price" property="avgPrice"/>
    </resultMap>
    <resultMap id="RegionBarMap" type="com.example.demo.dto.out.SalesRegionBarOut">
        <result column="region_code" property="regionCode"/>
        <result column="region_name" property="regionName"/>
        <result column="total_volume" property="totalVolume"/>
        <result column="avg_price" property="avgPrice"/>
    </resultMap>
    <resultMap id="SeriesPieMap" type="com.example.demo.dto.out.SalesSeriesPieOut">
        <result column="series_code" property="seriesCode"/>
        <result column="series_name" property="seriesName"/>
        <result column="brand_name" property="brandName"/>
        <result column="total_volume" property="totalVolume"/>
    </resultMap>
    <sql id="Base_Column_List">
        id, stat_month, region_code, region_name, series_code, series_name, brand_name,
        sales_volume, transaction_price, created_stime, modified_stime, is_del
    </sql>
    <insert id="batchInsert" parameterType="java.util.List">
        insert into report_sales_monthly
        (stat_month, region_code, region_name, series_code, series_name, brand_name,
        sales_volume, transaction_price, created_stime, modified_stime, is_del)
        values
        <foreach collection="doList" item="item" separator=",">
            (#{item.statMonth}, #{item.regionCode}, #{item.regionName}, #{item.seriesCode}, #{item.seriesName},
            #{item.brandName}, #{item.salesVolume}, #{item.transactionPrice},
            #{item.createdStime}, #{item.modifiedStime}, 0)
        </foreach>
    </insert>
    <!-- 明细查询:全维度 <if> 过滤的完整写法,排序固定保证导出顺序稳定 -->
    <select id="selectSalesReportList" resultMap="BaseResultMap" parameterType="com.example.demo.dto.in.SalesReportQueryIn">
        select <include refid="Base_Column_List"/>
        from report_sales_monthly
        where is_del = 0
        <if test="queryParam.statMonth != null">and stat_month = #{queryParam.statMonth}</if>
        <if test="queryParam.startMonth != null">and stat_month &gt;= #{queryParam.startMonth}</if>
        <if test="queryParam.endMonth != null">and stat_month &lt;= #{queryParam.endMonth}</if>
        <if test="queryParam.regionCode != null and queryParam.regionCode != ''">and region_code = #{queryParam.regionCode}</if>
        <if test="queryParam.seriesCode != null and queryParam.seriesCode != ''">and series_code = #{queryParam.seriesCode}</if>
        order by stat_month asc, region_code asc, series_code asc
    </select>
    <!-- 趋势:按月聚合,量 sum + 价 round(avg);where 无 statMonth 精确条件(分组维度自身不过滤) -->
    <select id="selectSalesTrend" resultMap="TrendMap" parameterType="com.example.demo.dto.in.SalesReportQueryIn">
        select stat_month,
               sum(sales_volume) as total_volume,
               round(avg(transaction_price), 2) as avg_price
        from report_sales_monthly
        where is_del = 0
        <if test="queryParam.startMonth != null">and stat_month &gt;= #{queryParam.startMonth}</if>
        <if test="queryParam.endMonth != null">and stat_month &lt;= #{queryParam.endMonth}</if>
        <if test="queryParam.regionCode != null and queryParam.regionCode != ''">and region_code = #{queryParam.regionCode}</if>
        <if test="queryParam.seriesCode != null and queryParam.seriesCode != ''">and series_code = #{queryParam.seriesCode}</if>
        group by stat_month
        order by stat_month asc
    </select>
    <!-- 地区柱状:group 连 name 列一起;where 不滤 region_code(自身即分组维度) -->
    <select id="selectRegionSalesBar" resultMap="RegionBarMap" parameterType="com.example.demo.dto.in.SalesReportQueryIn">
        select region_code, region_name,
               sum(sales_volume) as total_volume,
               round(avg(transaction_price), 2) as avg_price
        from report_sales_monthly
        where is_del = 0
        <if test="queryParam.statMonth != null">and stat_month = #{queryParam.statMonth}</if>
        <if test="queryParam.startMonth != null">and stat_month &gt;= #{queryParam.startMonth}</if>
        <if test="queryParam.endMonth != null">and stat_month &lt;= #{queryParam.endMonth}</if>
        group by region_code, region_name
        order by total_volume desc
    </select>
    <!-- 车系饼图:只要占比分母 total_volume,无均价;where 不滤 series_code -->
    <select id="selectSeriesSalesPie" resultMap="SeriesPieMap" parameterType="com.example.demo.dto.in.SalesReportQueryIn">
        select series_code, series_name, brand_name,
               sum(sales_volume) as total_volume
        from report_sales_monthly
        where is_del = 0
        <if test="queryParam.statMonth != null">and stat_month = #{queryParam.statMonth}</if>
        <if test="queryParam.startMonth != null">and stat_month &gt;= #{queryParam.startMonth}</if>
        <if test="queryParam.endMonth != null">and stat_month &lt;= #{queryParam.endMonth}</if>
        <if test="queryParam.regionCode != null and queryParam.regionCode != ''">and region_code = #{queryParam.regionCode}</if>
        group by series_code, series_name, brand_name
        order by total_volume desc
    </select>
</mapper>
```

### DO(字段区;getter/setter 为工具生成物,省略)

```java
@TableName("report_sales_monthly")
public class SalesReportDO {

    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;
    /** 统计月份 yyyyMM,例 202601 */
    @TableField("stat_month")
    private Integer statMonth;
    @TableField("region_code")
    private String regionCode;
    @TableField("region_name")
    private String regionName;
    @TableField("series_code")
    private String seriesCode;
    @TableField("series_name")
    private String seriesName;
    @TableField("brand_name")
    private String brandName;
    /** 销量(辆) */
    @TableField("sales_volume")
    private Integer salesVolume;
    /** 成交均价(元) */
    @TableField("transaction_price")
    private BigDecimal transactionPrice;
    // ……(省略:created_stime / modified_stime 审计时间两字段,均为 @TableField + Date 同构写法)
    @TableField("is_del")
    @TableLogic
    private Integer isDel;
    // ……(省略:全字段 getter/setter,String 字段 setter 带 trim,为 DAO 生成工具产出)
}
```

### DTO(QueryIn / 四张 Out)

```java
/** 查询入参:全端点共用,三类维度 + 分页默认值 */
@Getter @Setter
public class SalesReportQueryIn implements Serializable {
    private static final long serialVersionUID = 1L;
    private Integer pageNum = 1;
    private Integer pageSize = 10;
    /** 精确月份 yyyyMM,与 startMonth/endMonth 互斥 */
    private Integer statMonth;
    /** 月份范围起,包含 */
    private Integer startMonth;
    /** 月份范围止,包含 */
    private Integer endMonth;
    /** 地区编码 */
    private String regionCode;
    /** 车系编码 */
    private String seriesCode;
}

/** 明细返回:与 DO 同构 + 万元衍生展示字段 */
@Getter @Setter
public class SalesReportOut implements Serializable {
    private static final long serialVersionUID = 1L;
    private Long id;
    private Integer statMonth;
    private String regionCode;
    private String regionName;
    private String seriesCode;
    private String seriesName;
    private String brandName;
    private Integer salesVolume;
    private BigDecimal transactionPrice;
    /** 万元为单位的成交均价,方便图表直接展示 */
    private BigDecimal transactionPriceW;
}

/** 月度销量+成交价趋势 VO(折线图用):维度列 + 聚合值 */
@Getter @Setter
public class SalesTrendOut implements Serializable {
    private static final long serialVersionUID = 1L;
    /** 月份 yyyyMM */
    private Integer statMonth;
    /** 月度总销量(sum 升 Long,防溢出) */
    private Long totalVolume;
    /** 月度成交均价(元) */
    private BigDecimal avgPrice;
}

/** 地区销量汇总 VO(柱状图用) */
@Getter @Setter
public class SalesRegionBarOut implements Serializable {
    private static final long serialVersionUID = 1L;
    private String regionCode;
    private String regionName;
    private Long totalVolume;
    private BigDecimal avgPrice;
}

/** 车系销量占比 VO(饼图用):只要占比分母,无均价字段 */
@Getter @Setter
public class SalesSeriesPieOut implements Serializable {
    private static final long serialVersionUID = 1L;
    private String seriesCode;
    private String seriesName;
    private String brandName;
    private Long totalVolume;
}
```

### 常量(CACHE_SALES_REPORT 相关 key)

```java
/** 销量报表缓存 key 模板,参数顺序:statMonth / startMonth / endMonth / regionCode / seriesCode,空值用 ALL 占位 */
public static final String CACHE_SALES_REPORT_LIST       = "sales_report:list:%s:%s:%s:%s:%s";
public static final String CACHE_SALES_REPORT_TREND      = "sales_report:trend:%s:%s:%s:%s:%s";
public static final String CACHE_SALES_REPORT_REGION_BAR = "sales_report:region_bar:%s:%s:%s:%s:%s";
public static final String CACHE_SALES_REPORT_SERIES_PIE = "sales_report:series_pie:%s:%s:%s:%s:%s";
```
