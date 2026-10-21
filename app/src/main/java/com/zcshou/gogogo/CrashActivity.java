package com.zcshou.gogogo;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * 崩溃信息展示页：全局未捕获异常时弹出，显示完整堆栈，便于截图排查
 */
public class CrashActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String error = getIntent().getStringExtra("error");
        if (error == null || error.isEmpty()) {
            error = "(无崩溃信息)";
        }

        ScrollView scrollView = new ScrollView(this);
        scrollView.setBackgroundColor(0xFF1A1A2E);

        TextView textView = new TextView(this);
        textView.setText(error);
        textView.setTextColor(0xFFFF6B6B);
        textView.setTextSize(11);
        textView.setPadding(40, 60, 40, 60);
        textView.setGravity(Gravity.START);
        textView.setLineSpacing(4f, 1.1f);

        scrollView.addView(textView);
        setContentView(scrollView);
    }
}
