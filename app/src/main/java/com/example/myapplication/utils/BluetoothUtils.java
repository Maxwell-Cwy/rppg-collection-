package com.example.myapplication.utils;

import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

/**
 * 蓝牙工具类：检查蓝牙支持、状态等
 */
public class BluetoothUtils {
    /**
     * 检查设备是否支持BLE（低功耗蓝牙）
     */
    public static boolean isBleSupported(Context context) {
        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE);
    }

    /**
     * 检查蓝牙是否已开启
     */
    public static boolean isBluetoothEnabled() {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        return adapter != null && adapter.isEnabled();
    }

    /**
     * 打开蓝牙设置界面（请求用户开启蓝牙）
     */
    public static void openBluetoothSettings(Context context) {
        Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        context.startActivity(intent);
    }

    /**
     * 检查蓝牙地址是否合法
     */
    public static boolean isBluetoothAddressValid(String address) {
        if (address == null || address.length() != 17) {
            return false;
        }
        return address.matches("^([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})$");
    }
}