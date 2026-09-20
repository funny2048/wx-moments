package com.funny.moments.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.funny.framework.core.result.ApiResult;

/**
 * 优惠券服务 Feign 客户端（demo：下单查券/核销/失败还券补偿）。
 * <p>
 * 生产环境通过 feign.coupon.url 指向真实券服务；demo 默认指向本机必然调用失败，
 * 用于示范「强依赖 RPC 失败 → 下单失败」与「事务失败 → 还券补偿」两条路径的代码形态。
 * 日志铁律：调用处记录 URL/入参/响应/耗时（java-guide §11），本客户端由 OpenFeign FULL 级日志兜底。
 *
 * @author fangli
 * @since 2026-09-20
 */
@FeignClient(name = "coupon-service", url = "${feign.coupon.url:http://localhost:8080}")
public interface CouponFeignClient {

    /**
     * 查用户优惠券（校验归属与可用状态），返回券面额
     */
    @GetMapping("/coupon/query")
    ApiResult<CouponVO> queryCoupon(@RequestParam("userId") String userId, @RequestParam("couponId") String couponId);

    /**
     * 核销优惠券（下单占用）。成功返回 code=0；券已用/不存在等业务失败返回非 0
     */
    @PostMapping("/coupon/consume")
    ApiResult<Void> consumeCoupon(@RequestParam("couponId") String couponId, @RequestParam("orderNo") String orderNo);

    /**
     * 返还优惠券（下单失败补偿）
     */
    @PostMapping("/coupon/return")
    ApiResult<Void> returnCoupon(@RequestParam("couponId") String couponId, @RequestParam("orderNo") String orderNo);
}
