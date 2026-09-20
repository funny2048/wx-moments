# 业务样例:商品管理(goods)

> 本文件是 executor 编码前的风格对齐参照,不是可编译源码;基础设施依赖为占位形态(见「占位依赖对照表」),裁剪处以 `……(省略)` 标注。

## 匹配特征

- 主标签:`商品 CRUD` / `上下架` / `详情缓存` / `C端与管理端接口拆分`
- 适用任务示例:C 端详情页读穿缓存、B 端资料编辑防并发覆盖(乐观锁)、上下架后的 C 端可见性控制、同一实体对 C/B 两端的差异化出参

## 全链组成

```
C 端(只读):GoodsController → IGoodsService(接口 + Impl,详情读穿缓存) → GoodsMapper(接口 + XML) → DO / GoodsOut / 枚举 / 常量
管理端(写):GoodsManageController → 库存服务(归属订单域,见 order.md) → 同一 GoodsMapper(version CAS 更新)
```

涉及文件(包 `com.example.demo` 下):

| 层 | 文件 |
|---|---|
| 入口·C端 | controller/GoodsController.java |
| 入口·管理端 | controller/GoodsManageController.java(库存视图/扣减委托库存服务,见 order.md) |
| 服务 | service/goods/IGoodsService.java、service/goods/impl/GoodsServiceImpl.java |
| 数据 | dao/entity/GoodsDO.java、dao/mapper/GoodsMapper.java、resources/mapper/common/GoodsMapper.xml |
| DTO | dto/in/GoodsEditIn.java、dto/out/GoodsOut.java(B 端出参 GoodsStockOut 归属 order.md) |
| 枚举 | enums/GoodsStatusEnum.java |
| 常量 | consts/BaseCacheKeyConsts.java(goods 相关 key) |

## 占位依赖对照表

样例代码中的基础设施类均为**占位**(类名保留、包路径已省略),编码时必须替换为本项目实际等价物,禁止照抄;开源类可直接引入:

| 占位类 | 代表角色 | 编码时 |
|---|---|---|
| `ApiResult` | 统一返回包装 | 本项目统一返回,保持 `succ / fail(中文话术)` 用法 |
| `RedisClient` | Redis 客户端封装 | RedisTemplate;保留 `get / setex(key, value, seconds)` 用法即可等价迁移 |
| `OperateLog` | 操作日志注解 | 项目操作日志方案;无则删注解,保留方法级日志 |
| `CacheKeyConsts`(TEN_MINUTE_SECOND) | 缓存 TTL 常量 | 项目缓存 TTL 常量类 |
| `DateUtils` | 日期工具 | 项目日期工具 |
| `IInventoryService`(含 StockDeductIn / GoodsStockOut) | 库存服务·订单域 | 边界依赖:库存视图/扣减归属订单域样例,见 order.md |
| `com.baomidou.mybatisplus.*`(IService/ServiceImpl/BaseMapper/@TableLogic/@TableName) | 开源·ORM 增强 | MyBatis-Plus 可直接引入;纯 MyBatis 项目退化为生成的基础 Mapper |
| `com.alibaba.fastjson.*`(JSON / TypeReference) | 开源·JSON 序列化 | fastjson 可直接引入;换 fastjson2 时 TypeReference 泛型用法等价 |
| lombok / commons-lang3 / spring-web / jakarta.annotation | 开源·基础库 | 直接引入 |

## 风格要点(看什么)

**C端与管理端接口拆分**
1. 按消费方拆 Controller,URL 前缀即身份:`/goods` 为 C 端只读,`/goods/manage` 为 B 端写操作;C 端不引登录态、不挂操作日志,B 端写接口一律 `@OperateLog(module = "goods", type = "update", businessId = "#editIn.goodsId")`
2. 同名 `/detail` 两边各写各的,出参按消费方裁剪:C 端 `GoodsOut` 只留展示字段并文案化(statusStr/priceStr);`stock`、`version` 这类治理字段只在 B 端出参暴露(见 order.md),避免向 C 端泄露库存与并发控制细节
3. C 端 Controller 参数就是裸 `goodsId`,前置校验只有"非空且为正整数"一条,其余校验全部下沉 service;查无结果统一话术"商品不存在或已下架",不区分下架/删除,避免向 C 端泄露状态细节
4. 管理端编辑是读→写两段式:先调 `/detail` 取最新 `version`,提交时经 `GoodsEditIn.version` 原样带回;校验失败与业务失败都走 `ApiResult.fail(中文话术)`,HTTP 恒 200 业务码提示

