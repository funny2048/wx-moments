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

    /** 图片文件名白名单：/images/** 静态解析防路径穿越（R6）；上传文件名为服务端 UUID 生成，亦受此校验 */
    public static final Pattern IMAGE_FILE_NAME = Pattern.compile("^[A-Za-z0-9._-]+$");

    /** Feed 分页游标解码后格式：{createdStime毫秒 13 位}:{postId 1-18 位}；id 段限 18 位防 Long.parseLong 溢出落 code=100（R2-建议7） */
    public static final Pattern FEED_CURSOR = Pattern.compile("^\\d{13}:\\d{1,18}$");

    /** 用户ID 数字串（viewerId 参数 / Cookie mockUserId 解析，B3）：1-18 位纯数字，18 位上限防 Long.parseLong 溢出 */
    public static final Pattern USER_ID = Pattern.compile("^\\d{1,18}$");
}
