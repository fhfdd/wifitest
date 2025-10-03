package com.example.mywifiscanner;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.util.Log;
import androidx.core.content.ContextCompat;
import java.util.ArrayList;
import java.util.List;

public class WifiScanner {
    private static final String TAG = "WifiScanner";
    private final Context context;
    private final WifiManager wifiManager;
    private final Handler handler = new Handler();
    private int remainingScans;
    private List<List<ScanResult>> multipleScanResults;
    private ScanCallback currentCallback;
    private boolean isReceiverRegistered = false;
    private int scanIntervalMs; // 存储扫描间隔

    private final BroadcastReceiver scanReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            boolean success = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false);
            if (success && remainingScans > 0) {
                if (!hasLocationPermission()) {
                    handlePermissionDenied();
                    return;
                }

                try {
                    List<ScanResult> results = wifiManager.getScanResults();
                    multipleScanResults.add(new ArrayList<>(results));
                    remainingScans--;
                    Log.d(TAG, "扫描完成，剩余次数: " + remainingScans);

                    if (remainingScans > 0) {
                        // 使用传入的间隔时间，而不是固定值
                        handler.postDelayed(() -> {
                            if (!hasLocationPermission()) {
                                handlePermissionDenied();
                                return;
                            }
                            try {
                                boolean scanStarted = wifiManager.startScan();
                                if (!scanStarted) {
                                    Log.e(TAG, "扫描启动失败");
                                    handlePermissionDenied();
                                }
                            } catch (SecurityException e) {
                                Log.e(TAG, "权限异常: " + e.getMessage());
                                handlePermissionDenied();
                            }
                        }, scanIntervalMs); // 使用存储的间隔时间
                    } else {
                        Log.d(TAG, "所有扫描完成，共" + multipleScanResults.size() + "次结果");
                        if (currentCallback != null) {
                            currentCallback.onMultipleScansComplete(multipleScanResults);
                        }
                        unregisterReceiver();
                    }
                } catch (SecurityException e) {
                    Log.e(TAG, "获取扫描结果权限异常: " + e.getMessage());
                    handlePermissionDenied();
                }
            }
        }
    };

    public WifiScanner(Context context, WifiManager wifiManager) {
        this.context = context;
        this.wifiManager = wifiManager;
    }

    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(context,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void handlePermissionDenied() {
        Log.e(TAG, "权限被拒绝");
        if (currentCallback != null) {
            currentCallback.onPermissionDenied();
        }
        unregisterReceiver();
    }

    private void registerReceiver() {
        if (!isReceiverRegistered) {
            try {
                context.registerReceiver(scanReceiver,
                        new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
                isReceiverRegistered = true;
                Log.d(TAG, "广播接收器注册成功");
            } catch (Exception e) {
                Log.e(TAG, "注册广播接收器失败: " + e.getMessage());
            }
        }
    }

    private void unregisterReceiver() {
        if (isReceiverRegistered) {
            try {
                context.unregisterReceiver(scanReceiver);
                isReceiverRegistered = false;
                Log.d(TAG, "广播接收器已注销");
            } catch (Exception e) {
                Log.w(TAG, "注销接收器失败：" + e.getMessage());
            }
        }
    }

    public void performMultipleScans(int scanCount, int intervalMs, ScanCallback callback) {
        if (scanCount <= 0 || intervalMs < 1000) {
            Log.e(TAG, "参数无效: scanCount=" + scanCount + ", intervalMs=" + intervalMs);
            return;
        }

        // 存储扫描间隔供后续使用
        this.scanIntervalMs = intervalMs;

        // 提前检查权限
        if (!hasLocationPermission()) {
            Log.e(TAG, "没有位置权限");
            if (callback != null) {
                callback.onPermissionDenied();
            }
            return;
        }

        this.remainingScans = scanCount;
        this.currentCallback = callback;
        this.multipleScanResults = new ArrayList<>();

        enableWifi();
        registerReceiver();

        // 移除延迟，直接开始扫描
        try {
            boolean scanStarted = wifiManager.startScan();
            if (scanStarted) {
                Log.d(TAG, "开始多次扫描（共" + scanCount + "次，间隔" + intervalMs + "ms）");
            } else {
                Log.e(TAG, "扫描启动失败");
                if (currentCallback != null) {
                    currentCallback.onPermissionDenied();
                }
                unregisterReceiver();
            }
        } catch (SecurityException e) {
            Log.e(TAG, "启动扫描权限异常: " + e.getMessage());
            handlePermissionDenied();
        }
    }

    private void enableWifi() {
        if (!wifiManager.isWifiEnabled()) {
            boolean wifiEnabled = wifiManager.setWifiEnabled(true);
            Log.d(TAG, "WiFi " + (wifiEnabled ? "已启用" : "启用失败"));
        }
    }

    public void destroy() {
        handler.removeCallbacksAndMessages(null);
        unregisterReceiver();
        Log.d(TAG, "WifiScanner已销毁");
    }

    public interface ScanCallback {
        void onMultipleScansComplete(List<List<ScanResult>> allScanResults);
        void onPermissionDenied();
    }
}