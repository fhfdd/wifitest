package com.example.mywifiscanner;

import java.util.List;

/**
 * WiFi指纹数据模型：存储单个点位的WiFi指纹信息
 */
public class WifiFingerprint {
    private double pixelX; // 像素X坐标
    private double pixelY; // 像素Y坐标
    private int floor; // 楼层
    private String zone; // 区域
    private String label; // 特殊点标签（可为null）
    private String path; // 普通点路径（可为null）
    private List<FilteredWifi> filteredWifis; // 过滤后的WiFi信号列表

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