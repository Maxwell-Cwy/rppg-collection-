// ResultActivity.java
package com.example.myapplication;

import android.os.Bundle;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class ResultActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_result);

        TextView tvResult = findViewById(R.id.tv_result);
        String report = getIntent().getStringExtra("REPORT");
        String videoPath = getIntent().getStringExtra("VIDEO_PATH");

        StringBuilder sb = new StringBuilder();
        sb.append("✅ 检测已完成！\n\n");
        if (report != null) sb.append(report).append("\n\n");
        if (videoPath != null) sb.append("视频路径：").append(videoPath);

        tvResult.setText(sb.toString());
    }
}