package com.funny.moments.client;

import java.io.Serializable;
import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

/**
 * 优惠券信息（coupon-service 契约出参，demo）。
 *
 * @author fangli
 * @since 2026-09-20
 */
@Getter
@Setter
public class CouponVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String couponId;

    /** 券面额（元） */
    private BigDecimal amount;

    /** 券状态 1可用 2已核销 3已返还 4已过期 */
    private Integer status;
}
