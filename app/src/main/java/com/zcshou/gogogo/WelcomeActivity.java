package com.zcshou.gogogo;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

import com.zcshou.utils.GoUtils;

@SuppressLint("Registered")
public class WelcomeActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_welcome);

        Button startBtn = findViewById(R.id.startButton);
        startBtn.setOnClickListener(v -> startMainActivity());
    }

    private void startMainActivity() {
        if (!GoUtils.isNetworkAvailable(this)) {
            GoUtils.DisplayToast(this, getResources().getString(R.string.app_error_network));
            return;
        }
        Intent intent = new Intent(WelcomeActivity.this, MainActivity.class);
        startActivity(intent);
        WelcomeActivity.this.finish();
    }
}
