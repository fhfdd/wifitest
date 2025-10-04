package com.example.mywifiscanner;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PointF;
import android.util.AttributeSet;

import com.github.chrisbanes.photoview.PhotoView;

import java.util.ArrayList;
import java.util.List;

public class RedPointPhotoView extends PhotoView {
    private List<PointF> redPoints = new ArrayList<>(); // 存储所有红点坐标
    private Paint redPaint;

    public RedPointPhotoView(Context context) {
        super(context);
        initPaint();
    }

    public RedPointPhotoView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initPaint();
    }

    // 初始化红点画笔（红色、圆形）
    private void initPaint() {
        redPaint = new Paint();
        redPaint.setColor(Color.RED);
        redPaint.setStyle(Paint.Style.FILL);
        redPaint.setAntiAlias(true);
        redPaint.setStrokeWidth(10); // 红点大小
    }

    // 1. 点击添加红点（需结合PhotoView的缩放比例转换坐标）
    public void addRedPoint(float x, float y) {
        // 将View的点击坐标转为图片实际坐标（处理缩放）
        Matrix inverseMatrix = new Matrix();
        getImageMatrix().invert(inverseMatrix);
        float[] points = {x, y};
        inverseMatrix.mapPoints(points);
        redPoints.add(new PointF(points[0], points[1]));
        invalidate(); // 重绘
    }

    // 2. 取消查看坐标（清空所有红点）
    public void clearRedPoints() {
        redPoints.clear();
        invalidate(); // 重绘
    }

    // 3. 绘制红点（覆盖在图片上）
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas); // 先绘制图片
        // 绘制所有红点
        for (PointF point : redPoints) {
            canvas.drawCircle(point.x, point.y, 5, redPaint); // 半径5dp
        }
    }

    // 获取当前红点列表（用于保存）
    public List<PointF> getRedPoints() {
        return redPoints;
    }
}