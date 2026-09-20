package com.funny.moments.common.utils;

import org.apache.commons.lang3.StringUtils;

/**
 * 敏感数据脱敏工具（出参展示前统一处理，java-guide §9）。
 *
 * @author fangli
 * @since 2026-09-20
 */
public final class DesensitizeUtils {

    private DesensitizeUtils() {
    }

    /**
     * 中国大陆手机号脱敏：137****0969（保留前3后4）。
     * 长度异常时统一回退为 ****，任何分支都不会外露明文。
     */
    public static String maskMobile(String mobile) {
        if (StringUtils.isBlank(mobile) || mobile.length() != 11) {
            return "****";
        }
        return mobile.substring(0, 3) + "****" + mobile.substring(7);
    }
}
