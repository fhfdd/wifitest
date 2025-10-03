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
import android.os.Looper;
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
    private String currentEditingFile = null;
    private CoordinateManager coordinateManager;
    private GridControlModule gridControlModule;
    private MapFileModule mapFileModule;
    private InputSwitchModule inputSwitchModule;
    private Button btnOpenDrawer, btnShowAllMarkers, btnManageFingerprints, btnImportImage, scanButton, btnSelectWifi, btnOneClickSave, btnExport, btnRealTimeLocate;
    private ImageView imageView;
    private EditText etLabel, etPath;
    private EditText etPixelX, etPixelY;
    private Spinner spinnerFloor, spinnerZone;
    private DrawerLayout drawerLayout;
    private NavigationView navView;
    private FingerprintManager fingerprintManager;
    private ActivityResultLauncher<Intent> importLauncher;
    private RadioGroup rgPointType;

    // 工具类实例
    private PermissionManager permissionManager;
    private ImageHandler imageHandler;
    private WifiLocationManager wifiLocationManager;
    private WifiManager wifiManager;
    private WifiScanner wifiScanner;

    // 数据变量
    private List<List<ScanResult>> multipleScans = new ArrayList<>();
    private List<FilteredWifi> filteredWifis;
    private final List<FilteredWifi> selectedWifis = new ArrayList<>();
    private boolean isScanning = false;
    private final Handler handler = new Handler();
    private WifiFingerprint currentEditingFingerprint;

    // 图片选择启动器
    private final ActivityResultLauncher<Intent> imagePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri imageUri = result.getData().getData();
                    if (imageUri != null) {
                        imageHandler.loadImageFromUri(imageUri);
                        btnRealTimeLocate.setEnabled(true);
                        btnShowAllMarkers.setEnabled(true);
                    }
                }
            }
    );

    @Override
    public void onMultipleScansComplete(List<List<ScanResult>> allScanResults) {
        isScanning = false;
        scanButton.setEnabled(true);
        btnSelectWifi.setEnabled(true);
        btnOneClickSave.setEnabled(true);
        tvResult.append("所有扫描完成！共" + allScanResults.size() + "次结果\n");
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
        // 初始化工具类（关键：必须先初始化工具类，避免空指针）
        initManagers();
        // 初始化导入启动器
        initImportLauncher();
        // 初始化网格控制
        initGridControl();
        // 初始化侧边栏
        initDrawerLayout();

        // 初始化模块（依赖注入，使用成员变量而非局部变量）
        this.wifiScanner = new WifiScanner(this, wifiManager);
        this.coordinateManager = new CoordinateManager(imageHandler);
        this.mapFileModule = new MapFileModule(this, fingerprintManager);
        this.fingerprintManager = new FingerprintManager();

        // 绑定点位类型切换逻辑
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
        checkAndPromptNoFile();
    }

    // 更新文件状态显示（同步主布局和侧边栏）
    private void updateFileStatusDisplay() {
        MapData currentFile = mapFileModule.getCurrentMapData();
        String status;
        if (currentFile != null) {
            status = "当前编辑：" + currentFile.getFileName() + ".json";
        } else {
            status = "未选择文件（请创建或导入）";
        }
        tvCurrentFile.setText(status);
        if (navHeaderFileStatus != null) {
            navHeaderFileStatus.setText(status);
        }
    }

    // 启动时检查无文件并提示
    private void checkAndPromptNoFile() {
        if (mapFileModule.getCurrentMapData() == null) {
            new AlertDialog.Builder(this)
                    .setTitle("提示")
                    .setMessage("未检测到文件，是否创建新文件？")
                    .setPositiveButton("创建", (d, w) -> createNewFile())
                    .setNegativeButton("导入", (d, w) -> importJsonFile())
                    .show();
        }
    }

    // 绑定侧边栏菜单事件
    private void bindNavMenuEvents() {
        navView.setNavigationItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_create_new_file) {
                createNewFile();
            } else if (id == R.id.nav_import_file || id == R.id.nav_import_json) {
                importJsonFile();
            } else if (id == R.id.nav_update_current_file) {
                updateCurrentFile();
            } else if (id == R.id.nav_view_all_files) {
                viewAllFiles();
            } else if (id == R.id.nav_export_simplified) {
                exportSimplifiedData();
            } else if (id == R.id.nav_view_exported_files) {
                viewExportedFiles();
            } else if (id == R.id.nav_save_map_custom) {
                saveCurrentMap();
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
     * 设置侧边栏（移除重复逻辑）
     */
    private void setupNavigationView() {
        NavigationView navView = findViewById(R.id.navView);
        View headerView = navView.getHeaderView(0);
        navHeaderFileStatus = headerView.findViewById(R.id.navHeaderFileStatus);
        updateFileStatusDisplay();
    }

    /**
     * 更新文件状态（同时更新主界面和侧边栏）
     */
    private void updateFileStatus() {
        String statusText;
        int textColor;

        if (currentEditingFile != null) {
            statusText = "当前编辑: " + currentEditingFile;
            textColor = Color.GREEN;
        } else {
            statusText = "未选择文件";
            textColor = Color.RED;
        }

        if (tvFileStatus != null) {
            tvFileStatus.setText(statusText);
            tvFileStatus.setTextColor(textColor);
        }

        if (navHeaderFileStatus != null) {
            navHeaderFileStatus.setText(statusText);
            navHeaderFileStatus.setTextColor(textColor);
        }
    }

    /**
     * 导出精简数据
     */
    private void exportSimplifiedData() {
        if (currentEditingFile == null) {
            Toast.makeText(this, "请先选择或创建一个文件", Toast.LENGTH_SHORT).show();
            return;
        }

        List<WifiFingerprint> fingerprints = fingerprintManager.getAllFingerprints();
        if (fingerprints.isEmpty()) {
            Toast.makeText(this, "没有指纹数据可导出", Toast.LENGTH_SHORT).show();
            return;
        }

        Bitmap originalImage = imageHandler.getOriginalImage();
        String imageName = "map_image.png";
        int imageWidth = originalImage != null ? originalImage.getWidth() : 0;
        int imageHeight = originalImage != null ? originalImage.getHeight() : 0;

        String exportFileName = "simplified_" + currentEditingFile;

        boolean success = mapFileModule.exportSimplifiedFingerprints(
                exportFileName, fingerprints, imageName, imageWidth, imageHeight);

        if (success) {
            Toast.makeText(this, "精简数据导出成功", Toast.LENGTH_SHORT).show();
            tvResult.setText("精简数据已导出: " + exportFileName);
        } else {
            Toast.makeText(this, "精简数据导出失败", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 显示文件管理对话框
     */
    private void showFileManagementDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("文件管理");

        String[] options = {
                "创建新文件",
                "导入现有文件",
                "更新当前文件",
                "查看所有文件"
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
     * 创建新文件（修复：正确初始化MapData并处理保存结果）
     */
    private void createNewFile() {
        EditText etFileName = new EditText(this);
        etFileName.setHint("请输入文件名（不含.json后缀）");

        new AlertDialog.Builder(this)
                .setTitle("创建新文件")
                .setView(etFileName)
                .setPositiveButton("创建", (dialog, which) -> {
                    String fileName = etFileName.getText().toString().trim();
                    if (fileName.isEmpty()) {
                        Toast.makeText(this, "文件名不能为空", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (!fileName.endsWith(".json")) {
                        fileName += ".json";
                    }

                    MapData emptyData = new MapData();
                    emptyData.setFileName(fileName.replace(".json", ""));
                    emptyData.setPoints(new ArrayList<>());

                    MapData.GridConfig gridConfig = new MapData.GridConfig();
                    gridConfig.setShow(false);
                    gridConfig.setScale(50);
                    emptyData.setGridConfig(gridConfig);

                    boolean isSaved = mapFileModule.saveMapData(fileName, emptyData);
                    if (isSaved) {
                        currentEditingFile = fileName;
                        mapFileModule.setCurrentMapData(emptyData);
                        updateFileStatusDisplay();
                        Toast.makeText(this, "文件创建成功", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "文件创建失败", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /**
     * 导入现有文件
     */
    private void importExistingFile() {
        mapFileModule.importJsonFile(this, importLauncher);
    }

    /**
     * 更新当前文件
     */
    private void updateCurrentFile() {
        if (currentEditingFile == null) {
            Toast.makeText(this, "请先选择或创建一个文件", Toast.LENGTH_SHORT).show();
            return;
        }

        MapData currentData = collectCurrentMapData();
        if (mapFileModule.updateCurrentMapData(currentData)) {
            Toast.makeText(this, "文件更新成功", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "文件更新失败", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 查看所有文件
     */
    private void viewAllFiles() {
        List<String> fileNames = mapFileModule.getAllMapFileNames();
        if (fileNames.isEmpty()) {
            Toast.makeText(this, "没有找到任何地图文件", Toast.LENGTH_SHORT).show();
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("选择要编辑的文件");

        builder.setItems(fileNames.toArray(new String[0]), (dialog, which) -> {
            String selectedFile = fileNames.get(which);
            loadMapFile(selectedFile);
        });

        builder.setNegativeButton("取消", null);
        builder.show();
    }

    /**
     * 加载地图文件
     */
    private void loadMapFile(String fileName) {
        MapData mapData = mapFileModule.loadMapData(fileName);
        if (mapData != null) {
            fingerprintManager.clearAllFingerprints();
            fingerprintManager.importFromMapData(mapData);
            currentEditingFile = fileName;
            updateFileStatusDisplay();
            imageHandler.drawAllMarkers(fingerprintManager.getAllFingerprints());
            Toast.makeText(this, "文件加载成功", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "文件加载失败", Toast.LENGTH_SHORT).show();
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
        return "imported_map.json";
    }

    /**
     * 初始化网格控制模块
     */
    private void initGridControl() {
        CheckBox cbShowGrid = findViewById(R.id.cbShowGrid);
        SeekBar sbGridSize = findViewById(R.id.sbGridSize);
        TextView tvGridSize = findViewById(R.id.tvGridSize);

        MapData.GridConfig savedConfig = getSavedGridConfig();

        gridControlModule = new GridControlModule(cbShowGrid, sbGridSize, tvGridSize, savedConfig);

        gridControlModule.setOnGridChangeListener(config -> {
            if (imageHandler != null) {
                imageHandler.setGridConfig(config);
                List<WifiFingerprint> fingerprints = fingerprintManager.getAllFingerprints();
                if (!fingerprints.isEmpty()) {
                    imageHandler.drawAllMarkers(fingerprints);
                }
            }
        });
    }

    /**
     * 从保存的数据中获取网格配置
     */
    private MapData.GridConfig getSavedGridConfig() {
        MapData.GridConfig config = new MapData.GridConfig();
        config.setShow(false);
        config.setScale(50);
        return config;
    }

    // 查看导出文件
    private void viewExportedFiles() {
        try {
            if (mapFileModule != null) {
                mapFileModule.openSavedFilesDirectory();
                Toast.makeText(this, "正在打开导出文件目录", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "无法打开目录：" + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    // 导入JSON文件（修复：正确处理导入逻辑）
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

    // 保存当前地图
    private void saveCurrentMap() {
        showCustomFileNameDialog();
    }

    /**
     * 将指纹列表转换为MapData.PointData列表
     */
    private List<MapData.PointData> convertFingerprintsToPointData(List<WifiFingerprint> fingerprints) {
        List<MapData.PointData> pointDataList = new ArrayList<>();
        for (WifiFingerprint fp : fingerprints) {
            MapData.PointData pointData = new MapData.PointData();
            pointData.setSpecial(!TextUtils.isEmpty(fp.getLabel()));
            if (pointData.isSpecial()) {
                pointData.setLabel(fp.getLabel());
            } else {
                pointData.setPath(fp.getPath());
            }
            pointData.setX((float) fp.getPixelX());
            pointData.setY((float) fp.getPixelY());
            pointDataList.add(pointData);
        }
        return pointDataList;
    }

    /**
     * 保存地图数据到文件
     */
    private void saveMapToFile(String fileName) {
        MapData mapData = collectCurrentMapData();
        if (mapData != null && mapFileModule != null) {
            boolean success = mapFileModule.saveMapData(fileName, mapData);
            if (success) {
                Toast.makeText(this, "地图保存成功：" + fileName, Toast.LENGTH_SHORT).show();
                tvResult.setText("地图保存成功：\n文件名：" + fileName +
                        "\n点位数量：" + mapData.getPoints().size());
            } else {
                Toast.makeText(this, "地图保存失败", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, "无地图数据可保存", Toast.LENGTH_SHORT).show();
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
     * 初始化导入JSON的结果监听器
     */
    private void initImportLauncher() {
        importLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Uri selectedUri = result.getData().getData();
                        if (selectedUri != null) {
                            MapData importedData = mapFileModule.parseImportedJson(selectedUri, this);
                            if (importedData != null) {
                                fingerprintManager.importFromMapData(importedData);
                                imageHandler.drawAllMarkers(fingerprintManager.getAllFingerprints());
                                mapFileModule.setCurrentMapData(importedData);
                                currentEditingFile = importedData.getFileName() + ".json";
                                updateFileStatusDisplay();
                                Toast.makeText(this, "JSON导入成功", Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(this, "JSON解析失败", Toast.LENGTH_SHORT).show();
                            }
                        }
                    }
                }
        );
    }

    /**
     * 自定义文件名对话框
     */
    private void showCustomFileNameDialog() {
        EditText etFileName = new EditText(this);
        etFileName.setHint("请输入地图文件名（自动补全.json）");
        etFileName.setPadding(32, 16, 32, 16);

        String suggestedName = generateSuggestedFileName();
        etFileName.setText(suggestedName);

        new AlertDialog.Builder(this)
                .setTitle("自定义保存地图")
                .setView(etFileName)
                .setPositiveButton("确认保存", (dialog, which) -> {
                    String fileName = etFileName.getText().toString().trim();
                    if (TextUtils.isEmpty(fileName)) {
                        Toast.makeText(this, "文件名不能为空", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (!fileName.endsWith(".json")) {
                        fileName += ".json";
                    }
                    saveMapToFile(fileName);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /**
     * 生成建议的文件名
     */
    private String generateSuggestedFileName() {
        String zone = spinnerZone.getSelectedItem().toString();
        int floor = Integer.parseInt(spinnerFloor.getSelectedItem().toString());
        return String.format("%s_F%d_%s", zone, floor,
                new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date()));
    }

    /**
     * 收集当前地图数据
     */
    private MapData collectCurrentMapData() {
        MapData data = new MapData();
        List<WifiFingerprint> fingerprints = fingerprintManager.getAllFingerprints();
        data.setPoints(convertFingerprintsToPointData(fingerprints));

        if (gridControlModule != null) {
            data.setGridConfig(gridControlModule.getGridConfig());
        } else {
            MapData.GridConfig gridConfig = new MapData.GridConfig();
            gridConfig.setShow(false);
            gridConfig.setScale(50);
            data.setGridConfig(gridConfig);
        }

        return data;
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

        // 初始化Spinner
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

        // 点位类型切换监听
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
     * 初始化工具类（修复：赋值成员变量，避免空指针）
     */
    private void initManagers() {
        permissionManager = new PermissionManager(this);
        imageView = findViewById(R.id.imageView);
        imageHandler = new ImageHandler(this, findViewById(R.id.imageView));
        this.wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        coordinateManager = new CoordinateManager(imageHandler);
        permissionManager = new PermissionManager(this);
        fingerprintManager = new FingerprintManager();
        mapFileModule = new MapFileModule(this, fingerprintManager);
        wifiLocationManager = new WifiLocationManager(this, wifiManager, fingerprintManager);
        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);

        btnManageFingerprints = findViewById(R.id.btnManageFingerprints);
        btnExport = findViewById(R.id.btnExport);
        btnRealTimeLocate = findViewById(R.id.btnRealTimeLocate);
        btnShowAllMarkers = findViewById(R.id.btnShowAllMarkers);
        tvResult = findViewById(R.id.tvResult);

        CheckBox cbShowGrid = findViewById(R.id.cbShowGrid);
        SeekBar sbGridSize = findViewById(R.id.sbGridSize);
        TextView tvGridSize = findViewById(R.id.tvGridSize);
        RadioGroup rgPointType = findViewById(R.id.rgPointType);
        etPath = findViewById(R.id.etPath);
        etLabel = findViewById(R.id.etLabel);
    }

    /**
     * 设置事件监听
     */
    private void setListeners() {
        // 导入图片
        btnImportImage.setOnClickListener(v -> handleImportImage());
        // 扫描WiFi
        scanButton.setOnClickListener(v -> handleScanWifi());
        // 选择WiFi
        btnSelectWifi.setOnClickListener(v -> showWifiSelectDialog());
        // 管理指纹库
        btnManageFingerprints.setOnClickListener(v -> showFingerprintListDialog());
        // 一键保存
        btnOneClickSave.setOnClickListener(v -> {
            if (filteredWifis != null) {
                saveFingerprintToPixel(filteredWifis);
            } else {
                Toast.makeText(this, "请先完成WiFi扫描", Toast.LENGTH_SHORT).show();
            }
        });
        Button btnUpdateCurrentFile = findViewById(R.id.btnUpdateCurrentFile);
        if (btnUpdateCurrentFile != null) {
            btnUpdateCurrentFile.setOnClickListener(v -> updateCurrentMapFile());
        }
        Button btnFileManager = findViewById(R.id.btnOpenDrawer);
        btnFileManager.setOnClickListener(v -> showFileManagementDialog());
        // 导出指纹
        btnExport.setOnClickListener(v -> {
            String result = fingerprintManager.exportFingerprints();
            Toast.makeText(this, result, Toast.LENGTH_LONG).show();
            tvResult.setText("导出结果：\n" + result);
        });
        // 查看所有标记
        btnShowAllMarkers.setOnClickListener(v -> {
            List<WifiFingerprint> fingerprints = fingerprintManager.getAllFingerprints();
            if (fingerprints.isEmpty()) {
                Toast.makeText(this, "没有已绑定的坐标", Toast.LENGTH_SHORT).show();
                return;
            }
            imageHandler.drawAllMarkers(fingerprints);
            tvResult.setText("已显示所有坐标，共" + fingerprints.size() + "个");
        });
        // 实时定位
        btnRealTimeLocate.setOnClickListener(v -> handleRealTimeLocate());
        // 图片触摸监听
        setImageTouchListener();
    }

    /**
     * 更新当前地图文件
     */
    private void updateCurrentMapFile() {
        if (mapFileModule == null) {
            Toast.makeText(this, "文件模块未初始化", Toast.LENGTH_SHORT).show();
            return;
        }

        MapData currentData = collectCurrentMapData();
        if (currentData.getPoints().isEmpty()) {
            Toast.makeText(this, "无地图数据可更新", Toast.LENGTH_SHORT).show();
            return;
        }

        boolean success = mapFileModule.updateCurrentMapData(currentData);
        if (success) {
            Toast.makeText(this, "地图文件更新成功", Toast.LENGTH_SHORT).show();
            tvResult.setText("地图文件已更新\n点位数量：" + currentData.getPoints().size());
        } else {
            Toast.makeText(this, "地图文件更新失败", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 显示指纹点列表对话框
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
            showFingerprintEditDialog(selectedFp);
        });
        builder.setNegativeButton("取消", (dialog, which) -> dialog.dismiss());
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    /**
     * 处理图片导入
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
                    Toast.makeText(MainActivity.this, "需要存储权限才能导入图片", Toast.LENGTH_SHORT).show();
                }
            });
        } else {
            Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            intent.setType("image/*");
            imagePickerLauncher.launch(intent);
        }
    }

    /**
     * 处理WiFi扫描（修复：正确调用扫描方法）
     */

    private void startScanning() {
        isScanning = true;
        tvResult.setText("开始多次扫描WiFi（共" + MIN_SCAN_COUNT + "次，间隔3秒）...\n");
        scanButton.setEnabled(false);
        btnSelectWifi.setEnabled(false);
        btnOneClickSave.setEnabled(false);

        // 关键修改：直接传递 MainActivity.this 作为回调
        wifiScanner.performMultipleScans(
                MIN_SCAN_COUNT,
                3000,  // 间隔3秒
                MainActivity.this  // 使用当前Activity作为回调，而不是创建新的匿名对象
        );
    }
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
                    showLocationServiceDialog();
                    return;
                }

                startScanning();
            }



            @Override
            public void onPermissionDenied() {
                isScanning = false;
                scanButton.setEnabled(true);
                btnSelectWifi.setEnabled(true);
                btnOneClickSave.setEnabled(true);
                tvResult.append("扫描失败：权限不足或扫描被拒绝\n");
            }
        });
    }

    // 显示位置服务对话框
    private void showLocationServiceDialog() {
        new AlertDialog.Builder(this)
                .setTitle("位置服务未开启")
                .setMessage("扫描WiFi需要开启位置服务，请前往设置开启")
                .setPositiveButton("去设置", (dialog, which) -> {
                    // 跳转到位置服务设置页
                    startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                    // 延迟检查位置服务是否开启，若开启则重新启动扫描
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        if (wifiLocationManager != null && wifiLocationManager.isLocationEnabled()) {
                            startScanning(); // 此时可正常调用
                        }
                    }, 1000); // 延迟1秒检查（给用户操作时间）
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

    // 显示Toast
    void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    /**
     * 处理实时定位
     */
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
     * 显示WiFi选择对话框
     */
    private void showWifiSelectDialog() {
        if (multipleScans.size() < MIN_SCAN_COUNT) {
            Toast.makeText(this, "请先完成" + MIN_SCAN_COUNT + "次扫描", Toast.LENGTH_SHORT).show();
            return;
        }
        if (filteredWifis == null && fingerprintManager != null) {
            filteredWifis = fingerprintManager.filterAndSortWifi(multipleScans);
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
        int autoSelectCount = Math.min(6, filteredWifis.size());

        for (int i = 0; i < filteredWifis.size(); i++) {
            FilteredWifi wifi = filteredWifis.get(i);
            checkBoxes[i] = new CheckBox(this);
            checkBoxes[i].setText(String.format("%s（MAC：%s，信号：%ddBm）",
                    wifi.getSsid(),
                    wifi.getBssid().substring(12),
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
     * 保存指纹到像素点
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

        if ((label.isEmpty() && path.isEmpty()) || (!label.isEmpty() && !path.isEmpty())) {
            Toast.makeText(this, "请仅填写一项：特殊点名称（label）或普通点距离（path）", Toast.LENGTH_SHORT).show();
            return;
        }

        boolean success = fingerprintManager != null &&
                fingerprintManager.saveFingerprint(x, y, floor, zone, label, path, wifis);

        if (success) {
            String tip = label.isEmpty() ? "普通点（" + path + "）" : "特殊点（" + label + "）";
            Toast.makeText(this, tip + "保存成功：(" + (int) x + "," + (int) y + ")", Toast.LENGTH_SHORT).show();

            StringBuilder sb = new StringBuilder();
            sb.append("✅ 指纹点已保存\n");
            sb.append("坐标: (").append((int) x).append(", ").append((int) y).append(")\n");
            sb.append("楼层: ").append(floor).append("\n");
            sb.append("区域: ").append(zone).append("\n");
            sb.append(label.isEmpty() ? "普通点距离: " + path : "特殊点名称: " + label).append("\n");
            sb.append("WiFi数量: ").append(wifis.size()).append("个");

            tvResult.setText(sb.toString());
            etLabel.setText("");
            etPath.setText("");
        } else {
            Toast.makeText(this, "保存失败，请重试", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 检查基础条件
     */
    private boolean checkBasicConditions() {
        if (imageHandler == null || imageHandler.getOriginalImage() == null) {
            Toast.makeText(this, "请先导入平面图", Toast.LENGTH_SHORT).show();
            return false;
        }

        if (etPixelX.getText().toString().isEmpty() ||
                etPixelY.getText().toString().isEmpty()) {
            Toast.makeText(this, "请点击图片获取像素坐标", Toast.LENGTH_SHORT).show();
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
     * 验证保存前条件
     */
    private boolean validateBeforeSave() {
        if (selectedWifis.size() < MIN_SELECT_WIFI_COUNT) {
            showToast("请至少选择3个WiFi");
            return false;
        }

        float x = Float.parseFloat(etPixelX.getText().toString());
        float y = Float.parseFloat(etPixelY.getText().toString());
        if (!coordinateManager.isValidCoordinate(x, y)) {
            showToast("坐标超出地图范围");
            return false;
        }

        boolean isSpecialPoint = rgPointType.getCheckedRadioButtonId() == R.id.rbSpecialPoint;
        if (isSpecialPoint && TextUtils.isEmpty(etLabel.getText())) {
            showToast("特殊点请填写标签");
            return false;
        }
        return true;
    }

    /**
     * 显示指纹点编辑对话框
     */
    private void showFingerprintEditDialog(WifiFingerprint fingerprint) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(fingerprint.getLabel().isEmpty() ? "编辑普通点" : "编辑特殊点");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 30, 50, 30);

        EditText etLabelEdit = new EditText(this);
        etLabelEdit.setHint("特殊点名称（如：图书馆a口）");
        etLabelEdit.setText(fingerprint.getLabel());
        etLabelEdit.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        layout.addView(etLabelEdit);

        EditText etPathEdit = new EditText(this);
        etPathEdit.setHint("普通点距离描述（如：距离图书馆a口3米）");
        etPathEdit.setText(fingerprint.getPath());
        etPathEdit.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        layout.addView(etPathEdit);

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

        TextView tvCoordInfo = new TextView(this);
        tvCoordInfo.setText(String.format("坐标: (%.0f, %.0f) - 不可编辑",
                fingerprint.getPixelX(),
                fingerprint.getPixelY()));
        tvCoordInfo.setTextColor(Color.GRAY);
        tvCoordInfo.setPadding(0, 20, 0, 0);
        layout.addView(tvCoordInfo);

        TextView tvWifiInfo = new TextView(this);
        tvWifiInfo.setText("包含 " + fingerprint.getFilteredWifis().size() + " 个WiFi信号");
        tvWifiInfo.setTextColor(Color.GRAY);
        layout.addView(tvWifiInfo);

        builder.setView(layout);

        builder.setPositiveButton("保存", (dialog, which) -> {
            String newLabel = etLabelEdit.getText().toString().trim();
            String newPath = etPathEdit.getText().toString().trim();
            int newFloor = Integer.parseInt(spinnerFloorEdit.getSelectedItem().toString());
            String newZone = spinnerZoneEdit.getSelectedItem().toString();

            if ((newLabel.isEmpty() && newPath.isEmpty()) || (!newLabel.isEmpty() && !newPath.isEmpty())) {
                Toast.makeText(MainActivity.this, "请仅填写一项：特殊点名称或普通点距离", Toast.LENGTH_SHORT).show();
                return;
            }

            fingerprint.setLabel(newLabel);
            fingerprint.setPath(newPath);
            fingerprint.setFloor(newFloor);
            fingerprint.setZone(newZone);

            boolean success = fingerprintManager != null && fingerprintManager.updateFingerprint(fingerprint);

            if (success) {
                String tip = newLabel.isEmpty() ? "普通点（" + newPath + "）" : "特殊点（" + newLabel + "）";
                Toast.makeText(MainActivity.this, tip + "更新成功", Toast.LENGTH_SHORT).show();
                imageHandler.drawAllMarkers(fingerprintManager.getAllFingerprints());
                tvResult.setText("已更新：" + tip);
            } else {
                Toast.makeText(MainActivity.this, "更新失败", Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNegativeButton("删除", (dialog, which) -> {
            new AlertDialog.Builder(MainActivity.this)
                    .setTitle("确认删除")
                    .setMessage("确定要删除这个指纹点吗？此操作不可恢复。")
                    .setPositiveButton("删除", (d, w) -> {
                        boolean success = fingerprintManager != null && fingerprintManager.deleteFingerprint(fingerprint);
                        if (success) {
                            String tip = fingerprint.getLabel().isEmpty() ? "普通点" : "特殊点（" + fingerprint.getLabel() + "）";
                            Toast.makeText(MainActivity.this, tip + "删除成功", Toast.LENGTH_SHORT).show();
                            imageHandler.drawAllMarkers(fingerprintManager.getAllFingerprints());
                            tvResult.setText("已删除：" + tip);
                        } else {
                            Toast.makeText(MainActivity.this, "删除失败", Toast.LENGTH_SHORT).show();
                        }
                    })
                    .setNegativeButton("取消", null)
                    .show();
        });

        builder.setNeutralButton("重新扫描WiFi", (dialog, which) -> {
            currentEditingFingerprint = fingerprint;
            startRescanForFingerprint();
        });

        AlertDialog dialog = builder.create();
        dialog.show();
    }

    /**
     * 重新扫描WiFi（修复：实际执行扫描逻辑）
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

        // 调用多次扫描方法，使用当前类作为回调
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
     * 显示指纹库统计信息
     */
    private void showStatistics() {
        if (fingerprintManager == null) return;

        List<WifiFingerprint> fingerprints = fingerprintManager.getAllFingerprints();
        if (fingerprints.isEmpty()) {
            tvResult.setText("📊 指纹库统计：无保存的指纹点");
            return;
        }

        StringBuilder stats = new StringBuilder();
        stats.append("📊 指纹库统计\n");
        stats.append("总指纹点: ").append(fingerprints.size()).append("个\n");

        int specialCount = 0;
        int normalCount = 0;
        for (WifiFingerprint fp : fingerprints) {
            if (fp.getLabel().isEmpty()) {
                normalCount++;
            } else {
                specialCount++;
            }
        }
        stats.append("特殊点（带名称）: ").append(specialCount).append("个\n");
        stats.append("普通点（带距离）: ").append(normalCount).append("个\n");

        Map<Integer, Integer> floorStats = new HashMap<>();
        for (WifiFingerprint fp : fingerprints) {
            floorStats.put(fp.getFloor(), floorStats.getOrDefault(fp.getFloor(), 0) + 1);
        }
        for (Map.Entry<Integer, Integer> entry : floorStats.entrySet()) {
            stats.append("楼层 ").append(entry.getKey()).append(": ").append(entry.getValue()).append("个\n");
        }

        int totalWifis = 0;
        for (WifiFingerprint fp : fingerprints) {
            totalWifis += fp.getFilteredWifis().size();
        }
        stats.append("总WiFi信号: ").append(totalWifis).append("个");

        tvResult.setText(stats.toString());
    }

    /**
     * 处理图片点击事件
     */
    private void handleImageClick(float screenX, float screenY) {
        if (imageHandler == null || coordinateManager == null) return;

        float[] coords = coordinateManager.getCoordinatesFromTouch(screenX, screenY);
        if (coords == null) return;

        List<WifiFingerprint> fingerprints = fingerprintManager != null ? fingerprintManager.getAllFingerprints() : new ArrayList<>();
        if (fingerprints.isEmpty()) {
            etPixelX.setText(String.valueOf(Math.round(coords[0])));
            etPixelY.setText(String.valueOf(Math.round(coords[1])));
            return;
        }

        WifiFingerprint clickedFingerprint = coordinateManager.getClickedFingerprint(screenX, screenY, fingerprints);
        if (clickedFingerprint != null) {
            showFingerprintEditDialog(clickedFingerprint);
        } else {
            etPixelX.setText(String.valueOf(Math.round(coords[0])));
            etPixelY.setText(String.valueOf(Math.round(coords[1])));
        }
    }

    /**
     * 图片触摸监听
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
     * 实时定位回调接口
     */
    public interface LocationCallback {
        void onLocationUpdate(WifiLocationManager.LocationResult result, String displayText);
        void onProximityAlert(String message);
    }

    /**
     * 带播报功能的实时定位
     */
    private void handleRealTimeLocateWithAnnouncement(LocationCallback callback) {
        if (wifiLocationManager == null) return;

        WifiLocationManager.LocationResult result = wifiLocationManager.startRealTimeLocation();
        if (result != null) {
            String displayText = "";
            List<WifiFingerprint> fingerprints = fingerprintManager != null ? fingerprintManager.getAllFingerprints() : new ArrayList<>();
            for (WifiFingerprint fp : fingerprints) {
                if (fp.getPixelX() == result.getX() && fp.getPixelY() == result.getY() &&
                        fp.getFloor() == result.getFloor()) {
                    displayText = fp.getLabel().isEmpty() ? fp.getPath() : fp.getLabel();
                    break;
                }
            }

            if (imageHandler != null) {
                imageHandler.drawLocationMarker((float) result.getX(), (float) result.getY(), displayText);
            }

            if (callback != null) {
                callback.onLocationUpdate(result, displayText);
                if (!displayText.isEmpty()) {
                    callback.onProximityAlert("当前位置：" + displayText);
                }
            }

            tvResult.setText(String.format("定位成功：\n楼层：%d\n坐标：(%.0f,%.0f)\n当前位置：%s",
                    result.getFloor(),
                    result.getX(),
                    result.getY(),
                    displayText.isEmpty() ? "未标注" : displayText));
        } else {
            tvResult.setText("定位失败，未找到匹配的指纹数据");
        }
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

    // 补充缺失的saveCurrentFingerprint方法（按钮绑定使用）
    private void saveCurrentFingerprint() {
        if (filteredWifis == null || filteredWifis.isEmpty()) {
            Toast.makeText(this, "请先完成WiFi扫描", Toast.LENGTH_SHORT).show();
            return;
        }
        saveFingerprintToPixel(filteredWifis);
    }
}