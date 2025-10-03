package com.example.mywifiscanner;

import java.util.List;

/**
 * 地图数据模型类，用于存储地图相关的所有数据（点位、网格配置、文件名等）
 */
public class MapData {
    // 文件名（用于标识当前地图文件）
    private String fileName;
    // 点位数据列表（包含所有特殊点和普通点）
    private List<PointData> points;
    // 网格配置（是否显示网格、网格缩放比例等）
    private GridConfig gridConfig;

    // 默认构造方法（必须，用于JSON序列化/反序列化）
    public MapData() {}

    // 带参构造方法（可选，方便初始化）
    public MapData(String fileName, List<PointData> points, GridConfig gridConfig) {
        this.fileName = fileName;
        this.points = points;
        this.gridConfig = gridConfig;
    }

    // ==================== 文件名相关的Get/Set方法 ====================
    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    // ==================== 点位数据相关的Get/Set方法 ====================
    public List<PointData> getPoints() {
        return points;
    }

    public void setPoints(List<PointData> points) {
        this.points = points;
    }

    // ==================== 网格配置相关的Get/Set方法 ====================
    public GridConfig getGridConfig() {
        return gridConfig;
    }

    public void setGridConfig(GridConfig gridConfig) {
        this.gridConfig = gridConfig;
    }

    /**
     * 内部类：点位数据模型（单个点位的信息）
     */
    public static class PointData {
        // 是否为特殊点（true：特殊点；false：普通点）
        private boolean isSpecial;
        // 特殊点的标签（如“图书馆入口”，仅特殊点有效）
        private String label;
        // 普通点的路径描述（如“距离入口5米”，仅普通点有效）
        private String path;
        // 点位在图片上的X坐标（像素）
        private float x;
        // 点位在图片上的Y坐标（像素）
        private float y;

        // 默认构造方法
        public PointData() {}

        // 带参构造方法
        public PointData(boolean isSpecial, String label, String path, float x, float y) {
            this.isSpecial = isSpecial;
            this.label = label;
            this.path = path;
            this.x = x;
            this.y = y;
        }

        // Get/Set方法
        public boolean isSpecial() {
            return isSpecial;
        }

        public void setSpecial(boolean special) {
            isSpecial = special;
        }

        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public float getX() {
            return x;
        }

        public void setX(float x) {
            this.x = x;
        }

        public float getY() {
            return y;
        }

        public void setY(float y) {
            this.y = y;
        }
    }

    /**
     * 内部类：网格配置模型（地图网格的显示参数）
     */
    public static class GridConfig {
        // 是否显示网格
        private boolean isShow;
        // 网格缩放比例（单位：像素）
        private int scale;

        // 默认构造方法
        public GridConfig() {}

        // 带参构造方法
        public GridConfig(boolean isShow, int scale) {
            this.isShow = isShow;
            this.scale = scale;
        }

        // Get/Set方法
        public boolean isShow() {
            return isShow;
        }

        public void setShow(boolean show) {
            isShow = show;
        }

        public int getScale() {
            return scale;
        }

        public void setScale(int scale) {
            this.scale = scale;
        }
    }
}