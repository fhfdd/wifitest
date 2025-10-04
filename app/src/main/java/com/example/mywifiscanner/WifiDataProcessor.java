package com.example.mywifiscanner;

import android.net.wifi.ScanResult;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 统一的WiFi数据处理工具类
 * 负责WiFi信号的过滤、排序、平均计算、强度计算、相似度计算等
 */
public class WifiDataProcessor {
    private static final String TAG = "WifiDataProcessor";

    /**
     * 处理多次扫描结果（计算平均RSSI）
     * @param allScans 多次扫描结果集合
     * @param configManager 配置管理器（提供信号阈值）
     * @return 过滤并排序后的WiFi列表
     */
    public static List<FilteredWifi> processMultipleScansWithAverage(
            List<List<ScanResult>> allScans,
            ConfigManager configManager) {
        List<FilteredWifi> filtered = new ArrayList<>();
        if (allScans == null || allScans.isEmpty()) {
            Log.w(TAG, "无扫描结果可处理");
            return filtered;
        }

        // 计算每个BSSID的RSSI总和与计数
        Map<String, Integer> rssiSumMap = new HashMap<>();
        Map<String, Integer> countMap = new HashMap<>();
        Map<String, String> ssidMap = new HashMap<>();

        for (List<ScanResult> scan : allScans) {
            if (scan == null) continue;
            for (ScanResult result : scan) {
                if (result == null || result.BSSID == null) continue;

                String bssid = result.BSSID;
                int rssi = result.level;
                rssiSumMap.put(bssid, rssiSumMap.getOrDefault(bssid, 0) + rssi);
                countMap.put(bssid, countMap.getOrDefault(bssid, 0) + 1);
                ssidMap.put(bssid, result.SSID);
            }
        }

        // 过滤弱信号并计算平均值
        int rssiThreshold = configManager.getWifiThreshold();
        for (String bssid : rssiSumMap.keySet()) {
            int totalRssi = rssiSumMap.get(bssid);
            int count = countMap.get(bssid);
            int avgRssi = totalRssi / count;

            if (avgRssi > rssiThreshold) {
                FilteredWifi wifi = new FilteredWifi(ssidMap.get(bssid), bssid, avgRssi);
                filtered.add(wifi);
            } else {
                Log.d(TAG, "过滤弱信号 BSSID:" + bssid + " 平均强度:" + avgRssi + "（低于阈值" + rssiThreshold + "）");
            }
        }

        // 按信号强度降序排序
        filtered.sort((w1, w2) -> Integer.compare(w2.getRssi(), w1.getRssi()));
        return filtered;
    }

    /**
     * 处理多次扫描结果（保留最强RSSI）
     * @param allScans 多次扫描结果集合
     * @param configManager 配置管理器（提供信号阈值）
     * @return 过滤并排序后的WiFi列表
     */
    public static List<FilteredWifi> processMultipleScansWithMax(
            List<List<ScanResult>> allScans,
            ConfigManager configManager) {
        List<FilteredWifi> filtered = new ArrayList<>();
        if (allScans == null || allScans.isEmpty()) {
            Log.w(TAG, "无扫描结果可处理");
            return filtered;
        }

        // 保留每个BSSID的最强信号
        Map<String, FilteredWifi> bssidMap = new HashMap<>();
        int rssiThreshold = configManager.getWifiThreshold();

        for (List<ScanResult> scanList : allScans) {
            if (scanList == null || scanList.isEmpty()) continue;
            for (ScanResult scan : scanList) {
                if (scan.BSSID != null && scan.level >= rssiThreshold) {
                    String cleanedSsid = scan.SSID.replace("\"", "");
                    FilteredWifi wifi = new FilteredWifi(cleanedSsid, scan.BSSID, scan.level);
                    String bssid = wifi.getBssid();

                    // 仅保留更强的信号
                    if (bssidMap.containsKey(bssid)) {
                        FilteredWifi existing = bssidMap.get(bssid);
                        if (wifi.getRssi() > existing.getRssi()) {
                            bssidMap.put(bssid, wifi);
                        }
                    } else {
                        bssidMap.put(bssid, wifi);
                    }
                }
            }
        }

        filtered = new ArrayList<>(bssidMap.values());
        filtered.sort((w1, w2) -> Integer.compare(w2.getRssi(), w1.getRssi()));
        return filtered;
    }

    /**
     * 处理单次扫描结果
     * @param singleScan 单次扫描结果
     * @param configManager 配置管理器（提供信号阈值）
     * @return 过滤并排序后的WiFi列表
     */
    public static List<FilteredWifi> processSingleScan(
            List<ScanResult> singleScan,
            ConfigManager configManager) {
        List<FilteredWifi> filtered = new ArrayList<>();
        if (singleScan == null || singleScan.isEmpty()) {
            Log.w(TAG, "单次扫描结果为空");
            return filtered;
        }

        int rssiThreshold = configManager.getWifiThreshold();
        for (ScanResult result : singleScan) {
            if (result != null && result.BSSID != null && result.level > rssiThreshold) {
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
     * 计算两个WiFi列表的相似度（用于定位匹配）
     * @param currentWifis 当前扫描的WiFi列表
     * @param fingerprintWifis 指纹库中的WiFi列表
     * @return 相似度（0-1之间）
     */
    public static double calculateSimilarity(
            List<FilteredWifi> currentWifis,
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