package com.funny.moments.service.social.impl;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.funny.framework.core.exception.BizException;
import com.funny.moments.common.consts.PatternConsts;
import com.funny.moments.common.enums.PostStatusEnum;
import com.funny.moments.common.enums.SocialErrorCode;
import com.funny.moments.common.enums.VisibilityTypeEnum;
import com.funny.moments.common.utils.DateUtils;
import com.funny.moments.dao.entity.FriendTagDO;
import com.funny.moments.dao.entity.PostDO;
import com.funny.moments.dao.entity.PostImageDO;
import com.funny.moments.dao.entity.PostVisibilityTagDO;
import com.funny.moments.dao.entity.PostVisibilityUserDO;
import com.funny.moments.dao.entity.UserDO;
import com.funny.moments.dao.mapper.FriendTagMapper;
import com.funny.moments.dao.mapper.PostImageMapper;
import com.funny.moments.dao.mapper.PostMapper;
import com.funny.moments.dao.mapper.PostVisibilityTagMapper;
import com.funny.moments.dao.mapper.PostVisibilityUserMapper;
import com.funny.moments.dao.mapper.UserMapper;
import com.funny.moments.model.in.PostCreateIn;
import com.funny.moments.model.out.PostDetailOut;
import com.funny.moments.service.social.IPostService;
import com.funny.moments.service.social.IVisibilityService;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

/**
 * 朋友圈帖子实现（design §3.2.12-§3.2.15/§5.1/§6.2，T060 createPost + T070 帖子读写追加）：
 * createPost 为 4 表同事务写（@Transactional(rollbackFor)，事务内无 Redis/文件 IO，java-guide §5）；
 * getPost/deletePost/listUserPosts 为只读/单表软删（deletePost 单 DML 自动提交原子，无事务注解），
 * 读路径经 IVisibilityService.canView 统一权限判断（design §5.6 规则 1，单实现复用）。
 *
 * <p>校验顺序（api.md 接口12）：入参格式（1001/1022）→ 标签归属（1004/1006）→ 用户存在（1002），
 * 全部通过后才开写；type=1/2 携带的可见性列表静默忽略（不校验、不落库，建议 10 定稿）。
 *
 * @Author: funny2048
 * @Date: 2026-09-21
 */
@Slf4j
@Service
public class PostServiceImpl implements IPostService {

    /** 文字内容长度上限（api.md 接口12：trim 后 ≤2000 字符） */
    private static final int MAX_CONTENT_LENGTH = 2000;

    /** 单帖图片数上限（api.md 接口12：≤9 张，超限 1022） */
    private static final int MAX_IMAGE_COUNT = 9;

    /** 图片 URL 前缀（本地静态资源映射，design §3.2.17） */
    private static final String IMAGE_URL_PREFIX = "/images/";

    /** listUserPosts 结果条数上限缺省值（api.md 接口15：limit 缺省 100，B2 缺省才取默认） */
    private static final int DEFAULT_LIST_LIMIT = 100;

    /** listUserPosts 结果条数上限（api.md 接口15：显式越界 >500 抛 1001，B2） */
    private static final int MAX_LIST_LIMIT = 500;

    /** 放大补偿批大小倍数（建议5 定稿：批大小 = limit×3，缓解先截断后过滤漏旧公开帖） */
    private static final int SCAN_BATCH_FACTOR = 3;

    /** 放大补偿最大扫描批数（建议5 定稿：最多 3 批，有界性 9×limit 候选，R2-建议6） */
    private static final int MAX_SCAN_ROUNDS = 3;

    @Autowired
    private PostMapper postMapper;

    @Autowired
    private PostImageMapper postImageMapper;

    @Autowired
    private PostVisibilityUserMapper postVisibilityUserMapper;

    @Autowired
    private PostVisibilityTagMapper postVisibilityTagMapper;

