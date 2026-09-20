# 业务样例:订单下单(order)

> 本文件是 executor 编码前的风格对齐参照,不是可编译源码;基础设施依赖为占位形态(见「占位依赖对照表」),裁剪处以 `……(省略)` 标注。

## 匹配特征

- 主标签:`提交事务` / `MQ 收发` / `库存并发扣减` / `requestId 幂等` / `Manager 聚合多表`
- 适用任务示例:C 端提单类写流程(查校验→算价→扣资源→落单→发通知)、占用外部资源后本地落库需失败补偿的场景、防超卖的并发扣减、防重复提交的交易入口

## 全链组成

```
Controller → Service(接口 + Impl 编排) → Manager(事务层) → Mapper(接口 + XML) → DO / In / Out / 枚举 / MQ / 常量
写链路时序:幂等锁 → requestId 查重 → 本地查商品/促销算价 → Feign 查券/核销(事务外) → Manager 事务(原子扣库存+落单) → 事务提交后发 MQ
```

涉及文件(包 `com.example.demo` 下):

| 层 | 文件 |
|---|---|
| 入口/服务 | controller/OrderController.java、service/order/IOrderService.java、impl/OrderServiceImpl.java;service/inventory/IInventoryService.java、impl/InventoryServiceImpl.java(库存治理,与下单共用原子扣减 SQL,见风格要点 11-13) |
| 事务/数据 | manager/IOrderManager.java、impl/OrderManagerImpl.java;dao/entity/OrderInfoDO.java、PromotionDO.java、dao/mapper/OrderInfoMapper.java、PromotionMapper.java、GoodsMapper.java(库存 SQL)、resources/mapper/common/ 对应三个 XML |
| DTO | dto/in/OrderSubmitIn.java、OrderQueryIn.java、StockDeductIn.java、dto/out/OrderSubmitOut.java、GoodsStockOut.java |
| 枚举/MQ/常量 | enums/OrderStatusEnum.java、PromotionTypeEnum.java、mq/OrderMsg.java、OrderMsgService.java、consts/BaseCacheKeyConsts.java(order key) |

## 占位依赖对照表

样例代码中的基础设施类均为**占位**(类名保留、包路径已省略),编码时必须替换为本项目实际等价物,禁止照抄;开源类可直接引入:

| 占位类 | 代表角色 | 编码时 |
|---|---|---|
| `ApiResult` | 统一返回包装 | 替换为本项目统一返回,保持 `succ / fail / buildFailure(code, msg)` 三态用法 |
| `BizException` | 业务异常 | 项目业务异常;Manager 事务方法内抛出触发回滚,Service 层 catch 后转 ApiResult |
| `RedisLockManager` / `RedisLock` / `LockFailException` | Redis 锁封装 | Redisson 或自研锁封装;保持"非阻塞 tryLock,失败/异常统一返回 false"形态 |
| `ReturnCode` / `DateUtils` | 错误码 / 日期工具 | 项目内等价错误码 / 日期工具类 |
| `CouponFeignClient` / `CouponVO` | 外部券服务 RPC | 项目券服务 RPC 客户端(OpenFeign / Dubbo);保留 查询/核销/返还 三接口语义 |
| `AbstractRabbitMqService` | MQ 封装 | 项目 MQ(RocketMQ / Spring AMQP / Spring Events);需自带可靠投递(发送失败落库+补偿重推)与消费幂等 |
| `com.baomidou.mybatisplus.*` + `org.apache.ibatis.*`(@Mapper/@Param) | 开源·ORM 增强 | MyBatis-Plus 与 MyBatis 原生注解,可直接引入;纯 MyBatis 项目退化为生成的基础 Mapper |
| lombok / fastjson / commons-lang3 / spring-web / spring-tx / jakarta.annotation | 开源·工具与框架 | 直接引入 |

## 风格要点(看什么)

**编排与事务边界**
1. Controller 薄到只有:入口 info 日志(参数 JSON 化)→ 调 service → 结果日志;写链路不在 Controller 兜 try-catch,异常兜底收敛在 ServiceImpl(补偿逻辑需要感知异常)
2. ServiceImpl 是编排层不是事务层:submit 按编号步骤注释推进(1 幂等锁→2 查重→3 查商品→4 促销算价→5 查券→6 实付→7 核销→8 事务落单→9 发 MQ→10 补偿),步骤注释即教学文档
3. Manager 是唯一事务边界:只含同库多表写(扣库存+插订单),方法上 `@Transactional(rollbackFor = Exception.class)`;RPC/MQ/Redis 一律禁止进事务,Feign 券操作由 Service 在事务外编排
4. 外部资源"先占用后补偿":券先核销再落单,落单失败必还券;补偿失败打 error 告警转人工,不吞异常也不打断返回

