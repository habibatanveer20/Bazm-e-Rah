package com.example.bazmeraah;

import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

public class BaseActivity extends AppCompatActivity {

    private static boolean isThemeChanging = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        // ⚠️ Theme apply karo BEFORE super.onCreate() to avoid flicker
        SharedPreferences prefs = getSharedPreferences("AppSettings", MODE_PRIVATE);
        boolean isDarkMode = prefs.getBoolean("isDarkMode", false);

        if (isDarkMode) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        }

        super.onCreate(savedInstanceState);
    }

    // ✅ Naya method theme change track karne ke liye
    public static void setThemeChanging(boolean changing) {
        isThemeChanging = changing;
    }

    // ✅ Check karne ka method
    public static boolean isThemeChanging() {
        return isThemeChanging;
    }

    @Override
    protected void onResume() {
        super.onResume();

        // ✅ Agar theme change ho raha hai toh flag reset karo
        if (isThemeChanging) {
            isThemeChanging = false;
        }
    }
    // ✅ Method to prevent recreation on theme change
    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // ✅ Theme change par activity ko recreate hone se roko
        // Yeh method activity ko recreate hone se bachayega
    }
}