    @Autowired
    private FriendTagMapper friendTagMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private IVisibilityService visibilityService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PostDetailOut createPost(Long viewerId, PostCreateIn in) {
        // 入参体防御：JSON body 缺失/类型非法由 T100 HttpMessageNotReadableException 承载转 1001，此处拦直调 null
        if (Objects.isNull(in)) {
            throw bizException(SocialErrorCode.PARAM_INVALID);
        }
        // 1 visibilityType 必填且 ∈{1,2,3,4}（byCode 反查，禁魔法数字）
        VisibilityTypeEnum visibilityType = VisibilityTypeEnum.byCode(in.getVisibilityType());
        if (Objects.isNull(visibilityType)) {
            throw bizException(SocialErrorCode.PARAM_INVALID);
        }
        // 2 content trim 后 ≤2000 字符（1001）
        String content = StringUtils.isBlank(in.getContent()) ? "" : in.getContent().trim();
        if (content.length() > MAX_CONTENT_LENGTH) {
            throw bizException(SocialErrorCode.PARAM_INVALID);
        }
        // 3 imageUrls ≤9 张（超限 1022）；每项须为 /images/{白名单文件名}（^/images/[A-Za-z0-9._-]+$，非法 1001）
        List<String> imageUrls = CollectionUtils.isEmpty(in.getImageUrls()) ? new ArrayList<>() : in.getImageUrls();
        if (imageUrls.size() > MAX_IMAGE_COUNT) {
            throw bizException(SocialErrorCode.IMAGE_COUNT_EXCEEDED);
        }
        for (String imageUrl : imageUrls) {
            if (StringUtils.isBlank(imageUrl) || !imageUrl.startsWith(IMAGE_URL_PREFIX)
                    || !PatternConsts.IMAGE_FILE_NAME.matcher(imageUrl.substring(IMAGE_URL_PREFIX.length())).matches()) {
                throw bizException(SocialErrorCode.PARAM_INVALID);
            }
        }
        // 4 纯空帖拒绝：content 与 imageUrls 不得同空（1001）
        if (content.isEmpty() && imageUrls.isEmpty()) {
            throw bizException(SocialErrorCode.PARAM_INVALID);
        }
        // 5 可见性名单：type=1/2 静默忽略（不校验、不落库）；type=3/4 校验归属与存在
        boolean visibilityListEnabled = VisibilityTypeEnum.PART_VISIBLE == visibilityType
                || VisibilityTypeEnum.EXCLUDE == visibilityType;
        List<Long> tagIds = new ArrayList<>();
        List<Long> userIds = new ArrayList<>();
        if (visibilityListEnabled) {
            if (CollectionUtils.isNotEmpty(in.getVisibilityTagIds())) {
                // 先去重（Stream.distinct）再逐个校验：写入侧防 uniq_post_tag 唯一键冲突整批回滚
                tagIds = in.getVisibilityTagIds().stream().distinct().collect(Collectors.toList());
                for (Long tagId : tagIds) {
                    // 复用 T020/T030 既有归属校验口径：null=1004（不存在/已删），非归属人=1006
                    FriendTagDO tag = friendTagMapper.selectByTagId(tagId);
                    if (Objects.isNull(tag)) {
                        throw bizException(SocialErrorCode.TAG_NOT_FOUND);
                    }
                    if (!Objects.equals(tag.getUserId(), viewerId)) {
                        throw bizException(SocialErrorCode.NOT_TAG_OWNER);
                    }
                }
            }
            if (CollectionUtils.isNotEmpty(in.getVisibilityUserIds())) {
                userIds = in.getVisibilityUserIds().stream().distinct().collect(Collectors.toList());
                List<UserDO> users = userMapper.selectByUserIds(userIds);
                Set<Long> foundIds = users.stream().map(UserDO::getId).collect(Collectors.toSet());
                // 全命中校验：任一不存在（含已软删）抛 1002
                if (!foundIds.containsAll(userIds)) {
                    throw bizException(SocialErrorCode.USER_NOT_FOUND);
                }
            }
        }
        // 6 主帖落库（status 固定 NORMAL，C7；BaseMapper.insert 经 IdType.AUTO 回填 postId 供三张子表外键）
        PostDO post = new PostDO();
        post.setUserId(viewerId);
        post.setContent(content);
        post.setVisibilityType(visibilityType.getCode());
        post.setStatus(PostStatusEnum.NORMAL.getCode());
        postMapper.insert(post);
        // 7 图片：sort=1..n 按入参 imageUrls 顺序（顺序即展示排序）
        if (!imageUrls.isEmpty()) {
            List<PostImageDO> images = new ArrayList<>(imageUrls.size());
            for (int i = 0; i < imageUrls.size(); i++) {
                PostImageDO image = new PostImageDO();
                image.setPostId(post.getId());
                image.setImageUrl(imageUrls.get(i));
                image.setSort(i + 1);
                images.add(image);
            }
            postImageMapper.batchInsert(images);
        }
        // 8 可见性名单（仅 type=3/4 且名单非空；已去重，防唯一键冲突）
        if (visibilityListEnabled && !tagIds.isEmpty()) {
            List<PostVisibilityTagDO> visibilityTags = tagIds.stream().map(tagId -> {
                PostVisibilityTagDO visibilityTag = new PostVisibilityTagDO();
                visibilityTag.setPostId(post.getId());
                visibilityTag.setTagId(tagId);
                return visibilityTag;
            }).collect(Collectors.toList());
            postVisibilityTagMapper.batchInsert(visibilityTags);
        }
        if (visibilityListEnabled && !userIds.isEmpty()) {
            List<PostVisibilityUserDO> visibilityUsers = userIds.stream().map(userId -> {
                PostVisibilityUserDO visibilityUser = new PostVisibilityUserDO();
                visibilityUser.setPostId(post.getId());
                visibilityUser.setUserId(userId);
                return visibilityUser;
            }).collect(Collectors.toList());
            postVisibilityUserMapper.batchInsert(visibilityUsers);
        }
        log.info("createPost done, viewerId={}, postId={}, visibilityType={}, imageCount={}, tagCount={}, userCount={}",
                viewerId, post.getId(), visibilityType.getCode(), imageUrls.size(), tagIds.size(), userIds.size());
        // 9 回读 DB 默认值（created_stime）组装出参，保证响应时间与库内一致（口径同 createTag）
        return toDetailOut(postMapper.selectById(post.getId()), imageUrls);
    }

