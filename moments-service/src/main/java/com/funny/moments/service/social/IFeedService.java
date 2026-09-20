package com.funny.moments.service.social;

import com.funny.moments.model.out.FeedOut;

/**
 * Feed 瀑布流 Service（design §3.2.16/§5.4/§6.2，T080）：Cursor 分页组装（双锚点算法，B1 定稿），
 * 只读无事务。候选帖 SQL 预过滤私密帖（R1），type=3/4 经 IVisibilityService.canView 统一过滤
 * （design §5.6 规则 1：单实现复用，禁止散落重复判断）。
 *
 * @Author: funny2048
 * @Date: 2026-09-21
 */
public interface IFeedService {

    /**
     * 查询当前用户的时间线（自己 + 好友的帖子，created_stime DESC + id DESC，C8）。
     *
     * <p>放大补偿扫描（design §5.4）：批大小 pageSize×3、最多 3 批（有界 9×pageSize 候选），
     * 扫描进度锚点逐批前移（无论本批有无可见帖）；输出按四分支判定：
     * <ul>
     * <li>collected &gt; pageSize（截断发生）→ items=前 pageSize 条、hasMore=true、
     *     nextCursor=items 末条锚点（页末锚点；被截断可见帖下页重扫，canView 幂等不重复输出）</li>
     * <li>collected ≤ pageSize 且候选流扫尽 → items=collected、hasMore=false、nextCursor=null
     *     （真无更多；含无帖/无好友空集）</li>
     * <li>collected ≤ pageSize 且候选流未扫尽 → items=collected（可为空集）、hasMore=true、
     *     nextCursor=扫描进度锚点（items=[] && hasMore=true && nextCursor≠null 为合法组合，
     *     翻页链延续、已扫过的不可见候选不重扫）</li>
     * </ul>
     *
     * @param viewerId 当前视角用户 ID（调用方经 CurrentUserResolver.requireUser 保证非空）
     * @param cursor   分页游标（首页传 null/空白；Base64("{createdStime毫秒}:{postId}")，
     *                 解码失败/格式不匹配/数值段超 Long 范围抛 1030，不静默重置首页）
     * @param pageSize 每页条数（缺省 20；显式传入 <1 或 >50 抛 1001，B2 统一策略）
     * @return Feed 出参（items/nextCursor/hasMore，List 字段无值返回空数组禁 null）
     */
    FeedOut getFeed(Long viewerId, String cursor, Integer pageSize);
}
