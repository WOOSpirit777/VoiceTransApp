package com.quniu.baselib.util

import android.content.Context
import android.view.KeyEvent
import com.apkfuns.logutils.LogUtils
import com.quniu.baselib.widget.Media3AudioPlayer
import commonutil.CommonUtils
import commonutil.VolumeController

object GlobalKeyDispatcher {
    private var isInitialized = false

    fun init() {
        if (isInitialized) return
        isInitialized = true
    }

    /**
     * 在 Activity 的 onKeyDown 中调用
     */
    fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            event?.startTracking()
            return true
        }
        return false
    }

    /**
     * 在 Activity 的 onKeyUp 中调用（处理短按）
     */
    fun onKeyUp(keyCode: Int, event: KeyEvent?, activity: Context): Boolean {
        // 如果是长按触发后的抬起，或者是被取消的事件，则不处理短按逻辑
        if (event == null || event.isCanceled || (keyCode == KeyEvent.KEYCODE_BACK && event.isLongPress)) {
            return false
        }

        LogUtils.e("onKeyUp: $keyCode")
        return when (keyCode) {
//            KeyEvent.KEYCODE_BACK -> {
//                LogUtils.e("onKeyUp: $keyCode")
////                CommonUtils.gotoSettings(CommonUtils.ROUTE_HOME_ACTIVITY, activity, false)
//                if (Media3AudioPlayer.isPlaying()) Media3AudioPlayer.pause()
//                true
//            }
            134, 135 -> {
                val className = if (CommonUtils.isNetworkAvailable(activity)) {
                    CommonUtils.ROUTE_ONLINE_VOICE_TRANS
                } else {
                    CommonUtils.ROUTE_OFFLINE_VOICE_TRANS
                }
                LogUtils.d("跳转到 $className")
                CommonUtils.gotoSettings(className, activity, false)
                true
            }
            136 -> {
                CommonUtils.gotoSettings(CommonUtils.ROUTE_RECORDING_ACTIVITY, activity, false)
                true
            }
            24 -> {
                VolumeController(activity).volumeUpWithSystemUI()
                true
            }
            25 -> {
                VolumeController(activity).volumeDownWithSystemUI()
                true
            }
            else -> false
        }
    }

    fun handleKeyEvent(keyCode: Int, activity: Context): Boolean {
        LogUtils.e("handleKeyEvent: $keyCode")
        return when (keyCode) {
            134, 135 -> {
                val className = if (CommonUtils.isNetworkAvailable(activity)) {
                    CommonUtils.ROUTE_ONLINE_VOICE_TRANS
                } else {
                    CommonUtils.ROUTE_OFFLINE_VOICE_TRANS
                }
                LogUtils.d("跳转到 $className")
                CommonUtils.gotoSettings(className, activity, false)
                true
            }
            136 -> {
                CommonUtils.gotoSettings(CommonUtils.ROUTE_RECORDING_ACTIVITY, activity, false)
                true
            }

            24 -> {
                VolumeController(activity).volumeUpWithSystemUI()
                true
            }

            25 -> {
                VolumeController(activity).volumeDownWithSystemUI()
                true
            }

            else -> false
        }
    }

    fun onKeyLongPress(keyCode: Int, activity: Context): Boolean {
         LogUtils.e("onKeyLongPress: $keyCode")
        return when (keyCode) {
            KeyEvent.KEYCODE_BACK-> {
                runCatching { CommonUtils.gotoChat(context = activity) }
                true
            }
            else -> false
        }
    }
}
