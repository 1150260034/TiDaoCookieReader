package com.tidao.wuxia.app.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;
import java.util.UUID;

public class PrefsManager {
    private static final String PREFS_NAME = "tidao_prefs";
    private static final String KEY_SCKEY = "sckey";
    private static final String KEY_OWNER = "owner";
    private static final String KEY_EMAIL = "email";

    private final SharedPreferences prefs;
    private final Context context;

    public PrefsManager(Context context) {
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.context = context;
    }

    public void saveSckey(String sckey) {
        prefs.edit().putString(KEY_SCKEY, sckey).apply();
    }

    public String getSckey() {
        return prefs.getString(KEY_SCKEY, "");
    }

    public boolean hasSckey() {
        String s = getSckey();
        return s != null && !s.isEmpty() && s.startsWith("SCT") && s.length() >= 10;
    }

    public void clearSckey() {
        prefs.edit().remove(KEY_SCKEY).apply();
    }

    public void saveEmail(String email) {
        prefs.edit().putString(KEY_EMAIL, email == null ? "" : email.trim()).apply();
    }

    public String getEmail() {
        return prefs.getString(KEY_EMAIL, "");
    }

    public boolean hasEmail() {
        String value = getEmail();
        return value != null && !value.trim().isEmpty();
    }

    public void clearEmail() {
        prefs.edit().remove(KEY_EMAIL).apply();
    }

    /**
     * 获取设备唯一标识 owner（ANDROID_ID 优先，降级 UUID 持久化）
     */
    public String getOwner() {
        // 1. 优先使用 ANDROID_ID
        String androidId = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
        if (androidId != null && !androidId.isEmpty()) {
            return androidId;
        }
        // 2. 降级读取/生成 UUID
        String uuid = prefs.getString(KEY_OWNER, null);
        if (uuid == null || uuid.isEmpty()) {
            uuid = UUID.randomUUID().toString();
            prefs.edit().putString(KEY_OWNER, uuid).commit(); // 必须同步写入
        }
        return uuid;
    }
}