**requestId 幂等**
5. 双层防线:Redis 防重锁前置(窗口 5 分钟,窗口期内同 requestId 直接拒"提交处理中")→ 锁内 `selectByRequestId` 查 DB,命中即幂等返回原单(兜住锁过期后的迟到重试);DB 层 request_id 唯一索引最终兜底
6. 幂等命中返回 `ApiResult.succ(原单出参)` 而非错误码——重复提交对客户端是成功,不是失败

**算价与订单初始化**
7. 金额全程 BigDecimal,scale=2 HALF_UP 用常量 `AMOUNT_SCALE` 收口;实付 = 原价-促销-券后 `.max(BigDecimal.ZERO)` 封底防负数
8. 促销计算防倒挂:直减 `min(减免额, 订单额)`;折扣 `原价*(100-折扣率)/100`;无有效促销返回 ZERO 不返回 null
9. 下单时刻快照:goodsName/unitPrice 从查到的商品复制进订单,不随商品后续修改变化;status 初始化为 `OrderStatusEnum.CREATED`,新单永远从最小状态进入
10. 订单号 = 前缀 + 时间戳 + 6 位随机(ThreadLocalRandom),唯一性不在应用层保证,由 uk_order_no 唯一索引兜底

**库存扣减的并发控制**
11. 原子条件更新扣库存:`update goods set stock = stock - N where id=? and stock >= N and is_del=0`,数据库行锁保证判断与扣减原子,并发不超卖;影响 0 行即库存不足,在事务内抛 BizException 回滚
12. B 端手工扣减(InventoryServiceImpl.deduct)与 C 端下单共用同一条 deductStock SQL——单一扣减入口,防两处口径漂移
13. 并发编辑走乐观锁:`set version=version+1 where id=? and version=?`,0 行即冲突提示刷新重试;扣减场景不用乐观锁(重试代价高)用原子条件更新
14. 数量边界常量化(QUANTITY_MIN/MAX=1-99):下界防 0/负数,上界防恶意大批量,校验放在最前

**MQ 收发时序**
15. 只在事务提交后发送:sendOrderMsg 在 createOrder(事务方法)返回之后调用;发送失败只 error 告警不回滚已落库订单,最终一致交给 MQ 侧失败落库重推/下游对账
16. 发送带业务幂等 ID(provide 第二参传 orderId),消费端按此防重;消息体金额用 String,避免 BigDecimal 跨服务序列化差异

**外部调用与通用纪律**
17. Feign 三动作统一"异常或业务失败折叠为单一返回值":queryCoupon 统一 null、consume/returnCoupon 统一 false,调用方只判空值决定终止/补偿
18. 手写 SQL 一律 `is_del = 0`;促销有效性判断收在 SQL 内(status=1 + now() 闭区间 + order by id desc limit 1),不在 Java 层过滤
19. 日志格式 `类名.方法名 中文动作 key=value`,异常分支必须带异常对象 e;幂等命中/下单成功/MQ 投递等关键节点均 info 留痕
20. 锁 key 集中在常量类用 `%s` 模板(`demo:order:lock:submit:%s`),防重窗口复用全局 `LOCK_TIME_IDEMPOTENT_MILLIS`,不散落魔法值

## 代码

> 基础设施类(ApiResult/UserHolder 等)的 import 已省略,按文首对照表替换为本项目等价类;业务包 `com.example.demo` 对应你项目自己的业务包。

### Controller

```java
package com.example.demo.controller;
// ……(省略:demo 域 In/Out/Service 导入与 spring-web/fastjson/jakarta/lombok 标准导入)
/** C 端下单 demo(写流程参考形态):编排与事务边界见 OrderServiceImpl 类注释 */
@RestController
@RequestMapping("/order")
@Slf4j
public class OrderController {
    @Resource private IOrderService orderService;
    /** 提交订单(requestId 幂等,重复提交返回原单);couponId 可选(券操作走 Feign,失败自动补偿返还) */
    @PostMapping("/submit")
    public ApiResult<OrderSubmitOut> submit(String _appId, HttpServletRequest request,
                                            @RequestBody OrderSubmitIn submitIn) {
        log.info("OrderController.submit appId={}, param={}", _appId, JSONObject.toJSONString(submitIn));
        ApiResult<OrderSubmitOut> protocol = orderService.submit(submitIn);
        log.info("OrderController.submit 下单 result={}", JSONObject.toJSONString(protocol));
        return protocol;
    }
}
```

