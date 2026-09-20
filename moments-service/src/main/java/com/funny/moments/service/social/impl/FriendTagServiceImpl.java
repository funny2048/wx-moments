package com.funny.moments.service.social.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.funny.framework.core.exception.BizException;
import com.funny.moments.common.enums.SocialErrorCode;
import com.funny.moments.common.utils.DateUtils;
import com.funny.moments.dao.dto.TagFriendCountDTO;
import com.funny.moments.dao.entity.FriendTagDO;
import com.funny.moments.dao.entity.FriendTagRelationDO;
import com.funny.moments.dao.mapper.FriendTagMapper;
import com.funny.moments.dao.mapper.FriendTagRelationMapper;
import com.funny.moments.dao.mapper.FriendshipMapper;
import com.funny.moments.dao.mapper.UserMapper;
import com.funny.moments.model.in.TagCreateIn;
import com.funny.moments.model.in.TagEditIn;
import com.funny.moments.model.out.TagOut;
import com.funny.moments.service.social.IFriendTagService;
import com.funny.moments.service.social.cache.FriendTagCacheManager;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;

/**
 * 好友标签管理实现（design §3.2.5-§3.2.10/§5.6/§6.2，T030）。
 *
 * <p>事务口径（design §6.2）：仅 deleteTag 加 @Transactional(rollbackFor)（级联 2 表写，
 * 缓存 INCR 定稿走 afterCommit 回调，建议3）；create/update/bind/unbind 单表写无事务注解
 * （单 DML 自动提交原子，行为等价，建议 11 留痕），写后 evictAll 顺序调用即事务外。
 *
 * <p>校验顺序（api.md 接口9 / S4 裁决）：标签归属(1004/1006) → 用户存在(1002) → 好友关系(1007)
 * → 绑定查重(1008)，与 design §3.2.9 异常码序一致。
 *
 * @Author: funny2048
 * @Date: 2026-09-21
 */
@Slf4j
@Service
public class FriendTagServiceImpl implements IFriendTagService {

    /** 标签名长度上限（api.md 接口6/7：trim 后 1-16 字符） */
    private static final int TAG_NAME_MAX_LENGTH = 16;

    @Autowired
    private FriendTagMapper friendTagMapper;

    @Autowired
    private FriendTagRelationMapper friendTagRelationMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private FriendshipMapper friendshipMapper;

    @Autowired
    private FriendTagCacheManager friendTagCacheManager;

    @Override
    public List<TagOut> listTags(Long viewerId) {
        List<FriendTagDO> tags = friendTagMapper.selectByUserId(viewerId);
        if (CollectionUtils.isEmpty(tags)) {
            return new ArrayList<>();
        }
        // 批量统计好友数（防 N+1）；无绑定标签不出现在统计结果中，组装时补 0
        Map<Long, Integer> countMap = countMapByTagIds(
                tags.stream().map(FriendTagDO::getId).collect(Collectors.toList()));
        List<TagOut> result = new ArrayList<>(tags.size());
        for (FriendTagDO tag : tags) {
            result.add(toTagOut(tag, countMap.getOrDefault(tag.getId(), 0)));
        }
        return result;
    }

    @Override
    public TagOut createTag(Long viewerId, TagCreateIn in) {
        String tagName = validateTagName(in == null ? null : in.getTagName());
        checkTagNameDuplicated(viewerId, tagName, null);
        FriendTagDO tag = new FriendTagDO();
        tag.setUserId(viewerId);
        tag.setTagName(tagName);
        friendTagMapper.insert(tag);
        log.info("createTag done, viewerId={}, tagId={}, tagName={}", viewerId, tag.getId(), tagName);
        // design §6.2 写操作后 INCR ftagver：非事务方法顺序调用即事务外
        friendTagCacheManager.evictAll();
        // 回读 DB 默认值（created_stime）保证响应时间与库内一致
        return toTagOut(friendTagMapper.selectByTagId(tag.getId()), 0);
    }

