package com.funny.moments.model.out;

/**
 * 标签出参（api.md 接口5/6/7 / design §3.2.5-§3.2.7）。
 * friendCount 为 COUNT(DISTINCT friend_user_id) 结果（R2-建议8：与 tagNames 去重口径一致）；
 * 创建标签响应中固定为 0。
 *
 * @Author: funny2048
 * @Date: 2026-09-21
 */
public class TagOut {

    /** 标签 ID */
    private Long tagId;

    /** 标签名 */
    private String tagName;

    /** 该标签下好友数（按好友去重计数），单位：个 */
    private Integer friendCount;

    /** 创建时间（yyyy-MM-dd HH:mm:ss） */
    private String createdTimeStr;

    public Long getTagId() {
        return tagId;
    }

    public void setTagId(Long tagId) {
        this.tagId = tagId;
    }

    public String getTagName() {
        return tagName;
    }

    public void setTagName(String tagName) {
        this.tagName = tagName;
    }

    public Integer getFriendCount() {
        return friendCount;
    }

    public void setFriendCount(Integer friendCount) {
        this.friendCount = friendCount;
    }

    public String getCreatedTimeStr() {
        return createdTimeStr;
    }

    public void setCreatedTimeStr(String createdTimeStr) {
        this.createdTimeStr = createdTimeStr;
    }
}
