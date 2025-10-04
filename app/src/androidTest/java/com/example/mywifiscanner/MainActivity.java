package com.example.mywifiscanner;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.navigation.NavigationView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends AppCompatActivity implements WifiScanner.ScanCallback {

    private static final int MIN_SCAN_COUNT = 3;
    private static final int MIN_SELECT_WIFI_COUNT = 3;
    private TextView tvFileStatus, tvResult, navHeaderFileStatus, tvPermissionTip, tvCurrentFile;
    private String currentEditingFile = null; // 当前编辑的指纹库文件名
    private CoordinateManager coordinateManager;
    private MapFileModule mapFileModule; // 指纹库文件管理
    private InputSwitchModule inputSwitchModule;
    private Button btnOpenDrawer, btnShowAllMarkers, btnManageFingerprints, btnImportImage, scanButton, btnSelectWifi, btnOneClickSave, btnExport, btnRealTimeLocate;
    private ImageView imageView;
    private EditText etLabel, etPath;
    private EditText etPixelX, etPixelY;
    private Spinner spinnerFloor, spinnerZone;
    private DrawerLayout drawerLayout;
    private NavigationView navView;
    private FingerprintManager fingerprintManager; // 指纹管理器
    private ActivityResultLauncher<Intent> importLauncher; // 指纹库导入启动器
    private RadioGroup rgPointType;

    // 工具类实例
    private PermissionManager permissionManager;
    private ImageHandler imageHandler; // 图片处理（显示地图和标记）
    private WifiLocationManager wifiLocationManager; // 定位管理器
    private WifiManager wifiManager;
    private WifiScanner wifiScanner;

    // 数据变量
    private List<List<ScanResult>> multipleScans = new ArrayList<>(); // 多次扫描结果
    private List<FilteredWifi> filteredWifis; // 筛选后的WiFi列表
    private final List<FilteredWifi> selectedWifis = new ArrayList<>(); // 选中的WiFi
    private boolean isScanning = false;
    private final Handler handler = new Handler();
    private WifiFingerprint currentEditingFingerprint; // 当前编辑的指纹

    // 图片选择启动器（用于导入固定地图）
    private final ActivityResultLauncher<Intent> imagePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri imageUri = result.getData().getData();
                    if (imageUri != null) {
                        imageHandler.loadImageFromUri(imageUri); // 加载固定地图
                        btnRealTimeLocate.setEnabled(true);
                        btnShowAllMarkers.setEnabled(true);
                        Toast.makeText(this, "地图加载成功", Toast.LENGTH_SHORT).show();
                    }
                }
            }
    );

    @Override
    public void onMultipleScansComplete(List<List<ScanResult>> allScanResults) {
        this.multipleScans = allScanResults;
        this.filteredWifis = FingerprintManager.filterAndSortWifi(allScanResults); // 筛选WiFi
        runOnUiThread(() -> {
            tvResult.append("多次扫描完成，共发现" + filteredWifis.size() + "个稳定WiFi\n");
            btnSelectWifi.setEnabled(filteredWifis.size() >= MIN_SELECT_WIFI_COUNT);
            btnOneClickSave.setEnabled(filteredWifis.size() >= MIN_SELECT_WIFI_COUNT);
            scanButton.setEnabled(true);
            isScanning = false;
        });
    }

    @Override
    public void onPermissionDenied() {
        runOnUiThread(() ->
                Toast.makeText(this, "位置权限缺失，无法扫描WiFi", Toast.LENGTH_SHORT).show()
        );
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 初始化控件
        initViews();
        // 初始化工具类
        initManagers();
        // 初始化导入启动器
        initImportLauncher();
        // 初始化网格控制（如需保留网格功能）
        initGridControl();
        // 初始化侧边栏
        initDrawerLayout();

        // 绑定点位类型切换逻辑（特殊点/普通点）
        inputSwitchModule = new InputSwitchModule(
                findViewById(R.id.rgPointType),
                findViewById(R.id.etLabel),
                findViewById(R.id.etPath)
        );

        // 绑定事件
        bindNavMenuEvents();
        bindMainButtons();
        setListeners();

        // 初始化显示
        updateFileStatusDisplay();
        checkAndPromptNoFile(); // 启动时检查是否有指纹库文件
    }

    // 更新文件状态显示（同步主布局和侧边栏）
    private void updateFileStatusDisplay() {
        String status = (currentEditingFile != null)
                ? "当前编辑：" + currentEditingFile
                : "未选择文件（请创建或导入指纹库）";
        tvCurrentFile.setText(status);
        if (navHeaderFileStatus != null) {
            navHeaderFileStatus.setText(status);
        }
    }

    // 启动时检查无文件并提示
    private void checkAndPromptNoFile() {
        if (currentEditingFile == null) {
            new AlertDialog.Builder(this)
                    .setTitle("提示")
                    .setMessage("未检测到指纹库文件，是否创建新文件？")
                    .setPositiveButton("创建", (d, w) -> createNewFile())
                    .setNegativeButton("导入", (d, w) -> importJsonFile())
                    .show();
        }
    }

    // 绑定侧边栏菜单事件（仅保留指纹库相关操作）
    private void bindNavMenuEvents() {
        navView.setNavigationItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_create_new_file) {
                createNewFile(); // 创建新指纹库
            } else if (id == R.id.nav_import_file || id == R.id.nav_import_json) {
                importJsonFile(); // 导入指纹库
            } else if (id == R.id.nav_update_current_file) {
                updateCurrentFile(); // 更新当前指纹库
            } else if (id == R.id.nav_view_all_files) {
                viewAllFiles(); // 查看所有指纹库文件
            } else if (id == R.id.nav_export_simplified) {
                exportSimplifiedData(); // 导出指纹库
            } else if (id == R.id.nav_view_exported_files) {
                viewExportedFiles(); // 查看导出的指纹库
            }
            drawerLayout.closeDrawer(GravityCompat.START);
            return true;
        });
    }

    // 绑定主布局按钮事件
    private void bindMainButtons() {
        findViewById(R.id.btnOpenDrawer).setOnClickListener(v ->
                drawerLayout.openDrawer(GravityCompat.START)
        );

        findViewById(R.id.btnUpdateCurrentFile).setOnClickListener(v ->
                updateCurrentFile()
        );

        findViewById(R.id.btnExport).setOnClickListener(v ->
                exportSimplifiedData()
        );

        findViewById(R.id.btnOneClickSave).setOnClickListener(v ->
                saveCurrentFingerprint()
        );
    }

    /**
     * 设置侧边栏
     */
    private void setupNavigationView() {
        NavigationView navView = findViewById(R.id.navView);
        View headerView = navView.getHeaderView(0);
        navHeaderFileStatus = headerView.findViewById(R.id.navHeaderFileStatus);
        updateFileStatusDisplay();
    }

    /**
     * 导出指纹库（简化版，仅导出指纹列表）
     */
    private void exportSimplifiedData() {
        if (currentEditingFile == null) {
            Toast.makeText(this, "请先选择或创建一个指纹库文件", Toast.LENGTH_SHORT).show();
            return;
        }

        List<WifiFingerprint> fingerprints = fingerprintManager.getAllFingerprints();
        if (fingerprints.isEmpty()) {
            Toast.makeText(this, "没有指纹数据可导出", Toast.LENGTH_SHORT).show();
            return;
        }

        // 导出文件名（基于当前编辑的文件名）
        String exportFileName = "export_" + currentEditingFile;
        boolean success = mapFileModule.exportFingerprints(exportFileName, fingerprints);

        if (success) {
            Toast.makeText(this, "指纹库导出成功", Toast.LENGTH_SHORT).show();
            tvResult.setText("指纹库已导出: " + exportFileName);
        } else {
            Toast.makeText(this, "指纹库导出失败", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 显示文件管理对话框
     */
    private void showFileManagementDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("指纹库管理");

        String[] options = {
                "创建新指纹库",
                "导入现有指纹库",
                "更新当前指纹库",
                "查看所有指纹库"
        };

        builder.setItems(options, (dialog, which) -> {
            switch (which) {
                case 0:
                    createNewFile();
                    break;
                case 1:
                    importExistingFile();
                    break;
                case 2:
                    updateCurrentFile();
                    break;
                case 3:
                    viewAllFiles();
                    break;
            }
        });

        builder.setNegativeButton("取消", null);
        builder.show();
    }

    /**
     * 创建新指纹库文件
     */
    private void createNewFile() {
        EditText etFileName = new EditText(this);
        etFileName.setHint("请输入指纹库文件名（不含.json后缀）");

        new AlertDialog.Builder(this)
                .setTitle("创建新指纹库")
                .setView(etFileName)
                .setPositiveButton("创建", (dialog, which) -> {
                    String fileName = etFileName.getText().toString().trim();
                    if (fileName.isEmpty()) {
                        Toast.makeText(this, "文件名不能为空", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // 确保文件名以.json结尾
                    if (!fileName.endsWith(".json")) {
                        fileName += ".json";
                    }

                    // 保存空指纹库（后续可添加指纹）
                    boolean isSaved = mapFileModule.saveFingerprints(fileName, new ArrayList<>());
                    if (isSaved) {
                        currentEditingFile = fileName;
                        fingerprintManager.clearAllFingerprints(); // 清空当前指纹库
                        updateFileStatusDisplay();
                        Toast.makeText(this, "指纹库创建成功", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "指纹库创建失败", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /**
     * 导入现有指纹库文件
     */
    private void importExistingFile() {
        mapFileModule.importFingerprintFile(importLauncher);
    }

    /**
     * 更新当前指纹库文件（覆盖保存）
     */
    private void updateCurrentFile() {
        if (currentEditingFile == null) {
            Toast.makeText(this, "请先选择或创建一个指纹库文件", Toast.LENGTH_SHORT).show();
            return;
        }

        List<WifiFingerprint> currentFingerprints = fingerprintManager.getAllFingerprints();
        boolean success = mapFileModule.saveFingerprints(currentEditingFile, currentFingerprints);
        if (success) {
            Toast.makeText(this, "指纹库更新成功", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "指纹库更新失败", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 查看所有指纹库文件
     */
    private void viewAllFiles() {
        List<String> fileNames = mapFileModule.getAllFingerprintFileNames();
        if (fileNames.isEmpty()) {
            Toast.makeText(this, "没有找到任何指纹库文件", Toast.LENGTH_SHORT).show();
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("选择要编辑的指纹库");

        builder.setItems(fileNames.toArray(new String[0]), (dialog, which) -> {
            String selectedFile = fileNames.get(which);
            loadFingerprintFile(selectedFile); // 加载选中的指纹库
        });

        builder.setNegativeButton("取消", null);
        builder.show();
    }

    /**
     * 加载指纹库文件
     */
    private void loadFingerprintFile(String fileName) {
        List<WifiFingerprint> fingerprints = mapFileModule.loadFingerprints(fileName);
        if (fingerprints != null) {
            fingerprintManager.clearAllFingerprints();
            fingerprintManager.loadFromFile(fingerprints); // 加载指纹到管理器
            currentEditingFile = fileName;
            updateFileStatusDisplay();
            imageHandler.drawAllMarkers(fingerprints); // 在地图上绘制所有指纹标记
            Toast.makeText(this, "指纹库加载成功（共" + fingerprints.size() + "条指纹）", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "指纹库加载失败", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 从URI获取文件名
     */
    private String getFileNameFromUri(Uri uri) {
        String path = uri.getPath();
        if (path != null) {
            int lastSlash = path.lastIndexOf('/');
            if (lastSlash != -1 && lastSlash + 1 < path.length()) {
                return path.substring(lastSlash + 1);
            }
        }
        return "imported_fingerprints.json";
    }



    // 查看导出文件目录
    private void viewExportedFiles() {
        try {
            if (mapFileModule != null) {
                mapFileModule.openExportedDirectory();
                Toast.makeText(this, "正在打开导出文件目录", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "无法打开目录：" + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    // 导入JSON格式的指纹库文件
    private void importJsonFile() {
        try {
            if (mapFileModule != null && importLauncher != null) {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("application/json");
                importLauncher.launch(intent);
            }
        } catch (Exception e) {
            Toast.makeText(this, "无法导入文件：" + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 初始化侧边栏
     */
    private void initDrawerLayout() {
        drawerLayout = findViewById(R.id.drawerLayout);
        navView = findViewById(R.id.navView);
        navView.inflateHeaderView(R.layout.nav_header);
        setupNavigationView();

        Button btnOpenDrawer = findViewById(R.id.btnOpenDrawer);
        btnOpenDrawer.setOnClickListener(v -> {
            updateFileStatusDisplay();
            drawerLayout.openDrawer(GravityCompat.START);
        });
    }

    /**
     * 初始化导入指纹库的结果监听器
     */
    private void initImportLauncher() {
        importLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Uri selectedUri = result.getData().getData();
                        if (selectedUri != null) {
                            List<WifiFingerprint> importedFingerprints = mapFileModule.parseImportedFingerprints(selectedUri);
                            if (importedFingerprints != null && !importedFingerprints.isEmpty()) {
                                // 导入成功，更新当前指纹库
                                fingerprintManager.loadFromFile(importedFingerprints);
                                imageHandler.drawAllMarkers(importedFingerprints);
                                // 以导入的文件名作为当前编辑文件
                                currentEditingFile = getFileNameFromUri(selectedUri);
                                updateFileStatusDisplay();
                                Toast.makeText(this, "指纹库导入成功（共" + importedFingerprints.size() + "条）", Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(this, "指纹库解析失败", Toast.LENGTH_SHORT).show();
                            }
                        }
                    }
                }
        );
    }

    /**
     * 初始化控件
     */
    private void initViews() {
        drawerLayout = findViewById(R.id.drawerLayout);
        navView = findViewById(R.id.navView);
        tvCurrentFile = findViewById(R.id.tv_current_file);
        View headerView = navView.getHeaderView(0);
        navHeaderFileStatus = headerView.findViewById(R.id.navHeaderFileStatus);
        btnImportImage = findViewById(R.id.btnImportImage);
        imageView = findViewById(R.id.imageView);
        etPixelX = findViewById(R.id.etPixelX);
        etPixelY = findViewById(R.id.etPixelY);
        scanButton = findViewById(R.id.scanButton);
        btnSelectWifi = findViewById(R.id.btnSelectWifi);
        btnOneClickSave = findViewById(R.id.btnOneClickSave);
        btnExport = findViewById(R.id.btnExport);
        btnRealTimeLocate = findViewById(R.id.btnRealTimeLocate);
        btnShowAllMarkers = findViewById(R.id.btnShowAllMarkers);
        tvResult = findViewById(R.id.tvResult);
        etLabel = findViewById(R.id.etLabel);
        etPath = findViewById(R.id.etPath);
        rgPointType = findViewById(R.id.rgPointType);
        tvPermissionTip = findViewById(R.id.tvPermissionTip);

        // 初始化Spinner（楼层和区域）
        spinnerFloor = findViewById(R.id.spinnerFloor);
        spinnerZone = findViewById(R.id.spinnerZone);

        ArrayAdapter<CharSequence> floorAdapter = ArrayAdapter.createFromResource(this,
                R.array.floor_array, android.R.layout.simple_spinner_item);
        floorAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerFloor.setAdapter(floorAdapter);

        ArrayAdapter<CharSequence> zoneAdapter = ArrayAdapter.createFromResource(this,
                R.array.zone_array, android.R.layout.simple_spinner_item);
        zoneAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerZone.setAdapter(zoneAdapter);

        // 初始状态设置
        etPath.setVisibility(View.GONE);
        btnRealTimeLocate.setEnabled(false);
        btnSelectWifi.setEnabled(false);
        btnOneClickSave.setEnabled(false);
        btnShowAllMarkers.setEnabled(false);

        // 点位类型切换监听（特殊点/普通点）
        rgPointType.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rbSpecialPoint) {
                etLabel.setVisibility(View.VISIBLE);
                etPath.setVisibility(View.GONE);
                etLabel.setHint("特殊点名称（如：图书馆入口）");
            } else {
                etLabel.setVisibility(View.GONE);
                etPath.setVisibility(View.VISIBLE);
                etPath.setHint("普通点距离（如：距离图书馆入口5米）");
            }
        });
    }

    /**
     * 初始化工具类（适配简化后的模块）
     */
    private void initManagers() {
        permissionManager = new PermissionManager(this);
        imageView = findViewById(R.id.imageView);
        imageHandler = new ImageHandler(this, imageView); // 用于显示固定地图和指纹标记
        wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        coordinateManager = new CoordinateManager(imageHandler); // 处理地图坐标转换
        fingerprintManager = new FingerprintManager(); // 指纹管理
        mapFileModule = new MapFileModule(this); // 指纹库文件管理（仅依赖Context）
        wifiLocationManager = new WifiLocationManager(this, wifiManager, fingerprintManager); // 定位逻辑
        wifiScanner = new WifiScanner(this, wifiManager); // WiFi扫描

        // 绑定控件引用
        btnManageFingerprints = findViewById(R.id.btnManageFingerprints);
        btnExport = findViewById(R.id.btnExport);
        btnRealTimeLocate = findViewById(R.id.btnRealTimeLocate);
        btnShowAllMarkers = findViewById(R.id.btnShowAllMarkers);
        tvResult = findViewById(R.id.tvResult);
    }

    /**
     * 设置事件监听
     */
    private void setListeners() {
        // 导入固定地图
        btnImportImage.setOnClickListener(v -> handleImportImage());
        // 扫描WiFi
        scanButton.setOnClickListener(v -> handleScanWifi());
        // 选择WiFi（手动筛选）
        btnSelectWifi.setOnClickListener(v -> showWifiSelectDialog());
        // 管理指纹库（查看/编辑/删除指纹）
        btnManageFingerprints.setOnClickListener(v -> showFingerprintListDialog());
        // 一键保存指纹（使用筛选后的所有WiFi）
        btnOneClickSave.setOnClickListener(v -> {
            if (filteredWifis != null) {
                saveFingerprintToPixel(filteredWifis);
            } else {
                Toast.makeText(this, "请先完成WiFi扫描", Toast.LENGTH_SHORT).show();
            }
        });
        // 更新当前文件按钮
        Button btnUpdateCurrentFile = findViewById(R.id.btnUpdateCurrentFile);
        if (btnUpdateCurrentFile != null) {
            btnUpdateCurrentFile.setOnClickListener(v -> updateCurrentFile());
        }
        // 文件管理按钮
        Button btnFileManager = findViewById(R.id.btnOpenDrawer);
        btnFileManager.setOnClickListener(v -> showFileManagementDialog());
        // 导出指纹库
        btnExport.setOnClickListener(v -> {
            String result = fingerprintManager.exportFingerprints();
            Toast.makeText(this, "指纹库已转为JSON", Toast.LENGTH_LONG).show();
            tvResult.setText("导出结果：\n" + result);
        });
        // 查看所有标记（在地图上显示所有指纹）
        btnShowAllMarkers.setOnClickListener(v -> {
            List<WifiFingerprint> fingerprints = fingerprintManager.getAllFingerprints();
            if (fingerprints.isEmpty()) {
                Toast.makeText(this, "没有已保存的指纹", Toast.LENGTH_SHORT).show();
                return;
            }
            imageHandler.drawAllMarkers(fingerprints);
            tvResult.setText("已显示所有指纹，共" + fingerprints.size() + "个");
        });
        // 实时定位
        btnRealTimeLocate.setOnClickListener(v -> handleRealTimeLocate());
        // 图片触摸监听（获取坐标或编辑指纹）
        setImageTouchListener();
    }

    /**
     * 处理图片导入（固定地图）
     */
    private void handleImportImage() {
        if (permissionManager == null) {
            Toast.makeText(this, "权限管理器初始化失败", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!permissionManager.checkStoragePermission()) {
            permissionManager.requestStoragePermission(new PermissionManager.PermissionCallback() {
                @Override
                public void onPermissionGranted() {
                    Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                    intent.setType("image/*");
                    imagePickerLauncher.launch(intent);
                }

                @Override
                public void onPermissionDenied() {
                    Toast.makeText(MainActivity.this, "需要存储权限才能导入地图", Toast.LENGTH_SHORT).show();
                }
            });
        } else {
            Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            intent.setType("image/*");
            imagePickerLauncher.launch(intent);
        }
    }

    /**
     * 处理WiFi扫描
     */
    private void handleScanWifi() {
        if (isScanning) {
            Toast.makeText(this, "正在扫描中，请稍候...", Toast.LENGTH_SHORT).show();
            return;
        }
        if (permissionManager == null) {
            Toast.makeText(this, "权限管理器初始化失败", Toast.LENGTH_SHORT).show();
            return;
        }

        permissionManager.requestLocationPermission(new PermissionManager.PermissionCallback() {
            @Override
            public void onPermissionGranted() {
                if (wifiLocationManager != null && !wifiLocationManager.isLocationEnabled()) {
                    showLocationServiceDialog(); // 提示开启位置服务
                    return;
                }
                startScanning();
            }

            private void startScanning() {
                isScanning = true;
                tvResult.setText("开始多次扫描WiFi（共" + MIN_SCAN_COUNT + "次，间隔3秒）...\n");
                scanButton.setEnabled(false);
                btnSelectWifi.setEnabled(false);
                btnOneClickSave.setEnabled(false);
                // 执行多次扫描
                wifiScanner.performMultipleScans(
                        MIN_SCAN_COUNT,
                        3000,  // 间隔3秒
                        MainActivity.this // 当前类作为回调
                );
            }

            @Override
            public void onPermissionDenied() {
                Toast.makeText(MainActivity.this, "需要位置权限才能扫描WiFi", Toast.LENGTH_SHORT).show();
                showPermissionRationaleDialog("权限缺失", "扫描WiFi需要位置权限，请在设置中开启");
            }
        });
    }

    // 显示位置服务对话框
    private void showLocationServiceDialog() {
        new AlertDialog.Builder(this)
                .setTitle("位置服务未开启")
                .setMessage("需要开启位置服务才能扫描WiFi")
                .setPositiveButton("去设置", (dialog, which) -> {
                    Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                    startActivity(intent);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /**
     * 显示权限提示
     */
    private void showPermissionTip(String message) {
        if (tvPermissionTip != null) {
            tvPermissionTip.setText(message);
            tvPermissionTip.setVisibility(View.VISIBLE);
        }
    }

    /**
     * 隐藏权限提示
     */
    private void hidePermissionTip() {
        if (tvPermissionTip != null) {
            tvPermissionTip.setVisibility(View.GONE);
        }
    }

    // 处理实时定位
    private void handleRealTimeLocate() {
        if (imageHandler == null || imageHandler.getOriginalImage() == null) {
            Toast.makeText(this, "请先导入平面图", Toast.LENGTH_SHORT).show();
            return;
        }
        if (fingerprintManager == null || fingerprintManager.getAllFingerprints().isEmpty()) {
            Toast.makeText(this, "请先采集指纹数据", Toast.LENGTH_SHORT).show();
            return;
        }
        if (permissionManager == null || !permissionManager.checkLocationPermission()
                || (wifiLocationManager != null && !wifiLocationManager.isLocationEnabled())) {
            Toast.makeText(this, "请确保已授予位置权限并开启位置服务", Toast.LENGTH_SHORT).show();
            return;
        }

        WifiLocationManager.LocationResult result = wifiLocationManager.startRealTimeLocation();
        if (result != null) {
            String displayText = "";
            List<WifiFingerprint> fingerprints = fingerprintManager.getAllFingerprints();
            for (WifiFingerprint fp : fingerprints) {
                if (fp.getPixelX() == result.getX() && fp.getPixelY() == result.getY() && fp.getFloor() == result.getFloor()) {
                    if (fp.getLabel() != null && !fp.getLabel().isEmpty()) {
                        displayText = "特殊点：" + fp.getLabel();
                    } else if (fp.getPath() != null && !fp.getPath().isEmpty()) {
                        displayText = "普通点：" + fp.getPath();
                    }
                    break;
                }
            }
            imageHandler.drawLocationMarker((float) result.getX(), (float) result.getY(), displayText);
            tvResult.setText(String.format("定位成功：\n楼层：%d\n坐标：(%.0f,%.0f)\n%s",
                    result.getFloor(),
                    result.getX(),
                    result.getY(),
                    displayText.isEmpty() ? "未标注点位类型" : displayText));
        } else {
            tvResult.setText("定位失败，未找到匹配的指纹数据");
        }
    }

    /**
     * 显示WiFi选择对话框（手动筛选要保存的WiFi）
     */
    private void showWifiSelectDialog() {
        if (multipleScans.size() < MIN_SCAN_COUNT) {
            Toast.makeText(this, "请先完成" + MIN_SCAN_COUNT + "次扫描", Toast.LENGTH_SHORT).show();
            return;
        }
        if (filteredWifis == null) {
            filteredWifis = FingerprintManager.filterAndSortWifi(multipleScans);
        }
        if (filteredWifis == null || filteredWifis.isEmpty()) {
            Toast.makeText(this, "无可用WiFi信号", Toast.LENGTH_SHORT).show();
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("选择WiFi（至少" + MIN_SELECT_WIFI_COUNT + "个）");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 30, 50, 30);

        CheckBox[] checkBoxes = new CheckBox[filteredWifis.size()];
        selectedWifis.clear();
        int autoSelectCount = Math.min(6, filteredWifis.size()); // 自动选中前6个强信号

        for (int i = 0; i < filteredWifis.size(); i++) {
            FilteredWifi wifi = filteredWifis.get(i);
            checkBoxes[i] = new CheckBox(this);
            checkBoxes[i].setText(String.format("%s（MAC：%s，信号：%ddBm）",
                    wifi.getSsid(),
                    wifi.getBssid().substring(12), // 简化MAC显示
                    wifi.getRssi()));
            checkBoxes[i].setTextSize(14);
            layout.addView(checkBoxes[i]);

            if (i < autoSelectCount) {
                checkBoxes[i].setChecked(true);
                selectedWifis.add(wifi);
            }

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

        builder.setView(layout);
        builder.setPositiveButton("确定", (dialog, which) -> {
            if (selectedWifis.size() >= MIN_SELECT_WIFI_COUNT) {
                saveFingerprintToPixel(selectedWifis);
            } else {
                Toast.makeText(this, "请至少选择" + MIN_SELECT_WIFI_COUNT + "个WiFi", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("取消", null);
        builder.show();
    }

    /**
     * 保存指纹到指定坐标（核心功能）
     */
    private void saveFingerprintToPixel(List<FilteredWifi> wifis) {
        if (!checkBasicConditions()) return;

        double x;
        double y;
        int floor;
        try {
            x = Double.parseDouble(etPixelX.getText().toString());
            y = Double.parseDouble(etPixelY.getText().toString());
            floor = Integer.parseInt(spinnerFloor.getSelectedItem().toString());
        } catch (NumberFormatException e) {
            Toast.makeText(this, "坐标或楼层格式错误", Toast.LENGTH_SHORT).show();
            return;
        }

        String zone = spinnerZone.getSelectedItem().toString();
        String label = etLabel.getText().toString().trim();
        String path = etPath.getText().toString().trim();

        // 校验：特殊点和普通点不能同时为空或同时填写
        if ((label.isEmpty() && path.isEmpty()) || (!label.isEmpty() && !path.isEmpty())) {
            Toast.makeText(this, "请仅填写一项：特殊点名称（label）或普通点距离（path）", Toast.LENGTH_SHORT).show();
            return;
        }

        // 保存指纹到管理器
        boolean success = fingerprintManager.saveFingerprint(x, y, floor, zone, label, path, wifis);

        if (success) {
            String tip = label.isEmpty() ? "普通点（" + path + "）" : "特殊点（" + label + "）";
            Toast.makeText(this, tip + "保存成功：(" + (int) x + "," + (int) y + ")", Toast.LENGTH_SHORT).show();

            // 更新UI显示
            StringBuilder sb = new StringBuilder();
            sb.append("✅ 指纹点已保存\n");
            sb.append("坐标: (").append((int) x).append(", ").append((int) y).append(")\n");
            sb.append("楼层: ").append(floor).append("\n");
            sb.append("区域: ").append(zone).append("\n");
            sb.append(label.isEmpty() ? "普通点距离: " + path : "特殊点名称: " + label).append("\n");
            sb.append("WiFi数量: ").append(wifis.size()).append("个");

            tvResult.setText(sb.toString());
            // 清空输入框
            etLabel.setText("");
            etPath.setText("");
            // 在地图上绘制新保存的指纹
            imageHandler.drawAllMarkers(fingerprintManager.getAllFingerprints());
        } else {
            Toast.makeText(this, "保存失败，请重试", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 检查保存指纹的基础条件
     */
    private boolean checkBasicConditions() {
        if (imageHandler == null || imageHandler.getOriginalImage() == null) {
            Toast.makeText(this, "请先导入平面图", Toast.LENGTH_SHORT).show();
            return false;
        }

        if (etPixelX.getText().toString().isEmpty() || etPixelY.getText().toString().isEmpty()) {
            Toast.makeText(this, "请点击图片获取像素坐标", Toast.LENGTH_SHORT).show();
            return false;
        }

        if (currentEditingFile == null) {
            Toast.makeText(this, "请先创建或导入指纹库文件", Toast.LENGTH_SHORT).show();
            return false;
        }

        return true;
    }

    /**
     * 显示权限说明对话框
     */
    void showPermissionRationaleDialog(String title, String message) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("去设置", (dialog, which) -> {
                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    Uri uri = Uri.fromParts("package", getPackageName(), null);
                    intent.setData(uri);
                    startActivity(intent);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /**
     * 显示指纹列表对话框（管理已保存的指纹）
     */
    private void showFingerprintListDialog() {
        List<WifiFingerprint> fingerprints = fingerprintManager.getAllFingerprints();
        if (fingerprints == null || fingerprints.isEmpty()) {
            Toast.makeText(this, "指纹库为空，请添加指纹点", Toast.LENGTH_SHORT).show();
            tvResult.setText("当前没有保存任何指纹点");
            return;
        }

        CharSequence[] items = new CharSequence[fingerprints.size()];
        for (int i = 0; i < fingerprints.size(); i++) {
            WifiFingerprint fp = fingerprints.get(i);
            String displayText;
            if (fp.getLabel() != null && !fp.getLabel().isEmpty()) {
                displayText = String.format("特殊点：%s (%.0f, %.0f) - F%d",
                        fp.getLabel(), fp.getPixelX(), fp.getPixelY(), fp.getFloor());
            } else {
                String path = fp.getPath() == null || fp.getPath().isEmpty() ? "未标注距离" : fp.getPath();
                displayText = String.format("普通点：%s (%.0f, %.0f) - F%d",
                        path, fp.getPixelX(), fp.getPixelY(), fp.getFloor());
            }
            items[i] = displayText;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("选择要管理的指纹点（共" + fingerprints.size() + "个）");
        builder.setItems(items, (dialog, which) -> {
            WifiFingerprint selectedFp = fingerprints.get(which);
            showFingerprintEditDialog(selectedFp); // 编辑选中的指纹
        });
        builder.setNegativeButton("取消", (dialog, which) -> dialog.dismiss());
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    /**
     * 显示指纹编辑对话框（修改/删除/重新扫描）
     */
    private void showFingerprintEditDialog(WifiFingerprint fingerprint) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(fingerprint.getLabel().isEmpty() ? "编辑普通点" : "编辑特殊点");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 30, 50, 30);

        // 特殊点名称输入
        EditText etLabelEdit = new EditText(this);
        etLabelEdit.setHint("特殊点名称（如：图书馆a口）");
        etLabelEdit.setText(fingerprint.getLabel());
        etLabelEdit.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        layout.addView(etLabelEdit);

        // 普通点距离输入
        EditText etPathEdit = new EditText(this);
        etPathEdit.setHint("普通点距离描述（如：距离图书馆a口3米）");
        etPathEdit.setText(fingerprint.getPath());
        etPathEdit.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        layout.addView(etPathEdit);

        // 楼层选择
        TextView tvFloor = new TextView(this);
        tvFloor.setText("选择楼层");
        tvFloor.setTextSize(16);
        layout.addView(tvFloor);

        Spinner spinnerFloorEdit = new Spinner(this);
        ArrayAdapter<CharSequence> floorAdapter = ArrayAdapter.createFromResource(this,
                R.array.floor_array, android.R.layout.simple_spinner_item);
        floorAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerFloorEdit.setAdapter(floorAdapter);
        int floorPosition = floorAdapter.getPosition(String.valueOf(fingerprint.getFloor()));
        if (floorPosition >= 0) {
            spinnerFloorEdit.setSelection(floorPosition);
        }
        layout.addView(spinnerFloorEdit);

        // 区域选择
        TextView tvZone = new TextView(this);
        tvZone.setText("选择区域");
        tvZone.setTextSize(16);
        layout.addView(tvZone);

        Spinner spinnerZoneEdit = new Spinner(this);
        ArrayAdapter<CharSequence> zoneAdapter = ArrayAdapter.createFromResource(this,
                R.array.zone_array, android.R.layout.simple_spinner_item);
        zoneAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerZoneEdit.setAdapter(zoneAdapter);
        int zonePosition = zoneAdapter.getPosition(fingerprint.getZone());
        if (zonePosition >= 0) {
            spinnerZoneEdit.setSelection(zonePosition);
        }
        layout.addView(spinnerZoneEdit);

        // 坐标信息（不可编辑）
        TextView tvCoordInfo = new TextView(this);
        tvCoordInfo.setText(String.format("坐标: (%.0f, %.0f) - 不可编辑",
                fingerprint.getPixelX(),
                fingerprint.getPixelY()));
        tvCoordInfo.setTextColor(Color.GRAY);
        tvCoordInfo.setPadding(0, 20, 0, 0);
        layout.addView(tvCoordInfo);

        // WiFi数量信息
        TextView tvWifiInfo = new TextView(this);
        tvWifiInfo.setText("包含 " + fingerprint.getFilteredWifis().size() + " 个WiFi信号");
        tvWifiInfo.setTextColor(Color.GRAY);
        layout.addView(tvWifiInfo);

        builder.setView(layout);

        // 保存修改
        builder.setPositiveButton("保存", (dialog, which) -> {
            String newLabel = etLabelEdit.getText().toString().trim();
            String newPath = etPathEdit.getText().toString().trim();
            int newFloor = Integer.parseInt(spinnerFloorEdit.getSelectedItem().toString());
            String newZone = spinnerZoneEdit.getSelectedItem().toString();

            // 校验输入
            if ((newLabel.isEmpty() && newPath.isEmpty()) || (!newLabel.isEmpty() && !newPath.isEmpty())) {
                Toast.makeText(MainActivity.this, "请仅填写一项：特殊点名称或普通点距离", Toast.LENGTH_SHORT).show();
                return;
            }

            // 更新指纹信息
            fingerprint.setLabel(newLabel);
            fingerprint.setPath(newPath);
            fingerprint.setFloor(newFloor);
            fingerprint.setZone(newZone);

            boolean success = fingerprintManager.updateFingerprint(fingerprint);

            if (success) {
                String tip = newLabel.isEmpty() ? "普通点（" + newPath + "）" : "特殊点（" + newLabel + "）";
                Toast.makeText(MainActivity.this, tip + "更新成功", Toast.LENGTH_SHORT).show();
                imageHandler.drawAllMarkers(fingerprintManager.getAllFingerprints()); // 刷新地图标记
                tvResult.setText("已更新：" + tip);
            } else {
                Toast.makeText(MainActivity.this, "更新失败", Toast.LENGTH_SHORT).show();
            }
        });

        // 删除指纹
        builder.setNegativeButton("删除", (dialog, which) -> {
            new AlertDialog.Builder(MainActivity.this)
                    .setTitle("确认删除")
                    .setMessage("确定要删除这个指纹点吗？此操作不可恢复。")
                    .setPositiveButton("删除", (d, w) -> {
                        boolean success = fingerprintManager.deleteFingerprint(fingerprint);
                        if (success) {
                            String tip = fingerprint.getLabel().isEmpty() ? "普通点" : "特殊点（" + fingerprint.getLabel() + "）";
                            Toast.makeText(MainActivity.this, tip + "删除成功", Toast.LENGTH_SHORT).show();
                            imageHandler.drawAllMarkers(fingerprintManager.getAllFingerprints()); // 刷新地图标记
                            tvResult.setText("已删除：" + tip);
                        } else {
                            Toast.makeText(MainActivity.this, "删除失败", Toast.LENGTH_SHORT).show();
                        }
                    })
                    .setNegativeButton("取消", null)
                    .show();
        });

        // 重新扫描WiFi（更新当前指纹的WiFi信号）
        builder.setNeutralButton("重新扫描WiFi", (dialog, which) -> {
            currentEditingFingerprint = fingerprint;
            startRescanForFingerprint();
        });

        AlertDialog dialog = builder.create();
        dialog.show();
    }

    /**
     * 重新扫描WiFi并更新指纹
     */
    private void startRescanForFingerprint() {
        if (isScanning) {
            Toast.makeText(this, "正在扫描中，请稍候...", Toast.LENGTH_SHORT).show();
            return;
        }

        if (permissionManager == null || !permissionManager.checkLocationPermission()) {
            if (permissionManager != null) {
                permissionManager.requestLocationPermission(new PermissionManager.PermissionCallback() {
                    @Override
                    public void onPermissionGranted() {
                        startRescanForFingerprint();
                    }

                    @Override
                    public void onPermissionDenied() {
                        Toast.makeText(MainActivity.this, "需要位置权限才能扫描WiFi", Toast.LENGTH_SHORT).show();
                    }
                });
            }
            return;
        }

        isScanning = true;
        multipleScans.clear();
        tvResult.setText("开始重新扫描WiFi（共" + MIN_SCAN_COUNT + "次，间隔3秒）...\n");
        scanButton.setEnabled(false);

        // 执行多次扫描
        wifiScanner.performMultipleScans(MIN_SCAN_COUNT, 3000, new WifiScanner.ScanCallback() {

            @Override
            public void onMultipleScansComplete(List<List<ScanResult>> results) {
                runOnUiThread(() -> {
                    multipleScans = results;
                    filteredWifis = FingerprintManager.filterAndSortWifi(results);
                    // 更新当前编辑指纹的WiFi数据
                    if (currentEditingFingerprint != null) {
                        currentEditingFingerprint.setFilteredWifis(filteredWifis);
                        fingerprintManager.updateFingerprint(currentEditingFingerprint);
                        tvResult.append("重新扫描完成，已更新指纹数据\n");
                    }
                    isScanning = false;
                    scanButton.setEnabled(true);
                });
            }

            @Override
            public void onPermissionDenied() {
                runOnUiThread(() -> {
                    tvResult.append("重新扫描失败：权限不足\n");
                    isScanning = false;
                    scanButton.setEnabled(true);
                });
            }
        });
    }

    /**
     * 图片点击事件（获取坐标或选择指纹）
     */
    private void handleImageClick(float screenX, float screenY) {
        if (imageHandler == null || coordinateManager == null) return;

        float[] coords = coordinateManager.getCoordinatesFromTouch(screenX, screenY);
        if (coords == null) return;

        List<WifiFingerprint> fingerprints = fingerprintManager.getAllFingerprints();
        if (fingerprints.isEmpty()) {
            // 无指纹时，直接显示点击坐标
            etPixelX.setText(String.valueOf(Math.round(coords[0])));
            etPixelY.setText(String.valueOf(Math.round(coords[1])));
            return;
        }

        // 检查是否点击了已有的指纹标记
        WifiFingerprint clickedFingerprint = coordinateManager.getClickedFingerprint(screenX, screenY, fingerprints);
        if (clickedFingerprint != null) {
            showFingerprintEditDialog(clickedFingerprint); // 编辑该指纹
        } else {
            // 未点击指纹，显示当前坐标
            etPixelX.setText(String.valueOf(Math.round(coords[0])));
            etPixelY.setText(String.valueOf(Math.round(coords[1])));
        }
    }

    /**
     * 图片触摸监听（支持缩放、移动、点击）
     */
    private void setImageTouchListener() {
        imageView.setOnTouchListener((v, event) -> {
            boolean handled = imageHandler != null && imageHandler.handleTouchEvent(event);

            if (event.getAction() == MotionEvent.ACTION_UP && event.getPointerCount() == 1) {
                handleImageClick(event.getX(), event.getY());
            }
            return handled;
        });
    }

    /**
     * 处理权限请求结果
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (permissionManager != null) {
            permissionManager.handlePermissionResult(requestCode, grantResults);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        if (wifiScanner != null) {
            wifiScanner.destroy();
        }
    }

    // 保存当前指纹（按钮绑定使用）
    private void saveCurrentFingerprint() {
        if (filteredWifis == null || filteredWifis.isEmpty()) {
            Toast.makeText(this, "请先完成WiFi扫描", Toast.LENGTH_SHORT).show();
            return;
        }
        saveFingerprintToPixel(filteredWifis);
    }
}