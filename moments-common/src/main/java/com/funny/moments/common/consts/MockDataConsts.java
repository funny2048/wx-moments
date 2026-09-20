package com.funny.moments.common.consts;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 模拟数据生成常量池（POST /api/mock-data，design §5.5 / A7 / A9）。
 * 全部不可变集合，供 MockDataServiceImpl 随机组装昵称/城市/标签分布。
 *
 * @Author: funny2048
 * @Date: 2026-09-21
 */
public final class MockDataConsts {

    private MockDataConsts() {
    }

    /** 默认 8 标签名（D2：每用户初始化落库的默认标签，顺序即展示顺序） */
    public static final List<String> DEFAULT_TAG_NAMES = Collections.unmodifiableList(
            Arrays.asList("家人", "亲戚", "同事", "同学", "朋友", "球友", "客户", "其他"));

    /**
     * 标签分布权重（A9）：PRD §2.5 "兴趣好友 15%" 拆为 球友10 + 客户5（8 默认标签无"兴趣好友"）。
     * LinkedHashMap 保证遍历顺序稳定，加权抽样逻辑由 MockDataServiceImpl 实现。
     */
    public static final Map<String, Integer> TAG_WEIGHTS;

    static {
        Map<String, Integer> weights = new LinkedHashMap<>();
        weights.put("同事", 25);
        weights.put("同学", 20);
        weights.put("朋友", 25);
        weights.put("家人", 5);
        weights.put("亲戚", 5);
        weights.put("球友", 10);
        weights.put("客户", 5);
        weights.put("其他", 5);
        TAG_WEIGHTS = Collections.unmodifiableMap(weights);
    }

    /** 姓氏池（昵称 = SURNAME_POOL[rnd] + GIVEN_NAME_POOL[rnd]） */
    public static final List<String> SURNAME_POOL = Collections.unmodifiableList(
            Arrays.asList("王", "李", "张", "刘", "陈", "杨", "黄", "赵", "吴", "周",
                    "徐", "孙", "马", "朱", "胡", "郭", "何", "林", "罗", "高",
                    "郑", "梁", "谢", "宋", "唐", "许", "韩", "冯", "邓", "曹"));

    /** 名字用字池 */
    public static final List<String> GIVEN_NAME_POOL = Collections.unmodifiableList(
            Arrays.asList("伟", "芳", "娜", "敏", "静", "丽", "强", "磊", "军", "洋",
                    "勇", "艳", "杰", "娟", "涛", "明", "超", "秀", "霞", "平",
                    "刚", "桂", "英", "华", "梅", "鑫", "波", "斌", "宇", "浩",
                    "凯", "越", "妍", "欣", "怡", "晨", "雪", "婷", "睿", "昊"));

    /** 城市池（city 随机取值） */
    public static final List<String> CITY_POOL = Collections.unmodifiableList(
            Arrays.asList("北京", "上海", "广州", "深圳", "杭州", "成都", "重庆", "武汉",
                    "西安", "南京", "苏州", "天津", "长沙", "郑州", "青岛", "大连",
                    "厦门", "宁波", "无锡", "合肥", "福州", "济南"));
}
