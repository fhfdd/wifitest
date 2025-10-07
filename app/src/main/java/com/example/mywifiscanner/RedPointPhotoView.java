package com.example.mywifiscanner;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.Log;

import com.github.chrisbanes.photoview.PhotoView;

public class RedPointPhotoView extends PhotoView {
    private PointF currentRedPoint = null; // 单个红点（新点覆盖旧点）
    private Paint redPaint;
    private RectF imageDisplayRect = new RectF(); // 存储图片在屏幕上的实际显示区域

    // 构造函数
    public RedPointPhotoView(Context context) {
        super(context);
        initRedPaint();
    }

    public RedPointPhotoView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initRedPaint();
    }

    // 初始化红点画笔
    private void initRedPaint() {
        redPaint = new Paint();
        redPaint.setColor(Color.RED);
        redPaint.setStyle(Paint.Style.FILL);
        redPaint.setAntiAlias(true);
    }

    /**
     * 核心方法：添加红点（仅在点击图片内容时生效）
     * @param screenX 屏幕X坐标
     * @param screenY 屏幕Y坐标
     */
    public void addRedPoint(float screenX, float screenY) {
        // 1. 先计算图片在屏幕上的实际显示区域（关键：排除空白区域）
        calculateImageDisplayRect();

        // 2. 判断点击坐标是否在图片显示区域内（不在则不显示红点）
        if (!imageDisplayRect.contains(screenX, screenY)) {
            Log.d("RedPoint", "点击在图片空白区域，不显示红点");
            clearRedPoint(); // 清除旧红点（若点击空白）
            return;
        }

        // 3. 转换屏幕坐标为图片实际像素坐标（确保红点在图片内容上）
        Matrix inverseMatrix = new Matrix();
        getImageMatrix().invert(inverseMatrix);
        float[] points = {screenX, screenY};
        inverseMatrix.mapPoints(points);

        // 4. 覆盖旧红点，保存新红点坐标
        currentRedPoint = new PointF(points[0], points[1]);
        invalidate(); // 重绘显示红点
    }

    /**
     * 计算图片在屏幕上的实际显示区域（关键：获取图片可见部分的屏幕坐标）
     */
    private void calculateImageDisplayRect() {
        if (getDrawable() == null) return; // 图片未加载时不计算

        // 图片原始尺寸
        int imageWidth = getDrawable().getIntrinsicWidth();
        int imageHeight = getDrawable().getIntrinsicHeight();
        RectF originalRect = new RectF(0, 0, imageWidth, imageHeight);

        // 通过图片矩阵，将原始尺寸转换为屏幕上的实际显示尺寸和位置
        Matrix imageMatrix = getImageMatrix();
        imageMatrix.mapRect(imageDisplayRect, originalRect);
        Log.d("RedPoint", "图片显示区域（屏幕坐标）: " + imageDisplayRect);
    }

    // 清除红点
    public void clearRedPoint() {
        currentRedPoint = null;
        invalidate();
    }

    // 重绘：先画图片，再画红点（仅在图片内容上）
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas); // 先绘制图片内容
        if (currentRedPoint != null && getDrawable() != null) {
            // 红点半径8px（可调整）
            canvas.drawCircle(currentRedPoint.x, currentRedPoint.y, 8, redPaint);
        }
    }

    // 暴露方法：获取图片显示区域（供外部判断，可选）
    public RectF getImageDisplayRect() {
        calculateImageDisplayRect();
        return imageDisplayRect;
    }
}