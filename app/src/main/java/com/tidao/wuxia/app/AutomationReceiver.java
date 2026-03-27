package com.tidao.wuxia.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;

import com.tidao.wuxia.app.cookie.CookieExtractor;
import com.tidao.wuxia.app.cookie.GameDatabaseReader;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

/**
 * 自动化测试广播接收器
 * 接收 ADB 广播触发 Cookie 读取、复制、检测功能
 *
 * 支持的 Intent Action:
 * - com.tidao.wuxia.app.action.READ_COOKIE      // 读取 Cookie
 * - com.tidao.wuxia.app.action.COPY_ALL          // 复制全部
 * - com.tidao.wuxia.app.action.CHECK_WELFARE     // 检测每日福利
 * - com.tidao.wuxia.app.action.GET_STATUS        // 获取状态
 */
public class AutomationReceiver extends BroadcastReceiver {
    private static final String TAG = "TidaoResult";
    private static final String ACTION_PREFIX = "com.tidao.wuxia.app.action.";

    // Action 常量
    public static final String ACTION_READ_COOKIE = ACTION_PREFIX + "READ_COOKIE";
    public static final String ACTION_COPY_ALL = ACTION_PREFIX + "COPY_ALL";
    public static final String ACTION_CHECK_WELFARE = ACTION_PREFIX + "CHECK_WELFARE";
    public static final String ACTION_GET_STATUS = ACTION_PREFIX + "GET_STATUS";

    // 单例访问接口
    private static AutomationListener listener;

    public interface AutomationListener {
        Context getContext();
        CookieExtractor.CookieData getCookieData();
        GameDatabaseReader.RoleInfo getRoleInfo();
        String getDailyWelfareCheckResult();
        void performReadCookie();
        void performCopyAll();
        void performCheckWelfare();
    }

    public static void setListener(AutomationListener l) {
        listener = l;
    }

    public static void clearListener() {
        listener = null;
    }

    /**
     * 注册广播接收器
     */
    public static IntentFilter getIntentFilter() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_READ_COOKIE);
        filter.addAction(ACTION_COPY_ALL);
        filter.addAction(ACTION_CHECK_WELFARE);
        filter.addAction(ACTION_GET_STATUS);
        return filter;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (listener == null) {
            logResult(context, "error", "AutomationReceiver not initialized");
            return;
        }

        String action = intent.getAction();
        if (action == null) {
            logResult(context, "error", "No action specified");
            return;
        }

        switch (action) {
            case ACTION_READ_COOKIE:
                handleReadCookie(context);
                break;
            case ACTION_COPY_ALL:
                handleCopyAll(context);
                break;
            case ACTION_CHECK_WELFARE:
                handleCheckWelfare(context);
                break;
            case ACTION_GET_STATUS:
                handleGetStatus(context);
                break;
            default:
                logResult(context, "error", "Unknown action: " + action);
        }
    }

    private void handleReadCookie(Context context) {
        logResult(context, "status", "Reading cookie...");
        listener.performReadCookie();
    }

    private void handleCopyAll(Context context) {
        logResult(context, "status", "Copying all...");
        listener.performCopyAll();
    }

    private void handleCheckWelfare(Context context) {
        logResult(context, "status", "Checking welfare...");
        listener.performCheckWelfare();
    }

    private void handleGetStatus(Context context) {
        try {
            JSONObject result = new JSONObject();

            if (listener.getCookieData() != null) {
                CookieExtractor.CookieData cookie = listener.getCookieData();
                JSONObject cookieJson = new JSONObject();
                cookieJson.put("hasAccessToken", !cookie.accessToken.isEmpty());
                cookieJson.put("hasOpenid", !cookie.openid.isEmpty());
                cookieJson.put("hasUin", !cookie.uin.isEmpty());
                cookieJson.put("isComplete", cookie.isComplete());
                cookieJson.put("accessTokenPreview", cookie.accessToken.length() > 10 ?
                        cookie.accessToken.substring(0, 10) + "..." : cookie.accessToken);
                result.put("cookie", cookieJson);
            }

            if (listener.getRoleInfo() != null) {
                GameDatabaseReader.RoleInfo role = listener.getRoleInfo();
                JSONObject roleJson = new JSONObject();
                roleJson.put("hasPlayername", !role.playername.isEmpty());
                roleJson.put("hasRoleid", !role.roleid.isEmpty());
                roleJson.put("hasArea", !role.area.isEmpty());
                roleJson.put("playername", role.playername);
                roleJson.put("roleid", role.roleid);
                roleJson.put("area", role.area);
                roleJson.put("areaName", role.areaName);
                roleJson.put("isComplete", role.isComplete());
                result.put("role", roleJson);
            }

            result.put("dailyWelfareCheckResult", listener.getDailyWelfareCheckResult() != null ?
                    listener.getDailyWelfareCheckResult() : "");

            result.put("status", "ok");
            result.put("timestamp", System.currentTimeMillis());

            logResult(context, "status", result.toString());
            writeResultFile(context, result.toString());

        } catch (JSONException e) {
            logResult(context, "error", "JSON error: " + e.getMessage());
        }
    }

    /**
     * 输出结果到 Logcat
     */
    private void logResult(Context context, String type, String message) {
        String logMessage = String.format("[%s] %s", type.toUpperCase(), message);
        Log.i(TAG, logMessage);
    }

    /**
     * 写入结果文件
     */
    private void writeResultFile(Context context, String json) {
        try {
            File cacheDir = context.getCacheDir();
            File resultFile = new File(cacheDir, "result.txt");
            FileWriter writer = new FileWriter(resultFile);
            writer.write(json);
            writer.flush();
            writer.close();
            Log.d(TAG, "Result written to: " + resultFile.getAbsolutePath());
        } catch (IOException e) {
            Log.e(TAG, "Failed to write result file: " + e.getMessage());
        }
    }
}
