package com.example.mywifiscanner;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.view.GestureDetectorCompat;

import android.Manifest;
import android.content.Context;
import androidx.core.content.ContextCompat;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PointF;
import android.location.LocationManager;
import android.os.Build;
import android.provider.Settings; // 用于跳转设置页面
import android.net.Uri;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.util.FloatMath;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    // 配置参数（可根据需求调整）
    private static final int MIN_SCAN_COUNT = 3; // 一个位置至少扫描3次
    private static final int REQUEST_LOCATION_PERMISSION = 102; // 新增定位权限请求码
    private static final int MIN_SELECT_WIFI_COUNT = 3; // 手动选择至少3个WiFi
    private List<WifiFingerprint.FilteredWifi> filteredWifis; // 筛选后的所有稳定WiFi
    private List<WifiFingerprint.FilteredWifi> selectedWifis = new ArrayList<>(); // 用户手动选择的WiFi
    private static final int REQUEST_READ_STORAGE = 101;

    // 控件变量
// 控件变量
    private Button btnImportImage, scanButton, btnSelectWifi, btnOneClickSave, btnExport, btnRealTimeLocate; // 新增btnRealTimeLocate声明
    private ImageView imageView; // 替换为原生ImageView
    private TextView wifiText;
    private EditText etPixelX, etPixelY, etFloor;
    private WifiManager wifiManager;
    private FingerprintManager fingerprintManager;
    private List<List<ScanResult>> multipleScans = new ArrayList<>();
    private Handler handler = new Handler();
    private boolean isScanning = false;
    private Bitmap originalImage;

    // 图片缩放相关变量（原生实现）
    private Matrix matrix = new Matrix();
    private Matrix savedMatrix = new Matrix();
    private PointF start = new PointF();
    private PointF mid = new PointF();
    private float oldDist = 1f;
    private static final int NONE = 0;
    private static final int DRAG = 1;
    private static final int ZOOM = 2;
    private int mode = NONE;

    // 权限相关
    private static final int REQUEST_CODE_PERMISSIONS = 100;
    private static final String[] REQUIRED_PERMISSIONS = {
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_STATE
    };

    // 图片选择启动器
    private ActivityResultLauncher<Intent> imagePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri imageUri = result.getData().getData();
                    if (imageUri != null) {
                        loadImageFromUri(imageUri);
                    }
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 初始化所有控件
        initAllViews();

        // 初始化工具类
        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        fingerprintManager = new FingerprintManager(this);

        // 绑定所有按钮事件
        setAllButtonListeners();

        // 设置图片触摸监听（原生实现缩放和点击）
        setupImageTouchListener();
    }

    // 初始化所有控件
    private void initAllViews() {
        // 图片相关控件（替换为ImageView）
        btnImportImage = findViewById(R.id.btnImportImage);
        imageView = findViewById(R.id.imageView);
        imageView.setScaleType(ImageView.ScaleType.MATRIX); // 必须设置为MATRIX才能手动控制缩放

        // 输入控件
        etPixelX = findViewById(R.id.etPixelX);
        etPixelY = findViewById(R.id.etPixelY);
        etFloor = findViewById(R.id.etFloor);

        btnRealTimeLocate = findViewById(R.id.btnRealTimeLocate);
        btnRealTimeLocate.setEnabled(false); // 初始禁用，导入图片后启用

        // 功能按钮
        scanButton = findViewById(R.id.scanButton);
        btnSelectWifi = findViewById(R.id.btnSelectWifi);
        btnOneClickSave = findViewById(R.id.btnOneClickSave);
        btnExport = findViewById(R.id.btnExport);

        // 显示控件
        wifiText = findViewById(R.id.wifiText);

        // 初始禁用选择和保存按钮
        btnSelectWifi.setEnabled(false);
        btnOneClickSave.setEnabled(false);
    }

    // 绑定所有按钮事件
    private void setAllButtonListeners() {


// 1. 导入图片

        btnImportImage.setOnClickListener(v -> {
            String[] permissions;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissions = new String[]{Manifest.permission.READ_MEDIA_IMAGES};
            } else {
                permissions = new String[]{Manifest.permission.READ_EXTERNAL_STORAGE};
            }

            if (ContextCompat.checkSelfPermission(this, permissions[0]) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, permissions, REQUEST_READ_STORAGE);
            } else {
                Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                intent.setType("image/*");
                imagePickerLauncher.launch(intent);
            }
        });

        btnImportImage.setOnClickListener(v -> {
            // 检查权限（根据系统版本适配）
            boolean hasPermission;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // Android 13+ 用 READ_MEDIA_IMAGES
                hasPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
                        == PackageManager.PERMISSION_GRANTED;
            } else {
                // 旧版本用 READ_EXTERNAL_STORAGE
                hasPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                        == PackageManager.PERMISSION_GRANTED;
            }

            if (!hasPermission) {
                // 判断是否需要向用户解释为什么需要权限
                if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                                ? Manifest.permission.READ_MEDIA_IMAGES
                                : Manifest.permission.READ_EXTERNAL_STORAGE)) {
                    // 用户之前拒绝过，显示弹窗说明原因，并引导去设置开启
                    new AlertDialog.Builder(this)
                            .setTitle("需要存储权限")
                            .setMessage("导入图片需要访问您的相册，请在设置中开启存储权限")
                            .setPositiveButton("去设置", (dialog, which) -> {
                                // 跳转到应用权限设置页面
                                Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                                Uri uri = Uri.fromParts("package", getPackageName(), null);
                                intent.setData(uri);
                                startActivity(intent);
                            })
                            .setNegativeButton("取消", null)
                            .show();
                } else {
                    // 第一次请求权限，直接弹窗
                    String[] permissions = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                            ? new String[]{Manifest.permission.READ_MEDIA_IMAGES}
                            : new String[]{Manifest.permission.READ_EXTERNAL_STORAGE};
                    ActivityCompat.requestPermissions(this, permissions, REQUEST_READ_STORAGE);
                }
            } else {
                // 已有权限，启动图片选择
                Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                intent.setType("image/*");
                imagePickerLauncher.launch(intent);
            }
        });
        
