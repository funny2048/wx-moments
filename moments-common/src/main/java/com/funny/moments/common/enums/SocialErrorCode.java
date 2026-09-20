package com.funny.moments.common.enums;

import com.funny.framework.core.exception.ErrorCode;

/**
 * 社交业务错误码（错误码 >1000 业务段，编码规则同 ReturnCode 头注释）。
 * 与既有 ReturnCode（程序预定义段）并行：业务异常统一抛 BizException(SocialErrorCode 的 code/message)，
 * 由 web 层 BizExceptionAdvice 透传为 ApiResult。值域与 api.md §1.6 通用错误码表完全一致。
 *
 * @Author: funny2048
 * @Date: 2026-09-21
 */
public enum SocialErrorCode implements ErrorCode {

    PARAM_INVALID(1001, "参数校验失败"),
    USER_NOT_FOUND(1002, "用户不存在"),
    CURRENT_USER_REQUIRED(1003, "未选择当前用户"),
    TAG_NOT_FOUND(1004, "标签不存在"),
    TAG_NAME_DUPLICATED(1005, "标签名重复"),
    NOT_TAG_OWNER(1006, "非标签归属人"),
    FRIEND_REQUIRED(1007, "仅可对好友打标签"),
    TAG_ALREADY_BOUND(1008, "标签已绑定该好友"),
    POST_NOT_FOUND(1010, "帖子不存在或已删除"),
    POST_NO_PERMISSION(1011, "无权查看该帖子"),
    NOT_POST_AUTHOR(1012, "仅作者可删除该帖子"),
    IMAGE_FORMAT_UNSUPPORTED(1020, "图片格式不支持"),
    IMAGE_SIZE_EXCEEDED(1021, "图片超过5MB"),
    IMAGE_COUNT_EXCEEDED(1022, "单帖图片超过9张"),
    CURSOR_INVALID(1030, "分页游标非法"),
    MOCK_CONFIG_INVALID(1040, "模拟数据配置非法");

    private final int code;

    private final String message;

    SocialErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override
    public int getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
    }
}
