package com.example.mywifiscanner;
import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Build;
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
    private int scanIntervalMs = 1000; // 默认扫描间隔1秒

    private final BroadcastReceiver scanReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            boolean success = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false);
            if (success && remainingScans > 0) {
                // 1. 再次检查权限（防止扫描过程中权限被撤销）
                if (!hasLocationPermission()) {
                    handlePermissionDenied();
                    return;
                }

                try {
                    // 2. 获取扫描结果时捕获SecurityException
                    List<ScanResult> results = wifiManager.getScanResults();
                    multipleScanResults.add(new ArrayList<>(results));
                    remainingScans--;
                    Log.d(TAG, "扫描完成，剩余次数: " + remainingScans);

                    if (remainingScans > 0) {
                        handler.postDelayed(() -> {
                            if (!hasLocationPermission()) {
                                handlePermissionDenied();
                                return;
                            }
                            // 启动下一次扫描前检查权限并捕获异常
                            try {
                                boolean scanStarted = wifiManager.startScan();
                                if (!scanStarted) {
                                    Log.e(TAG, "扫描启动失败（可能系统限制）");
                                    handlePermissionDenied();
                                }
                            } catch (SecurityException e) {
                                Log.e(TAG, "启动扫描时权限被拒绝: " + e.getMessage());
                                handlePermissionDenied();
                            }
                        }, scanIntervalMs);
                    } else {
                        Log.d(TAG, "所有扫描完成，共" + multipleScanResults.size() + "次结果");
                        if (currentCallback != null) {
                            currentCallback.onMultipleScansComplete(multipleScanResults);
                        }
                        unregisterReceiver();
                    }
                } catch (SecurityException e) {
                    Log.e(TAG, "获取扫描结果时权限被拒绝: " + e.getMessage());
                    handlePermissionDenied();
                }
            }
        }
    };

    public WifiScanner(Context context, WifiManager wifiManager) {
        this.context = context;
        this.wifiManager = wifiManager;
    }

    // 检查位置权限（WiFi扫描必须）
    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(context,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    // 检查WiFi状态修改权限（启用/禁用WiFi）
    private boolean hasChangeWifiStatePermission() {
        return ContextCompat.checkSelfPermission(context,
                Manifest.permission.CHANGE_WIFI_STATE) == PackageManager.PERMISSION_GRANTED;
    }

    private void handlePermissionDenied() {
        Log.e(TAG, "权限被拒绝，无法继续扫描");
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

    public List<List<ScanResult>> performMultipleScans(int scanCount) {
        List<List<ScanResult>> results = new ArrayList<>();
        // 1. 先检查位置权限
        if (!hasLocationPermission()) {
            Log.e(TAG, "位置权限缺失，无法扫描WiFi");
            return results;
        }

        // 2. 启用WiFi（处理权限和API弃用）
        enableWifi();

        // 3. 循环扫描，每次都检查权限并捕获异常
        for (int i = 0; i < scanCount; i++) {
            // 再次检查权限（防止中途权限被撤销）
            if (!hasLocationPermission()) {
                Log.e(TAG, "第" + (i+1) + "次扫描：位置权限缺失");
                break;
            }

            try {
                // 启动扫描（可能抛出SecurityException）
                boolean success = wifiManager.startScan();
                if (!success) {
                    Log.e(TAG, "第" + (i+1) + "次扫描启动失败（系统限制）");
                    continue;
                }
            } catch (SecurityException e) {
                Log.e(TAG, "第" + (i+1) + "次扫描：启动权限被拒绝: " + e.getMessage());
                break;
            }

            // 获取扫描结果（可能抛出SecurityException）
            try {
                List<ScanResult> scanResults = wifiManager.getScanResults();
                if (scanResults != null && !scanResults.isEmpty()) {
                    results.add(new ArrayList<>(scanResults));
                    Log.d(TAG, "第" + (i+1) + "次扫描完成，获取" + scanResults.size() + "个结果");
                } else {
                    Log.w(TAG, "第" + (i+1) + "次扫描：无有效结果");
                }
            } catch (SecurityException e) {
                Log.e(TAG, "第" + (i+1) + "次扫描：获取结果权限被拒绝: " + e.getMessage());
                break;
            }
        }
        return results;
    }

    private void enableWifi() {
        // API 29+（Android 10）后setWifiEnabled已弃用，且可能无权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Log.w(TAG, "Android 10+ 不支持通过代码启用WiFi，请手动开启");
            return;
        }

        // 检查是否有修改WiFi状态的权限
        if (!hasChangeWifiStatePermission()) {
            Log.e(TAG, "无修改WiFi状态权限，无法自动启用WiFi");
            return;
        }

        // 启用WiFi（可能抛出SecurityException）
        if (!wifiManager.isWifiEnabled()) {
            try {
                boolean wifiEnabled = wifiManager.setWifiEnabled(true);
                Log.d(TAG, "WiFi " + (wifiEnabled ? "已启用" : "启用失败（系统限制）"));
            } catch (SecurityException e) {
                Log.e(TAG, "启用WiFi时权限被拒绝: " + e.getMessage());
            }
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