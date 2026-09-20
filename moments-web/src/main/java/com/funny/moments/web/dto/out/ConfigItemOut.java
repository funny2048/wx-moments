package com.funny.moments.web.dto.out;

import java.io.Serializable;

import lombok.Getter;
import lombok.Setter;

/**
 * 配置项键值对。
 *
 * @author fangli
 * @since 2026-09-18
 */
@Getter
@Setter
public class ConfigItemOut implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 配置 key */
    private String key;

    /** 配置 value */
    private String value;

    public ConfigItemOut() {
    }

    public ConfigItemOut(String key, String value) {
        this.key = key;
        this.value = value;
    }
}
