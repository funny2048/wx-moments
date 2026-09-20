package com.funny.moments.common.enums;

import java.util.stream.Stream;

/**
 * 性别枚举（user.gender，值域见 sql.md user 表 COMMENT：0-未知 1-男 2-女）。
 * 出参组装 genderStr 时通过本枚举反查，业务代码禁魔法数字。
 *
 * @Author: funny2048
 * @Date: 2026-09-21
 */
public enum GenderEnum {

    UNKNOWN(0, "未知"),
    MALE(1, "男"),
    FEMALE(2, "女");

    private final Integer code;

    private final String desc;

    GenderEnum(Integer code, String desc) {
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
    public static GenderEnum byCode(Integer code) {
        return Stream.of(values()).filter(e -> e.getCode().equals(code)).findFirst().orElse(null);
    }
}
