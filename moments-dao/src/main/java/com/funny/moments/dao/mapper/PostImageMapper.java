package com.funny.moments.dao.mapper;

import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.funny.moments.dao.entity.PostImageDO;
import org.apache.ibatis.annotations.Param;

/**
 * 帖子图片表 Mapper（design §6.3，T060 建文件 + T070 追加查询）：写入部分 batchInsert/logicDeleteAll（T060）；
 * selectByPostId（帖子详情组装）与 selectByPostIds（按用户查/Feed 批量取图防 N+1）已由 T070 追加（T080 直接复用）。
 *
 * <p>说明：DAO 生成工具 CodeGenerator 需 CODEGEN_DB_* 环境变量（未配置）且 pathInfo 固定输出
 * sample-* 模块，与本项目 moments-* 布局不符，故按 T004 既有手写基线手工实现（口径同 UserMapper）。
 *
 * @Author: funny2048
 * @Date: 2026-09-21
 */
public interface PostImageMapper extends BaseMapper<PostImageDO> {

    /**
     * 批量插入帖子图片（发帖事务内：sort=1..n 按入参 imageUrls 顺序；T090 模拟数据复用）。
     *
     * @param list 图片集合（非空）
     * @return 插入行数
     */
    int batchInsert(@Param("list") List<PostImageDO> list);

    /**
     * 查询单帖图片（is_del=0，按 sort 升序、同序按 id 升序保证确定性；帖子详情组装，T070 追加）。
     *
     * @param postId 帖子 ID
     * @return 图片列表（无图返回空列表，禁 null）
     */
    List<PostImageDO> selectByPostId(@Param("postId") Long postId);

    /**
     * 批量查询多帖图片（is_del=0，按 post_id、sort 升序；按用户查列表/Feed 批量组装防 N+1，T070 追加，T080 复用）。
     *
     * @param postIds 帖子 ID 集合（非空，空集合由 Service 前置拦截）
     * @return 图片列表（组内顺序即展示顺序，按 postId 分组组装）
     */
    List<PostImageDO> selectByPostIds(@Param("postIds") List<Long> postIds);

    /**
     * 软删全部帖子图片（T090 clear=true 复用；带 WHERE 条件）。
     *
     * @return 更新行数
     */
    int logicDeleteAll();
}
