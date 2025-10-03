package com.example.mywifiscanner;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

import androidx.activity.result.ActivityResultLauncher;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * 地图文件管理模块：负责地图数据的保存、加载、导入、导出等文件操作
 */
public class MapFileModule {
    private static final String TAG = "MapFileModule";
    private static final String MAP_DIR = "wifi_maps"; // 地图文件存储目录
    private final Context context;
    private final FingerprintManager fingerprintManager;
    private MapData currentMapData; // 当前正在编辑的地图数据

    // 构造方法：初始化上下文和指纹管理器
    public MapFileModule(Context context, FingerprintManager fingerprintManager) {
        this.context = context;
        this.fingerprintManager = fingerprintManager;
        // 确保存储目录存在
        ensureDirExists();
    }

    // 确保存储目录存在
    private void ensureDirExists() {
        File dir = new File(context.getFilesDir(), MAP_DIR);
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }

    // ==================== 当前地图数据管理 ====================
    public MapData getCurrentMapData() {
        return currentMapData;
    }

    public void setCurrentMapData(MapData currentMapData) {
        this.currentMapData = currentMapData;
    }

    // ==================== 地图文件保存 ====================
    /**
     * 保存地图数据到文件
     * @param fileName 文件名（含.json后缀）
     * @param data 要保存的地图数据
     * @return 是否保存成功
     */
    public boolean saveMapData(String fileName, MapData data) {
        if (data == null || fileName == null || fileName.isEmpty()) {
            Log.e(TAG, "保存失败：数据或文件名无效");
            return false;
        }

        try {
            // 序列化MapData为JSON
            Gson gson = new Gson();
            String json = gson.toJson(data);

            // 写入文件
            File file = new File(context.getFilesDir(), MAP_DIR + File.separator + fileName);
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(json.getBytes());
                Log.d(TAG, "地图保存成功：" + file.getAbsolutePath());
                // 更新当前地图数据
                setCurrentMapData(data);
                return true;
            }
        } catch (IOException e) {
            Log.e(TAG, "保存地图失败：" + e.getMessage());
            return false;
        }
    }

    // ==================== 地图文件加载 ====================
    /**
     * 从文件加载地图数据
     * @param fileName 文件名（含.json后缀）
     * @return 加载的MapData，失败返回null
     */
    public MapData loadMapData(String fileName) {
        try {
            File file = new File(context.getFilesDir(), MAP_DIR + File.separator + fileName);
            if (!file.exists()) {
                Log.e(TAG, "文件不存在：" + fileName);
                return null;
            }

            // 读取文件内容
            try (FileInputStream fis = new FileInputStream(file)) {
                byte[] buffer = new byte[(int) file.length()];
                fis.read(buffer);
                String json = new String(buffer);

                // 反序列化为MapData
                Gson gson = new Gson();
                Type type = new TypeToken<MapData>() {}.getType();
                MapData data = gson.fromJson(json, type);
                Log.d(TAG, "地图加载成功：" + fileName);
                return data;
            }
        } catch (IOException e) {
            Log.e(TAG, "加载地图失败：" + e.getMessage());
            return null;
        }
    }

    // ==================== 地图文件列表 ====================
    /**
     * 获取所有地图文件名（不含路径，含.json后缀）
     */
    public List<String> getAllMapFileNames() {
        List<String> fileNames = new ArrayList<>();
        File dir = new File(context.getFilesDir(), MAP_DIR);
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile() && file.getName().endsWith(".json")) {
                    fileNames.add(file.getName());
                }
            }
        }
        return fileNames;
    }

    // ==================== 地图文件更新 ====================
    /**
     * 更新当前地图数据（覆盖保存）
     */
    public boolean updateCurrentMapData(MapData data) {
        if (currentMapData == null || data == null) {
            Log.e(TAG, "更新失败：当前无编辑文件或数据为空");
            return false;
        }
        // 使用当前文件名保存
        return saveMapData(currentMapData.getFileName() + ".json", data);
    }

    // ==================== 地图文件导入 ====================
    /**
     * 启动文件选择器导入JSON地图文件
     */
    public void importJsonFile(Context context, ActivityResultLauncher<Intent> launcher) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        launcher.launch(intent);
    }

    /**
     * 解析导入的JSON文件为MapData（兼容单个对象和数组格式）
     */
    public MapData parseImportedJson(Uri uri, Context context) {
        InputStream is = null;
        try {
            // 1. 读取JSON文件内容（原逻辑不变）
            is = context.getContentResolver().openInputStream(uri);
            if (is == null) {
                Log.e(TAG, "无法打开输入流");
                return null;
            }
            byte[] buffer = new byte[is.available()];
            is.read(buffer);
            String json = new String(buffer);
            Gson gson = new Gson();

            // 2. 先尝试解析为“单个MapData对象”（正常导出的格式）
            try {
                Type objectType = new TypeToken<MapData>() {}.getType();
                MapData singleMapData = gson.fromJson(json, objectType);
                Log.d(TAG, "解析成功：导入的是单个MapData对象");
                return singleMapData;
            } catch (JsonSyntaxException e1) {
                // 3. 若解析单个对象失败，尝试解析为“MapData数组”（兼容错误格式）
                Log.w(TAG, "解析单个对象失败，尝试解析为MapData数组", e1);
                try {
                    Type arrayType = new TypeToken<List<MapData>>() {}.getType();
                    List<MapData> mapDataList = gson.fromJson(json, arrayType);

                    // 数组非空则返回第一个元素（符合“单个地图文件”的业务逻辑）
                    if (mapDataList != null && !mapDataList.isEmpty()) {
                        Log.d(TAG, "解析成功：导入的是MapData数组，返回第一个元素");
                        return mapDataList.get(0);
                    } else {
                        Log.e(TAG, "解析数组失败：数组为空");
                        return null;
                    }
                } catch (JsonSyntaxException e2) {
                    // 4. 若数组也解析失败，说明JSON格式完全不匹配
                    Log.e(TAG, "解析数组也失败，JSON格式错误（既非对象也非数组）", e2);
                    return null;
                }
            }

        } catch (IOException e) {
            Log.e(TAG, "读取导入文件失败：" + e.getMessage(), e);
            return null;
        } finally {
            // 5. 关闭输入流（避免资源泄漏，原代码遗漏）
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                    Log.w(TAG, "关闭输入流失败", e);
                }
            }
        }
    }

    // ==================== 地图文件导出 ====================
    /**
     * 导出精简的指纹数据（仅包含定位所需信息）
     */
    public boolean exportSimplifiedFingerprints(String fileName, List<WifiFingerprint> fingerprints,
                                                String imageName, int imageWidth, int imageHeight) {
        if (fingerprints == null || fingerprints.isEmpty()) {
            Log.e(TAG, "导出失败：无指纹数据");
            return false;
        }

        // 构建导出数据（可根据需求定义SimplifiedData类）
        SimplifiedData exportData = new SimplifiedData();
        exportData.setImageName(imageName);
        exportData.setImageWidth(imageWidth);
        exportData.setImageHeight(imageHeight);
        exportData.setFingerprints(fingerprints);

        try {
            Gson gson = new Gson();
            String json = gson.toJson(exportData);

            // 保存到导出目录
            File exportDir = new File(context.getExternalFilesDir(null), "exported_wifi");
            if (!exportDir.exists()) exportDir.mkdirs();

            File file = new File(exportDir, fileName);
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(json.getBytes());
                Log.d(TAG, "导出成功：" + file.getAbsolutePath());
                return true;
            }
        } catch (IOException e) {
            Log.e(TAG, "导出失败：" + e.getMessage());
            return false;
        }
    }

    /**
     * 打开导出文件目录
     */
    public void openSavedFilesDirectory() {
        File exportDir = new File(context.getExternalFilesDir(null), "exported_wifi");
        if (!exportDir.exists()) {
            exportDir.mkdirs();
        }
        // 跳转到文件管理器
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(Uri.fromFile(exportDir), "resource/folder");
        if (intent.resolveActivity(context.getPackageManager()) != null) {
            context.startActivity(intent);
        }
    }

    // 内部类：精简导出数据模型
    private static class SimplifiedData {
        private String imageName;
        private int imageWidth;
        private int imageHeight;
        private List<WifiFingerprint> fingerprints;

        // Getter和Setter
        public String getImageName() { return imageName; }
        public void setImageName(String imageName) { this.imageName = imageName; }
        public int getImageWidth() { return imageWidth; }
        public void setImageWidth(int imageWidth) { this.imageWidth = imageWidth; }
        public int getImageHeight() { return imageHeight; }
        public void setImageHeight(int imageHeight) { this.imageHeight = imageHeight; }
        public List<WifiFingerprint> getFingerprints() { return fingerprints; }
        public void setFingerprints(List<WifiFingerprint> fingerprints) { this.fingerprints = fingerprints; }
    }
}