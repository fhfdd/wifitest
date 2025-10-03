package com.example.mywifiscanner;

import android.net.wifi.ScanResult;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * WiFi数据处理工具类：负责WiFi信号的过滤、排序、平均计算等静态操作
 * 完全独立，不依赖其他业务模块
 */
public class WifiUtils {
    private static final String TAG = "WifiUtils";

    /**
     * 静态方法：过滤并排序WiFi信号（合并多次扫描结果，计算平均强度）
     */
    public static List<FilteredWifi> filterAndSortWifi(List<List<ScanResult>> allScans) {
        List<FilteredWifi> filtered = new ArrayList<>();
        if (allScans == null || allScans.isEmpty()) {
            Log.w(TAG, "无扫描结果可处理");
            return filtered;
        }

        // 计算每个WiFi的平均信号强度
        Map<String, Integer> rssiSumMap = new HashMap<>();
        Map<String, Integer> countMap = new HashMap<>();
        Map<String, String> ssidMap = new HashMap<>();

        for (List<ScanResult> scan : allScans) {
            if (scan == null) continue;

            for (ScanResult result : scan) {
                if (result == null) continue;

                String bssid = result.BSSID;
                if (bssid == null) continue;

                int rssi = result.level;
                rssiSumMap.put(bssid, rssiSumMap.getOrDefault(bssid, 0) + rssi);
                countMap.put(bssid, countMap.getOrDefault(bssid, 0) + 1);
                ssidMap.put(bssid, result.SSID);
            }
        }

        // 过滤弱信号并计算平均值
        for (String bssid : rssiSumMap.keySet()) {
            int totalRssi = rssiSumMap.get(bssid);
            int count = countMap.get(bssid);
            int avgRssi = totalRssi / count;

            if (avgRssi > -85) { // 过滤弱信号（强度 > -85dBm视为有效）
                FilteredWifi wifi = new FilteredWifi(ssidMap.get(bssid), bssid, avgRssi);
                filtered.add(wifi);
            }
        }

        // 按信号强度降序排序（信号强的在前）
        filtered.sort((w1, w2) -> Integer.compare(w2.getRssi(), w1.getRssi()));
        return filtered;
    }

    /**
     * 静态方法：单次扫描结果的快速过滤和排序
     */
// 1. 修复 filterAndSortSingleScan 方法
    public static List<FilteredWifi> filterAndSortSingleScan(List<ScanResult> singleScan) {
        List<FilteredWifi> filtered = new ArrayList<>();
        if (singleScan == null || singleScan.isEmpty()) {
            Log.w(TAG, "单次扫描结果为空");
            return filtered;
        }

        for (ScanResult result : singleScan) {
            if (result != null && result.level > -85) {
                // 用三参数构造器直接初始化
                FilteredWifi wifi = new FilteredWifi(
                        result.SSID,
                        result.BSSID,
                        result.level
                );
                filtered.add(wifi);
            }
        }

        // 按信号强度降序排序
        filtered.sort((w1, w2) -> Integer.compare(w2.getRssi(), w1.getRssi()));
        return filtered;
    }


    /**
     * 静态方法：计算WiFi列表的相似度（用于定位匹配）
     */
    public static double calculateSimilarity(List<FilteredWifi> currentWifis,
                                             List<FilteredWifi> fingerprintWifis) {
        if (currentWifis == null || fingerprintWifis == null ||
                currentWifis.isEmpty() || fingerprintWifis.isEmpty()) {
            return 0;
        }

        // 将指纹库的WiFi按BSSID映射
        Map<String, FilteredWifi> fingerprintMap = new HashMap<>();
        for (FilteredWifi fpWifi : fingerprintWifis) {
            if (fpWifi != null && fpWifi.getBssid() != null) {
                fingerprintMap.put(fpWifi.getBssid(), fpWifi);
            }
        }

        double totalSimilarity = 0;
        int matchCount = 0;

        // 遍历当前WiFi，匹配指纹库中相同BSSID的WiFi
        for (FilteredWifi currentWifi : currentWifis) {
            if (currentWifi == null || currentWifi.getBssid() == null) continue;

            FilteredWifi matchedFpWifi = fingerprintMap.get(currentWifi.getBssid());
            if (matchedFpWifi != null) {
                // 计算单个WiFi的信号相似度
                int rssiDiff = Math.abs(currentWifi.getRssi() - matchedFpWifi.getRssi());
                double similarity = Math.max(0, 1 - (double) rssiDiff / 40);
                totalSimilarity += similarity;
                matchCount++;
            }
        }

        return matchCount > 0 ? totalSimilarity / matchCount : 0;
    }
}