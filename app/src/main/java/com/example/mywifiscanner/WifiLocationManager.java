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
    private final ConfigManager configManager; // 保留配置管理器，支持动态配置

    // 保留原有构造方法，包含ConfigManager以支持动态配置
    public WifiLocationManager(Context context, WifiManager wifiManager,
                               FingerprintManager fingerprintManager, ConfigManager configManager) {
        this.context = context;
        this.wifiManager = wifiManager;
        this.fingerprintManager = fingerprintManager;
        this.configManager = configManager;
    }

    /**
     * 检查位置服务是否开启（保留异常处理）
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
                Log.e(TAG, "检查位置服务失败", e);
                return false;
            }
        }
    }

    /**
     * 执行实时定位 - 优化版本（整合权限检查和扫描逻辑）
     */
    public LocationResult startRealTimeLocation() {
        Log.d(TAG, "开始实时定位...");

        if (!checkLocationPermission()) {
            Log.e(TAG, "位置权限不足");
            return null;
        }

        // 启动扫描并立即返回结果（依赖系统扫描完成时机）
        boolean scanSuccess = wifiManager.startScan();
        if (!scanSuccess) {
            Log.e(TAG, "WiFi扫描启动失败");
            return null;
        }

        // 移除Thread.sleep(2000)，直接获取当前可用结果
        List<ScanResult> currentScans = wifiManager.getScanResults();
        if (currentScans == null || currentScans.isEmpty()) {
            Log.e(TAG, "扫描结果为空");
            return null;
        }

        // 后续处理逻辑保持不变...
        List<FilteredWifi> currentWifis = filterCurrentWifi(currentScans);
        if (currentWifis.isEmpty()) {
            Log.e(TAG, "过滤后无可用WiFi");
            return null;
        }

        return matchWithFingerprintLibraryOptimized(currentWifis);
    }

    /**
     * 筛选当前WiFi（使用配置阈值，而非硬编码-90dBm）
     */
    private List<FilteredWifi> filterCurrentWifi(List<ScanResult> currentScans) {
        // 复用数据处理类，统一过滤逻辑
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

        // 动态阈值：根据指纹库大小调整（比固定0.6更灵活）
        double threshold = calculateDynamicThreshold(fingerprints.size());

        // 遍历指纹库，计算相似度
        double bestSimilarity = 0;
        WifiFingerprint bestMatch = null;
        for (WifiFingerprint fp : fingerprints) {
            double similarity = calculateOptimizedSimilarity(currentWifis, fp.getFilteredWifis());
            if (similarity > bestSimilarity) {
                bestSimilarity = similarity;
                bestMatch = fp;
            }
        }

        // 使用动态阈值判断
        if (bestMatch != null && bestSimilarity > threshold) {
            Log.d(TAG, "定位结果：" + bestMatch.getPixelX() + "," + bestMatch.getPixelY()
                    + " 相似度：" + bestSimilarity + "（阈值：" + threshold + "）");
            return new LocationResult(
                    bestMatch.getPixelX(),
                    bestMatch.getPixelY(),
                    bestMatch.getFloor()
            );
        } else {
            Log.d(TAG, "无匹配结果（最高相似度：" + bestSimilarity + "，阈值：" + threshold + "）");
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
     * 优化相似度计算（使用HashMap优化匹配效率，考虑信号波动和匹配比例）
     */
    private double calculateOptimizedSimilarity(List<FilteredWifi> currentWifis,
                                                List<FilteredWifi> fingerprintWifis) {
        if (currentWifis.isEmpty() || fingerprintWifis.isEmpty()) {
            return 0;
        }

        // 使用HashMap存储指纹库WiFi，减少嵌套循环（比双重for循环高效）
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

        // 考虑匹配比例（解决部分匹配的问题）
        double matchRatio = (double) matchCount / Math.min(currentWifis.size(), fingerprintWifis.size());
        double baseSimilarity = matchCount > 0 ? totalSimilarity / matchCount : 0;

        // 综合相似度 = 基础相似度 × 匹配比例（比单纯信号相似度更合理）
        return baseSimilarity * matchRatio;
    }

    /**
     * 鲁棒的WiFi相似度计算（容忍信号波动，比简单的线性计算更合理）
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
            // 中间范围线性衰减
            return 1.0 - (double) (rssiDiff - 10) / 30;
        }
    }

    /**
     * 检查位置权限（适配Android 10+后台权限，比单纯检查ACCESS_FINE_LOCATION更全面）
     */
    public boolean checkLocationPermission() {
        // 简化权限检查，只需要基础定位权限
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
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