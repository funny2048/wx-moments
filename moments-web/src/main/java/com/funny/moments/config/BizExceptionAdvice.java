package com.funny.moments.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import com.funny.framework.core.exception.BizException;
import com.funny.framework.core.result.ApiResult;
import com.funny.moments.common.enums.SocialErrorCode;

/**
 * 社交业务异常全局处理（design §6.1/§9，A10 + R2-NB1 + R3 + Stage 6 裁决8 追加，共四类）：
 * <ul>
 * <li>① {@link BizException} → 透传业务 code/msg——框架 GlobalExceptionAdvice 仅兜底 Exception→code 100，无法透传业务码</li>
 * <li>② {@link MethodArgumentTypeMismatchException}（数值参数非数字，如 pageNo=abc、/api/users/xx，Spring 绑定层
 *     抛出根本到不了 Service 校验）→ 统一转 1001（B2"显式越界含非数字抛 1001"承诺的承载机制）</li>
 * <li>③ {@link HttpMessageNotReadableException}（JSON 请求体字段类型非法，如 body 传 "visibilityType":"abc"，
 *     Jackson 反序列化失败）→ 统一转 1001（与 ② 同模式）</li>
 * <li>④ {@link MaxUploadSizeExceededException}（文件超容器 multipart 上限，容器层抛出根本到不了业务校验）
 *     → 统一转 1021（api.md">5MB 返回 1021"承诺的承载机制）</li>
 * </ul>
 * 仿既有 SignExceptionAdvice 范式（@RestControllerAdvice + HIGHEST_PRECEDENCE），不改既有 advice 行为。
 *
 * @Author: funny2048
 * @Date: 2026-09-21
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class BizExceptionAdvice {

    private static final Logger log = LoggerFactory.getLogger(BizExceptionAdvice.class);

    @ExceptionHandler(BizException.class)
    public ApiResult<Void> handleBizException(BizException e) {
        log.warn("业务异常 code={}, msg={}", e.getCode(), e.getMessage());
        return ApiResult.buildFailure(e.getCode(), e.getMessage());
    }

    /**
     * R2-NB1：非数字数值参数（query/路径）在绑定层抛出，到不了 Service 校验，必须在此拦截统一转 1001，
     * 否则落框架 GlobalExceptionAdvice 兜底 code=100，违背 api.md"含非数字→1001"承诺。
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ApiResult<Void> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        log.warn("参数类型绑定失败 name={}, value={}, targetType={}", e.getName(), e.getValue(),
                e.getRequiredType() == null ? null : e.getRequiredType().getSimpleName());
        return buildParamInvalid();
    }

    /**
     * R3 裁决追加：JSON 请求体字段类型非法（Jackson 反序列化失败）转 1001，
     * 防 POST/PUT body 数值字段传字符串落 code=100 与阶段四用例口径分裂；
     * 日志仅记 msg 不回传 e 细节，避免泄露内部结构。
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ApiResult<Void> handleBodyNotReadable(HttpMessageNotReadableException e) {
        log.warn("请求体解析失败 msg={}", e.getMessage());
        return buildParamInvalid();
    }

    /**
     * Stage 6 编排裁决8（T100 缺口3）：文件超过容器 multipart 上限（单文件 6MB / 单请求 12MB，见 WebConfig）
     * 时在容器层抛 MaxUploadSizeExceededException，到不了业务校验，原落框架兜底 code=100，
     * 违背 api.md">5MB 返回 1021"契约，必须在此拦截统一转 1021；
     * 日志仅记上限值不回传 e 细节，避免泄露容器配置。
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ApiResult<Void> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException e) {
        log.warn("上传超出容器 multipart 上限 maxUploadSize={}B，统一转 1021", e.getMaxUploadSize());
        return ApiResult.buildFailure(SocialErrorCode.IMAGE_SIZE_EXCEEDED.getCode(),
                SocialErrorCode.IMAGE_SIZE_EXCEEDED.getMessage());
    }

    /**
     * PARAM_INVALID 统一出参辅助（1001/参数校验失败）。
     */
    private ApiResult<Void> buildParamInvalid() {
        return ApiResult.buildFailure(SocialErrorCode.PARAM_INVALID.getCode(),
                SocialErrorCode.PARAM_INVALID.getMessage());
    }
}