### Service 接口

```java
/** C 端下单 Service:写流程示范(本地查商品/促销 + Feign 券操作 + 事务扣库存落单 + 失败补偿);ApiResult 按对照表替换 */
public interface IOrderService {
    /** 提交订单(requestId 幂等),成功返回订单号与金额明细 */
    ApiResult<OrderSubmitOut> submit(OrderSubmitIn submitIn);
}
```

### ServiceImpl(裁剪版)

```java
package com.example.demo.service.order.impl;
// ……(省略:java.util/commons/spring/jakarta/lombok 标准导入;demo 域导入 com.example.demo.*(3 个 DO、
//      3 个 Mapper、In/Out、3 个枚举、IOrderManager、OrderMsg/OrderMsgService、BaseCacheKeyConsts))
/**
 * C 端下单写流程示范(步骤注释即教学文档)。编排顺序与事务边界:
 * 1.幂等锁前置+锁内查重 → 2.本地查商品/促销+算价 → 3.Feign 查券/核销(均在事务外) → 4.Manager 事务:原子扣库存+落单(uk 兜底)
 * → 5.事务失败→还券补偿(失败告警人工兜底) → 6.事务提交后发 MQ
 */
@Service
@Slf4j
public class OrderServiceImpl implements IOrderService {
    /** 数量边界:下界防 0/负数,上界防恶意大批量 */
    private static final int QUANTITY_MIN = 1;
    private static final int QUANTITY_MAX = 99;
    /** 金额精度:全程 BigDecimal,scale=2,HALF_UP */
    private static final int AMOUNT_SCALE = 2;
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    @Resource private GoodsMapper goodsMapper;
    @Resource private PromotionMapper promotionMapper;
    @Resource private OrderInfoMapper orderInfoMapper;
    @Resource private CouponFeignClient couponFeignClient;
    @Resource private IOrderManager orderManager;
    @Resource private OrderMsgService orderMsgService;
    @Resource private RedisLockManager redisLockManager;
    @Override
    public ApiResult<OrderSubmitOut> submit(OrderSubmitIn submitIn) {
        ApiResult checkResult = checkSubmitParam(submitIn);
        if (!ReturnCode.SUCCESS.getValue().equals(checkResult.getCode())) {
            return checkResult;
        }
        String requestId = submitIn.getRequestId();
        try {
            // 1. 幂等锁前置:窗口期内同一 requestId 只放行一个(TTL 到期自动释放)
            if (!tryLock(String.format(BaseCacheKeyConsts.LOCK_ORDER_SUBMIT_REQUEST_KEY, requestId))) {
                return ApiResult.fail("提交处理中，请勿重复提交");
            }
            // 2. 锁内查重:幂等返回原单(兜住锁过期后的迟到重试)
            OrderInfoDO existOrder = orderInfoMapper.selectByRequestId(requestId);
            if (Objects.nonNull(existOrder)) {
                log.info("OrderServiceImpl.submit 幂等命中 requestId={}, orderNo={}", requestId, existOrder.getOrderNo());
                return ApiResult.succ(convertOut(existOrder));
            }
            // 3. 本地查商品(含库存):C 端不可见下架/删除商品
            GoodsDO goods = goodsMapper.selectGoodsWithStock(submitIn.getGoodsId());
            if (Objects.isNull(goods)) { return ApiResult.fail("商品不存在"); }
            if (!GoodsStatusEnum.ON_SHELF.getValue().equals(goods.getStatus())) { return ApiResult.fail("商品已下架"); }
            // 4. 本地查促销 + 优惠计算(BigDecimal,scale=2 HALF_UP)
            BigDecimal originAmount = goods.getPrice().multiply(BigDecimal.valueOf(submitIn.getQuantity()))
                    .setScale(AMOUNT_SCALE, java.math.RoundingMode.HALF_UP);
            PromotionDO promotion = promotionMapper.selectValidByGoodsId(submitIn.getGoodsId());
            BigDecimal promotionAmount = calcPromotionAmount(originAmount, promotion);
            // 5. Feign 查券(可选:传了 couponId 才走;强依赖——查不到/不可用直接终止下单)
            BigDecimal couponAmount = BigDecimal.ZERO;
            if (StringUtils.isNotBlank(submitIn.getCouponId())) {
                CouponVO coupon = queryCoupon(submitIn.getUserId(), submitIn.getCouponId());
                if (Objects.isNull(coupon) || Objects.isNull(coupon.getAmount())) { return ApiResult.fail("优惠券不可用"); }
                couponAmount = coupon.getAmount();
            }
            // 6. 实付 = 原价 - 促销 - 券,下限 0(优惠合计不超过订单金额)
            BigDecimal payAmount = originAmount.subtract(promotionAmount).subtract(couponAmount)
                    .max(BigDecimal.ZERO).setScale(AMOUNT_SCALE, java.math.RoundingMode.HALF_UP);
            // 7. 事务外核销券(先核销后落单):核销失败说明券被占用/服务异常,终止下单,无需补偿
            String orderNo = generateOrderNo();
            boolean couponConsumed = false;
            if (StringUtils.isNotBlank(submitIn.getCouponId())) {
                if (!consumeCoupon(submitIn.getCouponId(), orderNo)) { return ApiResult.fail("优惠券核销失败，请稍后重试"); }
                couponConsumed = true;
            }
            // 8. 本地事务:原子扣库存 + 落单(库存不足抛 BizException 回滚)
            try {
                OrderInfoDO orderInfo = buildOrder(submitIn, orderNo, goods, promotion, promotionAmount, couponAmount, payAmount);
                orderManager.createOrder(orderInfo);
                log.info("OrderServiceImpl.submit 下单成功 requestId={}, orderNo={}, payAmount={}", requestId, orderNo, payAmount);
                // 9. 事务已提交(createOrder 返回即提交)→ 发 MQ;发送失败只告警不回滚
                sendOrderMsg(orderInfo);
                return ApiResult.succ(convertOut(orderInfo));
            } catch (Exception e) {
                // 10. 补偿:事务失败(库存不足/唯一键冲突等)→ 已核销的券必须返还
                log.error("OrderServiceImpl.submit 下单事务失败 requestId={}, orderNo={}", requestId, orderNo, e);
                if (couponConsumed && !returnCoupon(submitIn.getCouponId(), orderNo)) {
                    // 补偿也失败:打 error 告警人工兜底(券已核销但订单未落,必须人工介入)
                    log.error("OrderServiceImpl.submit 还券补偿失败！需人工处理 couponId={}, orderNo={}", submitIn.getCouponId(), orderNo);
                }
                if (e instanceof BizException bizException) { return ApiResult.fail(bizException.getMessage()); }
                return ApiResult.fail("下单失败，请稍后重试");
            }
        } catch (Exception e) {
            log.error("OrderServiceImpl.submit 下单异常 requestId={}", requestId, e);
            return ApiResult.fail("下单失败，请稍后重试");
        }
    }
    /** 入参校验:逐条不合法即 return fail。 */
    private ApiResult checkSubmitParam(OrderSubmitIn submitIn) {
        if (Objects.isNull(submitIn) || StringUtils.isBlank(submitIn.getRequestId())) { return ApiResult.fail("requestId不能为空"); }
        if (StringUtils.isBlank(submitIn.getUserId()) || Objects.isNull(submitIn.getGoodsId())) { return ApiResult.fail("用户与商品不能为空"); }
        if (Objects.isNull(submitIn.getQuantity()) || submitIn.getQuantity() < QUANTITY_MIN
                || submitIn.getQuantity() > QUANTITY_MAX) { return ApiResult.fail("购买数量需在1-99之间"); }
        return ApiResult.succ();
    }
    /** 促销优惠计算:直减 min(减免额,订单额) 防倒挂;折扣 原价*(100-折扣率)/100;无有效促销返回 0。 */
    private BigDecimal calcPromotionAmount(BigDecimal originAmount, PromotionDO promotion) {
        if (Objects.isNull(promotion) || Objects.isNull(promotion.getDiscount())) { return BigDecimal.ZERO; }
        PromotionTypeEnum typeEnum = PromotionTypeEnum.fromValue(promotion.getPromotionType());
        if (PromotionTypeEnum.FIX_AMOUNT == typeEnum) { return promotion.getDiscount().min(originAmount); }
        if (PromotionTypeEnum.DISCOUNT == typeEnum) {
            return originAmount.multiply(HUNDRED.subtract(promotion.getDiscount()))
                    .divide(HUNDRED, AMOUNT_SCALE, java.math.RoundingMode.HALF_UP);
        }
        return BigDecimal.ZERO;
    }
    /** Feign 查券:调用异常(服务不可达)或业务失败统一返回 null,由调用方终止下单。 */
    private CouponVO queryCoupon(String userId, String couponId) {
        try {
            ApiResult<CouponVO> result = couponFeignClient.queryCoupon(userId, couponId);
            if (Objects.nonNull(result) && ReturnCode.SUCCESS.getValue().equals(result.getCode())) { return result.getData(); }
            log.warn("OrderServiceImpl.queryCoupon 券不可用 userId={}, couponId={}, result={}", userId, couponId,
                    com.alibaba.fastjson.JSON.toJSONString(result));
            return null;
        } catch (Exception e) {
            log.error("OrderServiceImpl.queryCoupon 调用券服务异常 userId={}, couponId={}", userId, couponId, e);
            return null;
        }
    }
    // ……(省略:consumeCoupon / returnCoupon,与 queryCoupon 同构。差异点:分别调 couponFeignClient.consumeCoupon /
    //      returnCoupon(couponId, orderNo),统一"异常或业务失败折叠为 false",javadoc 口径对应核销/还券补偿)
    /** 事务提交后通知下游(事务内禁 MQ)。provide 第二参为业务幂等 ID(orderId),消费端按此防重;发送异常只告警不回滚。 */
    private void sendOrderMsg(OrderInfoDO orderInfo) {
        try {
            OrderMsg msg = new OrderMsg();
            msg.setOrderId(Long.valueOf(orderInfo.getId()));
            msg.setOrderNo(orderInfo.getOrderNo());
            msg.setOrderStatus(orderInfo.getStatus());
            msg.setPayAmount(Objects.toString(orderInfo.getPayAmount(), "0"));
            msg.setUserName(orderInfo.getUserId());
            orderMsgService.send(msg);
            log.info("OrderServiceImpl.sendOrderMsg 下单消息已投递 orderNo={}, orderId={}", orderInfo.getOrderNo(), orderInfo.getId());
        } catch (Exception e) {
            // 订单已提交不可回滚:告警人工/补偿任务兜底(MQ 落库重推或下游对账)
            log.error("OrderServiceImpl.sendOrderMsg 下单消息投递失败！需关注补偿 orderNo={}, orderId={}", orderInfo.getOrderNo(),
                    orderInfo.getId(), e);
        }
    }
    /** 订单号:时间戳 + 6 位随机(ThreadLocalRandom),唯一性由 uk_order_no 兜底。 */
    private String generateOrderNo() {
        return "O" + DateUtils.format(new Date(), "yyyyMMddHHmmss")
                + ThreadLocalRandom.current().nextInt(100000, 1000000);
    }
    /** 组装订单实体:商品名/单价做下单时刻快照,促销/券优惠与实付均已算定。 */
    private OrderInfoDO buildOrder(OrderSubmitIn submitIn, String orderNo, GoodsDO goods, PromotionDO promotion, BigDecimal promotionAmount,
                                   BigDecimal couponAmount, BigDecimal payAmount) {
        OrderInfoDO orderInfo = new OrderInfoDO();
        orderInfo.setRequestId(submitIn.getRequestId());
        orderInfo.setOrderNo(orderNo);
        // ……(省略:userId/goodsId/goodsName/quantity/unitPrice 五个快照 set,同构)
        orderInfo.setPromotionId(Objects.isNull(promotion) ? null : promotion.getId());
        orderInfo.setPromotionAmount(promotionAmount);
        orderInfo.setCouponId(submitIn.getCouponId());
        orderInfo.setCouponAmount(couponAmount);
        orderInfo.setPayAmount(payAmount);
        orderInfo.setStatus(OrderStatusEnum.CREATED.getValue());
        return orderInfo;
    }
    private OrderSubmitOut convertOut(OrderInfoDO orderInfo) {
        OrderSubmitOut out = new OrderSubmitOut();
        out.setOrderNo(orderInfo.getOrderNo());
        out.setPromotionAmount(orderInfo.getPromotionAmount());
        out.setCouponAmount(orderInfo.getCouponAmount());
        out.setPayAmount(orderInfo.getPayAmount());
        return out;
    }
    /** 尝试获取 Redis 防重锁(非阻塞),获取失败或异常统一返回 false。 */
    private boolean tryLock(String lockKey) {
        try {
            RedisLock redisLock = redisLockManager.fetchAndTryLock(lockKey, BaseCacheKeyConsts.LOCK_TIME_IDEMPOTENT_MILLIS);
            return redisLock.isAcquired();
        } catch (LockFailException e) {
            log.warn("OrderServiceImpl.tryLock 获取锁失败 lockKey={}", lockKey);
            return false;
        } catch (Exception e) {
            log.error("OrderServiceImpl.tryLock 获取锁异常 lockKey={}", lockKey, e);
            return false;
        }
    }
}
```

