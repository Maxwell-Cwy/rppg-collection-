package com.example.myapplication;

import android.app.Activity;
import android.hardware.usb.UsbDevice;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

// 1. 核心库基类
import androidx.annotation.NonNull;

import com.jiangdg.ausbc.MultiCameraClient;
import com.jiangdg.ausbc.base.CameraFragment;

// 2. 渲染视图相关接口和实现
import com.jiangdg.ausbc.camera.bean.PreviewSize;
import com.jiangdg.ausbc.widget.AspectRatioTextureView;
import com.jiangdg.ausbc.widget.IAspectRatio;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class MyCameraFragment extends CameraFragment {

    private static final String TAG = "MyCameraFragment";

    // 渲染控件
    private AspectRatioTextureView mCameraView;

    private UsbDevice mTargetDevice; // 新增：目标设备
    /**
     * 实现 1: 加载布局
     */
    @Override
    protected View getRootView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return inflater.inflate(R.layout.fragment_my_camera, container, false);
    }

    /**
     * 实现 2: 返回预览控件
     * 库会自动把摄像头流绑定到这个 View 上
     */
    @Override
    protected IAspectRatio getCameraView() {
        if (mCameraView == null) {
            mCameraView = new AspectRatioTextureView(requireContext());
        } else {
            // ⭐ 防止重复 attach
            ViewGroup parent = (ViewGroup) mCameraView.getParent();
            if (parent != null) {
                parent.removeView(mCameraView);
            }
        }
        return mCameraView;
    }

    /**
     * 实现 3: 确定容器
     * 库会将上面的 getCameraView() 添加到这个容器里
     */
    @Override
    protected ViewGroup getCameraViewContainer() {
        View root = getView();
        if (root == null) return null;
        return root.findViewById(R.id.camera_container);
    }

    /**
     * 状态监听
     */
    @Override
    public void onCameraState(@NonNull MultiCameraClient.ICamera self,
                              @NonNull State code,
                              @Nullable String msg) {



        Log.d(TAG, "onCameraState: " + code + " msg:" + msg);

        if (code == State.OPENED) {
            Log.i(TAG, "UVC Camera OPENED");

            // 打印该接口支持的所有分辨率和格式
            List<com.jiangdg.ausbc.camera.bean.PreviewSize> sizes = self.getAllPreviewSizes(4.0/3.0);
            for (com.jiangdg.ausbc.camera.bean.PreviewSize s : sizes) {
                Log.i("UVC_DUMP", "支持的分辨率: " + s.getWidth() + "x" + s.getHeight());
            }

            UsbDevice openedDevice = self.getUsbDevice();  // ← 看库是否有这个方法获取当前打开的设备
            if (openedDevice != null) {
                Log.w(TAG, "实际打开的设备: " + openedDevice.getProductName()
                        + " | path: " + openedDevice.getDeviceName()
                        + " | PID: " + openedDevice.getProductId());
            } else {
                Log.w(TAG, "库没有提供 getUsbDevice() 方法，无法确认打开的是哪个");
            }

            Activity act = getActivity();
            if (act instanceof StartActivity) {
                // ⭐ 等画面真正出来
                new Handler(Looper.getMainLooper())
                        .postDelayed(() -> {
                            ((StartActivity) act).onUvcPreviewReady();
                        }, 400); // 300~500ms
            }
        }

    }



    // ⭐ 对 Activity 暴露的「开始录制」方法
    public void startRecord(String videoPath) {
        captureVideoStart(
                new com.jiangdg.ausbc.callback.ICaptureCallBack() {
                    @Override
                    public void onBegin() {
                        Log.d(TAG, "开始录制");
                    }

                    @Override
                    public void onError(String s) {
                        Log.e(TAG, "录制错误: " + s);
                    }

                    @Override
                    public void onComplete(String s) {
                        Log.d(TAG, "录制完成: " + s);
                    }
                },
                videoPath,
                90_000L   // ⭐ 第三个参数必须有：时长（毫秒）
        );
    }


    // ⭐ 对 Activity 暴露的「停止录制」方法
    public void stopRecord() {
        captureVideoStop(); // protected → 子类里合法
    }

    public void releaseUvc() {
        try {
            stopRecord();      // 停止录像（你已经在用）
            closeCamera();     // ✅ protected，在子类里能调
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    // 新增：供 Activity 调用，设置选中的摄像头
    public void setTargetDevice(UsbDevice device) {
        this.mTargetDevice = device;
    }


    @Override
    protected UsbDevice getDefaultCamera() {
        if (mTargetDevice != null) {
            Log.i(TAG, "强制返回目标 UVC 设备: " + mTargetDevice.getProductName()
                    + " | path: " + mTargetDevice.getDeviceName());
            return mTargetDevice;
        }
        // 兜底：返回 super 或 null，让库用默认逻辑
        return super.getDefaultCamera();
    }




    /**
     * 重写库的方法：明确告诉库要打开哪一个设备
     * 如果返回 null，库会默认打开第一个搜到的 UVC
     */
// ⭐ 核心修复：这个方法必须存在且带 @Override
    // 库在 Open 摄像头之前会调用此方法，如果不重写，它永远打开第一个(RGB)

    /**
     * 核心修改：通过 CameraRequest 指定设备
     */
    /**

     * 核心修改：通过 CameraRequest 指定设备

     */
// ⭐ 核心修正：既然没有 getFilterDevice，我们直接在请求中锁定设备路径
    @NotNull
    @Override
    protected com.jiangdg.ausbc.camera.bean.CameraRequest getCameraRequest() {

        String targetPath = "/dev/bus/usb/002/004";  // ← 写死 NIR 的路径，测试用

        return new com.jiangdg.ausbc.camera.bean.CameraRequest.Builder()
                .setFrontCamera(false)
                .setPreviewWidth(640)
                .setPreviewHeight(480)
                .setCameraId(targetPath) // 锁定物理路径
                .create();
    }


    private String mRequestedId = "0"; // 新增变量

    public void setRequestedId(String id) {
        this.mRequestedId = id;
    }



}