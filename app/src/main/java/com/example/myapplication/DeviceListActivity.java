package com.example.myapplication;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.example.myapplication.utils.BluetoothUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 蓝牙设备选择界面：扫描并显示血氧仪设备
 */
public class DeviceListActivity extends AppCompatActivity {
    // 日志标签
    private static final String TAG = "DeviceListActivity";

    // 血氧仪服务UUID（用于过滤设备，需与你的血氧仪硬件UUID匹配）
    private static final UUID OXIMETER_SERVICE_UUID =
            UUID.fromString("0000FFB0-0000-1000-8000-00805f9b34fb");

    // 扫描时长（10秒自动停止）
    private static final long SCAN_DURATION = 10000;

    // 权限请求码
    private static final int REQUEST_BLUETOOTH_PERMISSIONS = 100;

    // UI控件
    private TextView tvScanStatus;   // 扫描状态
    private ListView lvDevices;      // 设备列表
    private Button btnRescan;        // 重新扫描按钮

    // 蓝牙相关
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothLeScanner mBluetoothLeScanner;
    private Handler mHandler = new Handler();

    // 设备列表（存储蓝牙设备对象，避免重复添加）
    private List<BluetoothDevice> mDeviceList = new ArrayList<>();
    // 设备列表适配器（显示设备名称和地址，供用户选择）
    private ArrayAdapter<String> mDeviceAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device_list);

        // 初始化UI控件
        initView();

        // 初始化蓝牙（检查支持性、开启状态、扫描器）
        initBluetooth();

        // 初始化设备列表适配器（绑定ListView）
        initDeviceAdapter();

        // 绑定按钮点击事件（重新扫描）
        bindButtonEvents();
    }

    /**
     * 初始化UI控件：绑定布局中的控件ID
     */
    private void initView() {
        tvScanStatus = findViewById(R.id.tv_scan_status);
        lvDevices = findViewById(R.id.lv_devices);
        btnRescan = findViewById(R.id.btn_rescan);
    }

    /**
     * 初始化蓝牙：检查设备支持性、蓝牙状态、获取扫描器
     */
    private void initBluetooth() {
        // 1. 检查设备是否支持BLE（低功耗蓝牙，血氧仪常用）
        if (!BluetoothUtils.isBleSupported(this)) {
            Toast.makeText(this, "设备不支持低功耗蓝牙（BLE），无法使用", Toast.LENGTH_SHORT).show();
            finish(); // 不支持则关闭当前界面
            return;
        }

        // 2. 获取蓝牙适配器（蓝牙功能的核心入口）
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, "设备没有蓝牙功能，无法使用", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // 3. 检查蓝牙是否已开启（未开启则引导用户开启）
        if (!mBluetoothAdapter.isEnabled()) {
            BluetoothUtils.openBluetoothSettings(this); // 打开系统蓝牙设置界面
            finish(); // 用户操作后关闭当前界面，返回主界面重新进入
            return;
        }

        // 4. 获取BLE扫描器（用于扫描低功耗蓝牙设备）
        mBluetoothLeScanner = mBluetoothAdapter.getBluetoothLeScanner();
        if (mBluetoothLeScanner == null) {
            Toast.makeText(this, "无法获取BLE扫描器，无法扫描设备", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // 5. 检查并请求蓝牙扫描权限（Android 12+ 必需）
        checkBluetoothPermissions();
    }

    /**
     * 初始化设备列表适配器：将设备信息（名称+地址）显示在ListView中
     */
    private void initDeviceAdapter() {
        // 适配器布局：使用系统默认的简单列表项（一行文本）
        mDeviceAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, new ArrayList<>());
        lvDevices.setAdapter(mDeviceAdapter);

        // 设备列表点击事件：用户选择设备后，返回设备地址给主界面
        lvDevices.setOnItemClickListener((parent, view, position, id) -> {
            // 防止数组越界（position需在设备列表范围内）
            if (position < mDeviceList.size()) {
                BluetoothDevice selectedDevice = mDeviceList.get(position);
                // 携带设备地址返回主界面（主界面通过onActivityResult接收）
                Intent resultIntent = new Intent();
                resultIntent.putExtra("DEVICE_ADDRESS", selectedDevice.getAddress());
                setResult(RESULT_OK, resultIntent);
                finish(); // 关闭当前界面，返回主界面
            }
        });
    }

    /**
     * 绑定按钮事件：重新扫描按钮的点击逻辑
     */
    private void bindButtonEvents() {
        btnRescan.setOnClickListener(v -> {
            stopScan(); // 先停止当前扫描（避免重复扫描）
            clearDeviceList(); // 清空之前的设备列表
            startScan(); // 开始新的扫描
        });
    }

    /**
     * 检查并请求蓝牙扫描权限（Android 12+ 必须动态请求BLUETOOTH_SCAN）
     */
    private void checkBluetoothPermissions() {
        // 需要请求的权限列表（根据系统版本调整，这里适配Android 12+）
        List<String> missingPermissions = new ArrayList<>();
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
                != PackageManager.PERMISSION_GRANTED) {
            missingPermissions.add(Manifest.permission.BLUETOOTH_SCAN);
        }

        // 若有缺失的权限，动态请求
        if (!missingPermissions.isEmpty()) {
            ActivityCompat.requestPermissions(this,
                    missingPermissions.toArray(new String[0]), REQUEST_BLUETOOTH_PERMISSIONS);
        } else {
            // 权限已全部授予，直接开始扫描
            startScan();
        }
    }

    /**
     * 开始扫描蓝牙设备：过滤血氧仪设备（通过服务UUID），设置超时自动停止
     */
    private void startScan() {
        // 1. 再次检查权限（防止用户中途撤销权限）
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
                != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "缺少蓝牙扫描权限，无法扫描设备", Toast.LENGTH_SHORT).show();
            return;
        }

        // 2. 更新扫描状态提示
        tvScanStatus.setText("正在扫描血氧仪设备...（10秒后自动停止）");

        // 3. 设置扫描过滤：只扫描包含血氧仪服务UUID的设备（减少无关设备干扰）
        List<ScanFilter> scanFilters = new ArrayList<>();
        ScanFilter oximeterFilter = new ScanFilter.Builder()
                .setServiceUuid(new android.os.ParcelUuid(OXIMETER_SERVICE_UUID)) // 匹配血氧仪服务UUID
                .build();
        scanFilters.add(oximeterFilter);

        // 4. 设置扫描参数：低延迟模式（优先快速扫描到设备）
        ScanSettings scanSettings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY) // 低延迟，适合主动扫描
                .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES) // 所有匹配结果都回调
                .build();

        // 5. 开始扫描（传入过滤规则、参数、回调）
        mBluetoothLeScanner.startScan(scanFilters, scanSettings, mScanCallback);

        // 6. 设置扫描超时：10秒后自动停止扫描（避免耗电）
        mHandler.postDelayed(() -> {
            stopScan();
            // 更新扫描完成后的状态（无设备/有设备）
            if (mDeviceList.isEmpty()) {
                tvScanStatus.setText("扫描完成，未找到血氧仪设备（请确认设备已开机）");
            } else {
                tvScanStatus.setText(String.format("扫描完成，找到%d个血氧仪设备", mDeviceList.size()));
            }
        }, SCAN_DURATION);
    }

    /**
     * 停止扫描蓝牙设备：释放扫描资源，避免后台耗电
     */
    private void stopScan() {
        if (mBluetoothLeScanner != null) {
            // 检查权限（防止用户中途撤销权限导致崩溃）
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
                    != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            mBluetoothLeScanner.stopScan(mScanCallback); // 停止扫描并解绑回调
        }
    }

    /**
     * 清空设备列表：重新扫描前调用，避免显示旧设备
     */
    private void clearDeviceList() {
        mDeviceList.clear(); // 清空设备对象列表
        mDeviceAdapter.clear(); // 清空适配器数据（ListView会同步更新）
        tvScanStatus.setText("正在扫描血氧仪设备...（10秒后自动停止）");
    }

    /**
     * 蓝牙扫描回调：扫描到设备、扫描失败、扫描停止时触发
     */
    private final ScanCallback mScanCallback = new ScanCallback() {
        // 1. 扫描到单个设备时触发（核心回调，用于添加设备到列表）
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            BluetoothDevice device = result.getDevice(); // 获取扫描到的蓝牙设备
            if (device != null) {
                addDeviceToList(device); // 将设备添加到列表（避免重复）
            }
        }

        // 2. 批量扫描结果回调（部分设备会批量返回结果，需处理）
        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            super.onBatchScanResults(results);
            for (ScanResult result : results) {
                BluetoothDevice device = result.getDevice();
                if (device != null) {
                    addDeviceToList(device);
                }
            }
        }

        // 3. 扫描失败时触发（如权限被拒、蓝牙关闭）
        @Override
        public void onScanFailed(int errorCode) {
            super.onScanFailed(errorCode);
            String errorMsg;
            switch (errorCode) {
                case SCAN_FAILED_ALREADY_STARTED:
                    errorMsg = "扫描已在进行中";
                    break;
                case SCAN_FAILED_APPLICATION_REGISTRATION_FAILED:
                    errorMsg = "应用注册失败（无法使用蓝牙）";
                    break;
                case SCAN_FAILED_INTERNAL_ERROR:
                    errorMsg = "蓝牙内部错误（请重启蓝牙）";
                    break;
                case SCAN_FAILED_FEATURE_UNSUPPORTED:
                    errorMsg = "设备不支持当前扫描模式";
                    break;
                default:
                    errorMsg = "扫描失败（错误码：" + errorCode + "）";
                    break;
            }
            tvScanStatus.setText("扫描失败：" + errorMsg);
            Toast.makeText(DeviceListActivity.this, "扫描失败：" + errorMsg, Toast.LENGTH_SHORT).show();
        }
    };

    /**
     * 将设备添加到列表：避免重复添加同一设备（通过设备地址判断）
     */
    private void addDeviceToList(BluetoothDevice device) {
        // 1. 检查设备是否已在列表中（通过设备唯一地址判断，避免重复）
        boolean isDeviceExists = false;
        for (BluetoothDevice existingDevice : mDeviceList) {
            if (existingDevice.getAddress().equals(device.getAddress())) {
                isDeviceExists = true;
                break;
            }
        }

        // 2. 若设备不在列表中，添加到列表并更新适配器
        if (!isDeviceExists) {
            mDeviceList.add(device); // 添加设备对象到列表
            // 构建显示文本：设备名称（无名称则显示"未知设备"）+ 设备地址
            String deviceInfo = (device.getName() != null && !device.getName().isEmpty())
                    ? device.getName() + "（" + device.getAddress() + "）"
                    : "未知设备（" + device.getAddress() + "）";
            mDeviceAdapter.add(deviceInfo); // 添加显示文本到适配器（ListView自动刷新）
        }
    }

    /**
     * 权限请求结果回调：用户授予/拒绝权限后触发
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_BLUETOOTH_PERMISSIONS) {
            // 检查所有请求的权限是否都被授予
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }

            if (allGranted) {
                // 权限全部授予，开始扫描
                startScan();
            } else {
                // 权限被拒绝，提示用户并关闭界面
                tvScanStatus.setText("蓝牙扫描权限被拒绝，无法扫描设备");
                Toast.makeText(this, "请授予蓝牙扫描权限以继续", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    /**
     * 生命周期：暂停时停止扫描（避免后台耗电）
     */
    @Override
    protected void onPause() {
        super.onPause();
        stopScan();
    }

    /**
     * 生命周期：销毁时释放资源（避免内存泄漏）
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopScan(); // 停止扫描
        mHandler.removeCallbacksAndMessages(null); // 移除所有延迟任务
        mDeviceList.clear(); // 清空设备列表
        mDeviceAdapter.clear(); // 清空适配器
    }
}