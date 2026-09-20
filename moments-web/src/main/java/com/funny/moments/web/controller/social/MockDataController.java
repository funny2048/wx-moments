package com.funny.moments.web.controller.social;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.alibaba.fastjson.JSON;
import com.funny.framework.core.result.ApiResult;
import com.funny.moments.model.in.MockDataGenerateIn;
import com.funny.moments.model.out.MockDataGenerateOut;
import com.funny.moments.service.social.IMockDataService;

import lombok.extern.slf4j.Slf4j;

/**
 * 模拟数据生成（design §3.2.11/§6.1，T090）：实验室工具接口，无当前用户要求（接口11 权限列"否"）；
 * clear=true 会软删清空 8 表业务数据，页面侧须二次确认（T110）。
 *
 * @Author: funny2048
 * @Date: 2026-09-21
 */
@Slf4j
@RestController
@RequestMapping("/api/mock-data")
public class MockDataController {

    @Autowired
    private IMockDataService mockDataService;

    /**
     * 批量生成模拟数据：用户/对称好友/默认 8 标签 + 自定义/按 A9 权重分布绑定/（可选）帖子与可见性抽样；
     * 配置越界（userCount/avgFriendsPerUser/extraTagsPerUser/postCount 超范围）返回 1040。
     */
    @PostMapping
    public ApiResult<MockDataGenerateOut> generate(String _appId, @RequestBody MockDataGenerateIn in) {
        log.info("MockDataController.generate appId={}, in={}", _appId, JSON.toJSONString(in));
        return ApiResult.succ(mockDataService.generate(in));
    }
}
