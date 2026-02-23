package com.example.myapplication.utils;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 时间工具类：提供各种格式的时间戳
 */
public class TimeUtils {

    /**
     * 获取精确到毫秒的时间戳（用于日志、时间戳对齐）
     * 格式：2025-04-05 15:22:33.456
     */
    public static String getPreciseTimeStamp() {
        SimpleDateFormat sdf = new SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault());
        return sdf.format(new Date());
    }

    /**
     /**
     * 获取用于文件名的超精准时间戳（毫秒级，永不重复）
     * 格式：20250405152233456
     */
    public static String getFileNameTimeStamp() {
        SimpleDateFormat sdf = new SimpleDateFormat(
                "yyyyMMddHHmmssSSS", Locale.getDefault());
        return sdf.format(new Date());
    }

    /**
     * 获取简洁时间戳（用于文件夹名，用户一眼就能看懂）
     * 格式：20250405_152233
     */
    public static String getSimpleTimeStamp() {
        SimpleDateFormat sdf = new SimpleDateFormat(
                "yyyyMMdd_HHmmss", Locale.getDefault());
        return sdf.format(new Date());
    }

    /**
     * 获取中文格式时间（用于报告标题）
     * 格式：2025年04月05日 15:22:33
     */
    public static String getCurrentTime() {
        SimpleDateFormat sdf = new SimpleDateFormat(
                "yyyy年MM月dd日 HH:mm:ss", Locale.CHINA);
        return sdf.format(new Date());
    }

    private static final ThreadLocal<SimpleDateFormat> PRECISE_SDF =
            ThreadLocal.withInitial(() -> new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()));

    /**
     * 将 "2025-04-05 15:22:33.456" 格式的时间戳解析为毫秒值
     */
    public static long parseTimeToMillis(String preciseTime) {
        if (preciseTime == null || preciseTime.isEmpty()) {
            return 0L;
        }
        try {
            Date date = PRECISE_SDF.get().parse(preciseTime);
            return date != null ? date.getTime() : 0L;
        } catch (ParseException e) {
            e.printStackTrace();
            return 0L;
        }
    }

    /**
     * 方便直接调用：返回当前时间的毫秒值（等价于 System.currentTimeMillis()）
     */
    public static long currentTimeMillis() {
        return System.currentTimeMillis();
    }
}