### Manager(接口 + 事务实现)

```java
/** 订单事务 Manager:只做「同库多表写」的事务边界,禁止 RPC/MQ/Redis;Feign 券操作在 OrderServiceImpl 编排,事务方法之外。 */
public interface IOrderManager {
    /**
     * 事务内完成下单落库:①原子扣库存(0行=库存不足抛 BizException 触发回滚)②插入订单;
     * orderNo/requestId 唯一索引兜底幂等,返回 insert 后回填主键 id 的订单实体
     */
    OrderInfoDO createOrder(OrderInfoDO orderInfo);
}
@Service
@Slf4j
public class OrderManagerImpl implements IOrderManager {
    @Autowired private GoodsMapper goodsMapper;
    @Autowired private OrderInfoMapper orderInfoMapper;
    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderInfoDO createOrder(OrderInfoDO orderInfo) {
        // 1. 原子扣库存:stock-N WHERE stock>=N,影响 0 行即库存不足(并发下无超扣窗口),抛异常回滚
        int deducted = goodsMapper.deductStock(orderInfo.getGoodsId(), orderInfo.getQuantity());
        if (deducted == 0) {
            throw new BizException(ReturnCode.FAIL.getValue(), "库存不足");
        }
        // 2. 落订单(request_id / order_no 唯一索引兜底幂等,撞唯一键异常回滚扣库存)
        orderInfoMapper.insert(orderInfo);
        log.info("OrderManagerImpl.createOrder 下单落库成功 orderNo={}, goodsId={}, quantity={}", orderInfo.getOrderNo(),
                orderInfo.getGoodsId(), orderInfo.getQuantity());
        return orderInfo;
    }
}
```

