package com.funny.moments.common.dto.login;

import java.util.Objects;

import com.funny.moments.common.enums.ReturnCode;


public class UserHolder {

    private static final ThreadLocal<LoginUser> CONTEXT = new ThreadLocal<>();

    public static void setUser(LoginUser user) {
        CONTEXT.set(user);
    }

    public static LoginUser getUser() {
        LoginUser loginUser = CONTEXT.get();
        if (Objects.isNull(loginUser)) {
            throw new RuntimeException(ReturnCode.FORBIDDEN.getDesc());
        }
        return loginUser;
    }

    public static void clear() {
        CONTEXT.remove();
    }
}