**详情缓存(读穿/写清)**
5. 仅 id 精确命中的 detail 走缓存:key 一维拼接 `goods:detail:%s`,TTL 用常量 10 分钟;命中率与新鲜度的折中理由写在类头 javadoc,不留隐性决策
6. 缓存读收敛为泛型私有方法 `cacheGet(key, loader, TypeReference)`:hit 反序列化返回;miss 走 `Supplier` 回源;回源结果非 null 才回写——查无商品不缓存,商品上架后立即可见
7. 缓存读写任何异常一律降级 `loader.get()` 直查库并 `log.warn` 带异常对象,可用性优先于命中率;外层 `detail` 再 try-catch 兜底返 null,Controller 转统一话术
8. 写清策略:商品下架/改价需 C 端即时生效时,由写操作方在事务提交后主动删 key(参照 campaign.md `evictCampaignCache`);本 demo 编辑链路未回清,靠 10 分钟 TTL 自然收敛——两种口径必须显式声明一种,不许悬空

**上下架状态控制**
9. `status` 1上架 2下架,值语义同时落在 DO 字段注释与 `GoodsStatusEnum`;出参转中文用 `getDescByValue`(未匹配返空串,不给 null)
10. "下架/删除对 C 端不可见"收敛在 SQL 层:`where is_del = 0 and status = 1`,业务代码不做二次状态判断;B 端与交易侧查询(`selectGoodsWithStock`)去掉 status 条件、保留 is_del
11. demo 未单设上下架端点,状态即普通编辑字段;扩展独立上下架接口时沿用 campaign.md 状态机批量模式(查全量→前置状态判断→最小更新 DO→批量事务更新→清缓存)

**并发编辑(乐观锁)**
12. 防并发覆盖用 version CAS:SQL `set ... version = version + 1 where id = ? and version = ?`,影响行数 0 即他人已提交,返回"数据已被他人修改,请刷新后重试"并 log.info 记冲突版本号
13. 编辑 SQL 只更新提交的字段(goods_name/category/price/version),不整实体回写;version 自增写在 SQL 内,不依赖应用层计算
14. 库存扣减(原子条件更新 `stock - N where stock >= N`)与 StockDeductIn / InventoryService 归属订单域样例,见 order.md

**通用纪律**
15. DO→Out 独立 `convert` 私有方法:`BeanUtils.copyProperties` 打底,再手工补 statusStr/priceStr/createdTimeStr;价格 `setScale(2, HALF_UP).toPlainString()`,金额全程 BigDecimal
16. 日志 `类名.方法名 中文动作 参数JSON`:入参 info、结果 info、异常 error 必带异常对象;设计取舍(缓存策略、并发范式)写在类头 javadoc,不散落方法内

## 代码

> 基础设施类(ApiResult/UserHolder 等)的 import 已省略,按文首对照表替换为本项目等价类;业务包 `com.example.demo` 对应你项目自己的业务包。

### Controller(C 端)

```java
package com.example.demo.controller;

import java.util.Objects;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.dto.out.GoodsOut;
import com.example.demo.service.goods.IGoodsService;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

/**
 * C 端商品查询 demo(只读、无登录态参考形态):
 * detail 单接口 + Redis 缓存 10 分钟;下架/删除商品对 C 端不可见。
 */
@RestController
@RequestMapping("/goods")
@Slf4j
public class GoodsController {

    @Resource
    private IGoodsService goodsService;

    /**
     * 商品详情查询(仅上架商品),命中缓存直接返回。
     *
     * @param _appId 调用方应用标识
     * @param goodsId 商品 id
     */
    @GetMapping("/detail")
    public ApiResult<GoodsOut> detail(String _appId, HttpServletRequest request, Integer goodsId) {
        log.info("GoodsController.detail appId={}, goodsId={}", _appId, goodsId);
        if (Objects.isNull(goodsId) || goodsId <= 0) {
            return ApiResult.fail("商品id必须为正整数");
        }
        GoodsOut goodsOut = goodsService.detail(goodsId);
        if (Objects.isNull(goodsOut)) {
            return ApiResult.fail("商品不存在或已下架");
        }
        return ApiResult.succ(goodsOut);
    }
}
```

