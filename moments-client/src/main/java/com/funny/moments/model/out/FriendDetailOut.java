package com.funny.moments.model.out;

import java.util.ArrayList;
import java.util.List;

/**
 * 好友详情出参（api.md 接口4 / design §3.2.4）：UserOut 全字段 + tagNames。
 * 好友注册时间即 createTimeStr。
 *
 * @Author: funny2048
 * @Date: 2026-09-21
 */
public class FriendDetailOut {

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

    /** 注册时间（yyyy-MM-dd HH:mm:ss） */
    private String createTimeStr;

    /** 我给该好友打的标签名列表（无标签为空数组，禁 null） */
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

    public String getCreateTimeStr() {
        return createTimeStr;
    }

    public void setCreateTimeStr(String createTimeStr) {
        this.createTimeStr = createTimeStr;
    }

    public List<String> getTagNames() {
        return tagNames;
    }

    public void setTagNames(List<String> tagNames) {
        this.tagNames = tagNames;
    }
}
