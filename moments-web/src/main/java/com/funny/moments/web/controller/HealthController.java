package com.funny.moments.web.controller;

import com.funny.framework.core.result.ApiResult;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.extern.slf4j.Slf4j;

/**
 * 健康检查。
 */
@RestController
@Slf4j
public class HealthController {

    @GetMapping("/heartbeat")
    public ApiResult<String> heartbeat(String _appId) {
        log.info("HealthController.heartbeat appId={}", _appId);
        return ApiResult.succ("ok");
    }

}
