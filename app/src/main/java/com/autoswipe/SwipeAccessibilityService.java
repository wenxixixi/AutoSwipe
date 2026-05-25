package com.autoswipe;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

import java.util.concurrent.atomic.AtomicInteger;

public class SwipeAccessibilityService extends AccessibilityService {

    private static final String TAG = "AutoSwipe";
    private int swipeCount = 100;
    private long swipeInterval = 2000;
    private int swipeDuration = 300;
    private boolean isRunning = false;
    private final AtomicInteger currentCount = new AtomicInteger(0);
    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable swipeRunnable;

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
    }

    @Override
    public void onInterrupt() {
        stopSwipe();
    }

    public void startSwipe(int count, long interval) {
        if (isRunning) return;
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
                if (callback != null) callback.onProgress(current, swipeCount);
                if (current >= swipeCount) {
                    isRunning = false;
                    Log.i(TAG, "自动滑动完成: 共滑动 " + swipeCount + " 次");
                    if (callback != null) callback.onCompleted();
                } else {
                    handler.postDelayed(this, swipeInterval);
                }
            }
        };
        handler.post(swipeRunnable);
    }

    public void stopSwipe() {
        if (!isRunning) return;
        isRunning = false;
        handler.removeCallbacks(swipeRunnable);
        Log.i(TAG, "停止自动滑动，已完成 " + currentCount.get() + " 次");
        if (callback != null) callback.onStopped();
    }

    public boolean isRunning() { return isRunning; }
    public int getCurrentCount() { return currentCount.get(); }

    private void performSwipe() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            Log.e(TAG, "Android 7.0 以下不支持手势模拟");
            return;
        }
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        float startX = screenWidth / 2f;
        float startY = screenHeight * 0.75f;
        float endX = screenWidth / 2f;
        float endY = screenHeight * 0.25f;
        Path swipePath = new Path();
        swipePath.moveTo(startX, startY);
        swipePath.lineTo(endX, endY);
        GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
        gestureBuilder.addStroke(new GestureDescription.StrokeDescription(swipePath, 0, swipeDuration));
        GestureResultCallbackImpl callbackImpl = new GestureResultCallbackImpl();
        dispatchGesture(gestureBuilder.build(), callbackImpl, null);
    }

    private class GestureResultCallbackImpl extends GestureDescription.GestureResultCallback {
        @Override
        public void onCompleted(GestureDescription gestureDescription) {
            Log.d(TAG, "滑动完成");
        }

        @Override
        public void onCancelled(GestureDescription gestureDescription) {
            Log.w(TAG, "滑动被取消");
        }
    }
}
