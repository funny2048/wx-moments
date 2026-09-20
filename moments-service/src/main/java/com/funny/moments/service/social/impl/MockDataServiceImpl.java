package com.funny.moments.service.social.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.funny.framework.core.exception.BizException;
import com.funny.moments.common.consts.MockDataConsts;
import com.funny.moments.common.enums.GenderEnum;
import com.funny.moments.common.enums.PostStatusEnum;
import com.funny.moments.common.enums.SocialErrorCode;
import com.funny.moments.common.enums.VisibilityTypeEnum;
import com.funny.moments.dao.entity.FriendTagDO;
import com.funny.moments.dao.entity.FriendTagRelationDO;
import com.funny.moments.dao.entity.FriendshipDO;
import com.funny.moments.dao.entity.PostDO;
import com.funny.moments.dao.entity.PostImageDO;
import com.funny.moments.dao.entity.PostVisibilityTagDO;
import com.funny.moments.dao.entity.PostVisibilityUserDO;
import com.funny.moments.dao.entity.UserDO;
import com.funny.moments.dao.mapper.FriendTagMapper;
import com.funny.moments.dao.mapper.FriendTagRelationMapper;
import com.funny.moments.dao.mapper.FriendshipMapper;
import com.funny.moments.dao.mapper.PostImageMapper;
import com.funny.moments.dao.mapper.PostMapper;
import com.funny.moments.dao.mapper.PostVisibilityTagMapper;
import com.funny.moments.dao.mapper.PostVisibilityUserMapper;
import com.funny.moments.dao.mapper.UserMapper;
import com.funny.moments.model.in.MockDataGenerateIn;
import com.funny.moments.model.out.MockDataGenerateOut;
import com.funny.moments.service.social.ILocalImageService;
import com.funny.moments.service.social.IMockDataService;
import com.funny.moments.service.social.cache.FriendIdsCacheManager;
import com.funny.moments.service.social.cache.FriendTagCacheManager;

import lombok.extern.slf4j.Slf4j;

/**
 * 模拟数据批量生成实现（design §5.5，T090；C15/C16/A7/A8/A9/R5/R11）。
 *
 * <p>事务口径（R11 防长事务）：generate 本身无事务注解——clear 段每表 logicDeleteAll 为单 DML
 * 自动提交（即逐表独立小事务）；用户/好友/标签/绑定各批为单条 ≤500 行批量语句自动提交；
 * 帖批为 post+post_image+可见性两表 4 表同事务（经 self 代理调用 writePostBatch，批间提交）。
 * 文件 IO（占位图池）与缓存 INCR 全程事务外（java-guide §5）。
 *
 * <p>中断恢复语义（建议7）：clear=true 时 clear 段完成即先失效双缓存，后续生成中断不残留
 * "已清空数据"的旧缓存；追加模式中断只意味着新数据未进缓存（miss 回源可见），无 stale 风险。
 *
 * @Author: funny2048
 * @Date: 2026-09-21
 */
@Slf4j
@Service
public class MockDataServiceImpl implements IMockDataService {

    // ==== 配置缺省值与上限（api.md 接口11；缺省取默认，显式越界抛 1040，B2） ====
    private static final int DEFAULT_USER_COUNT = 100;
    private static final int MAX_USER_COUNT = 100000;
    private static final int DEFAULT_AVG_FRIENDS = 10;
    private static final int MAX_AVG_FRIENDS = 500;
    private static final int DEFAULT_EXTRA_TAGS = 0;
    private static final int MAX_EXTRA_TAGS = 20;
    private static final int DEFAULT_POST_COUNT = 0;
    private static final int MAX_POST_COUNT = 10000;

    /** 单条批量语句行数上限（R11/java-guide 7.2 批量 ≤500） */
    private static final int BATCH_SIZE = 500;

    /** tagId 回读按 owner 分批大小（与 BATCH_SIZE 同口径，控制 IN 条件规模） */
    private static final int OWNER_QUERY_BATCH = 500;

    /** 人均好友数抖动幅度（design §5.5：avgFriendsPerUser±3） */
    private static final int FRIEND_JITTER = 3;

    /** 加抽次标签概率（30%，模拟一人多标签语义） */
    private static final double SECOND_TAG_PROBABILITY = 0.30;

    /** type=3/4 可见性名单抽样条数上限（shuffle+subList 无放回，R2-建议5） */
    private static final int MAX_VISIBILITY_SAMPLE = 3;

