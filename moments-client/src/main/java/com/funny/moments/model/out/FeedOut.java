package com.funny.moments.model.out;

import java.util.ArrayList;
import java.util.List;

/**
 * Feed 瀑布流出参（api.md 接口16 / design §3.2.16）。
 * nextCursor 为不透明游标（客户端原样回传）；hasMore=true 时必非 null，hasMore=false 时为 null；
 * items=[] 且 hasMore=true 为合法组合（B1：本页扫描范围内无可见帖但候选流未扫尽）。
 *
 * @Author: funny2048
 * @Date: 2026-09-21
 */
public class FeedOut {

    /** 当前页帖子数组（无帖子为空数组，禁 null） */
    private List<FeedItemOut> items = new ArrayList<>();

    /** 下一页游标（不透明值；hasMore=false 时为 null） */
    private String nextCursor;

    /** 是否还有更多（false 时停止滚动加载） */
    private Boolean hasMore;

    public List<FeedItemOut> getItems() {
        return items;
    }

    public void setItems(List<FeedItemOut> items) {
        this.items = items;
    }

    public String getNextCursor() {
        return nextCursor;
    }

    public void setNextCursor(String nextCursor) {
        this.nextCursor = nextCursor;
    }

    public Boolean getHasMore() {
        return hasMore;
    }

    public void setHasMore(Boolean hasMore) {
        this.hasMore = hasMore;
    }

    /**
     * Feed 元素出参：PostDetailOut 的展示子集（api.md 接口16 字段说明，无 visibilityType/status——Feed 展示不需要）。
     */
    public static class FeedItemOut {

        /** 帖子 ID */
        private Long postId;

        /** 作者用户 ID（自己或好友） */
        private Long userId;

        /** 作者昵称 */
        private String nickname;

        /** 作者头像 URL */
        private String avatar;

        /** 文字内容 */
        private String content;

        /** 图片 URL 数组（按 sort 升序，最多 9 张，无图为空数组，禁 null） */
        private List<String> imageUrls = new ArrayList<>();

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

        public String getCreateTimeStr() {
            return createTimeStr;
        }

        public void setCreateTimeStr(String createTimeStr) {
            this.createTimeStr = createTimeStr;
        }
    }
}
