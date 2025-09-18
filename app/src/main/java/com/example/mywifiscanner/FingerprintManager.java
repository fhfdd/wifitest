package com.example.mywifiscanner;

import android.content.Context;
import android.net.wifi.ScanResult;
import android.os.Environment;
import android.util.Log;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Collections;

// 指纹筛选、保存到本地、导出JSON的工具类
public class FingerprintManager {
    private static final String TAG = "FingerprintManager";
    private static final String FILE_NAME = "wifi_fingerprints.json"; // 保存文件名称
    private Context context;
    private List<WifiFingerprint> allFingerprints = new ArrayList<>(); // 存储所有“像素点-指纹”绑定

    // 初始化（传入Activity上下文）
    public FingerprintManager(Context context) {
        this.context = context;
        loadSavedFingerprints(); // 启动时加载已保存的指纹
    }

    // -------------------------- 1. WiFi筛选与排序（核心） --------------------------
    // 输入：多次扫描的WiFi结果列表
    // 输出：筛选后的稳定WiFi（按信号强度降序）
    public List<WifiFingerprint.FilteredWifi> filterAndSortWifi(List<List<ScanResult>> multipleScans) {
        // 步骤1：统计每个WiFi的出现次数和平均信号强度
        Map<String, WifiStats> wifiStatsMap = new HashMap<>();
        for (List<ScanResult> oneScan : multipleScans) {
            for (ScanResult result : oneScan) {
                String bssid = result.BSSID; // 用BSSID唯一标识WiFi（避免同SSID不同设备）
                if (bssid == null) continue; // 过滤无效WiFi

                WifiStats stats = wifiStatsMap.getOrDefault(bssid, new WifiStats());
                stats.ssid = result.SSID;
                stats.count++; // 累计出现次数
                stats.totalRssi += result.level; // 累计信号强度
                wifiStatsMap.put(bssid, stats);
            }
        }

        // 步骤2：筛选稳定WiFi（条件：出现≥3次 + 平均信号≥-85dBm）
        List<WifiFingerprint.FilteredWifi> filteredWifis = new ArrayList<>();
        for (Map.Entry<String, WifiStats> entry : wifiStatsMap.entrySet()) {
            WifiStats stats = entry.getValue();
            int avgRssi = stats.totalRssi / stats.count;
            if (stats.count >= 3 && avgRssi >= -85) { // 核心筛选条件，可调整
                filteredWifis.add(new WifiFingerprint.FilteredWifi(
                        entry.getKey(), // BSSID
                        stats.ssid,     // SSID
                        avgRssi         // 平均信号强度
                ));
            }
        }

        // 步骤3：按信号强度降序排序（强信号优先，定位更准）
        Collections.sort(filteredWifis, (a, b) -> b.getRssi() - a.getRssi());
        return filteredWifis;
    }

    // 辅助类：统计WiFi的出现次数和信号总和
    private static class WifiStats {
        String ssid;
        int count = 0;
        int totalRssi = 0;
    }

    // -------------------------- 2. 绑定“像素坐标+楼层+指纹”并保存 --------------------------
    // 输入：像素坐标、楼层、筛选后的WiFi
    // 功能：将绑定关系保存到本地
    public boolean saveFingerprint(double pixelX, double pixelY, int floor, List<WifiFingerprint.FilteredWifi> filteredWifis) {
        if (filteredWifis.isEmpty()) return false; // 无筛选后的WiFi，不保存

        // 创建“像素点-指纹”绑定对象
        WifiFingerprint newFingerprint = new WifiFingerprint(pixelX, pixelY, floor, filteredWifis);
        allFingerprints.add(newFingerprint);

        // 保存到本地文件（用Gson转JSON格式，方便后续读取）
        Gson gson = new Gson();
        String json = gson.toJson(allFingerprints);
        try {
            // 保存路径：手机存储/Android/data/你的包名/files/wifi_fingerprints.json
            File file = new File(context.getExternalFilesDir(null), FILE_NAME);
            FileWriter writer = new FileWriter(file);
            writer.write(json);
            writer.flush();
            writer.close();
            Log.d(TAG, "指纹保存成功，路径：" + file.getAbsolutePath());
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    // -------------------------- 3. 导出所有指纹（JSON格式，方便后续开发） --------------------------
    // 功能：导出所有“像素点-指纹”绑定到手机存储根目录（易找到）
    public String exportFingerprints() {
        if (allFingerprints.isEmpty()) return "无已保存的指纹";

        Gson gson = new Gson();
        String json = gson.toJson(allFingerprints); // 转JSON格式（可读性强，后续开发易解析）

        try {
            // 导出路径：手机存储根目录/WiFi_Fingerprints/exported_fingerprints.json
            File exportDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "WiFi_Fingerprints");
            if (!exportDir.exists()) exportDir.mkdirs(); // 创建文件夹

            File exportFile = new File(exportDir, "exported_fingerprints.json");
            FileWriter writer = new FileWriter(exportFile);
            writer.write(json);
            writer.flush();
            writer.close();
            return "导出成功！路径：" + exportFile.getAbsolutePath();
        } catch (IOException e) {
            e.printStackTrace();
            return "导出失败：" + e.getMessage();
        }
    }

    // -------------------------- 辅助：加载已保存的指纹 --------------------------
    private void loadSavedFingerprints() {
        File file = new File(context.getExternalFilesDir(null), FILE_NAME);
        if (!file.exists()) return;

        try {
            Reader reader = new FileReader(file);
            allFingerprints = new Gson().fromJson(reader, new TypeToken<List<WifiFingerprint>>() {}.getType());
            reader.close();
            Log.d(TAG, "加载已保存的指纹数量：" + allFingerprints.size());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // 获取所有已保存的指纹（用于调试显示）
    public List<WifiFingerprint> getAllFingerprints() {
        return allFingerprints;
    }
}