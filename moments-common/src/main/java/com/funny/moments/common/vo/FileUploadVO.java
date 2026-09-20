package com.funny.moments.common.vo;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FileUploadVO {
    private String key;
    private String url;
    private String originalName;
    private long size;
}
