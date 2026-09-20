package com.funny.moments.social;

import java.util.List;

import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;

/**
 * 测试响应 JSON 读取辅助（jayway JsonPath，随 spring-boot-starter-test 引入）。
 * 数值统一按 Number 兜底（Jackson 对整型返回 Integer/Long，比较前统一转 long/int）。
 */
final class JsonPathUtils {

    private JsonPathUtils() {
    }

    /** 读取 int 字段（$.code 等）。 */
    static int readInt(String body, String path) {
        return readNumber(body, path).intValue();
    }

    /** 读取数值字段。 */
    static Number readNumber(String body, String path) {
        return (Number) readNullable(body, path);
    }

    /** 读取数值数组；路径不存在（如空数组场景字段缺省）返回空列表。 */
    @SuppressWarnings("unchecked")
    static List<Number> readNumberList(String body, String path) {
        try {
            return (List<Number>) JsonPath.read(body, path);
        } catch (PathNotFoundException e) {
            return List.of();
        }
    }

    /** 读取字符串字段。 */
    static String readString(String body, String path) {
        Object value = readNullable(body, path);
        return value == null ? null : value.toString();
    }

    /** 读取字符串数组（如 $.data.imageUrls）。 */
    @SuppressWarnings("unchecked")
    static List<String> readStringList(String body, String path) {
        try {
            return (List<String>) JsonPath.read(body, path);
        } catch (PathNotFoundException e) {
            return List.of();
        }
    }

    /** 读取可空字段（字段存在但值为 null 时返回 null；路径缺失也返回 null）。 */
    static Object readNullable(String body, String path) {
        try {
            return JsonPath.read(body, path);
        } catch (PathNotFoundException e) {
            return null;
        }
    }
}