    /** 帖批大小（每批 4 表同事务）：50 帖 → 图 ≤450、可见性 ≤150+150，各单语句均 ≤500 行 */
    private static final int POST_BATCH_SIZE = 50;

    /** 单帖图片数上限（C17：1-9 张） */
    private static final int MAX_IMAGE_COUNT = 9;

    /** 纯图帖（content 为空）概率 */
    private static final double PURE_IMAGE_POST_RATE = 0.30;

    /** 可见范围概率分布（A8/§5.5）：公开 40% / 私密 20% / 部分可见 20% / 不给谁看 20% */
    private static final int PUBLIC_RATE = 40;
    private static final int PRIVATE_RATE = 20;
    private static final int PART_VISIBLE_RATE = 20;

    /** 可见范围抽签总权重（= 40+20+20+20，末段"不给谁看"复用 PART_VISIBLE_RATE 的 20%） */
    private static final int VISIBILITY_TOTAL_RATE = PUBLIC_RATE + PRIVATE_RATE + PART_VISIBLE_RATE * 2;

    /** 占位图池头像段大小（ILocalImageService.ensurePlaceholderPool：前 20 头像 + 后 20 内容图） */
    private static final int AVATAR_POOL_SIZE = 20;

    /** 性别枚举值缓存（随机下标取值，避免魔法数字） */
    private static final GenderEnum[] GENDERS = GenderEnum.values();

    /**
     * 帖子文案池（design §5.5 未冻结公共常量，T090 私有池：随机中文短句模拟朋友圈内容；
     * 纯图帖 content 为空串，图恒 1-9 张故不违反"content 与图非同空"）。
     */
    private static final List<String> CONTENT_POOL = List.of(
            "今天天气真好，出去走走。",
            "刚跑完五公里，状态不错。",
            "周末和朋友聚餐，太开心了。",
            "加班到深夜，给自己点个赞。",
            "这本书真不错，推荐给大家。",
            "周末爬山，山顶的风景美极了。",
            "尝试了新菜谱，味道还不错。",
            "今晚的月亮真圆。",
            "项目终于上线了，太不容易了。",
            "喝咖啡撸猫，周末标配。",
            "坚持早起的第三十天。",
            "路边的花都开了，心情很好。");

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private FriendshipMapper friendshipMapper;

    @Autowired
    private FriendTagMapper friendTagMapper;

    @Autowired
    private FriendTagRelationMapper friendTagRelationMapper;

    @Autowired
    private PostMapper postMapper;

    @Autowired
    private PostImageMapper postImageMapper;

    @Autowired
    private PostVisibilityUserMapper postVisibilityUserMapper;

    @Autowired
    private PostVisibilityTagMapper postVisibilityTagMapper;

    @Autowired
    private ILocalImageService localImageService;

    @Autowired
    private FriendIdsCacheManager friendIdsCacheManager;

    @Autowired
    private FriendTagCacheManager friendTagCacheManager;

    /**
     * 自注入代理（R11 帖批事务）：this 直调不经过 AOP 代理，@Transactional 会失效；
     * 帖批 4 表写必须经 self 调用。@Lazy 保证注入的是初始化完成后的最终代理实例
     * （Spring Boot 3 默认 CGLIB，支持按具体类自注入）。
     */
    @Autowired
    @Lazy
    private MockDataServiceImpl self;

