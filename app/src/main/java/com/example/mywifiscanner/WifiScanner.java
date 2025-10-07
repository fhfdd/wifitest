package com.example.mywifiscanner;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.util.Log;
import androidx.core.content.ContextCompat;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class WifiScanner {
    private static final String TAG = "WifiScanner";
    private final Context context;
    private final WifiManager wifiManager;
    private int scanIntervalMs; // 扫描间隔（从配置获取）

    public WifiScanner(Context context, WifiManager wifiManager, int scanIntervalMs) {
        this.context = context;
        this.wifiManager = wifiManager;
        this.scanIntervalMs = scanIntervalMs; // 从配置传入间隔
    }

    // 检查位置权限
    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(context,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    // 检查WiFi状态修改权限
    private boolean hasChangeWifiStatePermission() {
        return ContextCompat.checkSelfPermission(context,
                Manifest.permission.CHANGE_WIFI_STATE) == PackageManager.PERMISSION_GRANTED;
    }

    private void enableWifi() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Log.w(TAG, "Android 10+ 不支持通过代码启用WiFi，请手动开启");
            return;
        }

        if (!hasChangeWifiStatePermission()) {
            Log.e(TAG, "无修改WiFi状态权限，无法自动启用WiFi");
            return;
        }

        if (!wifiManager.isWifiEnabled()) {
            try {
                boolean wifiEnabled = wifiManager.setWifiEnabled(true);
                Log.d(TAG, "WiFi " + (wifiEnabled ? "已启用" : "启用失败（系统限制）"));
                // 等待WiFi启动（最多等5秒）
                int waitCount = 0;
                while (!wifiManager.isWifiEnabled() && waitCount < 5) {
                    Thread.sleep(1000);
                    waitCount++;
                }
            } catch (Exception e) {
                Log.e(TAG, "启用WiFi失败: " + e.getMessage());
            }
        }
    }

    // 同步执行多次扫描（阻塞当前线程）
    public List<List<ScanResult>> performMultipleScans(int scanCount) {
        List<List<ScanResult>> results = new ArrayList<>();

        // 检查权限
        if (!hasLocationPermission()) {
            Log.e(TAG, "位置权限缺失，无法扫描WiFi");
            return results;
        }

        // 启用WiFi
        enableWifi();

        // 循环执行指定次数扫描
        for (int i = 0; i < scanCount; i++) {
            Log.d(TAG, "开始第" + (i + 1) + "次扫描");

            // 再次检查权限
            if (!hasLocationPermission()) {
                Log.e(TAG, "第" + (i + 1) + "次扫描：位置权限缺失，终止扫描");
                break;
            }

            // 启动扫描
            boolean scanStarted;
            try {
                scanStarted = wifiManager.startScan();
            } catch (SecurityException e) {
                Log.e(TAG, "第" + (i + 1) + "次扫描：启动失败（权限被拒）");
                break;
            }

            if (!scanStarted) {
                Log.e(TAG, "第" + (i + 1) + "次扫描：系统拒绝启动扫描（可能频率限制）");
                // 强制等待间隔后继续下一次（避免密集调用）
                try {
                    Thread.sleep(scanIntervalMs);
                } catch (InterruptedException e) {
                    break;
                }
                continue;
            }

            // 同步等待扫描结果（最多等3秒，每100ms查一次）
            List<ScanResult> scanResults = null;
            int waitMs = 0;
            while (waitMs < 3000) {
                try {
                    scanResults = wifiManager.getScanResults();
                    if (scanResults != null && !scanResults.isEmpty()) {
                        break; // 拿到结果就退出等待
                    }
                } catch (SecurityException e) {
                    Log.e(TAG, "第" + (i + 1) + "次扫描：获取结果权限被拒");
                    break;
                }

                // 没拿到结果，继续等
                try {
                    Thread.sleep(100);
                    waitMs += 100;
                } catch (InterruptedException e) {
                    break;
                }
            }

            // 处理本次结果
            if (scanResults != null && !scanResults.isEmpty()) {
                results.add(new ArrayList<>(scanResults));
                Log.d(TAG, "第" + (i + 1) + "次扫描成功，获取" + scanResults.size() + "个结果");
            } else {
                Log.w(TAG, "第" + (i + 1) + "次扫描：超时未获取到有效结果");
            }

            // 扫描间隔（最后一次不需要等）
            if (i != scanCount - 1) {
                try {
                    Log.d(TAG, "等待" + scanIntervalMs + "ms后进行下一次扫描");
                    Thread.sleep(scanIntervalMs);
                } catch (InterruptedException e) {
                    Log.w(TAG, "扫描间隔被中断");
                    break;
                }
            }
        }

        Log.d(TAG, "所有扫描结束，共成功" + results.size() + "次");
        return results;
    }

    public void destroy() {
        // 同步方式无需销毁资源
        Log.d(TAG, "WifiScanner已销毁");
    }
}