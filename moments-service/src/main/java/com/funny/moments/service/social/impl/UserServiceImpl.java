package com.funny.moments.service.social.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.funny.framework.core.exception.BizException;
import com.funny.moments.common.enums.GenderEnum;
import com.funny.moments.common.enums.SocialErrorCode;
import com.funny.moments.common.utils.DateUtils;
import com.funny.moments.dao.entity.UserDO;
import com.funny.moments.dao.mapper.UserMapper;
import com.funny.moments.model.out.UserOut;
import com.funny.moments.model.out.UserPageOut;
import com.funny.moments.service.social.IUserService;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;

/**
 * 用户查询实现（design §6.2，T010）：只读查询，无事务注解。
 *
 * <p>参数越界统一策略（B2）：缺省才取默认值；显式传入越界一律抛 1001。
 * 非数字入参由 Spring 绑定层承载转 1001（T100 BizExceptionAdvice），Service 层见不到。
 *
 * @Author: funny2048
 * @Date: 2026-09-21
 */
@Slf4j
@Service
public class UserServiceImpl implements IUserService {

    /** 页码缺省值 */
    private static final int DEFAULT_PAGE_NO = 1;

    /** 每页条数缺省值 */
    private static final int DEFAULT_PAGE_SIZE = 20;

    /** 每页条数上限（切换器场景传 500） */
    private static final int MAX_PAGE_SIZE = 500;

    @Autowired
    private UserMapper userMapper;

    @Override
    public UserPageOut listUsers(Integer pageNo, Integer pageSize) {
        // B2 统一策略：缺省才取默认；显式传入 pageNo<1 或 pageSize<1 或 >500 抛 1001
        int no = pageNo == null ? DEFAULT_PAGE_NO : pageNo;
        int size = pageSize == null ? DEFAULT_PAGE_SIZE : pageSize;
        if (no < 1 || size < 1 || size > MAX_PAGE_SIZE) {
            throw bizException(SocialErrorCode.PARAM_INVALID);
        }
        UserPageOut out = new UserPageOut();
        out.setTotal(userMapper.countAll());
        List<UserDO> users = userMapper.selectPageList((no - 1) * size, size);
        out.setUsers(users.stream().map(this::convert).collect(Collectors.toList()));
        return out;
    }

    @Override
    public UserOut getUser(Long userId) {
        if (Objects.isNull(userId) || userId <= 0) {
            throw bizException(SocialErrorCode.PARAM_INVALID);
        }
        // selectById 经 @TableLogic 自动过滤 is_del=1
        UserDO user = userMapper.selectById(userId);
        if (Objects.isNull(user)) {
            throw bizException(SocialErrorCode.USER_NOT_FOUND);
        }
        return convert(user);
    }

    @Override
    public List<UserOut> listByUserIds(List<Long> userIds) {
        if (CollectionUtils.isEmpty(userIds)) {
            return new ArrayList<>();
        }
        List<UserDO> users = userMapper.selectByUserIds(userIds);
        // 按入参顺序组装（调用方多为列表/Feed 组装，顺序确定性）；已软删用户跳过
        Map<Long, UserDO> userMap = users.stream()
                .collect(Collectors.toMap(UserDO::getId, u -> u, (k1, k2) -> k2));
        return userIds.stream().distinct()
                .map(userMap::get)
                .filter(Objects::nonNull)
                .map(this::convert)
                .collect(Collectors.toList());
    }

    /**
     * DO 转 Out：genderStr 由 GenderEnum.byCode 反查（未匹配兜底"未知"），
     * createTimeStr 格式 yyyy-MM-dd HH:mm:ss（DateUtils.PATTERN）。
     */
    private UserOut convert(UserDO user) {
        UserOut out = new UserOut();
        out.setUserId(user.getId());
        out.setNickname(user.getNickname());
        out.setAvatar(user.getAvatar());
        out.setGender(user.getGender());
        GenderEnum genderEnum = GenderEnum.byCode(user.getGender());
        out.setGenderStr(genderEnum == null ? GenderEnum.UNKNOWN.getDesc() : genderEnum.getDesc());
        out.setCity(user.getCity());
        out.setCreateTimeStr(DateUtils.formatDefault(user.getCreatedStime()));
        return out;
    }

    /**
     * BizException 构造辅助：框架 BizException 仅 (code, message) 构造，收敛重复取值（口径同 LocalImageServiceImpl）。
     */
    private BizException bizException(SocialErrorCode errorCode) {
        return new BizException(errorCode.getCode(), errorCode.getMessage());
    }
}