    @Override
    public PostDetailOut getPost(Long viewerId, Long postId) {
        // postId null/≤0 → 1001（非数字由绑定层 MethodArgumentTypeMismatchException 承载转 1001，B2）
        if (Objects.isNull(postId) || postId <= 0) {
            throw bizException(SocialErrorCode.PARAM_INVALID);
        }
        // 帖子存在性：selectById 经 @TableLogic 过滤已软删 → null 即 1010（含已删帖）
        PostDO post = postMapper.selectById(postId);
        if (Objects.isNull(post)) {
            throw bizException(SocialErrorCode.POST_NOT_FOUND);
        }
        // 统一可见性判断（design §5.6 规则1：canView 单实现复用，详情路径不过 → 1011，语义走查 Q6）
        if (!visibilityService.canView(viewerId, postId)) {
            throw bizException(SocialErrorCode.POST_NO_PERMISSION);
        }
        // 组装：图片按 sort 升序 + 作者信息 + xxTypeStr/xxTimeStr（复用发帖组装口径）
        List<String> imageUrls = postImageMapper.selectByPostId(postId).stream()
                .map(PostImageDO::getImageUrl)
                .collect(Collectors.toList());
        return toDetailOut(post, imageUrls);
    }

    @Override
    public void deletePost(Long viewerId, Long postId) {
        // 参数校验同 getPost（1001）
        if (Objects.isNull(postId) || postId <= 0) {
            throw bizException(SocialErrorCode.PARAM_INVALID);
        }
        // 已删帖二次删除同样 1010：删帖非幂等（design §5.6 规则5b，前端按钮 loading 防双击）
        PostDO post = postMapper.selectById(postId);
        if (Objects.isNull(post)) {
            throw bizException(SocialErrorCode.POST_NOT_FOUND);
        }
        // 仅作者可删（design §5.6 规则3，水平越权防护 java-guide §9）
        if (!Objects.equals(post.getUserId(), viewerId)) {
            throw bizException(SocialErrorCode.NOT_POST_AUTHOR);
        }
        // 软删（is_del=1，status 保留业务态快照 C7）；单表写无事务注解（单 DML 原子）；图片文件保留（D7）
        postMapper.updateIsDel(postId);
        log.info("deletePost done, viewerId={}, postId={}", viewerId, postId);
    }

    @Override
    public List<PostDetailOut> listUserPosts(Long viewerId, Long targetUserId, Integer limit) {
        // 1 参数校验（B2 统一策略）：targetUserId 必填 >0；limit 缺省 100，显式 <1 或 >500 抛 1001
        if (Objects.isNull(targetUserId) || targetUserId <= 0) {
            throw bizException(SocialErrorCode.PARAM_INVALID);
        }
        int resultLimit = DEFAULT_LIST_LIMIT;
        if (Objects.nonNull(limit)) {
            if (limit < 1 || limit > MAX_LIST_LIMIT) {
                throw bizException(SocialErrorCode.PARAM_INVALID);
            }
            resultLimit = limit;
        }
        // 2 目标用户存在校验（1002）：selectById 经 @TableLogic 过滤已删
        UserDO targetUser = userMapper.selectById(targetUserId);
        if (Objects.isNull(targetUser)) {
            throw bizException(SocialErrorCode.USER_NOT_FOUND);
        }
        // 3 放大补偿扫描（建议5 定稿）：limit 为结果上限；批大小 limit×3、最多 3 批（有界 9×limit 候选，R2-建议6），
        //   复用 selectUserPosts 双字段游标（C8 口径）逐批前移扫描锚点——避免"先截断后过滤"漏掉截断点之后的旧公开帖
        int batchSize = resultLimit * SCAN_BATCH_FACTOR;
        Date scanTime = null;
        Long scanId = null;
        List<PostDO> collected = new ArrayList<>();
        for (int round = 0; round < MAX_SCAN_ROUNDS && collected.size() < resultLimit; round++) {
            List<PostDO> batch = postMapper.selectUserPosts(targetUserId, scanTime, scanId, batchSize);
            if (CollectionUtils.isEmpty(batch)) {
                break;  // 候选流扫尽
            }
            // 扫描锚点前移到本批末条（无论本批有无可见帖，与 Feed §5.4 同口径）
            PostDO last = batch.get(batch.size() - 1);
            scanTime = last.getCreatedStime();
            scanId = last.getId();
            // canView 过滤：不可见帖静默剔除（接口15 无 1011，仅详情直达路径触发）
            for (PostDO post : batch) {
                if (collected.size() >= resultLimit) {
                    break;
                }
                if (visibilityService.canView(viewerId, post.getId())) {
                    collected.add(post);
                }
            }
            if (batch.size() < batchSize) {
                break;  // 末批不满=候选流到尾
            }
        }
        // 4 批量组装（防 N+1）：单目标作者一次查询（步骤 2 已取）；图片 selectByPostIds 按 postId 分组
        return toDetailOutList(collected, targetUser, loadImageUrlsByPostId(collected));
    }

