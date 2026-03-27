package com.tidao.wuxia.app.cookie;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 检查账号是否已绑定游戏角色
 * 通过调用 974294 登录验证接口来判断
 */
public class BindingChecker {
    private static final String TAG = "BindingChecker";

    // AMS API 地址
    private static final String AMS_API = "https://comm.ams.game.qq.com/ams/ame/amesvr";

    // 每日福利活动 ID
    private static final String ACTIVITY_ID = "579009";

    // 登录验证 FlowID
    private static final String FLOW_ID_LOGIN = "974294";

    // SDID
    private static final String SDID = "268ff4a4b5606e39782f03601660df78";

    public interface OnBindingCheckListener {
        void onBindingCheckSuccess(boolean isBound, String area, String areaName, String roleName);
        void onBindingCheckFailed(String error, boolean isCookieExpired);
    }

    /**
     * 检查账号是否已绑定角色
     * @param context Android上下文
     * @param cookieData Cookie数据
     */
    public static void checkBindingStatus(Context context, CookieExtractor.CookieData cookieData) {
        new Thread(() -> {
            try {
                Log.d(TAG, "开始检查绑定状态...");

                // 构建请求
                String response = sendLoginRequest(cookieData);

                // 解析响应
                parseAndReportResult(response);

            } catch (Exception e) {
                Log.e(TAG, "检查绑定状态失败", e);
                reportError(e.getMessage());
            }
        }).start();
    }

    private static String sendLoginRequest(CookieExtractor.CookieData cookieData) throws Exception {
        String timestamp = String.valueOf(System.currentTimeMillis());
        String randomSuffix = UUID.randomUUID().toString().substring(0, 7);

        // 生成 sMiloTag
        String smiloTag = String.format("AMS-MILO-%s-%s-%s-%s-%s",
                ACTIVITY_ID, FLOW_ID_LOGIN, cookieData.openid, timestamp, randomSuffix);

        // 构建完整的URL（带查询参数）
        String urlStr = String.format("%s?ameVersion=0.3&sServiceType=wuxia&iActivityId=%s&sServiceDepartment=group_9&sSDID=%s&sMiloTag=%s&_=%s",
                AMS_API, ACTIVITY_ID, SDID, URLEncoder.encode(smiloTag, "UTF-8"), timestamp);

        Log.d(TAG, "请求URL: " + urlStr);

        // 构建POST参数
        Map<String, String> params = new HashMap<>();
        params.put("appid", cookieData.appid.isEmpty() ? "1104787345" : cookieData.appid);
        params.put("sServiceType", "wuxia");
        params.put("iActivityId", ACTIVITY_ID);
        params.put("iFlowId", FLOW_ID_LOGIN);
        params.put("g_tk", cookieData.gtk.isEmpty() ? "1842395457" : cookieData.gtk);
        params.put("e_code", "0");
        params.put("g_code", "0");
        params.put("eas_url", "http://wuxia.qq.com/lbact/a20230821lbapqam/flttl.html");
        params.put("eas_refer", "http://noreferrer/?reqid=" + timestamp + "&version=27");
        params.put("sServiceDepartment", "group_9");
        params.put("openid", cookieData.openid);
        params.put("openkey", cookieData.accessToken);
        params.put("ext", "{\"height\":2668,\"dpr\":1.1,\"client\":\"Android\",\"osVersion\":\"13\",\"channel\":\"tencent\",\"model\":\"Pixel 6 Pro\",\"deviceId\":\"\",\"appVersion\":\"1.3.38.463\",\"networkType\":\"wifi\",\"isTransformedApk\":\"false\",\"installSource\":\"com.tencent.gamehelper.wuxia\",\"batchType\":\"old\"}");

        // 发送请求
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();

        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setDoInput(true);
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(15000);
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 16; 23116PN5BC Build/BP2A.250605.031.A3; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/137.0.7151.115 Mobile Safari/537.36 GH_QQConnect GameHelper_1012/2103100009");
        conn.setRequestProperty("Accept", "application/json, text/plain, */*");
        conn.setRequestProperty("Origin", "https://wuxia.qq.com");
        conn.setRequestProperty("Referer", "https://wuxia.qq.com/lbact/a20230821lbapqam/flttl.html");
        conn.setRequestProperty("X-Requested-With", "com.tencent.gamehelper.wuxia");
        conn.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9,en-US;q=0.8,en;q=0.7");
        conn.setRequestProperty("Cookie", buildCookieString(cookieData));

        // 写入参数
        StringBuilder postData = new StringBuilder();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (postData.length() > 0) postData.append("&");
            postData.append(URLEncoder.encode(entry.getKey(), "UTF-8"));
            postData.append("=");
            postData.append(URLEncoder.encode(entry.getValue(), "UTF-8"));
        }

        Log.d(TAG, "POST数据: " + postData.toString());

        OutputStream os = conn.getOutputStream();
        os.write(postData.toString().getBytes("UTF-8"));
        os.flush();
        os.close();

