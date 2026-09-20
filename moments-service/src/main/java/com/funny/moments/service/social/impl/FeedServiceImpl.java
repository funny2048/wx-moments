package com.funny.moments.service.social.impl;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.funny.framework.core.exception.BizException;
import com.funny.moments.common.consts.PatternConsts;
import com.funny.moments.common.enums.SocialErrorCode;
import com.funny.moments.common.utils.DateUtils;
import com.funny.moments.dao.entity.PostDO;
import com.funny.moments.dao.entity.PostImageDO;
import com.funny.moments.dao.entity.UserDO;
import com.funny.moments.dao.mapper.PostImageMapper;
import com.funny.moments.dao.mapper.PostMapper;
import com.funny.moments.dao.mapper.UserMapper;
import com.funny.moments.model.out.FeedOut;
import com.funny.moments.service.social.IFeedService;
import com.funny.moments.service.social.IVisibilityService;
import com.funny.moments.service.social.cache.FriendIdsCacheManager;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

/**
 * Feed 瀑布流实现（design §3.2.16/§5.4，T080；B1 双锚点定稿）：Cursor 分页 + 放大补偿扫描。
 *
 * <p>双锚点语义：页末锚点=items 末条（截断分支，被截断可见帖下页重扫、canView 幂等）；
 * 扫描进度锚点=最后一批候选末条（items 不足/为空分支，翻页链延续、已扫候选不重扫——游标
 * 严格小于语义保证）。nextCursor 输出规则保证 hasMore=true 时必非 null，从根上消除
 * "空页 + 无游标 → 前端退化首页请求 → 永久循环"（B1 阻塞项修复）。
 *
 * <p>只读无事务；好友 ID 集合经 FriendIdsCacheManager（缓存 miss/异常自动回源 DB，C12 弱依赖），
 * 事务外读缓存不违反 java-guide §5。items=[] && hasMore=true && nextCursor≠null 为合法组合
 * （本页扫描范围 ≤9×pageSize 候选内无可见帖但候选流未扫尽，前端连续 3 空页停止由 T110 承载）。
 *
 * @Author: funny2048
 * @Date: 2026-09-21
 */
@Slf4j
@Service
public class FeedServiceImpl implements IFeedService {

    /** 每页条数缺省值（api.md 接口16：默认 20，B2 缺省才取默认） */
    private static final int DEFAULT_PAGE_SIZE = 20;

    /** 每页条数上限（api.md 接口16：显式 >50 抛 1001，B2 统一策略） */
    private static final int MAX_PAGE_SIZE = 50;

    /** 放大补偿批大小倍数（design §5.4：批大小 = pageSize×3，缓解先截断后过滤漏旧公开帖） */
    private static final int SCAN_BATCH_FACTOR = 3;

    /** 放大补偿最大扫描批数（design §5.4：最多 3 批，有界性 9×pageSize 候选） */
    private static final int MAX_SCAN_ROUNDS = 3;

    /** 游标明文两段分隔符（{createdStime毫秒}:{postId}） */
    private static final String CURSOR_SEPARATOR = ":";

    @Autowired
    private PostMapper postMapper;

    @Autowired
    private PostImageMapper postImageMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private FriendIdsCacheManager friendIdsCacheManager;

    @Autowired
    private IVisibilityService visibilityService;

