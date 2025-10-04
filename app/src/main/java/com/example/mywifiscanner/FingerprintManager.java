package com.example.mywifiscanner;

import android.net.wifi.ScanResult;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 指纹管理模块：负责WiFi指纹的采集、保存、更新、查询等操作
 * 职责：管理指纹数据的存储、检索和转换，不涉及扫描和坐标计算
 */
public class FingerprintManager {
    private static final String TAG = "FingerprintManager";
    private final List<WifiFingerprint> fingerprints = new ArrayList<>(); // 指纹列表

    // 构造方法简化，无依赖
    public FingerprintManager() {
    }

    public static List<FilteredWifi> filterAndSortWifi(List<List<ScanResult>> allScans, ConfigManager configManager) {
        return WifiDataProcessor.processMultipleScansWithMax(allScans, configManager);
    }

    // ==================== 指纹保存与更新 ====================
    /**
     * 保存新指纹
     */
    public boolean saveFingerprint(double x, double y, int floor, String zone,
                                   String label, String path, List<FilteredWifi> wifis) {
        if (wifis == null || wifis.isEmpty()) {
            Log.e(TAG, "保存失败：WiFi信号为空");
            return false;
        }

        WifiFingerprint fingerprint = new WifiFingerprint();
        fingerprint.setPixelX(x);
        fingerprint.setPixelY(y);
        fingerprint.setFloor(floor);
        fingerprint.setZone(zone);
        fingerprint.setLabel(label);
        fingerprint.setPath(path);
        fingerprint.setFilteredWifis(new ArrayList<>(wifis)); // 创建副本防止外部修改

        fingerprints.add(fingerprint);
        Log.d(TAG, "指纹保存成功：" + (label != null && !label.isEmpty() ? label : path));
        return true;
    }

    /**
     * 更新已有指纹
     */
    public boolean updateFingerprint(WifiFingerprint fingerprint) {
        int index = fingerprints.indexOf(fingerprint);
        if (index == -1) {
            Log.e(TAG, "更新失败：未找到指纹");
            return false;
        }
        fingerprints.set(index, fingerprint);
        Log.d(TAG, "指纹更新成功");
        return true;
    }

    /**
     * 导出所有指纹数据为JSON字符串
     */
    public String exportFingerprints() {
        if (fingerprints.isEmpty()) {
            return "[]"; // 空列表返回空JSON数组
        }

        // 构建JSON结构（实际项目建议用Gson等库，此处为示例）
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < fingerprints.size(); i++) {
            WifiFingerprint fp = fingerprints.get(i);
            json.append("{")
                    .append("\"x\":").append(fp.getPixelX()).append(",")
                    .append("\"y\":").append(fp.getPixelY()).append(",")
                    .append("\"floor\":").append(fp.getFloor()).append(",")
                    .append("\"zone\":\"").append(escapeJson(fp.getZone())).append("\",")
                    .append("\"label\":\"").append(escapeJson(fp.getLabel())).append("\",")
                    .append("\"path\":\"").append(escapeJson(fp.getPath())).append("\",")
                    .append("\"wifis\":[");

            // 拼接WiFi列表
            List<FilteredWifi> wifis = fp.getFilteredWifis();
            for (int j = 0; j < wifis.size(); j++) {
                FilteredWifi wifi = wifis.get(j);
                json.append("{")
                        .append("\"ssid\":\"").append(escapeJson(wifi.getSsid())).append("\",")
                        .append("\"bssid\":\"").append(escapeJson(wifi.getBssid())).append("\",")
                        .append("\"rssi\":").append(wifi.getRssi())
                        .append("}");
                if (j < wifis.size() - 1) json.append(",");
            }
            json.append("]")
                    .append("}");
            if (i < fingerprints.size() - 1) json.append(",");
        }
        json.append("]");
        return json.toString();
    }

    // 辅助方法：转义JSON中的特殊字符
    private String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    /**
     * 删除指纹
     */
    public boolean deleteFingerprint(WifiFingerprint fingerprint) {
        boolean removed = fingerprints.remove(fingerprint);
        if (removed) {
            Log.d(TAG, "指纹删除成功");
        } else {
            Log.e(TAG, "删除失败：未找到指纹");
        }
        return removed;
    }

    // ==================== 指纹查询 ====================
    /**
     * 获取所有指纹（返回副本，避免外部修改）
     */
    public List<WifiFingerprint> getAllFingerprints() {
        return new ArrayList<>(fingerprints);
    }

    /**
     * 从外部列表加载指纹（用于导入文件）
     */
    public void loadFromFile(List<WifiFingerprint> loadedFingerprints) {
        if (loadedFingerprints == null) {
            Log.w(TAG, "导入失败：指纹列表为空");
            return;
        }
        fingerprints.clear();
        fingerprints.addAll(loadedFingerprints);
        Log.d(TAG, "从文件导入指纹成功，共" + loadedFingerprints.size() + "条");
    }

    /**
     * 获取指定楼层的指纹
     */
    public List<WifiFingerprint> getFingerprintsByFloor(int floor) {
        List<WifiFingerprint> result = new ArrayList<>();
        for (WifiFingerprint fp : fingerprints) {
            if (fp.getFloor() == floor) {
                result.add(fp);
            }
        }
        return result;
    }

    /**
     * 获取指定区域的指纹
     */
    public List<WifiFingerprint> getFingerprintsByZone(String zone) {
        List<WifiFingerprint> result = new ArrayList<>();
        for (WifiFingerprint fp : fingerprints) {
            if (zone.equals(fp.getZone())) {
                result.add(fp);
            }
        }
        return result;
    }

    /**
     * 获取指纹总数
     */
    public int getFingerprintCount() {
        return fingerprints.size();
    }

    /**
     * 清空所有指纹
     */
    public void clearAllFingerprints() {
        fingerprints.clear();
        Log.d(TAG, "所有指纹已清空");
    }

    /**
     * 检查是否存在指纹数据
     */
    public boolean hasFingerprints() {
        return !fingerprints.isEmpty();
    }
}