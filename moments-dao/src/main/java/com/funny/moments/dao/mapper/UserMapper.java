package com.funny.moments.dao.mapper;

import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.funny.moments.dao.entity.UserDO;
import org.apache.ibatis.annotations.Param;

/**
 * 用户表 Mapper（design §6.3，T010）：继承 BaseMapper 复用 selectById（@TableLogic 过滤 is_del），
 * 业务 SQL 全部写在 XML（resources/mapper/UserMapper.xml）。
 *
 * <p>说明：DAO 生成工具 CodeGenerator 需 CODEGEN_DB_* 环境变量（未配置）且 pathInfo 固定输出
 * sample-* 模块，与本项目 moments-* 布局不符，故按 T004 既有手写基线手工实现（口径一致）。
 *
 * @Author: funny2048
 * @Date: 2026-09-21
 */
public interface UserMapper extends BaseMapper<UserDO> {

    /**
     * 分页查询用户（is_del=0，按注册顺序 id 升序，保证分页确定性）。
     *
     * @param offset   起始偏移（(pageNo-1)*pageSize，由 Service 计算）
     * @param pageSize 每页条数
     * @return 用户列表
     */
    List<UserDO> selectPageList(@Param("offset") Integer offset, @Param("pageSize") Integer pageSize);

    /**
     * 按 ID 集合批量查询用户（好友/Feed 作者信息组装，防 N+1）。
     *
     * @param userIds 用户 ID 集合（非空，空集合由 Service 前置拦截）
     * @return 用户列表（命中条数 ≤ 入参条数）
     */
    List<UserDO> selectByUserIds(@Param("userIds") List<Long> userIds);

    /**
     * 统计未删除用户总数（is_del=0）。
     *
     * @return 总数
     */
    Long countAll();

    /**
     * 批量插入用户（T090 模拟数据复用；回填自增 id 供后续好友关系生成）。
     *
     * @param list 用户集合（非空）
     * @return 插入行数
     */
    int batchInsert(@Param("list") List<UserDO> list);

    /**
     * 软删全部用户（T090 clear=true 复用；带 WHERE 条件）。
     *
     * @return 更新行数
     */
    int logicDeleteAll();
}
