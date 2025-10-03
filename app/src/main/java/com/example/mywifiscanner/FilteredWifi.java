package com.example.mywifiscanner;

// FilteredWifi.java
public class FilteredWifi {
    private String ssid; // WiFi名称
    private String bssid; // WiFi的MAC地址（唯一标识）
    private int rssi; // 信号强度（dBm）

    // 保留三参数构造函数
    public FilteredWifi(String ssid, String bssid, int rssi) {
        this.ssid = ssid;
        this.bssid = bssid;
        this.rssi = rssi;
    }

    // 保留原有的Getter和Setter
    public String getSsid() { return ssid; }
    public void setSsid(String ssid) { this.ssid = ssid; }

    public String getBssid() { return bssid; }
    public void setBssid(String bssid) { this.bssid = bssid; }

    public int getRssi() { return rssi; }
    public void setRssi(int rssi) { this.rssi = rssi; }
}