    @Override
    public TagOut updateTag(Long viewerId, Long tagId, TagEditIn in) {
        if (Objects.isNull(tagId) || tagId <= 0) {
            throw bizException(SocialErrorCode.PARAM_INVALID);
        }
        String tagName = validateTagName(in == null ? null : in.getTagName());
        requireOwnedTag(viewerId, tagId);
        // 改名查重排除自身 id（样例 campaign 惯用法：UPDATE 改名排除自身再查）
        checkTagNameDuplicated(viewerId, tagName, tagId);
        // 最小更新实体：只含主键 + 待更新字段 tagName（java-guide 2.3，禁止整实体传入更新）
        FriendTagDO update = new FriendTagDO();
        update.setId(tagId);
        update.setTagName(tagName);
        friendTagMapper.updateTagName(update);
        log.info("updateTag done, viewerId={}, tagId={}, tagName={}", viewerId, tagId, tagName);
        friendTagCacheManager.evictAll();
        FriendTagDO updated = friendTagMapper.selectByTagId(tagId);
        Map<Long, Integer> countMap = countMapByTagIds(Collections.singletonList(tagId));
        return toTagOut(updated, countMap.getOrDefault(tagId, 0));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteTag(Long viewerId, Long tagId) {
        if (Objects.isNull(tagId) || tagId <= 0) {
            throw bizException(SocialErrorCode.PARAM_INVALID);
        }
        requireOwnedTag(viewerId, tagId);
        // C13 级联软删：标签 + 其全部绑定同事务；post_visibility_tag 不级联（按 tagId 匹配自然失效）
        int tagRows = friendTagMapper.updateIsDel(tagId);
        int relationRows = friendTagRelationMapper.updateIsDelByTagId(tagId);
        log.info("deleteTag done, viewerId={}, tagId={}, tagRows={}, relationRows={}",
                viewerId, tagId, tagRows, relationRows);
        // 缓存失效定稿（design §5.6 规则8/建议3）：仅允许 afterCommit 回调内 INCR——
        // 方法尾直调=提交前=事务内操作 Redis，违反 java-guide §5（事务内禁中间件）
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    friendTagCacheManager.evictAll();
                }
            });
        } else {
            // 防御分支：无事务上下文（如自调用绕过代理）时直接失效，保证缓存不残留已删标签
            friendTagCacheManager.evictAll();
        }
    }

    @Override
    public void bindUser(Long viewerId, Long tagId, Long userId) {
        validateIds(tagId, userId);
        requireOwnedTag(viewerId, tagId);
        // 1002 先于 1007（Stage 4 审查裁决，对齐 design §3.2.9 异常码序）
        if (Objects.isNull(userMapper.selectById(userId))) {
            throw bizException(SocialErrorCode.USER_NOT_FOUND);
        }
        Long friendshipCount = friendshipMapper.existsFriendship(viewerId, userId);
        if (Objects.isNull(friendshipCount) || friendshipCount == 0) {
            throw bizException(SocialErrorCode.FRIEND_REQUIRED);
        }
        // INSERT 前先 SELECT 查重（java-guide 6.1）；并发窗口残留留痕 design §5.6 规则5
        if (Objects.nonNull(friendTagRelationMapper.selectByTagAndUser(tagId, userId))) {
            throw bizException(SocialErrorCode.TAG_ALREADY_BOUND);
        }
        FriendTagRelationDO relation = new FriendTagRelationDO();
        relation.setTagId(tagId);
        relation.setFriendUserId(userId);
        friendTagRelationMapper.insert(relation);
        log.info("bindUser done, viewerId={}, tagId={}, userId={}", viewerId, tagId, userId);
        // 单表写无事务注解，写后顺序调用即事务外（design §6.2）
        friendTagCacheManager.evictAll();
    }

    @Override
    public void unbindUser(Long viewerId, Long tagId, Long userId) {
        validateIds(tagId, userId);
        requireOwnedTag(viewerId, tagId);
        // 幂等：0 行命中（绑定本不存在）不报错（design §3.2.10/§5.6 规则5b）
        int rows = friendTagRelationMapper.updateIsDelByTagAndUser(tagId, userId);
        log.info("unbindUser done, viewerId={}, tagId={}, userId={}, rows={}", viewerId, tagId, userId, rows);
        friendTagCacheManager.evictAll();
    }

    /**
     * 标签名校验：trim 后 1-16 字符，越界抛 1001（api.md 接口6/7）。
     *
     * @return trim 后的标签名
     */
    private String validateTagName(String tagName) {
        if (Objects.isNull(tagName) || tagName.trim().isEmpty()) {
            throw bizException(SocialErrorCode.PARAM_INVALID);
        }
        String trimmed = tagName.trim();
        if (trimmed.length() > TAG_NAME_MAX_LENGTH) {
            throw bizException(SocialErrorCode.PARAM_INVALID);
        }
        return trimmed;
    }

    /**
     * 同用户内标签名查重（is_del=0 范围内，design §4.1：唯一性由 Service 层保证）。
     *
     * @param excludeTagId 改名查重排除自身 id；创建传 null
     */
    private void checkTagNameDuplicated(Long viewerId, String tagName, Long excludeTagId) {
        List<FriendTagDO> tags = friendTagMapper.selectByUserId(viewerId);
        boolean duplicated = tags.stream().anyMatch(tag ->
                tag.getTagName().equals(tagName) && !Objects.equals(tag.getId(), excludeTagId));
        if (duplicated) {
            throw bizException(SocialErrorCode.TAG_NAME_DUPLICATED);
        }
    }

    /**
     * 标签存在性与归属校验（design §5.6 规则2，水平越权防护）：不存在/已删 1004，非归属人 1006。
     *
     * @return 归属校验通过的标签
     */
    private FriendTagDO requireOwnedTag(Long viewerId, Long tagId) {
        FriendTagDO tag = friendTagMapper.selectByTagId(tagId);
        if (Objects.isNull(tag)) {
            throw bizException(SocialErrorCode.TAG_NOT_FOUND);
        }
        if (!Objects.equals(tag.getUserId(), viewerId)) {
            throw bizException(SocialErrorCode.NOT_TAG_OWNER);
        }
        return tag;
    }

    /**
     * 路径参数校验：tagId/userId 空或 &lt;=0 抛 1001（非数字由 T100 绑定层承载）。
     */
    private void validateIds(Long tagId, Long userId) {
        if (Objects.isNull(tagId) || tagId <= 0 || Objects.isNull(userId) || userId <= 0) {
            throw bizException(SocialErrorCode.PARAM_INVALID);
        }
    }

    /**
     * 批量统计 (tagId -&gt; 好友数)：COUNT(DISTINCT friend_user_id)（R2-建议8）。
     */
    private Map<Long, Integer> countMapByTagIds(List<Long> tagIds) {
        List<TagFriendCountDTO> rows = friendTagMapper.countFriendsByTagIds(tagIds);
        Map<Long, Integer> countMap = new HashMap<>(rows.size());
        for (TagFriendCountDTO row : rows) {
            countMap.put(row.getTagId(), row.getFriendCount());
        }
        return countMap;
    }

    /**
     * TagOut 组装（createdTimeStr 统一 yyyy-MM-dd HH:mm:ss）。
     */
    private TagOut toTagOut(FriendTagDO tag, Integer friendCount) {
        TagOut out = new TagOut();
        out.setTagId(tag.getId());
        out.setTagName(tag.getTagName());
        out.setFriendCount(friendCount);
        out.setCreatedTimeStr(DateUtils.formatDefault(tag.getCreatedStime()));
        return out;
    }

    /**
     * BizException 构造辅助：框架 BizException 仅 (code, message) 构造，收敛重复取值（口径同 UserServiceImpl）。
     */
    private BizException bizException(SocialErrorCode errorCode) {
        return new BizException(errorCode.getCode(), errorCode.getMessage());
    }
}
