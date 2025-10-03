package com.example.mywifiscanner;

import android.content.Context;
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

    // 构造方法简化，只保留必要的上下文
    public FingerprintManager() {
        // 不再依赖WifiScanner和CoordinateManager
    }

    // ==================== 指纹数据处理 ====================
    /**
     * 过滤并排序WiFi信号（合并多次扫描结果，计算平均强度，过滤弱信号）
     * 改为静态工具方法，不依赖实例状态
     */
// 注意：如果FilteredWifi已独立，需改为 List<FilteredWifi>
    public static List<FilteredWifi> filterAndSortWifi(List<List<ScanResult>> allScans) {
        List<FilteredWifi> filtered = new ArrayList<>();
        if (allScans == null || allScans.isEmpty()) {
            Log.w(TAG, "无扫描结果可处理");
            return filtered;
        }

        // 1. 遍历所有扫描结果列表，过滤有效WiFi
        for (List<ScanResult> scanList : allScans) { // 遍历每次扫描的结果列表
            if (scanList == null || scanList.isEmpty()) {
                continue; // 跳过空的子列表
            }
            for (ScanResult scan : scanList) { // 遍历单次扫描的每个结果
                // 过滤条件：BSSID不为空 + 信号强度达标（例如 >= -90）
                if (scan.BSSID != null && scan.level >= -90) {
                    filtered.add(new FilteredWifi(
                            scan.SSID,    // 对应ssid
                            scan.BSSID,   // 对应bssid
                            scan.level    // 对应rssi（信号强度）
                    ));
                }
            }
        }

        // 2. 去重：同一BSSID保留信号最强的记录（可选，根据业务需求）
        Map<String, FilteredWifi> bssidMap = new HashMap<>();
        for (FilteredWifi wifi : filtered) {
            String bssid = wifi.getBssid();
            // 如果map中已有该BSSID，比较信号强度，保留更强的（rssi值越大信号越强）
            if (bssidMap.containsKey(bssid)) {
                FilteredWifi existing = bssidMap.get(bssid);
                if (wifi.getRssi() > existing.getRssi()) {
                    bssidMap.put(bssid, wifi); // 替换为更强的信号
                }
            } else {
                bssidMap.put(bssid, wifi); // 首次添加
            }
        }
        // 用去重后的结果替换原列表
        filtered = new ArrayList<>(bssidMap.values());

        // 3. 排序：按信号强度（rssi）从强到弱排序（值越大信号越强）
        filtered.sort((wifi1, wifi2) -> Integer.compare(wifi2.getRssi(), wifi1.getRssi()));

        return filtered;
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
        Log.d(TAG, "指纹保存成功：" + (label != null ? label : path));
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

        // 构建JSON结构（这里使用字符串拼接示例，实际项目建议用Gson等库）
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
                if (j < wifis.size() - 1) {
                    json.append(",");
                }
            }
            json.append("]")
                    .append("}");
            if (i < fingerprints.size() - 1) {
                json.append(",");
            }
        }
        json.append("]");
        return json.toString();
    }

    // 辅助方法：转义JSON中的特殊字符（避免引号冲突）
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
     * 根据ID查找指纹
     */
    public WifiFingerprint findFingerprintById(int id) {
        if (id >= 0 && id < fingerprints.size()) {
            return fingerprints.get(id);
        }
        return null;
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

    // ==================== 数据转换方法 ====================
    /**
     * 将指纹数据转换为MapData的点位数据
     */
    public List<MapData.PointData> convertToPointData() {
        List<MapData.PointData> pointDataList = new ArrayList<>();
        for (WifiFingerprint fp : fingerprints) {
            MapData.PointData pointData = new MapData.PointData();
            pointData.setSpecial(fp.getLabel() != null && !fp.getLabel().isEmpty());
            pointData.setLabel(fp.getLabel());
            pointData.setPath(fp.getPath());
            pointData.setX((float) fp.getPixelX());
            pointData.setY((float) fp.getPixelY());
            pointDataList.add(pointData);
        }
        return pointDataList;
    }

    /**
     * 从MapData导入指纹数据
     */
    public void importFromMapData(MapData data) {
        if (data == null || data.getPoints() == null) {
            Log.w(TAG, "无数据可导入");
            return;
        }

        fingerprints.clear();
        for (MapData.PointData point : data.getPoints()) {
            WifiFingerprint fp = new WifiFingerprint();
            fp.setPixelX(point.getX());
            fp.setPixelY(point.getY());
            fp.setLabel(point.isSpecial() ? point.getLabel() : null);
            fp.setPath(point.isSpecial() ? null : point.getPath());
            // 注意：导入的数据可能没有WiFi信号数据，需要后续补充
            fingerprints.add(fp);
        }
        Log.d(TAG, "数据导入完成，共" + fingerprints.size() + "个点位");
    }

    // ==================== 指纹查询 ====================
    /**
     * 获取所有指纹
     */
    public List<WifiFingerprint> getAllFingerprints() {
        return new ArrayList<>(fingerprints); // 返回副本，避免外部修改
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
     * 获取指纹数量
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