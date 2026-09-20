package com.funny.moments.web.dto.out;

import java.io.Serializable;

import lombok.Getter;
import lombok.Setter;

/**
 * 手动分布式锁获取结果。
 *
 * @author fangli
 * @since 2026-09-18
 */
@Getter
@Setter
public class LockAcquireOut implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 锁 key */
    private String lockKey;

    /** 是否获取成功 */
    private Boolean acquired;

    /** 锁持有标识（锁 value） */
    private String lockValue;
}