### Mapper(接口 + XML)

```java
@Mapper
public interface OrderInfoMapper extends BaseMapper<OrderInfoDO> {
    /** 按幂等号查订单(下单幂等返回原单) */
    OrderInfoDO selectByRequestId(@Param("requestId") String requestId);
    /** 按条件查订单列表(导出用,配合 PageHelper 分页),日期为闭区间 */
    List<OrderInfoDO> selectOrderList(@Param("queryParam") OrderQueryIn queryParam);
}
@Mapper
public interface PromotionMapper extends BaseMapper<PromotionDO> {
    /** 查商品当前有效促销(启用 + 时间窗内,NOW 闭区间比对),多条时取最新一条 */
    PromotionDO selectValidByGoodsId(@Param("goodsId") Integer goodsId);
}
// GoodsMapper(BaseMapper<GoodsDO>)另有:selectGoodsWithStock(goodsId)、deductStock(goodsId, quantity),SQL 见下
```

```xml
<mapper namespace="com.example.demo.dao.mapper.OrderInfoMapper">
    <resultMap id="BaseResultMap" type="com.example.demo.dao.entity.OrderInfoDO">
        <id column="id" property="id"/>
        <result column="request_id" property="requestId"/>
        <result column="order_no" property="orderNo"/>
        <result column="pay_amount" property="payAmount"/>
        <result column="status" property="status"/>
        <!-- ……(省略:其余列映射,生成工具产出) -->
        <result column="is_del" property="isDel"/>
    </resultMap>
    <sql id="Base_Column_List">id, request_id, order_no, <!-- ……(省略其余列) --> pay_amount, status, is_del</sql>
    <select id="selectByRequestId" resultMap="BaseResultMap">
        select <include refid="Base_Column_List"/> from order_info
        where is_del = 0 and request_id = #{requestId}
        limit 1
    </select>
    <select id="selectOrderList" resultMap="BaseResultMap">
        select <include refid="Base_Column_List"/> from order_info
        where is_del = 0
        <if test="queryParam.orderStatus != null"> and status = #{queryParam.orderStatus} </if>
        <if test="queryParam.startTime != null"> and created_stime &gt;= #{queryParam.startTime} </if>
        <if test="queryParam.endTime != null"> and created_stime &lt;= #{queryParam.endTime} </if>
        order by id asc
    </select>
</mapper>
<mapper namespace="com.example.demo.dao.mapper.PromotionMapper">
    <resultMap id="BaseResultMap" type="com.example.demo.dao.entity.PromotionDO">
        <id column="id" property="id"/>
        <result column="goods_id" property="goodsId"/>
        <result column="promotion_type" property="promotionType"/>
        <result column="discount" property="discount"/>
        <result column="status" property="status"/>
        <!-- ……(省略:start_time/end_time/审计列映射) -->
        <result column="is_del" property="isDel"/>
    </resultMap>
    <!-- 有效促销:启用 + 当前时间落在 [start_time, end_time] 闭区间内 -->
    <select id="selectValidByGoodsId" resultMap="BaseResultMap">
        select <include refid="Base_Column_List"/> from promotion
        where is_del = 0 and status = 1 and goods_id = #{goodsId}
          and start_time &lt;= now() and end_time &gt;= now()
        order by id desc
        limit 1
    </select>
</mapper>
<!-- 以下位于 GoodsMapper.xml:原子扣库存,下单链路与 B 端库存治理共用;
     WHERE stock >= quantity 保证并发下不超卖,影响行数=0 即库存不足 -->
<update id="deductStock">
    update goods
    set stock = stock - #{quantity}
    where id = #{goodsId}
      and stock &gt;= #{quantity}
      and is_del = 0
</update>
<!-- 同文件另有乐观锁编辑 updateGoodsWithVersion:set version=version+1 where id=? and version=?,形态见风格要点 13 -->
```

