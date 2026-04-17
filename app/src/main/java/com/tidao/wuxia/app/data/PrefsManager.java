package com.tidao.wuxia.app.data;

import android.content.Context;
import android.content.SharedPreferences;

public class PrefsManager {
    private static final String PREFS_NAME = "tidao_prefs";
    private static final String KEY_SCKEY = "sckey";

    private final SharedPreferences prefs;

    public PrefsManager(Context context) {
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
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
}
