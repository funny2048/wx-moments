package com.funny.moments.common.utils;


import org.apache.commons.lang3.StringUtils;

import jakarta.servlet.http.HttpServletRequest;

/**
 * IP 工具类
 *
 * <p>获取客户端真实 IP，优先读取反向代理头部。</p>
 *
 * @author ai-designer
 * @since 2026-06-29
 */
public final class IpUtils {

    private static final String HEADER_X_FORWARDED_FOR = "X-Forwarded-For";
    private static final String HEADER_X_REAL_IP = "X-Real-IP";
    private static final String UNKNOWN = "unknown";

    private IpUtils() {
        // 工具类禁止实例化
    }

    /**
     * 获取客户端真实 IP
     *
     * <p>优先顺序：X-Forwarded-For → X-Real-IP → request.getRemoteAddr()</p>
     *
     * @param request HTTP 请求
     * @return 客户端 IP，获取不到返回 remoteAddr
     */
    public static String getClientIp(HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }
        String ip = request.getHeader(HEADER_X_FORWARDED_FOR);
        if (isEffectiveIp(ip)) {
            // 多级代理时取第一个真实 IP
            int idx = ip.indexOf(',');
            if (idx != -1) {
                ip = ip.substring(0, idx);
            }
            return ip.trim();
        }
        ip = request.getHeader(HEADER_X_REAL_IP);
        if (isEffectiveIp(ip)) {
            return ip.trim();
        }
        return request.getRemoteAddr();
    }

    private static boolean isEffectiveIp(String ip) {
        return StringUtils.isNotBlank(ip) && !UNKNOWN.equalsIgnoreCase(ip.trim());
    }
}
