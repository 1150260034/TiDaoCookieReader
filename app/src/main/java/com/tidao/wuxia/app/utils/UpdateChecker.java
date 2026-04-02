package com.tidao.wuxia.app.utils;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

import com.tidao.wuxia.app.BuildConfig;

/**
 * 检查 GitHub Releases 是否有新版本
 */
public class UpdateChecker {
    private static final String TAG = "UpdateChecker";

    private static final String GITHUB_API_URL =
            "https://api.github.com/repos/1150260034/TiDaoCookieReader/releases/latest";

    private static final String RELEASES_PAGE_URL =
            "https://github.com/1150260034/TiDaoCookieReader/releases";

    private static final int TIMEOUT_MS = 5000;

    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());
    private static final AtomicLong requestIdCounter = new AtomicLong(0);

    private static volatile long currentRequestId = -1;
    private static volatile Context activeContext = null;

    public interface UpdateCallback {
        /**
         * @param latestVersion   最新版本号，如 "1.3.0"
         * @param releasePageUrl  GitHub Releases 页面地址（兜底用）
         * @param apkDownloadUrl  APK 直接下载地址；assets 为空时为空字符串
         */
        void onUpdateAvailable(String latestVersion, String releasePageUrl, String apkDownloadUrl);
    }

    /**
     * 异步检查是否有新版本，仅在发现更新时回调（失败则静默忽略）
     *
     * @param currentVersion 当前版本号，如 "1.2.1"
     * @param callback       发现新版本时的回调（在主线程中调用）
     */
    public static void checkForUpdates(String currentVersion, UpdateCallback callback) {
        checkForUpdates(currentVersion, callback, null);
    }

    /**
     * 异步检查是否有新版本，支持无更新/失败时的回调（用于手动触发时恢复 UI 状态）
     *
     * @param currentVersion    当前版本号，如 "1.2.1"
     * @param callback          发现新版本时的回调（在主线程中调用）
     * @param noUpdateCallback  无新版本或检测失败时的回调（在主线程中调用），为 null 则静默
     */
    public static void checkForUpdates(String currentVersion, UpdateCallback callback, Runnable noUpdateCallback) {
        checkForUpdates(null, currentVersion, callback, noUpdateCallback);
    }

    /**
     * 异步检查是否有新版本，支持生命周期管理、无更新/失败时的回调
     *
     * @param context           上下文对象（用于生命周期检查），为 null 则不做生命周期检查
     * @param currentVersion    当前版本号，如 "1.2.1"
     * @param callback          发现新版本时的回调（在主线程中调用）
     * @param noUpdateCallback  无新版本或检测失败时的回调（在主线程中调用），为 null 则静默
     */
    public static void checkForUpdates(Context context, String currentVersion, UpdateCallback callback, Runnable noUpdateCallback) {
        // 防御性处理：确保 callback 不为 null，避免异步调用时出现 NPE
        final UpdateCallback safeCallback = (callback != null) ? callback :
                (latestVersion, releasePageUrl, apkDownloadUrl) -> {
                    // no-op: 静默忽略
                };
        final Runnable safeNoUpdateCallback = (noUpdateCallback != null) ? noUpdateCallback : () -> {
            // no-op: 静默忽略
        };

        // 生成新的请求 ID，用于取消旧请求
        final long requestId = requestIdCounter.incrementAndGet();
        currentRequestId = requestId;
        activeContext = context;

        executor.execute(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(GITHUB_API_URL);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(TIMEOUT_MS);
                conn.setReadTimeout(TIMEOUT_MS);
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
                conn.setRequestProperty("User-Agent", "TiDaoCookieReader/" + currentVersion);

                int responseCode = conn.getResponseCode();
                if (responseCode != 200) {
                    Log.d(TAG, "GitHub API 返回 " + responseCode + "，跳过更新检测");
                    postCallback(requestId, safeNoUpdateCallback);
                    return;
                }

                StringBuilder sb = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
                }

                JSONObject json = new JSONObject(sb.toString());
                String tagName = json.optString("tag_name", "");
                String releaseName = json.optString("name", "");
                String htmlUrl = json.optString("html_url", RELEASES_PAGE_URL);
                String publishedAt = json.optString("published_at", "");

                // 提取第一个 asset 的 APK 直链，供应用内下载使用
                String apkDownloadUrl = "";
                JSONArray assets = json.optJSONArray("assets");
                if (assets != null && assets.length() > 0) {
                    apkDownloadUrl = assets.getJSONObject(0).optString("browser_download_url", "");
                }

                // 优先从 release name 中提取版本号（格式如 "最新版本 v1.2.3"）
                // CI 使用固定 tag `latest`，tag_name 不含有效版本，需从 name 中解析
                String latestVersion = extractVersionFromName(releaseName);
                if (latestVersion == null) {
                    // 兼容 android-release.yml 发布的 tag 格式（tag_name 如 "v1.2.3"）
                    if (tagName.isEmpty()) {
                        postCallback(requestId, safeNoUpdateCallback);
                        return;
                    }
                    latestVersion = tagName.startsWith("v") ? tagName.substring(1) : tagName;
                }

                boolean hasNewerVersion = isNewerVersion(latestVersion, currentVersion);
                // 版本号相同时，用 release 发布时间 vs 构建时间戳判断是否有更新构建
                boolean hasNewerBuild = !hasNewerVersion
                        && isSameVersion(latestVersion, currentVersion)
                        && isNewerBuild(publishedAt);

                if (hasNewerVersion || hasNewerBuild) {
                    String displayVersion = hasNewerBuild
                            ? latestVersion + "（新构建）"
                            : latestVersion;
                    Log.d(TAG, "发现新版本: " + displayVersion
                            + (hasNewerBuild ? "（发布时间晚于构建时间）" : ""));
                    final String finalApkUrl = apkDownloadUrl;
                    final String finalVersion = displayVersion;
                    postCallback(requestId, () ->
                            safeCallback.onUpdateAvailable(finalVersion, htmlUrl, finalApkUrl));
                } else {
                    Log.d(TAG, "当前已是最新版本");
                    postCallback(requestId, safeNoUpdateCallback);
                }

            } catch (Exception e) {
                // 网络不通或解析失败，静默忽略，不影响正常使用
                Log.d(TAG, "更新检测失败（静默忽略）: " + e.getMessage());
                postCallback(requestId, safeNoUpdateCallback);
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }

    /**
     * 发送回调到主线程前检查生命周期和请求 ID，避免 Activity 销毁时抛出异常
     *
     * @param requestId 请求 ID，用于取消旧请求
     * @param callback  要执行的回调
     */
    private static void postCallback(long requestId, Runnable callback) {
        mainHandler.post(() -> {
            // 检查请求是否已被取消（新请求已发起）
            if (requestId != currentRequestId) {
                Log.d(TAG, "请求 ID 不匹配（已取消），跳过回调");
                return;
            }

            // 检查 Activity 生命周期
            Context ctx = activeContext;
            if (ctx instanceof Activity) {
                Activity activity = (Activity) ctx;
                if (activity.isFinishing() || activity.isDestroyed()) {
                    Log.d(TAG, "Activity 已销毁，跳过回调");
                    return;
                }
            }

            // 生命周期有效，执行回调
            callback.run();
        });
    }

    /**
     * 从 release name 中提取版本号。
     * CI 发布的 release name 格式为 "最新版本 v1.2.3"，从中提取 "1.2.3"；
     * 匹配不到时返回 null（由调用方决定 fallback 策略）。
     */
    private static String extractVersionFromName(String releaseName) {
        if (releaseName == null || releaseName.isEmpty()) return null;
        Matcher m = Pattern.compile("v(\\d+\\.\\d+(?:\\.\\d+)*)").matcher(releaseName);
        return m.find() ? m.group(1) : null;
    }

    /**
     * 比较两个版本号，判断 latest 是否比 current 更新
     * 支持 x.y.z 格式，不含 prerelease 后缀
     */
    private static boolean isNewerVersion(String latest, String current) {
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
     * 判断两个版本号是否完全相同（忽略 prerelease 后缀）
     */
    private static boolean isSameVersion(String latest, String current) {
        try {
            latest = latest.split("-")[0];
            current = current.split("-")[0];
            return latest.equals(current);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 判断 release 发布时间是否晚于当前 APK 的构建时间。
     * 用于版本号相同时检测是否有更新的构建可用。
     *
     * @param publishedAt GitHub release 的 published_at（ISO 8601 格式）
     * @return true 表示 release 比当前构建更新
     */
    private static boolean isNewerBuild(String publishedAt) {
        if (publishedAt == null || publishedAt.isEmpty()) return false;
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
            sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
            long releaseTime = sdf.parse(publishedAt).getTime();
            long buildTime = BuildConfig.BUILD_TIMESTAMP;
            // 留 5 分钟容差，避免同一次构建的微小时间差误判
            return releaseTime > buildTime + 300_000L;
        } catch (Exception e) {
            Log.d(TAG, "发布时间解析失败: " + e.getMessage());
            return false;
        }
    }
}