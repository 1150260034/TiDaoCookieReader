package com.tidao.wuxia.app.cookie;

import android.util.Log;
import android.util.Patterns;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

/**
 * Cookie 提取器
 * 从 HTTP 流量中提取天刀助手登录所需的 Cookie 信息
 */
public class CookieExtractor {
    private static final String TAG = "CookieExtractor";

    // 天刀助手相关的服务器地址
    private static final String[] TARGET_HOSTS = {
            "ams.game.qq.com",
            "comm.ams.game.qq.com",
            "comm.aci.game.qq.com",
            "apps.game.qq.com"
    };

    // 需要提取的 Cookie 字段
    public static class CookieData {
        public String accessToken = "";
        public String openid = "";
        public String uin = "";
        public String gtk = "";
        public String appid = "";
        public String acctype = "";
        public String eas_sid = "";
        public String appOpenId = "";
        // 角色信息字段（从游戏数据库读取）
        public String userId = "";
        public String roleid = "";
        public String rolelevel = "";
        public String rolename = "";
        public String areaid = "";
        public boolean isComplete() {
            return !accessToken.isEmpty()
                    && !openid.isEmpty()
                    && !uin.isEmpty();
        }

        @Override
        public String toString() {
            return "CookieData{" +
                    "accessToken='" + accessToken + '\'' +
                    ", openid='" + openid + '\'' +
                    ", uin='" + uin + '\'' +
                    ", gtk='" + gtk + '\'' +
                    ", appid='" + appid + '\'' +
                    ", eas_sid='" + eas_sid + '\'' +
                    ", appOpenId='" + appOpenId + '\'' +
                    ", userId='" + userId + '\'' +
                    ", roleid='" + roleid + '\'' +
                    ", rolelevel='" + rolelevel + '\'' +
                    ", rolename='" + rolename + '\'' +
                    ", areaid='" + areaid + '\'' +
                    '}';
        }

        /**
         * 转换为 accounts.json 格式
         */
        public String toAccountJson(String roleName, String area, String roleId) {
            return String.format(
                    "    {\n" +
                    "      \"openid\": \"%s\",\n" +
                    "      \"access_token\": \"%s\",\n" +
                    "      \"uin\": \"%s\",\n" +
                    "      \"gtk\": \"%s\",\n" +
                    "      \"appid\": \"%s\",\n" +
                    "      \"eas_sid\": \"%s\",\n" +
                    "      \"roleName\": \"%s\",\n" +
                    "      \"area\": \"%s\",\n" +
                    "      \"roleId\": \"%s\"\n" +
                    "    }",
                    openid, accessToken, uin, gtk, appid, eas_sid,
                    roleName != null ? roleName : "",
                    area != null ? area : "",
                    roleId != null ? roleId : ""
            );
        }

        /**
         * 生成完整的 Cookie 字符串
         */
        public String toCookieString() {
            String effectiveAppOpenId = !appOpenId.isEmpty() ? appOpenId : openid;
            String effectiveAcctype = !acctype.isEmpty() ? acctype : "qc";
            StringBuilder sb = new StringBuilder();
            if (!accessToken.isEmpty()) sb.append("access_token=").append(accessToken).append("; ");
            if (!openid.isEmpty()) sb.append("openId=").append(openid).append("; ");
            if (!openid.isEmpty()) sb.append("openid=").append(openid).append("; ");
            if (!effectiveAppOpenId.isEmpty()) sb.append("appOpenId=").append(effectiveAppOpenId).append("; ");
            if (!effectiveAppOpenId.isEmpty()) sb.append("appOpenid=").append(effectiveAppOpenId).append("; ");
            if (!uin.isEmpty()) sb.append("uin=").append(uin).append("; ");
            if (!appid.isEmpty()) sb.append("appid=").append(appid).append("; ");
            if (!appid.isEmpty()) sb.append("appId=").append(appid).append("; ");
            if (!eas_sid.isEmpty()) sb.append("eas_sid=").append(eas_sid).append("; ");
            if (!accessToken.isEmpty()) sb.append("accessToken=").append(accessToken).append("; ");
            sb.append("acctype=").append(effectiveAcctype);
            return sb.toString().trim();
        }
    }

    // 提取到的 Cookie 数据
    private CookieData cookieData = new CookieData();

