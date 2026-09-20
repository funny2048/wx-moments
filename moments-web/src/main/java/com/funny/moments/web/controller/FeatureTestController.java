package com.funny.moments.web.controller;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.funny.moments.client.CodegenFeignClient;
import com.funny.moments.web.dto.out.LockAcquireOut;
import com.funny.moments.web.dto.out.LockConcurrentOut;
import com.funny.moments.web.dto.out.SignGenerateOut;
import com.funny.framework.core.result.ApiResult;
import com.funny.framework.log.context.ContextAwareExecutor;
import com.funny.framework.log.context.ContextAwareSpringExecutor;
import com.funny.framework.log.tracing.TraceAwareTask;
import com.funny.framework.redis.DistributedLock;
import com.funny.framework.redis.RedisLock;
import com.funny.framework.redis.RedisLockManager;
import com.funny.framework.sign.annotation.AppIdCheck;
import com.funny.framework.sign.annotation.SignVerify;
import com.funny.framework.sign.helper.SignHelper;
import com.funny.framework.sign.model.SignParamMap;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;

/**
 * 功能测试控制器 - 测试 sign 签名 / redis 分布式锁 / crypto 加解密
 */
@Slf4j
@RestController
@RequestMapping("/test")
public class FeatureTestController {

    @Autowired
    private RedisLockManager redisLockManager;


    @Resource
    private CodegenFeignClient codegenFeignClient;

    @GetMapping("/log")
    public ApiResult<String> log(String _appId) {
        log.info("FeatureTestController.log appId={}", _appId);
        log.info("log info");
        log.warn("log warn");
        log.error("log error");
        return ApiResult.succ("ok");
    }

    // ==================== Sign 模块测试 ====================

    /**
     * 生成签名 - 手动调用 SignHelper 演示签名算法
     * 算法: MD5(appKey + 按key排序的参数拼接 + appKey).toUpperCase()
     *
     * @param _appId   调用方应用标识
     * @param appKey   应用密钥
     * @param bizParam 业务参数（可选）
     */
    @PostMapping("/sign/generate")
    public ApiResult<SignGenerateOut> generateSign(
            String _appId,
            @RequestParam("appKey") String appKey,
            @RequestParam(value = "bizParam", required = false) String bizParam) {
        log.info("FeatureTestController.generateSign appId={}, appKey={}, bizParam={}", _appId, appKey, bizParam);

        SignParamMap params = new SignParamMap();
        if (bizParam != null) {
            params.put("bizParam", bizParam);
        }
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
        params.put(SignHelper.TIMESTAMP_FIELD, timestamp);

        String sign = SignHelper.getSign(appKey, params);

        SignGenerateOut result = new SignGenerateOut();
        result.setSign(sign);
        result.setTimestamp(timestamp);
        result.setAlgorithm("MD5(appKey + sortedParams[key+value] + appKey).toUpperCase()");
        return ApiResult.succ(result);
    }

    /**
     * 签名验证接口 - 需要 sign 基础设施（AppIdService + SignCache）
     * 请求时需携带: appId, _timestamp, _sign 参数
     * 签名计算方式同 /test/sign/generate，appKey 从 appId 对应的配置中获取
     */
    @SignVerify
    @PostMapping("/sign/protected")
    public ApiResult<String> signProtected(String _appId) {
        log.info("FeatureTestController.signProtected appId={}", _appId);
        return ApiResult.succ("签名验证通过");
    }

    /**
     * AppId 检查接口 - 仅验证 appId 是否合法（不校验签名）
     * 请求时需携带: appId 参数
     */
    @AppIdCheck
    @GetMapping("/sign/appid-check")
    public ApiResult<String> appIdCheck(String _appId) {
        log.info("FeatureTestController.appIdCheck appId={}", _appId);
        return ApiResult.succ("appId验证通过");
    }

    // ==================== Redis 分布式锁测试 ====================

