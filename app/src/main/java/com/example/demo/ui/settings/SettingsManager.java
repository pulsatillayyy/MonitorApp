package com.example.demo.ui.settings;

import android.content.Context;
import android.content.SharedPreferences;

public class SettingsManager {
    private static final String PREF_NAME = "cine_monitor_prefs";
    private static final String KEY_RECORD_WITH_LUT = "record_with_lut";
    
    private final SharedPreferences prefs;
    
    public SettingsManager(Context context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }
    
    public boolean isRecordWithLut() {
        return prefs.getBoolean(KEY_RECORD_WITH_LUT, true); // 默认开启
    }
    
    public void setRecordWithLut(boolean enable) {
        prefs.edit().putBoolean(KEY_RECORD_WITH_LUT, enable).apply();
    }
}