    /**
     * 帖子出参组装：作者信息单独组装（nickname/avatar）；visibilityTypeStr 由枚举反查；
     * imageUrls 按 sort 升序（发帖链路即入参顺序）；createTimeStr 格式 yyyy-MM-dd HH:mm:ss。
     */
    private PostDetailOut toDetailOut(PostDO post, List<String> imageUrls) {
        PostDetailOut out = new PostDetailOut();
        out.setPostId(post.getId());
        out.setUserId(post.getUserId());
        UserDO author = userMapper.selectById(post.getUserId());
        if (Objects.nonNull(author)) {
            out.setNickname(author.getNickname());
            out.setAvatar(author.getAvatar());
        }
        out.setContent(post.getContent());
        out.getImageUrls().addAll(imageUrls);
        out.setVisibilityType(post.getVisibilityType());
        VisibilityTypeEnum typeEnum = VisibilityTypeEnum.byCode(post.getVisibilityType());
        out.setVisibilityTypeStr(typeEnum == null ? null : typeEnum.getDesc());
        out.setStatus(post.getStatus());
        out.setCreateTimeStr(DateUtils.formatDefault(post.getCreatedStime()));
        return out;
    }

    /**
     * 批量加载帖子图片并按 postId 分组（T070）：selectByPostIds 已按 (post_id, sort, id) 排序，
     * groupingBy 组内保持遇见顺序即展示顺序；空入参/无图返回空 Map（组缺省空列表兜底）。
     */
    private Map<Long, List<String>> loadImageUrlsByPostId(List<PostDO> posts) {
        if (CollectionUtils.isEmpty(posts)) {
            return new HashMap<>();
        }
        List<Long> postIds = posts.stream().map(PostDO::getId).collect(Collectors.toList());
        return postImageMapper.selectByPostIds(postIds).stream()
                .collect(Collectors.groupingBy(PostImageDO::getPostId,
                        Collectors.mapping(PostImageDO::getImageUrl, Collectors.toList())));
    }

    /**
     * 列表出参组装（T070）：与单帖 toDetailOut 同构，但作者信息与图片由调用方批量预取
     * （防 N+1，design §5.4 组装口径），组内不再回查 DB。
     */
    private List<PostDetailOut> toDetailOutList(List<PostDO> posts, UserDO author,
            Map<Long, List<String>> imageUrlsByPostId) {
        List<PostDetailOut> outs = new ArrayList<>(posts.size());
        for (PostDO post : posts) {
            PostDetailOut out = new PostDetailOut();
            out.setPostId(post.getId());
            out.setUserId(post.getUserId());
            if (Objects.nonNull(author)) {
                out.setNickname(author.getNickname());
                out.setAvatar(author.getAvatar());
            }
            out.setContent(post.getContent());
            out.getImageUrls().addAll(imageUrlsByPostId.getOrDefault(post.getId(), new ArrayList<>()));
            out.setVisibilityType(post.getVisibilityType());
            VisibilityTypeEnum typeEnum = VisibilityTypeEnum.byCode(post.getVisibilityType());
            out.setVisibilityTypeStr(typeEnum == null ? null : typeEnum.getDesc());
            out.setStatus(post.getStatus());
            out.setCreateTimeStr(DateUtils.formatDefault(post.getCreatedStime()));
            outs.add(out);
        }
        return outs;
    }

    /**
     * BizException 构造辅助：框架 BizException 仅 (code, message) 构造，收敛重复取值（口径同 UserServiceImpl）。
     */
    private BizException bizException(SocialErrorCode errorCode) {
        return new BizException(errorCode.getCode(), errorCode.getMessage());
    }
}
