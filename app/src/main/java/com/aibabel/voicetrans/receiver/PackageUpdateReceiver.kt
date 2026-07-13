package com.aibabel.voicetrans.receiver

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log

class PackageUpdateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) {
            Log.e(TAG, "onReceive: context或intent为空")
            return
        }

        val action = intent.action
        val dataString = intent.dataString
        val packageName = intent.data?.schemeSpecificPart

        Log.d(TAG, "收到广播: action=$action, data=$dataString, package=$packageName")

        when (action) {
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                Log.d(TAG, "ACTION_MY_PACKAGE_REPLACED - 本应用已更新")
                launchApp(context)
            }
            Intent.ACTION_PACKAGE_REPLACED -> {
                if (packageName == context.packageName) {
                    Log.d(TAG, "ACTION_PACKAGE_REPLACED - 本应用已更新")
                    launchApp(context)
                } else {
                    Log.d(TAG, "ACTION_PACKAGE_REPLACED - 其他应用更新，忽略: $packageName")
                }
            }
            else -> {
                Log.d(TAG, "未处理的广播: $action")
            }
        }
    }

    private fun launchApp(context: Context) {
        try {
            Log.d(TAG, "尝试启动应用: ${context.packageName}")

            // 方法1: 使用makeRestartActivityTask，最可靠的重启方式
            val componentName = ComponentName(context.packageName, "${context.packageName}.ui.MainActivity")
            val intent = Intent.makeRestartActivityTask(componentName)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)

            Log.d(TAG, "应用启动成功")
        } catch (e: Exception) {
            Log.e(TAG, "启动应用失败: ${e.message}", e)

            // 备选方案: 使用packageManager.getLaunchIntentForPackage
            try {
                val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    context.startActivity(launchIntent)
                    Log.d(TAG, "备选方案启动成功")
                } else {
                    Log.e(TAG, "无法获取启动Intent")
                }
            } catch (e2: Exception) {
                Log.e(TAG, "备选方案也失败: ${e2.message}", e2)
            }
        }
    }

    companion object {
        private const val TAG = "PackageUpdateReceiver"
    }
}
