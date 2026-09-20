package com.funny.moments.common.dto.login;

import java.io.Serializable;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LoginUser implements Serializable {

    private Long id;
    private String userName;
    private String userCode;
    private String realName;
    private String access;
    private String loginIp;
    private List<String> roleCodes;
    private List<String> menuCodes;
    private Long tenantId;

}