    @Override
    public MockDataGenerateOut generate(MockDataGenerateIn in) {
        long startMillis = System.currentTimeMillis();
        // 0 入参体防御（null body 由 T100 HttpMessageNotReadableException 承载，此处拦直调 null）
        if (Objects.isNull(in)) {
            throw bizException(SocialErrorCode.PARAM_INVALID);
        }
        // 1 配置校验（B2 统一策略：缺省才取默认；显式越界抛 mock 专用码 1040）
        int userCount = in.getUserCount() == null ? DEFAULT_USER_COUNT : in.getUserCount();
        int avgFriends = in.getAvgFriendsPerUser() == null ? DEFAULT_AVG_FRIENDS : in.getAvgFriendsPerUser();
        int extraTags = in.getExtraTagsPerUser() == null ? DEFAULT_EXTRA_TAGS : in.getExtraTagsPerUser();
        int postCount = in.getPostCount() == null ? DEFAULT_POST_COUNT : in.getPostCount();
        boolean clear = Boolean.TRUE.equals(in.getClear());
        if (userCount < 1 || userCount > MAX_USER_COUNT
                || avgFriends < 0 || avgFriends > MAX_AVG_FRIENDS
                || extraTags < 0 || extraTags > MAX_EXTRA_TAGS
                || postCount < 0 || postCount > MAX_POST_COUNT) {
            throw bizException(SocialErrorCode.MOCK_CONFIG_INVALID);
        }
        // 2 clear=true：8 表逐个独立小事务软删（单 DML 自动提交，R11）；
        //    clear 段完成即先失效缓存（建议7：后续生成中断时旧缓存不残留已清空数据）
        if (clear) {
            clearAllTables();
            evictCaches();
        }
        // 3（事务外）占位图池幂等生成：前 20 头像 + 后 20 内容图（A7/D5）
        List<String> imagePool = localImageService.ensurePlaceholderPool();
        List<String> avatarPool = imagePool.subList(0, Math.min(AVATAR_POOL_SIZE, imagePool.size()));
        List<String> contentImagePool = imagePool.subList(Math.min(AVATAR_POOL_SIZE, imagePool.size()),
                imagePool.size());
        // 4 分批生成（每批 ≤500 行独立提交，批间 log 进度）
        List<UserDO> users = generateUsers(userCount, avatarPool, startMillis);
        Map<Long, Set<Long>> friendsMap = new HashMap<>(users.size() * 2);
        int friendshipPairs = generateFriendships(users, avgFriends, friendsMap, startMillis);
        Map<Long, Map<String, Long>> userTagNameIdMap = generateTags(users, extraTags, startMillis);
        int bindingCount = generateBindings(friendsMap, userTagNameIdMap, startMillis);
        int actualPostCount = generatePosts(postCount, users, userTagNameIdMap, friendsMap,
                contentImagePool, startMillis);
        // 5 完成后（事务外）缓存全量失效（A4）
        evictCaches();
        // 6 统计出参
        MockDataGenerateOut out = new MockDataGenerateOut();
        out.setUserCount(users.size());
        out.setFriendshipPairs(friendshipPairs);
        out.setFriendshipRecords(friendshipPairs * 2);
        out.setTagCount(users.size() * (MockDataConsts.DEFAULT_TAG_NAMES.size() + extraTags));
        out.setBindingCount(bindingCount);
        out.setPostCount(actualPostCount);
        out.setImagePoolSize(imagePool.size());
        out.setElapsedMs(System.currentTimeMillis() - startMillis);
        log.info("mockData generate done, userCount={}, friendshipPairs={}, tagCount={}, bindingCount={}, "
                        + "postCount={}, imagePoolSize={}, elapsedMs={}",
                out.getUserCount(), out.getFriendshipPairs(), out.getTagCount(), out.getBindingCount(),
                out.getPostCount(), out.getImagePoolSize(), out.getElapsedMs());
        return out;
    }

    /**
     * clear=true 软删 8 表业务数据：子表先于主表（习惯序，表间无外键约束）；
     * 每表一条带 WHERE 的 UPDATE 单 DML 自动提交 = 逐表独立小事务（建议7/R11，实验室万级数据秒级）。
     */
    private void clearAllTables() {
        int visibilityTagRows = postVisibilityTagMapper.logicDeleteAll();
        int visibilityUserRows = postVisibilityUserMapper.logicDeleteAll();
        int imageRows = postImageMapper.logicDeleteAll();
        int postRows = postMapper.logicDeleteAll();
        int relationRows = friendTagRelationMapper.logicDeleteAll();
        int tagRows = friendTagMapper.logicDeleteAll();
        int friendshipRows = friendshipMapper.logicDeleteAll();
        int userRows = userMapper.logicDeleteAll();
        log.info("mockData clear done, rows: postVisibilityTag={}, postVisibilityUser={}, postImage={}, "
                        + "post={}, friendTagRelation={}, friendTag={}, friendship={}, user={}",
                visibilityTagRows, visibilityUserRows, imageRows, postRows,
                relationRows, tagRows, friendshipRows, userRows);
    }

    /**
     * 双缓存全量失效（A4：INCR fver + INCR ftagver，事务外）。
     * Redis 异常降级（C12 弱依赖）：warn 后继续，旧 key 至多存活 TTL 1h 自然过期。
     */
    private void evictCaches() {
        try {
            friendIdsCacheManager.evictAll();
            friendTagCacheManager.evictAll();
        } catch (Exception e) {
            log.warn("mockData evict caches fail, stale keys expire by TTL at most", e);
        }
    }