### Controller(管理端)

```java
package com.example.demo.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.alibaba.fastjson.JSONObject;
import com.example.demo.dto.in.GoodsEditIn;
import com.example.demo.dto.out.GoodsStockOut;            // → 库存域出参,见 order.md
import com.example.demo.service.inventory.IInventoryService;  // → 库存服务,见 order.md

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

/**
 * B 端商品治理 demo:
 * 并发编辑 → 乐观锁(version CAS,冲突提示刷新重试);库存视图与扣减 → 委托库存服务。
 */
@RestController
@RequestMapping("/goods/manage")
@Slf4j
public class GoodsManageController {

    @Resource
    private IInventoryService inventoryService;

    /**
     * 商品库存视图(编辑前读取 version/stock,B 端专用出参形态)。
     */
    @GetMapping("/detail")
    public ApiResult<GoodsStockOut> detail(String _appId, HttpServletRequest request, Integer goodsId) {
        log.info("GoodsManageController.detail appId={}, goodsId={}", _appId, goodsId);
        return inventoryService.detail(goodsId);
    }

    /**
     * 编辑商品基础信息(乐观锁)。
     *
     * @param editIn 须带回详情接口返回的 version;他人已先提交时本次编辑被拒绝(HTTP 层仍为 200,业务码提示重试)
     */
    @OperateLog(module = "goods", type = "update", businessId = "#editIn.goodsId")
    @PostMapping("/edit")
    public ApiResult edit(String _appId, HttpServletRequest request, @RequestBody GoodsEditIn editIn) {
        log.info("GoodsManageController.edit appId={}, param={}", _appId, JSONObject.toJSONString(editIn));
        ApiResult protocol = inventoryService.edit(editIn);
        log.info("GoodsManageController.edit result={}", JSONObject.toJSONString(protocol));
        return protocol;
    }

    // ……(省略 /deductStock:与 /edit 同构,委托库存服务原子扣减;归属订单域样例,见 order.md)
}
```

### Service 接口

```java
package com.example.demo.service.goods;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.demo.dao.entity.GoodsDO;
import com.example.demo.dto.out.GoodsOut;

/**
 * C 端商品查询 Service:detail 走 Redis 缓存(10 分钟),仅上架商品可见。
 */
public interface IGoodsService extends IService<GoodsDO> {

    /**
     * 查上架商品详情(含 statusStr / priceStr / createdTimeStr 出参转换),查无返回 null
     */
    GoodsOut detail(Integer goodsId);
}
```

### ServiceImpl

