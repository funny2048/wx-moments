package com.funny.moments.dao.dto;

/**
 * 标签名批量组装 DTO（T020）：FriendTagRelationMapper.selectTagNamesByOwnerAndFriends 的
 * 两表 join 投影行（friend_user_id + tag_name）。java-guide 2.3 禁止 Mapper 返回 List&lt;Map&gt;，
 * 故以显式 DTO 承载（非对外 API 契约，不落 moments-client）。
 *
 * @Author: funny2048
 * @Date: 2026-09-21
 */
public class FriendTagNameDTO {

    /** 被打标签的好友用户ID */
    private Long friendUserId;

    /** 标签名 */
    private String tagName;

    public Long getFriendUserId() {
        return friendUserId;
    }

    public void setFriendUserId(Long friendUserId) {
        this.friendUserId = friendUserId;
    }

    public String getTagName() {
        return tagName;
    }

    public void setTagName(String tagName) {
        this.tagName = tagName == null ? null : tagName.trim();
    }
}
