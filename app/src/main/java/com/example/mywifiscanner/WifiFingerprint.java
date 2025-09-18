package com.example.mywifiscanner;

import java.util.List;

// 存储“像素坐标+楼层+筛选后的WiFi指纹”的模型
public class WifiFingerprint {
    private double pixelX;       // 绑定的像素X坐标
    private double pixelY;       // 绑定的像素Y坐标
    private int floor;           // 楼层（Z轴，如1=1楼）
    private List<FilteredWifi> filteredWifis; // 筛选后的WiFi列表

    // 内部类：存储单条筛选后的WiFi（MAC+信号强度）
    public static class FilteredWifi {
        private String bssid;    // WiFi的唯一MAC地址（BSSID）
        private String ssid;     // WiFi名称
        private int rssi;        // 平均信号强度（dBm）

        public FilteredWifi(String bssid, String ssid, int rssi) {
            this.bssid = bssid;
            this.ssid = ssid;
            this.rssi = rssi;
        }

        // Getter（用于后续导出JSON）
        public String getBssid() { return bssid; }
        public String getSsid() { return ssid; }
        public int getRssi() { return rssi; }
    }

    // 构造方法
    public WifiFingerprint(double pixelX, double pixelY, int floor, List<FilteredWifi> filteredWifis) {
        this.pixelX = pixelX;
        this.pixelY = pixelY;
        this.floor = floor;
        this.filteredWifis = filteredWifis;
    }

    // Getter（用于导出JSON）
    public double getPixelX() { return pixelX; }
    public double getPixelY() { return pixelY; }
    public int getFloor() { return floor; }
    public List<FilteredWifi> getFilteredWifis() { return filteredWifis; }
}