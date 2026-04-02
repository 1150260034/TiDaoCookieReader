package com.tidao.wuxia.app.utils;

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
                    if (tagName.isEmpty()) return;
                    latestVersion = tagName.startsWith("v") ? tagName.substring(1) : tagName;
                }

                if (isNewerVersion(latestVersion, currentVersion)) {
                    Log.d(TAG, "发现新版本: " + latestVersion);
                    final String finalApkUrl = apkDownloadUrl;
                    final String finalVersion = latestVersion;
                    new Handler(Looper.getMainLooper()).post(() ->
                            callback.onUpdateAvailable(finalVersion, htmlUrl, finalApkUrl));
                } else {
                    Log.d(TAG, "当前已是最新版本");
                }

            } catch (Exception e) {
                // 网络不通或解析失败，静默忽略，不影响正常使用
                Log.d(TAG, "更新检测失败（静默忽略）: " + e.getMessage());
            } finally {
                if (conn != null) conn.disconnect();
            }
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
}
