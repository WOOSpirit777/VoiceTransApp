package com.aibabel.voicetrans.ui

import android.app.Activity
import android.content.Intent
import android.hardware.display.DisplayManager
import android.os.Handler
import android.util.DisplayMetrics
import android.util.Log
import android.view.Display

/**
 * 副屏显示管理器
 * 负责副屏检测、Presentation显示/隐藏、开关副屏广播
 */
class SecondaryDisplayManager(
    private val activity: Activity,
    private val handler: Handler
) {

    private var presentation: SecondaryPresentation? = null
    private var secondaryDisplay: Display? = null
    private var isDualScreen = false

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
                    showPresentation()
                }
            }
        }

        override fun onDisplayRemoved(displayId: Int) {
            Log.e(TAG, "Display removed: $displayId")
            if (secondaryDisplay?.displayId == displayId) {
                presentation?.dismiss()
                presentation = null
                secondaryDisplay = null
                isDualScreen = false
            }
        }

        override fun onDisplayChanged(displayId: Int) {
            Log.e(TAG, "Display changed: $displayId")
        }
    }

    fun register() {
        val displayManager = activity.getSystemService(Activity.DISPLAY_SERVICE) as DisplayManager
        displayManager.registerDisplayListener(displayListener, handler)
    }

    fun unregister() {
        val displayManager = activity.getSystemService(Activity.DISPLAY_SERVICE) as DisplayManager
        displayManager.unregisterDisplayListener(displayListener)
    }

    fun turnOnSubScreen() {
        val intent = Intent("custom.intent.TURN_ON_SUB_SCREEN")
        intent.setPackage("com.android.settings")
        activity.sendBroadcast(intent)
        Log.e(TAG, "Turn on sub screen")
    }

    fun turnOffSubScreen() {
        val intent = Intent("custom.intent.TURN_OFF_SUB_SCREEN")
        intent.setPackage("com.android.settings")
        activity.sendBroadcast(intent)
        Log.e(TAG, "Turn off sub screen")
    }

    fun detectAndInit() {
        val displayManager = activity.getSystemService(Activity.DISPLAY_SERVICE) as DisplayManager

        val allDisplays = displayManager.displays
        Log.e(TAG, "检测到 ${allDisplays.size} 个屏幕")
        for (d in allDisplays) {
            val metrics = DisplayMetrics()
            d.getMetrics(metrics)
            Log.e(TAG, "  屏幕: name=${d.name}, displayId=${d.displayId}, valid=${d.isValid}, size=${metrics.widthPixels}x${metrics.heightPixels}")
        }

        val presentationDisplays = displayManager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
        Log.e(TAG, "Presentation 类屏幕: ${presentationDisplays.size} 个")

        if (presentationDisplays.isNotEmpty() && presentationDisplays[0].isValid) {
            secondaryDisplay = presentationDisplays[0]
            isDualScreen = true

            val metrics = DisplayMetrics()
            secondaryDisplay!!.getMetrics(metrics)
            Log.e(TAG, "选中副屏: ${secondaryDisplay!!.name}, 分辨率: ${metrics.widthPixels}x${metrics.heightPixels}")

            showPresentation()
        } else {
            Log.e(TAG, "未找到可用副屏 (Presentation 类)")
        }
    }

    fun showPresentation() {
        try {
            if (presentation == null && secondaryDisplay != null) {
                presentation = SecondaryPresentation(activity, secondaryDisplay!!)
            }
            if (presentation != null && !presentation!!.isShowing) {
                presentation!!.show()
                Log.e(TAG, "双屏模式已启用 - Presentation show() 成功")
            }
        } catch (e: Exception) {
            Log.e(TAG, "显示副屏失败: ${e.message}")
        }
    }

    fun dismissPresentation() {
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

    fun clearContent() {
        if (isDualScreen && presentation != null) {
            presentation!!.clearContent()
            Log.e(TAG, "Clear second screen content")
        }
    }

    fun isDualScreenAvailable(): Boolean = isDualScreen && secondaryDisplay != null

    fun release() {
        turnOffSubScreen()
        dismissPresentation()
        unregister()
    }

    companion object {
        private const val TAG = "SecondaryDisplayMgr"
    }
}
