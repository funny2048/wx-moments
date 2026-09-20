package com.funny.moments.web.controller.social;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.funny.framework.core.result.ApiResult;
import com.funny.moments.model.out.UserOut;
import com.funny.moments.model.out.UserPageOut;
import com.funny.moments.service.social.IUserService;

import lombok.extern.slf4j.Slf4j;

/**
 * 用户查询（design §3.2.1/§3.2.2，T010）：用户列表（分页）+ 用户详情。
 *
 * @Author: funny2048
 * @Date: 2026-09-21
 */
@Slf4j
@RestController
@RequestMapping("/api/users")
public class UserController {

    @Autowired
    private IUserService userService;

    /**
     * 用户列表：pageNo/pageSize 缺省默认 1/20；显式越界或非数字统一 1001（非数字由 T100 绑定层承载）。
     */
    @GetMapping
    public ApiResult<UserPageOut> listUsers(String _appId, Integer pageNo, Integer pageSize) {
        log.info("UserController.listUsers appId={}, pageNo={}, pageSize={}", _appId, pageNo, pageSize);
        return ApiResult.succ(userService.listUsers(pageNo, pageSize));
    }

    /**
     * 用户详情：不存在（含已删除）抛 1002。
     */
    @GetMapping("/{userId}")
    public ApiResult<UserOut> getUser(String _appId, @PathVariable("userId") Long userId) {
        log.info("UserController.getUser appId={}, userId={}", _appId, userId);
        return ApiResult.succ(userService.getUser(userId));
    }
}
