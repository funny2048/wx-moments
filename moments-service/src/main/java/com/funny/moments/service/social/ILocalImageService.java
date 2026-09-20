package com.funny.moments.service.social;

import java.util.List;

import org.springframework.web.multipart.MultipartFile;

import com.funny.moments.model.out.ImageUploadOut;

/**
 * 本地图片存储 Service（design §3.2.17，C16/C17）：上传图片落本地文件夹（目录可配置），
 * 并提供占位图池幂等生成（mock 数据与页面默认头像共用）。
 *
 * @Author: funny2048
 * @Date: 2026-09-21
 */
public interface ILocalImageService {

    /**
     * 上传图片：扩展名白名单（jpg/jpeg/png/gif/webp）+ 大小 ≤5MB 校验，
     * 文件名由服务端生成（UUID+扩展名，防覆盖/防穿越）。
     *
     * @param file 上传文件；缺失/空文件/无扩展名/扩展名不在白名单抛 1020，超 5MB 抛 1021
     * @return imageUrl = /images/{fileName}
     */
    ImageUploadOut upload(MultipartFile file);

    /**
     * 幂等生成占位图池（A7，共 40 张纯色 PNG）：头像 ph_avatar_01..20（200×200）+ 内容 ph_content_01..20（600×600），
     * 目标文件已存在则跳过写入。
     *
     * @return 全部占位图访问 URL（前 20 为头像、后 20 为内容图，顺序固定）
     */
    List<String> ensurePlaceholderPool();
}
