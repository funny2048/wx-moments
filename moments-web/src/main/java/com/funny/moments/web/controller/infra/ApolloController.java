package com.funny.moments.web.controller.infra;

import java.util.ArrayList;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.funny.moments.web.dto.out.ConfigItemOut;
import com.funny.framework.core.result.ApiResult;

import lombok.extern.slf4j.Slf4j;

/**
 * Apollo 配置查询 demo：接入 Apollo 后从默认命名空间读取配置并按 key/value 输出。
 */
@Slf4j
@RestController
@RequestMapping(value = "/api/config")
public class ApolloController {

    @GetMapping("/apollo")
    public ApiResult<List<ConfigItemOut>> apollo(String _appId) {
        log.info("ApolloController.apollo appId={}", _appId);
        List<ConfigItemOut> configList = new ArrayList<>();
        // 接入 Apollo 后在此读取默认命名空间：ConfigService.getAppConfig() 遍历填充 configList
        return ApiResult.succ(configList);
    }
}
