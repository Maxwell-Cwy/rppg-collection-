package com.example.myapplication;
import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import com.example.myapplication.model.DetectionTimeStamp;
import com.example.myapplication.model.OximeterData;
import com.example.myapplication.utils.DataSaver;
import com.example.myapplication.utils.TimeUtils;
import androidx.camera.view.PreviewView;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import android.widget.TextView;

import java.io.File;


public class StartActivity extends AppCompatActivity
        implements BluetoothService.BluetoothListener,
        VideoRecorder.VideoListener,
        DataUploadService.UploadListener {

    private static final String TAG = "StartActivity";
    private static final int REQUEST_ALL_PERMISSIONS = 1001;
    private static final int REQUEST_SELECT_DEVICE = 1002;

    // UI
    private PreviewView previewView;

    private MaterialButton btnBluetoothDetect;
    private MaterialButton btnStartDetection;
    private MaterialButton btnManualUpload;
    private MaterialButton btnExitPreview;

    // 服务
    private BluetoothService bluetoothService;
    private VideoRecorder videoRecorder;
//    private DataUploadService uploadService;

    // 数据
    private DetectionTimeStamp timeStamp;
    private OximeterData oximeterData;
    private String videoFilePath;

    private TextView tvCountdown;
    private CountDownTimer countDownTimer;

    private boolean isDetectionInProgress = false; //检测是否开始
    private boolean shouldSaveAndUpload = false; //是否应该自动上传和保存到本地

    private MaterialButton btnInputBloodPressure; //输入血压按钮

    private static final String TAG_UVC = "UVC_FRAGMENT";
   // private final MyCameraFragment uvcFragment= new MyCameraFragment();;
    private final String[] REQUIRED_PERMISSIONS = {
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.INTERNET,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_start);
        initViews();
        initServices();
        checkAllPermissions();
        bindEvents();
    }

    private void initViews() {
        previewView = findViewById(R.id.preview_view);

        btnBluetoothDetect = findViewById(R.id.btn_bluetooth_detect);
        btnStartDetection = findViewById(R.id.btn_start_detection);
        btnManualUpload = findViewById(R.id.btn_manual_upload);
        btnExitPreview = findViewById(R.id.btn_exit_preview);
        tvCountdown = findViewById(R.id.tv_countdown);
        btnInputBloodPressure = findViewById(R.id.btn_input_blood_pressure);

        previewView.setVisibility(View.GONE);
        btnExitPreview.setVisibility(View.GONE);
        tvCountdown.setVisibility(View.GONE);


        //
        btnStartDetection.setEnabled(true);
        btnStartDetection.setAlpha(1.0f);


        btnExitPreview.setOnClickListener(v -> {
            if (hasUvcCamera(this)) {
                exitUvcMode();      // ⭐ UVC 专用退出
            } else {
                stopDetectionEarly(); // CameraX 原逻辑
            }
        });

    }

    private void stopDetectionEarly() {
        isDetectionInProgress = false;
        shouldSaveAndUpload = false;

        oximeterData.clear(); // 👈 你需要确保 OximeterData 有 clear() 方法
        timeStamp.clear();    // 👈 你需要确保 DetectionTimeStamp 有 clear() 方法

        if (countDownTimer != null) {
            countDownTimer.cancel();
            countDownTimer = null;
        }
        tvCountdown.setVisibility(View.GONE);

        if (videoRecorder != null) {
            videoRecorder.stopRecording();
            videoRecorder.releaseResources(); // 👈 关键：释放资源
        }

        if (bluetoothService != null) {
            bluetoothService.stopReceivingData();
        }

        // 清空路径，避免残留
        videoFilePath = null;

        // 切回界面
        previewView.setVisibility(View.GONE);
        btnExitPreview.setVisibility(View.GONE);

        runOnUiThread(() -> {
//            tvDetectionResult.setText("⚠️ 检测提前终止");
            Toast.makeText(this, "检测提前终止", Toast.LENGTH_LONG).show();
            DataSaver.setBloodPressure(-1, -1);
        });
    }

    private void initServices() {
        bluetoothService = new BluetoothService(this, this);
        videoRecorder = new VideoRecorder(this, this, previewView);
        timeStamp = new DetectionTimeStamp();
        oximeterData = new OximeterData();
    }

    private void bindEvents() {

        btnInputBloodPressure.setOnClickListener(v -> showBloodPressureInputDialog());
        btnBluetoothDetect.setOnClickListener(v -> {
            startActivityForResult(new Intent(this, DeviceListActivity.class), REQUEST_SELECT_DEVICE);
        });

        btnStartDetection.setOnClickListener(v -> {
            if (!DataSaver.hasBloodPressure()) {
                Toast.makeText(StartActivity.this, "请先输入血压值", Toast.LENGTH_SHORT).show();
                return;
            }
            startDetection();
        });

        btnManualUpload.setOnClickListener(v -> {
            startActivity(new Intent(this, DataSelectionActivity.class));
        });
    }

    private void showBloodPressureInputDialog() {
        // 创建两个 EditText 用于输入 收缩压 和 舒张压
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 20, 40, 20);

        final EditText etSystolic = new EditText(this);
        etSystolic.setHint("收缩压 (如 120)");
        etSystolic.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        layout.addView(etSystolic);

        final EditText etDiastolic = new EditText(this);
        etDiastolic.setHint("舒张压 (如 80)");
        etDiastolic.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        layout.addView(etDiastolic);

        new AlertDialog.Builder(this)
                .setTitle("请输入血压值")
                .setView(layout)
                .setPositiveButton("确定", (dialog, which) -> {
                    String sysStr = etSystolic.getText().toString().trim();
                    String diaStr = etDiastolic.getText().toString().trim();

                    if (sysStr.isEmpty() || diaStr.isEmpty()) {
                        Toast.makeText(StartActivity.this, "请输入完整的血压值", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    try {
                        int systolic = Integer.parseInt(sysStr);
                        int diastolic = Integer.parseInt(diaStr);
//
//                        // 简单校验范围（可选）
//                        if (systolic < 70 || systolic > 250 || diastolic < 40 || diastolic > 150) {
//                            Toast.makeText(MainActivity.this, "血压值可能异常，请确认", Toast.LENGTH_LONG).show();
//                        }

                        DataSaver.setBloodPressure(systolic, diastolic);

                        // 更新状态栏显示
                        runOnUiThread(() -> {
                            Toast.makeText(this, "✅ 血压已录入：" + systolic + "/" + diastolic + " mmHg", Toast.LENGTH_LONG).show();
//                            tvBloodPressureStatus.setText("✅ 血压已录入：" + systolic + "/" + diastolic + " mmHg");
//                            tvBloodPressureStatus.setVisibility(View.VISIBLE);
                        });


                    } catch (NumberFormatException e) {
                        Toast.makeText(StartActivity.this, "请输入有效的数字", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }



    private void startDetection() {
        oximeterData.clear();
        timeStamp.clear();

        // 标记检测开始
        isDetectionInProgress = true;
        shouldSaveAndUpload = true;

        // === 切换到全屏预览模式 ===
        previewView.setVisibility(View.VISIBLE);
        btnExitPreview.setVisibility(View.VISIBLE);
        tvCountdown.setVisibility(View.VISIBLE);

        // 先判断模式
        // 标记当前是否使用 UVC
        boolean isUvcMode = hasUvcCamera(this);

        // ===== 先清理旧倒计时 =====
        if (countDownTimer != null) {
            countDownTimer.cancel();
            countDownTimer = null;
        }

        // ===== 根据模式选 UI =====
        TextView uvcCountdown = findViewById(R.id.uvc_countdown);


        if (isUvcMode) {
            previewView.setVisibility(View.GONE);
            tvCountdown.setVisibility(View.GONE);

            uvcCountdown.setVisibility(View.VISIBLE);
            uvcCountdown.setText("--"); // ⭐ 等画面出来再开始

            startUvcPreviewAndRecord(); // 只做这一件事

        } else {
            previewView.setVisibility(View.VISIBLE);
            tvCountdown.setVisibility(View.VISIBLE);
            uvcCountdown.setVisibility(View.GONE);

            countDownTimer = new CountDownTimer(90_000, 1_000) {
                @Override
                public void onTick(long millisUntilFinished) {
                    tvCountdown.setText(String.valueOf(millisUntilFinished / 1000));
                }

                @Override
                public void onFinish() {
                    tvCountdown.setVisibility(View.GONE);
                }
            }.start();

            startCameraXPreviewAndRecord();

// 延迟稍作调整，确保 videoRecorder 已经初始化完成
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (bluetoothService != null) {
                    Log.d(TAG, "原生模式：开启蓝牙数据接收");
                    bluetoothService.startReceivingData();
                }
            }, 800); // 增加到 800ms 避开 CameraX 的初始化峰值

        }

        btnExitPreview.setVisibility(View.VISIBLE);

    }


    private void startCameraXPreviewAndRecord() {
        if (videoRecorder != null) {
            videoRecorder.releaseResources();
        }

        videoRecorder = new VideoRecorder(this, this, previewView);
        videoRecorder.startRecording(90_000);
    }


    public void onUvcPreviewReady() {
        TextView uvcCountdown = findViewById(R.id.uvc_countdown);

        if (!isDetectionInProgress) return;

        // 防止重复启动
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }

        countDownTimer = new CountDownTimer(90_000, 1_000) {
            @Override
            public void onTick(long millisUntilFinished) {
                uvcCountdown.setText(String.valueOf(millisUntilFinished / 1000));
            }

            @Override
            public void onFinish() {
                uvcCountdown.setVisibility(View.GONE);
                if (isDetectionInProgress) {
                    exitUvcMode();
                }
            }
        }.start();
    }


    private void startUvcPreviewAndRecord() {


        // 1. 获取 UVC 容器
        View uvcContainer = findViewById(R.id.uvc_container);
        if (uvcContainer == null) return;
        MyCameraFragment fragment = new MyCameraFragment();
        // 2. 隐藏背景、图标、标题栏等（这些不需要显示）
        findViewById(R.id.bg_frame).setVisibility(View.GONE);
        findViewById(R.id.center_icon).setVisibility(View.GONE);
        findViewById(R.id.bottom_button_container).setVisibility(View.GONE);
        findViewById(R.id.title_bar).setVisibility(View.GONE);

        int[] iconIds = {R.id.btn_heart_rate, R.id.btn_positive_emotion, R.id.btn_negative_emotion,
                R.id.btn_hrvariability, R.id.btn_physiological, R.id.btn_psychological};
        for (int id : iconIds) {
            View v = findViewById(id);
            if (v != null) v.setVisibility(View.GONE);
        }

        // 3. 显示容器并加载 Fragment
        uvcContainer.setVisibility(View.VISIBLE);

        // 注意：这里只需要 commit 一次
        getSupportFragmentManager()
                .beginTransaction()
                .replace(
                        R.id.uvc_container,
                        new MyCameraFragment(),
                        TAG_UVC   // ← 用 TAG 管理
                )
                .commitAllowingStateLoss();


        // 必须在 replace 之后调用，确保它们盖在 Fragment 之上
        btnExitPreview.setVisibility(View.VISIBLE);
        btnExitPreview.bringToFront();

        // 获取 UVC 专用倒计时并置顶
        TextView uvcCountdown = findViewById(R.id.uvc_countdown);
        if (uvcCountdown != null) {
            uvcCountdown.setVisibility(View.VISIBLE);
            uvcCountdown.bringToFront();
        }

        // 强制让父布局重新绘制层级
        ((View)uvcContainer.getParent()).invalidate();

// 1. 获取原始时间，假设是 "2026-02-23 01:09:04.123"
        String rawTime = TimeUtils.getPreciseTimeStamp();

        // 2. 核心修改：如果包含小数点，只取小数点前面的部分
        String timeWithoutMillis = rawTime.contains(".") ? rawTime.split("\\.")[0] : rawTime;

        // 3. 替换非法字符，生成文件夹名 "20260223_010904"
        String timeStampFolder = timeWithoutMillis.replace("-", "")
                .replace(":", "")
                .replace(" ", "_");

        File recordDir = new File(getExternalFilesDir(null), "OximeterRecords/" + timeStampFolder);

        if (!recordDir.exists()) recordDir.mkdirs();

        // 3. 视频文件必须叫 checkVideo.mp4 才能被你的上传逻辑识别
        File videoFile = new File(recordDir, "checkVideo.mp4");
        videoFilePath = videoFile.getAbsolutePath();

        // 4. 启动 Fragment 并录制
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            Fragment f = getSupportFragmentManager().findFragmentByTag(TAG_UVC);
            if (f instanceof MyCameraFragment && f.isAdded()) {
                MyCameraFragment uvc = (MyCameraFragment) f;

                // 记录开始时间
                timeStamp.setVideoStartTime(TimeUtils.getPreciseTimeStamp());

                // 直接录制到最终位置
                uvc.startRecord(videoFilePath);


                // ⭐ 核心修改：在 UVC 录制开始后，立即同步启动蓝牙接收
                if (bluetoothService != null) {
                    Log.d(TAG, "UVC 录制已开始，同步启动蓝牙接收");
                    bluetoothService.startReceivingData();
                }
            }
        }, 1500);

    }




    private boolean hasUvcCamera(Context context) {
        UsbManager usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        if (usbManager == null) return false;

        for (UsbDevice device : usbManager.getDeviceList().values()) {
            // UVC 设备的标准 class
            if (device.getDeviceClass() == UsbConstants.USB_CLASS_VIDEO) {
                return true;
            }

            // 有些 UVC 是 interface 级别声明
            for (int i = 0; i < device.getInterfaceCount(); i++) {
                if (device.getInterface(i).getInterfaceClass()
                        == UsbConstants.USB_CLASS_VIDEO) {
                    return true;
                }
            }
        }
        return false;
    }

    private void exitUvcMode() {
        Log.d(TAG, "exitUvcMode");

        // 1️⃣ 停止倒计时
        if (countDownTimer != null) {
            countDownTimer.cancel();
            countDownTimer = null;
        }

        // 2️⃣ 通过 TAG 找 Fragment（⚠️ 核心）
        Fragment f = getSupportFragmentManager().findFragmentByTag(TAG_UVC);

        if (f instanceof MyCameraFragment) {
            MyCameraFragment uvc = (MyCameraFragment) f;
            try {
                if (uvc.isAdded()) {
                    uvc.stopRecord();     // 你自己封装的


                    // 模拟 CameraX 的回调，确保执行 DataSaver.saveAllData
                    if (isDetectionInProgress && shouldSaveAndUpload) {
                        String endTime = TimeUtils.getPreciseTimeStamp();
                        onVideoFinished(videoFilePath, endTime);
                    }
                    uvc.releaseUvc();
                }
            } catch (Exception e) {
                Log.w(TAG, "stop uvc error", e);
            }


            getSupportFragmentManager().beginTransaction()
                    .remove(uvc)
                    .commitAllowingStateLoss();

        }

        // 4️⃣ 隐藏 UVC 容器
        View uvcContainer = findViewById(R.id.uvc_container);
        if (uvcContainer != null) {
            uvcContainer.setVisibility(View.GONE);
        }

        // 5️⃣ 恢复主界面 UI
        previewView.setVisibility(View.GONE);
        btnExitPreview.setVisibility(View.GONE);
        tvCountdown.setVisibility(View.GONE);

        TextView uvcCountdown = findViewById(R.id.uvc_countdown);
        if (uvcCountdown != null) {
            uvcCountdown.setVisibility(View.GONE);
        }

        // 6️⃣ 恢复背景和按钮
        findViewById(R.id.bg_frame).setVisibility(View.VISIBLE);
        findViewById(R.id.center_icon).setVisibility(View.VISIBLE);
        findViewById(R.id.bottom_button_container).setVisibility(View.VISIBLE);
        findViewById(R.id.title_bar).setVisibility(View.VISIBLE);

        int[] iconIds = {
                R.id.btn_heart_rate, R.id.btn_positive_emotion,
                R.id.btn_negative_emotion, R.id.btn_hrvariability,
                R.id.btn_physiological, R.id.btn_psychological
        };
        for (int id : iconIds) {
            View v = findViewById(id);
            if (v != null) v.setVisibility(View.VISIBLE);
        }

        // 7️⃣ 状态复位
        isDetectionInProgress = false;
        shouldSaveAndUpload = false;
        DataSaver.setBloodPressure(-1, -1);
    }


    // ===================== BluetoothListener =====================
    @Override
    public void onBluetoothConnected(String deviceName, String deviceAddress) {
        runOnUiThread(() -> {
            btnStartDetection.setEnabled(true);
            btnStartDetection.setAlpha(1.0f);
            timeStamp.setBluetoothConnectTime(TimeUtils.getPreciseTimeStamp());
            Toast.makeText(this, "蓝牙已连接：" + deviceName, Toast.LENGTH_LONG).show();
//            tvBluetoothStatus.setText("蓝牙已连接：" + deviceName);
        });
    }

    @Override
    public void onBluetoothConnectFailed(String errorMsg) {
        runOnUiThread(() -> {
//            tvBluetoothStatus.setText("蓝牙连接失败：" + errorMsg);
            Toast.makeText(this, "蓝牙连接失败：" + errorMsg, Toast.LENGTH_LONG).show();
            stopDetectionEarly();
            runOnUiThread(() -> Toast.makeText(this, "连接失败：" + errorMsg, Toast.LENGTH_LONG).show());
        });
    }

    @Override
    public void onBluetoothDisconnected() {
        if (isDetectionInProgress) {

            isDetectionInProgress = false; // 立即标记为已终止
            shouldSaveAndUpload = false;

            // 停止视频和蓝牙
            if (videoRecorder != null) {
                videoRecorder.stopRecording();
            }
            // bluetoothService 已断开，但可显式清理
            if (bluetoothService != null) {
                bluetoothService.stopReceivingData();
            }

            // 切回主界面（不保存、不上传）
            runOnUiThread(() -> {
                previewView.setVisibility(View.GONE);
                btnExitPreview.setVisibility(View.GONE);
                stopDetectionEarly();
//                tvBluetoothStatus.setText("❌ 蓝牙连接意外断开，检测已终止，请重新连接");
                Toast.makeText(this, "蓝牙断开，检测已取消", Toast.LENGTH_LONG).show();
            });
        }
    }


    //收集数据
    @Override
    public void onDataReceived(String hexData) {
        oximeterData.addData(hexData);
    }

    @Override
    public void onDataStartReceiving(String startTime) {
        timeStamp.setBluetoothDataStartTime(startTime);
//        runOnUiThread(() -> tvStatus.append("\n蓝牙数据开始采集：" + startTime));
    }

    @Override
    public void onDataStopReceiving(String endTime) {
        timeStamp.setBluetoothDataEndTime(endTime);
    }

    // ===================== VideoListener =====================
    @Override
    public void onVideoStarted(String videoPath, String startTime) {
        videoFilePath = videoPath;
        timeStamp.setVideoStartTime(startTime);
        runOnUiThread(() -> {
            previewView.setVisibility(android.view.View.VISIBLE);
//            tvStatus.append("\n视频开始录制：" + startTime);
        });
    }

    @Override
    public void onVideoFinished(String videoPath, String endTime) {
        isDetectionInProgress = false;
        timeStamp.setVideoEndTime(endTime);
        bluetoothService.stopReceivingData(); // 同步停止蓝牙
        Fragment f = getSupportFragmentManager().findFragmentByTag(TAG_UVC);

        if (f instanceof MyCameraFragment) {
            ((MyCameraFragment) f).releaseUvc();
        }
        runOnUiThread(() -> {
            // 清理倒计时显示
            if (countDownTimer != null) {
                countDownTimer.cancel();
                countDownTimer = null;
            }
            tvCountdown.setVisibility(View.GONE);

            // === 恢复原界面 ===
            previewView.setVisibility(View.GONE);
            btnExitPreview.setVisibility(View.GONE);

            previewView.setVisibility(android.view.View.GONE);
//            tvStatus.append("\n视频录制完成：" + endTime);

            btnManualUpload.setEnabled(true);
            btnManualUpload.setAlpha(1.0f);
            // 本地保存
            // ✅ 只有 shouldSaveAndUpload 为 true 才保存和上传！
            if (shouldSaveAndUpload) {
                shouldSaveAndUpload=false; //也防止再次上传
                String report = "检测已完成！\n检测报告：\n" + oximeterData.generateReport();
//                tvDetectionResult.setText(report);
//                tvDetectionResult.setVisibility(View.VISIBLE);
                try {

                    DataSaver.saveAllData(this, videoPath, oximeterData, timeStamp);
                    Toast.makeText(this, "本地保存成功", Toast.LENGTH_SHORT).show();
                    // ✅ 构建报告并跳转到新页面
                    Intent intent = new Intent(this, ResultActivity.class);
                    intent.putExtra("REPORT", report);
                    intent.putExtra("VIDEO_PATH", videoPath);
                    startActivity(intent);
                } catch (Exception e) {

                    Toast.makeText(this, "本地保存失败", Toast.LENGTH_SHORT).show();
                }
                // 自动上传
//                uploadService.uploadAllData(oximeterData, videoPath, timeStamp, this);
            } else {
                // ❌ 被提前终止（蓝牙断开 / 用户退出），不保存
//                tvDetectionResult.setText("⚠️ 检测未正常完成，数据已丢弃");
//                tvDetectionResult.setVisibility(View.VISIBLE);
                Toast.makeText(this, "检测未完成，数据未保存", Toast.LENGTH_SHORT).show();
            }

        });
        DataSaver.setBloodPressure(-1, -1);
    }



    @Override
    public void onVideoError(String errorMsg) {
        runOnUiThread(() -> {
            DataSaver.setBloodPressure(-1, -1);
            new AlertDialog.Builder(this)
                    .setTitle("视频录制失败")
                    .setMessage(errorMsg)
                    .setPositiveButton("确定", (d, w) -> {
                        // 点击“确定”后不自动重试，仅关闭弹窗
                        d.dismiss();
                    })
                    .show();
        });
    }

    // ===================== UploadListener =====================
    @Override
    public void onUploadSuccess(String response) {
        runOnUiThread(() -> {

            DataSaver.setBloodPressure(-1, -1);
            Toast.makeText(this, "上传成功", Toast.LENGTH_SHORT).show();
        });
    }


    @Override
    public void onUploadFailed(String errorMsg) {
        runOnUiThread(() -> {
            Toast.makeText(this, "上传失败" + errorMsg, Toast.LENGTH_LONG).show();
        });
    }

    @Override
    public void onUploadProgress(int progress) {

    }


    private void checkAllPermissions() {
        boolean missing = false;
        for (String p : REQUIRED_PERMISSIONS) {
            if (ActivityCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                missing = true;
                break;
            }
        }
        if (missing) {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_ALL_PERMISSIONS);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_SELECT_DEVICE && resultCode == RESULT_OK && data != null) {
            String address = data.getStringExtra("DEVICE_ADDRESS");
            bluetoothService.connectToDevice(address);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (bluetoothService != null) bluetoothService.disconnect();
        if (videoRecorder != null) videoRecorder.releaseResources();
    }
}

