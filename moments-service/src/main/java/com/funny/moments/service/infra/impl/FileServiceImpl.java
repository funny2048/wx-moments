package com.funny.moments.service.infra.impl;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.funny.moments.common.vo.FileUploadVO;
import com.funny.moments.service.infra.IFileService;
import com.funny.framework.file.FileClient;
import com.funny.framework.file.FileUploadResult;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class FileServiceImpl implements IFileService {

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;
    private static final DateTimeFormatter DATE_PATH = DateTimeFormatter.ofPattern("yyyy/MM/dd");
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "jpg", "jpeg", "png", "gif", "bmp", "webp",
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
            "zip", "rar", "7z", "txt", "csv", "mp4", "mp3"
    );

    @Autowired
    private FileClient fileClient;

    @Override
    public FileUploadVO upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("上传文件不能为空");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("文件大小不能超过10MB");
        }

        String originalName = file.getOriginalFilename();
        String extension = getExtension(originalName);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("不支持的文件类型: " + extension);
        }

        String key = "upload/" + LocalDate.now().format(DATE_PATH) + "/" + UUID.randomUUID() + "." + extension;

        try (InputStream inputStream = file.getInputStream()) {
            log.info("调用文件存储上传 key={}, size={}, contentType={}", key, file.getSize(), file.getContentType());
            FileUploadResult result = fileClient.upload(key, inputStream, file.getContentType());
            log.info("文件存储上传响应 key={}, url={}", result.getKey(), result.getUrl());

            FileUploadVO vo = new FileUploadVO();
            vo.setKey(result.getKey());
            vo.setUrl(result.getUrl());
            vo.setOriginalName(originalName);
            vo.setSize(file.getSize());
            return vo;
        } catch (IOException e) {
            log.error("file upload error, originalName={}", originalName, e);
            throw new RuntimeException("文件上传失败", e);
        }
    }

    @Override
    public InputStream download(String key) {
        log.info("调用文件存储下载 key={}", key);
        return fileClient.download(key);
    }

    @Override
    public void delete(String key) {
        if (!key.startsWith("upload/")) {
            throw new IllegalArgumentException("非法的文件key");
        }
        log.info("调用文件存储删除 key={}", key);
        fileClient.delete(key);
    }

    private String getExtension(String filename) {
        if (filename == null) return "";
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == filename.length() - 1) return "";
        return filename.substring(dotIndex + 1).toLowerCase();
    }
}