    @Override
    public FeedOut getFeed(Long viewerId, String cursor, Integer pageSize) {
        // 1 pageSize 校验（B2 统一策略）：缺省取默认 20；显式越界（<1 或 >50）抛 1001
        //   （非数字由 web 层 MethodArgumentTypeMismatchException 承载转 1001，Service 见不到）
        int size = DEFAULT_PAGE_SIZE;
        if (Objects.nonNull(pageSize)) {
            if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
                throw bizException(SocialErrorCode.PARAM_INVALID);
            }
            size = pageSize;
        }
        // 2 cursor 解码（R4 不静默重置首页）：非空才解码；decode 异常 / 格式不匹配 / 数值段溢出均 1030
        Date cursorTime = null;
        Long cursorId = null;
        if (StringUtils.isNotBlank(cursor)) {
            byte[] decodedBytes;
            try {
                // 非法 Base64 串（如 "!!!"）在此抛 IllegalArgumentException（建议6：decode 异常同码 1030）
                decodedBytes = Base64.getDecoder().decode(cursor);
            } catch (IllegalArgumentException e) {
                throw bizException(SocialErrorCode.CURSOR_INVALID);
            }
            String decoded = new String(decodedBytes, StandardCharsets.UTF_8);
            // 正则 ^\d{13}:\d{1,18}$（R2-建议7：id 段限 18 位防 parseLong 溢出落 code=100）
            if (!PatternConsts.FEED_CURSOR.matcher(decoded).matches()) {
                throw bizException(SocialErrorCode.CURSOR_INVALID);
            }
            String[] parts = decoded.split(CURSOR_SEPARATOR);
            try {
                cursorTime = new Date(Long.parseLong(parts[0]));
                cursorId = Long.parseLong(parts[1]);
            } catch (NumberFormatException e) {
                // 双保险：正则限位下理论不可达，防口径变更后溢出落框架兜底 code=100（R2-建议7）
                throw bizException(SocialErrorCode.CURSOR_INVALID);
            }
        }
        // 3 候选作者集合：viewerId + 好友 ID（去重；缓存空集有哨兵不穿透 DB，T020 建议8）
        Set<Long> friendIds = friendIdsCacheManager.get(viewerId);
        Set<Long> candidateIdSet = new HashSet<>(friendIds);
        candidateIdSet.add(viewerId);
        List<Long> candidateUserIds = new ArrayList<>(candidateIdSet);
        // 4 放大补偿取数循环（B1）：扫描进度锚点初始=入参 cursor 值；批取满且未集满则续批
        int batchSize = size * SCAN_BATCH_FACTOR;
        Date scanTime = cursorTime;
        Long scanId = cursorId;
        boolean scanEnd = false;
        int scannedCount = 0;
        List<PostDO> collected = new ArrayList<>();
        int round = 0;
        while (round < MAX_SCAN_ROUNDS && collected.size() < size) {
            List<PostDO> batch = postMapper.selectFeedPage(candidateUserIds, viewerId, scanTime, scanId, batchSize);
            if (CollectionUtils.isEmpty(batch)) {
                // 候选流扫尽（含无帖/无好友空集场景）
                scanEnd = true;
                break;
            }
            // 扫描进度锚点前移到本批末条（无论本批有无可见帖，design §5.4）
            PostDO last = batch.get(batch.size() - 1);
            scanTime = last.getCreatedStime();
            scanId = last.getId();
            scannedCount += batch.size();
            // canView 统一过滤（type=1 直通 SQL 已放行；type=2 非作者已被 SQL 预过滤不进候选）；
            // 全量收集不内层截断：collected 可超过 pageSize，由输出判定分支一截断并取页末锚点
            for (PostDO post : batch) {
                if (visibilityService.canView(viewerId, post.getId())) {
                    collected.add(post);
                }
            }
            if (batch.size() < batchSize) {
                // 末批不满=候选流到尾
                scanEnd = true;
                break;
            }
            round++;
        }
        // 5 输出判定（B1 核心，四分支完备定义）
        FeedOut out = new FeedOut();
        List<PostDO> pagePosts;
        if (collected.size() > size) {
            // 分支一·截断发生：items=前 pageSize 条；页末锚点=items 末条（被截断可见帖下页重扫不丢）
            pagePosts = new ArrayList<>(collected.subList(0, size));
            out.setHasMore(true);
            PostDO pageLast = pagePosts.get(pagePosts.size() - 1);
            out.setNextCursor(encodeCursor(pageLast.getCreatedStime(), pageLast.getId()));
        } else if (scanEnd) {
            // 分支二·候选流扫尽：真无更多（含空集场景），nextCursor=null
            pagePosts = collected;
            out.setHasMore(false);
            out.setNextCursor(null);
        } else {
            // 分支三·未集满且候选流未扫尽：items 可为空集，扫描进度锚点保证翻页链延续不重扫——
            // items=[] && hasMore=true && nextCursor≠null 合法组合（前端连续 3 空页停止，T110）
            pagePosts = collected;
            out.setHasMore(true);
            out.setNextCursor(encodeCursor(scanTime, scanId));
        }
        // 6 批量组装（防 N+1，design §5.4）：图片 selectByPostIds + 作者 selectByUserIds
        out.setItems(toFeedItemOutList(pagePosts));
        // 关键节点统计日志（design §10：候选数/可见数/批次）
        log.info("getFeed done, viewerId={}, pageSize={}, cursorApplied={}, rounds={}, scanEnd={}, "
                        + "candidateAuthorCount={}, scannedCount={}, visibleCount={}, itemSize={}, hasMore={}",
                viewerId, size, Objects.nonNull(cursorTime), round, scanEnd, candidateUserIds.size(),
                scannedCount, collected.size(), pagePosts.size(), out.getHasMore());
        return out;
    }

    /**
     * 游标编码：Base64("{createdStime毫秒}:{postId}")。对客户端为不透明值（建议1：不承诺内部语义）；
     * created_stime 为 DATETIME 秒级精度，毫秒段恒为 000，解码侧 13 位正则天然匹配。
     */
    private String encodeCursor(Date cursorTime, Long cursorId) {
        String raw = cursorTime.getTime() + CURSOR_SEPARATOR + cursorId;
        return Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Feed 元素批量组装（防 N+1）：作者信息一次批量查（distinct 作者 ID → Map，toMap 处理重复 key），
     * 图片一次批量查按 postId 分组（selectByPostIds 已按 (post_id, sort, id) 排序，组内即展示顺序）。
     */
    private List<FeedOut.FeedItemOut> toFeedItemOutList(List<PostDO> posts) {
        if (CollectionUtils.isEmpty(posts)) {
            return new ArrayList<>();
        }
        List<Long> postIds = posts.stream().map(PostDO::getId).collect(Collectors.toList());
        Map<Long, List<String>> imageUrlsByPostId = postImageMapper.selectByPostIds(postIds).stream()
                .collect(Collectors.groupingBy(PostImageDO::getPostId,
                        Collectors.mapping(PostImageDO::getImageUrl, Collectors.toList())));
        List<Long> authorIds = posts.stream().map(PostDO::getUserId).distinct().collect(Collectors.toList());
        Map<Long, UserDO> authorMap = userMapper.selectByUserIds(authorIds).stream()
                .collect(Collectors.toMap(UserDO::getId, Function.identity(), (k1, k2) -> k2));
        List<FeedOut.FeedItemOut> items = new ArrayList<>(posts.size());
        for (PostDO post : posts) {
            FeedOut.FeedItemOut item = new FeedOut.FeedItemOut();
            item.setPostId(post.getId());
            item.setUserId(post.getUserId());
            UserDO author = authorMap.get(post.getUserId());
            if (Objects.nonNull(author)) {
                item.setNickname(author.getNickname());
                item.setAvatar(author.getAvatar());
            }
            item.setContent(post.getContent());
            item.getImageUrls().addAll(imageUrlsByPostId.getOrDefault(post.getId(), new ArrayList<>()));
            item.setCreateTimeStr(DateUtils.formatDefault(post.getCreatedStime()));
            items.add(item);
        }
        return items;
    }

    /**
     * BizException 构造辅助：框架 BizException 仅 (code, message) 构造，收敛重复取值（口径同 PostServiceImpl）。
     */
    private BizException bizException(SocialErrorCode errorCode) {
        return new BizException(errorCode.getCode(), errorCode.getMessage());
    }
}
