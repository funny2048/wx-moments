package com.funny.moments.service.social;

import java.util.List;

import com.funny.moments.model.out.UserOut;
import com.funny.moments.model.out.UserPageOut;

/**
 * 用户查询 Service（design §3.2.1/§3.2.2/§6.2，T010）：只读无事务。
 *
 * @Author: funny2048
 * @Date: 2026-09-21
 */
public interface IUserService {

    /**
     * 分页查询用户列表（用户列表页 + 切换器数据源）。
     *
     * @param pageNo   页码，缺省默认 1；显式传入 <1 抛 1001（B2 统一策略，非数字由绑定层承载）
     * @param pageSize 每页条数，缺省默认 20；显式传入 <1 或 >500 抛 1001
     * @return 总数 + 当前页用户
     */
    UserPageOut listUsers(Integer pageNo, Integer pageSize);

    /**
     * 查询单个用户详情。
     *
     * @param userId 用户 ID，null 或 <=0 抛 1001
     * @return 用户详情；不存在（含已删除）抛 1002
     */
    UserOut getUser(Long userId);

    /**
     * 按 ID 集合批量组装用户出参（好友/Feed 作者信息，防 N+1）。
     *
     * @param userIds 用户 ID 集合；空集合返回空列表（禁 null）
     * @return 按入参顺序返回；入参中不存在（已软删）的 ID 被跳过
     */
    List<UserOut> listByUserIds(List<Long> userIds);
}
