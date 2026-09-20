package com.funny.moments.web.controller.social;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.funny.framework.core.result.ApiResult;
import com.funny.moments.model.out.FriendDetailOut;
import com.funny.moments.model.out.FriendOut;
import com.funny.moments.service.social.IFriendshipService;
import com.funny.moments.web.support.CurrentUserResolver;

import lombok.extern.slf4j.Slf4j;

/**
 * 好友查询（design §3.2.3/§3.2.4，T020）：我的好友列表（tagId 可选过滤）+ 好友详情。
 * 当前用户由 CurrentUserResolver 解析（T100，design §6.1：viewerId 参数 > Cookie mockUserId，缺失抛 1003）。
 *
 * @Author: funny2048
 * @Date: 2026-09-21
 */
@Slf4j
@RestController
@RequestMapping("/api/friends")
public class FriendshipController {

    @Autowired
    private IFriendshipService friendshipService;

    /**
     * 我的好友列表：tagId 传入时按标签过滤（标签不存在或不归属当前用户抛 1004）。
     */
    @GetMapping
    public ApiResult<List<FriendOut>> getFriends(String _appId,
            @RequestParam(value = "tagId", required = false) Long tagId, HttpServletRequest request) {
        log.info("FriendshipController.getFriends appId={}, tagId={}", _appId, tagId);
        Long viewerId = CurrentUserResolver.requireUser(request);
        return ApiResult.succ(friendshipService.listFriends(viewerId, tagId));
    }

    /**
     * 好友详情：基础信息 + 我给其打的标签；用户不存在抛 1002。
     */
    @GetMapping("/{userId}")
    public ApiResult<FriendDetailOut> getFriendDetail(String _appId,
            @PathVariable("userId") Long userId, HttpServletRequest request) {
        log.info("FriendshipController.getFriendDetail appId={}, userId={}", _appId, userId);
        Long viewerId = CurrentUserResolver.requireUser(request);
        return ApiResult.succ(friendshipService.getFriendDetail(viewerId, userId));
    }
}
