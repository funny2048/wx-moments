package com.funny.moments.service.social;

import com.funny.moments.model.in.MockDataGenerateIn;
import com.funny.moments.model.out.MockDataGenerateOut;

/**
 * 模拟数据批量生成 Service（design §3.2.11/§5.5/§6.2，T090；C15/C16/A7/A8/A9/R5/R11）。
 * 复用各域 Mapper 的 batchInsert/logicDeleteAll 与占位图池，不新增独立表写入路径。
 *
 * @Author: funny2048
 * @Date: 2026-09-21
 */
public interface IMockDataService {

    /**
     * 批量生成模拟数据（api.md 接口11）：
     * <ul>
     * <li>用户：昵称=姓氏池+名字池随机、性别/城市随机、头像=占位头像池循环</li>
     * <li>好友：人均 avgFriendsPerUser±3 抖动、全局 pair 去重防自环防重、对称双记录
     *     A→B + B→A 同批写入（R5，成对不跨批保证同事务提交）</li>
     * <li>标签：每人默认 8 标签（D2）+ extraTagsPerUser 个自定义（"自定义标签N"）</li>
     * <li>绑定：每个 (owner,friend) 按权重抽 1 主标签（A9），30% 概率加抽 1 个不重复次标签</li>
     * <li>帖子（postCount&gt;0）：随机用户 1-2 帖凑数，type 分布 1:40%/2:20%/3:20%/4:20%，
     *     图 1-9 张取内容图池；type=3/4 抽作者自己的标签/好友写可见性两表
     *     （shuffle+subList 无放回抽样，防撞唯一键，R2-建议5）</li>
     * </ul>
     *
     * <p>clear=true 先逐表独立小事务软删 8 表业务数据，clear 段完成即失效缓存（建议7：
     * 中断恢复语义）；生成段按批分批提交防长事务（R11：单条批量语句 ≤500 行，帖批 4 表同事务）；
     * 文件 IO（占位图池，幂等）与缓存 INCR 全程在事务外（java-guide §5）。
     *
     * @param in 生成配置；userCount/avgFriendsPerUser/extraTagsPerUser/postCount 缺省取默认值，
     *           显式越界抛 1040（MOCK_CONFIG_INVALID，mock 专用码，B2 统一策略）
     * @return 生成统计（userCount/friendshipPairs/friendshipRecords/tagCount/bindingCount/
     *         postCount/imagePoolSize/elapsedMs）
     */
    MockDataGenerateOut generate(MockDataGenerateIn in);
}
