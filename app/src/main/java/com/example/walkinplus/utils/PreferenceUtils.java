package com.example.walkinplus.utils;

import android.content.Context;
import android.content.SharedPreferences;

public class PreferenceUtils {

    private static final String PREFERENCE_KEY_SERIAL = "serial";

    private static Context mAppContext;

    // Prevent instantiation
    private PreferenceUtils() {
    }

    public static void init(Context appContext) {
        mAppContext = appContext;
    }

    private static SharedPreferences getSharedPreferences() {
        return mAppContext.getSharedPreferences("walkinplus", Context.MODE_PRIVATE);
    }

    public static void setSerial(String serial) {
        SharedPreferences.Editor editor = getSharedPreferences().edit();
        editor.putString(PREFERENCE_KEY_SERIAL, serial).apply();
    }

    public static String getSerial() {
        return getSharedPreferences().getString(PREFERENCE_KEY_SERIAL, "");
    }

}
