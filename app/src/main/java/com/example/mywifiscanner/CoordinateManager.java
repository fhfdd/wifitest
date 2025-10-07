package com.example.mywifiscanner;

import android.graphics.Bitmap;
import android.graphics.PointF;

import java.util.List;

/**
 * 坐标管理器 - 处理所有坐标相关操作（依赖ImageHandler）
 */
public class CoordinateManager {
    private ImageHandler imageHandler;

    public CoordinateManager(ImageHandler imageHandler) {
        this.imageHandler = imageHandler;
    }

    /**
     * 获取触摸点的图片坐标
     */
    public float[] getCoordinatesFromTouch(float screenX, float screenY) {
        return imageHandler.calculatePixelCoordinates(screenX, screenY);
    }

    /**
     * 验证坐标是否在图片范围内
     */
    public boolean isValidCoordinate(float x, float y) {
        Bitmap image = imageHandler.getOriginalImage();
        if (image == null) return false;

        return x >= 0 && x <= image.getWidth() && y >= 0 && y <= image.getHeight();
    }

    /**
     * 获取所有标记的坐标信息
     */
    public List<PointF> getMarkerPositions(List<WifiFingerprint> fingerprints) {
        return imageHandler.getMarkerPositions(fingerprints);
    }

    /**
     * 检查点击是否在标记范围内
     */
    public WifiFingerprint getClickedFingerprint(float screenX, float screenY, List<WifiFingerprint> fingerprints) {
        return imageHandler.getClickedFingerprint(screenX, screenY, fingerprints);
    }
}