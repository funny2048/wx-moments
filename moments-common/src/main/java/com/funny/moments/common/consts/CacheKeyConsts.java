package com.funny.moments.common.consts;



/**
 * @Author: zwf
 * @Date: 2021-06-25 18:32
 * @author funny2048
 */
public class CacheKeyConsts {

    public static final String CACHE_CAMPAIGN = "campaign:%s";

    /** 销量报表缓存 key 模板，参数顺序：statMonth / startMonth / endMonth / regionCode / seriesCode，空值用 ALL 占位 */
    public static final String CACHE_SALES_REPORT_LIST       = "sales_report:list:%s:%s:%s:%s:%s";
    public static final String CACHE_SALES_REPORT_TREND      = "sales_report:trend:%s:%s:%s:%s:%s";
    public static final String CACHE_SALES_REPORT_REGION_BAR = "sales_report:region_bar:%s:%s:%s:%s:%s";
    public static final String CACHE_SALES_REPORT_SERIES_PIE = "sales_report:series_pie:%s:%s:%s:%s:%s";

    public static String LOCK_CAMPAIGN_OPERATE_KEY = "lock_campaign_operate_%s";
    public static String COMMON_TASK_CACHE_KEY = "COMMON_TASK_CACHE_KEY_%s";

    public static final Integer FIVE_MINUTE_SECOND = 60 * 5;
    public static final Integer TEN_MINUTE_SECOND = 60 * 10;
    public static final Integer HALF_DAY_SECOND = 60 * 60 * 12;
    public static final Integer ONE_HOUR_SECOND = 60 * 60 * 1;
    public static final Integer ONE_DAY_SECOND = 60 * 60 * 24;
    public static final Integer ONE_MONTH_SECOND = 60 * 60 * 24 * 30;
    public static final Integer LOCK_TIME_MIN = 1000 * 10;

    // ===================== social-timeline 好友/标签缓存（全局版本号失效，A4）=====================

    /** 好友ID集合缓存 key 模板，参数顺序：{fver 全局版本}:{userId} */
    public static final String CACHE_MOMENTS_FRIEND_IDS = "moments:frd:%s:%s";
    /** 好友标签绑定缓存 key 模板，参数顺序：{ftagver 全局版本}:{ownerId}:{friendId} */
    public static final String CACHE_MOMENTS_FRIEND_TAGS = "moments:ftag:%s:%s:%s";
    /** 好友缓存全局版本 key（bind/unbind/deleteTag/mockData 后 INCR 使旧 key 自然失效） */
    public static final String CACHE_MOMENTS_FRIEND_VER = "moments:fver";
    /** 标签缓存全局版本 key（INCR 失效，语义同上） */
    public static final String CACHE_MOMENTS_FTAG_VER = "moments:ftagver";

}
