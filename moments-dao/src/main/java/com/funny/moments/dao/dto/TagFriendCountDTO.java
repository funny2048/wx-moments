package com.funny.moments.dao.dto;

/**
 * 标签好友数统计 DTO（T030）：FriendTagMapper.countFriendsByTagIds 的投影行
 * （tag_id + COUNT(DISTINCT friend_user_id)，R2-建议8）。java-guide 2.3 禁止 Mapper 返回
 * List&lt;Map&gt;，故以显式 DTO 承载（非对外 API 契约，不落 moments-client）。
 *
 * @Author: funny2048
 * @Date: 2026-09-21
 */
public class TagFriendCountDTO {

    /** 标签 ID */
    private Long tagId;

    /** 该标签下好友数（按好友去重计数） */
    private Integer friendCount;

    public Long getTagId() {
        return tagId;
    }

    public void setTagId(Long tagId) {
        this.tagId = tagId;
    }

    public Integer getFriendCount() {
        return friendCount;
    }

    public void setFriendCount(Integer friendCount) {
        this.friendCount = friendCount;
    }
}
