package com.funny.moments.model.out;

/**
 * 图片上传出参（api.md 接口17 / design §3.2.17）。
 *
 * @Author: funny2048
 * @Date: 2026-09-21
 */
public class ImageUploadOut {

    /** 访问 URL（文件名=服务端生成 UUID+扩展名，防覆盖防穿越；经 /images/** 静态映射访问） */
    private String imageUrl;

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }
}
