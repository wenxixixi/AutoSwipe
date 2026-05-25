package com.autoswipe;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Intent;
import android.graphics.Path;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 无障碍服务 - 核心滑动逻辑
 * 通过模拟手势实现自动向上滑动
 */
public class SwipeAccessibilityService extends AccessibilityService {

    private static final String TAG = "AutoSwipe";

    // 滑动参数
    private int swipeCount = 100;        // 总滑动次数
    private long swipeInterval = 2000;   // 每次滑动间隔（毫秒）
    private int swipeDuration = 300;     // 单次滑动时长（毫秒）

    // 状态
    private boolean isRunning = false;
    private final AtomicInteger currentCount = new AtomicInteger(0);
    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable swipeRunnable;

    // 回调接口
    public interface SwipeCallback {
        void onProgress(int current, int total);
        void onCompleted();
        void onStopped();
    }

    private static SwipeCallback callback;
    private static SwipeAccessibilityService instance;

    public static SwipeAccessibilityService getInstance() {
        return instance;
    }

    public static void setCallback(SwipeCallback cb) {
        callback = cb;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopSwipe();
        instance = null;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // 不需要处理特定事件，仅用于保持服务活跃
    }

    @Override
    public void onInterrupt() {
        stopSwipe();
    }

    /**
     * 开始自动滑动
     */
    public void startSwipe(int count, long interval) {
        if (isRunning) {
            Log.w(TAG, "滑动已在进行中");
            return;
        }

        this.swipeCount = count;
        this.swipeInterval = interval;
        this.isRunning = true;
        this.currentCount.set(0);

        Log.i(TAG, "开始自动滑动: 总次数=" + count + ", 间隔=" + interval + "ms");

        swipeRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isRunning) return;

                int current = currentCount.incrementAndGet();
                performSwipe();

                if (callback != null) {
                    callback.onProgress(current, swipeCount);
                }

                if (current >= swipeCount) {
                    // 滑动完成
                    isRunning = false;
                    Log.i(TAG, "自动滑动完成: 共滑动 " + swipeCount + " 次");
                    if (callback != null) {
                        callback.onCompleted();
                    }
                } else {
                    // 继续下一次滑动
                    handler.postDelayed(this, swipeInterval);
                }
            }
        };

        // 立即执行第一次滑动
        handler.post(swipeRunnable);
    }

    /**
     * 停止自动滑动
     */
    public void stopSwipe() {
        if (!isRunning) return;
        isRunning = false;
        handler.removeCallbacks(swipeRunnable);
        Log.i(TAG, "停止自动滑动，已完成 " + currentCount.get() + " 次");
        if (callback != null) {
            callback.onStopped();
        }
    }

    /**
     * 获取当前状态
     */
    public boolean isRunning() {
        return isRunning;
    }

    public int getCurrentCount() {
        return currentCount.get();
    }

    /**
     * 执行一次向上滑动手势
     * 从屏幕中下方滑动到中上方
     */
    private void performSwipe() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            Log.e(TAG, "Android 7.0 以下不支持手势模拟");
            return;
        }

        // 获取屏幕尺寸
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int screenHeight = getResources().getDisplayMetrics().heightPixels;

        // 滑动起点：屏幕中下方
        float startX = screenWidth / 2f;
        float startY = screenHeight * 0.75f;

        // 滑动终点：屏幕中上方
        float endX = screenWidth / 2f;
        float endY = screenHeight * 0.25f;

        // 构建滑动路径
        Path swipePath = new Path();
        swipePath.moveTo(startX, startY);
        swipePath.lineTo(endX, endY);

        // 构建手势描述
        GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
        gestureBuilder.addStroke(new GestureDescription.StrokeDescription(
                swipePath, 0, swipeDuration));

        // 执行手势
        dispatchGesture(gestureBuilder.build(), new GestureDescription.GestureResultCallback() {
            @Override
            public void onCompleted(GestureDescription gestureDescription) {
                Log.d(TAG, "第 " + currentCount.get() + " 次滑动完成");
            }

            @Override
            public void onCancelled(GestureDescription gestureDescription) {
                Log.w(TAG, "第 " + currentCount.get() + " 次滑动被取消");
            }
        }, null);
    }
}
