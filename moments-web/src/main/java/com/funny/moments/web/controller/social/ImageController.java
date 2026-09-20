package com.funny.moments.web.controller.social;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.funny.framework.core.result.ApiResult;
import com.funny.moments.model.out.ImageUploadOut;
import com.funny.moments.service.social.ILocalImageService;

import lombok.extern.slf4j.Slf4j;

/**
 * 本地图片上传（design §3.2.17）：multipart 上传到本地文件夹（目录可配置），
 * 返回 /images/{fileName} 访问 URL（发帖前置步骤）。
 *
 * @Author: funny2048
 * @Date: 2026-09-21
 */
@Slf4j
@RestController
@RequestMapping("/api/images")
public class ImageController {

    @Autowired
    private ILocalImageService localImageService;

    /**
     * required=false：file 字段缺失时交由 Service 统一抛 1020（api.md 接口17 / TC140②），
     * 避免 Spring 抛 MissingServletRequestPartException 落全局兜底 code=100。
     */
    @PostMapping("/upload")
    public ApiResult<ImageUploadOut> upload(String _appId, @RequestParam(value = "file", required = false) MultipartFile file) {
        log.info("ImageController.upload appId={}, originalName={}, size={}", _appId,
                file == null ? null : file.getOriginalFilename(), file == null ? null : file.getSize());
        return ApiResult.succ(localImageService.upload(file));
    }
}
