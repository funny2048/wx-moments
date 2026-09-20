package com.funny.moments.model.out;

/**
 * 用户出参（api.md 接口1 users[] 元素 / 接口2 / design §3.2.1-§3.2.2）。
 * genderStr 由 GenderEnum.byCode 反查组装；createTimeStr 格式 yyyy-MM-dd HH:mm:ss（DateUtils.PATTERN）。
 *
 * @Author: funny2048
 * @Date: 2026-09-21
 */
public class UserOut {

    /** 用户 ID */
    private Long userId;

    /** 昵称（允许重名） */
    private String nickname;

    /** 头像 URL（/images/{fileName}） */
    private String avatar;

    /** 性别：0-未知 1-男 2-女 */
    private Integer gender;

    /** 性别中文（未知/男/女） */
    private String genderStr;

    /** 城市 */
    private String city;

    /** 注册时间（yyyy-MM-dd HH:mm:ss） */
    private String createTimeStr;

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
}
