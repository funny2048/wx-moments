package com.funny.moments.web.controller.infra;

import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.funny.moments.common.vo.FileUploadVO;
import com.funny.moments.service.infra.IFileService;
import com.funny.framework.core.result.ApiResult;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

/**
 * 文件上传/下载/删除 demo。
 */
@Slf4j
@RestController
@RequestMapping("/file")
public class FileController {

    @Autowired
    private IFileService fileService;

    @PostMapping("/upload")
    public ApiResult<FileUploadVO> upload(String _appId, @RequestParam("file") MultipartFile file) {
        log.info("FileController.upload appId={}, originalName={}, size={}", _appId, file.getOriginalFilename(), file.getSize());
        FileUploadVO vo = fileService.upload(file);
        return ApiResult.succ(vo);
    }

    /**
     * 文件下载为二进制流，不走统一响应体（区别于 JSON 接口）。
     */
    @GetMapping("/download/**")
    public ResponseEntity<InputStreamResource> download(String _appId, HttpServletRequest request) {
        String key = extractKey(request);
        log.info("FileController.download appId={}, key={}", _appId, key);

        InputStream inputStream = fileService.download(key);
        String fileName = URLEncoder.encode(key.substring(key.lastIndexOf('/') + 1), StandardCharsets.UTF_8);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + fileName)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(new InputStreamResource(inputStream));
    }

    @DeleteMapping("/delete")
    public ApiResult delete(String _appId, @RequestParam("key") String key) {
        log.info("FileController.delete appId={}, key={}", _appId, key);
        fileService.delete(key);
        return ApiResult.succ();
    }

    /**
     * 从 /file/download/** 路径中截取对象 key，截取前先做长度校验避免越界。
     */
    private String extractKey(HttpServletRequest request) {
        String fullPath = request.getRequestURI();
        String prefix = request.getContextPath() + "/file/download/";
        if (fullPath.length() <= prefix.length()) {
            return "";
        }
        return fullPath.substring(prefix.length());
    }
}
