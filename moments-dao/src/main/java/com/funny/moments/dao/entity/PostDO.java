package com.funny.moments.dao.entity;

import java.util.Date;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 朋友圈帖子表 DO（sql.md §1 表5 post；Feed 排序键为 (created_stime, id)，C8）。
 *
 * @Author: funny2048
 * @Date: 2026-09-21
 */
@TableName("post")
public class PostDO {

    /** 主键（postId） */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 作者用户ID */
    private Long userId;

    /** 文字内容（≤2000字） */
    private String content;

    /** 可见范围：1-公开 2-私密 3-部分可见 4-不给谁看（VisibilityTypeEnum，tinyint→Integer） */
    private Integer visibilityType;

    /** 业务状态：1-正常 0-删除（PostStatusEnum，软删走is_del） */
    private Integer status;

    /** 发布时间（Feed排序键之一） */
    private Date createdStime;

    /** 修改时间 */
    private Date modifiedStime;

    /** 是否删除 0-正常 1-删除 */
    @TableLogic
    private Integer isDel;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content == null ? null : content.trim();
    }

    public Integer getVisibilityType() {
        return visibilityType;
    }

    public void setVisibilityType(Integer visibilityType) {
        this.visibilityType = visibilityType;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Date getCreatedStime() {
        return createdStime;
    }

    public void setCreatedStime(Date createdStime) {
        this.createdStime = createdStime;
    }

    public Date getModifiedStime() {
        return modifiedStime;
    }

    public void setModifiedStime(Date modifiedStime) {
        this.modifiedStime = modifiedStime;
    }

    public Integer getIsDel() {
        return isDel;
    }

    public void setIsDel(Integer isDel) {
        this.isDel = isDel;
    }
}
