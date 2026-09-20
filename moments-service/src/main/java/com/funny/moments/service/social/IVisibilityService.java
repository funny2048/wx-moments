package com.funny.moments.service.social;

/**
 * 帖子可见性统一判断 Service（design §5.2/§6.2，T050；PRD §3.8）：canView 单一实现，
 * 被 PostServiceImpl（详情/按用户查列表）与 FeedServiceImpl（逐帖过滤）共用
 * （design §5.6 规则 1：统一单实现，禁止散落重复判断）。只读无事务、无 Controller（内部功能）。
 *
 * @Author: funny2048
 * @Date: 2026-09-21
 */
public interface IVisibilityService {

    /**
     * 判定 viewer 对帖子的可见性（design §5.2 判定链，优先级自上而下短路）：
     * <ol>
     * <li>帖子不存在（含已软删，selectById 经 @TableLogic 过滤）→ false</li>
     * <li>作者本人 → true（最高优先级，私密帖作者自己可见）</li>
     * <li>type=1 公开 → true</li>
     * <li>type=2 私密 → false</li>
     * <li>type=3 部分可见 → tagHit OR userHit（名单空集时两者皆 false → 不可见，C6）</li>
     * <li>type=4 不给谁看 → !(tagHit OR userHit)（名单空集 → 可见，等同公开，C6）</li>
     * </ol>
     * tagHit = 帖子指定标签 ∩ 作者给 viewer 打的标签（经 FriendTagCacheManager 缓存读取）≠ ∅；
     * userHit = 帖子指定好友名单包含 viewer。
     *
     * @param viewerId 当前视角用户 ID（调用方经 CurrentUserResolver.requireUser 保证非空）
     * @param postId   帖子 ID
     * @return true=可见；false=不可见（调用方按语义决定 1011 或静默过滤）
     */
    boolean canView(Long viewerId, Long postId);
}
