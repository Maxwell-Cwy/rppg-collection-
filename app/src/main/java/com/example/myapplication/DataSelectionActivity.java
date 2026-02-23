package com.example.myapplication;

import android.app.ProgressDialog;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.model.DetectionTimeStamp;
import com.example.myapplication.model.OximeterData;

import org.json.JSONObject;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class DataSelectionActivity extends AppCompatActivity {

    private Spinner spinnerFilter;
    private RecyclerView recyclerView;
    private DataSelectionAdapter adapter;
    private List<File> allDirectories = new ArrayList<>();
    private List<Boolean> isUploadedList = new ArrayList<>();
    private List<File> filteredDirectories = new ArrayList<>();
    private DataUploadService uploadService;
    private String currentFilter = "全部"; // 默认

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_data_selection);

        spinnerFilter = findViewById(R.id.spinner_filter);
        recyclerView = findViewById(R.id.recycler_view);
        findViewById(R.id.btn_upload_selected).setOnClickListener(v -> uploadSelectedData());

        uploadService = new DataUploadService(this);

        // 设置 Spinner
        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, new String[]{"全部", "未上传", "已上传"});
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerFilter.setAdapter(spinnerAdapter);
        spinnerFilter.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                currentFilter = (String) parent.getItemAtPosition(position);
                filterDirectories();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        loadAllDirectories();
        filterDirectories();
    }

    private void loadAllDirectories() {
        File rootDir = new File(getExternalFilesDir(null), "OximeterRecords");
        if (!rootDir.exists()) {
            Toast.makeText(this, "未找到数据目录", Toast.LENGTH_SHORT).show();
            return;
        }
        File[] dirs = rootDir.listFiles(File::isDirectory);
        if (dirs != null) {
            allDirectories = Arrays.asList(dirs);
            // 按时间降序排序
            allDirectories.sort((a, b) -> b.getName().compareTo(a.getName()));

            isUploadedList.clear(); //清空
            // 为每个目录检查 uploaded
            for (File dir : allDirectories) {
                boolean isUploaded = false;
                try {
                    File jsonFile = new File(dir, "checkInfor.json");
                    if (jsonFile.exists()) {
                        String jsonStr = new String(Files.readAllBytes(jsonFile.toPath()), "UTF-8");
                        JSONObject json = new JSONObject(jsonStr);
                        isUploaded = json.optBoolean("uploaded", false);
                    }
                } catch (Exception e) {
                    // 如果无 JSON 或解析失败，假设未上传
                }
                isUploadedList.add(isUploaded);
            }
        }
    }

    private List<Boolean> getUploadedStatusForFiltered() {
        List<Boolean> status = new ArrayList<>();
        for (File dir : filteredDirectories) {
            int index = allDirectories.indexOf(dir);
            status.add(index != -1 && isUploadedList.get(index));
        }
        return status;
    }

    private void filterDirectories() {
        filteredDirectories.clear();
        for (int i = 0; i < allDirectories.size(); i++) {
            boolean isUploaded = isUploadedList.get(i);
            if ("全部".equals(currentFilter) ||
                    ("未上传".equals(currentFilter) && !isUploaded) ||
                    ("已上传".equals(currentFilter) && isUploaded)) {
                filteredDirectories.add(allDirectories.get(i));
            }
        }

        if (adapter == null) {
            adapter = new DataSelectionAdapter(filteredDirectories, getUploadedStatusForFiltered());
            recyclerView.setLayoutManager(new LinearLayoutManager(this));
            recyclerView.setAdapter(adapter);
        } else {
            adapter.updateDirectories(filteredDirectories, getUploadedStatusForFiltered());
        }
    }

    private void uploadSelectedData() {
        List<File> selected = adapter.getSelectedDirectories();
        if (selected.isEmpty()) {
            Toast.makeText(this, "未选择数据", Toast.LENGTH_SHORT).show();
            return;
        }

        ProgressDialog pd = new ProgressDialog(this);
        pd.setMessage("上传中...");
        pd.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        pd.setMax(selected.size());
        pd.setProgress(0);
        pd.setCancelable(false);
        pd.show();

        final int[] completedCount = {0};

        for (File dir : selected) {
            try {
                File videoFile = new File(dir, "checkVideo.mp4");
                File jsonFile = new File(dir, "checkInfor.json");

                if (!videoFile.exists()) {
                    Toast.makeText(this, dir.getName() + " 缺少视频文件", Toast.LENGTH_SHORT).show();
                    completedCount[0]++;
                    pd.setProgress(completedCount[0]);
                    if (completedCount[0] == selected.size()) pd.dismiss();
                    continue;
                }
                if (!jsonFile.exists()) {
                    Toast.makeText(this, dir.getName() + " 缺少JSON文件", Toast.LENGTH_SHORT).show();
                    completedCount[0]++;
                    pd.setProgress(completedCount[0]);
                    if (completedCount[0] == selected.size()) pd.dismiss();
                    continue;
                }

                // 读取本地完整 JSON
                String jsonStr = new String(Files.readAllBytes(jsonFile.toPath()), "UTF-8");
                JSONObject fullJson = new JSONObject(jsonStr);

                // === 提取血压值（用于后面插入）===
                int systolic = fullJson.optInt("blood_pressure_systolic", -1);
                int diastolic = fullJson.optInt("blood_pressure_diastolic", -1);

                // === 移除 uploaded 字段（不传给后台）===
                if (fullJson.has("uploaded")) {
                    fullJson.remove("uploaded");
                }

                // === 确保血压字段存在（防止 null 变成缺失）===
                if (systolic > 0) {
                    fullJson.put("blood_pressure_systolic", systolic);
                } else {
                    fullJson.put("blood_pressure_systolic", JSONObject.NULL);
                }
                if (diastolic > 0) {
                    fullJson.put("blood_pressure_diastolic", diastolic);
                } else {
                    fullJson.put("blood_pressure_diastolic", JSONObject.NULL);
                }

                // === 生成最终要上传的 extendJson 字符串 ===
                String extendJson = fullJson.toString();

                // === 解析时间戳（用于上传校验）===
                DetectionTimeStamp timeStamp = new DetectionTimeStamp();
                timeStamp.setBluetoothConnectTime(fullJson.optString("bluetooth_connect_time", null));
                timeStamp.setBluetoothDataStartTime(fullJson.optString("data_start_time", null));
                timeStamp.setVideoStartTime(fullJson.optString("video_start_time", null));

                if (timeStamp.getBluetoothConnectTime() == null || timeStamp.getVideoStartTime() == null) {
                    Toast.makeText(this, dir.getName() + " 时间戳不完整", Toast.LENGTH_SHORT).show();
                    completedCount[0]++;
                    pd.setProgress(completedCount[0]);
                    if (completedCount[0] == selected.size()) pd.dismiss();
                    continue;
                }

                // === 解析 OximeterData（仅用于 hasData() 检查）===
                OximeterData oximeterData = OximeterData.fromJson(fullJson);

                if (!oximeterData.hasData()) {
                    Toast.makeText(this, dir.getName() + " 数据为空", Toast.LENGTH_SHORT).show();
                    completedCount[0]++;
                    pd.setProgress(completedCount[0]);
                    if (completedCount[0] == selected.size()) pd.dismiss();
                    continue;
                }

                uploadService.uploadWithJsonString(extendJson, videoFile.getAbsolutePath(), new DataUploadService.UploadListener() {
                    @Override
                    public void onUploadSuccess(String response) {
                        runOnUiThread(() -> {
                            Toast.makeText(DataSelectionActivity.this, dir.getName() + " 上传成功", Toast.LENGTH_SHORT).show();
                            Log.w("上传成功",response);
                            // 更新本地 uploaded 状态
                            try {
                                fullJson.put("uploaded", true);
                                Files.write(jsonFile.toPath(), fullJson.toString(2).getBytes("UTF-8")); // 美化可选
                                int index = allDirectories.indexOf(dir);
                                if (index != -1) isUploadedList.set(index, true);
                                filterDirectories();
                            } catch (Exception e) {
                                Toast.makeText(DataSelectionActivity.this, "更新上传状态失败", Toast.LENGTH_SHORT).show();
                            }
                            completedCount[0]++;
                            pd.setProgress(completedCount[0]);
                            if (completedCount[0] == selected.size()) pd.dismiss();
                        });
                    }

                    @Override
                    public void onUploadFailed(String errorMsg) {
                        runOnUiThread(() -> {
                            Toast.makeText(DataSelectionActivity.this, dir.getName() + " 上传失败: " + errorMsg, Toast.LENGTH_SHORT).show();
                            Log.w("上传失败",errorMsg);
                            completedCount[0]++;
                            pd.setProgress(completedCount[0]);
                            if (completedCount[0] == selected.size()) pd.dismiss();
                        });
                    }

                    @Override
                    public void onUploadProgress(int progress) {
                        // 文件级进度，不影响整体目录进度
                    }
                });

            } catch (Exception e) {
                Toast.makeText(this, dir.getName() + " 加载失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                completedCount[0]++;
                pd.setProgress(completedCount[0]);
                if (completedCount[0] == selected.size()) pd.dismiss();
            }
        }
    }
}