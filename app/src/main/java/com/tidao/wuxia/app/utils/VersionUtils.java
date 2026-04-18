package com.tidao.wuxia.app.utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 纯 Java 版本号解析与比较工具类，无 Android 运行时依赖。
 * 由 UpdateChecker 委托调用，也可在本地 JVM 单元测试中直接使用。
 */
class VersionUtils {

    // 用于从 release name 中提取构建号，如 "Build 42" -> 42
    private static final Pattern BUILD_NUMBER_PATTERN = Pattern.compile("Build\\s+(\\d+)");

    // 私有构造，工具类不允许实例化
    private VersionUtils() {}

    /**
     * 从 release name 中提取版本号。
     * CI 发布的 release name 格式为 "最新版本 v1.2.3"，从中提取 "1.2.3"；
     * 匹配不到时返回 null（由调用方决定 fallback 策略）。
     */
    static String extractVersionFromName(String releaseName) {
        if (releaseName == null || releaseName.isEmpty()) return null;
        Matcher m = Pattern.compile("v(\\d+\\.\\d+(?:\\.\\d+)*)").matcher(releaseName);
        return m.find() ? m.group(1) : null;
    }

    /**
     * 比较两个版本号，判断 latest 是否比 current 更新。
     * 支持 x.y.z 格式，忽略 prerelease 后缀（"-" 之后的部分），不足段补零对齐。
     */
    static boolean isNewerVersion(String latest, String current) {
        try {
            // 只取 "-" 之前的部分，避免 prerelease 后缀干扰
            latest = latest.split("-")[0];
            current = current.split("-")[0];

            String[] latestParts = latest.split("\\.");
            String[] currentParts = current.split("\\.");
            int len = Math.max(latestParts.length, currentParts.length);
            for (int i = 0; i < len; i++) {
                int latestPart = i < latestParts.length ? Integer.parseInt(latestParts[i].trim()) : 0;
                int currentPart = i < currentParts.length ? Integer.parseInt(currentParts[i].trim()) : 0;
                if (latestPart > currentPart) return true;
                if (latestPart < currentPart) return false;
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 判断两个版本号是否完全相同（忽略 prerelease 后缀，补零对齐）。
     */
    static boolean isSameVersion(String latest, String current) {
        try {
            latest = latest.split("-")[0];
            current = current.split("-")[0];
            String[] latestParts = latest.split("\\.");
            String[] currentParts = current.split("\\.");
            int len = Math.max(latestParts.length, currentParts.length);
            for (int i = 0; i < len; i++) {
                int lp = i < latestParts.length ? Integer.parseInt(latestParts[i].trim()) : 0;
                int cp = i < currentParts.length ? Integer.parseInt(currentParts[i].trim()) : 0;
                if (lp != cp) return false;
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 提取远端构建号（如 "Build 42" -> 42）。解析失败返回 null。
     */
    static Integer extractBuildNumber(String source) {
        if (source == null || source.isEmpty()) return null;
        try {
            Matcher m = BUILD_NUMBER_PATTERN.matcher(source);
            if (!m.find()) return null;
            return Integer.parseInt(m.group(1));
        } catch (Exception e) {
            return null;
        }
    }
}
