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

    private FcUploader() {}

    public interface UploadCallback {
        void onSuccess(String status, String name);
        void onFailed(String message);
    }

    public static void upload(String accountName, String cookieString,
                              JSONObject roleParams, String sckey,
                              Handler mainHandler, UploadCallback callback) {
        new Thread(() -> {
            Log.d(TAG, "开始上传: account=" + accountName + ", url=" + BuildConfig.UPLOAD_COOKIE_URL);
            HttpURLConnection conn = null;
            try {
                JSONObject body = new JSONObject();
                body.put("auth_token", BuildConfig.AUTH_TOKEN);
                body.put("name", accountName);
                body.put("cookies", cookieString);
                body.put("role_params", roleParams);
                body.put("sckey", sckey);

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
                Log.d(TAG, "响应: HTTP " + code);
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
                Log.d(TAG, "响应体: " + respBody);

                if (code == 200) {
                    JSONObject resp = new JSONObject(respBody);
                    String status = resp.optString("status", "");
                    String name = resp.optString("name", accountName);
                    Log.i(TAG, "上传成功: status=" + status + ", name=" + name);
                    mainHandler.post(() -> {
                        if (callback != null) callback.onSuccess(status, name);
                    });
                } else if (code == 403) {
                    Log.e(TAG, "认证失败: HTTP 403, body=" + respBody);
                    postFailed(mainHandler, callback, "认证失败（unauthorized）");
                } else if (code == 400) {
                    Log.e(TAG, "参数错误: HTTP 400, body=" + respBody);
                    postFailed(mainHandler, callback, "请求参数错误：" + respBody);
                } else {
                    Log.e(TAG, "上传失败: HTTP " + code + ", body=" + respBody);
                    postFailed(mainHandler, callback, "上传失败（HTTP " + code + "）：" + respBody);
                }
            } catch (Exception e) {
                Log.e(TAG, "上传异常", e);
                postFailed(mainHandler, callback, "上传异常：" + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
            } finally {
                if (conn != null) conn.disconnect();
            }
        }).start();
    }

    private static void postFailed(Handler mainHandler, UploadCallback callback, String msg) {
        mainHandler.post(() -> {
            if (callback != null) callback.onFailed(msg);
        });
    }
}
