// PermissionManager.java
package com.example.mywifiscanner;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class PermissionManager {
    public static final int REQUEST_READ_STORAGE = 101;
    public static final int REQUEST_LOCATION_PERMISSION = 102;
    private PermissionCallback permissionCallback;
    private PermissionCallback storagePermissionCallback;
    private PermissionCallback locationPermissionCallback;
    public interface PermissionCallback {
        void onPermissionGranted();
        void onPermissionDenied();
    }
    private Activity activity;
    public PermissionManager(Activity activity) {
        this.activity = activity;
    }
    public boolean checkStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(activity,
                    android.Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED;
        } else {
            return ContextCompat.checkSelfPermission(activity,
                    android.Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        }
    }
    public String[] getStoragePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return new String[]{android.Manifest.permission.READ_MEDIA_IMAGES};
        } else {
            return new String[]{android.Manifest.permission.READ_EXTERNAL_STORAGE};
        }
    }
    public boolean checkLocationPermission() {
        return ContextCompat.checkSelfPermission(activity,
                android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }
    public void requestStoragePermission(PermissionCallback callback) {
        if (checkStoragePermission()) {
            callback.onPermissionGranted();
        } else {
            this.storagePermissionCallback = callback;
            ActivityCompat.requestPermissions(activity, getStoragePermissions(), REQUEST_READ_STORAGE);
        }
    }
    public void requestLocationPermission(PermissionCallback callback) {
        if (checkLocationPermission()) {
            callback.onPermissionGranted();
        } else {
            this.locationPermissionCallback = callback;
            ActivityCompat.requestPermissions(activity,
                    new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION},
                    REQUEST_LOCATION_PERMISSION);
        }
    }
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