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

/**
 * 指纹库文件管理模块：修复权限适配、异常处理、文件校验逻辑
 */
public class MapFileModule {
    private static final String TAG = "MapFileModule";
    private static final String FINGERPRINT_DIR = "WiFi_Fingerprints"; // 下载目录子文件夹
    private final Context context;
    private final Gson gson = new Gson();

    public MapFileModule(Context context) {
        this.context = context;
        ensureDirExists(); // 初始化时确保目录存在
    }

    /**
     * 修改为公共下载目录下的子文件夹
     * 路径：/storage/emulated/0/Download/WiFi_Fingerprints/
     */
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
     * 确保目录存在，若不存在则创建（修复创建失败的场景）
     */
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
                Log.e(TAG, "指纹库目录创建失败（可能权限不足或存储空间满）");
                Toast.makeText(context, "文件目录创建失败，无法保存指纹库", Toast.LENGTH_SHORT).show();
            }
        }
    }

    /**
     * 解析导入的指纹文件（从输入流）- 修复版本
     */
    public List<WifiFingerprint> parseImportedFingerprints(InputStream inputStream) {
        try {
            // 读取输入流内容
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
                return null;
            }

            // 使用Gson解析
            Type type = new TypeToken<List<WifiFingerprint>>(){}.getType();
            List<WifiFingerprint> fingerprints = gson.fromJson(json, type);

            if (fingerprints == null) {
                Log.e(TAG, "Gson解析返回null");
                return null;
            }

            Log.d(TAG, "解析导入的指纹库成功，共" + fingerprints.size() + "条指纹");
            return fingerprints;

        } catch (IOException e) {
            Log.e(TAG, "读取文件流失败：" + e.getMessage());
            return null;
        } catch (JsonSyntaxException e) {
            Log.e(TAG, "JSON格式错误，无法解析指纹库：" + e.getMessage());
            Log.e(TAG, "JSON语法异常位置: " + e.getCause());
            return null;
        } catch (Exception e) {
            Log.e(TAG, "解析指纹库时发生未知错误：" + e.getMessage());
            return null;
        }
    }

    // ==================== 指纹库保存 ====================
    public boolean saveFingerprints(String fileName, List<WifiFingerprint> fingerprints) {
        if (fingerprints == null || fileName == null || fileName.isEmpty()) {
            Log.e(TAG, "保存失败：指纹列表为空或文件名无效");
            return false;
        }

        try {
            String json = gson.toJson(fingerprints);
            Log.d(TAG, "准备保存JSON，长度: " + json.length());

            // 1. 确保目录存在
            File dir = getFingerprintDirectory();
            if (dir == null) { // 增加目录为空的判断
                Log.e(TAG, "保存失败：目录不可用");
                return false;
            }
            if (!dir.exists()) {
                boolean dirCreated = dir.mkdirs();
                Log.d(TAG, "创建目录: " + dir.getAbsolutePath() + " 结果: " + dirCreated);
            }

            // 2. 创建文件对象
            File file = new File(dir, fileName);

            // 3. 写入文件
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(json.getBytes("UTF-8"));
                fos.flush();
                Log.d(TAG, "指纹库保存成功：" + file.getAbsolutePath());

                // 保存成功后，提示用户文件位置
                String savePath = file.getAbsolutePath();
                Toast.makeText(context, "文件已保存至：" + savePath, Toast.LENGTH_LONG).show();

                // 通知系统更新文件
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    MediaScannerConnection.scanFile(
                            context,
                            new String[]{file.getAbsolutePath()},
                            null,
                            null
                    );
                }
                return true;
            }
        } catch (IOException e) {
            Log.e(TAG, "保存指纹库失败：" + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    // ==================== 指纹库加载 ====================
    public List<WifiFingerprint> loadFingerprints(String fileName) {
        try {
            File file = new File(getFingerprintDirectory(), fileName);
            Log.d(TAG, "加载文件路径: " + file.getAbsolutePath());

            if (!file.exists()) {
                Log.e(TAG, "文件不存在：" + fileName);
                return new ArrayList<>(); // 返回空列表而不是null
            }

            try (FileInputStream fis = new FileInputStream(file)) {
                StringBuilder jsonBuilder = new StringBuilder();
                byte[] buffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    jsonBuilder.append(new String(buffer, 0, bytesRead, "UTF-8"));
                }

                String json = jsonBuilder.toString().trim();
                Log.d(TAG, "加载JSON内容，长度: " + json.length());

                if (json.isEmpty()) {
                    Log.w(TAG, "文件内容为空");
                    return new ArrayList<>();
                }

                Type type = new TypeToken<List<WifiFingerprint>>(){}.getType();
                List<WifiFingerprint> fingerprints = gson.fromJson(json, type);

                if (fingerprints == null) {
                    Log.w(TAG, "解析结果为null，返回空列表");
                    return new ArrayList<>();
                }

                Log.d(TAG, "指纹库加载成功：" + fileName + "（共" + fingerprints.size() + "条指纹）");
                return fingerprints;
            }
        } catch (IOException e) {
            Log.e(TAG, "加载指纹库失败：" + e.getMessage());
            return new ArrayList<>(); // 返回空列表而不是null
        } catch (JsonSyntaxException e) {
            Log.e(TAG, "JSON格式错误，无法解析指纹库：" + e.getMessage());
            return new ArrayList<>(); // 返回空列表而不是null
        }
    }

    /**
     * 获取所有指纹库文件（过滤非JSON文件）
     */
    public List<String> getAllFingerprintFileNames() {
        List<String> fileNames = new ArrayList<>();
        File dir = getFingerprintDirectory();
        if (dir == null || !dir.exists()) {
            Log.e(TAG, "获取文件列表失败：目录不可用");
            return fileNames;
        }

        File[] files = dir.listFiles();
        if (files == null) {
            Log.w(TAG, "获取文件列表失败：目录为空或无权限");
            return fileNames;
        }

        for (File file : files) {
            if (file.isFile() && file.getName().endsWith(".json")) {
                fileNames.add(file.getName());
                Log.d(TAG, "发现指纹库文件：" + file.getName());
            }
        }
        return fileNames;
    }

    /**
     * 导出指纹库（保存到公共下载目录子文件夹）
     */
    public boolean exportFingerprints(String fileName, List<WifiFingerprint> fingerprints) {
        if (fingerprints == null || fingerprints.isEmpty()) {
            Log.e(TAG, "导出失败：指纹列表为空");
            Toast.makeText(context, "无指纹数据可导出", Toast.LENGTH_SHORT).show();
            return false;
        }
        if (fileName == null || fileName.isEmpty()) {
            fileName = "export_fingerprints_" + System.currentTimeMillis() + ".json";
        } else if (!fileName.endsWith(".json")) {
            fileName += ".json";
        }

        File dir = getFingerprintDirectory();
        if (dir == null || !dir.exists()) {
            Log.e(TAG, "导出失败：目录不可用");
            Toast.makeText(context, "文件目录不可用，无法导出", Toast.LENGTH_SHORT).show();
            return false;
        }

        File exportFile = new File(dir, fileName);
        try {
            String json = gson.toJson(fingerprints);
            try (FileOutputStream fos = new FileOutputStream(exportFile)) {
                fos.write(json.getBytes());
                fos.flush();

                // 通知系统更新媒体库
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    MediaScannerConnection.scanFile(
                            context,
                            new String[]{exportFile.getAbsolutePath()},
                            null,
                            null
                    );
                }

                Log.d(TAG, "指纹库导出成功：" + exportFile.getAbsolutePath());
                // 提示用户文件位置
                Toast.makeText(context, "导出成功！路径：" + exportFile.getAbsolutePath(), Toast.LENGTH_LONG).show();
                return true;
            }
        } catch (IOException e) {
            Log.e(TAG, "导出失败：IO错误", e);
            Toast.makeText(context, "导出失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
            return false;
        } catch (Exception e) {
            Log.e(TAG, "导出失败：未知错误", e);
            Toast.makeText(context, "导出失败：未知错误", Toast.LENGTH_SHORT).show();
            return false;
        }
    }

    /**
     * 打开导出目录（适配公共下载目录）
     */
    public void openExportedDirectory() {
        File dir = getFingerprintDirectory();
        if (dir == null) {
            Log.e(TAG, "打开目录失败：目录不可用");
            Toast.makeText(context, "目录不可用，无法打开", Toast.LENGTH_SHORT).show();
            return;
        }

        // 确保目录存在
        if (!dir.exists() && !dir.mkdirs()) {
            Log.e(TAG, "打开目录失败：无法创建目录 " + dir.getAbsolutePath());
            Toast.makeText(context, "无法创建存储目录", Toast.LENGTH_SHORT).show();
            return;
        }

        // 构建FileProvider的Authority（需与AndroidManifest中一致）
        String authority = context.getPackageName() + ".fileprovider";
        Uri dirUri;
        try {
            dirUri = FileProvider.getUriForFile(context, authority, dir);
            Log.d(TAG, "生成目录Uri：" + dirUri.toString());
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "FileProvider配置错误：" + e.getMessage() + "\n请检查file_paths.xml是否正确配置", e);
            Toast.makeText(context, "文件配置错误，无法打开目录", Toast.LENGTH_SHORT).show();
            Toast.makeText(context, "文件存储路径：" + dir.getAbsolutePath(), Toast.LENGTH_LONG).show();
            return;
        }

        // 打开目录的Intent
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(dirUri, "application/vnd.android.document.dir");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        // 检查是否有应用能处理该Intent
        if (intent.resolveActivity(context.getPackageManager()) != null) {
            try {
                context.startActivity(intent);
                return;
            } catch (Exception e) {
                Log.e(TAG, "启动文件管理器失败：" + e.getMessage(), e);
            }
        }

        // 备用方案：尝试用更通用的MIME类型
        intent.setDataAndType(dirUri, "resource/folder");
        if (intent.resolveActivity(context.getPackageManager()) != null) {
            try {
                context.startActivity(intent);
                return;
            } catch (Exception e) {
                Log.e(TAG, "备用方案启动失败：" + e.getMessage());
            }
        }

        // 最终方案：提示用户手动查找
        Log.w(TAG, "所有方案均失败，提示用户手动查找");
        Toast.makeText(context, "无法自动打开目录，请手动访问：", Toast.LENGTH_SHORT).show();
        Toast.makeText(context, dir.getAbsolutePath(), Toast.LENGTH_LONG).show();
    }
}