### DO(字段区;getter/setter 为工具生成物,省略)

```java
@TableName("order_info")
public class OrderInfoDO {
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;
    @TableField("request_id") private String requestId;        // 客户端幂等号,同一 requestId 重复下单只落一单
    @TableField("order_no") private String orderNo;
    @TableField("user_id") private String userId;
    @TableField("goods_id") private Integer goodsId;
    @TableField("goods_name") private String goodsName;        // 商品名称快照(下单时刻,不随商品修改变化)
    @TableField("quantity") private Integer quantity;
    @TableField("unit_price") private BigDecimal unitPrice;    // 成交单价(元,下单时刻快照)
    @TableField("promotion_id") private Integer promotionId;
    @TableField("promotion_amount") private BigDecimal promotionAmount;    // 促销优惠金额(元)
    @TableField("coupon_id") private String couponId;
    @TableField("coupon_amount") private BigDecimal couponAmount;          // 券优惠金额(元)
    @TableField("pay_amount") private BigDecimal payAmount;    // 实付金额(元)= 单价*数量-促销-券,下限0
    @TableField("status") private Integer status;              // 订单状态 1已创建 2已关闭
    // ……(省略:created_stime / modified_stime 审计列,同构;getter/setter 为工具生成物)
    @TableField("is_del") @TableLogic private Integer isDel;
}
/** 促销 DO 同构(@TableName("promotion")):goods_id、promotion_type(1直减 2折扣)、discount(直减=减免金额元/单;
 *  折扣=折扣率百分比,90=9折)、start_time、end_time(闭区间)、status(1启用 2停用)、审计列、is_del(@TableLogic) */
```

