package com.funny.moments.service.social;

import java.util.List;

import com.funny.moments.model.in.TagCreateIn;
import com.funny.moments.model.in.TagEditIn;
import com.funny.moments.model.out.TagOut;

/**
 * 好友标签管理服务（design §6.2，T030）：标签 CRUD + 好友绑定/解绑 + 级联软删。
 *
 * <p>事务口径（design §6.2 / tasks T030）：仅 deleteTag 加 @Transactional（级联 2 表写）；
 * create/update/bind/unbind 单表写无事务注解（单 DML 自动提交原子，建议 11 留痕）。
 *
 * @Author: funny2048
 * @Date: 2026-09-21
 */
public interface IFriendTagService {

    /**
     * 我的标签列表（含每标签好友数，COUNT(DISTINCT) 口径）。
     *
     * @param viewerId 当前用户（标签归属人）
     * @return 标签列表（无标签返回空列表）
     */
    List<TagOut> listTags(Long viewerId);

    /**
     * 创建标签（api.md 接口6）：tagName trim 后 1-16 字符（1001）；同用户同名查重（1005）。
     *
     * @param viewerId 当前用户
     * @param in       入参（tagName）
     * @return 新建标签（friendCount=0）
     */
    TagOut createTag(Long viewerId, TagCreateIn in);

    /**
     * 修改标签名（api.md 接口7）：标签不存在 1004；非归属人 1006；新名与其他标签重复 1005。
     *
     * @param viewerId 当前用户
     * @param tagId    标签 ID
     * @param in       入参（tagName）
     * @return 修改后标签（含当前好友数）
     */
    TagOut updateTag(Long viewerId, Long tagId, TagEditIn in);

    /**
     * 删除标签（api.md 接口8）：事务内级联软删全部绑定（C13）；缓存失效走 afterCommit（建议3 定稿）。
     *
     * @param viewerId 当前用户
     * @param tagId    标签 ID
     */
    void deleteTag(Long viewerId, Long tagId);

    /**
     * 给好友绑定标签（api.md 接口9）：1004/1006 标签校验 → 1002 用户存在 → 1007 好友关系
     * （S4 裁决：1002 先于 1007）→ 1008 查重 → 插入。
     *
     * @param viewerId 当前用户
     * @param tagId    标签 ID
     * @param userId   好友用户 ID
     */
    void bindUser(Long viewerId, Long tagId, Long userId);

    /**
     * 解绑好友标签（api.md 接口10）：幂等（0 行命中不报错）。
     *
     * @param viewerId 当前用户
     * @param tagId    标签 ID
     * @param userId   好友用户 ID
     */
    void unbindUser(Long viewerId, Long tagId, Long userId);
}
