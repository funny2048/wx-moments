package com.funny.moments.dao.mapper;

import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.funny.moments.dao.entity.FriendshipDO;
import org.apache.ibatis.annotations.Param;

/**
 * 好友关系表 Mapper（design §6.3，T020）：好友为对称双记录（C11），
 * 单向查询 user_id 视角即可覆盖双向语义。
 *
 * <p>说明：DAO 生成工具 CodeGenerator 需 CODEGEN_DB_* 环境变量（未配置）且 pathInfo 固定输出
 * sample-* 模块，故按 T004 既有手写基线手工实现（口径同 UserMapper）。
 *
 * @Author: funny2048
 * @Date: 2026-09-21
 */
public interface FriendshipMapper extends BaseMapper<FriendshipDO> {

    /**
     * 查询用户的好友 ID 集合（is_del=0，单向视角；对称双记录保证双向一致）。
     *
     * @param userId 用户 ID
     * @return 好友 ID 集合（无好友返回空列表）
     */
    List<Long> selectFriendIds(@Param("userId") Long userId);

    /**
     * 校验好友关系是否存在（计数语义，>0 即存在；T030 bindUser 的 1007 校验复用）。
     *
     * @param userId       关系一方（标签归属人）
     * @param friendUserId 关系另一方（被打标签的好友）
     * @return 计数（0=非好友）
     */
    Long existsFriendship(@Param("userId") Long userId, @Param("friendUserId") Long friendUserId);

    /**
     * 批量插入好友关系（T090 复用；对称双记录 A→B + B→A 由调用方成对构造，pair 集合去重）。
     *
     * @param list 关系集合（非空）
     * @return 插入行数
     */
    int batchInsert(@Param("list") List<FriendshipDO> list);

    /**
     * 软删全部好友关系（T090 clear=true 复用；带 WHERE 条件）。
     *
     * @return 更新行数
     */
    int logicDeleteAll();
}
