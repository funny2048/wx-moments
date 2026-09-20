package com.funny.moments.dao.mapper;

import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.funny.moments.dao.dto.FriendTagNameDTO;
import com.funny.moments.dao.entity.FriendTagRelationDO;
import org.apache.ibatis.annotations.Param;

/**
 * 标签-好友绑定表 Mapper（design §6.3）：selectTagNamesByOwnerAndFriends + selectFriendIdsByTag
 * 由 T020 建文件（R2-NB2 前移）；selectByTagAndUser/selectTagIdsOfFriend/updateIsDelByTagId/
 * updateIsDelByTagAndUser/batchInsert/logicDeleteAll 由 T030 追加（禁动 T020 既有方法）。
 * 单条插入复用 BaseMapper.insert（java-guide 2.3；自定义同名 insert 会与 MP 注入语句冲突）。
 *
 * <p>说明：DAO 生成工具 CodeGenerator 需 CODEGEN_DB_* 环境变量（未配置）且 pathInfo 固定输出
 * sample-* 模块，故按 T004 既有手写基线手工实现（口径同 UserMapper）。
 *
 * @Author: funny2048
 * @Date: 2026-09-21
 */
public interface FriendTagRelationMapper extends BaseMapper<FriendTagRelationDO> {

    /**
     * 批量查 owner 给多个好友打的标签名（好友列表 tagNames 组装，防 N+1）。
     * 两表 join 限定 t.user_id = ownerId（标签归属，防跨 owner 串标签）；
     * GROUP BY (friend_user_id, tag_name) 防并发残留同名标签绑定多行。
     *
     * @param ownerId    标签归属人（当前用户）
     * @param friendIds  好友 ID 集合（非空，空集合由 Service 前置拦截）
     * @return (friendUserId, tagName) 投影行列表，按 friend_user_id、tag_id 升序（api.md 接口3 tagNames 顺序按标签 id）
     */
    List<FriendTagNameDTO> selectTagNamesByOwnerAndFriends(@Param("ownerId") Long ownerId,
                                                           @Param("friendIds") List<Long> friendIds);

    /**
     * 按标签查好友 ID 集合（T020 tagId 过滤分支数据源；标签归属已由 selectByTagId 前置校验）。
     *
     * @param tagId 标签 ID
     * @return 好友 ID 集合（标签下无好友返回空列表）
     */
    List<Long> selectFriendIdsByTag(@Param("tagId") Long tagId);

    /**
     * 绑定查重（T030 bindUser 的 1008 前置查询，java-guide 6.1 INSERT 前先 SELECT）。
     * LIMIT 1 取单条（design §5.6 规则5：并发残留双行时存在性语义不变）。
     *
     * @param tagId        标签 ID（归属已前置校验）
     * @param friendUserId 好友用户 ID
     * @return 绑定记录；未绑定或已解绑返回 null
     */
    FriendTagRelationDO selectByTagAndUser(@Param("tagId") Long tagId,
                                           @Param("friendUserId") Long friendUserId);

    /**
     * 查 owner 给好友打的标签 ID 集合（两表 join 限定 t.user_id = ownerId 防跨 owner 串标签；
     * FriendTagCacheManager 回源数据源，消费侧转 Set 天然去重并发残留）。
     *
     * @param ownerId       标签归属人（帖子作者）
     * @param friendUserId  好友用户 ID（观众）
     * @return 标签 ID 列表（无标签返回空列表）
     */
    List<Long> selectTagIdsOfFriend(@Param("ownerId") Long ownerId,
                                    @Param("friendUserId") Long friendUserId);

    /**
     * 按标签级联软删全部绑定（deleteTag 事务内，C13）。
     *
     * @param tagId 标签 ID
     * @return 更新行数
     */
    int updateIsDelByTagId(@Param("tagId") Long tagId);

    /**
     * 软删单条绑定（unbindUser；0 行命中=幂等成功，design §3.2.10）。
     *
     * @param tagId        标签 ID
     * @param friendUserId 好友用户 ID
     * @return 更新行数（0=绑定本就不存在）
     */
    int updateIsDelByTagAndUser(@Param("tagId") Long tagId,
                                @Param("friendUserId") Long friendUserId);

    /**
     * 批量插入绑定（T090 模拟数据复用；去重由调用方保证）。
     *
     * @param list 绑定集合（非空）
     * @return 插入行数
     */
    int batchInsert(@Param("list") List<FriendTagRelationDO> list);

    /**
     * 软删全部绑定（T090 clear=true 复用；带 WHERE 条件）。
     *
     * @return 更新行数
     */
    int logicDeleteAll();
}