```java
package com.example.demo.service.goods.impl;

// ……(标准 import 区:java.math.RoundingMode / java.util.function.Supplier / commons-lang3 StringUtils /
//      spring BeanUtils+@Service / fastjson JSON+TypeReference / lombok / jakarta,均按对照表直接引入)

import com.example.demo.consts.BaseCacheKeyConsts;
import com.example.demo.dao.entity.GoodsDO;
import com.example.demo.dao.mapper.GoodsMapper;
import com.example.demo.dto.out.GoodsOut;
import com.example.demo.enums.GoodsStatusEnum;

/**
 * C 端商品查询 Service。
 * <p>
 * 缓存策略:detail 按商品 id 一维拼 key,TTL 10 分钟。
 * 商品基础信息变更低频,10 分钟窗口在命中率和数据新鲜度间取平衡,
 * 商品下架/改价如需即时生效,由写操作方主动删 key(本示例无写入口,靠 TTL 自然收敛)。
 * 缓存读取异常一律降级回源直查,绝不影响可用性。
 */
@Service
@Slf4j
public class GoodsServiceImpl extends ServiceImpl<GoodsMapper, GoodsDO> implements IGoodsService {

    /** 价格展示保留 2 位小数 */
    private static final int PRICE_SCALE = 2;

    @Resource
    private GoodsMapper goodsMapper;

    @Resource
    private RedisClient redisClient;

    @Override
    public GoodsOut detail(Integer goodsId) {
        try {
            String cacheKey = String.format(BaseCacheKeyConsts.CACHE_GOODS_DETAIL, goodsId);
            Supplier<GoodsDO> loader = () -> goodsMapper.selectGoodsDetail(goodsId);
            GoodsDO goodsDO = cacheGet(cacheKey, loader, new TypeReference<GoodsDO>() {});
            return Objects.isNull(goodsDO) ? null : convert(goodsDO);
        } catch (Exception e) {
            log.error("GoodsServiceImpl.detail 商品详情查询异常 goodsId={}", goodsId, e);
            return null;
        }
    }

    /**
     * 统一缓存读:miss → loader 回源 → 回写(TTL 10 分钟);hit → 反序列化返回。
     * 回源结果非 null 才回写;查无商品不缓存,等价于每次穿透查库(低频场景可接受)。
     */
    private <T> T cacheGet(String key, Supplier<T> loader, TypeReference<T> typeRef) {
        try {
            String cached = redisClient.get(key);
            if (StringUtils.isNotBlank(cached)) {
                return JSON.parseObject(cached, typeRef);
            }
            T fresh = loader.get();
            if (fresh != null) {
                redisClient.setex(key, JSON.toJSONString(fresh), CacheKeyConsts.TEN_MINUTE_SECOND);
            }
            return fresh;
        } catch (Exception e) {
            log.warn("GoodsServiceImpl.cacheGet 缓存降级直读 key={}", key, e);
            return loader.get();
        }
    }

    /**
     * DO → C 端出参:补状态中文、价格展示文案、时间展示文案。
     */
    private GoodsOut convert(GoodsDO src) {
        GoodsOut out = new GoodsOut();
        BeanUtils.copyProperties(src, out);
        out.setStatusStr(GoodsStatusEnum.getDescByValue(src.getStatus()));
        if (Objects.nonNull(src.getPrice())) {
            out.setPriceStr(src.getPrice().setScale(PRICE_SCALE, RoundingMode.HALF_UP).toPlainString());
        }
        out.setCreatedTimeStr(DateUtils.formatDefault(src.getCreatedStime()));
        return out;
    }
}
```

### Mapper(接口 + XML)

```java
@Mapper
public interface GoodsMapper extends BaseMapper<GoodsDO> {

    /** 查上架商品详情(C 端不可见下架/删除商品) */
    GoodsDO selectGoodsDetail(@Param("goodsId") Integer goodsId);

    /** 查商品(含库存/版本号,不限上架状态)——下单校验与库存治理用 */
    GoodsDO selectGoodsWithStock(@Param("goodsId") Integer goodsId);

    /** 原子扣减库存(防超卖):stock-N WHERE stock>=N,影响行数=0 即库存不足(订单域调用,见 order.md) */
    int deductStock(@Param("goodsId") Integer goodsId, @Param("quantity") Integer quantity);

    /** 乐观锁编辑:version+1 WHERE version=旧值,影响行数=0 即已被他人修改 */
    int updateGoodsWithVersion(@Param("goodsId") Integer goodsId, @Param("goodsName") String goodsName,
                               @Param("category") String category, @Param("price") BigDecimal price,
                               @Param("version") Integer version);
}
```

```xml
<mapper namespace="com.example.demo.dao.mapper.GoodsMapper">

    <resultMap id="BaseResultMap" type="com.example.demo.dao.entity.GoodsDO">
        <id column="id" property="id"/>
        <result column="goods_name" property="goodsName"/>
        <result column="price" property="price"/>
        <result column="stock" property="stock"/>
        <result column="version" property="version"/>
        <result column="status" property="status"/>
        <!-- ……(省略:category / main_image / summary / created_stime / modified_stime / is_del 列映射,生成工具产出) -->
    </resultMap>

    <sql id="Base_Column_List">
        id, goods_name, category, price, stock, version, status, main_image, summary, created_stime, modified_stime, is_del
    </sql>

    <!-- C 端查询:is_del + status=1 双条件,下架即不可见 -->
    <select id="selectGoodsDetail" resultMap="BaseResultMap">
        select
        <include refid="Base_Column_List"/>
        from goods
        where is_del = 0
          and status = 1
          and id = #{goodsId}
    </select>

    <!-- B 端/交易侧查询:不限上架状态,保留软删 -->
    <select id="selectGoodsWithStock" resultMap="BaseResultMap">
        select
        <include refid="Base_Column_List"/>
        from goods
        where is_del = 0
          and id = #{goodsId}
    </select>

    <!-- 原子扣库存:WHERE stock >= quantity 保证并发下不超卖,影响行数=0 即库存不足(订单域调用,见 order.md) -->
    <update id="deductStock">
        update goods
        set stock = stock - #{quantity}
        where id = #{goodsId}
          and stock &gt;= #{quantity}
          and is_del = 0
    </update>

    <!-- 乐观锁编辑:version 比对 CAS,影响行数=0 表示已被他人修改,调用方提示刷新重试 -->
    <update id="updateGoodsWithVersion">
        update goods
        set goods_name = #{goodsName},
            category = #{category},
            price = #{price},
            version = version + 1
        where id = #{goodsId}
          and version = #{version}
          and is_del = 0
    </update>
</mapper>
```

