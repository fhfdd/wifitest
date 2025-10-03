package com.example.mywifiscanner;

import android.content.Context;
import android.content.SharedPreferences;

public class ConfigManager {
    private static final String PREFS_NAME = "WifiScannerConfig";
    private static final String KEY_SCAN_COUNT = "scan_count";
    private static final String KEY_SCAN_INTERVAL = "scan_interval";
    private static final String KEY_WIFI_THRESHOLD = "wifi_threshold";
    private static final String KEY_SIMILARITY_THRESHOLD = "similarity_threshold";
    private static final String KEY_GRID_ENABLED = "grid_enabled";
    private static final String KEY_GRID_SIZE = "grid_size";

    private final SharedPreferences prefs;

    public ConfigManager(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        initDefaultValues();
    }

    private void initDefaultValues() {
        if (!prefs.contains(KEY_SCAN_COUNT)) {
            restoreDefaultSettings();
        }
    }

    // 扫描设置
    public int getScanCount() {
        return prefs.getInt(KEY_SCAN_COUNT, 3);
    }

    public void setScanCount(int count) {
        prefs.edit().putInt(KEY_SCAN_COUNT, count).apply();
    }

    public int getScanInterval() {
        return prefs.getInt(KEY_SCAN_INTERVAL, 3000);
    }

    public void setScanInterval(int intervalMs) {
        prefs.edit().putInt(KEY_SCAN_INTERVAL, intervalMs).apply();
    }

    // WiFi过滤设置
    public int getWifiThreshold() {
        return prefs.getInt(KEY_WIFI_THRESHOLD, -85);
    }

    public void setWifiThreshold(int threshold) {
        prefs.edit().putInt(KEY_WIFI_THRESHOLD, threshold).apply();
    }

    // 相似度阈值
    public double getSimilarityThreshold() {
        return prefs.getFloat(KEY_SIMILARITY_THRESHOLD, 0.6f);
    }

    public void setSimilarityThreshold(double threshold) {
        prefs.edit().putFloat(KEY_SIMILARITY_THRESHOLD, (float) threshold).apply();
    }

    // 网格设置
    public boolean isGridEnabled() {
        return prefs.getBoolean(KEY_GRID_ENABLED, false);
    }

    public void setGridEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_GRID_ENABLED, enabled).apply();
    }

    public int getGridSize() {
        return prefs.getInt(KEY_GRID_SIZE, 50);
    }

    public void setGridSize(int size) {
        prefs.edit().putInt(KEY_GRID_SIZE, size).apply();
    }

    // 恢复默认设置
    public void restoreDefaultSettings() {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putInt(KEY_SCAN_COUNT, 3);
        editor.putInt(KEY_SCAN_INTERVAL, 3000);
        editor.putInt(KEY_WIFI_THRESHOLD, -85);
        editor.putFloat(KEY_SIMILARITY_THRESHOLD, 0.6f);
        editor.putBoolean(KEY_GRID_ENABLED, false);
        editor.putInt(KEY_GRID_SIZE, 50);
        editor.apply();
    }

    // 导出配置
    public String exportConfig() {
        return String.format(
                "扫描次数: %d\n扫描间隔: %dms\nWiFi阈值: %ddBm\n相似度阈值: %.2f\n网格大小: %dpx",
                getScanCount(), getScanInterval(), getWifiThreshold(),
                getSimilarityThreshold(), getGridSize()
        );
    }

    // 检查配置是否有效
    public boolean validateConfig() {
        return getScanCount() > 0 &&
                getScanInterval() >= 1000 &&
                getWifiThreshold() <= 0 &&
                getSimilarityThreshold() > 0 &&
                getSimilarityThreshold() <= 1 &&
                getGridSize() > 0;
    }
}