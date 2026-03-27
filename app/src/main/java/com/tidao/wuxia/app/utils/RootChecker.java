package com.tidao.wuxia.app.utils;

import android.content.Context;
import android.util.Log;

/**
 * Root权限检测工具
 */
public class RootChecker {
    private static final String TAG = "RootChecker";

    /**
     * 检测设备是否已Root
     */
    public static boolean isDeviceRooted() {
        // 方法1: 检查su命令是否存在
        if (checkSuExists()) {
            return true;
        }

        // 方法2: 检查常见的root相关路径
        if (checkRootPaths()) {
            return true;
        }

        // 方法3: 检查test-keys签名（常见于root过的设备）
        if (checkTestKeys()) {
            return true;
        }

        return false;
    }

    /**
     * 检查su命令是否存在
     */
    private static boolean checkSuExists() {
        String[] paths = {
            "/system/bin/su",
            "/system/xbin/su",
            "/vendor/bin/su",
            "/sbin/su",
            "/system/su"
        };

        for (String path : paths) {
            if (fileExists(path)) {
                Log.d(TAG, "Found su at: " + path);
                return true;
            }
        }
        return false;
    }

    /**
     * 检查常见的root相关文件/目录
     */
    private static boolean checkRootPaths() {
        String[] paths = {
            "/system/app/Superuser.apk",
            "/system/app/SuperSU",
            "/system/xbin/daemonsu",
            "/system/etc/init.d/99SuperSUDaemon",
            "/sbin/.supersu",
            "/system/bin/.ext",
            "/system/etc/.hasdat"
        };

        for (String path : paths) {
            if (fileExists(path)) {
                Log.d(TAG, "Found root path: " + path);
                return true;
            }
        }
        return false;
    }

    /**
     * 检查系统签名是否为test-keys（通常表示已root或自定义ROM）
     */
    private static boolean checkTestKeys() {
        try {
            String buildTags = android.os.Build.TAGS;
            if (buildTags != null && buildTags.contains("test-keys")) {
                Log.d(TAG, "Found test-keys in build tags");
                return true;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking build tags", e);
        }
        return false;
    }

    /**
     * 检查文件是否存在
     */
    private static boolean fileExists(String path) {
        try {
            java.io.File file = new java.io.File(path);
            return file.exists();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 检测Superuser管理应用是否存在
     */
    public static boolean hasSuperUserApp(Context context) {
        String[] packages = {
            "com.noshufou.android.su",
            "com.noshufou.android.su.elite",
            "eu.chainfire.supersu",
            "com.koushikdutta.superuser",
            "com.thirdparty.superuser",
            "com.yellowes.su"
        };

        try {
            android.content.pm.PackageManager pm = context.getPackageManager();
            for (String pkg : packages) {
                try {
                    android.content.pm.PackageInfo pi = pm.getPackageInfo(pkg, 0);
                    if (pi != null) {
                        Log.d(TAG, "Found SuperUser app: " + pkg);
                        return true;
                    }
                } catch (Exception e) {
                    // Package not found, continue
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking SuperUser apps", e);
        }
        return false;
    }
}
