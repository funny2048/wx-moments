package com.funny.moments.dao.mapper;

import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.funny.moments.dao.dto.TagFriendCountDTO;
import com.funny.moments.dao.entity.FriendTagDO;
import org.apache.ibatis.annotations.Param;

/**
 * 好友标签表 Mapper（design §6.3）：selectByTagId 由 T020 建文件（R3 裁决方案①）；
 * selectByUserId/updateTagName/updateIsDel/batchInsert/countFriendsByTagIds/logicDeleteAll
 * 由 T030 追加（禁动 T020 既有方法）。单条插入复用 BaseMapper.insert（java-guide 2.3）。
 *
 * <p>说明：DAO 生成工具 CodeGenerator 需 CODEGEN_DB_* 环境变量（未配置）且 pathInfo 固定输出
 * sample-* 模块，故按 T004 既有手写基线手工实现（口径同 UserMapper）。
 *
 * @Author: funny2048
 * @Date: 2026-09-21
 */
public interface FriendTagMapper extends BaseMapper<FriendTagDO> {

    /**
     * 按标签 ID 查询（is_del=0）：T020 tagId 过滤归属校验 + T030/T060 复用。
     *
     * @param tagId 标签 ID
     * @return 标签；不存在或已删除返回 null（null 或 user_id != viewer 均按 1004 处理）
     */
    FriendTagDO selectByTagId(@Param("tagId") Long tagId);

    /**
     * 查询用户的标签列表（is_del=0，按 id 升序=创建顺序；T030 标签列表/同名查重，T090 复用）。
     *
     * @param userId 标签归属人
     * @return 标签列表（无标签返回空列表）
     */
    List<FriendTagDO> selectByUserId(@Param("userId") Long userId);

    /**
     * 修改标签名（最小更新实体：只含主键 id + 待更新字段 tagName，java-guide 2.3）。
     *
     * @param tag 更新载体（仅 id + tagName 有值）
     * @return 更新行数
     */
    int updateTagName(@Param("tag") FriendTagDO tag);

    /**
     * 软删标签（is_del=1；deleteTag 事务内调用）。
     *
     * @param tagId 标签 ID
     * @return 更新行数
     */
    int updateIsDel(@Param("tagId") Long tagId);

    /**
     * 批量插入标签（T090 模拟数据复用）。
     *
     * @param list 标签集合（非空）
     * @return 插入行数
     */
    int batchInsert(@Param("list") List<FriendTagDO> list);

    /**
     * 批量统计标签下好友数（COUNT(DISTINCT friend_user_id)，R2-建议8：与 tagNames 去重口径一致，
     * 并发残留两条绑定记录时计数不虚高）。
     *
     * @param tagIds 标签 ID 集合（非空，空集合由 Service 前置拦截）
     * @return (tagId, friendCount) 投影行列表（无绑定的标签不出现在结果中，由 Service 补 0）
     */
    List<TagFriendCountDTO> countFriendsByTagIds(@Param("tagIds") List<Long> tagIds);

    /**
     * 批量查多个归属人的标签（T090 追加：模拟数据 tagId 回读组装——friend_tag 的 batchInsert
     * 不回填自增 id，绑定/帖子可见性抽样需按 owner 批量取回 tagId；ownerIds 由调用方按 ≤500 分批）。
     *
     * @param ownerIds 标签归属人集合（非空且 ≤500，空集合由 Service 前置拦截）
     * @return 标签列表（is_del=0，按 user_id、id 升序；仅命中当前有效标签，不含历史软删行）
     */
    List<FriendTagDO> selectByOwnerIds(@Param("ownerIds") List<Long> ownerIds);

    /**
     * 软删全部标签（T090 clear=true 复用；带 WHERE 条件）。
     *
     * @return 更新行数
     */
    int logicDeleteAll();
}
