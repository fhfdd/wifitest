package com.example.mywifiscanner;

import java.io.Serializable;

/**
 * 单条WiFi扫描结果模型
 */
public class WifiScanResult implements Serializable {
    private String bssid; // 路由器MAC地址（唯一标识）
    private String ssid;  // WiFi名称
    private int rssi;     // 信号强度（dBm）

    public WifiScanResult(String bssid, String ssid, int rssi) {
        this.bssid = bssid;
        this.ssid = ssid;
        this.rssi = rssi;
    }

    // Getter和Setter
    public String getBssid() { return bssid; }
    public String getSsid() { return ssid; }
    public int getRssi() { return rssi; }
    public void setRssi(int rssi) { this.rssi = rssi; }
}