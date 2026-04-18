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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

import com.tidao.wuxia.app.BuildConfig;

/**
 * 检查 GitHub Releases 或阿里云云函数是否有新版本。
 * 双源策略：云函数优先（国内 CDN 加速），失败时自动回退 GitHub API。
 */
public class UpdateChecker {
    private static final String TAG = "UpdateChecker";

    // 用于从 release body 中提取 .bin 下载链接
    private static final Pattern BIN_URL_PATTERN = Pattern.compile("https://[\\w./%-]+\\.bin");

    private static final String GITHUB_API_URL =
            "https://api.github.com/repos/1150260034/TiDaoCookieReader/releases/latest";

    private static final String RELEASES_PAGE_URL =
            "https://github.com/1150260034/TiDaoCookieReader/releases";

    // 云端更新详情页：CI 构建时注入，未配置时回退到 GitHub Releases 页面
    private static final String CLOUD_RELEASES_PAGE_URL =
            BuildConfig.CLOUD_RELEASES_PAGE_URL.isEmpty()
                    ? RELEASES_PAGE_URL
                    : BuildConfig.CLOUD_RELEASES_PAGE_URL;

    private static final int TIMEOUT_MS = 5000;

    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());
    private static final AtomicLong requestIdCounter = new AtomicLong(0);

    private static volatile long currentRequestId = -1;
    private static volatile Context activeContext = null;

    public interface UpdateCallback {
        /**
         * @param latestVersion   最新版本号，如 "1.3.0"
         * @param releasePageUrl  版本详情页 URL（云端或 GitHub Releases，供打开浏览器兜底用）
         * @param apkDownloadUrl  APK 直接下载地址；assets 为空时为空字符串
         */
        void onUpdateAvailable(String latestVersion, String releasePageUrl, String apkDownloadUrl);
    }

    /**
     * 检查失败时的回调（网络不通、解析错误、API 返回非 200 等情况）。
     * 仅在明确失败时触发，不与"当前已是最新版本"混淆。
     */
    public interface CheckFailedCallback {
        /**
         * @param reason 失败原因描述，已为中文，可直接展示或写入日志
         */
        void onCheckFailed(String reason);
    }

    /**
     * 返回更新详情页 URL（云端 CDN 优先，未配置时回退 GitHub Releases），
     * 用于检查失败时提供手动跳转兜底路径。
     */
    public static String getReleasesPageUrl() {
        return CLOUD_RELEASES_PAGE_URL;
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
     * @param noUpdateCallback  无新版本时的回调（在主线程中调用），为 null 则静默
     */
    public static void checkForUpdates(Context context, String currentVersion, UpdateCallback callback, Runnable noUpdateCallback) {
        checkForUpdates(context, currentVersion, callback, noUpdateCallback, null);
    }

    /**
     * 异步检查是否有新版本，三态回调：发现更新 / 无更新 / 检查失败（互斥，不混淆）
     *
     * @param context           上下文对象（用于生命周期检查），为 null 则不做生命周期检查
     * @param currentVersion    当前版本号，如 "1.2.1"
     * @param callback          发现新版本时的回调（在主线程中调用）
     * @param noUpdateCallback  服务端明确返回"无新版本"时的回调（在主线程中调用），为 null 则静默
     * @param failedCallback    所有渠道均无法完成检查时的回调（网络不通/解析失败等），为 null 则静默
     */
    public static void checkForUpdates(Context context, String currentVersion,
                                       UpdateCallback callback, Runnable noUpdateCallback,
                                       CheckFailedCallback failedCallback) {
        // 防御性处理：确保 callback 不为 null，避免异步调用时出现 NPE
        final UpdateCallback safeCallback = (callback != null) ? callback :
                (latestVersion, releasePageUrl, apkDownloadUrl) -> {
                    // no-op: 静默忽略
                };
        final Runnable safeNoUpdateCallback = (noUpdateCallback != null) ? noUpdateCallback : () -> {
            // no-op: 静默忽略
        };
        final CheckFailedCallback safeFailedCallback = (failedCallback != null) ? failedCallback : reason -> {
            // no-op: 静默忽略（静默自检路径）
        };

        // 生成新的请求 ID，用于取消旧请求
        final long requestId = requestIdCounter.incrementAndGet();
        currentRequestId = requestId;
        activeContext = context;

        executor.execute(() -> {
            // 双源策略：云函数可用时优先尝试，失败则回退 GitHub
            boolean cloudFunctionAvailable = !BuildConfig.API_TOKEN.isEmpty()
                    && !BuildConfig.FC_URL.isEmpty();

            if (cloudFunctionAvailable) {
                boolean handled = checkFromCloudFunction(
                        requestId, currentVersion, safeCallback, safeNoUpdateCallback);
                if (handled) return;
                Log.d(TAG, "云函数不可用，回退 GitHub API");
            }

            checkFromGitHub(requestId, currentVersion, safeCallback, safeNoUpdateCallback, safeFailedCallback);
        });
    }

    /**
     * 通过阿里云云函数检查更新（国内高速通道）。
     * 返回 true 表示已成功处理（无论有无更新），false 表示请求失败需回退。
     */
    private static boolean checkFromCloudFunction(long requestId, String currentVersion,
                                                   UpdateCallback callback, Runnable noUpdateCallback) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(BuildConfig.FC_URL);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setRequestProperty("Authorization", "Bearer " + BuildConfig.API_TOKEN);
            conn.setRequestProperty("User-Agent", "TiDaoCookieReader/" + currentVersion);

            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                Log.d(TAG, "云函数返回 " + responseCode + "，将回退 GitHub");
                return false;
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
            String remoteVersion = json.optString("version", "");
            int remoteVersionCode = json.optInt("versionCode", 0);
            String downloadUrl = json.optString("downloadUrl", "");

            if (remoteVersion.isEmpty()) {
                Log.d(TAG, "云函数返回的版本号为空，将回退 GitHub");
                return false;
            }

            boolean hasNewerVersion = isNewerVersion(remoteVersion, currentVersion);
            boolean hasNewerBuild = !hasNewerVersion
                    && isSameVersion(remoteVersion, currentVersion)
                    && hasNewerBuildCode(remoteVersionCode);

            if (hasNewerVersion || hasNewerBuild) {
                Log.d(TAG, "云函数发现新版本: " + remoteVersion
                        + (hasNewerBuild ? "（构建号更高）" : ""));
                postCallback(requestId, () ->
                        callback.onUpdateAvailable(remoteVersion, CLOUD_RELEASES_PAGE_URL, downloadUrl));
            } else {
                Log.d(TAG, "云函数检查：当前已是最新版本");
                postCallback(requestId, noUpdateCallback);
            }
            return true;

        } catch (Exception e) {
            Log.d(TAG, "云函数请求失败: " + e.getMessage());
            return false;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /**
     * 通过 GitHub API 检查更新（兜底通道）
     */
    private static void checkFromGitHub(long requestId, String currentVersion,
                                         UpdateCallback safeCallback, Runnable safeNoUpdateCallback,
                                         CheckFailedCallback safeFailedCallback) {
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
                final String reason = "更新服务器返回错误 (HTTP " + responseCode + ")";
                postCallback(requestId, () -> safeFailedCallback.onCheckFailed(reason));
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
            String body = json.optString("body", "");

            // 提取第一个 asset 的 APK 直链，供应用内下载使用
            String apkDownloadUrl = "";
            JSONArray assets = json.optJSONArray("assets");
            if (assets != null && assets.length() > 0) {
                apkDownloadUrl = assets.getJSONObject(0).optString("browser_download_url", "");
            } else {
                // 若 assets 为空，尝试从 body 里找 .bin 下载链接
                Matcher m = BIN_URL_PATTERN.matcher(body);
                if (m.find()) {
                    apkDownloadUrl = m.group();
                }
            }

            // 优先从 release name 中提取版本号（格式如 "最新版本 v1.2.3"）
            // CI 使用固定 tag `latest`，tag_name 不含有效版本，需从 name 中解析
            String latestVersion = extractVersionFromName(releaseName);
            if (latestVersion == null) {
                // 兼容 android-release.yml 发布的 tag 格式（tag_name 如 "v1.2.3"）
                if (tagName.isEmpty()) {
                    Log.d(TAG, "GitHub API 返回数据无法解析版本号");
                    postCallback(requestId, () -> safeFailedCallback.onCheckFailed("无法从服务端解析版本信息"));
                    return;
                }
                latestVersion = tagName.startsWith("v") ? tagName.substring(1) : tagName;
            }

            boolean hasNewerVersion = isNewerVersion(latestVersion, currentVersion);
            // 版本号相同时，比较 release name 或 body 中的构建号与本地 VERSION_CODE
            Integer remoteBuildCode = extractBuildNumber(releaseName);
            if (remoteBuildCode == null) {
                remoteBuildCode = extractBuildNumber(body);
            }
            boolean hasNewerBuild = !hasNewerVersion
                    && isSameVersion(latestVersion, currentVersion)
                    && hasNewerBuildCode(remoteBuildCode);

            if (hasNewerVersion || hasNewerBuild) {
                Log.d(TAG, "发现新版本: " + latestVersion
                        + (hasNewerBuild ? "（构建号更高）" : ""));
                final String finalApkUrl = apkDownloadUrl;
                final String finalVersion = latestVersion;
                postCallback(requestId, () ->
                        safeCallback.onUpdateAvailable(finalVersion, htmlUrl, finalApkUrl));
            } else {
                Log.d(TAG, "当前已是最新版本");
                postCallback(requestId, safeNoUpdateCallback);
            }

        } catch (Exception e) {
            // 网络不通或解析失败：通知失败回调（由调用方决定是否静默）
            Log.d(TAG, "更新检测失败: " + e.getMessage());
            final String errMsg = e.getMessage() != null ? e.getMessage() : "网络请求失败";
            postCallback(requestId, () -> safeFailedCallback.onCheckFailed(errMsg));
        } finally {
            if (conn != null) conn.disconnect();
        }
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
     * 从 release name 中提取版本号。委托 {@link VersionUtils#extractVersionFromName}。
     * 包私有以允许单元测试直接验证。
     */
    static String extractVersionFromName(String releaseName) {
        return VersionUtils.extractVersionFromName(releaseName);
    }

    /**
     * 比较两个版本号，判断 latest 是否比 current 更新。委托 {@link VersionUtils#isNewerVersion}。
     * 包私有以允许单元测试直接验证。
     */
    static boolean isNewerVersion(String latest, String current) {
        return VersionUtils.isNewerVersion(latest, current);
    }

    /**
     * 判断两个版本号是否完全相同（忽略 prerelease 后缀，补零对齐）。委托 {@link VersionUtils#isSameVersion}。
     * 包私有以允许单元测试直接验证。
     */
    static boolean isSameVersion(String latest, String current) {
        return VersionUtils.isSameVersion(latest, current);
    }

    /**
     * 提取远端构建号（如 "Build 42" -> 42）。委托 {@link VersionUtils#extractBuildNumber}。
     * 包私有以允许单元测试直接验证。
     */
    static Integer extractBuildNumber(String source) {
        return VersionUtils.extractBuildNumber(source);
    }

    /**
     * 统一构建号比较策略：仅当远端构建号存在且大于本地 VERSION_CODE 时判定有更新。
     */
    private static boolean hasNewerBuildCode(Integer remoteBuildCode) {
        if (remoteBuildCode == null) return false;
        return remoteBuildCode > BuildConfig.VERSION_CODE;
    }
}