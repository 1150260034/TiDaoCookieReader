package com.tidao.wuxia.app.cookie;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

/**
 * 直接从 WebView Cookie 数据库读取 Cookie
 * 使用 su 命令访问天刀助手应用的私有数据
 *
 * 天刀助手包名: com.tencent.gamehelper.wuxia
 * Cookie数据库路径: /data/data/com.tencent.gamehelper.wuxia/app_webview/Default/Cookies
 */
public class WebViewCookieReader {
    private static final String TAG = "WebViewCookieReader";

    // 天刀助手包名
    private static final String TARGET_PACKAGE = "com.tencent.gamehelper.wuxia";

    // Cookie 数据库在目标应用中的路径
    private static final String TARGET_COOKIE_DB = "/data/data/" + TARGET_PACKAGE + "/app_webview/Default/Cookies";

    // 临时复制文件的位置
    private static final String TEMP_COOKIE_DB = "/data/local/tmp/tidao_cookies.db";

    public static class CookieData {
        public String accessToken = "";
        public String openid = "";
        public String uin = "";
        public String appid = "";
        public String acctype = "";
        public String eas_sid = "";
        public String appOpenId = "";

        public boolean isComplete() {
            return !accessToken.isEmpty()
                    && !openid.isEmpty()
                    && !uin.isEmpty();
        }

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

        @Override
        public String toString() {
            return "WebViewCookieData{" +
                    "accessToken='" + accessToken + '\'' +
                    ", openid='" + openid + '\'' +
                    ", uin='" + uin + '\'' +
                    ", appid='" + appid + '\'' +
                    ", acctype='" + acctype + '\'' +
                    ", eas_sid='" + eas_sid + '\'' +
                    ", appOpenId='" + appOpenId + '\'' +
                    '}';
        }
    }

    public interface OnCookieReadListener {
        void onCookieReadSuccess(CookieData data);
        void onCookieReadFailed(String error);
    }

    /**
     * 使用 su 命令读取天刀助手的 Cookie 数据库
     */
    public static void readCookies(Context context, OnCookieReadListener listener) {
        new Thread(() -> {
            try {
                Log.d(TAG, "开始读取 Cookie，使用 su 命令...");

                // 1. 使用 su 命令复制 Cookie 数据库到临时位置
                boolean copySuccess = copyCookieDatabaseWithSu();
                if (!copySuccess) {
                    listener.onCookieReadFailed("无法复制 Cookie 数据库，可能没有 root 权限");
                    return;
                }

                // 2. 读取复制后的数据库
                CookieData data = readCookieDatabase(context);

                // 3. 清理临时文件
                cleanupTempFile();

                if (data != null && data.isComplete()) {
                    listener.onCookieReadSuccess(data);
                } else {
                    listener.onCookieReadFailed("Cookie 不完整或读取失败");
                }

            } catch (Exception e) {
                Log.e(TAG, "读取 Cookie 失败", e);
                listener.onCookieReadFailed(e.getMessage());
            }
        }).start();
    }

    /**
     * 使用 su 命令复制 Cookie 数据库
     */
    private static boolean copyCookieDatabaseWithSu() {
        try {
            Log.d(TAG, "复制 Cookie 数据库: " + TARGET_COOKIE_DB);

            // 先确保目标目录存在
            ProcessBuilder checkDir = new ProcessBuilder(
                    "su", "-c", "ls -la /data/data/" + TARGET_PACKAGE + "/app_webview/Default/"
            );
            Process p = checkDir.start();
            int exitCode = p.waitFor();
            Log.d(TAG, "检查目录 exit code: " + exitCode);

            // 使用 cat 通过 su 复制文件
            // 注意: 需要使用 dd 或者 cat 重定向
            String copyCmd = "su -c \"cat " + TARGET_COOKIE_DB + " > " + TEMP_COOKIE_DB + " && chmod 666 " + TEMP_COOKIE_DB + "\"";

            Log.d(TAG, "执行命令: " + copyCmd);

            Process process = Runtime.getRuntime().exec(new String[]{"sh", "-c", copyCmd});
            int result = process.waitFor();

            Log.d(TAG, "复制结果 exit code: " + result);

            // 验证文件是否复制成功
            ProcessBuilder verify = new ProcessBuilder("ls", "-la", TEMP_COOKIE_DB);
            Process verifyProcess = verify.start();
            int verifyResult = verifyProcess.waitFor();

            return verifyResult == 0;

        } catch (Exception e) {
            Log.e(TAG, "复制 Cookie 数据库失败", e);
            return false;
        }
    }

