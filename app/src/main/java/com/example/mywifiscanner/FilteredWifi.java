package com.example.mywifiscanner;
import com.google.gson.annotations.SerializedName;

// FilteredWifi.java
public class FilteredWifi {
    @SerializedName("ssid")
    private String ssid;
    @SerializedName("bssid")
    private String bssid;
    @SerializedName("rssi")
    private int rssi;

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