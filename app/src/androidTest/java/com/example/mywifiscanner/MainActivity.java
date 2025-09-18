package com.example.mywifiscanner; // 请替换为你的实际包名

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

public class MainActivity extends AppCompatActivity {

    // 控件和WiFi管理器
    private Button scanButton;
    private TextView wifiText;
    private WifiManager wifiManager;

    // 权限请求码（自定义，确保唯一）
    private static final int REQUEST_CODE_LOCATION_PERMISSIONS = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 初始化控件
        scanButton = findViewById(R.id.scanButton);
        wifiText = findViewById(R.id.wifiText);

        // 获取系统WiFi服务
        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);

        // 按钮点击事件：检查权限并扫描WiFi
        scanButton.setOnClickListener(v -> checkPermissionsAndScan());
    }

    // 检查权限并决定是否扫描
    private void checkPermissionsAndScan() {
        // 同时检查精确位置和粗略位置权限
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED
                || ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {

            // 权限未授予，申请双权限
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{
                            android.Manifest.permission.ACCESS_FINE_LOCATION,
                            android.Manifest.permission.ACCESS_COARSE_LOCATION
                    },
                    REQUEST_CODE_LOCATION_PERMISSIONS
            );
        } else {
            // 权限已授予，直接扫描
            scanWifi();
        }
    }

    // 处理权限申请结果
    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_CODE_LOCATION_PERMISSIONS) {
            boolean allPermissionsGranted = true;

            // 检查所有申请的权限是否都被授予
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allPermissionsGranted = false;
                    break;
                }
            }

            if (allPermissionsGranted) {
                // 所有权限都通过，开始扫描
                scanWifi();
            } else {
                // 有任何权限被拒绝，提示用户
                Toast.makeText(
                        this,
                        "需要位置权限才能扫描WiFi网络",
                        Toast.LENGTH_SHORT
                ).show();
            }
        }
    }

    // 核心方法：扫描WiFi并显示结果
    private void scanWifi() {
        // 提示用户正在扫描
        Toast.makeText(this, "正在扫描WiFi...", Toast.LENGTH_SHORT).show();

        // 确保WiFi已开启（如果未开启，尝试开启）
        if (!wifiManager.isWifiEnabled()) {
            wifiManager.setWifiEnabled(true);
            Toast.makeText(this, "WiFi已自动开启", Toast.LENGTH_SHORT).show();
        }

        // 开始扫描（返回是否成功）
        boolean isScanSuccess = wifiManager.startScan();

        if (isScanSuccess) {
            // 扫描成功，获取结果列表
            List<ScanResult> wifiList = wifiManager.getScanResults();

            // 处理扫描结果
            if (wifiList.isEmpty()) {
                wifiText.setText("未发现可用WiFi网络");
            } else {
                // 拼接WiFi信息
                StringBuilder sb = new StringBuilder();
                sb.append("发现 ").append(wifiList.size()).append(" 个WiFi网络：\n\n");

                for (ScanResult wifi : wifiList) {
                    sb.append("名称：").append(wifi.SSID).append("\n");
                    sb.append("MAC地址：").append(wifi.BSSID).append("\n");
                    sb.append("信号强度：").append(wifi.level).append(" dBm\n");
                    sb.append("加密方式：").append(getSecurityType(wifi)).append("\n");
                    sb.append("-------------------------\n");
                }

                // 显示结果
                wifiText.setText(sb.toString());
            }
        } else {
            // 扫描失败（可能是系统限制或硬件问题）
            Toast.makeText(this, "扫描失败，请重试", Toast.LENGTH_SHORT).show();
            wifiText.setText("扫描失败，请检查权限和WiFi状态");
        }
    }

    // 辅助方法：解析WiFi加密类型
    private String getSecurityType(ScanResult result) {
        if (result.capabilities.contains("WPA3")) {
            return "WPA3";
        } else if (result.capabilities.contains("WPA2")) {
            return "WPA2";
        } else if (result.capabilities.contains("WPA")) {
            return "WPA";
        } else if (result.capabilities.contains("WEP")) {
            return "WEP";
        } else {
            return "开放网络（无密码）";
        }
    }
}
