package com.funny.moments.web.controller;


import org.springframework.stereotype.Controller;

import com.funny.moments.common.vo.UserVo;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;


/**
 * 控制器基类
 * @author funny2048
 */
@Controller
@Slf4j
public class BaseController {


    protected boolean hasRole(String userCode, String roleCode) {
       return false;
    }


    protected UserVo getCurrentUser(HttpServletRequest request){

        return new UserVo();
    }

}