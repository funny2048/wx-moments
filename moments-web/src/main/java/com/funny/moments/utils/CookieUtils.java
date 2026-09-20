package com.funny.moments.utils;

import java.util.HashMap;
import java.util.Map;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * @author funny2048
 * @date 2018/11/18 12:18
 */
public class CookieUtils {

    /**
     * 更新cookie
     *
     * @param response response
     * @param name     cookie名称
     * @param value    cookie值
     */
    public static void addCookie(HttpServletResponse response, String name, String value, Integer maxAge, String domain) {
        Cookie cookie = new Cookie(name, value);
        cookie.setDomain(domain);
        cookie.setPath("/");
        cookie.setMaxAge(maxAge);
        cookie.setHttpOnly(Boolean.TRUE);
        response.addCookie(cookie);
    }

    /**
     * 更新cookie
     *
     * @param response response
     * @param name     cookie名称
     * @param value    cookie值
     */
    public static void updateCookie(HttpServletResponse response, String name, String value, Integer maxAge, String domain) {
        Cookie cookie = new Cookie(name, value);
        cookie.setDomain(domain);
        cookie.setPath("/");
        cookie.setMaxAge(maxAge);
        cookie.setHttpOnly(Boolean.TRUE);
        response.addCookie(cookie);
    }


    /**
     * 更新cookie
     *
     * @param response response
     * @param cookie   cookie
     */
    public static void updateCookie(HttpServletResponse response, Cookie cookie) {
        response.addCookie(cookie);
    }


    /**
     * 获取cookie
     *
     * @param request request
     * @param name    cookie的名称
     * @return cookie
     */
    public static Cookie getCookieByName(HttpServletRequest request, String name) {
        Map<String, Cookie> cookieMap = readCookieMap(request);
        if (cookieMap.containsKey(name)) {
            return cookieMap.get(name);
        } else {
            return null;
        }
    }

    /**
     * 获取cookie的值
     *
     * @param request request
     * @param name    cookie的名称
     * @return cookie值
     */
    public static String getCookieValueByName(HttpServletRequest request, String name) {
        Map<String, Cookie> cookieMap = readCookieMap(request);
        if (cookieMap.containsKey(name)) {
            return cookieMap.get(name).getValue();
        } else {
            return null;
        }
    }

    /**
     * 获取cookie转成map
     *
     * @param request request
     * @return map
     */
    public static Map<String, Cookie> readCookieMap(HttpServletRequest request) {
        Map<String, Cookie> cookieMap = new HashMap<String, Cookie>();
        Cookie[] cookies = request.getCookies();
        if (null != cookies) {
            for (Cookie cookie : cookies) {
                cookieMap.put(cookie.getName(), cookie);
            }
        }
        return cookieMap;
    }


}
