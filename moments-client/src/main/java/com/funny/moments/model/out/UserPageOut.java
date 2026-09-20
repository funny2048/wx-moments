package com.funny.moments.model.out;

import java.util.ArrayList;
import java.util.List;

/**
 * 用户列表分页出参（api.md 接口1 / design §3.2.1）。
 *
 * @Author: funny2048
 * @Date: 2026-09-21
 */
public class UserPageOut {

    /** 总用户数，单位：个 */
    private Long total;

    /** 当前页用户数组（无值返回空数组，禁 null） */
    private List<UserOut> users = new ArrayList<>();

    public Long getTotal() {
        return total;
    }

    public void setTotal(Long total) {
        this.total = total;
    }

    public List<UserOut> getUsers() {
        return users;
    }

    public void setUsers(List<UserOut> users) {
        this.users = users;
    }
}
