package com.funny.moments.common.utils;

import java.text.DateFormat;
import java.text.Format;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.TimeZone;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateFormatUtils;

import lombok.extern.slf4j.Slf4j;

/**
 * #func 日期格式化utils #desc 在此添加实现相关说明
 *
 * @author qiaozhenpeng
 * @version 4.0
 */
@Slf4j
public class DateUtils {

    public static final int SECOND = 1;
    public static final int MINUTE = 2;
    public static final int HOUR = 3;
    public static final int DAY = 4;
    public static final String PATTERN = "yyyy-MM-dd HH:mm:ss";

    public static final String PATTERNS = "yyyy-MM-dd_HHmmss";

    public static final String FORMAT = "yyyy-MM-dd HH:mm:ss";
    public static final String MD = "MMdd-HHmmss";
    public static final String FORMAT_MD = "MMdd";
    public static final String FORMAT_YEAR = "yyyy";

    public static final String PATTERN_SSS = "yyyy-MM-dd HH:mm:ss.SSS";
    public static final String MAX_DATE = "2099-12-31 23:59:59";

    private static final String DAY_MIN_TIME = "00:00:00";
    private static final String DAY_MAX_TIME = "23:59:59";


    public static final String FORMAT_YMD = "yyyy-MM-dd";
    public static final String FORMAT_YMD2 = "yyyy/MM/dd";
    public static final String FORMAT_YM = "yyyy-MM";
    public static final String FORMAT_YM2 = "yyyyMM";
    public static final String FORMAT_YYYYMMDD = "yyyyMMdd";
    public static final String MEDIA_FORMAT = "yyyy/MM/dd HH:mm:ss";


    /**
     * #func 判断指定日期的格式是否合法<br>
     * #desc 在此添加实现相关说明
     *
     * @author qiaozhenpeng
     * @version 4.0
     */
    public static boolean isValid(String date, String pattern) {
        Date parseDate = parseDate(date, pattern);
        return parseDate != null && format(parseDate, pattern).equals(date);
    }

    /**
     * 是否是有效的日期区间<br>
     * 日期区间用半角","分隔,且结束日期大于等于开始日期
     *
     * @param dateHorizon 日期区间,eg.2014-01-02,2014-11-11
     * @author robin
     */
    public static boolean isValidDateHorizon(String dateHorizon) {
        if (StringUtils.isBlank(dateHorizon)) {
            return false;
        }
        String[] tmpAry = StringUtils.split(dateHorizon, ",");
        if (tmpAry.length < 2) {
            return false;
        }
        if (!isValid(tmpAry[0], "yyyy-MM-dd")) {
            return false;
        }
        if (!isValid(tmpAry[1], "yyyy-MM-dd")) {
            return false;
        }
        return true;
    }

    /**
     * #func 格式化日期<br>
     * #desc 在此添加实现相关说明
     *
     * @author qiaozhenpeng
     * @version 4.0
     */
    public static String format(Date date, String pattern) {
        if (date == null) {
            return null;
        }
        return DateFormatUtils.format(date, pattern);
    }

    /**
     * #func 格式化日期<br>
     * #desc 使用yyyy-MM-dd作为样式
     *
     * @author qiaozhenpeng
     * @version VERSION
     */
    public static String format(Date date) {
        return DateFormatUtils.format(date, "yyyy-MM-dd");
    }

    public static String formatDefault(Date date) {
        return DateFormatUtils.format(date, PATTERN);
    }

    public static String formatSS(Date date) {
        return DateFormatUtils.format(date, PATTERNS);
    }

    public static String formatDefaultSSS(Date date) {
        return DateFormatUtils.format(date, PATTERN_SSS);
    }

    /**
     * #func 把字符串转化为日期<br>
     * #desc 在此添加实现相关说明
     *
     * @author qiaozhenpeng
     * @version 4.0
     */
    public static Date parseDate(String date, String pattern) {
        try {
            return org.apache.commons.lang3.time.DateUtils.parseDate(date,
                    new String[]{pattern});
        } catch (ParseException e) {
            return null;
        }
    }

