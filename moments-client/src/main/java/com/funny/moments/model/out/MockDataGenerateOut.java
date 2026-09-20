package com.funny.moments.model.out;

/**
 * 模拟数据生成统计出参（api.md 接口11 / design §3.2.11）。
 *
 * @Author: funny2048
 * @Date: 2026-09-21
 */
public class MockDataGenerateOut {

    /** 实际生成用户数，单位：个 */
    private Integer userCount;

    /** 好友关系对数（A-B 计 1 对），单位：对 */
    private Integer friendshipPairs;

    /** friendship 表写入记录数（=对数×2，对称双记录），单位：条 */
    private Integer friendshipRecords;

    /** 生成标签总数（含默认 8×用户数），单位：个 */
    private Integer tagCount;

    /** 标签绑定关系总数，单位：条 */
    private Integer bindingCount;

    /** 生成帖子数，单位：条 */
    private Integer postCount;

    /** 占位图池文件数（头像 20 + 内容图 20），单位：个 */
    private Integer imagePoolSize;

    /** 生成耗时，单位：毫秒 */
    private Long elapsedMs;

    public Integer getUserCount() {
        return userCount;
    }

    public void setUserCount(Integer userCount) {
        this.userCount = userCount;
    }

    public Integer getFriendshipPairs() {
        return friendshipPairs;
    }

    public void setFriendshipPairs(Integer friendshipPairs) {
        this.friendshipPairs = friendshipPairs;
    }

    public Integer getFriendshipRecords() {
        return friendshipRecords;
    }

    public void setFriendshipRecords(Integer friendshipRecords) {
        this.friendshipRecords = friendshipRecords;
    }

    public Integer getTagCount() {
        return tagCount;
    }

    public void setTagCount(Integer tagCount) {
        this.tagCount = tagCount;
    }

    public Integer getBindingCount() {
        return bindingCount;
    }

    public void setBindingCount(Integer bindingCount) {
        this.bindingCount = bindingCount;
    }

    public Integer getPostCount() {
        return postCount;
    }

    public void setPostCount(Integer postCount) {
        this.postCount = postCount;
    }

    public Integer getImagePoolSize() {
        return imagePoolSize;
    }

    public void setImagePoolSize(Integer imagePoolSize) {
        this.imagePoolSize = imagePoolSize;
    }

    public Long getElapsedMs() {
        return elapsedMs;
    }

    public void setElapsedMs(Long elapsedMs) {
        this.elapsedMs = elapsedMs;
    }
}
