package com.funny.moments.dao.entity;

import java.util.Date;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 帖子指定好友表 DO（sql.md §1 表7 post_visibility_user）。
 *
 * @Author: funny2048
 * @Date: 2026-09-21
 */
@TableName("post_visibility_user")
public class PostVisibilityUserDO {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属帖子ID（post.id） */
    private Long postId;

    /** 指定可见/不可见用户ID（type=3可见白名单/type=4排除名单） */
    private Long userId;

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

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
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
