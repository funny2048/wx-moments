package com.funny.moments.web.dto.out;

import java.io.Serializable;

import lombok.Getter;
import lombok.Setter;

/**
 * 签名生成结果：调用方将 timestamp / sign 作为 _timestamp / _sign 参数携带即可通过 @SignVerify 校验。
 *
 * @author fangli
 * @since 2026-09-18
 */
@Getter
@Setter
public class SignGenerateOut implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 签名值 */
    private String sign;

    /** 参与签名的秒级时间戳 */
    private String timestamp;

    /** 签名算法说明 */
    private String algorithm;
}
