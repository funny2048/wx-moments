package com.funny.moments.web.dto.out;

import java.io.Serializable;

import lombok.Getter;
import lombok.Setter;

/**
 * 并发竞争锁测试结果。
 *
 * @author fangli
 * @since 2026-09-18
 */
@Getter
@Setter
public class LockConcurrentOut implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 锁 key */
    private String lockKey;

    /** 并发线程数 */
    private Integer threadCount;

    /** 获取锁成功次数 */
    private Integer successCount;

    /** 获取锁失败次数 */
    private Integer failCount;
}
