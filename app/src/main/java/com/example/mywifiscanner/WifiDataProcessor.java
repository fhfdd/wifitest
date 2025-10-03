package com.example.mywifiscanner;

import android.net.wifi.ScanResult;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WifiDataProcessor {

    public static List<FilteredWifi> filterAndSortWifi(List<List<ScanResult>> allScans) {
        List<FilteredWifi> filtered = new ArrayList<>();
        if (allScans == null || allScans.isEmpty()) {
            return filtered;
        }

        Map<String, Integer> rssiSumMap = new HashMap<>();
        Map<String, Integer> countMap = new HashMap<>();
        Map<String, String> ssidMap = new HashMap<>();

        for (List<ScanResult> scan : allScans) {
            for (ScanResult result : scan) {
                String bssid = result.BSSID;
                int rssi = result.level;

                rssiSumMap.put(bssid, rssiSumMap.getOrDefault(bssid, 0) + rssi);
                countMap.put(bssid, countMap.getOrDefault(bssid, 0) + 1);
                ssidMap.put(bssid, result.SSID);
            }
        }

        for (String bssid : rssiSumMap.keySet()) {
            int totalRssi = rssiSumMap.get(bssid);
            int count = countMap.get(bssid);
            int avgRssi = totalRssi / count;

            if (avgRssi > -85) {
                FilteredWifi wifi = new FilteredWifi(ssidMap.get(bssid), bssid,avgRssi);
                wifi.setSsid(ssidMap.get(bssid));
                wifi.setBssid(bssid);
                wifi.setRssi(avgRssi);
                filtered.add(wifi);
            }
        }

        filtered.sort((w1, w2) -> Integer.compare(w2.getRssi(), w1.getRssi()));
        return filtered;
    }
}