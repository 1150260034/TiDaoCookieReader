package com.tidao.wuxia.app.net;

import android.os.Handler;
import android.util.Log;
import com.tidao.wuxia.app.BuildConfig;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public final class FcUploader {

    private static final String TAG = "FcUploader";

    public static final int ERROR_ROLE_CONFLICT = 1001;

    private static final int MAX_LOG_BODY_LEN = 200;

    private FcUploader() {}

    /** 账号名脱敏：仅保留首字符 + *** */
    private static String maskName(String name) {
        if (name == null || name.isEmpty()) return "***";
        return name.charAt(0) + "***";
    }

    /** 截断响应体，避免日志泄露敏感数据 */
    private static String truncateBody(String text) {
        if (text == null) return "";
        if (text.length() <= MAX_LOG_BODY_LEN) return text;
        return text.substring(0, MAX_LOG_BODY_LEN) + "...(truncated)";
    }

    public interface UploadCallback {
        void onSuccess(String status, String name);
        void onFailed(int errorCode, String message);
    }

    public static void upload(String accountName, String cookieString,
                              JSONObject roleParams, String sckey, String owner, String email,
                              Handler mainHandler, UploadCallback callback) {
        doUpload(accountName, cookieString, roleParams, sckey, owner, email,
                false, "上传", mainHandler, callback);
    }

    /**
     * 与 {@link #upload} 完全一致，额外在请求体中附加 overwrite=true。
     */
    public static void uploadWithOverwrite(String accountName, String cookieString,
                              JSONObject roleParams, String sckey, String owner, String email,
                              Handler mainHandler, UploadCallback callback) {
        doUpload(accountName, cookieString, roleParams, sckey, owner, email,
                true, "覆盖上传", mainHandler, callback);
    }

    private static void doUpload(String accountName, String cookieString,
                                  JSONObject roleParams, String sckey, String owner, String email,
                                  boolean overwrite, String logLabel,
                                  Handler mainHandler, UploadCallback callback) {
        new Thread(() -> {
            if (BuildConfig.DEBUG) Log.d(TAG, "开始" + logLabel + ": account=" + maskName(accountName));
            HttpURLConnection conn = null;
            try {
                JSONObject body = buildUploadBody(accountName, cookieString, roleParams, sckey, owner, email);
                if (overwrite) {
                    body.put("overwrite", true);
                }

                byte[] postData = body.toString().getBytes("UTF-8");

                URL url = new URL(BuildConfig.UPLOAD_COOKIE_URL);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                conn.setRequestProperty("Accept", "application/json");
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);
                conn.setDoOutput(true);

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(postData);
                }

                int code = conn.getResponseCode();
                if (BuildConfig.DEBUG) Log.d(TAG, "响应: HTTP " + code);
                java.io.InputStream is = (code >= 200 && code < 300)
                        ? conn.getInputStream() : conn.getErrorStream();
                StringBuilder sb = new StringBuilder();
                if (is != null) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line);
                    reader.close();
                }
                String respBody = sb.toString();
                if (BuildConfig.DEBUG) Log.d(TAG, "响应体: " + truncateBody(respBody));

                if (code == 200) {
                    JSONObject resp = new JSONObject(respBody);
                    String status = resp.optString("status", "");
                    String name = resp.optString("name", accountName);
                    Log.i(TAG, logLabel + "成功: status=" + status + ", name=" + maskName(name));
                    mainHandler.post(() -> {
                        if (callback != null) callback.onSuccess(status, name);
                    });
                } else if (code == 403) {
                    Log.e(TAG, "认证失败: HTTP 403, body=" + truncateBody(respBody));
                    postFailed(mainHandler, callback, 0, "认证失败（unauthorized）");
                } else if (code == 400) {
                    Log.e(TAG, "参数错误: HTTP 400, body=" + truncateBody(respBody));
                    postFailed(mainHandler, callback, 0, "请求参数错误：" + truncateBody(respBody));
                } else if (code == 409) {
                    handle409Conflict(respBody, mainHandler, callback);
                } else {
                    Log.e(TAG, logLabel + "失败: HTTP " + code + ", body=" + truncateBody(respBody));
                    postFailed(mainHandler, callback, 0, logLabel + "失败（HTTP " + code + "）：" + truncateBody(respBody));
                }
            } catch (Exception e) {
                Log.e(TAG, logLabel + "异常", e);
                postFailed(mainHandler, callback, 0, logLabel + "异常：" + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
            } finally {
                if (conn != null) conn.disconnect();
            }
        }).start();
    }

    private static void handle409Conflict(String respBody, Handler mainHandler, UploadCallback callback) {
        String detail = "一个QQ号只能绑定一个角色";
        try {
            JSONObject errorJson = new JSONObject(respBody);
            detail = errorJson.optString("detail", detail);
        } catch (Exception ignored) {}
        Log.w(TAG, "角色冲突: HTTP 409, detail=" + detail);
        mainHandler.post(() -> {
            if (callback != null) callback.onFailed(ERROR_ROLE_CONFLICT, "角色冲突：" + detail);
        });
    }

    static JSONObject buildUploadBody(String accountName, String cookieString,
                                      JSONObject roleParams, String sckey,
                                      String owner, String email) throws Exception {
        JSONObject body = new JSONObject();
        body.put("name", accountName);
        body.put("cookies", cookieString);
        body.put("role_params", roleParams);
        body.put("sckey", sckey);
        body.put("owner", owner);
        body.put("email", email == null ? "" : email.trim());
        return body;
    }

    private static void postFailed(Handler mainHandler, UploadCallback callback, int errorCode, String msg) {
        mainHandler.post(() -> {
            if (callback != null) callback.onFailed(errorCode, msg);
        });
    }
}
