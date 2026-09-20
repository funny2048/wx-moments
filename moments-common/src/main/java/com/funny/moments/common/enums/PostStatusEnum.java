package com.funny.moments.common.enums;

import java.util.stream.Stream;

/**
 * 帖子业务状态枚举（post.status，值域：1-正常 0-删除）。
 * 软删走 is_del（@TableLogic），status 保留业务态快照；发帖固定 NORMAL。
 *
 * @Author: funny2048
 * @Date: 2026-09-21
 */
public enum PostStatusEnum {

    DELETED(0, "删除"),
    NORMAL(1, "正常");

    private final Integer code;

    private final String desc;

    PostStatusEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public Integer getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    /**
     * 按 code 反查枚举，未匹配返回 null（调用方自行兜底）。
     */
    public static PostStatusEnum byCode(Integer code) {
        return Stream.of(values()).filter(e -> e.getCode().equals(code)).findFirst().orElse(null);
    }
}
