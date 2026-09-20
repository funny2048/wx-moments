package com.funny.moments.dao.entity;

import java.util.Date;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 好友关系表 DO（sql.md §1 表2 friendship；双向关系两条对称记录，C11）。
 *
 * @Author: funny2048
 * @Date: 2026-09-21
 */
@TableName("friendship")
public class FriendshipDO {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关系一方用户ID */
    private Long userId;

    /** 关系另一方用户ID（与user_id对称成对存储） */
    private Long friendUserId;

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

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getFriendUserId() {
        return friendUserId;
    }

    public void setFriendUserId(Long friendUserId) {
        this.friendUserId = friendUserId;
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
