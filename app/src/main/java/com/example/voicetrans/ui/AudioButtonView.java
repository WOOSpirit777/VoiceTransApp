package com.example.voicetrans.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.core.content.ContextCompat;

import com.example.voicetrans.R;

/**
 * 自定义长按录音按钮
 * 支持长按录制动画效果
 */
public class AudioButtonView extends View {

    private Paint circlePaint;
    private Paint textPaint;
    private Paint ripplePaint;

    private float centerX;
    private float centerY;
    private float currentRadius = 0;
    private float maxRadius = 0;
    private float rippleRadius = 0;
    private float rippleAlpha = 0;

    private boolean isRecording = false;
    private boolean isPressed = false;

    private String normalText = "按住说话";
    private String recordingText = "松开结束";

    private AudioButtonCallback callback;

    // 动画
    private ValueAnimator recordingAnimator;
    private ValueAnimator rippleAnimator;

    public interface AudioButtonCallback {
        void onRecordingStart();
        void onRecordingEnd();
    }

    public AudioButtonView(Context context) {
        super(context);
        init();
    }

    public AudioButtonView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public AudioButtonView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        // 主圆画笔
        circlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        circlePaint.setColor(ContextCompat.getColor(getContext(), R.color.primary));
        circlePaint.setStyle(Paint.Style.FILL);

        // 文字画笔
        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(0xFFFFFFFF);
        textPaint.setTextSize(48);
        textPaint.setTextAlign(Paint.Align.CENTER);

        // 波纹画笔
        ripplePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        ripplePaint.setColor(ContextCompat.getColor(getContext(), R.color.primary));
        ripplePaint.setStyle(Paint.Style.STROKE);
        ripplePaint.setStrokeWidth(4);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        centerX = w / 2f;
        centerY = h / 2f;
        maxRadius = Math.min(w, h) / 2f - 20;
        currentRadius = maxRadius;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // 绘制波纹效果
        if (rippleRadius > 0 && rippleAlpha > 0) {
            ripplePaint.setAlpha((int) rippleAlpha);
            canvas.drawCircle(centerX, centerY, rippleRadius, ripplePaint);
        }

        // 绘制主圆
        canvas.drawCircle(centerX, centerY, currentRadius, circlePaint);

        // 绘制文字
        String text = isRecording ? recordingText : normalText;
        float textY = centerY - (textPaint.descent() + textPaint.ascent()) / 2;
        canvas.drawText(text, centerX, textY, textPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                isPressed = true;
                startRecordingAnimation();
                if (callback != null) {
                    callback.onRecordingStart();
                }
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                    isPressed = false;
                    stopRecordingAnimation();
                    if (callback != null) {
                        callback.onRecordingEnd();
                    }
                return true;
        }
        return super.onTouchEvent(event);
    }

    private void startRecordingAnimation() {
        isRecording = true;

        // 缩放动画
        recordingAnimator = ValueAnimator.ofFloat(maxRadius, maxRadius * 0.9f);
        recordingAnimator.setDuration(200);
        recordingAnimator.setRepeatCount(ValueAnimator.INFINITE);
        recordingAnimator.setRepeatMode(ValueAnimator.REVERSE);
        recordingAnimator.addUpdateListener(animation -> {
            currentRadius = (float) animation.getAnimatedValue();
            invalidate();
        });
        recordingAnimator.start();

        // 波纹动画
        startRippleAnimation();

        // 改变颜色为红色
        circlePaint.setColor(0xFFF44336);
        invalidate();
    }

    private void stopRecordingAnimation() {
        isRecording = false;

        if (recordingAnimator != null) {
            recordingAnimator.cancel();
            recordingAnimator = null;
        }

        if (rippleAnimator != null) {
            rippleAnimator.cancel();
            rippleAnimator = null;
        }

        currentRadius = maxRadius;
        rippleRadius = 0;
        rippleAlpha = 0;

        // 恢复颜色
        circlePaint.setColor(ContextCompat.getColor(getContext(), R.color.primary));
        invalidate();
    }

    private void startRippleAnimation() {
        rippleAnimator = ValueAnimator.ofFloat(0f, 1f);
        rippleAnimator.setDuration(1000);
        rippleAnimator.addUpdateListener(animation -> {
            float progress = (float) animation.getAnimatedValue();
            rippleRadius = maxRadius + progress * 80;
            rippleAlpha = (1 - progress) * 100;
            invalidate();

            if (progress >= 1f && isRecording) {
                animation.setCurrentPlayTime(0);
            }
        });
        rippleAnimator.start();
    }

    public void setCallback(AudioButtonCallback callback) {
        this.callback = callback;
    }

    public void setNormalText(String text) {
        this.normalText = text;
        invalidate();
    }

    public void setRecordingText(String text) {
        this.recordingText = text;
        invalidate();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (recordingAnimator != null) {
            recordingAnimator.cancel();
        }
        if (rippleAnimator != null) {
            rippleAnimator.cancel();
        }
    }
}