// 2. 开始扫描
        scanButton.setOnClickListener(v -> {
            if (isScanning) {
                Toast.makeText(this, "正在扫描中，请勿重复点击", Toast.LENGTH_SHORT).show();
                return;
            }
            // 先检查位置权限
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                        REQUEST_LOCATION_PERMISSION);
                return;
            }
            // 位置权限已授予，检查位置服务是否开启
            if (isLocationEnabled()) {
                startMultipleScans();
            } else {
                new AlertDialog.Builder(this)
                        .setTitle("需要位置服务")
                        .setMessage("扫描 WiFi 需要开启位置服务，是否去设置开启？")
                        .setPositiveButton("去设置", (dialog, which) -> {
                            Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                            startActivity(intent);
                        })
                        .setNegativeButton("取消", null)
                        .show();
            }
        });



        // 3. 手动选择WiFi
        btnSelectWifi.setOnClickListener(v -> {
            // 先触发筛选，再弹出选择对话框
            if (multipleScans.size() < MIN_SCAN_COUNT) {
                Toast.makeText(this, "扫描次数不足！需至少扫描" + MIN_SCAN_COUNT + "次", Toast.LENGTH_SHORT).show();
                return;
            }
            if (filteredWifis == null) {
                filteredWifis = fingerprintManager.filterAndSortWifi(multipleScans);
                if (filteredWifis.isEmpty()) {
                    Toast.makeText(this, "无符合条件的稳定WiFi", Toast.LENGTH_SHORT).show();
                    return;
                }
            }
            // 弹出选择对话框
            showWifiSelectDialog();
        });

        // 4. 一键保存
        btnOneClickSave.setOnClickListener(v -> {
            // ① 校验基础条件（图片、像素、楼层）
            if (!checkBasicConditions()) return;

            // ② 校验扫描次数（至少MIN_SCAN_COUNT次）
            if (multipleScans.size() < MIN_SCAN_COUNT) {
                Toast.makeText(this, "扫描次数不足！需至少扫描" + MIN_SCAN_COUNT + "次", Toast.LENGTH_SHORT).show();
                return;
            }

            // ③ 直接用筛选后的所有WiFi保存（无需手动选择）
            saveFingerprintToPixel(filteredWifis);
        });

        // 5. 导出指纹
        btnExport.setOnClickListener(v -> {
            String exportResult = fingerprintManager.exportFingerprints();
            Toast.makeText(this, exportResult, Toast.LENGTH_LONG).show();
            wifiText.setText("导出结果：\n" + exportResult);
        });

        // 6. 实时定位按钮
        btnRealTimeLocate.setOnClickListener(v -> {
            // 检查基础条件
            if (originalImage == null) {
                Toast.makeText(this, "请先导入校园平面图", Toast.LENGTH_SHORT).show();
                return;
            }
            if (fingerprintManager.getAllFingerprints().isEmpty()) {
                Toast.makeText(this, "请先采集并保存指纹数据", Toast.LENGTH_SHORT).show();
                return;
            }
            // 检查权限并开始定位
            if (checkLocationPermission() && isLocationEnabled()) {
                startRealTimeLocation(); // 执行实时定位逻辑
            }
        });
    }

    private boolean isLocationEnabled() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // 对于Android 10及以上，我们可以使用LocationManager
            LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            return locationManager.isLocationEnabled();
        } else {
            // 对于旧版本，检查位置模式
            int mode = Settings.Secure.getInt(getContentResolver(), Settings.Secure.LOCATION_MODE, Settings.Secure.LOCATION_MODE_OFF);
            return (mode != Settings.Secure.LOCATION_MODE_OFF);
        }
    }

    // 假设在 MainActivity 中新增方法
