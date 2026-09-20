package com.funny.moments.web.controller.infra;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.funny.framework.core.result.ApiResult;
import com.funny.framework.crypto.exception.CryptoException;
import com.funny.framework.crypto.model.CryptoHashResponse;
import com.funny.framework.crypto.utils.CryptoHashUtils;

import lombok.extern.slf4j.Slf4j;

/**
 * 加解密 demo：funny-framework-crypto 工具演示。
 */
@Slf4j
@RestController
@RequestMapping(value = "/api/security")
public class SecurityController {

    @GetMapping("/des")
    public ApiResult<CryptoHashResponse> decrypt(String _appId, String str) {
        log.info("SecurityController.decrypt appId={}, str={}", _appId, str);
        try {
            CryptoHashResponse cryptoHashResponse = CryptoHashUtils.decrypt(str);
            return ApiResult.succ(cryptoHashResponse);
        } catch (CryptoException e) {
            log.error("SecurityController.decrypt 解密异常 str={}", str, e);
            return ApiResult.fail("解密失败");
        }
    }

    @GetMapping("/enc")
    public ApiResult<CryptoHashResponse> encrypt(String _appId, String str) {
        log.info("SecurityController.encrypt appId={}, str={}", _appId, str);
        try {
            CryptoHashResponse cryptoHashResponse = CryptoHashUtils.encrypt(str);
            return ApiResult.succ(cryptoHashResponse);
        } catch (CryptoException e) {
            log.error("SecurityController.encrypt 加密异常 str={}", str, e);
            return ApiResult.fail("加密失败");
        }
    }
}
