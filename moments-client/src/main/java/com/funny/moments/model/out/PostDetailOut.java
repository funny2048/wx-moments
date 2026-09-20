package com.funny.moments.model.out;

import java.util.ArrayList;
import java.util.List;

/**
 * 帖子详情/列表元素出参（api.md 接口12/13/15 / design §3.2.12-§3.2.13、§3.2.15）。
 * visibilityTypeStr 由 VisibilityTypeEnum.byCode 反查组装；createTimeStr 格式 yyyy-MM-dd HH:mm:ss；
 * imageUrls 按 sort 升序（顺序与发帖请求一致），无图返回空数组禁 null。
 *
 * @Author: funny2048
 * @Date: 2026-09-21
 */
public class PostDetailOut {

    /** 帖子 ID */
    private Long postId;

    /** 作者用户 ID */
    private Long userId;

    /** 作者昵称（组装） */
    private String nickname;

    /** 作者头像 URL（组装） */
    private String avatar;

    /** 文字内容 */
    private String content;

    /** 图片 URL 数组（按 sort 升序，最多 9 张，无图为空数组，禁 null） */
    private List<String> imageUrls = new ArrayList<>();

    /** 可见范围：1-公开 2-私密 3-部分可见 4-不给谁看 */
    private Integer visibilityType;

    /** 可见范围中文 */
    private String visibilityTypeStr;

    /** 业务状态：1-正常 0-删除（业务态快照，软删走 is_del，接口不外露 is_del） */
    private Integer status;

    /** 发布时间（yyyy-MM-dd HH:mm:ss） */
    private String createTimeStr;

    public Long getPostId() {
        return postId;
    }

    public void setPostId(Long postId) {
        this.postId = postId;
    }

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

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public List<String> getImageUrls() {
        return imageUrls;
    }

    public void setImageUrls(List<String> imageUrls) {
        this.imageUrls = imageUrls;
    }

    public Integer getVisibilityType() {
        return visibilityType;
    }

    public void setVisibilityType(Integer visibilityType) {
        this.visibilityType = visibilityType;
    }

    public String getVisibilityTypeStr() {
        return visibilityTypeStr;
    }

    public void setVisibilityTypeStr(String visibilityTypeStr) {
        this.visibilityTypeStr = visibilityTypeStr;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public String getCreateTimeStr() {
        return createTimeStr;
    }

    public void setCreateTimeStr(String createTimeStr) {
        this.createTimeStr = createTimeStr;
    }
}
