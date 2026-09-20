package com.funny.moments.service.social;

import java.util.List;

import com.funny.moments.model.in.PostCreateIn;
import com.funny.moments.model.out.PostDetailOut;

/**
 * 朋友圈帖子 Service（design §3.2.12-§3.2.15/§6.2，T060 createPost + T070 帖子读写追加）。
 *
 * @Author: funny2048
 * @Date: 2026-09-21
 */
public interface IPostService {

    /**
     * 发布朋友圈（api.md 接口12 / design §3.2.12、§5.1）：post/post_image/post_visibility_user/
     * post_visibility_tag 4 表同一事务（任一步失败整体回滚，无残留）。
     *
     * <p>校验（任一失败抛 BizException，事务回滚）：
     * visibilityType 必填且 ∈{1,2,3,4}（1001）；content trim 后 ≤2000 字符（1001）；
     * imageUrls ≤9 张（超限 1022）且每项匹配 ^/images/[A-Za-z0-9._-]+$（非法 1001）；
     * content 与 imageUrls 不得同空（1001）；type=3/4 时 visibilityTagIds 逐个校验存在（1004）
     * 与归属当前用户（1006）、visibilityUserIds 须全部存在（1002）；type=1/2 时可见性两列表
     * 静默忽略（不校验、不落库，建议 10 定稿）。
     *
     * @param viewerId 当前视角用户（作者）
     * @param in      发帖入参
     * @return 完整帖子出参（含作者信息/visibilityTypeStr/createTimeStr）
     */
    PostDetailOut createPost(Long viewerId, PostCreateIn in);

    /**
     * 帖子详情（api.md 接口13 / design §3.2.13，T070）：经 canView 统一权限校验。
     *
     * <p>异常：postId null/≤0 抛 1001（非数字由绑定层承载转 1001）；帖子不存在（含已软删）抛 1010；
     * canView 不过抛 1011（如私密帖非作者、部分可见未命中，语义走查 Q6）。
     *
     * @param viewerId 当前视角用户
     * @param postId   帖子 ID
     * @return 帖子出参（图片按 sort 升序 + 作者信息 + visibilityTypeStr/createTimeStr）
     */
    PostDetailOut getPost(Long viewerId, Long postId);

    /**
     * 软删帖子（api.md 接口14 / design §3.2.14、§5.3，T070）：is_del=1，status 保留业务态快照（C7）；
     * 图片文件保留（D7）。
     *
     * <p>异常：postId null/≤0 抛 1001；帖子不存在（含已删，第二次删同样 1010——删帖非幂等，
     * design §5.6 规则 5b）抛 1010；当前用户非作者抛 1012（越权防护）。
     *
     * @param viewerId 当前视角用户
     * @param postId   帖子 ID
     */
    void deletePost(Long viewerId, Long postId);

    /**
     * 指定用户帖子列表（api.md 接口15 / design §3.2.15，C14）：当前用户视角 canView 过滤，
     * 不可见帖静默剔除（不抛 1011）；createTime DESC 排序。
     *
     * <p>limit 为结果条数上限（非扫描上限，建议 5 定稿）：服务端按 limit×3 批大小最多 3 批
     * 放大补偿扫描并过滤（有界 9×limit，R2-建议6），避免"先截断后过滤"漏掉截断点之后的旧公开帖。
     *
     * <p>异常：targetUserId null/≤0 抛 1001；limit 缺省 100、显式 &lt;1 或 &gt;500 抛 1001（B2）；
     * 目标用户不存在抛 1002。
     *
     * @param viewerId    当前视角用户
     * @param targetUserId 目标作者用户 ID（被查看者，与 viewerId 语义区分）
     * @param limit       结果条数上限（缺省 100，范围 1-500）
     * @return 可见帖子列表（无可见帖子返回空列表，禁 null）
     */
    List<PostDetailOut> listUserPosts(Long viewerId, Long targetUserId, Integer limit);
}
