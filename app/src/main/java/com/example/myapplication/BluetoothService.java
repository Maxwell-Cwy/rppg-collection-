package com.example.myapplication;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;   // 新增这行！
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import androidx.core.app.ActivityCompat;
import com.example.myapplication.model.OximeterData;
import com.example.myapplication.utils.HexUtils;
import com.example.myapplication.utils.BluetoothUtils;
import com.example.myapplication.utils.TimeUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class BluetoothService {
    private static final String TAG = "BluetoothService";

    // 血氧仪协议UUID
    private static final UUID OXIMETER_SERVICE_UUID =
            UUID.fromString("0000FFB0-0000-1000-8000-00805f9b34fb");
    private static final UUID OXIMETER_CHARACTERISTIC_UUID =
            UUID.fromString("0000FFB2-0000-1000-8000-00805f9b34fb");
    private static final UUID CLIENT_CONFIG_DESCRIPTOR_UUID =
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");


    // 协议命令（全部加了强制转换，彻底解决 byte 报错）
    private static final byte[] DEVICE_READY_CMD = {
            (byte) 0xFF, (byte) 0xFE, 0x04, (byte) 0x87, 0x22, 0x61
    };
    private static final byte[] START_MEASURE_CMD = {
            (byte) 0xFF, (byte) 0xFE, 0x04, (byte) 0xB5, 0x01, (byte) 0xB0
    };

    private final Context mContext;
    private final BluetoothListener mListener;
    private final BluetoothAdapter mBluetoothAdapter;
    private BluetoothGatt mBluetoothGatt;
    private BluetoothGattCharacteristic mCharacteristic;
    private final OximeterData mOximeterData;
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    private boolean isReceivingData = false;
    private boolean isConnected = false;
    private String bluetoothDataStartTime;
    private String bluetoothDataEndTime;

    private static Integer index=0;

    public interface BluetoothListener {
        void onBluetoothConnected(String deviceName, String deviceAddress);
        void onBluetoothConnectFailed(String errorMsg);
        void onBluetoothDisconnected();
        void onDataReceived(String hexData);
        void onDataStartReceiving(String startTime);  // 只保留这个有参数版本
        void onDataStopReceiving(String endTime);
    }

    public BluetoothService(Context context, BluetoothListener listener) {
        this.mContext = context;
        this.mListener = listener;
        this.mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        this.mOximeterData = new OximeterData();
    }

    public void connectToDevice(String deviceAddress) {
        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            mListener.onBluetoothConnectFailed("蓝牙未开启，请先开启蓝牙");
            return;
        }

        if (!BluetoothUtils.isBluetoothAddressValid(deviceAddress)) {
            mListener.onBluetoothConnectFailed("蓝牙地址不合法");
            return;
        }

        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(deviceAddress);
        if (device == null) {
            mListener.onBluetoothConnectFailed("无法获取蓝牙设备");
            return;
        }

        mMainHandler.post(() -> {
            if (ActivityCompat.checkSelfPermission(mContext, android.Manifest.permission.BLUETOOTH_CONNECT)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                mListener.onBluetoothConnectFailed("缺少蓝牙连接权限");
                return;
            }
            mBluetoothGatt = device.connectGatt(mContext, false, mGattCallback);
        });
    }

    public void startReceivingData() {
        if (!isConnected || mCharacteristic == null) {
            mListener.onBluetoothConnectFailed("蓝牙未连接，无法开始接收数据");
            return;
        }

//        new Thread(() -> {
//            try {
//                // 发送100个0x00唤醒设备
//                for (int i = 0; i < 100; i++) {
//                    sendData(new byte[]{0x00});
//                    Thread.sleep(300);
//                }
//
//                sendData(DEVICE_READY_CMD);
//                Thread.sleep(500);
//
//                sendData(START_MEASURE_CMD);
//                Thread.sleep(500);
//
//                isReceivingData = true;  // 只设置一次
//
//            } catch (InterruptedException e) {
//                Log.e(TAG, "发送命令线程中断: " + e.getMessage());
//                mMainHandler.post(() -> mListener.onBluetoothConnectFailed("发送测量命令失败"));
//            }
//        }).start();
        isReceivingData = true;
        bluetoothDataStartTime = TimeUtils.getPreciseTimeStamp();  // 记录开始
        Log.e("Time","记录时间："+bluetoothDataStartTime);
        mMainHandler.post(() -> mListener.onDataStartReceiving(bluetoothDataStartTime));  // 只调用有参数版本
    }


    // 新增停止方法
    public void stopReceivingData() {
        bluetoothDataEndTime = TimeUtils.getPreciseTimeStamp();  // 记录结束
        isReceivingData = false;
        // 可发送停止命令给设备（如果协议支持）
        sendData(new byte[] { /* 停止命令 */ });
        mMainHandler.post(() -> mListener.onDataStopReceiving(bluetoothDataEndTime));  // 新回调
    }

    private void sendData(byte[] data) {
        if (mBluetoothGatt == null || mCharacteristic == null) {
            Log.e(TAG, "发送数据失败：GATT或特征值为空");
            return;
        }

        if (ActivityCompat.checkSelfPermission(mContext, android.Manifest.permission.BLUETOOTH_CONNECT)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            return;
        }

        mCharacteristic.setValue(data);
        mBluetoothGatt.writeCharacteristic(mCharacteristic);
    }

    public void disconnect() {
        if (mBluetoothGatt != null) {
            if (ActivityCompat.checkSelfPermission(mContext, android.Manifest.permission.BLUETOOTH_CONNECT)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                return;
            }
            mBluetoothGatt.disconnect();
            mBluetoothGatt.close();
            mBluetoothGatt = null;
        }
        isConnected = false;
        isReceivingData = false;
        mCharacteristic = null;
    }

    public OximeterData getCollectedData() {
        return mOximeterData;
    }

    public boolean isConnected() {
        return isConnected;
    }

    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                if (ActivityCompat.checkSelfPermission(mContext, android.Manifest.permission.BLUETOOTH_CONNECT)
                        != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    return;
                }
                gatt.discoverServices();

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                isConnected = false;
                mMainHandler.post(mListener::onBluetoothDisconnected);
                gatt.close();
                mBluetoothGatt = null;
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);

            if (status != BluetoothGatt.GATT_SUCCESS) {
                mMainHandler.post(() -> mListener.onBluetoothConnectFailed("服务发现失败，错误码：" + status));
                return;
            }

            BluetoothGattService service = gatt.getService(OXIMETER_SERVICE_UUID);
            if (service == null) {
                mMainHandler.post(() -> mListener.onBluetoothConnectFailed("未找到血氧仪服务"));
                return;
            }

            mCharacteristic = service.getCharacteristic(OXIMETER_CHARACTERISTIC_UUID);
            if (mCharacteristic == null) {
                mMainHandler.post(() -> mListener.onBluetoothConnectFailed("未找到血氧仪特征值"));
                return;
            }

            setCharacteristicNotification(true);
            isConnected = true;

            String deviceName = gatt.getDevice().getName();
            String deviceAddress = gatt.getDevice().getAddress();
            String displayName = (deviceName == null || deviceName.isEmpty())
                    ? "未知血氧仪设备" : deviceName;

            mMainHandler.post(() -> mListener.onBluetoothConnected(displayName, deviceAddress));
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicChanged(gatt, characteristic);

            byte[] data = characteristic.getValue();
            if (data != null && data.length > 0) {
                String hexData = HexUtils.bytesToHex(data);

                // 只有在正式开始检测后才显示到界面
                if (isReceivingData) {
                    mMainHandler.post(() -> mListener.onDataReceived(hexData));
                    index++;
                    Log.w("数据总数：","第"+index+":"+hexData);
                }

            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e(TAG, "特征值写入失败，错误码：" + status);
            }
        }
    };

    // 关键修复：使用 BluetoothGattDescriptor 完整类名 + 去掉 var
    private void setCharacteristicNotification(boolean enable) {
        if (mBluetoothGatt == null || mCharacteristic == null) {
            return;
        }

        if (ActivityCompat.checkSelfPermission(mContext, android.Manifest.permission.BLUETOOTH_CONNECT)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            return;
        }

        mBluetoothGatt.setCharacteristicNotification(mCharacteristic, enable);

        BluetoothGattDescriptor descriptor = mCharacteristic.getDescriptor(CLIENT_CONFIG_DESCRIPTOR_UUID);
        if (descriptor != null) {
            descriptor.setValue(enable
                    ? BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    : BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
            mBluetoothGatt.writeDescriptor(descriptor);
        }
    }
}