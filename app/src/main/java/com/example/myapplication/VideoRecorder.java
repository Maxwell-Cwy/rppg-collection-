package com.example.myapplication;
import android.widget.Toast;
import android.content.Context;
import android.util.Log;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.video.FileOutputOptions;
import androidx.camera.video.Quality;
import androidx.camera.video.QualitySelector;
import androidx.camera.video.Recorder;
import androidx.camera.video.Recording;
import androidx.camera.video.VideoCapture;
import androidx.camera.video.VideoRecordEvent;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;
import com.example.myapplication.model.DetectionTimeStamp;
import com.example.myapplication.model.OximeterData;
import com.example.myapplication.utils.DataSaver;
import com.example.myapplication.utils.TimeUtils;
import com.google.common.util.concurrent.ListenableFuture;
import java.io.File;

import android.os.Environment;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;

public class VideoRecorder {

    private final Context context;
    private final VideoListener listener;
    private final PreviewView previewView;

    private VideoCapture<Recorder> videoCapture;
    private Recording currentRecording;



    public interface VideoListener {
        void onVideoStarted(String videoPath, String startTime);
        void onVideoFinished(String videoPath, String endTime);  // 修改：添加endTime
        void onVideoError(String errorMsg);
    }

    public VideoRecorder(Context context, VideoListener listener, PreviewView previewView) {
        this.context = context;
        this.listener = listener;
        this.previewView = previewView;
    }


    public void startRecording(long durationMillis) {
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(context);
        future.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = future.get();

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                Recorder recorder = new Recorder.Builder()
                        .setQualitySelector(QualitySelector.from(Quality.HD))
                        .build();
                videoCapture = VideoCapture.withOutput(recorder);

                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(
                        (LifecycleOwner) context,
                        CameraSelector.DEFAULT_FRONT_CAMERA,
                        preview,
                        videoCapture
                );

                File dir = new File(context.getExternalFilesDir(null), "OximeterRecords");
                if (!dir.exists()) dir.mkdirs();

                String timeFolderName = TimeUtils.getSimpleTimeStamp(); // 2025-04-05_15-32-28
                File timeDir = new File(dir, timeFolderName);
                timeDir.mkdirs();

                File videoFile = new File(timeDir, "checkVideo.mp4");


                FileOutputOptions options = new FileOutputOptions.Builder(videoFile).build();

                currentRecording = videoCapture.getOutput()
                        .prepareRecording(context, options)
                        .start(ContextCompat.getMainExecutor(context), recordEvent -> {
                            if (recordEvent instanceof VideoRecordEvent.Start) {
                                String startTime = TimeUtils.getPreciseTimeStamp();
                                //获取视频绝对路径
                                listener.onVideoStarted(videoFile.getAbsolutePath(), startTime);
                                new android.os.Handler(android.os.Looper.getMainLooper())
                                        .postDelayed(() -> {
                                            stopRecording();
                                            // 校验时长（可选：读取文件元数据或计算时间差）
                                            long actualDuration = System.currentTimeMillis() - TimeUtils.parseTimeToMillis(startTime);
                                            Log.w("时差","毫秒:"+actualDuration);
                                            if (Math.abs(actualDuration - durationMillis) > 2000) {  // 允许1s偏差
                                                listener.onVideoError("视频时长不符: " + (actualDuration / 1000) + "秒");
                                            }
                                        }, durationMillis);

                            } else if (recordEvent instanceof VideoRecordEvent.Finalize) {
                                VideoRecordEvent.Finalize finalizeEvent = (VideoRecordEvent.Finalize) recordEvent;
                                if (finalizeEvent.hasError()) {
                                    listener.onVideoError("录制失败：" + finalizeEvent.getError());
                                } else {
                                    String path = videoFile.getAbsolutePath();
                                    String endTime = TimeUtils.getPreciseTimeStamp();
                                    listener.onVideoFinished(path,endTime);
                                }
                                currentRecording = null;
                            }
                        });

            } catch (Exception e) {
                e.printStackTrace();
                listener.onVideoError("启动失败：" + e.getMessage());
            }
        }, ContextCompat.getMainExecutor(context));
    }


    public void stopRecording() {
        if (currentRecording != null) {
            String endTime = TimeUtils.getPreciseTimeStamp();  // 记录结束时间
            currentRecording.stop();
            currentRecording = null;
        }
    }

    public boolean isRecording() {
        return currentRecording != null;
    }

    public void releaseResources() {
        stopRecording();
    }


}