    /**
     * 手动获取/释放分布式锁 - 使用 RedisLockManager
     * 演示 try-with-resources 模式自动释放
     *
     * @param _appId   调用方应用标识
     * @param key      锁的业务key
     * @param expireMs 锁过期时间（毫秒）
     */
    @GetMapping("/lock/manual")
    public ApiResult<LockAcquireOut> manualLock(
            String _appId,
            @RequestParam("key") String key,
            @RequestParam(value = "expireMs", defaultValue = "10000") Long expireMs) {
        log.info("FeatureTestController.manualLock appId={}, key={}, expireMs={}", _appId, key, expireMs);

        String lockKey = "test:lock:" + key;
        RedisLock lock = null;
        try {
            lock = redisLockManager.fetchAndTryLock(lockKey, expireMs);
            LockAcquireOut result = new LockAcquireOut();
            result.setLockKey(lockKey);
            result.setAcquired(true);
            result.setLockValue(lock.getValue());
            return ApiResult.succ(result);
        } catch (Exception e) {
            log.error("FeatureTestController.manualLock 获取分布式锁失败 lockKey={}", lockKey, e);
            return ApiResult.fail("获取锁失败");
        } finally {
            if (lock != null) {
                lock.releaseLock();
            }
        }
    }

    /**
     * 注解式分布式锁 - 使用 @DistributedLock 注解
     * AOP 自动加锁/释放，业务代码无感知
     *
     * @param _appId 调用方应用标识
     * @param taskId 任务ID，会作为锁的一部分
     */
    @DistributedLock(key = "'test:lock:annotation:' + #taskId", expireInMilliseconds = 10000)
    @GetMapping("/lock/annotation")
    public ApiResult<String> annotationLock(String _appId, @RequestParam("taskId") String taskId) {
        log.info("FeatureTestController.annotationLock appId={}, taskId={}", _appId, taskId);
        return ApiResult.succ("注解式分布式锁获取成功, taskId=" + taskId);
    }

    /**
     * 并发竞争锁测试 - 模拟多线程竞争同一把锁
     * 验证分布式锁的互斥性：同一时刻只有一个线程能获取到锁
     *
     * @param _appId      调用方应用标识
     * @param threadCount 并发线程数
     */
    @GetMapping("/lock/concurrent")
    public ApiResult<LockConcurrentOut> concurrentLock(
            String _appId,
            @RequestParam(value = "threadCount", defaultValue = "5") Integer threadCount) {
        log.info("FeatureTestController.concurrentLock appId={}, threadCount={}", _appId, threadCount);

        String lockKey = "test:lock:concurrent:" + System.currentTimeMillis();
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executor.submit(() -> {
                RedisLock lock = null;
                try {
                    lock = redisLockManager.fetch(lockKey, 5000);
                    if (lock.tryLock()) {
                        successCount.incrementAndGet();
                        log.info("线程{}获取锁成功", index);
                        Thread.sleep(1000);
                    } else {
                        failCount.incrementAndGet();
                        log.info("线程{}获取锁失败", index);
                    }
                } catch (Exception e) {
                    log.error("线程{}执行异常", index, e);
                } finally {
                    if (lock != null) {
                        lock.releaseLock();
                    }
                    latch.countDown();
                }
            });
        }

        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        executor.shutdown();

        LockConcurrentOut result = new LockConcurrentOut();
        result.setLockKey(lockKey);
        result.setThreadCount(threadCount);
        result.setSuccessCount(successCount.get());
        result.setFailCount(failCount.get());
        return ApiResult.succ(result);
    }


    @GetMapping("/thread/trace")
    public ApiResult<Void> threadTrace(String _appId) {
        log.info("FeatureTestController.threadTrace appId={}", _appId);
        ContextAwareExecutor executor = new ContextAwareExecutor(4, 8, 60, TimeUnit.SECONDS, new LinkedBlockingQueue<>());
        executor.submit(() -> {
            log.info("executor.submit threadId={}", Thread.currentThread().threadId());
        });

        // Spring 线程池
        ContextAwareSpringExecutor springExecutor = new ContextAwareSpringExecutor();
        springExecutor.setCorePoolSize(4);
        springExecutor.setMaxPoolSize(8);
        springExecutor.setQueueCapacity(100);
        springExecutor.setThreadNamePrefix("ctx-aware-");
        springExecutor.initialize();
        springExecutor.submit(() -> {
            log.info("springExecutor.submit threadId={}", Thread.currentThread().threadId());
        });

        // 单任务追踪
        new TraceAwareTask() {
            @Override
            protected void execute() {
                log.info("TraceAwareTask threadId={}", Thread.currentThread().threadId());
            }
        }.start();
        return ApiResult.succ();
    }

    @GetMapping("/feign")
    public ApiResult<String> heartbeat(String _appId) {
        log.info("FeatureTestController.heartbeat appId={}", _appId);
        String result = codegenFeignClient.heartbeat();
        log.info("FeatureTestController.heartbeat feign 响应 result={}", result);
        return ApiResult.succ(result);
    }
}
