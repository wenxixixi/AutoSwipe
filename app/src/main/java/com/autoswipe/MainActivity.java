package com.autoswipe;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

/**
 * 主界面 - 权限引导
 * 引导用户开启无障碍服务和悬浮窗权限
 */
public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        TextView tvAccessibilityStatus = findViewById(R.id.tv_accessibility_status);
        TextView tvOverlayStatus = findViewById(R.id.tv_overlay_status);
        Button btnOpenAccessibility = findViewById(R.id.btn_open_accessibility);
        Button btnOpenOverlay = findViewById(R.id.btn_open_overlay);
        Button btnStartService = findViewById(R.id.btn_start_service);

        // 检查无障碍服务状态
        updateAccessibilityStatus(tvAccessibilityStatus);

        // 检查悬浮窗权限状态
        updateOverlayStatus(tvOverlayStatus);

        // 打开无障碍服务设置
        btnOpenAccessibility.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            startActivity(intent);
        });

        // 打开悬浮窗权限设置
        btnOpenOverlay.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            }
        });

        // 启动悬浮窗服务
        btnStartService.setOnClickListener(v -> {
            if (!isAccessibilityServiceEnabled()) {
                // 如果无障碍服务未开启，先引导开启
                Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                startActivity(intent);
                return;
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                // 如果悬浮窗权限未开启，先引导开启
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivity(intent);
                return;
            }

            // 启动悬浮窗服务
            Intent serviceIntent = new Intent(this, FloatingWindowService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }

            // 返回桌面
            Intent homeIntent = new Intent(Intent.ACTION_MAIN);
            homeIntent.addCategory(Intent.CATEGORY_HOME);
            homeIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(homeIntent);

            Toast.makeText(this, "悬浮窗已启动，请打开目标应用", Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateAccessibilityStatus(findViewById(R.id.tv_accessibility_status));
        updateOverlayStatus(findViewById(R.id.tv_overlay_status));
    }

    /**
     * 检查无障碍服务是否已开启
     */
    private boolean isAccessibilityServiceEnabled() {
        int enabled = 0;
        try {
            enabled = Settings.Secure.getInt(
                    getContentResolver(),
                    Settings.Secure.ACCESSIBILITY_ENABLED
            );
        } catch (Settings.SettingNotFoundException e) {
            // 未找到设置
        }
        return enabled == 1;
    }

    /**
     * 更新无障碍服务状态显示
     */
    private void updateAccessibilityStatus(TextView tv) {
        if (isAccessibilityServiceEnabled()) {
            tv.setText("✅ 无障碍服务已开启");
            tv.setTextColor(0xFF4CAF50);
        } else {
            tv.setText("❌ 无障碍服务未开启");
            tv.setTextColor(0xFFF44336);
        }
    }

    /**
     * 更新悬浮窗权限状态显示
     */
    private void updateOverlayStatus(TextView tv) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(this)) {
            tv.setText("✅ 悬浮窗权限已开启");
            tv.setTextColor(0xFF4CAF50);
        } else {
            tv.setText("❌ 悬浮窗权限未开启");
            tv.setTextColor(0xFFF44336);
        }
    }
}
