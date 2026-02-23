// Modified DataSaver.java
package com.example.myapplication.utils;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import com.example.myapplication.model.DetectionTimeStamp;
import com.example.myapplication.model.OximeterData;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public class DataSaver {
    private static final String TAG = "DataSaver";
    // 👇 新增：静态血压字段（不推荐！）
    private static int systolic = -1;
    private static int diastolic = -1;

    // 提供设置方法
    public static void setBloodPressure(int sys, int dia) {
        systolic = sys;
        diastolic = dia;
    }
    //判断是否已输入
    public static boolean hasBloodPressure() {
        return systolic > 0 && diastolic > 0;
    }

    public static void saveAllData(Context context,
                                   String videoPath,
                                   OximeterData oximeterData,
                                   DetectionTimeStamp timeStamp) {
        try {

            // 1. 复制视频
            File sourceVideo = new File(videoPath);
            File timeDir = sourceVideo.getParentFile();

            // 【可选】校验目录是否存在（按你之前需求：不存在就报错）
            if (timeDir == null || !timeDir.exists()) {
                throw new IllegalStateException("视频所在目录不存在: " + (timeDir != null ? timeDir.getAbsolutePath() : "null"));
            }


            // 2. 保存原始数据
            File rawFile = new File(timeDir, "originData.txt");
            Files.write(rawFile.toPath(), oximeterData.toHexString().getBytes());

            // 3. 保存报告
            File reportFile = new File(timeDir, "checkReport.txt");
            Files.write(reportFile.toPath(), oximeterData.generateReport().getBytes("UTF-8"));

            // 4. 保存 JSON
            File jsonFile = new File(timeDir, "checkInfor.json");
            String json = generateJson(oximeterData, timeStamp, true); // 带 uploaded
            Files.write(jsonFile.toPath(), json.getBytes("UTF-8"));

            Log.e(TAG, "检测数据已完整保存！\n路径: " + timeDir.getAbsolutePath());

        } catch (Exception e) {
            Log.e(TAG, "本地保存失败", e);
            throw new RuntimeException("保存失败: " + e.getMessage(), e);
        }
    }
    public static String generateJson(OximeterData data, DetectionTimeStamp ts, boolean includeUploaded) {
        try {
            // ========== 基础信息（你原来就有的）==========
            StringBuilder json = new StringBuilder();
            json.append("{\n");
            json.append("  \"device_model\": \"").append(Build.MODEL).append("\",\n");
//            json.append("  \"detect_start_time\": \"").append(data.getStartTime()).append("\",\n");
            json.append("  \"bluetooth_connect_time\": \"").append(safe(ts != null ? ts.getBluetoothConnectTime() : null)).append("\",\n");
            json.append("  \"data_start_time\": \"").append(safe(ts != null ? ts.getBluetoothDataStartTime() : null)).append("\",\n");
            json.append("  \"video_start_time\": \"").append(safe(ts != null ? ts.getVideoStartTime() : null)).append("\",\n");

            // ========== 统计值（你原来就有的）==========
            json.append("  \"avg_spo2\": ").append(data.getAvgSpo2() >= 0 ? data.getAvgSpo2() : "null").append(",\n");
            json.append("  \"min_spo2\": ").append(data.getMinSpo2() >= 0 ? data.getMinSpo2() : "null").append(",\n");
            json.append("  \"max_spo2\": ").append(data.getMaxSpo2()).append(",\n");
            json.append("  \"avg_pr\": ").append(data.getAvgPr() >= 0 ? data.getAvgPr() : "null").append(",\n");
            json.append("  \"min_pr\": ").append(data.getMinPr() >= 0 ? data.getMinPr() : "null").append(",\n");
            json.append("  \"max_pr\": ").append(data.getMaxPr()).append(",\n");
            json.append("  \"temperature\": ").append(data.getTemperature() > 0 ? String.format("%.1f", data.getTemperature()) : "null").append(",\n");
            json.append("  \"pi\": ").append(data.getPi() >= 0 ? String.format("%.2f", data.getPi()) : "null").append(",\n");
            json.append("  \"respiration_rate\": ").append(data.getRespirationRate() > 0 ? data.getRespirationRate() : "null").append(",\n");
            json.append("  \"probe_status\": \"").append(data.getProbeStatus()).append("\",\n");
            json.append("  \"battery_level\": ").append(data.getBatteryLevel()).append(",\n");
            // ========== 血压数据（新增）==========
            json.append(" \"blood_pressure_systolic\": ").append(systolic > 0 ? systolic : "null").append(",\n");
            json.append(" \"blood_pressure_diastolic\": ").append(diastolic > 0 ? diastolic : "null").append(",\n");

            // ========== PPG 完整波形数据（新增，带采样率）==========
            json.append("  \"ppg_sample_rate_hz\": 5,\n");
            json.append("  \"ppg_data\": [\n");
            var ppgList = data.getPpgList();
            var barList = data.getBarList();
            int ppgSize = Math.min(ppgList.size(), barList.size());
            for (int i = 0; i < ppgSize; i++) {
                json.append("    {\"index\":").append(i)
                        .append(",\"wave\":").append(ppgList.get(i))
                        .append(",\"bar\":").append(barList.get(i)).append("}");
                if (i < ppgSize - 1) json.append(",");
                json.append("\n");
            }
            json.append("  ],\n");

            // ========== HRV 数据（新增）==========
            json.append("  \"hrv_sample_rate\": \"1_pack_per_10_beats\",\n");
            json.append("  \"hrv_data\": [\n");
            var hrvList = data.getHrvList();
            for (int i = 0; i < hrvList.size(); i++) {
                json.append("    ").append(hrvList.get(i));
                if (i < hrvList.size() - 1) json.append(",");
                json.append("\n");
            }
            json.append("  ],\n");

            json.append("  \"raw_hex_data\": \"").append(data.toHexString().replace("\"", "\\\"")).append("\"\n");
            if (includeUploaded) {
                json.append(",  \"uploaded\": false\n");
            }
            json.append("}");

            return json.toString();

        } catch (Exception e) {
            Log.e(TAG, "生成JSON失败", e);
            return "{\"error\": \"generate json failed\"}";
        }
    }
    private static String safe(String s) {
        return s != null ? s : "";
    }
}