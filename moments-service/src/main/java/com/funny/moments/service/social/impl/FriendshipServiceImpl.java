package com.funny.moments.service.social.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.funny.framework.core.exception.BizException;
import com.funny.moments.common.enums.GenderEnum;
import com.funny.moments.common.enums.SocialErrorCode;
import com.funny.moments.common.utils.DateUtils;
import com.funny.moments.dao.dto.FriendTagNameDTO;
import com.funny.moments.dao.entity.FriendTagDO;
import com.funny.moments.dao.entity.UserDO;
import com.funny.moments.dao.mapper.FriendTagMapper;
import com.funny.moments.dao.mapper.FriendTagRelationMapper;
import com.funny.moments.dao.mapper.UserMapper;
import com.funny.moments.model.out.FriendDetailOut;
import com.funny.moments.model.out.FriendOut;
import com.funny.moments.service.social.IFriendshipService;
import com.funny.moments.service.social.cache.FriendIdsCacheManager;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;

/**
 * 好友查询实现（design §6.2，T020）：只读无事务；好友 ID 集合经 FriendIdsCacheManager 缓存。
 *
 * <p>tagId 过滤分支（R3 裁决方案①）：标签存在性与归属由本 task 自建的
 * FriendTagMapper.selectByTagId + FriendTagRelationMapper.selectFriendIdsByTag 支撑，
 * 不依赖 T030 任何产物（R2-NB2 独立编译交付）。
 *
 * @Author: funny2048
 * @Date: 2026-09-21
 */
@Slf4j
@Service
public class FriendshipServiceImpl implements IFriendshipService {

    @Autowired
    private FriendTagMapper friendTagMapper;

    @Autowired
    private FriendTagRelationMapper friendTagRelationMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private FriendIdsCacheManager friendIdsCacheManager;

    @Override
    public List<FriendOut> listFriends(Long viewerId, Long tagId) {
        List<Long> friendIds;
        if (Objects.nonNull(tagId)) {
            // tagId 过滤分支：越界 1001；标签不存在或不归属当前用户均 1004（api.md 接口3）
            if (tagId <= 0) {
                throw bizException(SocialErrorCode.PARAM_INVALID);
            }
            FriendTagDO tag = friendTagMapper.selectByTagId(tagId);
            if (Objects.isNull(tag) || !Objects.equals(tag.getUserId(), viewerId)) {
                throw bizException(SocialErrorCode.TAG_NOT_FOUND);
            }
            // 绑定表无 DB 唯一键，distinct 防并发残留 (tag, friend) 双行导致列表重复
            friendIds = friendTagRelationMapper.selectFriendIdsByTag(tagId).stream()
                    .distinct().collect(Collectors.toList());
        } else {
            // 全量好友：走缓存（miss/异常回源 DB，空集有哨兵缓存）
            friendIds = new ArrayList<>(friendIdsCacheManager.get(viewerId));
        }
        if (CollectionUtils.isEmpty(friendIds)) {
            return new ArrayList<>();
        }
        // 好友基础信息批量组装（防 N+1）
        List<UserDO> users = userMapper.selectByUserIds(friendIds);
        Map<Long, UserDO> userMap = users.stream()
                .collect(Collectors.toMap(UserDO::getId, u -> u, (k1, k2) -> k2));
        // tagNames 批量组装（R2-NB2：本 task 自建方法，不依赖 T030）
        Map<Long, List<String>> tagNamesMap = batchTagNames(viewerId, friendIds);
        // 按 friendIds 顺序组装；已软删好友跳过（防御）
        List<FriendOut> result = new ArrayList<>(friendIds.size());
        for (Long friendId : friendIds) {
            UserDO user = userMap.get(friendId);
            if (Objects.isNull(user)) {
                log.warn("friend user soft-deleted, skip assembly, friendId={}", friendId);
                continue;
            }
            FriendOut out = new FriendOut();
            fillBase(out, user);
            out.setTagNames(tagNamesMap.getOrDefault(friendId, new ArrayList<>()));
            result.add(out);
        }
        return result;
    }

    @Override
    public FriendDetailOut getFriendDetail(Long viewerId, Long friendUserId) {
        if (Objects.isNull(friendUserId) || friendUserId <= 0) {
            throw bizException(SocialErrorCode.PARAM_INVALID);
        }
        UserDO user = userMapper.selectById(friendUserId);
        if (Objects.isNull(user)) {
            throw bizException(SocialErrorCode.USER_NOT_FOUND);
        }
        FriendDetailOut out = new FriendDetailOut();
        out.setUserId(user.getId());
        out.setNickname(user.getNickname());
        out.setAvatar(user.getAvatar());
        out.setGender(user.getGender());
        GenderEnum genderEnum = GenderEnum.byCode(user.getGender());
        out.setGenderStr(genderEnum == null ? GenderEnum.UNKNOWN.getDesc() : genderEnum.getDesc());
        out.setCity(user.getCity());
        out.setCreateTimeStr(DateUtils.formatDefault(user.getCreatedStime()));
        // 单好友标签组装：复用批量方法（顺序按标签 id，展示侧 distinct 兜底）
        List<FriendTagNameDTO> rows = friendTagRelationMapper
                .selectTagNamesByOwnerAndFriends(viewerId, Collections.singletonList(friendUserId));
        out.setTagNames(rows.stream().map(FriendTagNameDTO::getTagName).distinct().collect(Collectors.toList()));
        return out;
    }

    @Override
    public Set<Long> listFriendIds(Long viewerId) {
        return friendIdsCacheManager.get(viewerId);
    }

    /**
     * 批量组装 (friendId -&gt; tagNames)：SQL 已按 (friend, tagName) GROUP BY 去重，
     * 此处再 distinct 兜底并发绑定窗口的展示侧重复（design §5.6 规则5 / 建议4）。
     */
    private Map<Long, List<String>> batchTagNames(Long ownerId, List<Long> friendIds) {
        List<FriendTagNameDTO> rows = friendTagRelationMapper
                .selectTagNamesByOwnerAndFriends(ownerId, friendIds);
        Map<Long, List<String>> tagNamesMap = rows.stream().collect(Collectors.groupingBy(
                FriendTagNameDTO::getFriendUserId,
                Collectors.mapping(FriendTagNameDTO::getTagName, Collectors.toList())));
        tagNamesMap.replaceAll((k, v) -> v.stream().distinct().collect(Collectors.toList()));
        return tagNamesMap;
    }

    /**
     * FriendOut 基础字段填充（UserOut 同构字段）。
     */
    private void fillBase(FriendOut out, UserDO user) {
        out.setUserId(user.getId());
        out.setNickname(user.getNickname());
        out.setAvatar(user.getAvatar());
        out.setGender(user.getGender());
        GenderEnum genderEnum = GenderEnum.byCode(user.getGender());
        out.setGenderStr(genderEnum == null ? GenderEnum.UNKNOWN.getDesc() : genderEnum.getDesc());
        out.setCity(user.getCity());
    }

    /**
     * BizException 构造辅助：框架 BizException 仅 (code, message) 构造，收敛重复取值（口径同 UserServiceImpl）。
     */
    private BizException bizException(SocialErrorCode errorCode) {
        return new BizException(errorCode.getCode(), errorCode.getMessage());
    }
}
