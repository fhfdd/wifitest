package com.example.mywifiscanner;

import android.content.Context;
import android.content.Intent;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;
import androidx.core.content.FileProvider;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class MapFileModule {
    private static final String TAG = "MapFileModule";
    private static final String FINGERPRINT_DIR = "WiFi_Fingerprints";
    private final Context context;
    private final Gson gson = new Gson();

    public MapFileModule(Context context) {
        this.context = context;
        ensureDirExists();
    }

    private File getFingerprintDirectory() {
        // 获取公共下载目录
        File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        if (downloadDir == null) {
            Log.e(TAG, "公共下载目录不可用");
            return null;
        }
        // 创建WiFi_Fingerprints子文件夹
        File fingerprintDir = new File(downloadDir, FINGERPRINT_DIR);
        Log.d(TAG, "指纹库目录：" + fingerprintDir.getAbsolutePath());
        return fingerprintDir;
    }

    /**
     * 打开导出的指纹库文件所在目录（WiFi_Fingerprints）
     */
    public void openExportedDirectory() {
        File dir = getFingerprintDirectory();
        if (dir == null || !dir.exists()) {
            Log.e(TAG, "目录不存在或不可用，无法打开");
            Toast.makeText(context, "文件目录不存在", Toast.LENGTH_SHORT).show();
            return;
        }

        // 创建打开目录的Intent
        Intent intent = new Intent(Intent.ACTION_VIEW);
        Uri dirUri;

        // 适配Android 10+的分区存储
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // 公共下载目录下的子文件夹，直接使用Uri.fromFile（公共目录无需FileProvider）
            dirUri = Uri.fromFile(dir);
            intent.setDataAndType(dirUri, "resource/folder"); // 指定为文件夹类型
        } else {
            // 低版本直接使用文件Uri
            dirUri = Uri.fromFile(dir);
            intent.setDataAndType(dirUri, "file/*");
        }

        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); // 非Activity上下文需添加此标志
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        try {
            // 启动文件管理器打开目录
            context.startActivity(intent);
            Log.d(TAG, "已打开目录：" + dir.getAbsolutePath());
        } catch (Exception e) {
            // 处理无应用可打开目录的情况
            Log.e(TAG, "无法打开目录，可能没有文件管理器应用：" + e.getMessage());
            Toast.makeText(context, "无法打开目录，请手动前往下载文件夹中的WiFi_Fingerprints查看", Toast.LENGTH_LONG).show();
        }
    }
    private void ensureDirExists() {
        File dir = getFingerprintDirectory();
        if (dir == null) {
            Toast.makeText(context, "存储目录不可用，无法操作文件", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!dir.exists()) {
            boolean created = dir.mkdirs();
            if (created) {
                Log.d(TAG, "指纹库目录创建成功：" + dir.getAbsolutePath());
            } else {
                Log.e(TAG, "指纹库目录创建失败");
                Toast.makeText(context, "文件目录创建失败，无法保存指纹库", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // 【关键修改】解析导入的指纹文件时，确保filteredWifis不为null
    public List<WifiFingerprint> parseImportedFingerprints(InputStream inputStream) {
        try {
            StringBuilder jsonBuilder = new StringBuilder();
            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                jsonBuilder.append(new String(buffer, 0, bytesRead, "UTF-8"));
            }

            String json = jsonBuilder.toString().trim();
            Log.d(TAG, "解析JSON内容，长度: " + json.length());

            if (json.isEmpty()) {
                Log.e(TAG, "导入的文件为空");
                return new ArrayList<>(); // 空文件返回空列表
            }

            Type type = new TypeToken<List<WifiFingerprint>>(){}.getType();
            List<WifiFingerprint> fingerprints = gson.fromJson(json, type);

            if (fingerprints == null) {
                Log.e(TAG, "Gson解析返回null，返回空列表");
                return new ArrayList<>();
            }

            // 强制初始化每个指纹的filteredWifis（核心修复）
            for (WifiFingerprint fp : fingerprints) {
                if (fp.getFilteredWifis() == null) {
                    fp.setFilteredWifis(new ArrayList<>());
                    Log.w(TAG, "修复空WiFi列表：指纹数据不完整");
                }
            }

            Log.d(TAG, "解析导入的指纹库成功，共" + fingerprints.size() + "条指纹");
            return fingerprints;

        } catch (IOException e) {
            Log.e(TAG, "读取文件流失败：" + e.getMessage());
            return new ArrayList<>();
        } catch (JsonSyntaxException e) {
            Log.e(TAG, "JSON格式错误：" + e.getMessage());
            return new ArrayList<>();
        } catch (Exception e) {
            Log.e(TAG, "解析未知错误：" + e.getMessage());
            return new ArrayList<>();
        }
    }

    public boolean saveFingerprints(String fileName, List<WifiFingerprint> fingerprints) {
        if (fingerprints == null || fileName == null || fileName.isEmpty()) {
            Log.e(TAG, "保存失败：参数无效");
            return false;
        }

        try {
            String json = gson.toJson(fingerprints);
            File dir = getFingerprintDirectory();
            if (dir == null) {
                Log.e(TAG, "保存失败：目录不可用");
                return false;
            }
            if (!dir.exists() && !dir.mkdirs()) {
                Log.e(TAG, "目录创建失败");
                return false;
            }

            File file = new File(dir, fileName);
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(json.getBytes("UTF-8"));
                fos.flush();
                Log.d(TAG, "保存成功：" + file.getAbsolutePath());
                Toast.makeText(context, "文件已保存至：" + file.getAbsolutePath(), Toast.LENGTH_LONG).show();

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    MediaScannerConnection.scanFile(context, new String[]{file.getAbsolutePath()}, null, null);
                }
                return true;
            }
        } catch (IOException e) {
            Log.e(TAG, "保存失败：" + e.getMessage());
            return false;
        }
    }

    public List<WifiFingerprint> loadFingerprints(String fileName) {
        try {
            File file = new File(getFingerprintDirectory(), fileName);
            if (!file.exists()) {
                Log.e(TAG, "文件不存在：" + fileName);
                return new ArrayList<>();
            }

            try (FileInputStream fis = new FileInputStream(file)) {
                StringBuilder jsonBuilder = new StringBuilder();
                byte[] buffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    jsonBuilder.append(new String(buffer, 0, bytesRead, "UTF-8"));
                }

                String json = jsonBuilder.toString().trim();
                if (json.isEmpty()) {
                    Log.w(TAG, "文件内容为空");
                    return new ArrayList<>();
                }

                Type type = new TypeToken<List<WifiFingerprint>>(){}.getType();
                List<WifiFingerprint> fingerprints = gson.fromJson(json, type);

                // 【补充修复】加载时同样检查并初始化空列表
                if (fingerprints != null) {
                    for (WifiFingerprint fp : fingerprints) {
                        if (fp.getFilteredWifis() == null) {
                            fp.setFilteredWifis(new ArrayList<>());
                        }
                    }
                }

                return fingerprints != null ? fingerprints : new ArrayList<>();
            }
        } catch (Exception e) {
            Log.e(TAG, "加载失败：" + e.getMessage());
            return new ArrayList<>();
        }
    }

    public List<String> getAllFingerprintFileNames() {
        List<String> fileNames = new ArrayList<>();
        File dir = getFingerprintDirectory();
        if (dir == null || !dir.exists()) {
            Log.e(TAG, "获取文件列表失败");
            return fileNames;
        }

        File[] files = dir.listFiles();
        if (files == null) {
            Log.w(TAG, "目录为空或无权限");
            return fileNames;
        }

        for (File file : files) {
            if (file.isFile() && file.getName().endsWith(".json")) {
                fileNames.add(file.getName());
            }
        }
        return fileNames;
    }

    public boolean exportFingerprints(String fileName, List<WifiFingerprint> fingerprints) {
        if (fingerprints == null || fingerprints.isEmpty()) {
            Log.e(TAG, "导出失败：无数据");
            Toast.makeText(context, "无指纹数据可导出", Toast.LENGTH_SHORT).show();
            return false;
        }
        return saveFingerprints(fileName, fingerprints);
    }
}