package com.funny.moments.web.controller.social;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.funny.framework.core.result.ApiResult;
import com.funny.moments.model.out.FeedOut;
import com.funny.moments.service.social.IFeedService;
import com.funny.moments.web.support.CurrentUserResolver;

import lombok.extern.slf4j.Slf4j;

/**
 * Feed 瀑布流（design §3.2.16，T080，api.md 接口16）：Cursor 分页滚动加载。
 * cursor 首页不传（下页传上响应 nextCursor 原样回传，不透明值）；pageSize 缺省 20 范围 1-50；
 * 非法游标（Base64 解码失败/格式不匹配/数值段溢出）Service 抛 1030，不静默重置首页（R4）。
 * 当前用户由 CurrentUserResolver 解析（viewerId 参数 > Cookie mockUserId，缺失抛 1003）。
 *
 * @Author: funny2048
 * @Date: 2026-09-21
 */
@Slf4j
@RestController
@RequestMapping("/api/feed")
public class FeedController {

    @Autowired
    private IFeedService feedService;

    /**
     * Feed 查询：自己 + 好友的帖子，canView 统一过滤，created_stime DESC + id DESC（C8）。
     * items=[] 且 hasMore=true 为合法组合（B1：本页扫描范围内无可见帖但候选流未扫尽，
     * 前端携带 nextCursor 继续翻页、连续 3 空页停止）。
     */
    @GetMapping
    public ApiResult<FeedOut> getFeed(String _appId,
            @RequestParam(value = "cursor", required = false) String cursor,
            @RequestParam(value = "pageSize", required = false) Integer pageSize,
            HttpServletRequest request) {
        log.info("FeedController.getFeed appId={}, cursor={}, pageSize={}", _appId, cursor, pageSize);
        Long viewerId = CurrentUserResolver.requireUser(request);
        return ApiResult.succ(feedService.getFeed(viewerId, cursor, pageSize));
    }
}
