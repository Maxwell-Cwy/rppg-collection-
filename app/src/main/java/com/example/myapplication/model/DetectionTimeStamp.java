package com.example.myapplication.model;

/**
 * 时间戳模型：记录所有关键时间点
 */
public class DetectionTimeStamp {
    // 蓝牙连接成功时间戳
    private String bluetoothConnectTime;
    // 视频录制开始时间戳
    private String videoStartTime;
    // 蓝牙数据开始检测时间戳
    private String bluetoothDataStartTime;

    // 新增结束时间戳
    private String bluetoothDataEndTime;
    private String videoEndTime;

    // 空构造
    public DetectionTimeStamp() {}

    // Getter & Setter
    public String getBluetoothConnectTime() {
        return bluetoothConnectTime;
    }

    public void setBluetoothConnectTime(String bluetoothConnectTime) {
        this.bluetoothConnectTime = bluetoothConnectTime;
    }

    public String getVideoStartTime() {
        return videoStartTime;
    }

    public void setVideoStartTime(String videoStartTime) {
        this.videoStartTime = videoStartTime;
    }

    public String getBluetoothDataStartTime() {
        return bluetoothDataStartTime;
    }

    public void setBluetoothDataStartTime(String bluetoothDataStartTime) {
        this.bluetoothDataStartTime = bluetoothDataStartTime;
    }


    // Getter & Setter for new fields
    public String getBluetoothDataEndTime() {
        return bluetoothDataEndTime;
    }

    public void setBluetoothDataEndTime(String bluetoothDataEndTime) {
        this.bluetoothDataEndTime = bluetoothDataEndTime;
    }

    public String getVideoEndTime() {
        return videoEndTime;
    }

    public void setVideoEndTime(String videoEndTime) {
        this.videoEndTime = videoEndTime;
    }

    public void clear() {
//        bluetoothConnectTime = null;
        bluetoothDataStartTime = null;
        bluetoothDataEndTime = null;
        videoStartTime = null;
        videoEndTime = null;
    }

    @Override
    public String toString() {
        return "蓝牙连接成功: " + bluetoothConnectTime + "\n" +
                "视频开始录制: " + videoStartTime + "\n" +
                "数据开始检测: " + bluetoothDataStartTime + "\n" +
//                "蓝牙数据结束: " + bluetoothDataEndTime + "\n" +  // 新增
                "视频结束: " + videoEndTime;  // 新增
    }
}