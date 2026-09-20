package com.funny.moments.model.out;

import java.util.ArrayList;
import java.util.List;

/**
 * 我的好友列表出参（api.md 接口3 / design §3.2.3）：UserOut 字段 + tagNames。
 * tagNames 为当前用户给该好友打的标签名列表（按标签 id 顺序，无标签为空数组）。
 *
 * @Author: funny2048
 * @Date: 2026-09-21
 */
public class FriendOut {

    /** 好友用户 ID */
    private Long userId;

    /** 昵称 */
    private String nickname;

    /** 头像 URL */
    private String avatar;

    /** 性别：0-未知 1-男 2-女 */
    private Integer gender;

    /** 性别中文 */
    private String genderStr;

    /** 城市 */
    private String city;

    /** 当前用户给该好友打的标签名列表（无标签为空数组，禁 null） */
    private List<String> tagNames = new ArrayList<>();

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getAvatar() {
        return avatar;
    }

    public void setAvatar(String avatar) {
        this.avatar = avatar;
    }

    public Integer getGender() {
        return gender;
    }

    public void setGender(Integer gender) {
        this.gender = gender;
    }

    public String getGenderStr() {
        return genderStr;
    }

    public void setGenderStr(String genderStr) {
        this.genderStr = genderStr;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public List<String> getTagNames() {
        return tagNames;
    }

    public void setTagNames(List<String> tagNames) {
        this.tagNames = tagNames;
    }
}
