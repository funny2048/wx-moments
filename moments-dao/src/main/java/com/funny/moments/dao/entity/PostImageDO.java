package com.funny.moments.dao.entity;

import java.util.Date;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 帖子图片表 DO（sql.md §1 表6 post_image；图片单独存储带排序）。
 *
 * @Author: funny2048
 * @Date: 2026-09-21
 */
@TableName("post_image")
public class PostImageDO {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属帖子ID（post.id） */
    private Long postId;

    /** 图片URL（/images/{fileName}） */
    private String imageUrl;

    /** 排序号：1-9（单帖最多9图） */
    private Integer sort;

    /** 创建时间 */
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

    public Long getPostId() {
        return postId;
    }

    public void setPostId(Long postId) {
        this.postId = postId;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl == null ? null : imageUrl.trim();
    }

    public Integer getSort() {
        return sort;
    }

    public void setSort(Integer sort) {
        this.sort = sort;
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
