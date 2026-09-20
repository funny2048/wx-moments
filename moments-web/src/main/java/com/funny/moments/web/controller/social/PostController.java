package com.funny.moments.web.controller.social;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.alibaba.fastjson.JSON;
import com.funny.framework.core.result.ApiResult;
import com.funny.moments.model.in.PostCreateIn;
import com.funny.moments.model.out.PostDetailOut;
import com.funny.moments.service.social.IPostService;
import com.funny.moments.web.support.CurrentUserResolver;

import lombok.extern.slf4j.Slf4j;

/**
 * 朋友圈帖子（design §3.2.12-§3.2.15，T060 发布 + T070 帖子读写追加 3 端点）。
 * 当前用户由 CurrentUserResolver 解析（viewerId 参数 > Cookie mockUserId，缺失抛 1003；
 * 参数名 viewerId 与 GET /api/posts?userId= 的目标作者语义区分，A3/C14）。
 *
 * @Author: funny2048
 * @Date: 2026-09-21
 */
@Slf4j
@RestController
@RequestMapping("/api/posts")
public class PostController {

    @Autowired
    private IPostService postService;

    /**
     * 发布朋友圈：post/post_image/post_visibility_user/post_visibility_tag 4 表同事务；
     * 校验失败（1001/1022/1004/1006/1002）整体回滚无残留；type=1/2 携带的可见性列表静默忽略。
     */
    @PostMapping
    public ApiResult<PostDetailOut> createPost(String _appId, @RequestBody PostCreateIn in,
            HttpServletRequest request) {
        log.info("PostController.createPost appId={}, in={}", _appId, JSON.toJSONString(in));
        Long viewerId = CurrentUserResolver.requireUser(request);
        return ApiResult.succ(postService.createPost(viewerId, in));
    }

    /**
     * 帖子详情（T070，api.md 接口13）：经 canView 统一权限校验；1010 不存在（含已删）/ 1011 无权查看。
     */
    @GetMapping("/{postId}")
    public ApiResult<PostDetailOut> getPost(String _appId, @PathVariable("postId") Long postId,
            HttpServletRequest request) {
        log.info("PostController.getPost appId={}, postId={}", _appId, postId);
        Long viewerId = CurrentUserResolver.requireUser(request);
        return ApiResult.succ(postService.getPost(viewerId, postId));
    }

    /**
     * 软删帖子（T070，api.md 接口14）：仅作者可删（1012 越权）；已删帖二次删 1010（非幂等，§5.6 规则5b）；data=null。
     */
    @DeleteMapping("/{postId}")
    public ApiResult<Void> deletePost(String _appId, @PathVariable("postId") Long postId,
            HttpServletRequest request) {
        log.info("PostController.deletePost appId={}, postId={}", _appId, postId);
        Long viewerId = CurrentUserResolver.requireUser(request);
        postService.deletePost(viewerId, postId);
        return ApiResult.succ();
    }

    /**
     * 指定用户帖子列表（T070，api.md 接口15，C14）：canView 过滤静默不报错（不可见帖直接剔除）。
     * userId 目标作者（required=false + Service 判空：缺失转 1001，避免落框架兜底 code=100）；
     * limit 结果上限（缺省 100，范围 1-500）；viewerId 为视角覆盖参数，两者语义不同不冲突（A3）。
     */
    @GetMapping
    public ApiResult<List<PostDetailOut>> listUserPosts(String _appId,
            @RequestParam(value = "userId", required = false) Long userId,
            @RequestParam(value = "limit", required = false) Integer limit,
            HttpServletRequest request) {
        log.info("PostController.listUserPosts appId={}, userId={}, limit={}", _appId, userId, limit);
        Long viewerId = CurrentUserResolver.requireUser(request);
        return ApiResult.succ(postService.listUserPosts(viewerId, userId, limit));
    }
}
