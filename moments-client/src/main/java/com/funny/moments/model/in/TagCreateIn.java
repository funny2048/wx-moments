package com.funny.moments.model.in;

/**
 * 创建标签入参（api.md 接口6 / design §3.2.6）。
 * 校验（Service 层）：tagName trim 后 1-16 字符，越界抛 1001。
 *
 * @Author: funny2048
 * @Date: 2026-09-21
 */
public class TagCreateIn {

    /** 标签名（同一用户内查重，软删范围内），trim 后 1-16 字符 */
    private String tagName;

    public String getTagName() {
        return tagName;
    }

    public void setTagName(String tagName) {
        this.tagName = tagName;
    }
}
