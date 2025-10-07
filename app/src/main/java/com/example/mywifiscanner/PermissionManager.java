package com.example.mywifiscanner;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

/**
 * 权限管理工具类，处理权限检查与请求
 */
public class PermissionManager {
    // 权限请求码
    public static final int REQUEST_READ_STORAGE = 101;
    public static final int REQUEST_LOCATION_PERMISSION = 102;
    private PermissionCallback permissionCallback;
    private PermissionCallback storagePermissionCallback;
    private PermissionCallback locationPermissionCallback;

    // 权限回调接口
    public interface PermissionCallback {
        void onPermissionGranted();
        void onPermissionDenied();
    }

    private Activity activity;

    public PermissionManager(Activity activity) {
        this.activity = activity;
    }

    /**
     * 检查存储权限（适配Android 13+）
     */
    public boolean checkStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(activity,
                    android.Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED;
        } else {
            return ContextCompat.checkSelfPermission(activity,
                    android.Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        }
    }

    /**
     * 获取存储权限数组（适配版本）
     */
    public String[] getStoragePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return new String[]{android.Manifest.permission.READ_MEDIA_IMAGES};
        } else {
            return new String[]{android.Manifest.permission.READ_EXTERNAL_STORAGE};
        }
    }

    /**
     * 检查位置权限
     */
    public boolean checkLocationPermission() {
        // 自用场景下，放宽权限检查（例如只检查粗略位置，或直接返回true）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // 允许使用粗略位置（无需精确定位权限）
            return ContextCompat.checkSelfPermission(activity,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        } else {
            // 旧版本直接返回true（跳过检查，仅测试用）
            return true;
        }
    }

    /**
     * 请求存储权限（新增方法）
     */
    public void requestStoragePermission(PermissionCallback callback) {
        if (checkStoragePermission()) {
            callback.onPermissionGranted();
        } else {
            this.storagePermissionCallback = callback;
            ActivityCompat.requestPermissions(activity, getStoragePermissions(), REQUEST_READ_STORAGE);
        }
    }

    /**
     * 请求位置权限（新增方法）
     */
    public void requestLocationPermission(PermissionCallback callback) {
        if (checkLocationPermission()) {
            callback.onPermissionGranted();
        } else {
            this.locationPermissionCallback = callback;
            // 改为请求粗略位置权限
            ActivityCompat.requestPermissions(activity,
                    new String[]{android.Manifest.permission.ACCESS_COARSE_LOCATION},
                    REQUEST_LOCATION_PERMISSION);
        }
    }

    /**
     * 处理权限请求结果（新增方法）
     */
    public void handlePermissionResult(int requestCode, int[] grantResults) {
        if (requestCode == REQUEST_READ_STORAGE && storagePermissionCallback != null) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                storagePermissionCallback.onPermissionGranted();
            } else {
                storagePermissionCallback.onPermissionDenied();
            }
            storagePermissionCallback = null;
        } else if (requestCode == REQUEST_LOCATION_PERMISSION && locationPermissionCallback != null) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                locationPermissionCallback.onPermissionGranted();
            } else {
                locationPermissionCallback.onPermissionDenied();
            }
            locationPermissionCallback = null;
        }
    }
}