// 实时定位并在图片上标记位置
    private void startRealTimeLocation() {
        // 1. 检查权限（再次确认）
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        // 2. 实时扫描当前WiFi
        boolean scanSuccess = wifiManager.startScan();
        if (!scanSuccess) {
            Toast.makeText(this, "扫描WiFi失败，请重试", Toast.LENGTH_SHORT).show();
            return;
        }
        List<ScanResult> currentScans = wifiManager.getScanResults();
        if (currentScans.isEmpty()) {
            Toast.makeText(this, "未扫描到WiFi，无法定位", Toast.LENGTH_SHORT).show();
            return;
        }

        // 3. 筛选当前有效WiFi
        List<WifiFingerprint.FilteredWifi> currentFilteredWifis = filterCurrentWifi(currentScans);
        if (currentFilteredWifis.isEmpty()) {
            Toast.makeText(this, "无有效WiFi信号，无法定位", Toast.LENGTH_SHORT).show();
            return;
        }

        // 4. 与指纹库匹配
        WifiFingerprint matchedFingerprint = matchWithFingerprintLibrary(currentFilteredWifis);
        if (matchedFingerprint == null) {
            wifiText.setText("未匹配到位置，请在更多区域采集指纹");
            return;
        }

        // 5. 在图片上标记定位结果
        double x = matchedFingerprint.getPixelX();
        double y = matchedFingerprint.getPixelY();
        int floor = matchedFingerprint.getFloor();
        drawLocationMarker((float) x, (float) y); // 绘制标记

        // 6. 显示定位信息
        wifiText.setText("定位成功：\n楼层：" + floor + "\n像素坐标：(" + (int)x + "," + (int)y + ")");
    }

    // 在图片上绘制定位标记（红色圆点）
    private void drawLocationMarker(float x, float y) {
        if (originalImage == null) return;

        // 创建可修改的位图
        Bitmap markedBitmap = originalImage.copy(Bitmap.Config.ARGB_8888, true);
        Canvas canvas = new Canvas(markedBitmap);
        Paint paint = new Paint();
        paint.setColor(Color.RED); // 标记颜色
        paint.setStyle(Paint.Style.FILL);
        paint.setAntiAlias(true); // 抗锯齿
        canvas.drawCircle(x, y, 15, paint); // 绘制圆点（半径15px）

        // 显示标记后的图片
        imageView.setImageBitmap(markedBitmap);
        imageView.setImageMatrix(matrix); // 保持之前的缩放状态
    }

    // 筛选当前 WiFi（简化版，可复用 filterAndSortWifi 逻辑）
    private List<WifiFingerprint.FilteredWifi> filterCurrentWifi(List<ScanResult> currentScans) {
        List<WifiFingerprint.FilteredWifi> result = new ArrayList<>();
        for (ScanResult scan : currentScans) {
            if (scan.BSSID != null && scan.level >= -90) { // 筛选信号较强的 WiFi
                result.add(new WifiFingerprint.FilteredWifi(
                        scan.BSSID,
                        scan.SSID,
                        scan.level
                ));
            }
        }
        return result;
    }

    // 与指纹库匹配（核心：计算当前 WiFi 与指纹库中条目的相似度）
    private WifiFingerprint matchWithFingerprintLibrary(List<WifiFingerprint.FilteredWifi> currentWifis) {
        if (currentWifis.isEmpty() || fingerprintManager.getAllFingerprints().isEmpty()) {
            return null;
        }

        WifiFingerprint bestMatch = null;
        double highestSimilarity = 0;

        for (WifiFingerprint fingerprint : fingerprintManager.getAllFingerprints()) {
            List<WifiFingerprint.FilteredWifi> savedWifis = fingerprint.getFilteredWifis();
            double similarity = calculateSimilarity(currentWifis, savedWifis);
            if (similarity > highestSimilarity) {
                highestSimilarity = similarity;
                bestMatch = fingerprint;
            }
        }

        // 相似度阈值（比如超过 60% 才认为匹配成功）
        return highestSimilarity > 0.6 ? bestMatch : null;
    }

    // 计算 WiFi 列表的相似度（示例：简单计算共同 WiFi 的信号强度相似度）
    private double calculateSimilarity(List<WifiFingerprint.FilteredWifi> list1,
                                       List<WifiFingerprint.FilteredWifi> list2) {
        if (list1.isEmpty() || list2.isEmpty()) return 0;

        int matchedCount = 0;
        double signalSimilaritySum = 0;

        for (WifiFingerprint.FilteredWifi wifi1 : list1) {
            for (WifiFingerprint.FilteredWifi wifi2 : list2) {
                if (wifi1.getBssid().equals(wifi2.getBssid())) { // 匹配相同 MAC 的 WiFi
                    matchedCount++;
                    // 计算信号强度的相似度（0-1 之间）
                    int maxRssiDiff = 40; // 假设最大信号差为 40 dBm
                    int rssiDiff = Math.abs(wifi1.getRssi() - wifi2.getRssi());
                    double rssiSimilarity = 1 - (double) rssiDiff / maxRssiDiff;
                    signalSimilaritySum += rssiSimilarity;
                    break;
                }
            }
        }

        // 平均相似度 = 总信号相似度 / 匹配的 WiFi 数量
        return matchedCount > 0 ? (signalSimilaritySum / matchedCount) : 0;
    }

    // 在图片上绘制定位点（示例：简化版，实际可使用 Canvas 绘制）
    private void drawLocationOnImage(double pixelX, double pixelY) {
        // 这里需要用 Canvas 在 ImageView 上绘制一个圆点，标记定位位置
        // 示例代码（需要结合自定义 View 或 ImageView 的绘制逻辑）：
        Bitmap mutableBitmap = originalImage.copy(Bitmap.Config.ARGB_8888, true);
        Canvas canvas = new Canvas(mutableBitmap);
        Paint paint = new Paint();
        paint.setColor(Color.RED);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawCircle((float) pixelX, (float) pixelY, 10, paint); // 绘制红色圆点
        imageView.setImageBitmap(mutableBitmap);
    }

    // 加载选中的图片
    private void loadImageFromUri(Uri imageUri) {
        try {
            // 读取图片流，获取原图
            InputStream inputStream = getContentResolver().openInputStream(imageUri);
            originalImage = BitmapFactory.decodeStream(inputStream);
            if (originalImage == null) {
                Toast.makeText(this, "图片加载失败，请选其他图片", Toast.LENGTH_SHORT).show();
                return;
            }

            // 重置矩阵（清除之前的缩放状态）
            matrix.reset();
            imageView.setImageMatrix(matrix);
            // 显示图片
            imageView.setImageBitmap(originalImage);
            wifiText.setText("图片导入成功！点击图片任意位置获取像素点（可双指缩放/拖动图片）");
            // 图片导入成功后启用定位按钮
            btnRealTimeLocate.setEnabled(true);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            Toast.makeText(this, "图片不存在", Toast.LENGTH_SHORT).show();
        }
    }

    // 原生实现图片缩放、拖动和点击获取像素
    private void setupImageTouchListener() {
        imageView.setOnTouchListener((v, event) -> {
            ImageView view = (ImageView) v;
            view.setScaleType(ImageView.ScaleType.MATRIX);

            // 处理触摸事件
            switch (event.getAction() & MotionEvent.ACTION_MASK) {
                // 单点触摸开始
                case MotionEvent.ACTION_DOWN:
                    savedMatrix.set(matrix);
                    start.set(event.getX(), event.getY());
                    mode = DRAG;
                    break;

                // 双点触摸开始
                case MotionEvent.ACTION_POINTER_DOWN:
                    oldDist = spacing(event);
                    if (oldDist > 10f) {
                        savedMatrix.set(matrix);
                        midPoint(mid, event);
                        mode = ZOOM;
                    }
                    break;

                // 触摸移动
                case MotionEvent.ACTION_MOVE:
                    if (mode == DRAG) {
                        // 拖动
                        matrix.set(savedMatrix);
                        matrix.postTranslate(event.getX() - start.x, event.getY() - start.y);
                    } else if (mode == ZOOM) {
                        // 缩放
                        float newDist = spacing(event);
                        if (newDist > 10f) {
                            matrix.set(savedMatrix);
                            float scale = newDist / oldDist;
                            matrix.postScale(scale, scale, mid.x, mid.y);
                        }
                    }
                    break;

                // 触摸结束
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_POINTER_UP:
                    mode = NONE;
                    // 单点点击时计算像素坐标
                    if (event.getPointerCount() == 1) {
                        calculatePixelCoordinates(event.getX(), event.getY());
                    }
                    break;
            }

            // 应用矩阵变换
            view.setImageMatrix(matrix);
            return true;
        });
    }

    // 用于请求权限的启动器
    private ActivityResultLauncher<String[]> permissionsLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(),
            result -> {
                boolean allGranted = true;
                for (Boolean granted : result.values()) {
                    if (!granted) {
                        allGranted = false;
                        break;
                    }
                }
                if (allGranted) {
                    // 所有权限都已授予，可以开始扫描
                    startMultipleScans();
                } else {
                    Toast.makeText(this, "需要所有权限才能扫描WiFi", Toast.LENGTH_SHORT).show();
                }
            }
    );

    // 计算两点之间的距离（用于缩放）
    private float spacing(MotionEvent event) {
        float x = event.getX(0) - event.getX(1);
        float y = event.getY(0) - event.getY(1);
        return (float) Math.sqrt(x * x + y * y);
    }

    // 计算两点的中点（用于缩放中心点）
    private void midPoint(PointF point, MotionEvent event) {
        float x = event.getX(0) + event.getX(1);
        float y = event.getY(0) + event.getY(1);
        point.set(x / 2, y / 2);
    }

    // 计算点击位置对应的原图像素坐标
    private void calculatePixelCoordinates(float x, float y) {
        if (originalImage == null) {
            Toast.makeText(this, "请先导入图片", Toast.LENGTH_SHORT).show();
            return;
        }

        // 获取当前矩阵的逆矩阵（用于将屏幕坐标转换为原图坐标）
        Matrix inverse = new Matrix();
        if (!matrix.invert(inverse)) {
            return;
        }

        // 将屏幕坐标转换为原图坐标
        float[] points = new float[]{x, y};
        inverse.mapPoints(points);

        // 确保坐标在原图范围内
        int originalWidth = originalImage.getWidth();
        int originalHeight = originalImage.getHeight();
        double originalX = Math.max(0, Math.min(points[0], originalWidth));
        double originalY = Math.max(0, Math.min(points[1], originalHeight));

        // 自动填充到输入框
        etPixelX.setText(String.valueOf(Math.round(originalX)));
        etPixelY.setText(String.valueOf(Math.round(originalY)));

        // 提示用户
        Toast.makeText(this,
                "已获取像素点：(" + Math.round(originalX) + "," + Math.round(originalY) + ")",
                Toast.LENGTH_SHORT).show();
    }

    // 校验基础条件
    private boolean checkBasicConditions() {
        if (originalImage == null) {
            Toast.makeText(this, "请先导入校园平面图", Toast.LENGTH_SHORT).show();
            return false;
        }
        String xStr = etPixelX.getText().toString();
        String yStr = etPixelY.getText().toString();
        String floorStr = etFloor.getText().toString();
        if (xStr.isEmpty() || yStr.isEmpty() || floorStr.isEmpty()) {
            Toast.makeText(this, "请点击图片获取像素点，并填写楼层", Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }

    // 显示WiFi选择对话框
// 修改showWifiSelectDialog方法，添加自动勾选逻辑
    private void showWifiSelectDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("选择需保留的WiFi（至少" + MIN_SELECT_WIFI_COUNT + "个）");

        LinearLayout checkBoxLayout = new LinearLayout(this);
        checkBoxLayout.setOrientation(LinearLayout.VERTICAL);
        checkBoxLayout.setPadding(50, 30, 50, 30);
        CheckBox[] checkBoxes = new CheckBox[filteredWifis.size()];
        selectedWifis.clear();

        // 自动勾选的数量（取前6个，可根据需求调整）
        int autoSelectCount = Math.min(6, filteredWifis.size());

        for (int i = 0; i < filteredWifis.size(); i++) {
            WifiFingerprint.FilteredWifi wifi = filteredWifis.get(i);
            checkBoxes[i] = new CheckBox(this);
            String wifiInfo = wifi.getSsid() + "（MAC：" + wifi.getBssid().substring(12) + "，信号：" + wifi.getRssi() + "dBm）";
            checkBoxes[i].setText(wifiInfo);
            checkBoxes[i].setTextSize(14);
            checkBoxLayout.addView(checkBoxes[i]);

            // 添加间距
            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) checkBoxes[i].getLayoutParams();
            params.bottomMargin = 10;
            checkBoxes[i].setLayoutParams(params);

            // 核心：自动勾选前autoSelectCount个（信号最强的）
            if (i < autoSelectCount) {
                checkBoxes[i].setChecked(true);
                selectedWifis.add(wifi);
            }

            // 监听勾选状态变化（支持手动取消）
            int finalI = i;
            checkBoxes[i].setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    if (!selectedWifis.contains(wifi)) {
                        selectedWifis.add(wifi);
                    }
                } else {
                    selectedWifis.remove(wifi);
                }
            });
        }

        builder.setView(checkBoxLayout);
        builder.setPositiveButton("确定保存", (dialog, which) -> {
            if (selectedWifis.size() >= MIN_SELECT_WIFI_COUNT) {
                saveFingerprintToPixel(selectedWifis); // 保存用户选择的WiFi
            } else {
                Toast.makeText(this, "需至少选择" + MIN_SELECT_WIFI_COUNT + "个WiFi", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("取消", null);
        builder.show();
    }


    // 保存指纹到像素点
    private void saveFingerprintToPixel(List<WifiFingerprint.FilteredWifi> targetWifis) {
        double pixelX = Double.parseDouble(etPixelX.getText().toString());
        double pixelY = Double.parseDouble(etPixelY.getText().toString());
        int floor = Integer.parseInt(etFloor.getText().toString());

        boolean saveSuccess = fingerprintManager.saveFingerprint(pixelX, pixelY, floor, targetWifis);
        if (saveSuccess) {
            Toast.makeText(this, "保存成功！绑定像素点(" + (int) pixelX + "," + (int) pixelY + ")", Toast.LENGTH_SHORT).show();
            // 显示保存的WiFi列表
            StringBuilder sb = new StringBuilder();
            sb.append("已保存").append(targetWifis.size()).append("个WiFi到该像素点：\n");
            for (int i = 0; i < targetWifis.size(); i++) {
                sb.append(i + 1).append(". ").append(targetWifis.get(i).getSsid()).append("（信号：").append(targetWifis.get(i).getRssi()).append("dBm）\n");
            }
            wifiText.setText(sb.toString());
        } else {
            Toast.makeText(this, "保存失败", Toast.LENGTH_SHORT).show();
        }
    }

    private void startSingleScan() {
        isScanning = true;
        multipleScans.clear(); // 清空之前的扫描记录
        wifiText.setText("开始扫描...\n");

        // 禁用相关按钮
        scanButton.setEnabled(false);
        btnSelectWifi.setEnabled(false);
        btnOneClickSave.setEnabled(false);

        // 只扫描一次
        scanOnce();
        isScanning = false; // 扫描一次后就标记为“扫描完成”

        // 处理扫描结果
        filteredWifis = fingerprintManager.filterAndSortWifi(multipleScans);
        if (filteredWifis.isEmpty()) {
            wifiText.append("扫描完成，但无符合条件的 WiFi\n");
            btnSelectWifi.setEnabled(false);
            btnOneClickSave.setEnabled(false);
        } else {
            String message = "扫描完成！发现" + filteredWifis.size() + "个 WiFi\n";
            wifiText.append(message);
            btnSelectWifi.setEnabled(true);
            btnOneClickSave.setEnabled(true);
        }
        scanButton.setEnabled(true); // 重新启用扫描按钮
    }

    // 多次扫描
    private void startMultipleScans() {
        isScanning = true;
        multipleScans.clear();
        wifiText.setText("开始扫描（需至少" + MIN_SCAN_COUNT + "次，间隔3秒）...\n");

        // 禁用相关按钮
        scanButton.setEnabled(false);
        btnSelectWifi.setEnabled(false);
        btnOneClickSave.setEnabled(false);

        // 扫描MIN_SCAN_COUNT次
        for (int i = 0; i < MIN_SCAN_COUNT; i++) {
            int delay = i * 3000;
            handler.postDelayed(() -> {
                scanOnce();
                // 最后一次扫描完成后，筛选WiFi并启用按钮
                if (multipleScans.size() == MIN_SCAN_COUNT) {
                    isScanning = false;
                    filteredWifis = fingerprintManager.filterAndSortWifi(multipleScans);
                    if (filteredWifis.isEmpty()) {
                        wifiText.append("扫描完成，但无符合条件的稳定WiFi\n");
                        btnSelectWifi.setEnabled(false);
                        btnOneClickSave.setEnabled(false);
                    } else {
                        String message = "扫描完成！发现" + filteredWifis.size() + "个稳定WiFi\n";
                        wifiText.append(message);
                        btnSelectWifi.setEnabled(true);
                        btnOneClickSave.setEnabled(true);
                    }
                    // 重新启用扫描按钮
                    scanButton.setEnabled(true);
                }
            }, delay);
        }
    }

    // 单次扫描（修复权限检查）
    private void scanOnce() {
        if (!wifiManager.isWifiEnabled()) {
            wifiManager.setWifiEnabled(true);
            Toast.makeText(this, "WiFi已开启", Toast.LENGTH_SHORT).show();
        }

        // 检查位置权限（关键修复）
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "缺少位置权限，无法扫描WiFi", Toast.LENGTH_SHORT).show();
            return; // 权限不足时直接返回，避免崩溃
        }

        boolean scanSuccess = wifiManager.startScan();
        if (scanSuccess) {
            List<ScanResult> scanResult = wifiManager.getScanResults();
            multipleScans.add(scanResult);
            wifiText.append("第" + multipleScans.size() + "次扫描：发现" + scanResult.size() + "个WiFi\n");
        } else {
            wifiText.append("第" + (multipleScans.size() + 1) + "次扫描失败\n");
        }
    }

    // 检查所有权限是否授予
    private boolean checkAllPermissionsGranted() {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ActivityCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    // 实时定位前检查权限
    private boolean checkLocationPermission() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            // 申请位置权限
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    REQUEST_LOCATION_PERMISSION);
            return false;
        }
        return true;
    }

    // 处理权限申请结果
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_READ_STORAGE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                intent.setType("image/*");
                imagePickerLauncher.launch(intent);
            } else {
                Toast.makeText(this, "需要存储权限才能导入图片", Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (checkAllPermissionsGranted()) {
                startMultipleScans();
            } else {
                Toast.makeText(this, "需要所有权限才能扫描WiFi", Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == REQUEST_LOCATION_PERMISSION) { // 处理位置权限回调
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "位置权限已授予，可尝试实时定位", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "需要位置权限才能扫描WiFi", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // 释放资源
    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        if (originalImage != null && !originalImage.isRecycled()) {
            originalImage.recycle();
        }
    }
}
