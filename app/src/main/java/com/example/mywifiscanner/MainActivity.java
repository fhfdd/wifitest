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
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends AppCompatActivity implements WifiScanner.ScanCallback {

    private static final String TAG = "MainActivity";
    private static final int MIN_SCAN_COUNT = 3;
    private static final int MIN_SELECT_WIFI_COUNT = 1;
    private TextView tvFileStatus, tvResult, navHeaderFileStatus, tvPermissionTip, tvCurrentFile;
    private String currentEditingFile = null; // 当前编辑的指纹库文件名
    private CoordinateManager coordinateManager;
    private RedPointPhotoView redPointPhotoView;
    private MapFileModule mapFileModule; // 指纹库文件管理
    private InputSwitchModule inputSwitchModule;
    private Button btnShowAllMarkers, btnManageFingerprints, btnImportImage, scanButton, btnSelectWifi, btnOneClickSave, btnExport, btnRealTimeLocate;
    private ImageView imageView, btnMore;
    private EditText etLabel, etPath;
    private EditText etPixelX, etPixelY;
    private Spinner spinnerFloor, spinnerZone;
    private DrawerLayout drawerLayout;
    private NavigationView navView;
    private FingerprintManager fingerprintManager; // 指纹管理器
    private ActivityResultLauncher<Intent> importLauncher; // 指纹库导入启动器
    private RadioGroup rgPointType;

    // 新增配置管理器
    private ConfigManager configManager;
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
    private boolean isMarkersVisible = false;
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
        Log.d(TAG, "扫描完成回调，收到扫描次数: " + (allScanResults != null ? allScanResults.size() : 0));

        // 确保结果不为空
        if (allScanResults == null || allScanResults.isEmpty()) {
            Log.e(TAG, "扫描结果为空");
            runOnUiThread(() -> {
                tvResult.append("扫描完成，但未获得任何扫描结果\n");
                scanButton.setEnabled(true);
                isScanning = false;
            });
            return;
        }

        // 存储扫描结果到成员变量
        this.multipleScans = new ArrayList<>(allScanResults);

        // 处理WiFi数据
        try {
            this.filteredWifis = WifiDataProcessor.processMultipleScansWithAverage(allScanResults, configManager);
            Log.d(TAG, "WiFi处理完成，找到WiFi数量: " + (filteredWifis != null ? filteredWifis.size() : 0));

            runOnUiThread(() -> {
                if (filteredWifis != null && !filteredWifis.isEmpty()) {
                    tvResult.append("✅ 多次扫描完成！共发现 " + filteredWifis.size() + " 个稳定WiFi\n");

                    // 无论WiFi数量多少，都启用选择按钮（这样用户至少能看到WiFi列表）
                    btnSelectWifi.setEnabled(true);

                    btnOneClickSave.setEnabled(filteredWifis.size() >= MIN_SELECT_WIFI_COUNT);

                    // 显示WiFi信号强度范围
                    if (filteredWifis.size() > 0) {
                        int maxRssi = filteredWifis.get(0).getRssi();
                        int minRssi = filteredWifis.get(filteredWifis.size() - 1).getRssi();
                        tvResult.append("信号强度范围: " + maxRssi + "dBm 到 " + minRssi + "dBm\n");
                    }
                } else {
                    tvResult.append("❌ 扫描完成，但未发现稳定的WiFi信号\n");
                    // 即使没有WiFi，也允许用户查看（会显示空列表）
                    btnSelectWifi.setEnabled(true);
                    btnOneClickSave.setEnabled(false);
                }

                scanButton.setEnabled(true);
                isScanning = false;

                Log.d(TAG, "UI更新完成 - btnSelectWifi: " + btnSelectWifi.isEnabled() +
                        ", btnOneClickSave: " + btnOneClickSave.isEnabled());
            });

        } catch (Exception e) {
            Log.e(TAG, "处理WiFi数据时出错: " + e.getMessage(), e);
            runOnUiThread(() -> {
                tvResult.append("❌ 处理WiFi数据时出错: " + e.getMessage() + "\n");
                scanButton.setEnabled(true);
                isScanning = false;
            });
        }
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
        // 初始化工具类（包含配置管理器）
        initManagers();
        // 初始化导入启动器
        initImportLauncher();
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

    // 绑定侧边栏菜单事件（包含所有文件操作）
    private void bindNavMenuEvents() {
        navView.setNavigationItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_create_new_file) {
                createNewFile();
            } else if (id == R.id.nav_import_file) {
                importJsonFile();
            } else if (id == R.id.nav_update_current_file) {
                updateCurrentFile();
            } else if (id == R.id.nav_save_as) { // 新增：另存为功能
                saveAsNewFile();
            } else if (id == R.id.nav_view_all_files) {
                viewAllFiles();
            } else if (id == R.id.nav_export_simplified) {
                exportSimplifiedData();
            } else if (id == R.id.nav_view_exported_files) {
                viewExportedFiles();
            } else if (id == R.id.btn_more) {
                showSettingsDialog();
            }
            drawerLayout.closeDrawer(GravityCompat.START);
            return true;
        });
    }

    // 显示配置设置对话框
    private void showSettingsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("配置设置");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 30, 50, 30);

        // 扫描次数设置
        TextView tvScanCount = new TextView(this);
        tvScanCount.setText("扫描次数: " + configManager.getScanCount());
        layout.addView(tvScanCount);

        SeekBar sbScanCount = new SeekBar(this);
        sbScanCount.setMax(10);
        sbScanCount.setProgress(configManager.getScanCount() - 1);
        sbScanCount.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int count = progress + 1;
                tvScanCount.setText("扫描次数: " + count);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        layout.addView(sbScanCount);

        // WiFi信号阈值设置
        TextView tvWifiThreshold = new TextView(this);
        tvWifiThreshold.setText("WiFi信号阈值: " + configManager.getWifiThreshold() + "dBm");
        layout.addView(tvWifiThreshold);

        SeekBar sbWifiThreshold = new SeekBar(this);
        sbWifiThreshold.setMax(40); // -50到-90dBm的范围
        sbWifiThreshold.setProgress(-50 - configManager.getWifiThreshold());
        sbWifiThreshold.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int threshold = -50 - progress;
                tvWifiThreshold.setText("WiFi信号阈值: " + threshold + "dBm");
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        layout.addView(sbWifiThreshold);

        builder.setView(layout);
        builder.setPositiveButton("保存", (dialog, which) -> {
            int newScanCount = sbScanCount.getProgress() + 1;
            int newWifiThreshold = -50 - sbWifiThreshold.getProgress();

            configManager.setScanCount(newScanCount);
            configManager.setWifiThreshold(newWifiThreshold);
            Toast.makeText(this, "配置已保存", Toast.LENGTH_SHORT).show();
        });
        builder.setNegativeButton("取消", null);
        builder.show();
    }

    // 绑定主布局按钮事件
    private void bindMainButtons() {
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
     * 创建新指纹库文件 - 数组包装版本
     */
    private void createNewFile() {
        EditText etFileName = new EditText(this);
        etFileName.setHint("请输入指纹库文件名（不含.json后缀）");

        // 使用数组包装，数组引用是final的
        final String[] fileNameHolder = new String[1];

        new AlertDialog.Builder(this)
                .setTitle("创建新指纹库")
                .setView(etFileName)
                .setPositiveButton("创建", (dialog, which) -> {
                    String inputName = etFileName.getText().toString().trim();
                    if (inputName.isEmpty()) {
                        Toast.makeText(this, "文件名不能为空", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // 确保文件名以.json结尾
                    if (!inputName.endsWith(".json")) {
                        inputName += ".json";
                    }

                    fileNameHolder[0] = inputName;
                    saveNewFile(fileNameHolder[0]); // 现在可以正常调用了
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /**
     * 保存新文件 - 独立方法
     */
    private void saveNewFile(String fileName) {
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
    }

    /**
     * 另存为新文件（新增功能）
     */
    private void saveAsNewFile() {
        if (fingerprintManager.getFingerprintCount() == 0) {
            Toast.makeText(this, "当前无指纹数据可保存", Toast.LENGTH_SHORT).show();
            return;
        }

        EditText etFileName = new EditText(this);
        etFileName.setHint("请输入新文件名（不含.json后缀）");
        if (currentEditingFile != null) {
            // 建议基于当前文件名修改
            String baseName = currentEditingFile.replace(".json", "_copy");
            etFileName.setText(baseName);
        }

        new AlertDialog.Builder(this)
                .setTitle("另存为新指纹库")
                .setView(etFileName)
                .setPositiveButton("保存", (dialog, which) -> {
                    String fileName = etFileName.getText().toString().trim();
                    if (fileName.isEmpty()) {
                        Toast.makeText(this, "文件名不能为空", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (!fileName.endsWith(".json")) {
                        fileName += ".json";
                    }

                    List<WifiFingerprint> currentFingerprints = fingerprintManager.getAllFingerprints();
                    boolean success = mapFileModule.saveFingerprints(fileName, currentFingerprints);
                    if (success) {
                        currentEditingFile = fileName;
                        updateFileStatusDisplay();
                        Toast.makeText(this, "另存为成功：" + fileName, Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "另存为失败，请重试", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
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

    /**
     * 导入JSON格式的指纹库文件（优化：允许自定义保存文件名）
     */
    /**
     * 导入JSON格式的指纹库文件（修复版本）
     */
    private void importJsonFile() {
        try {
            // 检查存储权限
            if (!permissionManager.checkStoragePermission()) {
                permissionManager.requestStoragePermission(new PermissionManager.PermissionCallback() {
                    @Override
                    public void onPermissionGranted() {
                        launchFilePicker();
                    }

                    @Override
                    public void onPermissionDenied() {
                        Toast.makeText(MainActivity.this, "需要存储权限才能导入文件", Toast.LENGTH_SHORT).show();
                    }
                });
            } else {
                launchFilePicker();
            }
        } catch (Exception e) {
            Toast.makeText(this, "无法导入文件：" + e.getMessage(), Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Import file error", e);
        }
    }

    /**
     * 启动文件选择器
     */
    private void launchFilePicker() {
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/json");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            // 可选：设置初始目录
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI,
                        DocumentsContract.buildRootUri("com.android.externalstorage.documents", "primary"));
            }

            importLauncher.launch(intent);
        } catch (Exception e) {
            Toast.makeText(this, "无法打开文件选择器：" + e.getMessage(), Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Launch file picker error", e);
        }
    }

    /**
     * 初始化侧边栏
     */
    private void initDrawerLayout() {
        drawerLayout = findViewById(R.id.drawerLayout);
        navView = findViewById(R.id.navView);
        setupNavigationView();
    }

    /**
     * 初始化导入指纹库的结果监听器（修复：Uri转InputStream，匹配方法参数）
     */
    /**
     * 初始化导入指纹库的结果监听器（修复版本）
     */
    private void initImportLauncher() {
        importLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Uri selectedUri = result.getData().getData();
                        if (selectedUri != null) {
                            handleImportFile(selectedUri);
                        }
                    }
                }
        );
    }

    /**
     * 处理文件导入（独立方法，修复版本）
     */
    private void handleImportFile(Uri selectedUri) {
        InputStream inputStream = null;
        try {
            // 1. 从Uri获取InputStream
            inputStream = getContentResolver().openInputStream(selectedUri);
            if (inputStream == null) {
                Toast.makeText(this, "无法打开文件流", Toast.LENGTH_SHORT).show();
                return;
            }

            // 2. 解析指纹数据
            List<WifiFingerprint> importedFingerprints = mapFileModule.parseImportedFingerprints(inputStream);

            if (importedFingerprints != null && !importedFingerprints.isEmpty()) {
                // 成功导入，让用户输入保存文件名
                showImportSuccessDialog(selectedUri, importedFingerprints);
            } else {
                Toast.makeText(this, "指纹库解析失败（空数据或格式错误）", Toast.LENGTH_SHORT).show();
                Log.e(TAG, "导入的指纹数据为空或解析失败");
            }
        } catch (FileNotFoundException e) {
            Toast.makeText(this, "文件未找到：" + e.getMessage(), Toast.LENGTH_SHORT).show();
            Log.e(TAG, "File not found when importing", e);
        } catch (Exception e) {
            Toast.makeText(this, "导入失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Error during import", e);
        } finally {
            // 3. 关闭流
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    Log.e(TAG, "Failed to close input stream", e);
                }
            }
        }
    }

    /**
     * 显示导入成功对话框（修复版本）
     */
    private void showImportSuccessDialog(Uri selectedUri, List<WifiFingerprint> importedFingerprints) {
        EditText etFileName = new EditText(this);
        String defaultName = getFileNameFromUri(selectedUri);
        etFileName.setText(defaultName);
        etFileName.setHint("请输入保存的文件名（含.json）");

        new AlertDialog.Builder(this)
                .setTitle("导入成功")
                .setMessage("共导入" + importedFingerprints.size() + "条指纹，设置保存文件名：")
                .setView(etFileName)
                .setPositiveButton("保存", (d, w) -> {
                    String fileName = etFileName.getText().toString().trim();
                    if (fileName.isEmpty()) {
                        Toast.makeText(this, "文件名不能为空", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // 确保文件名以.json结尾
                    if (!fileName.endsWith(".json")) {
                        fileName += ".json";
                    }

                    // 保存导入的指纹到新文件
                    boolean saveSuccess = mapFileModule.saveFingerprints(fileName, importedFingerprints);
                    if (saveSuccess) {
                        // 更新当前编辑状态
                        fingerprintManager.loadFromFile(importedFingerprints);
                        imageHandler.drawAllMarkers(importedFingerprints);
                        currentEditingFile = fileName;
                        updateFileStatusDisplay();
                        Toast.makeText(this, "已保存为：" + fileName, Toast.LENGTH_SHORT).show();
                        tvResult.setText("导入成功！共" + importedFingerprints.size() + "条指纹，已保存为：" + fileName);
                    } else {
                        Toast.makeText(this, "文件保存失败，请重试", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }


    /**
     * 初始化控件
     */
    private void initViews() {
        drawerLayout = findViewById(R.id.drawerLayout);
        navView = findViewById(R.id.navView);
        btnMore = findViewById(R.id.btn_more);
        redPointPhotoView = findViewById(R.id.imageView);
        tvCurrentFile = findViewById(R.id.tv_current_file);
        View headerView = navView.getHeaderView(0);
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


        // 在 initViews() 方法末尾添加以下代码
        Log.d(TAG, "初始化视图完成");
        Log.d(TAG, "按钮状态 - btnSelectWifi: " + btnSelectWifi.isEnabled() +
                ", btnOneClickSave: " + btnOneClickSave.isEnabled() +
                ", btnShowAllMarkers: " + btnShowAllMarkers.isEnabled());
    }



    /**
     * 初始化工具类（适配简化后的模块）- 添加日志版本
     */
    private void initManagers() {
        Log.d(TAG, "开始初始化管理器");

        permissionManager = new PermissionManager(this);
        Log.d(TAG, "权限管理器初始化完成");

        // 初始化配置管理器
        configManager = new ConfigManager(this);
        Log.d(TAG, "配置管理器初始化完成，扫描次数: " + configManager.getScanCount());

        imageView = findViewById(R.id.imageView);
        imageHandler = new ImageHandler(this, imageView);
        Log.d(TAG, "图片处理器初始化完成");

        wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        if (wifiManager == null) {
            Log.e(TAG, "无法获取WifiManager服务");
            Toast.makeText(this, "无法访问WiFi功能", Toast.LENGTH_SHORT).show();
        } else {
            Log.d(TAG, "WifiManager获取成功");
        }

        coordinateManager = new CoordinateManager(imageHandler);
        fingerprintManager = new FingerprintManager();
        mapFileModule = new MapFileModule(this);

        wifiLocationManager = new WifiLocationManager(this, wifiManager, fingerprintManager, configManager);
        Log.d(TAG, "定位管理器初始化完成");

        wifiScanner = new WifiScanner(this, wifiManager);
        Log.d(TAG, "WiFi扫描器初始化完成");

        // 绑定控件引用
        btnManageFingerprints = findViewById(R.id.btnManageFingerprints);
        btnExport = findViewById(R.id.btnExport);
        btnRealTimeLocate = findViewById(R.id.btnRealTimeLocate);
        btnShowAllMarkers = findViewById(R.id.btnShowAllMarkers);
        tvResult = findViewById(R.id.tvResult);

        Log.d(TAG, "所有管理器初始化完成");
    }

    /**
     * 设置事件监听（完整修复版本 - 确保所有按钮都有效）
     */
    private void setListeners() {
        Log.d(TAG, "开始设置按钮监听器");

        // 导入固定地图
        btnImportImage.setOnClickListener(v -> {
            Log.d(TAG, "点击导入图片按钮");
            handleImportImage();
        });

        // 更多按钮
        btnMore.setOnClickListener(v -> {
            Log.d(TAG, "点击更多按钮");
            updateFileStatusDisplay();
            drawerLayout.openDrawer(GravityCompat.START);
        });

        // 扫描WiFi
        scanButton.setOnClickListener(v -> {
            Log.d(TAG, "点击扫描WiFi按钮");
            handleScanWifi();
        });

        // 选择WiFi（手动筛选）- 修复：添加点击监听
        btnSelectWifi.setOnClickListener(v -> {
            Log.d(TAG, "点击选择WiFi按钮 - 启用状态: " + btnSelectWifi.isEnabled());
            Log.d(TAG, "filteredWifis状态: " + (filteredWifis != null ? filteredWifis.size() + "个" : "null"));

            if (!btnSelectWifi.isEnabled()) {
                Toast.makeText(this, "按钮未启用，请先完成WiFi扫描", Toast.LENGTH_SHORT).show();
                return;
            }

            if (filteredWifis == null || filteredWifis.isEmpty()) {
                Toast.makeText(this, "请先完成WiFi扫描", Toast.LENGTH_SHORT).show();
                return;
            }
            showWifiSelectDialog();
        });

        // 管理指纹库（查看/编辑/删除指纹）
        btnManageFingerprints.setOnClickListener(v -> {
            Log.d(TAG, "点击管理指纹库按钮");
            showFingerprintListDialog();
        });

        // 一键保存指纹（使用筛选后的所有WiFi）- 修复：添加点击监听
        btnOneClickSave.setOnClickListener(v -> {
            Log.d(TAG, "点击一键保存按钮 - 启用状态: " + btnOneClickSave.isEnabled());
            Log.d(TAG, "filteredWifis状态: " + (filteredWifis != null ? filteredWifis.size() + "个" : "null"));

            if (!btnOneClickSave.isEnabled()) {
                Toast.makeText(this, "按钮未启用，请先完成WiFi扫描", Toast.LENGTH_SHORT).show();
                return;
            }


            saveCurrentFingerprint();
        });

        // 导出指纹库
        btnExport.setOnClickListener(v -> {
            Log.d(TAG, "点击导出按钮");
            exportSimplifiedData();
        });

        // 查看所有标记（切换显示/隐藏）- 修复：实现切换功能
        btnShowAllMarkers.setOnClickListener(v -> {
            Log.d(TAG, "点击查看坐标按钮");
            toggleMarkersDisplay();
        });

        // 实时定位按钮
        btnRealTimeLocate.setOnClickListener(v -> {
            Log.d(TAG, "点击实时定位按钮");
            handleRealTimeLocate();
        });

        // 图片触摸监听（获取坐标或编辑指纹）
        setImageTouchListener();

        Log.d(TAG, "所有按钮监听器设置完成");
    }

    /**
     * 切换标记显示/隐藏（修复版本）
     */
    private void toggleMarkersDisplay() {
        List<WifiFingerprint> fingerprints = fingerprintManager.getAllFingerprints();

        if (fingerprints.isEmpty()) {
            Toast.makeText(this, "没有已保存的指纹", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!isMarkersVisible) {
            // 显示标记
            imageHandler.drawAllMarkers(fingerprints);
            btnShowAllMarkers.setText("隐藏坐标");
            btnShowAllMarkers.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#FF9800"))); // 橙色提示
            tvResult.setText("已显示所有指纹标记，共" + fingerprints.size() + "个");
            isMarkersVisible = true;
            Log.d(TAG, "显示标记，数量: " + fingerprints.size());
        } else {
            // 隐藏标记 - 重新加载原始图片
            if (imageHandler.getOriginalImage() != null) {
                imageHandler.getImageView().setImageBitmap(imageHandler.getOriginalImage());
                btnShowAllMarkers.setText("查看坐标");
                btnShowAllMarkers.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#1565C0"))); // 恢复蓝色
                tvResult.setText("已隐藏所有指纹标记");
                isMarkersVisible = false;
                Log.d(TAG, "隐藏标记");
            }
        }
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
     * 处理WiFi扫描（修复版本 - 解决lambda表达式变量问题）
     */
    /**
     * 处理WiFi扫描（增强版本）
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
        if (wifiScanner == null) {
            Toast.makeText(this, "WiFi扫描器初始化失败", Toast.LENGTH_SHORT).show();
            return;
        }

        // 提前获取配置参数
        final int scanCount = configManager.getScanCount();
        final int intervalMs = configManager.getScanInterval();

        Log.d(TAG, "开始WiFi扫描流程，扫描次数: " + scanCount + ", 间隔: " + intervalMs + "ms");

        permissionManager.requestLocationPermission(new PermissionManager.PermissionCallback() {
            @Override
            public void onPermissionGranted() {
                Log.d(TAG, "位置权限已获取，检查位置服务");

                // 检查位置服务是否开启
                if (wifiLocationManager != null && !wifiLocationManager.isLocationEnabled()) {
                    Log.w(TAG, "位置服务未开启");
                    showLocationServiceDialog();
                    return;
                }

                Log.d(TAG, "开始WiFi扫描");
                startScanning(scanCount, intervalMs);
            }

            @Override
            public void onPermissionDenied() {
                Log.e(TAG, "位置权限被拒绝");
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "需要位置权限才能扫描WiFi", Toast.LENGTH_SHORT).show();
                    showPermissionRationaleDialog("权限缺失", "扫描WiFi需要位置权限，请在设置中开启");
                });
            }
        });
    }

    /**
     * 开始扫描WiFi（独立方法，修复版本）
     */
    private void startScanning(int scanCount, int intervalMs) {
        isScanning = true;
        tvResult.setText(String.format("开始多次扫描WiFi（共%d次，间隔%d秒）...\n",
                scanCount, intervalMs / 1000));
        scanButton.setEnabled(false);
        btnSelectWifi.setEnabled(false);
        btnOneClickSave.setEnabled(false);

        // 执行多次扫描
        wifiScanner.performMultipleScans(
                scanCount,
                intervalMs,
                MainActivity.this // 当前类作为回调
        );
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
    // 处理实时定位（修复版本）
    private void handleRealTimeLocate() {
        // 简化检查条件，主要关注指纹库是否存在
        if (fingerprintManager == null || fingerprintManager.getAllFingerprints().isEmpty()) {
            Toast.makeText(this, "请先采集指纹数据", Toast.LENGTH_SHORT).show();
            return;
        }

        // 简化权限检查
        if (permissionManager == null || !permissionManager.checkLocationPermission()) {
            Toast.makeText(this, "需要位置权限", Toast.LENGTH_SHORT).show();
            permissionManager.requestLocationPermission(new PermissionManager.PermissionCallback() {
                @Override
                public void onPermissionGranted() {
                    performRealTimeLocation();
                }

                @Override
                public void onPermissionDenied() {
                    Toast.makeText(MainActivity.this, "需要位置权限才能测试", Toast.LENGTH_SHORT).show();
                }
            });
            return;
        }

        // 执行定位（指纹测试）
        performRealTimeLocation();
    }

    /**
     * 执行实时定位逻辑（增强版本 - 添加详细日志和容错）
     */
    private void performRealTimeLocation() {
        if (wifiLocationManager == null) {
            Toast.makeText(this, "定位管理器未初始化", Toast.LENGTH_SHORT).show();
            return;
        }

        // 显示扫描提示
        tvResult.setText("正在扫描WiFi信号进行定位...\n请确保：\n• 已开启WiFi\n• 已授予位置权限\n• 当前位置有WiFi信号");

        Log.d(TAG, "开始执行实时定位");

        // 获取当前指纹库信息用于调试
        List<WifiFingerprint> fingerprints = fingerprintManager.getAllFingerprints();
        Log.d(TAG, "指纹库中共有 " + fingerprints.size() + " 个指纹点");

        // 执行定位
        WifiLocationManager.LocationResult result = wifiLocationManager.startRealTimeLocation();

        if (result != null) {
            // 定位成功
            Log.d(TAG, "定位成功 - 坐标: (" + result.getX() + ", " + result.getY() + "), 楼层: " + result.getFloor());

            String displayText = "";
            boolean foundExactMatch = false;

            // 查找匹配的指纹点信息 - 使用容差匹配
            for (WifiFingerprint fp : fingerprints) {
                double distance = Math.sqrt(Math.pow(fp.getPixelX() - result.getX(), 2) +
                        Math.pow(fp.getPixelY() - result.getY(), 2));

                Log.d(TAG, "比较指纹点: (" + fp.getPixelX() + ", " + fp.getPixelY() +
                        ") - 距离: " + distance + ", 楼层: " + fp.getFloor());

                // 使用容差匹配（20像素范围内视为匹配）
                if (distance < 20 && fp.getFloor() == result.getFloor()) {
                    foundExactMatch = true;
                    if (fp.getLabel() != null && !fp.getLabel().isEmpty()) {
                        displayText = "特殊点：" + fp.getLabel();
                    } else if (fp.getPath() != null && !fp.getPath().isEmpty()) {
                        displayText = "普通点：" + fp.getPath();
                    } else {
                        displayText = "未命名点位";
                    }
                    Log.d(TAG, "找到匹配指纹点: " + displayText);
                    break;
                }
            }

            // 如果没有精确匹配，尝试查找最近的点
            if (!foundExactMatch && !fingerprints.isEmpty()) {
                WifiFingerprint nearestFp = fingerprints.get(0);
                double minDistance = Double.MAX_VALUE;

                for (WifiFingerprint fp : fingerprints) {
                    double distance = Math.sqrt(Math.pow(fp.getPixelX() - result.getX(), 2) +
                            Math.pow(fp.getPixelY() - result.getY(), 2));
                    if (distance < minDistance) {
                        minDistance = distance;
                        nearestFp = fp;
                    }
                }

                displayText = "最近点位（距离" + (int)minDistance + "像素）";
                if (nearestFp.getLabel() != null && !nearestFp.getLabel().isEmpty()) {
                    displayText += "：" + nearestFp.getLabel();
                }
                Log.d(TAG, "使用最近指纹点，距离: " + minDistance);
            }

            // 在地图上绘制定位标记
            imageHandler.drawLocationMarker((float) result.getX(), (float) result.getY(), displayText);

            // 显示定位结果
            String resultText = String.format("📍 定位成功！\n楼层：%d\n坐标：(%.0f, %.0f)\n%s",
                    result.getFloor(),
                    result.getX(),
                    result.getY(),
                    displayText.isEmpty() ? "新位置（指纹库中无匹配标签）" : displayText);

            tvResult.setText(resultText);
            Toast.makeText(this, "定位完成", Toast.LENGTH_SHORT).show();

        } else {
            // 定位失败 - 提供详细错误信息
            Log.e(TAG, "定位失败，返回结果为null");

            String errorDetail = "❌ 定位失败\n\n可能原因：\n";

            // 检查当前WiFi扫描情况
            try {
                List<ScanResult> currentScan = wifiManager.getScanResults();
                if (currentScan == null || currentScan.isEmpty()) {
                    errorDetail += "• 当前未扫描到任何WiFi信号\n";
                } else {
                    errorDetail += "• 当前扫描到 " + currentScan.size() + " 个WiFi信号\n";

                    // 检查信号强度
                    int strongSignals = 0;
                    for (ScanResult sr : currentScan) {
                        if (sr.level > configManager.getWifiThreshold()) {
                            strongSignals++;
                        }
                    }
                    errorDetail += "• 其中 " + strongSignals + " 个信号强度达标\n";
                }
            } catch (SecurityException e) {
                errorDetail += "• 无法访问WiFi扫描结果（权限问题）\n";
            }

            errorDetail += "• 指纹库数据可能不足\n";
            errorDetail += "• 当前位置与指纹采集位置差异较大\n";
            errorDetail += "• WiFi信号环境发生变化\n";

            tvResult.setText(errorDetail);
            Toast.makeText(this, "定位失败，请查看详细原因", Toast.LENGTH_LONG).show();
        }
    }
    /**
     * 显示WiFi选择对话框（修复版本 - 放宽条件限制）
     */
    private void showWifiSelectDialog() {
        Log.d(TAG, "显示WiFi选择对话框");

        // 放宽条件：只要有扫描结果就可以显示，不管WiFi数量多少
        if (filteredWifis == null) {
            Toast.makeText(this, "请先完成WiFi扫描", Toast.LENGTH_SHORT).show();
            Log.w(TAG, "无法显示WiFi选择对话框：filteredWifis为null");
            return;
        }

        Log.d(TAG, "准备显示WiFi选择对话框，WiFi数量: " + filteredWifis.size());

        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        // 修改标题，提示最小要求但允许查看
        String title = "选择要保存的WiFi信号";
        if (filteredWifis.size() < MIN_SELECT_WIFI_COUNT) {
            title += "（至少需要" + MIN_SELECT_WIFI_COUNT + "个，当前只有" + filteredWifis.size() + "个）";
        } else {
            title += "（至少选择" + MIN_SELECT_WIFI_COUNT + "个）";
        }
        builder.setTitle(title);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 30, 50, 30);

        // 如果WiFi列表为空，显示提示信息
        if (filteredWifis.isEmpty()) {
            TextView emptyText = new TextView(this);
            emptyText.setText("未发现任何WiFi信号\n\n可能原因：\n• 位置权限未开启\n• WiFi功能未开启\n• 周围没有WiFi信号\n• 信号强度阈值设置过高");
            emptyText.setTextSize(16);
            emptyText.setTextColor(Color.RED);
            emptyText.setPadding(0, 20, 0, 20);
            emptyText.setGravity(Gravity.CENTER);
            layout.addView(emptyText);
        } else {
            CheckBox[] checkBoxes = new CheckBox[filteredWifis.size()];
            selectedWifis.clear();

            // 自动选中前6个强信号（但不超过总数量）
            int autoSelectCount = Math.min(6, filteredWifis.size());
            Log.d(TAG, "自动选择数量: " + autoSelectCount);

            for (int i = 0; i < filteredWifis.size(); i++) {
                FilteredWifi wifi = filteredWifis.get(i);
                checkBoxes[i] = new CheckBox(this);

                // 简化MAC地址显示（取后6位）
                String shortBssid = wifi.getBssid();
                if (shortBssid != null && shortBssid.length() > 12) {
                    shortBssid = shortBssid.substring(12);
                }

                String wifiText = String.format("%s\nMAC：%s，信号：%ddBm",
                        wifi.getSsid().isEmpty() ? "[隐藏SSID]" : wifi.getSsid(),
                        shortBssid,
                        wifi.getRssi());

                checkBoxes[i].setText(wifiText);
                checkBoxes[i].setTextSize(14);
                checkBoxes[i].setPadding(0, 10, 0, 10);
                layout.addView(checkBoxes[i]);

                // 自动选中强信号
                if (i < autoSelectCount) {
                    checkBoxes[i].setChecked(true);
                    selectedWifis.add(wifi);
                    Log.d(TAG, "自动选中WiFi: " + wifi.getSsid() + " (" + wifi.getRssi() + "dBm)");
                }

                int finalI = i;
                checkBoxes[i].setOnCheckedChangeListener((buttonView, isChecked) -> {
                    if (isChecked) {
                        if (!selectedWifis.contains(wifi)) {
                            selectedWifis.add(wifi);
                            Log.d(TAG, "手动选中WiFi: " + wifi.getSsid());
                        }
                    } else {
                        selectedWifis.remove(wifi);
                        Log.d(TAG, "取消选中WiFi: " + wifi.getSsid());
                    }
                });
            }
        }

        builder.setView(layout);

        builder.setPositiveButton("确定保存", (dialog, which) -> {
            Log.d(TAG, "用户确认保存，选中WiFi数量: " + selectedWifis.size());

            if (selectedWifis.size() >= MIN_SELECT_WIFI_COUNT) {
                saveFingerprintToPixel(selectedWifis);
            } else {
                String message = "请至少选择" + MIN_SELECT_WIFI_COUNT + "个WiFi（当前选中" + selectedWifis.size() + "个）";
                Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                Log.w(TAG, "选中WiFi数量不足: " + selectedWifis.size());
            }
        });

        builder.setNegativeButton("取消", (dialog, which) -> {
            Log.d(TAG, "用户取消WiFi选择");
            dialog.dismiss();
        });

        // 只有在有WiFi时才显示全选按钮
        if (!filteredWifis.isEmpty()) {
            builder.setNeutralButton("全选", (dialog, which) -> {
                Log.d(TAG, "用户点击全选");
                for (int i = 0; i < filteredWifis.size(); i++) {
                    CheckBox checkBox = (CheckBox) layout.getChildAt(i);
                    if (checkBox != null) {
                        checkBox.setChecked(true);
                    }
                }
            });
        }

        AlertDialog dialog = builder.create();
        dialog.show();

        Log.d(TAG, "WiFi选择对话框显示完成");
    }

    /**
     * 更新选中WiFi数量显示
     */
    private void updateSelectedWifiCount(AlertDialog.Builder builder, int selectedCount) {
        // 这里我们无法直接更新标题，但可以在日志中记录
        Log.d(TAG, "当前选中WiFi数量: " + selectedCount + "/" + MIN_SELECT_WIFI_COUNT + " (需要)");
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
     * 重新扫描WiFi并更新指纹（修复版本）
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
        int scanCount = configManager.getScanCount();
        int intervalMs = configManager.getScanInterval();
        tvResult.setText(String.format("开始重新扫描WiFi（共%d次，间隔%d秒）...\n",
                scanCount, intervalMs / 1000));
        scanButton.setEnabled(false);

        // 执行多次扫描 - 修复：使用MainActivity.this引用外部类实例
        wifiScanner.performMultipleScans(scanCount, intervalMs, new WifiScanner.ScanCallback() {

            @Override
            public void onMultipleScansComplete(List<List<ScanResult>> allScanResults) {
                Log.d(TAG, "重新扫描完成回调，扫描次数: " + (allScanResults != null ? allScanResults.size() : 0));

                // 修复：使用MainActivity.this引用外部类成员变量
                MainActivity.this.multipleScans = allScanResults;

                // 处理扫描结果
                if (allScanResults != null && !allScanResults.isEmpty()) {
                    MainActivity.this.filteredWifis = WifiDataProcessor.processMultipleScansWithAverage(allScanResults, configManager);
                    Log.d(TAG, "WiFi处理完成，找到WiFi数量: " + (filteredWifis != null ? filteredWifis.size() : 0));
                } else {
                    MainActivity.this.filteredWifis = new ArrayList<>();
                    Log.w(TAG, "扫描结果为空");
                }

                runOnUiThread(() -> {
                    // 更新当前编辑指纹的WiFi数据
                    if (currentEditingFingerprint != null && filteredWifis != null) {
                        currentEditingFingerprint.setFilteredWifis(new ArrayList<>(filteredWifis));
                        fingerprintManager.updateFingerprint(currentEditingFingerprint);
                        tvResult.append("重新扫描完成，已更新指纹数据\n");
                        Log.d(TAG, "指纹数据更新完成");
                    }

                    // 更新UI状态
                    if (filteredWifis != null && !filteredWifis.isEmpty()) {
                        tvResult.append("✅ 重新扫描完成！发现 " + filteredWifis.size() + " 个WiFi信号\n");
                    } else {
                        tvResult.append("❌ 重新扫描完成，但未发现可用WiFi信号\n");
                    }

                    isScanning = false;
                    scanButton.setEnabled(true);
                    Log.d(TAG, "重新扫描状态结束，isScanning: " + isScanning);
                });
            }

            @Override
            public void onPermissionDenied() {
                runOnUiThread(() -> {
                    tvResult.append("重新扫描失败：权限不足\n");
                    isScanning = false;
                    scanButton.setEnabled(true);
                    Log.e(TAG, "重新扫描权限被拒绝");
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
     * 图片触摸监听（支持缩放、移动、点击+添加红点）
     */
    private void setImageTouchListener() {
        imageView.setOnTouchListener((v, event) -> {
            // 1. 先处理图片缩放/拖动（ImageHandler的触摸逻辑，保留原有功能）
            boolean handled = imageHandler.handleTouchEvent(event);

            // 2. 只在“单点抬起”时显示单次红点
            if (event.getAction() == MotionEvent.ACTION_UP && event.getPointerCount() == 1) {
                // 可选：点击新位置前清除旧红点（确保立即覆盖）
                redPointPhotoView.clearRedPoint();
                // 调用RedPointPhotoView的方法，添加新红点（自动覆盖旧点）
                redPointPhotoView.addRedPoint(event.getX(), event.getY());

                // 3. 保留原有坐标显示逻辑（Toast+文本框更新）
                float[] coords = coordinateManager.getCoordinatesFromTouch(event.getX(), event.getY());
                if (coords != null) {
                    Toast.makeText(MainActivity.this,
                            "坐标: (" + (int)coords[0] + ", " + (int)coords[1] + ")",
                            Toast.LENGTH_SHORT).show();
                    etPixelX.setText(String.valueOf((int)coords[0]));
                    etPixelY.setText(String.valueOf((int)coords[1]));

                    // 检查是否点击已有指纹标记（原有逻辑保留）
                    List<WifiFingerprint> fingerprints = fingerprintManager.getAllFingerprints();
                    WifiFingerprint clickedFingerprint = coordinateManager.getClickedFingerprint(
                            event.getX(), event.getY(), fingerprints);
                    if (clickedFingerprint != null) {
                        showFingerprintEditDialog(clickedFingerprint);
                    }
                }
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

    // 保存当前指纹（按钮绑定使用）- 增强版本
    private void saveCurrentFingerprint() {
        Log.d(TAG, "开始保存当前指纹");

        if (filteredWifis == null || filteredWifis.isEmpty()) {
            Toast.makeText(this, "请先完成WiFi扫描", Toast.LENGTH_SHORT).show();
            Log.w(TAG, "保存失败：没有可用的WiFi数据");
            return;
        }

        // 检查坐标是否已输入
        if (etPixelX.getText().toString().isEmpty() || etPixelY.getText().toString().isEmpty()) {
            Toast.makeText(this, "请先点击图片获取坐标", Toast.LENGTH_SHORT).show();
            Log.w(TAG, "保存失败：坐标未输入");
            return;
        }

        // 检查点位类型输入
        String label = etLabel.getText().toString().trim();
        String path = etPath.getText().toString().trim();
        if ((label.isEmpty() && path.isEmpty()) || (!label.isEmpty() && !path.isEmpty())) {
            Toast.makeText(this, "请仅填写一项：特殊点名称或普通点距离", Toast.LENGTH_SHORT).show();
            Log.w(TAG, "保存失败：点位类型输入错误");
            return;
        }

        Log.d(TAG, "开始保存指纹，使用所有" + filteredWifis.size() + "个WiFi信号");

        // 使用所有过滤后的WiFi信号
        saveFingerprintToPixel(filteredWifis);
    }
}