package com.tidao.wuxia.app.net;

import android.app.AlertDialog;
import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ProgressBar;

import com.tidao.wuxia.app.data.PrefsManager;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

public final class ServerChanBinder {
    private static final String BIND_URL = "https://sct.ftqq.com/appkey/create/forward?name=%E5%A4%A9%E5%88%80%E8%87%AA%E5%8A%A8%E7%AD%BE%E5%88%B0&url=tidao%3A%2F%2Fserverchan%2Fcallback%3Fsckey%3D%7Bappkey%7D";

    private ServerChanBinder() {}

    public interface BindCallback {
        void onSuccess(String sckey);
        void onFailed(String reason);
        void onCancelled();
    }

    public static void startBinding(Context context, PrefsManager prefsManager,
                                    Handler mainHandler, BindCallback callback) {
        FrameLayout container = new FrameLayout(context);
        FrameLayout.LayoutParams fill = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        WebView webView = new WebView(context);
        webView.setLayoutParams(fill);
        ProgressBar progressBar = new ProgressBar(context);
        progressBar.setIndeterminate(true);
        progressBar.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        container.addView(webView);
        container.addView(progressBar);

        final AlertDialog[] dialogHolder = new AlertDialog[1];
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if (!"tidao".equalsIgnoreCase(uri.getScheme())) {
                    progressBar.setVisibility(View.VISIBLE);
                    return false;
                }
                String sckey = uri.getQueryParameter("sckey");
                if (dialogHolder[0] != null && dialogHolder[0].isShowing()) {
                    dialogHolder[0].dismiss();
                }
                if (isBlank(sckey)) {
                    postFailed(mainHandler, callback, "未获取到 SendKey");
                    return true;
                }
                validateSckeyAsync(sckey.trim(), prefsManager, mainHandler, callback);
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                progressBar.setVisibility(View.GONE);
            }
        });

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle("绑定 Server酱")
                .setView(container)
                .setCancelable(false)
                .setNegativeButton("取消绑定", (d, which) -> postCancelled(mainHandler, callback))
                .create();
        dialogHolder[0] = dialog;
        dialog.setOnDismissListener(d -> {
            webView.stopLoading();
            webView.destroy();
        });
        dialog.show();
        webView.loadUrl(BIND_URL);
    }

    private static void validateSckeyAsync(String sckey, PrefsManager prefsManager,
                                           Handler mainHandler, BindCallback callback) {
        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL("https://sctapi.ftqq.com/" + sckey + ".send");
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(15000);
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

                String body = "title=" + encode("绑定验证") + "&desp=" + encode("天刀签到助手绑定成功");
                OutputStream outputStream = connection.getOutputStream();
                outputStream.write(body.getBytes("UTF-8"));
                outputStream.close();

                InputStream stream = connection.getResponseCode() >= 400
                        ? connection.getErrorStream() : connection.getInputStream();
                String response = readAll(stream);
                JSONObject json = new JSONObject(response);
                if (json.optInt("code", -1) == 0) {
                    prefsManager.saveSckey(sckey);
                    mainHandler.post(() -> {
                        if (callback != null) {
                            callback.onSuccess(sckey);
                        }
                    });
                    return;
                }
                String reason = json.optString("message");
                if (isBlank(reason)) {
                    reason = json.optJSONObject("data") != null
                            ? json.optJSONObject("data").optString("error") : "SendKey 验证失败";
                }
                postFailed(mainHandler, callback, isBlank(reason) ? "SendKey 验证失败" : reason);
            } catch (Exception e) {
                postFailed(mainHandler, callback, isBlank(e.getMessage()) ? "网络错误" : e.getMessage());
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }).start();
    }

    private static String readAll(InputStream stream) throws Exception {
        if (stream == null) {
            return "";
        }
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream, "UTF-8"));
        StringBuilder builder = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            builder.append(line);
        }
        reader.close();
        return builder.toString();
    }

    private static String encode(String value) throws Exception {
        return URLEncoder.encode(value, "UTF-8");
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static void postFailed(Handler mainHandler, BindCallback callback, String reason) {
        mainHandler.post(() -> {
            if (callback != null) {
                callback.onFailed(reason);
            }
        });
    }

    private static void postCancelled(Handler mainHandler, BindCallback callback) {
        mainHandler.post(() -> {
            if (callback != null) {
                callback.onCancelled();
            }
        });
    }
}
