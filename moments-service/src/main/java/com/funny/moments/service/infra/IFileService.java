package com.funny.moments.service.infra;

import java.io.InputStream;

import org.springframework.web.multipart.MultipartFile;

import com.funny.moments.common.vo.FileUploadVO;

public interface IFileService {
    FileUploadVO upload(MultipartFile file);

    InputStream download(String key);

    void delete(String key);
}