    /**
     * #func 把字符串转化为日期<br>
     * #desc 使用yyyy-MM-dd作为样式
     *
     * @author qiaozhenpeng
     * @version 4.0
     */
    public static Date parseDate(String date) {
        try {
            return org.apache.commons.lang3.time.DateUtils.parseDate(date,
                    new String[]{"yyyy-MM-dd"});
        } catch (ParseException e) {
            return null;
        }
    }

    public static Date parseDateYMDHMS(String date) {
        try {
            return org.apache.commons.lang3.time.DateUtils.parseDate(date, new String[]{PATTERN});
        } catch (ParseException e) {
            return null;
        }
    }

    public static Date parseDateDefault(String date) {
        try {
            return org.apache.commons.lang3.time.DateUtils.parseDate(date,
                    new String[]{PATTERN});
        } catch (ParseException e) {
            return null;
        }
    }

    /**
     * #func 计算相对年<br>
     * #desc amount可以为负数
     *
     * @author qiaozhenpeng
     * @version 4.0
     */
    public static Date addYears(Date date, int amount) {
        return org.apache.commons.lang3.time.DateUtils.addYears(date, amount);
    }

    /**
     * #func 计算相对月<br>
     * #desc amount可以为负数
     *
     * @author qiaozhenpeng
     * @version 4.0
     */
    public static Date addMonths(Date date, int amount) {
        return org.apache.commons.lang3.time.DateUtils.addMonths(date, amount);
    }

    /**
     * #func 计算相对日<br>
     * #desc amount可以为负数
     *
     * @author qiaozhenpeng
     * @version 4.0
     */
    public static Date addDays(Date date, int amount) {
        return org.apache.commons.lang3.time.DateUtils.addDays(date, amount);
    }

    public static Date addHours(Date date, int amount) {
        return org.apache.commons.lang3.time.DateUtils.addHours(date, amount);
    }

    /**
     * 给定日期格式字符串,加/日期后返回原格式的日期字符串<br>
     * #desc addDays为计算相对时间，可为负数
     *
     * @author qiaozhenpeng
     * @version 4.0.14
     */
    public static String addDays(String dateStr, String pattern, int addDays) {
        try {
            Date date = parseDate(dateStr, pattern);
            if (addDays != 0) {
                date = addDays(date, addDays);
            }
            return DateUtils.format(date, pattern);
        } catch (Exception e) {
            return null;
        }
    }

    public static Date addSeconds(Date date, int secs) {
        return org.apache.commons.lang3.time.DateUtils.addSeconds(date, secs);
    }

    /**
     * #func 计算相对星期<br>
     * #desc 在此添加实现相关说明
     *
     * @author qiaozhenpeng
     * @version 4.0
     */
    public static Date addWeeks(Date date, int amount) {
        return org.apache.commons.lang3.time.DateUtils.addWeeks(date, amount);
    }

    /**
     * #func 计算时间跨度<br>
     * #desc 计算方式：结束时间减开始时间
     *
     * @author qiaozhenpeng
     * @version 4.0
     */
    public static long getTimeSpan(Date begin, Date end, int type) {
        long diff = end.getTime() - begin.getTime();
        switch (type) {
            case DAY:
            default:
                return diff / org.apache.commons.lang3.time.DateUtils.MILLIS_PER_DAY;
            case HOUR:
                return diff
                        / org.apache.commons.lang3.time.DateUtils.MILLIS_PER_HOUR;
            case MINUTE:
                return diff
                        / org.apache.commons.lang3.time.DateUtils.MILLIS_PER_MINUTE;
            case SECOND:
                return diff
                        / org.apache.commons.lang3.time.DateUtils.MILLIS_PER_SECOND;
        }
    }

    /**
     * #func 获得日期差<br>
     * #desc 不足一天的忽略
     *
     * @author qiaozhenpeng
     * @version 4.0
     */
    public static long getDaySpan(Date begin, Date end) {
        return getTimeSpan(begin, end, DAY);
    }

