package com.funny.moments.common.consts;

import java.util.regex.Pattern;

/**
 * 校验用预编译正则常量（禁止在方法体内 Pattern.compile，java-guide §10）。
 *
 * @author fangli
 * @since 2026-09-20
 */
public final class PatternConsts {

    private PatternConsts() {
    }

    /** 中国大陆手机号：1 开头，第二位 3-9，共 11 位 */
    public static final Pattern MOBILE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");
}
