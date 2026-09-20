package com.funny.moments.dao.mapper;

import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.funny.moments.dao.entity.PostVisibilityTagDO;
import org.apache.ibatis.annotations.Param;

/**
 * 帖子指定标签表 Mapper（design §6.3，T060 建文件 + T050 追加查询）：
 * 写入部分 batchInsert/logicDeleteAll（T060）；selectTagIdsByPostId（canView 的 tagHit 判定，T050 追加）。
 * 删标签不级联本表（按 tagId 匹配自然失效，C13）。
 *
 * <p>说明：DAO 生成工具 CodeGenerator 需 CODEGEN_DB_* 环境变量（未配置）且 pathInfo 固定输出
 * sample-* 模块，与本项目 moments-* 布局不符，故按 T004 既有手写基线手工实现（口径同 UserMapper）。
 *
 * @Author: funny2048
 * @Date: 2026-09-21
 */
public interface PostVisibilityTagMapper extends BaseMapper<PostVisibilityTagDO> {

    /**
     * 批量插入帖子指定标签（发帖事务内，仅 type=3/4 且 visibilityTagIds 非空；入参已去重，
     * 防 uniq_post_tag 唯一键冲突整批回滚；T090 模拟数据复用）。
     *
     * @param list 指定标签集合（非空）
     * @return 插入行数
     */
    int batchInsert(@Param("list") List<PostVisibilityTagDO> list);

    /**
     * 查询帖子指定标签 ID 集合（canView 的 tagHit 判定数据源，design §5.2，T050 追加）：
     * 与作者给 viewer 打的标签集求交集，非空即命中。
     *
     * @param postId 帖子 ID
     * @return 指定标签 ID 列表（无记录返回空列表，禁 null）
     */
    List<Long> selectTagIdsByPostId(@Param("postId") Long postId);

    /**
     * 软删全部帖子指定标签（T090 clear=true 复用；带 WHERE 条件）。
     *
     * @return 更新行数
     */
    int logicDeleteAll();
}
