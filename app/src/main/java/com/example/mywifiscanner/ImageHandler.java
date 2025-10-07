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

public class ImageHandler {
    private boolean showGrid = false; // 控制是否显示网格（本地变量，替代MapData配置）
    private int gridSize = 50; // 网格大小（默认50像素，替代MapData配置）
    private Context context;
    private List<PointF> redPoints = new ArrayList<>();
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
    private Paint gridPaint; // 网格画笔

    // 无参构造（初始化用）
    public ImageHandler(Context context) {
        this.context = context;
    }

    // 带ImageView的构造（关联显示控件）
    public ImageHandler(Context context, ImageView imageView) {
        this.context = context;
        this.imageView = imageView;
        if (imageView != null) {
            initImageView();
        }
    }

    // 设置ImageView（后续关联控件用）
    public void setImageView(ImageView imageView) {
        this.imageView = imageView;
        if (imageView != null) {
            initImageView();
        }
    }

    // 控制网格显示（替代原setGridConfig）
    public void setGrid(boolean show, int size) {
        this.showGrid = show;
        this.gridSize = size > 0 ? size : 50; // 确保网格大小为正数
        invalidate();
    }

    // 重绘图片
    public void invalidate() {
        if (imageView != null) {
            imageView.invalidate();
        }
    }

    /**
     * 初始化ImageView配置（设置缩放模式）
     */
    private void initImageView() {
        if (imageView == null) return;
        // 改为与布局一致的 fitXY 模式，PhotoView 支持
        imageView.setScaleType(ImageView.ScaleType.FIT_XY);
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
        if (imageView == null) return;
        matrix.reset();
        imageView.setImageMatrix(matrix);
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
        if (originalImage == null || imageView == null) return null;

        Matrix inverse = new Matrix();
        if (!matrix.invert(inverse)) return null; // 矩阵不可逆时返回null

        float[] points = new float[]{x, y};
        inverse.mapPoints(points); // 转换坐标

        // 限制坐标在图片范围内（避免超出边界）
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
    public WifiFingerprint getClickedFingerprint(float screenX, float screenY, List<WifiFingerprint> fingerprints) {
        if (originalImage == null || imageView == null) return null;

        float[] pixelCoords = calculatePixelCoordinates(screenX, screenY);
        if (pixelCoords == null) return null;

        for (WifiFingerprint fp : fingerprints) {
            double distance = Math.sqrt(
                    Math.pow(pixelCoords[0] - fp.getPixelX(), 2) +
                            Math.pow(pixelCoords[1] - fp.getPixelY(), 2)
            );
            if (distance < 15) { // 15像素内视为命中
                return fp;
            }
        }
        return null;
    }

    /**
     * 绘制所有指纹标记（含可选网格）
     */
    public void drawAllMarkers(List<WifiFingerprint> fingerprints) {
        if (originalImage == null || imageView == null) return;

        // 创建可编辑的图片副本
        Bitmap markedBitmap = originalImage.copy(Bitmap.Config.ARGB_8888, true);
        Canvas canvas = new Canvas(markedBitmap);

        // 1. 绘制网格（若开启）
        if (showGrid) {
            drawGrid(canvas, gridSize);
        }

        // 2. 绘制指纹标记（红色圆点+描述）
        Paint circlePaint = new Paint();
        circlePaint.setColor(Color.RED);
        circlePaint.setStyle(Paint.Style.FILL);
        circlePaint.setAntiAlias(true);

        Paint textPaint = new Paint();
        textPaint.setColor(Color.BLACK);
        textPaint.setTextSize(30);
        textPaint.setAntiAlias(true);

        for (WifiFingerprint fp : fingerprints) {
            float x = (float) fp.getPixelX();
            float y = (float) fp.getPixelY();
            // 绘制红色圆点（半径15像素）
            canvas.drawCircle(x, y, 15, circlePaint);
            // 绘制描述文本（特殊点显示标签，普通点显示坐标）
            String text = fp.getLabel() != null && !fp.getLabel().isEmpty()
                    ? fp.getLabel()
                    : String.format("(%.0f,%.0f)", x, y);
            canvas.drawText(text, x - 40, y + 40, textPaint);
        }

        // 更新ImageView显示
        imageView.setImageBitmap(markedBitmap);
        imageView.setImageMatrix(matrix);
    }

    /**
     * 绘制网格（半透明灰色）
     */
    private void drawGrid(Canvas canvas, int gridSize) {
        if (gridSize <= 0 || originalImage == null) return;

        if (gridPaint == null) {
            gridPaint = new Paint();
            gridPaint.setColor(Color.GRAY);
            gridPaint.setStrokeWidth(1f);
            gridPaint.setAlpha(128); // 半透明，不遮挡图片
        }

        int width = originalImage.getWidth();
        int height = originalImage.getHeight();

        // 绘制垂直线（X轴方向）
        for (int x = gridSize; x < width; x += gridSize) {
            canvas.drawLine(x, 0, x, height, gridPaint);
        }

        // 绘制水平线（Y轴方向）
        for (int y = gridSize; y < height; y += gridSize) {
            canvas.drawLine(0, y, width, y, gridPaint);
        }
    }

    /**
     * 绘制定位标记（红色圆点+标签）
     */
    public void drawLocationMarker(float x, float y, String label) {
        if (originalImage == null || imageView == null) return;

        Bitmap markedBitmap = originalImage.copy(Bitmap.Config.ARGB_8888, true);
        Canvas canvas = new Canvas(markedBitmap);

        // 绘制红色圆点
        Paint paint = new Paint();
        paint.setColor(Color.RED);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(x, y, 15, paint);

        // 绘制标签（若有）
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


    // 显示坐标提示
    public void showCoordinateToast(float x, float y) {
        String coordText = String.format("坐标: (%.0f, %.0f)", x, y);
        Toast.makeText(context, coordText, Toast.LENGTH_SHORT).show();
    }

    // ==================== 工具方法（计算间距、中点） ====================
    private float spacing(android.view.MotionEvent event) {
        float x = event.getX(0) - event.getX(1);
        float y = event.getY(0) - event.getY(1);
        return (float) Math.sqrt(x * x + y * y);
    }

    private void midPoint(PointF point, android.view.MotionEvent event) {
        float x = event.getX(0) + event.getX(1);
        float y = event.getY(0) + event.getY(1);
        point.set(x / 2, y / 2);
    }

    // ==================== Getter ====================
    public Bitmap getOriginalImage() {
        return originalImage;
    }

    public ImageView getImageView() {
        return imageView;
    }

    // 网格控制相关Getter/Setter
    public boolean isShowGrid() {
        return showGrid;
    }

    public int getGridSize() {
        return gridSize;
    }
}