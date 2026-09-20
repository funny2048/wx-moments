// 模板样例【触发型】：自造数据 + @Transactional 回滚 + mapper 查库硬断言
// 仅参考写法，包名/类名/业务语义按真实项目替换，禁止直接拷贝
package cn.com.example.activity.provider;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.annotation.Resource;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

import cn.com.example.activity.contract.order.OrderCreateIn;
import cn.com.example.activity.provider.dao.domain.order.OrderRecord;
import cn.com.example.activity.provider.dao.mapper.order.OrderRecordMapper;
import cn.com.example.activity.provider.service.order.IOrderCreateService;
import cn.hutool.core.lang.UUID;

/**
 * 订单创建触发型集成测试（连 dev 环境）
 * 写法要点：真实业务形态入参自造数据，方法级 @Transactional 回滚不留脏数据，
 * 断言必须 mapper 查库验证落库结果，禁止只断言返回值
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = ProviderApplication.class)
@ActiveProfiles("job-dev")
public class OrderCreateTriggerTests {

    static {
        System.setProperty("env", "DEV");
    }

    @Resource
    private IOrderCreateService orderCreateService;

    @Resource
    private OrderRecordMapper orderRecordMapper;

    /**
     * 正例：创建订单成功
     * 规则1 返回 orderId 非空
     * 规则2 order_record 按 requestId 可查到 1 行，status=INIT，金额与入参一致
     */
    @Test
    @Transactional
    public void createOrderSuccess() {
        OrderCreateIn in = buildValidIn();
        String orderId = orderCreateService.create(in);

        Assert.assertNotNull("orderId 应非空", orderId);
        OrderRecord record = orderRecordMapper.getByRequestId(in.getRequestId());
        Assert.assertNotNull("订单应已落库", record);
        Assert.assertEquals("状态应为 INIT", "INIT", record.getStatus());
        Assert.assertEquals("落库金额应与入参一致", in.getAmount(), record.getAmount(), 0.01);
    }

    /**
     * 反例：非法入参被拒
     * 规则1 抛 BizException 且 code=ERR_PARAM
     * 规则2 order_record 不落任何行（查库断言，非只看返回值）
     */
    @Test
    @Transactional
    public void createOrderRejectInvalidParam() {
        OrderCreateIn in = buildValidIn();
        in.setSkuId(null); // 必填参数置空

        try {
            orderCreateService.create(in);
            Assert.fail("非法入参应抛 BizException");
        } catch (BizException e) {
            Assert.assertEquals("ERR_PARAM", e.getCode());
        }
        Assert.assertEquals("非法入参不应落库", 0, orderRecordMapper.countByRequestId(in.getRequestId()));
    }

    /**
     * 幂等-重复：同一 requestId 连续提交 2 次
     * 规则1 两次调用均不抛错
     * 规则2 order_record 仅 1 行有效数据
     */
    @Test
    @Transactional
    public void createOrderIdempotentRepeat() {
        OrderCreateIn in = buildValidIn();
        orderCreateService.create(in);
        orderCreateService.create(in);

        Assert.assertEquals("重复提交应仅 1 行有效数据", 1, orderRecordMapper.countByRequestId(in.getRequestId()));
    }

    /**
     * 幂等-并发：20 线程同 requestId 对齐起跑并发提交
     * 规则1 仅 1 行有效数据，其余为重复拒绝或幂等返回
     * 口径说明：并发窗口依赖分布式锁，dev 环境锁正常时应稳定通过
     */
    @Test
    @Transactional
    public void createOrderConcurrentSameRequestId() throws Exception {
        OrderCreateIn in = buildValidIn();
        int threads = 20;
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    orderCreateService.create(in);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                } catch (Exception ignored) {
                    // 单线程冲突拒绝属预期，此处不判异常口径
                }
            });
        }
        ready.await();
        start.countDown();
        pool.shutdown();

        Assert.assertEquals("并发提交应仅 1 行有效数据", 1, orderRecordMapper.countByRequestId(in.getRequestId()));
    }

    private OrderCreateIn buildValidIn() {
        OrderCreateIn in = new OrderCreateIn();
        in.setRequestId(UUID.randomUUID().toString());
        in.setSkuId(9527L);
        in.setAmount(199.00);
        in.setBuyerId(10001L);
        return in;
    }
}
