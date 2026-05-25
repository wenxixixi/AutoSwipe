package com.autoswipe;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

/**
 * 悬浮窗服务 - 提供控制面板
 * 可拖动的悬浮窗，包含开始/停止按钮和参数设置
 */
public class FloatingWindowService extends Service {

    private static final String TAG = "FloatingWindow";
    private static final String CHANNEL_ID = "autoswipe_channel";
    private static final int NOTIFICATION_ID = 1;

    private WindowManager windowManager;
    private View floatingView;
    private WindowManager.LayoutParams params;

    // 控件
    private Button btnStart;
    private Button btnStop;
    private Button btnClose;
    private EditText etCount;
    private EditText etInterval;
    private TextView tvStatus;
    private TextView tvProgress;

    // 状态
    private boolean isExpanded = true;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, buildNotification());
        createFloatingWindow();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (floatingView != null) {
            windowManager.removeView(floatingView);
        }
        SwipeAccessibilityService.setCallback(null);
    }

    /**
     * 创建通知渠道（Android 8.0+）
     */
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "自动滑动服务",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("自动滑动功能正在运行");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    /**
     * 构建前台通知
     */
    private Notification buildNotification() {
        return new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("自动滑动")
                .setContentText("服务运行中")
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .build();
    }

    /**
     * 创建悬浮窗
     */
    private void createFloatingWindow() {
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        // 加载悬浮窗布局
        LayoutInflater inflater = LayoutInflater.from(this);
        floatingView = inflater.inflate(R.layout.floating_window, null);

        // 设置悬浮窗参数
        int type;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            type = WindowManager.LayoutParams.TYPE_PHONE;
        }

        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );

        params.gravity = Gravity.TOP | Gravity.END;
        params.x = 16;
        params.y = 200;

        // 添加到窗口
        windowManager.addView(floatingView, params);

        // 初始化控件
        initViews(floatingView);

        // 设置拖动功能
        setupDrag(floatingView);

        // 设置回调
        setupSwipeCallback();
    }

    /**
     * 初始化控件引用和事件
     */
    private void initViews(View view) {
        btnStart = view.findViewById(R.id.btn_start);
        btnStop = view.findViewById(R.id.btn_stop);
        btnClose = view.findViewById(R.id.btn_close);
        etCount = view.findViewById(R.id.et_count);
        etInterval = view.findViewById(R.id.et_interval);
        tvStatus = view.findViewById(R.id.tv_status);
        tvProgress = view.findViewById(R.id.tv_progress);

        // 默认值
        etCount.setText("100");
        etInterval.setText("2");

        // 开始按钮
        btnStart.setOnClickListener(v -> {
            SwipeAccessibilityService service = SwipeAccessibilityService.getInstance();
            if (service == null) {
                Toast.makeText(this, "请先开启无障碍服务！", Toast.LENGTH_SHORT).show();
                return;
            }

            try {
                int count = Integer.parseInt(etCount.getText().toString().trim());
                long interval = (long) (Float.parseFloat(etInterval.getText().toString().trim()) * 1000);

                if (count <= 0 || interval <= 0) {
                    Toast.makeText(this, "请输入有效的数值", Toast.LENGTH_SHORT).show();
                    return;
                }

                service.startSwipe(count, interval);
                updateUI(true, 0, count);
            } catch (NumberFormatException e) {
                Toast.makeText(this, "请输入有效的数值", Toast.LENGTH_SHORT).show();
            }
        });

        // 停止按钮
        btnStop.setOnClickListener(v -> {
            SwipeAccessibilityService service = SwipeAccessibilityService.getInstance();
            if (service != null) {
                service.stopSwipe();
                updateUI(false, 0, 0);
            }
        });

        // 关闭按钮
        btnClose.setOnClickListener(v -> {
            SwipeAccessibilityService service = SwipeAccessibilityService.getInstance();
            if (service != null) {
                service.stopSwipe();
            }
            stopSelf();
        });
    }

    /**
     * 设置拖动功能
     */
    private void setupDrag(View view) {
        View dragHandle = view.findViewById(R.id.drag_handle);

        dragHandle.setOnTouchListener(new View.OnTouchListener() {
            private int initialX;
            private int initialY;
            private float initialTouchX;
            private float initialTouchY;
            private long startTime;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = params.x;
                        initialY = params.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        startTime = System.currentTimeMillis();
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        params.x = initialX + (int) (event.getRawX() - initialTouchX);
                        params.y = initialY + (int) (event.getRawY() - initialTouchY);
                        windowManager.updateViewLayout(floatingView, params);
                        return true;

                    case MotionEvent.ACTION_UP:
                        // 短按切换展开/收起
                        if (System.currentTimeMillis() - startTime < 200) {
                            toggleExpand();
                        }
                        return true;
                }
                return false;
            }
        });
    }

    /**
     * 切换悬浮窗展开/收起状态
     */
    private void toggleExpand() {
        View controlPanel = floatingView.findViewById(R.id.control_panel);
        isExpanded = !isExpanded;
        controlPanel.setVisibility(isExpanded ? View.VISIBLE : View.GONE);
    }

    /**
     * 设置滑动回调
     */
    private void setupSwipeCallback() {
        SwipeAccessibilityService.setCallback(new SwipeAccessibilityService.SwipeCallback() {
            @Override
            public void onProgress(int current, int total) {
                // 在主线程更新UI
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                    updateUI(true, current, total);
                });
            }

            @Override
            public void onCompleted() {
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                    updateUI(false, 0, 0);
                    Toast.makeText(FloatingWindowService.this,
                            "自动滑动完成！", Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onStopped() {
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                    updateUI(false, 0, 0);
                });
            }
        });
    }

    /**
     * 更新UI状态
     */
    private void updateUI(boolean running, int current, int total) {
        if (tvStatus != null) {
            tvStatus.setText(running ? "● 运行中" : "○ 已停止");
            tvStatus.setTextColor(running ? 0xFF4CAF50 : 0xFF9E9E9E);
        }
        if (tvProgress != null) {
            if (running) {
                tvProgress.setText(current + " / " + total);
            } else {
                tvProgress.setText("");
            }
        }
        if (btnStart != null) {
            btnStart.setEnabled(!running);
        }
        if (btnStop != null) {
            btnStop.setEnabled(running);
        }
    }
}