    /**
     * #func 获得小时差<br>
     * #desc 不足一小时的忽略
     *
     * @author qiaozhenpeng
     * @version 4.0
     */
    public static long getHourSpan(Date begin, Date end) {
        return getTimeSpan(begin, end, HOUR);
    }

    /**
     * #func 获得分钟差<br>
     * #desc 不足一分钟的忽略
     *
     * @author qiaozhenpeng
     * @version 4.0
     */
    public static long getMinuteSpan(Date begin, Date end) {
        return getTimeSpan(begin, end, MINUTE);
    }

    /**
     * #func 获得秒差<br>
     * #desc 不足一秒的忽略
     *
     * @author qiaozhenpeng
     * @version 4.0
     */
    public static long getSecondSpan(Date begin, Date end) {
        return getTimeSpan(begin, end, SECOND);
    }

    /**
     * #func 获取月份差<br>
     *
     * @author qiaozhenpeng
     */
    public static int getMonthSpan(Date begin, Date end) {
        Calendar beginCal = new GregorianCalendar();
        beginCal.setTime(begin);
        Calendar endCal = new GregorianCalendar();
        endCal.setTime(end);
        int month = (endCal.get(Calendar.MONTH)) - (beginCal.get(Calendar.MONTH));
        int year = (endCal.get(Calendar.YEAR)) - (beginCal.get(Calendar.YEAR));
        return year * 12 + month;
    }

