package com.example.mywifiscanner;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * WiFi指纹数据模型：存储单个点位的WiFi指纹信息
 */
public class WifiFingerprint {

    @SerializedName("pixelX") // 对应JSON中的"x"字段
    private double pixelX;
    @SerializedName("pixelY") // 对应JSON中的"y"字段
    private double pixelY;
    @SerializedName("floor")
    private int floor;
    @SerializedName("zone")
    private String zone;
    @SerializedName("label")
    private String label;
    @SerializedName("path")
    private String path;
    @SerializedName("wifis") // 对应JSON中的"wifis"数组
    private List<FilteredWifi> filteredWifis;

    // 默认构造方法（JSON序列化需要）
    public WifiFingerprint() {}

    // Getter和Setter
    public double getPixelX() { return pixelX; }
    public void setPixelX(double pixelX) { this.pixelX = pixelX; }

    public double getPixelY() { return pixelY; }
    public void setPixelY(double pixelY) { this.pixelY = pixelY; }

    public int getFloor() { return floor; }
    public void setFloor(int floor) { this.floor = floor; }

    public String getZone() { return zone; }
    public void setZone(String zone) { this.zone = zone; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }

    public List<FilteredWifi> getFilteredWifis() { return filteredWifis; }
    public void setFilteredWifis(List<FilteredWifi> filteredWifis) { this.filteredWifis = filteredWifis; }


}