        // 读取响应
        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
        StringBuilder response = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            response.append(line);
        }
        reader.close();
        conn.disconnect();

        Log.d(TAG, "API响应: " + response.toString());
        return response.toString();
    }

    private static String buildCookieString(CookieExtractor.CookieData cookieData) {
        StringBuilder sb = new StringBuilder();
        if (!cookieData.accessToken.isEmpty()) sb.append("access_token=").append(cookieData.accessToken).append("; ");
        if (!cookieData.openid.isEmpty()) sb.append("openid=").append(cookieData.openid).append("; ");
        if (!cookieData.openid.isEmpty()) sb.append("appOpenId=").append(cookieData.openid).append("; ");
        if (!cookieData.openid.isEmpty()) sb.append("appOpenid=").append(cookieData.openid).append("; ");
        if (!cookieData.uin.isEmpty()) sb.append("uin=").append(cookieData.uin).append("; ");
        if (!cookieData.appid.isEmpty()) sb.append("appid=").append(cookieData.appid).append("; ");
        if (!cookieData.appid.isEmpty()) sb.append("appId=").append(cookieData.appid).append("; ");
        if (!cookieData.eas_sid.isEmpty()) sb.append("eas_sid=").append(cookieData.eas_sid).append("; ");
        if (!cookieData.accessToken.isEmpty()) sb.append("accessToken=").append(cookieData.accessToken);
        return sb.toString().trim();
    }

    private static void parseAndReportResult(String response) {
        try {
            // 直接在response中检测iRet状态码
            if (response.contains("\"iRet\":\"3\"")) {
                String msg = extractJsonField(response, "msg");
                Log.d(TAG, "API错误(参数错误): " + msg);
                reportError("API错误: " + msg);
                return;
            }

            // 检查iRet=101 (未登录/cookie失效)
            if (response.contains("\"iRet\":\"101\"")) {
                String msg = extractJsonField(response, "msg");
                Log.d(TAG, "Cookie失效或未登录: " + msg);
                reportError("Cookie失效，请重新获取", true);
                return;
            }

            // 提取 modRet.jData
            // JSON结构: {"modRet": {"jData": {"data": [...]}}}
            String modRet = extractJsonBlock(response, "modRet");
            if (modRet == null) {
                Log.d(TAG, "未找到modRet，视为未绑定");
                reportSuccess(false, null, null, null);
                return;
            }

            String jData = extractJsonBlock(modRet, "jData");
            if (jData == null || jData.isEmpty() || jData.equals("{}") || jData.equals("[]")) {
                Log.d(TAG, "jData为空，账号未绑定角色");
                reportSuccess(false, null, null, null);
                return;
            }

            // jData.data 是角色信息
            String data = extractJsonBlock(jData, "data");
            if (data == null || data.isEmpty() || data.equals("[]")) {
                Log.d(TAG, "jData.data为空，账号未绑定角色");
                reportSuccess(false, null, null, null);
                return;
            }

            // 如果data是数组，取第一个元素
            if (data.startsWith("[")) {
                data = extractArrayFirstElement(data);
            }

            // 提取角色信息
            String roleName = extractJsonField(data, "playername");
            String area = extractJsonField(data, "Farea");
            String areaName = extractJsonField(data, "areaName");

            if (roleName != null && !roleName.isEmpty()) {
                Log.d(TAG, "账号已绑定角色: " + roleName + ", area: " + area);
                reportSuccess(true, area, areaName, roleName);
            } else {
                Log.d(TAG, "无法获取角色信息，视为未绑定");
                reportSuccess(false, null, null, null);
            }

        } catch (Exception e) {
            Log.e(TAG, "解析响应失败", e);
            reportError(e.getMessage());
        }
    }

    private static String extractJsonField(String json, String field) {
        try {
            // 匹配 "field":"value" 或 "field":"value"
            String pattern = "\"" + field + "\"\\s*:\\s*\"?([^\",}\\s]+)\"?";
            java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
            java.util.regex.Matcher m = p.matcher(json);
            if (m.find()) {
                return m.group(1);
            }
        } catch (Exception e) {
            Log.e(TAG, "提取字段失败: " + field, e);
        }
        return null;
    }

    private static String extractJsonBlock(String json, String blockName) {
        try {
            // 匹配 "blockName": {...}
            String pattern = "\"" + blockName + "\"\\s*:\\s*\\{([^\\}]*\\})?";
            java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
            java.util.regex.Matcher m = p.matcher(json);
            if (m.find()) {
                String block = m.group(0);
                // 找到完整的块
                int start = block.indexOf('{');
                int braceCount = 0;
                int end = start;
                for (int i = start; i < json.length(); i++) {
                    if (json.charAt(i) == '{') braceCount++;
                    else if (json.charAt(i) == '}') {
                        braceCount--;
                        if (braceCount == 0) {
                            end = i + 1;
                            break;
                        }
                    }
                }
                return json.substring(start, end);
            }
        } catch (Exception e) {
            Log.e(TAG, "提取块失败: " + blockName, e);
        }
        return null;
    }

    private static String extractArrayFirstElement(String json) {
        try {
            // 找第一个 { ... } 块
            int start = json.indexOf('{');
            if (start < 0) return json;

            int braceCount = 0;
            int end = start;
            for (int i = start; i < json.length(); i++) {
                char c = json.charAt(i);
                if (c == '{') braceCount++;
                else if (c == '}') {
                    braceCount--;
                    if (braceCount == 0) {
                        end = i + 1;
                        break;
                    }
                }
            }
            return json.substring(start, end);
        } catch (Exception e) {
            return json;
        }
    }

    private static void reportSuccess(boolean isBound, String area, String areaName, String roleName) {
        new Handler(Looper.getMainLooper()).post(() -> {
            if (listener != null) {
                listener.onBindingCheckSuccess(isBound, area, areaName, roleName);
            }
        });
    }

    private static void reportError(String error) {
        reportError(error, false);
    }

    private static void reportError(String error, boolean isCookieExpired) {
        new Handler(Looper.getMainLooper()).post(() -> {
            if (listener != null) {
                listener.onBindingCheckFailed(error, isCookieExpired);
            }
        });
    }

    private static OnBindingCheckListener listener;

    public static void setOnBindingCheckListener(OnBindingCheckListener l) {
        listener = l;
    }
}