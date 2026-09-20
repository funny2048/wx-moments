package com.funny.moments.model.in;

/**
 * 批量生成模拟数据入参（api.md 接口11 / design §3.2.11）。
 * 校验（Service 层）：越界抛 1040（专用码，非 1001）。
 *
 * @Author: funny2048
 * @Date: 2026-09-21
 */
public class MockDataGenerateIn {

    /** 生成用户数，默认 100，范围 1-100000 */
    private Integer userCount;

    /** 人均好友数，默认 10，范围 0-500（好友关系对数≈userCount×该值/2，表记录数=对数×2） */
    private Integer avgFriendsPerUser;

    /** 每用户额外自定义标签数（默认 8 标签之外），默认 0，范围 0-20 */
    private Integer extraTagsPerUser;

    /** 生成帖子数（A8 最小扩展，页面级测试用），默认 0 不生成，范围 0-10000 */
    private Integer postCount;

    /** true=先软删清空 8 张表业务数据再生成（页面二次确认）；默认 false 追加 */
    private Boolean clear;

    public Integer getUserCount() {
        return userCount;
    }

    public void setUserCount(Integer userCount) {
        this.userCount = userCount;
    }

    public Integer getAvgFriendsPerUser() {
        return avgFriendsPerUser;
    }

    public void setAvgFriendsPerUser(Integer avgFriendsPerUser) {
        this.avgFriendsPerUser = avgFriendsPerUser;
    }

    public Integer getExtraTagsPerUser() {
        return extraTagsPerUser;
    }

    public void setExtraTagsPerUser(Integer extraTagsPerUser) {
        this.extraTagsPerUser = extraTagsPerUser;
    }

    public Integer getPostCount() {
        return postCount;
    }

    public void setPostCount(Integer postCount) {
        this.postCount = postCount;
    }

    public Boolean getClear() {
        return clear;
    }

    public void setClear(Boolean clear) {
        this.clear = clear;
    }
}
