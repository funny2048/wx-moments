package com.funny.moments.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.funny.framework.core.result.ApiResult;
import com.funny.framework.sign.exception.SignException;

/**
 * C 端签名认证异常处理：比 starter-web 兜底的 GlobalExceptionAdvice（返回码 100 系统异常）更具体，
 * 让客户端能拿到签名失败的原因描述（缺参/过期/签名错误等）。
 *
 * @author fangli
 * @since 2026-09-20
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SignExceptionAdvice {

    private static final Logger log = LoggerFactory.getLogger(SignExceptionAdvice.class);

    @ExceptionHandler(SignException.class)
    public ApiResult<Void> handleSignException(SignException e) {
        log.warn("签名认证失败 uri-message={}", e.getMessage());
        return ApiResult.fail(e.getMessage());
    }
}
