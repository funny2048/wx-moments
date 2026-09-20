package com.funny.moments.dao.mapper;

import java.util.Date;
import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.funny.moments.dao.entity.PostDO;
import org.apache.ibatis.annotations.Param;

/**
 * 朋友圈帖子表 Mapper（design §6.3，T060）：本 task 实现 selectUserPosts/updateIsDel/batchInsert/logicDeleteAll；
 * selectFeedPage 留 T080 追加（禁动本 task 既有语句）。单条插入与主键点查复用 BaseMapper：
 * insert 经 IdType.AUTO 回填自增 postId（发帖 4 表事务外键来源），selectById 经 @TableLogic 自动过滤 is_del=1。
 *
 * <p>说明：DAO 生成工具 CodeGenerator 需 CODEGEN_DB_* 环境变量（未配置）且 pathInfo 固定输出
 * sample-* 模块，与本项目 moments-* 布局不符，故按 T004 既有手写基线手工实现（口径同 UserMapper）。
 *
 * @Author: funny2048
 * @Date: 2026-09-21
 */
public interface PostMapper extends BaseMapper<PostDO> {

    /**
     * 按用户查询帖子（is_del=0 且 status=1，created_stime 倒序，同秒按 id 倒序）。
     * 带双字段游标两参（C8 同 selectFeedPage 口径），供 T070 listUserPosts 放大补偿扫描复用（建议5）。
     *
     * @param userId     目标作者 ID
     * @param cursorTime 游标时间（首页/首轮传 null=不追加游标条件）
     * @param cursorId   游标帖子 ID（与 cursorTime 成对使用，严格小于语义排除已扫锚点）
     * @param limit      批大小（结果上限由 Service 层控制）
     * @return 帖子列表（无帖返回空列表）
     */
    List<PostDO> selectUserPosts(@Param("userId") Long userId, @Param("cursorTime") Date cursorTime,
            @Param("cursorId") Long cursorId, @Param("limit") Integer limit);

    /**
     * Feed 候选分批查询（T080 追加，design §5.4）：自己+好友的可见候选帖（is_del=0 且 status=1，
     * created_stime 倒序，同秒按 id 倒序），私密帖非作者在 SQL 预过滤（R1）。
     *
     * <p>双字段游标（C8，与 selectUserPosts 同口径）：严格小于语义排除已扫锚点及之前记录；
     * Service 层按"批大小 pageSize×3、最多 3 批"放大补偿扫描后逐帖 canView 过滤（B1 双锚点）。
     *
     * @param userIds    候选作者 ID 集合（viewerId + 好友 ID，非空，空集合由 Service 前置拦截）
     * @param viewerId   当前视角用户（私密帖仅作者本人放行：visibility_type != 2 OR user_id = viewerId）
     * @param cursorTime 游标时间（首页传 null=不追加游标条件）
     * @param cursorId   游标帖子 ID（与 cursorTime 成对使用）
     * @param limit      批大小（建议 pageSize×3）
     * @return 候选帖列表（按 created_stime DESC, id DESC；无候选返回空列表）
     */
    List<PostDO> selectFeedPage(@Param("userIds") List<Long> userIds, @Param("viewerId") Long viewerId,
            @Param("cursorTime") Date cursorTime, @Param("cursorId") Long cursorId, @Param("limit") Integer limit);

    /**
     * 软删帖子（is_del=1；deletePost 调用，仅作者可删由 Service 校验；status 保留业务态快照 C7）。
     *
     * @param postId 帖子 ID
     * @return 更新行数
     */
    int updateIsDel(@Param("postId") Long postId);

    /**
     * 批量插入帖子（T090 模拟数据复用；useGeneratedKeys 回填自增 id，供可见性两表外键生成）。
     *
     * @param list 帖子集合（非空）
     * @return 插入行数
     */
    int batchInsert(@Param("list") List<PostDO> list);

    /**
     * 软删全部帖子（T090 clear=true 复用；带 WHERE 条件）。
     *
     * @return 更新行数
     */
    int logicDeleteAll();
}
