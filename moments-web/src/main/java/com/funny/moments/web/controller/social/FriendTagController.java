package com.funny.moments.web.controller.social;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.funny.framework.core.result.ApiResult;
import com.funny.moments.model.in.TagCreateIn;
import com.funny.moments.model.in.TagEditIn;
import com.funny.moments.model.out.TagOut;
import com.funny.moments.service.social.IFriendTagService;
import com.funny.moments.web.support.CurrentUserResolver;

import lombok.extern.slf4j.Slf4j;

/**
 * 好友标签管理（design §3.2.5-§3.2.10，T030）：标签 CRUD + 好友绑定/解绑，6 端点。
 * 当前用户由 CurrentUserResolver 解析（design §6.1：viewerId 参数 > Cookie mockUserId，缺失抛 1003）；
 * 标签归属由 Service 校验（1004 不存在 / 1006 非归属人）。
 *
 * @Author: funny2048
 * @Date: 2026-09-21
 */
@Slf4j
@RestController
@RequestMapping("/api/friend-tags")
public class FriendTagController {

    @Autowired
    private IFriendTagService friendTagService;

    /**
     * 我的标签列表：含每标签好友数（COUNT(DISTINCT) 口径）。
     */
    @GetMapping
    public ApiResult<List<TagOut>> listTags(String _appId, HttpServletRequest request) {
        log.info("FriendTagController.listTags appId={}", _appId);
        Long viewerId = CurrentUserResolver.requireUser(request);
        return ApiResult.succ(friendTagService.listTags(viewerId));
    }

    /**
     * 创建标签：tagName trim 后 1-16 字符（1001）；同名查重（1005）。
     */
    @PostMapping
    public ApiResult<TagOut> createTag(String _appId, @RequestBody TagCreateIn in, HttpServletRequest request) {
        log.info("FriendTagController.createTag appId={}, tagName={}", _appId, in == null ? null : in.getTagName());
        Long viewerId = CurrentUserResolver.requireUser(request);
        return ApiResult.succ(friendTagService.createTag(viewerId, in));
    }

    /**
     * 修改标签名：仅标签归属人（1006）；新名查重（1005）。
     */
    @PutMapping("/{tagId}")
    public ApiResult<TagOut> updateTag(String _appId, @PathVariable("tagId") Long tagId,
            @RequestBody TagEditIn in, HttpServletRequest request) {
        log.info("FriendTagController.updateTag appId={}, tagId={}, tagName={}",
                _appId, tagId, in == null ? null : in.getTagName());
        Long viewerId = CurrentUserResolver.requireUser(request);
        return ApiResult.succ(friendTagService.updateTag(viewerId, tagId, in));
    }

    /**
     * 删除标签：事务内级联软删全部绑定（C13）；data=null。
     */
    @DeleteMapping("/{tagId}")
    public ApiResult<Void> deleteTag(String _appId, @PathVariable("tagId") Long tagId,
            HttpServletRequest request) {
        log.info("FriendTagController.deleteTag appId={}, tagId={}", _appId, tagId);
        Long viewerId = CurrentUserResolver.requireUser(request);
        friendTagService.deleteTag(viewerId, tagId);
        return ApiResult.succ();
    }

    /**
     * 给好友绑定标签：1004/1006 标签校验 → 1002 用户存在 → 1007 好友关系 → 1008 查重；data=null。
     */
    @PostMapping("/{tagId}/users/{userId}")
    public ApiResult<Void> bindUser(String _appId, @PathVariable("tagId") Long tagId,
            @PathVariable("userId") Long userId, HttpServletRequest request) {
        log.info("FriendTagController.bindUser appId={}, tagId={}, userId={}", _appId, tagId, userId);
        Long viewerId = CurrentUserResolver.requireUser(request);
        friendTagService.bindUser(viewerId, tagId, userId);
        return ApiResult.succ();
    }

    /**
     * 解绑好友标签：幂等（绑定不存在也返回成功）；data=null。
     */
    @DeleteMapping("/{tagId}/users/{userId}")
    public ApiResult<Void> unbindUser(String _appId, @PathVariable("tagId") Long tagId,
            @PathVariable("userId") Long userId, HttpServletRequest request) {
        log.info("FriendTagController.unbindUser appId={}, tagId={}, userId={}", _appId, tagId, userId);
        Long viewerId = CurrentUserResolver.requireUser(request);
        friendTagService.unbindUser(viewerId, tagId, userId);
        return ApiResult.succ();
    }
}
