package com.funny.moments.service.social.impl;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.funny.moments.common.enums.VisibilityTypeEnum;
import com.funny.moments.dao.entity.PostDO;
import com.funny.moments.dao.mapper.PostMapper;
import com.funny.moments.dao.mapper.PostVisibilityTagMapper;
import com.funny.moments.dao.mapper.PostVisibilityUserMapper;
import com.funny.moments.service.social.IVisibilityService;
import com.funny.moments.service.social.cache.FriendTagCacheManager;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;

/**
 * 帖子可见性统一判断实现（design §5.2/§6.2，T050）：canView 判定链短路执行，
 * 作者本人 &gt; 公开 &gt; 私密 &gt; 标签/好友匹配（type=3 命中可见、type=4 命中不可见，空集语义 C6）。
 * 只读无事务；tagHit 的标签来源走 FriendTagCacheManager（缓存 miss/异常自动回源 DB，C12 弱依赖），
 * 事务外读缓存不违反 java-guide §5。
 *
 * <p>调用方：PostServiceImpl（详情 1011 / 按用户查静默过滤）与 FeedServiceImpl（逐帖过滤，T080）。
 *
 * @Author: funny2048
 * @Date: 2026-09-21
 */
@Slf4j
@Service
public class VisibilityServiceImpl implements IVisibilityService {

    @Autowired
    private PostMapper postMapper;

    @Autowired
    private PostVisibilityTagMapper postVisibilityTagMapper;

    @Autowired
    private PostVisibilityUserMapper postVisibilityUserMapper;

    @Autowired
    private FriendTagCacheManager friendTagCacheManager;

    @Override
    public boolean canView(Long viewerId, Long postId) {
        // 0 帖子存在性：selectById 经 @TableLogic 自动过滤 is_del=1（已删帖全路径不可见，design §5.3）
        PostDO post = postMapper.selectById(postId);
        if (Objects.isNull(post)) {
            return false;
        }
        // 1 作者本人：最高优先级（私密帖作者自己可见）
        if (Objects.equals(post.getUserId(), viewerId)) {
            return true;
        }
        Integer type = post.getVisibilityType();
        // 2 公开：所有人可见
        if (VisibilityTypeEnum.PUBLIC.getCode().equals(type)) {
            return true;
        }
        // 3 私密：仅作者可见（上面已短路）
        if (VisibilityTypeEnum.PRIVATE.getCode().equals(type)) {
            log.debug("canView rejected by PRIVATE, viewerId={}, postId={}", viewerId, postId);
            return false;
        }
        // 4/5 type=3/4 共用一次命中计算：标签 OR 好友匹配（design §5.2）
        boolean hit = tagHit(post, viewerId) || userHit(post, viewerId);
        if (VisibilityTypeEnum.PART_VISIBLE.getCode().equals(type)) {
            // 部分可见：命中才可见；名单空集 → tagHit/userHit 皆 false → 不可见（C6）
            if (!hit) {
                log.debug("canView rejected by PART_VISIBLE miss, viewerId={}, postId={}", viewerId, postId);
            }
            return hit;
        }
        // 不给谁看（type=4）：命中则不可见；名单空集 → 等同公开（C6）
        if (hit) {
            log.debug("canView rejected by EXCLUDE hit, viewerId={}, postId={}", viewerId, postId);
        }
        return !hit;
    }

    /**
     * 标签命中：帖子指定标签 ∩ 作者给 viewer 打的标签 ≠ ∅。
     * 帖子未配标签名单（空集）直接未命中，免查作者标签缓存（type=3 空集不可见 / type=4 空集可见，C6）。
     */
    private boolean tagHit(PostDO post, Long viewerId) {
        List<Long> postTagIds = postVisibilityTagMapper.selectTagIdsByPostId(post.getId());
        if (CollectionUtils.isEmpty(postTagIds)) {
            return false;
        }
        // 作者给观众打的标签（缓存读取，miss/异常自动回源 DB；owner=帖子作者，design §5.2）
        Set<Long> viewerTagIds = friendTagCacheManager.getTagIds(post.getUserId(), viewerId);
        if (CollectionUtils.isEmpty(viewerTagIds)) {
            return false;
        }
        return postTagIds.stream().anyMatch(viewerTagIds::contains);
    }

    /**
     * 好友命中：帖子指定好友名单包含 viewer（type=3 命中可见 / type=4 命中不可见）。
     */
    private boolean userHit(PostDO post, Long viewerId) {
        return postVisibilityUserMapper.selectUserIdsByPostId(post.getId()).contains(viewerId);
    }
}
