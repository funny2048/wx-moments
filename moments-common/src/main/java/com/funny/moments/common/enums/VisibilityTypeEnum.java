package com.funny.moments.common.enums;

import java.util.stream.Stream;

/**
 * 朋友圈可见范围枚举（post.visibility_type，值域：1-公开 2-私密 3-部分可见 4-不给谁看）。
 * canView 统一判断（VisibilityServiceImpl）与出参 visibilityTypeStr 组装均通过本枚举反查，业务代码禁魔法数字。
 *
 * @Author: funny2048
 * @Date: 2026-09-21
 */
public enum VisibilityTypeEnum {

    PUBLIC(1, "公开"),
    PRIVATE(2, "私密"),
    PART_VISIBLE(3, "部分可见"),
    EXCLUDE(4, "不给谁看");

    private final Integer code;

    private final String desc;

    VisibilityTypeEnum(Integer code, String desc) {
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
     * 按 code 反查枚举，未匹配返回 null（发帖入参校验 byCode==null 即抛 1001）。
     */
    public static VisibilityTypeEnum byCode(Integer code) {
        return Stream.of(values()).filter(e -> e.getCode().equals(code)).findFirst().orElse(null);
    }
}
