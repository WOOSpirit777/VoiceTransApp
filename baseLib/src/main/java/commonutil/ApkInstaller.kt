package commonutil

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.apkfuns.logutils.LogUtils
import com.liulishuo.okdownload.DownloadTask
import com.liulishuo.okdownload.core.breakpoint.BlockInfo
import com.liulishuo.okdownload.core.breakpoint.BreakpointInfo
import com.liulishuo.okdownload.core.cause.EndCause
import com.liulishuo.okdownload.core.listener.DownloadListener4
import com.liulishuo.okdownload.core.listener.assist.Listener4Assist
import java.io.File
import java.io.FileInputStream

class ApkInstaller {

    companion object {
        private const val TAG = "ApkInstaller"

        interface Callback {
            fun onStart()
            fun onProgress(current: Long, total: Long)
            fun onCompleted(apkFile: File)
            fun onError(e: Exception)
        }

        fun downloadAndInstallApk(context: Context, url: String, fileName: String, callback: Callback) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "Storage permission not granted")
                callback.onError(Exception("Storage permission not granted"))
                return
            }

            val parentDir = context.getExternalFilesDir(null)
            if (parentDir == null) {
                Log.e(TAG, "External files directory is null")
                callback.onError(Exception("External files directory is null"))
                return
            }

            val apkFile = File(parentDir, fileName)
            val task = DownloadTask.Builder(url, parentDir)
                .setFilename(fileName)
                .setMinIntervalMillisCallbackProcess(200)
                .build()

            callback.onStart()

            task.enqueue(object : DownloadListener4() {
                private var totalLength: Long = -1

                override fun infoReady(task: DownloadTask, info: BreakpointInfo, fromBreakpoint: Boolean, model: Listener4Assist.Listener4Model) {
                    totalLength = info.totalLength
                    Log.d(TAG, "Download info ready, total length: $totalLength")
                }

                override fun progressBlock(task: DownloadTask, blockIndex: Int, currentBlockOffset: Long) {}

                override fun progress(task: DownloadTask, currentOffset: Long) {
                    if (totalLength > 0) {
                        callback.onProgress(currentOffset, totalLength)
                    }
                }

                override fun blockEnd(task: DownloadTask, blockIndex: Int, info: BlockInfo) {}

                override fun taskEnd(task: DownloadTask, cause: EndCause, realCause: Exception?, model: Listener4Assist.Listener4Model) {
                    if (cause == EndCause.COMPLETED) {
                        if (!apkFile.exists() || !apkFile.isFile) {
                            val e = Exception("Downloaded APK file not found: ${apkFile.absolutePath}")
                            Log.e(TAG, e.message ?: "Unknown error")
                            callback.onError(e)
                            return
                        }

                        Log.d(TAG, "Download completed: ${apkFile.absolutePath}")
                        callback?.onCompleted(apkFile)
                        checkInstallPermissionAndInstall(context, apkFile, callback)
                    } else {
                        val e = realCause ?: Exception("Download failed: $cause")
                        Log.e(TAG, "Download failed", e)
                        callback?.onError(e)
                    }
                }

                override fun taskStart(task: DownloadTask) {
                    Log.d(TAG, "Download task started")
                }

                override fun connectStart(task: DownloadTask, blockIndex: Int, requestHeaderFields: Map<String, List<String>>) {
                    Log.d(TAG, "Connect started for block $blockIndex")
                }

                override fun connectEnd(task: DownloadTask, blockIndex: Int, responseCode: Int, responseHeaderFields: Map<String, List<String>>) {
                    Log.d(TAG, "Connect ended for block $blockIndex, response code: $responseCode")
                }
            })
        }

        private fun checkInstallPermissionAndInstall(context: Context, apkFile: File, callback: Callback?) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.INSTALL_PACKAGES) == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Has INSTALL_PACKAGES permission, attempting silent install")
                silentInstallApk(context, apkFile, callback)
            } else {
                Log.d(TAG, "No INSTALL_PACKAGES permission, falling back to normal install")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val haveInstallPermission = context.packageManager.canRequestPackageInstalls()
                    if (!haveInstallPermission) {
                        Log.e(TAG, "No permission to install packages. Need to request MANAGE_UNKNOWN_APP_SOURCES.")
                        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        try {
                            context.startActivity(intent)
                            Log.d(TAG, "Started permission settings activity")
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to start permission settings activity", e)
                            callback?.onError(e)
                        }
                        return
                    }
                }
                normalInstallApk(context, apkFile, callback)
            }
        }

        private fun silentInstallApk(context: Context, apkFile: File, callback: Callback?) {
            if (!apkFile.exists() || !apkFile.isFile) {
                val e = Exception("APK file not found for installation: ${apkFile.absolutePath}")
                Log.e(TAG, e.message ?: "Unknown error")
                callback?.onError(e)
                return
            }

            val packageInstaller = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            var session: PackageInstaller.Session? = null

            try {
                val sessionId = packageInstaller.createSession(params)
                session = packageInstaller.openSession(sessionId)

                // 为本次提交创建唯一 action，便于接收端区分
                val installAction = "${context.packageName}.ACTION_INSTALL_SESSION_$sessionId"

                // 动态注册接收器，用于接收 commit 后系统发回的安装状态
                val receiver = object : BroadcastReceiver() {
                    override fun onReceive(ctx: Context, intent: Intent) {
                        try {
                            val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
                            val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                            Log.d(TAG, "Install broadcast received. status=$status, message=$message")
                            when (status) {
                                PackageInstaller.STATUS_SUCCESS -> {
                                    callback?.onCompleted(apkFile)
                                }
                                PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                                    // 平台要求用户确认安装（例如授予未知来源权限或确认安装窗口）
                                    // 系统通常会通过 Intent.EXTRA_INTENT 携带一个用于启动确认界面的 Intent
                                    val userIntent: Intent? = try {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                            intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                                        } else {
                                            @Suppress("DEPRECATION")
                                            intent.getParcelableExtra(Intent.EXTRA_INTENT) as? Intent
                                        }
                                    } catch (e: Exception) {
                                        Log.w(TAG, "Failed to obtain userIntent from broadcast extras", e)
                                        null
                                    }

                                    if (userIntent != null) {
                                        userIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        try {
                                            context.startActivity(userIntent)
                                        } catch (e: Exception) {
                                            Log.e(TAG, "Failed to start user action intent", e)
                                            callback?.onError(Exception("Pending user action required: $message"))
                                        }
                                    } else {
                                        // 如果系统没有提供可启动的 Intent，则回退到交互式安装（正常安装流程）
                                        Log.w(TAG, "STATUS_PENDING_USER_ACTION but no EXTRA_INTENT provided; falling back to interactive install. Extras: ${intent.extras?.keySet()}")
                                        callback?.onError(Exception("Pending user action required but no user intent provided: $message"))
                                        try {
                                            // 触发交互式安装界面作为回退
                                            normalInstallApk(context, apkFile, callback)
                                        } catch (e: Exception) {
                                            Log.e(TAG, "Failed to fallback to normal install after pending user action", e)
                                        }
                                    }
                                }
                                else -> {
                                    callback?.onError(Exception("Install failed (status=$status): $message"))
                                }
                            }
                        } finally {
                            try { context.unregisterReceiver(this) } catch (ignored: Exception) { }
                        }
                    }
                }

                try {
                    context.registerReceiver(receiver, IntentFilter(installAction))
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to register install result receiver", e)
                }

                // 写入 APK 到会话（流式写入，避免大内存占用）
                var bytesWritten = 0L
                FileInputStream(apkFile).use { input ->
                    session.openWrite("apk", 0, apkFile.length()).use { output ->
                        val buffer = ByteArray(8 * 1024) // 使用 8KB 缓冲
                        var len: Int
                        while (input.read(buffer).also { len = it } != -1) {
                            output.write(buffer, 0, len)
                            bytesWritten += len
                        }
                        session.fsync(output)
                    }
                }

                Log.d(TAG, "Wrote $bytesWritten bytes to session $sessionId (expected ${apkFile.length()})")

                // 提交安装请求，使用带 action 的 Intent 以便接收结果
                val intent = Intent(installAction)
                intent.putExtra("apk_path", apkFile.absolutePath)
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    sessionId,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                session.commit(pendingIntent.intentSender)
                Log.i(TAG, "Silent install committed for APK: ${apkFile.absolutePath} (session=$sessionId)")
            } catch (e: Exception) {
                LogUtils.e("Silent install failed, falling back to normal install", e)
                normalInstallApk(context, apkFile, callback)
            } finally {
                try {
                    session?.close()
                } catch (ignored: Exception) {
                }
            }
        }

        private fun normalInstallApk(context: Context, apkFile: File, callback: Callback?) {
            if (!apkFile.exists() || !apkFile.isFile) {
                val e = Exception("APK file not found for installation: ${apkFile.absolutePath}")
                Log.e(TAG, e.message ?: "Unknown error")
                callback?.onError(e)
                return
            }

            val intent = Intent(Intent.ACTION_VIEW)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val authority = "${context.packageName}.fileprovider"
                val apkUri = FileProvider.getUriForFile(context, authority, apkFile)
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                intent.setDataAndType(apkUri, "application/vnd.android.package-archive")
            } else {
                intent.setDataAndType(Uri.fromFile(apkFile), "application/vnd.android.package-archive")
            }

            try {
                context.startActivity(intent)
                Log.i(TAG, "Started install activity for APK: ${apkFile.absolutePath}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start install activity", e)
                callback?.onError(e)
            }
        }
    }
}