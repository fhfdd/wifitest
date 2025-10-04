package com.example.mywifiscanner;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 用于WiFi指纹定位的管理类 - 优化版本
 */
public class WifiLocationManager {
    private static final String TAG = "WifiLocationManager";
    private final Context context;
    private final WifiManager wifiManager;
    private final FingerprintManager fingerprintManager;
    private final ConfigManager configManager;

    public WifiLocationManager(Context context, WifiManager wifiManager,
                               FingerprintManager fingerprintManager, ConfigManager configManager) {
        this.context = context;
        this.wifiManager = wifiManager;
        this.fingerprintManager = fingerprintManager;
        this.configManager = configManager;
    }

    /**
     * 检查位置服务是否开启
     */
    public boolean isLocationEnabled() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            LocationManager systemLocationManager =
                    (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
            return systemLocationManager != null && systemLocationManager.isLocationEnabled();
        } else {
            try {
                int mode = Settings.Secure.getInt(
                        context.getContentResolver(),
                        Settings.Secure.LOCATION_MODE);
                return mode != Settings.Secure.LOCATION_MODE_OFF;
            } catch (Exception e) {
                return false;
            }
        }
    }

    /**
     * 执行实时定位 - 优化版本
     */
    public LocationResult startRealTimeLocation() {
        Log.d(TAG, "开始实时定位...");

        if (!checkLocationPermission()) {
            Log.e(TAG, "位置权限不足");
            return null;
        }

        // 1. 扫描当前WiFi
        boolean scanSuccess = wifiManager.startScan();
        if (!scanSuccess) {
            Log.e(TAG, "WiFi扫描启动失败");
            return null;
        }

        // 等待扫描完成（简单延迟）
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        List<ScanResult> currentScans = wifiManager.getScanResults();
        if (currentScans == null || currentScans.isEmpty()) {
            Log.e(TAG, "扫描结果为空");
            return null;
        }

        Log.d(TAG, "扫描到 " + currentScans.size() + " 个WiFi信号");

        // 2. 筛选当前有效WiFi
        List<FilteredWifi> currentWifis = filterCurrentWifi(currentScans);
        if (currentWifis.isEmpty()) {
            Log.e(TAG, "过滤后无可用WiFi");
            return null;
        }

        Log.d(TAG, "过滤后剩余 " + currentWifis.size() + " 个强信号WiFi");

        // 3. 与指纹库匹配（使用优化算法）
        return matchWithFingerprintLibraryOptimized(currentWifis);
    }

    /**
     * 筛选当前WiFi（使用配置阈值）
     */
    private List<FilteredWifi> filterCurrentWifi(List<ScanResult> currentScans) {
        return WifiDataProcessor.processSingleScan(currentScans, configManager);
    }

    /**
     * 与指纹库匹配 - 优化版本（考虑信号波动和多个候选点）
     */
    private LocationResult matchWithFingerprintLibraryOptimized(List<FilteredWifi> currentWifis) {
        List<WifiFingerprint> fingerprints = fingerprintManager.getAllFingerprints();
        if (fingerprints.isEmpty()) {
            Log.e(TAG, "指纹库为空");
            return null;
        }

        Log.d(TAG, "开始匹配指纹库，共有 " + fingerprints.size() + " 个指纹点");

        List<LocationCandidate> candidates = new ArrayList<>();
        double bestSimilarity = 0;
        WifiFingerprint bestMatch = null;

        for (WifiFingerprint fingerprint : fingerprints) {
            // 计算相似度（优化版本）
            double similarity = calculateOptimizedSimilarity(currentWifis, fingerprint.getFilteredWifis());

            Log.d(TAG, String.format("指纹点 (%.0f, %.0f) F%d 相似度: %.3f",
                    fingerprint.getPixelX(), fingerprint.getPixelY(),
                    fingerprint.getFloor(), similarity));

            candidates.add(new LocationCandidate(fingerprint, similarity));

            if (similarity > bestSimilarity) {
                bestSimilarity = similarity;
                bestMatch = fingerprint;
            }
        }

        // 输出所有候选点信息
        Log.d(TAG, "=== 定位候选点 ===");
        for (LocationCandidate candidate : candidates) {
            Log.d(TAG, String.format("候选点: (%.0f, %.0f) F%d - 相似度: %.3f",
                    candidate.fingerprint.getPixelX(), candidate.fingerprint.getPixelY(),
                    candidate.fingerprint.getFloor(), candidate.similarity));
        }

        // 动态阈值：根据指纹库大小调整
        double dynamicThreshold = calculateDynamicThreshold(fingerprints.size());
        Log.d(TAG, "动态相似度阈值: " + dynamicThreshold);

        if (bestSimilarity > dynamicThreshold && bestMatch != null) {
            Log.d(TAG, String.format("定位成功！最佳匹配点: (%.0f, %.0f) F%d, 相似度: %.3f",
                    bestMatch.getPixelX(), bestMatch.getPixelY(),
                    bestMatch.getFloor(), bestSimilarity));

            return new LocationResult(
                    bestMatch.getPixelX(),
                    bestMatch.getPixelY(),
                    bestMatch.getFloor()
            );
        } else {
            Log.w(TAG, "定位失败：无足够相似度的指纹点（最高相似度: " + bestSimilarity + "）");

            // 如果没有达到阈值，但存在相似度较高的点，返回相似度最高的点
            if (bestSimilarity > 0.3 && bestMatch != null) {
                Log.w(TAG, "使用较低相似度点作为备选: " + bestSimilarity);
                return new LocationResult(
                        bestMatch.getPixelX(),
                        bestMatch.getPixelY(),
                        bestMatch.getFloor()
                );
            }

            return null;
        }
    }

    /**
     * 计算动态相似度阈值（根据指纹库大小调整）
     */
    private double calculateDynamicThreshold(int fingerprintCount) {
        if (fingerprintCount < 5) {
            return 0.4; // 指纹库较小，降低阈值
        } else if (fingerprintCount < 10) {
            return 0.5;
        } else {
            return 0.6; // 指纹库较大，提高阈值以获得更精确匹配
        }
    }

    /**
     * 优化相似度计算（考虑信号波动和部分匹配）
     */
    private double calculateOptimizedSimilarity(List<FilteredWifi> currentWifis,
                                                List<FilteredWifi> fingerprintWifis) {
        if (currentWifis.isEmpty() || fingerprintWifis.isEmpty()) {
            return 0;
        }

        // 将指纹库的WiFi按BSSID映射
        Map<String, FilteredWifi> fingerprintMap = new HashMap<>();
        for (FilteredWifi fpWifi : fingerprintWifis) {
            fingerprintMap.put(fpWifi.getBssid(), fpWifi);
        }

        double totalSimilarity = 0;
        int matchCount = 0;

        // 遍历当前WiFi，匹配指纹库中相同BSSID的WiFi
        for (FilteredWifi currentWifi : currentWifis) {
            FilteredWifi matchedFpWifi = fingerprintMap.get(currentWifi.getBssid());
            if (matchedFpWifi != null) {
                // 计算单个WiFi的信号相似度（考虑信号波动）
                double similarity = calculateRobustWifiSimilarity(currentWifi, matchedFpWifi);
                totalSimilarity += similarity;
                matchCount++;

                Log.d(TAG, String.format("  WiFi匹配: %s, 信号: %d vs %d, 相似度: %.3f",
                        currentWifi.getSsid(), currentWifi.getRssi(),
                        matchedFpWifi.getRssi(), similarity));
            }
        }

        // 考虑匹配比例
        double matchRatio = (double) matchCount / Math.min(currentWifis.size(), fingerprintWifis.size());
        double baseSimilarity = matchCount > 0 ? totalSimilarity / matchCount : 0;

        // 综合相似度 = 基础相似度 × 匹配比例
        double finalSimilarity = baseSimilarity * matchRatio;

        Log.d(TAG, String.format("  匹配统计: %d/%d, 基础相似度: %.3f, 匹配比例: %.3f, 最终相似度: %.3f",
                matchCount, Math.min(currentWifis.size(), fingerprintWifis.size()),
                baseSimilarity, matchRatio, finalSimilarity));

        return finalSimilarity;
    }

    /**
     * 鲁棒的WiFi相似度计算（容忍信号波动）
     */
    private double calculateRobustWifiSimilarity(FilteredWifi currentWifi,
                                                 FilteredWifi fingerprintWifi) {
        int rssiDiff = Math.abs(currentWifi.getRssi() - fingerprintWifi.getRssi());

        // 信号差在10dBm内认为完全相似，超过40dBm认为不相似
        if (rssiDiff <= 10) {
            return 1.0;
        } else if (rssiDiff >= 40) {
            return 0.0;
        } else {
            // 线性衰减
            return 1.0 - (double) (rssiDiff - 10) / 30;
        }
    }

    /**
     * 检查位置权限（适配Android 10+后台权限）
     */
    public boolean checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        // 新增：检查后台定位权限（Android 10+）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return ContextCompat.checkSelfPermission(context,
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    /**
     * 定位候选点内部类
     */
    private static class LocationCandidate {
        WifiFingerprint fingerprint;
        double similarity;

        LocationCandidate(WifiFingerprint fingerprint, double similarity) {
            this.fingerprint = fingerprint;
            this.similarity = similarity;
        }
    }

    /**
     * 定位结果实体类
     */
    public static class LocationResult {
        private final double x;
        private final double y;
        private final int floor;

        public LocationResult(double x, double y, int floor) {
            this.x = x;
            this.y = y;
            this.floor = floor;
        }

        public double getX() { return x; }
        public double getY() { return y; }
        public int getFloor() { return floor; }
    }
}