package com.funny.moments.common.enums;

/**
 * 返回码规则，0 成功
 * 5xx>code>0 程序预定义的错误
 * code>1000 业务上的错误
 * <0 服务运行错误
 * @Author: funny2048
 * @Date: 2025/1/22
 */
public enum ReturnCode {
    SUCCESS(0, "success"),
    FAIL(-1, "失败"),
    // =====================以下为程序预设置错误================================
    OPERATE_LIMIT_CODE(429, "限流"),
    UNAUTHORIZED(401, "权限不足"),
    FORBIDDEN(403, "需要登录"),
    GET_LOCK_FAIL_CODE(100, "获取锁失败"),


    ;

    private Integer value;

    private String desc;

    ReturnCode(Integer value, String desc) {
        this.value = value;
        this.desc = desc;
    }

    public Integer getValue() {
        return value;
    }

    public String getDesc() {
        return desc;
    }
}
