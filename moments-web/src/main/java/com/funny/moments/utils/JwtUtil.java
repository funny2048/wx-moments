package com.funny.moments.utils;

import com.auth0.jwt.JWT;
import com.auth0.jwt.exceptions.JWTDecodeException;
import com.auth0.jwt.interfaces.DecodedJWT;

import lombok.extern.slf4j.Slf4j;

/**
 * JWT 解析工具 demo。
 *
 * @author funny2048
 * @date 2018/11/18 12:18
 */
@Slf4j
public class JwtUtil {

    private static final String USER_NAME = "sys";

    /**
     * 获得token中的信息无需secret解密也能获得
     *
     * @param token 令牌
     * @return token中包含的用户名，解析失败返回 null
     */
    public static String getUserString(String token) {
        try {
            DecodedJWT jwt = JWT.decode(token);
            return jwt.getClaim(USER_NAME).asString();
        } catch (JWTDecodeException e) {
            log.error("JwtUtil.getUserString 解析token异常 token={}", token, e);
            return null;
        }
    }
}
