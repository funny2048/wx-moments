package com.funny.moments.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

@FeignClient(name = "codegen-web", url = "${feign.codegen.url:http://localhost:8080}")
public interface CodegenFeignClient {

    @GetMapping("/heartbeat")
    String heartbeat();
}
