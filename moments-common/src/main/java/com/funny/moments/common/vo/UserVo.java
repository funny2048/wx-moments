package com.funny.moments.common.vo;

import java.io.Serializable;

import lombok.Getter;
import lombok.Setter;

/**
 * @author funny2048
 * @date 2019-07-20
 * Copyright by funny2048
 */
@Getter
@Setter
public class UserVo implements Serializable {

    private Long userId;
    private String userName;
    private String userAccount;
    private String userCode;
    private String loginIp;
    private Long businessProductId;


}
