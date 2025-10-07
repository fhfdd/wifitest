package com.example.mywifiscanner;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PointF;
import android.net.Uri;
import android.widget.ImageView;
import android.widget.Toast;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 图片处理工具类，负责加载、缩放、坐标计算和标记绘制（整合红点逻辑）
 */
public class ImageHandler {
    private Bitmap markerBitmap; // 用于临时存储带标记的图片
    private PointF currentMarker; // 记录当前标记位置
    private boolean isMarkersVisible = false; // 标识是否显示所有指纹标记
    private Context context;
    private ImageView imageView; // 显示图片的控件
    private Bitmap originalImage; // 原始图片
    // 缩放相关变量
    private Matrix matrix = new Matrix();
    private Matrix savedMatrix = new Matrix();
    private PointF start = new PointF();
    private PointF mid = new PointF();
    private float oldDist = 1f;
    private static final int NONE = 0;
    private static final int DRAG = 1;
    private static final int ZOOM = 2;
    private int mode = NONE;

    // 构造方法：关联上下文和图片控件
    public ImageHandler(Context context, ImageView imageView) {
        this.context = context;
        this.imageView = imageView;
        initImageView();
    }

    /**
     * 在指定位置绘制当前标记（单点标记）
     * @param x 图片像素X坐标
     * @param y 图片像素Y坐标
     */
    public void drawCurrentMarker(float x, float y) {
        if (originalImage == null) return;

        // 保存当前标记位置
        currentMarker = new PointF(x, y);

        // 复制原始图片，在副本上绘制标记（避免修改原图）
        markerBitmap = originalImage.copy(Bitmap.Config.ARGB_8888, true);
        Canvas canvas = new Canvas(markerBitmap);
        Paint paint = new Paint();
        paint.setColor(Color.RED);
        paint.setStyle(Paint.Style.FILL);
        paint.setAntiAlias(true);

        // 绘制红色圆点（半径8px）
        canvas.drawCircle(x, y, 8, paint);

        // 显示带标记的图片
        imageView.setImageBitmap(markerBitmap);
        imageView.setImageMatrix(matrix); // 保持当前缩放状态
    }

    /**
     * 清除当前标记（恢复显示原图或已有的指纹标记）
     * @param fingerprints 指纹列表（用于重新绘制所有标记）
     */
    public void clearCurrentMarker(List<WifiFingerprint> fingerprints) {
        if (currentMarker == null) return;

        currentMarker = null;
        // 恢复显示原图（如果没有其他指纹标记）或重新绘制所有指纹标记
        if (isMarkersVisible && fingerprints != null && !fingerprints.isEmpty()) {
            // 如果处于"显示所有指纹"状态，重新绘制所有指纹
            drawAllMarkers(fingerprints);
        } else {
            // 否则直接显示原图并保持矩阵状态
            imageView.setImageBitmap(originalImage);
            imageView.setImageMatrix(matrix);
        }
    }

    /**
     * 初始化ImageView配置（使用矩阵模式支持缩放拖动）
     */
    private void initImageView() {
        if (imageView != null) {
            imageView.setScaleType(ImageView.ScaleType.MATRIX);
        }
    }

