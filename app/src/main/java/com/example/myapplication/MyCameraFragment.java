package com.example.myapplication;

import android.app.Activity;
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
import com.jiangdg.ausbc.widget.AspectRatioTextureView;
import com.jiangdg.ausbc.widget.IAspectRatio;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MyCameraFragment extends CameraFragment {

    private static final String TAG = "MyCameraFragment";

    // 渲染控件
    private AspectRatioTextureView mCameraView;


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




}