### DTO(In / Out)

```java
// ……(省略:各类均 @Getter @Setter + implements Serializable + serialVersionUID,机械生成物)
/** C 端下单入参 */
public class OrderSubmitIn {
    private String requestId;     // 客户端幂等号(每次提交唯一,重复提交返回原单)
    private String userId;
    private Integer goodsId;
    private Integer quantity;     // 购买数量 1-99
    private String couponId;      // 优惠券ID,可选(不传=不使用优惠券)
}
/** 订单查询条件(订单导出用):日期为 String 接收、服务端转 Date,闭区间 */
public class OrderQueryIn {
    private Integer orderStatus;  // 订单状态 1已创建 2已关闭,空=全部
    private Date startTime;       // 下单开始时间(含),服务端转换,格式 yyyy-MM-dd
    private Date endTime;         // 下单结束时间(含当天 23:59:59),服务端转换
}
/** B 端手工扣库存入参(StockDeductIn:goodsId + quantity 1-9999 + reason 审计;与下单共用原子扣减 SQL) */
/** B 端库存视图出参(GoodsStockOut:id/goodsName/category/price/stock/version/status/statusStr,编辑提交须带回 version) */
/** 下单结果出参 */
public class OrderSubmitOut {
    private String orderNo;
    private BigDecimal promotionAmount;   // 促销优惠金额(元)
    private BigDecimal couponAmount;      // 券优惠金额(元)
    private BigDecimal payAmount;         // 实付金额(元)
}
```

