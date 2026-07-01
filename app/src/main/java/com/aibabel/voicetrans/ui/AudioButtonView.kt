package com.aibabel.voicetrans.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import com.aibabel.voicetrans.R

class AudioButtonView : View {

    interface AudioButtonCallback {
        fun onRecordingStart()
        fun onRecordingEnd()
    }

    private lateinit var circlePaint: Paint
    private lateinit var textPaint: Paint
    private lateinit var ripplePaint: Paint

    private var centerX = 0f
    private var centerY = 0f
    private var currentRadius = 0f
    private var maxRadius = 0f
    private var rippleRadius = 0f
    private var rippleAlpha = 0f

    private var isRecording = false
    private var isPressed = false

    private var normalText = "开始录音"
    private var recordingText = "结束录音"

    private var callback: AudioButtonCallback? = null

    private var recordingAnimator: ValueAnimator? = null
    private var rippleAnimator: ValueAnimator? = null

    constructor(context: Context) : super(context) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        init()
    }

    private fun init() {
        circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ContextCompat.getColor(context, R.color.primary)
            style = Paint.Style.FILL
        }

        textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            textSize = 48f
            textAlign = Paint.Align.CENTER
        }

        ripplePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ContextCompat.getColor(context, R.color.primary)
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        centerX = w / 2f
        centerY = h / 2f
        maxRadius = minOf(w, h) / 2f - 20f
        currentRadius = maxRadius
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (rippleRadius > 0 && rippleAlpha > 0) {
            ripplePaint.alpha = rippleAlpha.toInt()
            canvas.drawCircle(centerX, centerY, rippleRadius, ripplePaint)
        }

        canvas.drawCircle(centerX, centerY, currentRadius, circlePaint)

        val text = if (isRecording) recordingText else normalText
        val textY = centerY - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(text, centerX, textY, textPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                isPressed = true
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (isPressed) {
                    isPressed = false
                    performClick()
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                isPressed = false
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        if (isRecording) {
            stopRecordingAnimation()
            callback?.onRecordingEnd()
        } else {
            startRecordingAnimation()
            callback?.onRecordingStart()
        }
        return true
    }

    private fun startRecordingAnimation() {
        isRecording = true

        recordingAnimator = ValueAnimator.ofFloat(maxRadius, maxRadius * 0.9f).apply {
            duration = 200
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            addUpdateListener { animation ->
                currentRadius = animation.animatedValue as Float
                invalidate()
            }
            start()
        }

        startRippleAnimation()

        circlePaint.color = 0xFFF44336.toInt()
        invalidate()
    }

    private fun stopRecordingAnimation() {
        isRecording = false

        recordingAnimator?.cancel()
        recordingAnimator = null

        rippleAnimator?.cancel()
        rippleAnimator = null

        currentRadius = maxRadius
        rippleRadius = 0f
        rippleAlpha = 0f

        circlePaint.color = ContextCompat.getColor(context, R.color.primary)
        invalidate()
    }

    private fun startRippleAnimation() {
        rippleAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1000
            addUpdateListener { animation ->
                val progress = animation.animatedValue as Float
                rippleRadius = maxRadius + progress * 80
                rippleAlpha = (1 - progress) * 100
                invalidate()

                if (progress >= 1f && isRecording) {
                    animation.currentPlayTime = 0
                }
            }
            start()
        }
    }

    fun setCallback(callback: AudioButtonCallback?) {
        this.callback = callback
    }

    fun setNormalText(text: String) {
        normalText = text
        invalidate()
    }

    fun setRecordingText(text: String) {
        recordingText = text
        invalidate()
    }

    fun setRecordingState(recording: Boolean) {
        if (recording && !isRecording) {
            startRecordingAnimation()
        } else if (!recording && isRecording) {
            stopRecordingAnimation()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        recordingAnimator?.cancel()
        rippleAnimator?.cancel()
    }
}