    /**
     * #func 获取当前时间当月的第一天<br>
     *
     * @author qiaozhenpeng
     */
    public static Date getFirstDayOfMonth(Date date) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        int firstDay = calendar.getActualMinimum(Calendar.DAY_OF_MONTH);
        return org.apache.commons.lang3.time.DateUtils.setDays(date, firstDay);
    }

    /**
     * #func 获取当前时间当月的最后一天<br>
     *
     * @author qiaozhenpeng
     */
    public static Date getLastDayOfMonth(Date date) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        int firstDay = calendar.getActualMaximum(Calendar.DAY_OF_MONTH);
        return org.apache.commons.lang3.time.DateUtils.setDays(date, firstDay);
    }


    /**
     * 获得指定日期以前的一个日期，0表示当天 正整数向前推 负数向后推
     * 比如1 表示明天  -1表示昨天  0表示今天
     *
     * @author qiaozhenpeng
     * @version 1.0.0
     */
    public static Date getDateByNum(Date date, Integer num, String format) throws ParseException {
        if (num == null) {
            return null;
        }
        Calendar cal = Calendar.getInstance();
        if (date != null) {
            cal.setTime(date);
        }
        cal.setTimeInMillis(cal.getTimeInMillis() + num * 24L * 60 * 60L * 1000L);

        return strToDateTime(dateToString(cal.getTime(), format), "");
    }

    /**
     * 获得指定日期以前的一个日期，0表示当天 正整数向前推 负数向后推
     * 比如1 表示明天  -1表示昨天  0表示今天
     *
     * @author qiaozhenpeng
     * @version 1.0.0
     */
    public static Date getDateByNum(Date date, Integer num) throws ParseException {
        if (num == null) {
            return null;
        }
        Calendar cal = Calendar.getInstance();
        if (date != null) {
            cal.setTime(date);
        }
        cal.setTimeInMillis(cal.getTimeInMillis() + num * 24L * 60 * 60L * 1000L);

        return cal.getTime();
    }

    public static String dateToString(Date date, String format) {
        SimpleDateFormat sdf = new SimpleDateFormat(format);
        return sdf.format(date);
    }

    public static String dateToString(Date date) {
        return dateToString(date, "yyyy-MM-dd");
    }

    public static String dateToStringSS(Date date) {
        return dateToString(date, FORMAT);
    }

    public static Date strToDateTime(String time) throws ParseException {

        SimpleDateFormat sdf = new SimpleDateFormat(
                "yyyy-MM-dd");

        Date date = sdf.parse(time);

        return date;
    }

    public static Date strToDateTime(String time, String join) throws ParseException {
        SimpleDateFormat sdf = new SimpleDateFormat(
                "yyyy" + join + "MM" + join + "dd");
        Date date = sdf.parse(time);
        return date;
    }

    /**
     * @param date 日期
     * @return true:weekend;
     * @Description whether the date is weekend or not
     * @author lizhaofu
     */
    public static boolean isWeekEnd(Date date) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        return Calendar.SATURDAY == calendar.get(Calendar.DAY_OF_WEEK) || Calendar.SUNDAY == calendar.get(Calendar.DAY_OF_WEEK);
    }

    /**
     * @param date 日期
     * @return int:day;
     * @Description get date of month
     * @author lizhaofu
     */
    public static int getDayOfMonth(Date date) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        return calendar.get(Calendar.DAY_OF_MONTH);
    }


    public static Date parseStartDate(String startDate) {
        StringBuilder sb = new StringBuilder(startDate);
        sb.append(" ");
        sb.append(DAY_MIN_TIME);
        return parseDate(sb.toString(), PATTERN);
    }

    public static Date parseEndDate(String endDate) {
        StringBuilder sb = new StringBuilder(endDate);
        sb.append(" ");
        sb.append(DAY_MAX_TIME);
        return parseDate(sb.toString(), PATTERN);
    }


    /**
     * 获取当前季度
     *
     * @param date 日期
     * @return
     */
    public static int getCurrentSeason(Date date) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        int currentMonth = calendar.get(Calendar.MONTH) + 1;
        if (1 <= currentMonth && currentMonth <= 3) {
            return 1;
        } else if (4 <= currentMonth && currentMonth <= 6) {
            return 2;
        } else if (7 <= currentMonth && currentMonth <= 9) {
            return 3;
        } else if (10 <= currentMonth && currentMonth <= 12) {
            return 4;
        }
        return 0;
    }

    /**
     * 获取当前日期的年份季度 如 201501
     *
     * @param date 日期
     * @return
     */
    public static int getCurrentYearWithSeason(Date date) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        int currentYear = calendar.get(Calendar.YEAR);
        return currentYear * 100 + getCurrentSeason(date);
    }

    /**
     * 获取当前日期，时间00:00:00
     *
     * @return Date
     */
    public static Date getToday() {

        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        Date today = calendar.getTime();

        return today;
    }

    /**
     * 获取时间2099-12-31，23:59:59
     *
     * @return java.util.Date
     */
    public static Date getMaxDate() {
        Date date = null;
        DateFormat formatter = new SimpleDateFormat(FORMAT);
        try {
            date = formatter.parse(MAX_DATE);
        } catch (ParseException e) {
            log.error("DateUtils 日期解析异常", e);
        }
        return date;
    }

    /**
     * 获取当前日期是本月的第几天
     */
    public static int getDaysByCurrentDay() {
        return LocalDate.now().get(ChronoField.DAY_OF_MONTH);
    }

    /**
     * 获取时间是本月的第几天
     */
    public static int getDaysByDate(Date date) {
        String format = format(new Date());
        return LocalDate.parse(format).get(ChronoField.DAY_OF_MONTH);
    }

    /***
     * 将字符串转化为日期
     */
    public static Date paraseStringToDate(String timestr) {
        Date date = null;


        Format format = new SimpleDateFormat("yyyy-MM-dd");
        try {
            date = (Date) format.parseObject(timestr);
        } catch (ParseException e) {
            log.error("DateUtils 日期解析异常", e);
        }
        return date;
    }


    /***
     * 根据传入的日期获取所在月份所有日期
     */
    public static List<String> getAllDaysMonthByDate(Date d) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        List<String> list = new ArrayList();
        Date date = getMonthStart(d);
        Date monthEnd = getMonthEnd(d);
        while (!date.after(monthEnd)) {
            list.add(sdf.format(date));
            date = getNext(date);
        }
        return list;
    }

    /***
     * 获取当前时间所在月份之前的所有日期
     */
    public static List<String> getAllDaysMonthBeforeDate(Date d) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        List<String> list = new ArrayList();
        Date date = getMonthStart(d);
        while (!date.after(d)) {
            list.add(sdf.format(date));
            date = getNext(date);
        }
        return list;
    }


    public static String getCurrentTime() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmssSSS");
        String format = sdf.format(new Date());
        return format;
    }


    /***
     * 获取日期的当月开始日期
     */
    public static Date getMonthStart(Date date) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        int index = calendar.get(Calendar.DAY_OF_MONTH);
        calendar.add(Calendar.DATE, (1 - index));
        return calendar.getTime();
    }

    /***
     * 获取日期的当月结束日期
     */
    public static Date getMonthEnd(Date date) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.add(Calendar.MONTH, 1);
        int index = calendar.get(Calendar.DAY_OF_MONTH);
        calendar.add(Calendar.DATE, (-index));
        return calendar.getTime();
    }

    /***
     * 获取日期的后一天
     */
    public static Date getNext(Date date) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.add(Calendar.DATE, 1);
        return calendar.getTime();
    }

    /***
     * 获取日期的前一天
     */
    public static Date getLast(Date date) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.add(Calendar.DATE, -1);
        return calendar.getTime();
    }

    /**
     * 判断时间格式 格式必须为“YYYY-MM-dd”
     * 2004-2-30 是无效的
     * 2003-2-29 是无效的
     *
     * @param str 日期
     * @return boolean
     */
    public static boolean isValidDate(String str) {
        DateFormat formatter = new SimpleDateFormat(FORMAT_YMD);
        try {
            Date date = formatter.parse(str);
            return str.equals(formatter.format(date));
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 判断时间格式 格式必须为“yyyy-MM-dd HH:mm:ss”
     *
     * @param str 日期
     * @return boolean
     */
    public static boolean isValidDateHHMMSS(String str) {
        DateFormat formatter = new SimpleDateFormat(FORMAT);
        try {
            Date date = formatter.parse(str);
            return str.equals(formatter.format(date));
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 通过时间秒毫秒数判断两个时间的天数相隔；注意：同一天内的任意时间间的比较返值为0
     *
     * @param date1 日期
     * @param date2 日期
     * @return
     */
    public static int differentDaysByMillisecond(Date date1, Date date2) {
        int days = (int) ((date2.getTime() - date1.getTime()) / (1000 * 3600 * 24));
        return days;
    }

    public static int differentDaysByMillisecond(String time1, String time2) {
        Date date1 = parseDateDefault(time1);
        Date date2 = parseDateDefault(time2);
        int days = differentDaysByMillisecond(date1, date2);
        return days;
    }


    public static Date getBeforeDaysDateStart(Date date, int days) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.set(11, 0);
        calendar.set(12, 0);
        calendar.set(13, 0);
        calendar.set(14, 0);
        calendar.add(Calendar.DAY_OF_MONTH, days);
        return calendar.getTime();
    }


    public static Date getYesterdayStart() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(11, 0);
        calendar.set(12, 0);
        calendar.set(13, 0);
        calendar.set(14, 0);
        calendar.add(Calendar.DAY_OF_MONTH, -1);
        Date today = calendar.getTime();
        return today;
    }

    public static Date getYesterdayEnd() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(11, 23);
        calendar.set(12, 59);
        calendar.set(13, 59);
        calendar.set(14, 0);
        calendar.add(Calendar.DAY_OF_MONTH, -1);
        Date today = calendar.getTime();
        return today;
    }

    public static long getUnixTimestamp() {
        Date currentDate = new Date();
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(currentDate);
        TimeZone timeZone = TimeZone.getTimeZone("UTC");
        calendar.setTimeZone(timeZone);
        // 转换为UTC时间
        Date utcDate = calendar.getTime();
        long time = utcDate.getTime() / 1000;
        return time;
    }

    public static Date getMaxDateByParam(Date startDate, Date endDate) {
        return startDate.before(endDate) ? endDate : startDate;
    }

    public static Date getMinDateByParam(Date startDate, Date endDate) {
        return startDate.before(endDate) ? startDate : endDate;
    }

    /**
     * 根据给定日期获取年的最后一位和月日
     *
     * @param now
     * @return
     */
    public static String formatYMMDD(Date now) {
        String md = DateUtils.format(now, DateUtils.FORMAT_MD);
        String year = DateUtils.format(now, DateUtils.FORMAT_YEAR);
        return year.substring(year.length() - 1) + md;
    }

    /**
     * 获取两个时间中的每一天
     *
     * @return
     * @throws ParseException
     */
    public static List<Date> getDays(Date startDate, Date endDate) {
        //定义一个接受时间的集合
        List<Date> lDate = new ArrayList<>();
        lDate.add(startDate);
        Calendar calBegin = Calendar.getInstance();
        // 使用给定的 Date 设置此 Calendar 的时间
        calBegin.setTime(startDate);
        Calendar calEnd = Calendar.getInstance();
        // 使用给定的 Date 设置此 Calendar 的时间
        calEnd.setTime(endDate);
        // 测试此日期是否在指定日期之后
        while (endDate.after(calBegin.getTime())) {
            // 根据日历的规则，为给定的日历字段添加或减去指定的时间量
            calBegin.add(Calendar.DAY_OF_MONTH, 1);
            lDate.add(calBegin.getTime());
        }
        return lDate;
    }

    public static List<String> nearDateStrByDays(Date endDate, int days) {
        List<String> dateStr = new ArrayList<>();
        for (int i = days; i > 0; i--) {
            dateStr.add(format(getBeforeDaysDateStart(endDate, -i)));
        }
        return dateStr;
    }

    public static boolean isInPubCycle(Date startTime, Date endTime) {
        return compareDay(startTime, new Date()) && (endTime == null || compareDay(new Date(), endTime));
    }

    /**
     * 比较两个日期大小，单位为天<br>
     * 结束日期在起始日期之后返回true
     *
     * @return
     */
    public static boolean compareDay(Date startTime, Date endTime) {

        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
            String startDateStr = sdf.format(startTime);
            String endDateStr = sdf.format(endTime);
            Date sdfStartDate = sdf.parse(startDateStr);
            Date sdfEndDate = sdf.parse(endDateStr);
            return sdfEndDate.compareTo(sdfStartDate) >= 0;
        } catch (Exception e) {
            return false;
        }
    }

    public static void main(String[] args) {

//        try{
//            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
//            String startDateStr = "2025-01-11";
//            String endDateStr = "2025-01-11";
//            Date sdfStartDate = sdf.parse(startDateStr);
//            Date sdfEndDate = sdf.parse(endDateStr);
//
//            boolean isPub = DateUtils.isInPubCycle(sdfStartDate, sdfEndDate);
//            if (isPub) {
//                System.out.println(CampaignStatusEnum.BIDDING.getDesc());
//            } else {
//                //不在投放期判断 是否为待投放或者已下线
//                if (sdfEndDate != null && !DateUtils.compareDay(new Date(), sdfEndDate)) {
//                    System.out.println(CampaignStatusEnum.OFFLINE.getDesc());
//                } else {
//                    System.out.println(CampaignStatusEnum.PENDING.getDesc());
//                }
//            }
//        }catch (Exception e){
//            e.printStackTrace();
//        }
        System.out.println(formatTime(addSeconds(new Date(), -10)));
        System.out.println(formatTime(addSeconds(new Date(), -60 * 2)));

        System.out.println(formatTime(addSeconds(new Date(), -60 * 60 * 3)));
        System.out.println(formatTime(addSeconds(new Date(), -60 * 60 * 10)));
        System.out.println(formatTime(addSeconds(new Date(), -60 * 60 * 18)));

        System.out.println(formatTime(addSeconds(new Date(), -60 * 60 * 24)));
        System.out.println(formatTime(addSeconds(new Date(), -60 * 60 * 30)));
        System.out.println(formatTime(addSeconds(new Date(), -60 * 60 * 48)));

        System.out.println(formatTime(addSeconds(new Date(), -60 * 60 * 49)));
        System.out.println(formatTime(addSeconds(new Date(), -60 * 60 * 60)));
        System.out.println(formatTime(addSeconds(new Date(), -60 * 60 * 72)));

        System.out.println(formatTime(addSeconds(new Date(), -60 * 60 * 80)));
        System.out.println("100小时前" + formatTime(addSeconds(new Date(), -60 * 60 * 100)));

    }

    /**
     * 格式化发布时间（距当前时间）
     *
     * @param desTime 目标时间
     * @return
     */
    public static String formatTime(Date desTime) {
        String timeStr = "";

        Date curTime = new Date();
        if (desTime == null) {
            return timeStr;
        }
        if (desTime.getTime() > curTime.getTime()) {
            return timeStr;
        }

        try {
            Calendar curCalendar = Calendar.getInstance();
            curCalendar.setTime(curTime);
            Calendar desCalendar = Calendar.getInstance();
            desCalendar.setTime(desTime);

            Integer curYear = curCalendar.get(Calendar.YEAR);
            Integer curMonth = curCalendar.get(Calendar.MONTH) + 1;
            Integer curDate = curCalendar.get(Calendar.DATE);
            Integer desYear = desCalendar.get(Calendar.YEAR);
            Integer desMonth = desCalendar.get(Calendar.MONTH) + 1;
            Integer desDate = desCalendar.get(Calendar.DATE);

            //同一天
            if (curYear == desYear.intValue() && curMonth == desMonth.intValue() && curDate == desDate.intValue()) {
                // 相差【分钟】数
                Long minDiff = (curTime.getTime() - desTime.getTime()) / (1 * 60 * 1000);
                // 相差【小时】数
                Long hourDiff = (curTime.getTime() - desTime.getTime()) / (1 * 60 * 60 * 1000);
                if (hourDiff <= 0 && minDiff <= 0) { // 刚刚
                    timeStr = "刚刚";
                } else if (hourDiff <= 0 && minDiff > 0) {  // ${x}分钟前
                    timeStr = minDiff + "分钟前";
                } else {  // ${x}小时前
                    timeStr = hourDiff + "小时前";
                }
            } else { // 垮天
                Calendar dt = Calendar.getInstance();
                Calendar ct = Calendar.getInstance();
                ct.set(curYear, curMonth, curDate);
                dt.set(desYear, desMonth, desDate);
                //两时间相隔天数
                Long dayDiff = (ct.getTimeInMillis() - dt.getTimeInMillis()) / (1 * 24 * 60 * 60 * 1000);
                Integer desHour = desCalendar.get(Calendar.HOUR_OF_DAY);
                Integer desMin = desCalendar.get(Calendar.MINUTE);
                if (dayDiff == 1) { // 昨天 xx:xx
                    timeStr = "昨天 " + String.format("%s:%s", String.format("%02d", desHour), String.format("%02d", desMin));
                } else if (dayDiff == 2) { // 前天 xx:xx
                    timeStr = "前天 " + String.format("%s:%s", String.format("%02d", desHour), String.format("%02d", desMin));
                } else if (dayDiff == 3) { // 昨天 xx:xx
                    timeStr = "3天前"; // "3天前"
                }
//                else {  // 相差 超 3天 的
//                    if(curYear == desYear.intValue()) { // 同一年的  月-日
//                        timeStr = String.format("%s-%s", String.format("%02d", desMonth), String.format("%02d", desDate));
//                    } else {  // 垮同年的，年-月-日
//                        timeStr = String.format("%s-%s-%s", desYear, String.format("%02d", desMonth), String.format("%02d", desDate));
//                    }
//                }
            }
        } catch (Exception e) {
            log.error("DateUtils 时间计算异常", e);
        }
        return timeStr;
    }
}