    // 回调接口
    public interface OnCookieExtractedListener {
        void onCookieExtracted(CookieData data);
        void onProgress(String message);
        void onError(String error);
    }

    private OnCookieExtractedListener listener;

    public void setListener(OnCookieExtractedListener listener) {
        this.listener = listener;
    }

    /**
     * 处理接收到的 HTTP 数据
     * @param host 目标主机
     * @param port 目标端口
     * @param data HTTP 请求或响应数据
     * @param isRequest true 表示请求，false 表示响应
     */
    public void processHttpData(String host, int port, byte[] data, boolean isRequest) {
        if (data == null || data.length == 0) return;

        try {
            // 检查是否是目标服务器
            if (!isTargetHost(host)) return;

            // 解析 HTTP 数据
            String httpData = decompressIfNeeded(data);

            if (isRequest) {
                processRequest(host, port, httpData);
            } else {
                processResponse(host, port, httpData);
            }

        } catch (Exception e) {
            Log.e(TAG, "Error processing HTTP data", e);
        }
    }

    private boolean isTargetHost(String host) {
        for (String target : TARGET_HOSTS) {
            if (host.contains(target) || target.contains(host)) {
                return true;
            }
        }
        return false;
    }

    private String decompressIfNeeded(byte[] data) {
        try {
            // 检查是否 gzip 压缩
            if (data.length > 2 &&
                    (data[0] == (byte) 0x1f && data[1] == (byte) 0x8b)) {

                ByteArrayInputStream bis = new ByteArrayInputStream(data);
                GZIPInputStream gis = new GZIPInputStream(bis);
                ByteArrayOutputStream bos = new ByteArrayOutputStream();

                byte[] buffer = new byte[1024];
                int len;
                while ((len = gis.read(buffer)) != -1) {
                    bos.write(buffer, 0, len);
                }

                return bos.toString("UTF-8");
            } else {
                return new String(data, StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            return new String(data, StandardCharsets.UTF_8);
        }
    }

    private void processRequest(String host, int port, String httpData) {
        // 从 URL 和 Header 中提取 Cookie
        Pattern urlPattern = Pattern.compile("(GET|POST)\\s+([^\\s]+)");
        Matcher urlMatcher = urlPattern.matcher(httpData);
        if (urlMatcher.find()) {
            String url = urlMatcher.group(2);
            extractFromUrl(url);

            if (listener != null) {
                listener.onProgress("检测到请求: " + url);
            }
        }

        // 从 Cookie header 中提取
        Pattern cookiePattern = Pattern.compile("Cookie:[^\r\n]+", Pattern.CASE_INSENSITIVE);
        Matcher cookieMatcher = cookiePattern.matcher(httpData);
        if (cookieMatcher.find()) {
            String cookie = cookieMatcher.group();
            extractFromCookieHeader(cookie);
        }
    }

    private void processResponse(String host, int port, String httpData) {
        // 从 Set-Cookie header 中提取
        Pattern setCookiePattern = Pattern.compile("Set-Cookie:[^\r\n]+", Pattern.CASE_INSENSITIVE);
        Matcher setCookieMatcher = setCookiePattern.matcher(httpData);
        while (setCookieMatcher.find()) {
            String setCookie = setCookieMatcher.group();
            extractFromSetCookie(setCookie);
        }

        // 检查响应体中是否包含 Cookie 信息（JSON 响应）
        if (httpData.contains("access_token") || httpData.contains("openid")) {
            extractFromJson(httpData);
        }
    }

    private void extractFromUrl(String url) {
        Log.d(TAG, "Extracting from URL: " + url);

        // access_token
        Pattern atPattern = Pattern.compile("access_token=([^&\\s]+)");
        Matcher atMatcher = atPattern.matcher(url);
        if (atMatcher.find() && cookieData.accessToken.isEmpty()) {
            cookieData.accessToken = atMatcher.group(1);
            notifyUpdate();
        }

        // openid
        Pattern oidPattern = Pattern.compile("openid=([^&\\s]+)");
        Matcher oidMatcher = oidPattern.matcher(url);
        if (oidMatcher.find() && cookieData.openid.isEmpty()) {
            cookieData.openid = oidMatcher.group(1);
            notifyUpdate();
        }

        // uin
        Pattern uinPattern = Pattern.compile("uin=([^&\\s]+)");
        Matcher uinMatcher = uinPattern.matcher(url);
        if (uinMatcher.find() && cookieData.uin.isEmpty()) {
            cookieData.uin = uinMatcher.group(1);
            notifyUpdate();
        }

        // gtk (g_tk)
        Pattern gtkPattern = Pattern.compile("g_tk=([^&\\s]+)");
        Matcher gtkMatcher = gtkPattern.matcher(url);
        if (gtkMatcher.find() && cookieData.gtk.isEmpty()) {
            cookieData.gtk = gtkMatcher.group(1);
            notifyUpdate();
        }

        // appid
        Pattern appidPattern = Pattern.compile("appid=([^&\\s]+)");
        Matcher appidMatcher = appidPattern.matcher(url);
        if (appidMatcher.find() && cookieData.appid.isEmpty()) {
            cookieData.appid = appidMatcher.group(1);
            notifyUpdate();
        }
    }

    private void extractFromCookieHeader(String cookie) {
        Log.d(TAG, "Extracting from Cookie: " + cookie);

        extractKeyValue("access_token", cookie, value -> cookieData.accessToken = value);
        extractKeyValue("openid", cookie, value -> cookieData.openid = value);
        extractKeyValue("appOpenId", cookie, value -> cookieData.appOpenId = value);
        extractKeyValue("uin", cookie, value -> cookieData.uin = value);
        extractKeyValue("appid", cookie, value -> cookieData.appid = value);
        extractKeyValue("eas_sid", cookie, value -> cookieData.eas_sid = value);
    }

    private void extractFromSetCookie(String setCookie) {
        Log.d(TAG, "Extracting from Set-Cookie: " + setCookie);

        // Set-Cookie: access_token=xxx; path=/; expires=...
        Pattern valuePattern = Pattern.compile("([^=]+)=([^;]+)");
        Matcher matcher = valuePattern.matcher(setCookie);
        while (matcher.find()) {
            String name = matcher.group(1).trim().toLowerCase();
            String value = matcher.group(2).trim();

            switch (name) {
                case "access_token":
                    if (cookieData.accessToken.isEmpty()) cookieData.accessToken = value;
                    break;
                case "openid":
                    if (cookieData.openid.isEmpty()) cookieData.openid = value;
                    break;
                case "appopenid":
                    if (cookieData.appOpenId.isEmpty()) cookieData.appOpenId = value;
                    break;
                case "uin":
                    if (cookieData.uin.isEmpty()) cookieData.uin = value;
                    break;
                case "appid":
                    if (cookieData.appid.isEmpty()) cookieData.appid = value;
                    break;
                case "eas_sid":
                    if (cookieData.eas_sid.isEmpty()) cookieData.eas_sid = value;
                    break;
            }
        }
        notifyUpdate();
    }

    private void extractFromJson(String json) {
        Log.d(TAG, "Extracting from JSON: " + json);

        // 简单的 JSON 提取
        extractJsonValue(json, "access_token", value -> cookieData.accessToken = value);
        extractJsonValue(json, "openid", value -> cookieData.openid = value);
        extractJsonValue(json, "uin", value -> cookieData.uin = value);
        extractJsonValue(json, "userUin", value -> {
            if (cookieData.uin.isEmpty()) cookieData.uin = value;
        });
        extractJsonValue(json, "appOpenId", value -> cookieData.appOpenId = value);
        notifyUpdate();
    }

    private void extractKeyValue(String key, String source, java.util.function.Consumer<String> setter) {
        Pattern pattern = Pattern.compile(key + "=([^;\\s]+)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(source);
        if (matcher.find()) {
            setter.accept(matcher.group(1));
        }
    }

    private void extractJsonValue(String json, String key, java.util.function.Consumer<String> setter) {
        Pattern pattern = Pattern.compile("\"" + key + "\"\\s*:\\s*\"?([^\",}\\s]+)\"?");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            setter.accept(matcher.group(1));
        }
    }

    private void notifyUpdate() {
        if (listener != null) {
            listener.onCookieExtracted(cookieData);
        }
    }

    public CookieData getCookieData() {
        return cookieData;
    }

    public void reset() {
        cookieData = new CookieData();
    }
}