    /**
     * 生成用户：昵称=姓氏池+名字池随机（允许重名，D6）、性别/城市随机、头像=占位头像池循环（A7）；
     * 分批插入（≤500/批，useGeneratedKeys 回填自增 userId 供后续好友/标签/帖子生成引用）。
     */
    private List<UserDO> generateUsers(int userCount, List<String> avatarPool, long startMillis) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        List<UserDO> users = new ArrayList<>(userCount);
        for (int i = 0; i < userCount; i++) {
            UserDO user = new UserDO();
            user.setNickname(MockDataConsts.SURNAME_POOL.get(random.nextInt(MockDataConsts.SURNAME_POOL.size()))
                    + MockDataConsts.GIVEN_NAME_POOL.get(random.nextInt(MockDataConsts.GIVEN_NAME_POOL.size())));
            user.setAvatar(avatarPool.get(i % avatarPool.size()));
            user.setGender(GENDERS[random.nextInt(GENDERS.length)].getCode());
            user.setCity(MockDataConsts.CITY_POOL.get(random.nextInt(MockDataConsts.CITY_POOL.size())));
            users.add(user);
        }
        int batchNo = 0;
        for (int from = 0; from < users.size(); from += BATCH_SIZE) {
            List<UserDO> batch = users.subList(from, Math.min(from + BATCH_SIZE, users.size()));
            userMapper.batchInsert(batch);
            batchNo++;
            log.info("mockData users batch {} done, total={}, elapsedMs={}",
                    batchNo, Math.min(from + BATCH_SIZE, users.size()), System.currentTimeMillis() - startMillis);
        }
        return users;
    }

    /**
     * 生成好友关系（对称双记录，C11/R5）：按 api.md 口径"对数≈userCount×人均/2"——
     * 每用户以"当前度数补齐到目标值"方式选目标（已被人选中的好友关系直接计入双方度数，
     * 不重复建对），全局 friendsMap 天然按 (min,max) 语义去重防自环防重（§5.6 规则6）；
     * 对称双记录 A→B + B→A 同批写入（成对不跨批，R5）。
     *
     * @param friendsMap 出参载体：userId → 好友集合（双向对称，帖子可见性好友抽样复用）
     * @return 好友关系对数（A-B 计 1 对）
     */
    private int generateFriendships(List<UserDO> users, int avgFriends, Map<Long, Set<Long>> friendsMap,
            long startMillis) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        List<FriendshipDO> buffer = new ArrayList<>(BATCH_SIZE);
        int pairCount = 0;
        int batchNo = 0;
        for (UserDO user : users) {
            // 目标度数 = avg±3 抖动后 clamp（不超其他用户总数；<0 归 0）
            int target = clamp(avgFriends + random.nextInt(FRIEND_JITTER * 2 + 1) - FRIEND_JITTER,
                    0, users.size() - 1);
            Set<Long> myFriends = friendsMap.computeIfAbsent(user.getId(), k -> new HashSet<>());
            // 随机选目标的有界尝试（候选耗尽/密集重试场景下提前收敛，保证终止）
            int attempts = 0;
            int maxAttempts = target * 5 + 50;
            while (myFriends.size() < target && attempts < maxAttempts) {
                attempts++;
                UserDO candidate = users.get(random.nextInt(users.size()));
                if (candidate.getId().equals(user.getId()) || myFriends.contains(candidate.getId())) {
                    continue; // 防自环 / 已是好友（对称关系已存在，不重复建对）
                }
                Set<Long> candidateFriends = friendsMap.computeIfAbsent(candidate.getId(), k -> new HashSet<>());
                // 对称双记录成对入批：批容量不足 2 行时先整批提交，保证对内两条同事务（R5）
                if (buffer.size() + 2 > BATCH_SIZE) {
                    friendshipMapper.batchInsert(buffer);
                    buffer.clear();
                    batchNo++;
                    log.info("mockData friendship batch {} done, pairsTotal={}, elapsedMs={}",
                            batchNo, pairCount, System.currentTimeMillis() - startMillis);
                }
                myFriends.add(candidate.getId());
                candidateFriends.add(user.getId());
                buffer.add(ofFriendship(user.getId(), candidate.getId()));
                buffer.add(ofFriendship(candidate.getId(), user.getId()));
                pairCount++;
            }
        }
        if (!buffer.isEmpty()) {
            friendshipMapper.batchInsert(buffer);
            batchNo++;
            log.info("mockData friendship batch {} done, pairsTotal={}, elapsedMs={}",
                    batchNo, pairCount, System.currentTimeMillis() - startMillis);
        }
        return pairCount;
    }

    /**
     * 生成标签：每人默认 8 标签（D2）+ extraTags 个自定义（"自定义标签N"，用户内唯一）。
     * 分批插入（≤500 行/语句）后按 ownerIds（≤500/批）回读 tagId——friend_tag 的 batchInsert
     * 不回填自增 id，绑定与帖子可见性抽样依赖 (userId → tagName → tagId) 映射。
     *
     * @return userId → (tagName → tagId) 映射（仅本次生成的有效标签）
     */
    private Map<Long, Map<String, Long>> generateTags(List<UserDO> users, int extraTags, long startMillis) {
        Map<Long, Map<String, Long>> userTagNameIdMap = new HashMap<>(users.size() * 2);
        List<FriendTagDO> buffer = new ArrayList<>(BATCH_SIZE);
        List<Long> ownerBuffer = new ArrayList<>(OWNER_QUERY_BATCH);
        int batchNo = 0;
        int userTotal = 0;
        int tagsPerUser = MockDataConsts.DEFAULT_TAG_NAMES.size() + extraTags;
        for (UserDO user : users) {
            // 先判容量再入批：单条批量语句严格 ≤500 行（用户级整体入批，不拆单人标签）
            if (buffer.size() + tagsPerUser > BATCH_SIZE) {
                friendTagMapper.batchInsert(buffer);
                buffer.clear();
                batchNo++;
                log.info("mockData tags batch {} done, usersTotal={}, elapsedMs={}",
                        batchNo, userTotal, System.currentTimeMillis() - startMillis);
            }
            for (String tagName : MockDataConsts.DEFAULT_TAG_NAMES) {
                buffer.add(ofTag(user.getId(), tagName));
            }
            for (int i = 1; i <= extraTags; i++) {
                buffer.add(ofTag(user.getId(), "自定义标签" + i));
            }
            ownerBuffer.add(user.getId());
            userTotal++;
            if (ownerBuffer.size() >= OWNER_QUERY_BATCH) {
                readBackTagIds(ownerBuffer, userTagNameIdMap);
            }
        }
        if (!buffer.isEmpty()) {
            friendTagMapper.batchInsert(buffer);
            batchNo++;
            log.info("mockData tags batch {} done, usersTotal={}, elapsedMs={}",
                    batchNo, userTotal, System.currentTimeMillis() - startMillis);
        }
        readBackTagIds(ownerBuffer, userTagNameIdMap);
        return userTagNameIdMap;
    }

    /**
     * 按 ownerIds 批量回读标签，填充 userId → (tagName → tagId) 映射（追加模式下 userId 为全新
     * 自增段，天然不与历史软删数据串台）。
     */
    private void readBackTagIds(List<Long> ownerIds, Map<Long, Map<String, Long>> userTagNameIdMap) {
        if (ownerIds.isEmpty()) {
            return;
        }
        List<FriendTagDO> tags = friendTagMapper.selectByOwnerIds(ownerIds);
        for (FriendTagDO tag : tags) {
            userTagNameIdMap.computeIfAbsent(tag.getUserId(), k -> new HashMap<>(16))
                    .put(tag.getTagName(), tag.getId());
        }
        ownerIds.clear();
    }

    /**
     * 生成标签绑定：每个 (owner,friend) 有向对按 TAG_WEIGHTS 加权抽 1 主标签（A9 分布），
     * 30% 概率再加抽 1 个不重复次标签（主/次标签名互斥，构造侧保证 (tagId,friendUserId) 不重）；
     * 好友关系对称 → 双向各打一次标签（A 给 B 打、B 给 A 打，各自用自己名下的标签）。
     *
     * @return 绑定记录总数
     */
    private int generateBindings(Map<Long, Set<Long>> friendsMap, Map<Long, Map<String, Long>> userTagNameIdMap,
            long startMillis) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        List<FriendTagRelationDO> buffer = new ArrayList<>(BATCH_SIZE);
        int bindingCount = 0;
        int batchNo = 0;
        for (Map.Entry<Long, Set<Long>> entry : friendsMap.entrySet()) {
            Long owner = entry.getKey();
            Map<String, Long> nameIdMap = userTagNameIdMap.get(owner);
            if (nameIdMap == null || nameIdMap.isEmpty()) {
                continue; // 理论不可达（每人必有默认 8 标签），防御跳过
            }
            for (Long friend : entry.getValue()) {
                // 先判容量再入批（主+最多 1 次 = 2 行）：单条批量语句严格 ≤500 行
                if (buffer.size() + 2 > BATCH_SIZE) {
                    friendTagRelationMapper.batchInsert(buffer);
                    buffer.clear();
                    batchNo++;
                    log.info("mockData bindings batch {} done, bindingsTotal={}, elapsedMs={}",
                            batchNo, bindingCount, System.currentTimeMillis() - startMillis);
                }
                String primaryName = weightedPickTagName(null);
                buffer.add(ofRelation(nameIdMap.get(primaryName), friend));
                bindingCount++;
                if (random.nextDouble() < SECOND_TAG_PROBABILITY) {
                    String secondaryName = weightedPickTagName(primaryName);
                    buffer.add(ofRelation(nameIdMap.get(secondaryName), friend));
                    bindingCount++;
                }
            }
        }
        if (!buffer.isEmpty()) {
            friendTagRelationMapper.batchInsert(buffer);
            batchNo++;
            log.info("mockData bindings batch {} done, bindingsTotal={}, elapsedMs={}",
                    batchNo, bindingCount, System.currentTimeMillis() - startMillis);
        }
        return bindingCount;
    }

    /**
     * 生成帖子（postCount>0）：随机作者 1-2 帖凑数；type 分布 1:40%/2:20%/3:20%/4:20%；
     * 图 1-9 张取内容图池（无放回）；type=3/4 抽作者自己的标签/好友写可见性两表（无放回，R2-建议5）。
     * 每批 {@value #POST_BATCH_SIZE} 帖经 self 代理走 4 表同事务提交（R11 批间提交）。
     *
     * @return 实际生成帖子数
     */
    private int generatePosts(int postCount, List<UserDO> users, Map<Long, Map<String, Long>> userTagNameIdMap,
            Map<Long, Set<Long>> friendsMap, List<String> contentImagePool, long startMillis) {
        if (postCount <= 0) {
            return 0;
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        List<PostDO> postBuffer = new ArrayList<>(POST_BATCH_SIZE);
        List<PostSpec> specBuffer = new ArrayList<>(POST_BATCH_SIZE);
        int created = 0;
        int batchNo = 0;
        while (created < postCount) {
            Long authorId = users.get(random.nextInt(users.size())).getId();
            // 随机用户 1-2 帖凑数（不足 postCount 取余量）
            int round = Math.min(1 + random.nextInt(2), postCount - created);
            for (int i = 0; i < round; i++) {
                PostDO post = new PostDO();
                post.setUserId(authorId);
                post.setContent(randomContent());
                post.setVisibilityType(randomVisibilityType());
                post.setStatus(PostStatusEnum.NORMAL.getCode());
                postBuffer.add(post);
                specBuffer.add(buildPostSpec(post, userTagNameIdMap, friendsMap, contentImagePool));
                created++;
                if (postBuffer.size() >= POST_BATCH_SIZE) {
                    self.writePostBatch(postBuffer, specBuffer);
                    postBuffer.clear();
                    specBuffer.clear();
                    batchNo++;
                    log.info("mockData posts batch {} done, postsTotal={}, elapsedMs={}",
                            batchNo, created, System.currentTimeMillis() - startMillis);
                }
            }
        }
        if (!postBuffer.isEmpty()) {
            self.writePostBatch(postBuffer, specBuffer);
            batchNo++;
            log.info("mockData posts batch {} done, postsTotal={}, elapsedMs={}",
                    batchNo, created, System.currentTimeMillis() - startMillis);
        }
        return created;
    }

    /**
     * 帖批 4 表同事务写入（R11：批间提交防长事务；必须经 self 代理调用否则事务失效）：
     * post 批量落库（useGeneratedKeys 回填 postId）→ 按回填 id 组装 post_image（sort=1..n）
     * 与可见性两表批量落库；任一步失败整批回滚（对齐 createPost 4 表事务语义）。
     * 批内单语句行数：帖 ≤50、图 ≤450、可见性 ≤150+150，均 ≤500。
     */
    @Transactional(rollbackFor = Exception.class)
    public void writePostBatch(List<PostDO> posts, List<PostSpec> specs) {
        postMapper.batchInsert(posts);
        List<PostImageDO> images = new ArrayList<>(posts.size() * MAX_IMAGE_COUNT);
        List<PostVisibilityTagDO> visibilityTags = new ArrayList<>(posts.size());
        List<PostVisibilityUserDO> visibilityUsers = new ArrayList<>(posts.size());
        for (int i = 0; i < posts.size(); i++) {
            PostDO post = posts.get(i);
            PostSpec spec = specs.get(i);
            for (int sort = 1; sort <= spec.imageUrls.size(); sort++) {
                images.add(ofPostImage(post.getId(), spec.imageUrls.get(sort - 1), sort));
            }
            for (Long tagId : spec.tagIds) {
                visibilityTags.add(ofVisibilityTag(post.getId(), tagId));
            }
            for (Long userId : spec.userIds) {
                visibilityUsers.add(ofVisibilityUser(post.getId(), userId));
            }
        }
        if (!images.isEmpty()) {
            postImageMapper.batchInsert(images);
        }
        if (!visibilityTags.isEmpty()) {
            postVisibilityTagMapper.batchInsert(visibilityTags);
        }
        if (!visibilityUsers.isEmpty()) {
            postVisibilityUserMapper.batchInsert(visibilityUsers);
        }
    }

    /**
     * 单帖生成规格组装：图 1-9 张（内容图池 shuffle+subList 无放回）；type=3/4 时抽作者自己的
     * 标签/好友（各 1-{@value #MAX_VISIBILITY_SAMPLE} 条无放回，池空则该名单为空——
     * type=3 空集=仅作者可见、type=4 空集=等同公开，C6 语义）。
     */
    private PostSpec buildPostSpec(PostDO post, Map<Long, Map<String, Long>> userTagNameIdMap,
            Map<Long, Set<Long>> friendsMap, List<String> contentImagePool) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        PostSpec spec = new PostSpec();
        int imageCount = 1 + random.nextInt(MAX_IMAGE_COUNT);
        List<String> shuffledUrls = new ArrayList<>(contentImagePool);
        Collections.shuffle(shuffledUrls, random);
        spec.imageUrls = new ArrayList<>(shuffledUrls.subList(0, imageCount));
        boolean visibilityListEnabled = VisibilityTypeEnum.PART_VISIBLE.getCode().equals(post.getVisibilityType())
                || VisibilityTypeEnum.EXCLUDE.getCode().equals(post.getVisibilityType());
        if (visibilityListEnabled) {
            // 标签抽样：作者自己的标签（默认 8 + 自定义，恒非空）
            Map<String, Long> nameIdMap = userTagNameIdMap.get(post.getUserId());
            if (nameIdMap != null && !nameIdMap.isEmpty()) {
                spec.tagIds = sampleWithoutReplacement(new ArrayList<>(nameIdMap.values()));
            }
            // 好友抽样：作者的好友（可能为空集——无好友作者 type=3/4 名单留空）
            Set<Long> friends = friendsMap.get(post.getUserId());
            if (friends != null && !friends.isEmpty()) {
                spec.userIds = sampleWithoutReplacement(new ArrayList<>(friends));
            }
        }
        return spec;
    }

    /**
     * 无放回抽样（R2-建议5）：shuffle 打乱候选后 subList 取前 n（n=1..min(cap, 池大小)），
     * 构造侧保证不重复，防撞 uniq_post_tag/uniq_post_user 唯一键致整批事务回滚。
     */
    private List<Long> sampleWithoutReplacement(List<Long> candidates) {
        if (candidates.isEmpty()) {
            return new ArrayList<>();
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        List<Long> shuffled = new ArrayList<>(candidates);
        Collections.shuffle(shuffled, random);
        int sampleCount = 1 + random.nextInt(Math.min(MAX_VISIBILITY_SAMPLE, shuffled.size()));
        return new ArrayList<>(shuffled.subList(0, sampleCount));
    }

    /**
     * TAG_WEIGHTS 加权抽签（A9）：roll ∈ [1, 非排除项权重和] 顺序累计命中；
     * excludeName 非空时排除该标签（次标签与主标签不重复），权重守恒下必命中。
     */
    private String weightedPickTagName(String excludeName) {
        int totalWeight = 0;
        for (Map.Entry<String, Integer> entry : MockDataConsts.TAG_WEIGHTS.entrySet()) {
            if (!entry.getKey().equals(excludeName)) {
                totalWeight += entry.getValue();
            }
        }
        int roll = ThreadLocalRandom.current().nextInt(totalWeight) + 1;
        int accumulated = 0;
        String fallback = null;
        for (Map.Entry<String, Integer> entry : MockDataConsts.TAG_WEIGHTS.entrySet()) {
            if (entry.getKey().equals(excludeName)) {
                continue;
            }
            fallback = entry.getKey();
            accumulated += entry.getValue();
            if (roll <= accumulated) {
                return entry.getKey();
            }
        }
        return fallback; // 权重守恒下不可达，防御兜底
    }

    /**
     * 随机可见范围：公开 40% / 私密 20% / 部分可见 20% / 不给谁看 20%（枚举取码，禁魔法数字）。
     */
    private Integer randomVisibilityType() {
        int roll = ThreadLocalRandom.current().nextInt(VISIBILITY_TOTAL_RATE);
        if (roll < PUBLIC_RATE) {
            return VisibilityTypeEnum.PUBLIC.getCode();
        }
        if (roll < PUBLIC_RATE + PRIVATE_RATE) {
            return VisibilityTypeEnum.PRIVATE.getCode();
        }
        if (roll < PUBLIC_RATE + PRIVATE_RATE + PART_VISIBLE_RATE) {
            return VisibilityTypeEnum.PART_VISIBLE.getCode();
        }
        return VisibilityTypeEnum.EXCLUDE.getCode();
    }

    /**
     * 随机帖子文案：30% 纯图帖（空串），否则文案池随机句（图恒 1-9 张，非同空合法）。
     */
    private String randomContent() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        if (random.nextDouble() < PURE_IMAGE_POST_RATE) {
            return "";
        }
        return CONTENT_POOL.get(random.nextInt(CONTENT_POOL.size()));
    }

    private FriendshipDO ofFriendship(Long userId, Long friendUserId) {
        FriendshipDO friendship = new FriendshipDO();
        friendship.setUserId(userId);
        friendship.setFriendUserId(friendUserId);
        return friendship;
    }

    private FriendTagDO ofTag(Long userId, String tagName) {
        FriendTagDO tag = new FriendTagDO();
        tag.setUserId(userId);
        tag.setTagName(tagName);
        return tag;
    }

    private FriendTagRelationDO ofRelation(Long tagId, Long friendUserId) {
        FriendTagRelationDO relation = new FriendTagRelationDO();
        relation.setTagId(tagId);
        relation.setFriendUserId(friendUserId);
        return relation;
    }

    private PostImageDO ofPostImage(Long postId, String imageUrl, int sort) {
        PostImageDO image = new PostImageDO();
        image.setPostId(postId);
        image.setImageUrl(imageUrl);
        image.setSort(sort);
        return image;
    }

    private PostVisibilityTagDO ofVisibilityTag(Long postId, Long tagId) {
        PostVisibilityTagDO visibilityTag = new PostVisibilityTagDO();
        visibilityTag.setPostId(postId);
        visibilityTag.setTagId(tagId);
        return visibilityTag;
    }

    private PostVisibilityUserDO ofVisibilityUser(Long postId, Long userId) {
        PostVisibilityUserDO visibilityUser = new PostVisibilityUserDO();
        visibilityUser.setPostId(postId);
        visibilityUser.setUserId(userId);
        return visibilityUser;
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * BizException 构造辅助：框架 BizException 仅 (code, message) 构造，收敛重复取值（口径同 UserServiceImpl）。
     */
    private BizException bizException(SocialErrorCode errorCode) {
        return new BizException(errorCode.getCode(), errorCode.getMessage());
    }

    /**
     * 单帖生成规格（包级可见：CGLIB 代理子类同包可访问 writePostBatch 签名中的该类型）：
     * imageUrls（1-9 张，顺序即 sort）；tagIds/userIds 仅 type=3/4 非空（无放回抽样，天然去重）。
     */
    static class PostSpec {

        private List<String> imageUrls = new ArrayList<>();

        private List<Long> tagIds = new ArrayList<>();

        private List<Long> userIds = new ArrayList<>();
    }
}