    /**
     * 从Uri加载图片
     */
    public void loadImageFromUri(Uri imageUri) {
        if (imageView == null) {
            Toast.makeText(context, "图片控件未初始化", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            InputStream inputStream = context.getContentResolver().openInputStream(imageUri);
            originalImage = BitmapFactory.decodeStream(inputStream);
            if (originalImage == null) {
                Toast.makeText(context, "图片加载失败", Toast.LENGTH_SHORT).show();
                return;
            }
            resetMatrix(); // 重置缩放状态
            imageView.setImageBitmap(originalImage);
            Toast.makeText(context, "图片导入成功", Toast.LENGTH_SHORT).show();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            Toast.makeText(context, "图片不存在", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 重置图片矩阵（清除缩放/拖动状态）
     */
    public void resetMatrix() {
        matrix.reset();
        if (imageView != null) {
            imageView.setImageMatrix(matrix);
        }
    }

    /**
     * 处理触摸事件（缩放、拖动）
     */
    public boolean handleTouchEvent(android.view.MotionEvent event) {
        if (originalImage == null || imageView == null) return false;

        switch (event.getAction() & android.view.MotionEvent.ACTION_MASK) {
            case android.view.MotionEvent.ACTION_DOWN:
                savedMatrix.set(matrix);
                start.set(event.getX(), event.getY());
                mode = DRAG;
                break;
            case android.view.MotionEvent.ACTION_POINTER_DOWN:
                oldDist = spacing(event);
                if (oldDist > 10f) {
                    savedMatrix.set(matrix);
                    midPoint(mid, event);
                    mode = ZOOM;
                }
                break;
            case android.view.MotionEvent.ACTION_MOVE:
                if (mode == DRAG) {
                    matrix.set(savedMatrix);
                    matrix.postTranslate(event.getX() - start.x, event.getY() - start.y);
                } else if (mode == ZOOM) {
                    float newDist = spacing(event);
                    if (newDist > 10f) {
                        matrix.set(savedMatrix);
                        float scale = newDist / oldDist;
                        matrix.postScale(scale, scale, mid.x, mid.y);
                    }
                }
                break;
            case android.view.MotionEvent.ACTION_UP:
            case android.view.MotionEvent.ACTION_POINTER_UP:
                mode = NONE;
                break;
        }
        imageView.setImageMatrix(matrix);
        return true;
    }

    /**
     * 计算点击位置对应的原图坐标（屏幕坐标→图片像素坐标）
     */
    public float[] calculatePixelCoordinates(float x, float y) {
        if (originalImage == null) return null;

        Matrix inverse = new Matrix();
        if (!matrix.invert(inverse)) return null; // 矩阵不可逆时返回null

        float[] points = new float[]{x, y};
        inverse.mapPoints(points); // 转换坐标

        // 限制坐标在图片范围内
        points[0] = Math.max(0, Math.min(points[0], originalImage.getWidth()));
        points[1] = Math.max(0, Math.min(points[1], originalImage.getHeight()));
        return points;
    }

    /**
     * 获取所有指纹标记的坐标
     */
    public List<PointF> getMarkerPositions(List<WifiFingerprint> fingerprints) {
        List<PointF> positions = new ArrayList<>();
        for (WifiFingerprint fingerprint : fingerprints) {
            positions.add(new PointF((float) fingerprint.getPixelX(), (float) fingerprint.getPixelY()));
        }
        return positions;
    }

    /**
     * 检查点击是否命中指纹标记（15像素范围内）
     */
    public WifiFingerprint getClickedFingerprint(float x, float y, List<WifiFingerprint> fingerprints) {
        if (fingerprints == null || fingerprints.isEmpty()) return null;

        float[] points = calculatePixelCoordinates(x, y);
        if (points == null) return null;

        for (WifiFingerprint fingerprint : fingerprints) {
            double distance = Math.sqrt(
                    Math.pow(points[0] - fingerprint.getPixelX(), 2) +
                            Math.pow(points[1] - fingerprint.getPixelY(), 2)
            );
            if (distance < 15) { // 15像素内视为命中
                return fingerprint;
            }
        }
        return null;
    }

    /**
     * 绘制所有指纹标记（红点+标签）
     */
    public void drawAllMarkers(List<WifiFingerprint> fingerprints) {
        if (originalImage == null || fingerprints == null || fingerprints.isEmpty()) return;

        // 创建可编辑的图片副本
        Bitmap markedBitmap = originalImage.copy(Bitmap.Config.ARGB_8888, true);
        Canvas canvas = new Canvas(markedBitmap);

        // 配置画笔
        Paint circlePaint = new Paint();
        circlePaint.setColor(Color.RED);
        circlePaint.setStyle(Paint.Style.FILL);
        circlePaint.setAntiAlias(true);

        Paint textPaint = new Paint();
        textPaint.setColor(Color.BLACK);
        textPaint.setTextSize(30);
        textPaint.setAntiAlias(true);

        // 逐个绘制标记
        for (WifiFingerprint fingerprint : fingerprints) {
            float x = (float) fingerprint.getPixelX();
            float y = (float) fingerprint.getPixelY();
            // 绘制红点（半径15像素）
            canvas.drawCircle(x, y, 15, circlePaint);
            // 绘制标签或坐标
            String text = fingerprint.getLabel() != null && !fingerprint.getLabel().isEmpty()
                    ? fingerprint.getLabel()
                    : String.format("(%.0f,%.0f)", x, y);
            canvas.drawText(text, x - 40, y + 40, textPaint);
        }

        imageView.setImageBitmap(markedBitmap);
        imageView.setImageMatrix(matrix);
        isMarkersVisible = true; // 更新标记显示状态
    }

    /**
     * 绘制定位标记（单个红点+可选标签）
     */
    public void drawLocationMarker(float x, float y, String label) {
        if (originalImage == null) return;

        Bitmap markedBitmap = originalImage.copy(Bitmap.Config.ARGB_8888, true);
        Canvas canvas = new Canvas(markedBitmap);

        // 绘制红点
        Paint circlePaint = new Paint();
        circlePaint.setColor(Color.RED);
        circlePaint.setStyle(Paint.Style.FILL);
        circlePaint.setAntiAlias(true);
        canvas.drawCircle(x, y, 15, circlePaint);

        // 绘制标签
        if (label != null && !label.isEmpty()) {
            Paint textPaint = new Paint();
            textPaint.setColor(Color.BLACK);
            textPaint.setTextSize(30);
            textPaint.setAntiAlias(true);
            canvas.drawText(label, x - 40, y + 40, textPaint);
        }

        imageView.setImageBitmap(markedBitmap);
        imageView.setImageMatrix(matrix);
    }

    /**
     * 重载：绘制定位标记（仅红点）
     */
    public void drawLocationMarker(float x, float y) {
        drawLocationMarker(x, y, null);
    }

    /**
     * 计算两点间距（缩放用）
     */
    private float spacing(android.view.MotionEvent event) {
        float x = event.getX(0) - event.getX(1);
        float y = event.getY(0) - event.getY(1);
        return (float) Math.sqrt(x * x + y * y);
    }

    /**
     * 计算两点中点（缩放中心用）
     */
    private void midPoint(PointF point, android.view.MotionEvent event) {
        float x = event.getX(0) + event.getX(1);
        float y = event.getY(0) + event.getY(1);
        point.set(x / 2, y / 2);
    }

    // Getter和Setter
    public Bitmap getOriginalImage() {
        return originalImage;
    }

    public ImageView getImageView() {
        return imageView;
    }

    public void setImageView(ImageView imageView) {
        this.imageView = imageView;
        initImageView();
    }

    public boolean isMarkersVisible() {
        return isMarkersVisible;
    }

    public void setMarkersVisible(boolean markersVisible) {
        isMarkersVisible = markersVisible;
    }
}