### 枚举

```java
/** 订单状态。本 demo 只做「已创建/已关闭」两态(支付回调、超时关单不在示范范围) */
public enum OrderStatusEnum {
    CREATED(1, "已创建"),
    CLOSED(2, "已关闭"),
    ;
    // ……(value/desc 字段、构造器与 getter 同构,省略)
    /** 按状态值取中文描述,未匹配返回空串(出参 statusStr 用) */
    public static String getDescByValue(Integer value) {
        for (OrderStatusEnum e : values()) {
            if (Objects.equals(e.getValue(), value)) { return e.getDesc(); }
        }
        return "";
    }
}
/** 促销类型:优惠计算规则见 OrderServiceImpl.calcPromotionAmount */
public enum PromotionTypeEnum {
    /** 直减:discount = 每单减免金额(元) */
    FIX_AMOUNT(1, "直减"),
    /** 折扣:discount = 折扣率百分比(90=9折),优惠 = 原价 * (100-折扣率) / 100 */
    DISCOUNT(2, "折扣"),
    ;
    // ……(value/desc/构造器/getter 同构;fromValue(Integer) 遍历匹配,未匹配返回 null 由调用方兜底)
}
```

### MQ(消息体 + 发送服务)

```java
/** 订单消息体 */
@Getter
@Setter
public class OrderMsg implements Serializable {
    private Long orderId;
    private Integer orderStatus;
    private String userName;
    private String orderNo;       // 订单号(下单成功通知下游时携带)
    private String payAmount;     // 实付金额(元,字符串形式避免跨服务 BigDecimal 序列化差异)
}
/**
 * MQ 可靠投递:继承 AbstractRabbitMqService 即获得拓扑自动声明、失败落库 base_mq、消费防重、
 * 补偿重推能力(替换为项目 MQ 时需自带这四件套,见文首对照表)。
 */
@Slf4j
@Service
public class OrderMsgService extends AbstractRabbitMqService<OrderMsg> {   // [占位] → 项目 MQ 封装
    // ……(省略:getMessageType 返回 10(base_mq 补偿路由次索引)、getMessageDesc 返回"订单状态变更消息",模板方法)
    @Override
    protected void consumeMessage(OrderMsg msgBody) {
        log.info("消费订单消息: orderId={}, orderNo={}, status={}, payAmount={}, user={}", msgBody.getOrderId(),
                msgBody.getOrderNo(), msgBody.getOrderStatus(), msgBody.getPayAmount(), msgBody.getUserName());
        // 业务处理失败直接抛出:自动落库 base_mq,由补偿任务指数退避重推
    }
    /** 发送:第二个参数为业务幂等 ID(orderId),消费端按此防重 */
    public void send(OrderMsg msg) {
        provide(msg, String.valueOf(msg.getOrderId()));
    }
}
```

### 常量(order 相关锁 key)

```java
public final class BaseCacheKeyConsts {
    /** 下单幂等锁 key 模板,参数为 requestId,防重窗口复用 LOCK_TIME_IDEMPOTENT_MILLIS */
    public static final String LOCK_ORDER_SUBMIT_REQUEST_KEY = "demo:order:lock:submit:%s";
    /** 幂等防重窗口(毫秒),5 分钟内同一 requestId 视为重复提交 */
    public static final long LOCK_TIME_IDEMPOTENT_MILLIS = 5 * 60 * 1000L;
}
```
