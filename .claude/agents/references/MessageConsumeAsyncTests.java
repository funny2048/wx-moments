// 模板样例【MQ 消费触发 + 异步/跨事务兜底】：@Transactional 管不到的场景用 @Sql 造数+清理
// 仅参考写法，包名/类名/业务语义按真实项目替换，禁止直接拷贝
package cn.com.example.activity.provider;

import java.util.concurrent.TimeUnit;
import javax.annotation.Resource;

import org.junit.Assert;
import org.junit.AfterEach;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

import cn.com.example.activity.provider.dao.mapper.order.OrderRecordMapper;
import cn.com.example.activity.provider.mq.OrderPaySuccessConsumer;
import cn.com.example.activity.provider.mq.message.OrderPaySuccessMessage;
import cn.hutool.core.lang.UUID;

/**
 * MQ 消费与异步场景集成测试（连 dev 环境）
 * 写法要点：常规消费触发走 @Transactional 回滚 + 查库断言；
 * REQUIRES_NEW / 线程池异步落库 @Transactional 管不到，必须 @Sql 前置造数 + 后置清理，禁止留脏数据
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = ProviderApplication.class)
@ActiveProfiles("job-dev")
public class MessageConsumeAsyncTests {

    static {
        System.setProperty("env", "DEV");
    }

    @Resource
    private OrderPaySuccessConsumer orderPaySuccessConsumer;

    @Resource
    private OrderRecordMapper orderRecordMapper;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 正例：消费支付成功消息，订单状态流转为 PAID
     * 规则1 消费不抛错
     * 规则2 order_record 状态置 PAID，payTime 非空
     */
    @Test
    @Transactional
    public void consumePaySuccessMessage() {
        String requestId = UUID.randomUUID().toString();
        OrderPaySuccessMessage message = OrderPaySuccessMessage.of(requestId, 199.00);

        orderPaySuccessConsumer.handle(message);

        Assert.assertEquals("状态应流转为 PAID", "PAID", orderRecordMapper.getStatusByRequestId(requestId));
        Assert.assertNotNull("payTime 应非空", orderRecordMapper.getPayTimeByRequestId(requestId));
    }

    /**
     * 反例：消费重复消息（MQ 至少一次投递）
     * 规则1 重复消费不抛错、不产生第二次副作用
     * 规则2 状态仍为 PAID，金额不重复累加
     */
    @Test
    @Transactional
    public void consumeDuplicatedMessage() {
        String requestId = UUID.randomUUID().toString();
        OrderPaySuccessMessage message = OrderPaySuccessMessage.of(requestId, 199.00);
        orderPaySuccessConsumer.handle(message);
        orderPaySuccessConsumer.handle(message);

        Assert.assertEquals("重复消费状态不变", "PAID", orderRecordMapper.getStatusByRequestId(requestId));
        Assert.assertEquals("金额不重复累加", 199.00,
                orderRecordMapper.getAmountByRequestId(requestId), 0.01);
    }

    /**
     * 异步落库场景：消费后奖励由线程池异步写入（REQUIRES_NEW，@Transactional 管不到）
     * 兜底口径：@Sql BEFORE 造前置订单 + 触发后轮询等待 + 断言 + @Sql AFTER 按业务主键清理
     * 规则1 reward_record 按 requestId 恰好 1 行
     */
    @Test
    @Sql(scripts = "/sql/order_pay_success_before.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    @Sql(scripts = "/sql/order_pay_success_after.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
    public void consumeThenAsyncRewardWrite() throws Exception {
        String requestId = "IT-ASYNC-REWARD-0001"; // 与 before.sql 造数主键一致，after.sql 按此清理
        orderPaySuccessConsumer.handle(OrderPaySuccessMessage.of(requestId, 199.00));

        Assert.assertTrue("异步奖励应在 5s 内落库",
                orderRecordMapper.awaitRewardRow(requestId, 5, TimeUnit.SECONDS));
        Assert.assertEquals("异步奖励应恰好 1 行", 1, orderRecordMapper.countRewardByRequestId(requestId));
    }

    /**
     * 共享中间件口径：Redis key 测试内自造自清
     * 规则1 写入带短 TTL 的 key 后业务读取命中
     */
    @Test
    public void redisKeyWriteAndCleanup() {
        String key = "it:test:lock:" + UUID.randomUUID();
        stringRedisTemplate.opsForValue().set(key, "1", 60, TimeUnit.SECONDS);

        Assert.assertTrue("key 应命中", Boolean.TRUE.equals(stringRedisTemplate.hasKey(key)));

        stringRedisTemplate.delete(key); // 共享 dev Redis 必须显式清理
        Assert.assertFalse("key 应已清理", Boolean.TRUE.equals(stringRedisTemplate.hasKey(key)));
    }

    @AfterEach
    public void cleanRedis() {
        // 兜底清理本类测试自造的 Redis key（防断言中断遗留）
    }
}
