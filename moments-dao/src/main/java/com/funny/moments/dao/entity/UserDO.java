package com.funny.moments.dao.entity;

import java.util.Date;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 用户表 DO（sql.md §1 表1 user，字段一一对应）。
 * 手写 getter/setter（禁 Lombok）；主键 Long；String setter trim；is_del 由 @TableLogic 软删过滤。
 *
 * @Author: funny2048
 * @Date: 2026-09-21
 */
@TableName("user")
public class UserDO {

    /** 主键（userId） */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 昵称（允许重名） */
    private String nickname;

    /** 头像URL（/images/{fileName}） */
    private String avatar;

    /** 性别：0-未知 1-男 2-女（GenderEnum） */
    private Integer gender;

    /** 城市 */
    private String city;

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

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname == null ? null : nickname.trim();
    }

    public String getAvatar() {
        return avatar;
    }

    public void setAvatar(String avatar) {
        this.avatar = avatar == null ? null : avatar.trim();
    }

    public Integer getGender() {
        return gender;
    }

    public void setGender(Integer gender) {
        this.gender = gender;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city == null ? null : city.trim();
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
