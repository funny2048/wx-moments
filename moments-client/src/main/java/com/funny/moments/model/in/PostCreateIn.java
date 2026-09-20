package com.funny.moments.model.in;

import java.util.List;

/**
 * 发布朋友圈入参（api.md 接口12 / design §3.2.12）。
 * 校验（Service 层）：visibilityType 必填 ∈{1,2,3,4}（1001）；content trim 后 ≤2000 字符（1001）；
 * imageUrls ≤9 张（超限 1022）且每项匹配 ^/images/[A-Za-z0-9._-]+$（1001）；
 * content 与 imageUrls 不得同空（1001）；
 * visibilityTagIds/visibilityUserIds 仅 type=3/4 生效，type=1/2 时静默忽略（不校验、不落库，建议 10 定稿）。
 *
 * @Author: funny2048
 * @Date: 2026-09-21
 */
public class PostCreateIn {

    /** 文字内容，可选，trim 后 ≤2000 字符 */
    private String content;

    /** 已上传图片 URL，可选，≤9 张，顺序即展示排序（sort=1..n） */
    private List<String> imageUrls;

    /** 可见范围：1-公开 2-私密 3-部分可见 4-不给谁看，必填 */
    private Integer visibilityType;

    /** 指定标签 ID，type=3/4 生效 */
    private List<Long> visibilityTagIds;

    /** 指定好友用户 ID，type=3/4 生效 */
    private List<Long> visibilityUserIds;

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

    public List<Long> getVisibilityTagIds() {
        return visibilityTagIds;
    }

    public void setVisibilityTagIds(List<Long> visibilityTagIds) {
        this.visibilityTagIds = visibilityTagIds;
    }

    public List<Long> getVisibilityUserIds() {
        return visibilityUserIds;
    }

    public void setVisibilityUserIds(List<Long> visibilityUserIds) {
        this.visibilityUserIds = visibilityUserIds;
    }
}