### DO(字段区;getter/setter 为工具生成物,省略)

```java
@TableName("goods")
public class GoodsDO {

    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    @TableField("goods_name")
    private String goodsName;

    @TableField("category")
    private String category;

    /** 售价(元) */
    @TableField("price")
    private BigDecimal price;

    /** 库存(下单原子扣减 stock-N WHERE stock>=N 防超卖) */
    @TableField("stock")
    private Integer stock;

    /** 乐观锁版本号,并发编辑 CAS 更新(version+1 WHERE version=旧值) */
    @TableField("version")
    private Integer version;

    /** 状态 1上架 2下架 */
    @TableField("status")
    private Integer status;

    @TableField("main_image")
    private String mainImage;

    @TableField("summary")
    private String summary;

    @TableField("created_stime")
    private Date createdStime;

    @TableField("modified_stime")
    private Date modifiedStime;

    @TableField("is_del")
    @TableLogic
    private Integer isDel;

    // ……(省略:全字段 getter/setter,由 DAO 生成工具产出;setter 对 String 字段做 null 安全 trim)
}
```

### DTO(In / Out)

```java
/** B 端编辑入参:乐观锁——必须携带编辑前读取到的 version */
@Getter
@Setter
public class GoodsEditIn implements Serializable {

    private static final long serialVersionUID = 1L;

    private Integer goodsId;

    private String goodsName;

    private String category;

    /** 售价(元) */
    private BigDecimal price;

    /** 乐观锁版本号:客户端从详情接口读到的 version,提交时原样带回 */
    private Integer version;
}

/** C 端商品详情返回:只含展示字段,数值文案化 */
@Getter
@Setter
public class GoodsOut implements Serializable {

    private static final long serialVersionUID = 1L;

    private Integer id;
    private String goodsName;
    private String category;

    /** 售价(元),金额全程 BigDecimal */
    private BigDecimal price;

    /** 售价展示文案,如 1299.00 */
    private String priceStr;

    private Integer status;

    /** 状态中文标识:上架/下架 */
    private String statusStr;

    private String mainImage;
    private String summary;

    /** 创建时间 YYYY-MM-DD HH:mm:ss */
    private String createdTimeStr;
}
```

### 枚举

```java
/** 商品状态:C 端仅上架商品可见;value + desc,出参转中文用 getDescByValue(未匹配返空串) */
public enum GoodsStatusEnum {

    ON_SHELF(1, "上架"),
    OFF_SHELF(2, "下架"),
    ;

    private final Integer value;
    private final String desc;

    GoodsStatusEnum(Integer value, String desc) {
        this.value = value;
        this.desc = desc;
    }

    public Integer getValue() { return value; }

    public String getDesc() { return desc; }

    public static String getDescByValue(Integer value) {
        for (GoodsStatusEnum e : values()) {
            if (Objects.equals(e.getValue(), value)) {
                return e.getDesc();
            }
        }
        return "";
    }
}
```

### 常量(商品缓存 key)

```java
public final class BaseCacheKeyConsts {

    /** 商品详情缓存 key 模板,参数为 goodsId,TTL 见 CacheKeyConsts.TEN_MINUTE_SECOND */
    public static final String CACHE_GOODS_DETAIL = "goods:detail:%s";

    // ……(省略:本文件其余为 campaign/sales/leads/order 域 key)
}

// TTL 常量 TEN_MINUTE_SECOND(600 秒)在占位 CacheKeyConsts 中,落地时并入本项目缓存 TTL 常量
```
