package com.aibabel.voicetrans.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.display.DisplayManager
import android.os.Handler
import android.util.DisplayMetrics
import android.util.Log
import android.view.Display

/**
 * 副屏显示管理器
 * 负责副屏检测、Presentation显示/隐藏、开关副屏广播、HALL传感器监听
 */
class SecondaryDisplayManager(
    private val activity: Activity,
    private val handler: Handler
) {

    private var presentation: SecondaryPresentation? = null
    private var secondaryDisplay: Display? = null
    private var isDualScreen = false
    private var currentMode = 0
    private var isProcessingDisplayChange = false
    private var pendingShowRunnable: Runnable? = null
    private var ensureTopRunnable: Runnable? = null
    private var isHallSensorRegistered = false
    private var lastHallState = -1  // -1: 未知, 0: 打开(无磁铁), 1: 合上(有磁铁)
    private val HALL_THRESHOLD = 50.0f  // 磁场强度阈值(μT)，超过此值认为磁铁靠近
    private var userPresentReceiver: android.content.BroadcastReceiver? = null
    private var isUserPresentRegistered = false
    private var hasTurnedOnSubScreen = false

    private val hallSensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent?) {
            event?.let {
                // 磁力计返回三个轴向的磁场强度，取绝对值最大的
                val maxValue = maxOf(Math.abs(it.values[0]), Math.abs(it.values[1]), Math.abs(it.values[2]))
                val state = if (maxValue > HALL_THRESHOLD) 1 else 0
                Log.d(TAG, "HALL 磁场强度: x=${it.values[0]}, y=${it.values[1]}, z=${it.values[2]}, max=$maxValue, state=$state")
                if (state != lastHallState) {
                    lastHallState = state
                    onHallStateChanged(state)
                }
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) {
            Log.e(TAG, "Display added: $displayId")
            val displayManager = activity.getSystemService(Activity.DISPLAY_SERVICE) as DisplayManager
            val display = displayManager.getDisplay(displayId)
            if (display != null && display.isValid) {
                val presentationDisplays = displayManager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
                if (presentationDisplays.isNotEmpty() && presentationDisplays[0].displayId == displayId) {
                    secondaryDisplay = display
                    isDualScreen = true
                    scheduleShowPresentation(500)
                }
            }
        }

        override fun onDisplayRemoved(displayId: Int) {
            Log.e(TAG, "Display removed: $displayId")
            if (secondaryDisplay?.displayId == displayId) {
                cancelEnsureTop()
                presentation?.dismiss()
                presentation = null
                secondaryDisplay = null
                isDualScreen = false
            }
        }

        override fun onDisplayChanged(displayId: Int) {
            Log.e(TAG, "Display changed: $displayId")
            if (secondaryDisplay?.displayId == displayId || displayId == Display.DEFAULT_DISPLAY) {
                val displayManager = activity.getSystemService(Activity.DISPLAY_SERVICE) as DisplayManager
                val display = displayManager.getDisplay(displayId)
                if (display != null && display.isValid) {
                    if (display.state == Display.STATE_ON) {
                        Log.e(TAG, "屏幕已亮起，触发副屏恢复")
                        scheduleShowPresentation(800)
                    } else {
                        Log.e(TAG, "屏幕状态: OFF")
                        cancelEnsureTop()
                        if (presentation != null) {
                            try { presentation?.dismiss() } catch (e: Exception) {}
                            presentation = null
                            Log.e(TAG, "屏幕OFF，已清理Presentation")
                        }
                    }
                }
            }
        }
    }

    private fun scheduleShowPresentation(delayMs: Long = 500) {
        pendingShowRunnable?.let { handler.removeCallbacks(it) }
        cancelEnsureTop()
        pendingShowRunnable = Runnable {
            if (isProcessingDisplayChange) return@Runnable
            isProcessingDisplayChange = true
            try {
                if (secondaryDisplay == null) {
                    detectDisplay()
                }
                if (secondaryDisplay != null) {
                    showPresentationInternal(currentMode)
                    onDisplayReadyCallback?.invoke()
                    scheduleEnsureTop()
                }
            } finally {
                isProcessingDisplayChange = false
            }
        }
        handler.postDelayed(pendingShowRunnable!!, delayMs)
    }

    private fun scheduleEnsureTop() {
        cancelEnsureTop()
        ensureTopRunnable = Runnable {
            if (presentation != null && isDualScreen) {
                if (presentation?.isShowing == true) {
                    Log.e(TAG, "重新显示Presentation确保在最上层")
                    try {
                        presentation?.hide()
                        presentation?.show()
                    } catch (e: Exception) {
                        Log.e(TAG, "重新显示Presentation失败: ${e.message}")
                    }
                } else {
                    Log.e(TAG, "Presentation未显示，重新创建")
                    showPresentationInternal(currentMode)
                }
            }
            ensureTopRunnable?.let { handler.postDelayed(it, 1500) }
        }
        handler.postDelayed(ensureTopRunnable!!, 1000)
    }

    private fun cancelEnsureTop() {
        ensureTopRunnable?.let { handler.removeCallbacks(it) }
        ensureTopRunnable = null
    }

    private fun detectDisplay() {
        val displayManager = activity.getSystemService(Activity.DISPLAY_SERVICE) as DisplayManager
        val presentationDisplays = displayManager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
        if (presentationDisplays.isNotEmpty() && presentationDisplays[0].isValid) {
            secondaryDisplay = presentationDisplays[0]
            isDualScreen = true
            val metrics = DisplayMetrics()
            secondaryDisplay!!.getMetrics(metrics)
            Log.e(TAG, "检测到副屏: ${secondaryDisplay!!.name}, 分辨率: ${metrics.widthPixels}x${metrics.heightPixels}")
        } else {
            Log.e(TAG, "未找到可用副屏")
        }
    }

    private fun onHallStateChanged(state: Int) {
        Log.e(TAG, "皮套状态: ${if (state == 1) "合上" else "打开"}")
        if (state == 1) {
            cancelEnsureTop()
            if (presentation != null) {
                try { presentation?.dismiss() } catch (e: Exception) {}
                presentation = null
            }
            turnOffSubScreen()
            hasTurnedOnSubScreen = false
        } else {
            Log.e(TAG, "皮套打开，恢复副屏")
            turnOnSubScreen()
            scheduleShowPresentation(800)
        }
    }

    fun register() {
        val displayManager = activity.getSystemService(Activity.DISPLAY_SERVICE) as DisplayManager
        displayManager.registerDisplayListener(displayListener, handler)
        registerHallSensor()
        registerUserPresentReceiver()
    }

    fun unregister() {
        val displayManager = activity.getSystemService(Activity.DISPLAY_SERVICE) as DisplayManager
        displayManager.unregisterDisplayListener(displayListener)
        pendingShowRunnable?.let { handler.removeCallbacks(it) }
        cancelEnsureTop()
        unregisterHallSensor()
        unregisterUserPresentReceiver()
    }

    private var onDisplayReadyCallback: (() -> Unit)? = null

    fun setOnDisplayReadyCallback(callback: () -> Unit) {
        onDisplayReadyCallback = callback
    }

    fun onStart() {
        Log.e(TAG, "onStart: 打开副屏")
        turnOnSubScreen()
        scheduleShowPresentation(500)
    }

    fun onStop() {
        Log.e(TAG, "onStop: 关闭副屏")
        cancelEnsureTop()
        pendingShowRunnable?.let { handler.removeCallbacks(it) }
        if (presentation != null) {
            try { presentation?.dismiss() } catch (e: Exception) {}
            presentation = null
        }
        turnOffSubScreen()
        hasTurnedOnSubScreen = false
    }

    private fun registerUserPresentReceiver() {
        if (isUserPresentRegistered) return
        userPresentReceiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_USER_PRESENT) {
                    Log.e(TAG, "收到 USER_PRESENT 广播，恢复副屏")
                    turnOnSubScreen()
                    scheduleShowPresentation(800)
                }
            }
        }
        val filter = IntentFilter(Intent.ACTION_USER_PRESENT)
        activity.registerReceiver(userPresentReceiver, filter)
        isUserPresentRegistered = true
        Log.e(TAG, "USER_PRESENT 广播已注册")
    }

    private fun unregisterUserPresentReceiver() {
        if (!isUserPresentRegistered) return
        userPresentReceiver?.let {
            try { activity.unregisterReceiver(it) } catch (e: Exception) {}
        }
        userPresentReceiver = null
        isUserPresentRegistered = false
        Log.e(TAG, "USER_PRESENT 广播已注销")
    }

    private fun registerHallSensor() {
        if (isHallSensorRegistered) return
        val sensorManager = activity.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        // 尝试多种可能的HALL传感器类型
        val hallSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
            ?: sensorManager.getDefaultSensor(65536) // TYPE_HALL 常量值
        if (hallSensor != null) {
            sensorManager.registerListener(hallSensorListener, hallSensor, SensorManager.SENSOR_DELAY_NORMAL)
            isHallSensorRegistered = true
            Log.e(TAG, "HALL 传感器已注册: ${hallSensor.name}")
        } else {
            Log.e(TAG, "未找到 HALL 传感器")
        }
    }

    private fun unregisterHallSensor() {
        if (!isHallSensorRegistered) return
        val sensorManager = activity.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        sensorManager.unregisterListener(hallSensorListener)
        isHallSensorRegistered = false
        Log.e(TAG, "HALL 传感器已注销")
    }

    fun turnOnSubScreen() {
        if (hasTurnedOnSubScreen) {
            Log.e(TAG, "副屏已开启，跳过重复发送TURN_ON广播")
            return
        }
        val intent = Intent("custom.intent.TURN_ON_SUB_SCREEN")
        intent.setPackage("com.android.settings")
        activity.sendBroadcast(intent)
        hasTurnedOnSubScreen = true
        Log.e(TAG, "Turn on sub screen")
    }

    fun turnOffSubScreen() {
        val intent = Intent("custom.intent.TURN_OFF_SUB_SCREEN")
        intent.setPackage("com.android.settings")
        activity.sendBroadcast(intent)
        Log.e(TAG, "Turn off sub screen")
    }

    fun detectAndInit() {
        if (isProcessingDisplayChange) {
            Log.e(TAG, "正在处理屏幕变化，跳过重复检测")
            return
        }
        detectDisplay()
        if (secondaryDisplay != null) {
            showPresentationInternal(currentMode)
        }
    }

    fun setMode(mode: Int) {
        currentMode = mode
    }

    private fun showPresentationInternal(mode: Int = 0) {
        try {
            if (secondaryDisplay == null) {
                Log.e(TAG, "副屏为空，无法显示Presentation")
                return
            }
            if (presentation != null && presentation!!.isShowing) {
                Log.e(TAG, "Presentation已在显示，刷新内容")
                presentation!!.clearContent(mode)
                return
            }
            if (presentation != null) {
                try { presentation?.dismiss() } catch (e: Exception) {}
                presentation = null
            }
            presentation = SecondaryPresentation(activity, secondaryDisplay!!)
            presentation?.show()
            presentation?.clearContent(mode)
            Log.e(TAG, "双屏模式已启用 - Presentation show() 成功")
        } catch (e: Exception) {
            Log.e(TAG, "显示副屏失败: ${e.message}")
        }
    }

    fun showPresentation(mode: Int = 0) {
        currentMode = mode
        showPresentationInternal(mode)
        scheduleEnsureTop()
    }

    fun dismissPresentation() {
        cancelEnsureTop()
        if (presentation != null && presentation!!.isShowing) {
            presentation!!.dismiss()
        }
    }

    fun updateContent(sourceText: String?, targetText: String?, sourceLang: String?, targetLang: String?, mode: Int) {
        if (isDualScreen && presentation != null) {
            presentation!!.updateContent(sourceText, targetText, sourceLang, targetLang, mode)
            Log.e(TAG, "Update second screen mode=$mode: $targetText")
        }
    }

    fun clearContent(mode: Int = 0) {
        if (isDualScreen && presentation != null) {
            presentation!!.clearContent(mode)
            Log.e(TAG, "Clear second screen content, mode=$mode")
        }
    }

    fun isDualScreenAvailable(): Boolean = isDualScreen && secondaryDisplay != null

    fun release() {
        cancelEnsureTop()
        pendingShowRunnable?.let { handler.removeCallbacks(it) }
        turnOffSubScreen()
        dismissPresentation()
        unregisterHallSensor()
        unregisterUserPresentReceiver()
        unregister()
        hasTurnedOnSubScreen = false
    }

    companion object {
        private const val TAG = "SecondaryDisplayMgr"
    }
}
