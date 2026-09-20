package com.funny.moments.dao.mapper;

import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.funny.moments.dao.entity.PostVisibilityUserDO;
import org.apache.ibatis.annotations.Param;

/**
 * 帖子指定好友表 Mapper（design §6.3，T060 建文件 + T050 追加查询）：
 * 写入部分 batchInsert/logicDeleteAll（T060）；selectUserIdsByPostId（canView 的 userHit 判定，T050 追加）。
 *
 * <p>说明：DAO 生成工具 CodeGenerator 需 CODEGEN_DB_* 环境变量（未配置）且 pathInfo 固定输出
 * sample-* 模块，与本项目 moments-* 布局不符，故按 T004 既有手写基线手工实现（口径同 UserMapper）。
 *
 * @Author: funny2048
 * @Date: 2026-09-21
 */
public interface PostVisibilityUserMapper extends BaseMapper<PostVisibilityUserDO> {

    /**
     * 批量插入帖子指定好友（发帖事务内，仅 type=3/4 且 visibilityUserIds 非空；入参已去重，
     * 防 uniq_post_user 唯一键冲突整批回滚；T090 模拟数据复用）。
     *
     * @param list 指定好友集合（非空）
     * @return 插入行数
     */
    int batchInsert(@Param("list") List<PostVisibilityUserDO> list);

    /**
     * 查询帖子指定好友 ID 集合（canView 的 userHit 判定数据源，design §5.2，T050 追加）：
     * type=3 命中即可见 / type=4 命中即不可见。
     *
     * @param postId 帖子 ID
     * @return 指定好友 ID 列表（无记录返回空列表，禁 null）
     */
    List<Long> selectUserIdsByPostId(@Param("postId") Long postId);

    /**
     * 软删全部帖子指定好友（T090 clear=true 复用；带 WHERE 条件）。
     *
     * @return 更新行数
     */
    int logicDeleteAll();
}
