package com.example.mywifiscanner;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.provider.Settings;

import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 用于WiFi指纹定位的管理类
 */
public class WifiLocationManager {
    private final Context context;
    private final WifiManager wifiManager;
    private final FingerprintManager fingerprintManager;

    public WifiLocationManager(Context context, WifiManager wifiManager, FingerprintManager fingerprintManager) {
        this.context = context;
        this.wifiManager = wifiManager;
        this.fingerprintManager = fingerprintManager;
    }

    /**
     * 检查位置服务是否开启
     */
    public boolean isLocationEnabled() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            LocationManager systemLocationManager =
                    (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
            return systemLocationManager.isLocationEnabled();
        } else {
            int mode = Settings.Secure.getInt(
                    context.getContentResolver(),
                    Settings.Secure.LOCATION_MODE,
                    Settings.Secure.LOCATION_MODE_OFF);
            return mode != Settings.Secure.LOCATION_MODE_OFF;
        }
    }

    /**
     * 执行实时定位
     */
    public LocationResult startRealTimeLocation() {
        if (!checkLocationPermission()) {
            return null; // 权限不足返回空
        }

        // 检查位置权限
        if (ContextCompat.checkSelfPermission(context,
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return null;
        }

        // 1. 扫描当前WiFi
        boolean scanSuccess = wifiManager.startScan();
        if (!scanSuccess) return null;

        List<ScanResult> currentScans = wifiManager.getScanResults();
        if (currentScans.isEmpty()) return null;

        // 2. 筛选当前有效WiFi
        List<FilteredWifi> currentWifis = filterCurrentWifi(currentScans);
        if (currentWifis.isEmpty()) return null;

        // 3. 与指纹库匹配（传入两个列表计算相似度）
        return matchWithFingerprintLibrary(currentWifis);
    }

    /**
     * 筛选当前WiFi（信号≥-90dBm）
     */
    private List<FilteredWifi> filterCurrentWifi(List<ScanResult> currentScans) {
        List<FilteredWifi> result = new ArrayList<>();
        for (ScanResult scan : currentScans) {
            if (scan.BSSID != null && scan.level >= -90) { // BSSID不为空且信号强度达标
                result.add(new FilteredWifi(
                        scan.BSSID, scan.SSID, scan.level
                ));

            }
        }
        return result;
    }

    /**
     * 与指纹库匹配，找到最相似的位置
     */
    private LocationResult matchWithFingerprintLibrary(List<FilteredWifi> currentWifis) {
        List<WifiFingerprint> fingerprints = fingerprintManager.getAllFingerprints();
        if (fingerprints.isEmpty()) return null;

        WifiFingerprint bestMatch = null;
        double highestSimilarity = 0;

        for (WifiFingerprint fingerprint : fingerprints) {
            // 计算当前WiFi列表与指纹库中某个指纹的WiFi列表的相似度（修复参数不匹配问题）
            double similarity = calculateSimilarity(currentWifis, fingerprint.getFilteredWifis());
            if (similarity > highestSimilarity) {
                highestSimilarity = similarity;
                bestMatch = fingerprint;
            }
        }

        // 相似度需超过60%才视为有效匹配
        if (highestSimilarity > 0.6 && bestMatch != null) {
            return new LocationResult(
                    bestMatch.getPixelX(),
                    bestMatch.getPixelY(),
                    bestMatch.getFloor()
            );
        }
        return null;
    }

    /**
     * 修复：计算两个WiFi列表之间的整体相似度
     * 逻辑：通过BSSID匹配相同WiFi，计算每个匹配项的信号相似度，再求平均值
     */
    private double calculateSimilarity(List<FilteredWifi> currentWifis,
                                       List<FilteredWifi> fingerprintWifis) {
        if (currentWifis.isEmpty() || fingerprintWifis.isEmpty()) {
            return 0; // 任一列表为空，相似度为0
        }

        // 将指纹库的WiFi按BSSID映射（BSSID是WiFi的唯一标识）
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
                // 计算单个WiFi的信号相似度
                totalSimilarity += calculateSingleWifiSimilarity(currentWifi, matchedFpWifi);
                matchCount++;
            }
        }

        // 平均相似度（若无匹配项，返回0）
        return matchCount > 0 ? totalSimilarity / matchCount : 0;
    }

    /**
     * 辅助方法：计算单个WiFi的信号相似度（原逻辑保留）
     */
    private double calculateSingleWifiSimilarity(FilteredWifi currentWifi,
                                                 FilteredWifi fingerprintWifi) {
        int rssiDiff = Math.abs(currentWifi.getRssi() - fingerprintWifi.getRssi());
        // 信号差>40时相似度为0，否则按比例计算（信号越接近，相似度越高）
        return Math.max(0, 1 - (double) rssiDiff / 40);
    }

    /**
     * 检查位置权限（适配Android 10+后台权限）
     */
    public boolean checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        // Android 10+ 后台定位需要额外权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return ContextCompat.checkSelfPermission(context,
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED;
        }
        return true;
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