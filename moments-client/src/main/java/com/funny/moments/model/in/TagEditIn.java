package com.funny.moments.model.in;

/**
 * 修改标签入参（api.md 接口7 / design §3.2.7）。
 * 校验（Service 层）：tagName trim 后 1-16 字符，越界抛 1001；tagId 为路径参数不在本 DTO。
 *
 * @Author: funny2048
 * @Date: 2026-09-21
 */
public class TagEditIn {

    /** 新标签名，trim 后 1-16 字符 */
    private String tagName;

    public String getTagName() {
        return tagName;
    }

    public void setTagName(String tagName) {
        this.tagName = tagName;
    }
}