    /**
     * 通过 su 命令直接读取数据库内容
     */
    private static CookieData readCookieDatabase(Context context) {
        CookieData result = new CookieData();

        try {
            // 使用 sqlite3 通过 su 读取数据
            // 但更简单的方式是：直接用 Java 读取我们复制出来的文件

            // 读取临时文件
            File tempDb = new File(TEMP_COOKIE_DB);
            if (!tempDb.exists()) {
                Log.e(TAG, "临时 Cookie 数据库文件不存在");
                return null;
            }

            Log.d(TAG, "打开临时数据库: " + tempDb.getAbsolutePath() + ", 大小: " + tempDb.length());

            // 打开只读数据库
            SQLiteDatabase db = SQLiteDatabase.openDatabase(
                    TEMP_COOKIE_DB,
                    null,
                    SQLiteDatabase.OPEN_READONLY
            );

            try {
                // 查询目标 Cookie
                String[] targetDomains = {".wuxia.qq.com", ".game.qq.com", ".qq.com"};
                StringBuilder domainCondition = new StringBuilder();
                for (int i = 0; i < targetDomains.length; i++) {
                    if (i > 0) domainCondition.append(" OR ");
                    domainCondition.append("host_key LIKE '").append(targetDomains[i]).append("'");
                }

                String query = String.format(
                        "SELECT host_key, name, value FROM cookies WHERE %s",
                        domainCondition.toString()
                );

                Log.d(TAG, "执行查询: " + query);

                java.util.HashMap<String, Map.Entry<String, String>> cookiesMap = new java.util.HashMap<>();
                android.database.Cursor cursor = db.rawQuery(query, null);

                if (cursor != null) {
                    while (cursor.moveToNext()) {
                        String hostKey = cursor.getString(0);
                        String name = cursor.getString(1);
                        String value = cursor.getString(2);

                        Log.d(TAG, String.format("Cookie: [%s] %s = %s",
                                hostKey, name,
                                value != null && value.length() > 50 ? value.substring(0, 50) + "..." : value));

                        String key = name.toLowerCase();
                        if (!cookiesMap.containsKey(key)) {
                            cookiesMap.put(key, new java.util.AbstractMap.SimpleEntry<>(name, value));
                        }
                    }
                    cursor.close();
                }

                // 提取目标 Cookie
                for (Map.Entry<String, Map.Entry<String, String>> entry : cookiesMap.entrySet()) {
                    String name = entry.getValue().getKey();
                    String value = entry.getValue().getValue();
                    String nameLower = entry.getKey();

                    switch (nameLower) {
                        case "access_token":
                        case "accesstoken":
                            if (result.accessToken.isEmpty()) {
                                result.accessToken = value;
                            }
                            break;
                        case "openid":
                        case "open_id":
                            if (result.openid.isEmpty()) {
                                result.openid = value;
                            }
                            break;
                        case "uin":
                            if (result.uin.isEmpty()) {
                                result.uin = value;
                            }
                            break;
                        case "appid":
                        case "app_id":
                            if (result.appid.isEmpty()) {
                                result.appid = value;
                            }
                            break;
                        case "acctype":
                        case "acc_type":
                            if (result.acctype.isEmpty()) {
                                result.acctype = value;
                            }
                            break;
                        case "eas_sid":
                            if (result.eas_sid.isEmpty()) {
                                result.eas_sid = value;
                            }
                            break;
                        case "appopenid":
                        case "app_openid":
                            if (result.appOpenId.isEmpty()) {
                                result.appOpenId = value;
                            }
                            break;
                    }
                }

                Log.d(TAG, "读取结果: " + result);

            } finally {
                db.close();
            }

        } catch (Exception e) {
            Log.e(TAG, "读取数据库失败", e);
            return null;
        }

        return result;
    }

    /**
     * 清理临时文件
     */
    private static void cleanupTempFile() {
        try {
            ProcessBuilder cleanup = new ProcessBuilder("rm", "-f", TEMP_COOKIE_DB);
            Process p = cleanup.start();
            p.waitFor();
            Log.d(TAG, "临时文件已清理");
        } catch (Exception e) {
            Log.e(TAG, "清理临时文件失败", e);
        }
    }

    /**
     * 同步读取 Cookie（阻塞）
     */
    public static CookieData readCookiesSync(Context context) throws Exception {
        final Object lock = new Object();
        final CookieData[] result = new CookieData[1];
        final String[] error = new String[1];

        readCookies(context, new OnCookieReadListener() {
            @Override
            public void onCookieReadSuccess(CookieData data) {
                result[0] = data;
                synchronized (lock) {
                    lock.notify();
                }
            }

            @Override
            public void onCookieReadFailed(String err) {
                error[0] = err;
                synchronized (lock) {
                    lock.notify();
                }
            }
        });

        synchronized (lock) {
            lock.wait(15000); // 15秒超时
        }

        if (error[0] != null) {
            throw new Exception(error[0]);
        }

        return result[0];
    }
}
