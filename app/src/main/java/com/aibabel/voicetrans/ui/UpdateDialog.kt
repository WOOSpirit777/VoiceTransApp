package com.aibabel.voicetrans.ui

import android.app.Dialog
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.aibabel.voicetrans.R
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class UpdateDialog(context: Context) : Dialog(context, R.style.UpdateDialogStyle) {

    private var tvTitle: TextView
    private var tvContent: TextView
    private var layoutProgress: LinearLayout
    private var progressBar: ProgressBar
    private var tvProgress: TextView
    private var btnCancel: Button
    private var btnUpdate: Button

    private var downloadUrl: String = ""
    private var isDownloading = false
    private val mainHandler = Handler(Looper.getMainLooper())

    init {
        setContentView(R.layout.dialog_update)

        tvTitle = findViewById(R.id.tvTitle)
        tvContent = findViewById(R.id.tvContent)
        layoutProgress = findViewById(R.id.layoutProgress)
        progressBar = findViewById(R.id.progressBar)
        tvProgress = findViewById(R.id.tvProgress)
        btnCancel = findViewById(R.id.btnCancel)
        btnUpdate = findViewById(R.id.btnUpdate)

        setCancelable(false)
        setCanceledOnTouchOutside(false)

        window?.let {
            val params = it.attributes
            params.gravity = Gravity.TOP
            params.width = WindowManager.LayoutParams.MATCH_PARENT
            params.height = WindowManager.LayoutParams.WRAP_CONTENT
            params.windowAnimations = R.style.UpdateDialogAnimation
            it.attributes = params
        }

        btnCancel.setOnClickListener {
            if (!isDownloading) {
                dismiss()
            }
        }

        btnUpdate.setOnClickListener {
            if (!isDownloading && downloadUrl.isNotEmpty()) {
                startDownload()
            }
        }
    }

    fun setDownloadUrl(url: String) {
        downloadUrl = url
    }

    private fun startDownload() {
        isDownloading = true
        layoutProgress.visibility = View.VISIBLE
        btnUpdate.isEnabled = false
        btnCancel.isEnabled = false
        btnUpdate.text = context.getString(R.string.downloading)

        Thread {
            try {
                val client = OkHttpClient()
                val request = Request.Builder().url(downloadUrl).build()
                val response = client.newCall(request).execute()

                if (!response.isSuccessful) {
                    mainHandler.post {
                        isDownloading = false
                        btnUpdate.isEnabled = true
                        btnCancel.isEnabled = true
                        btnUpdate.text = context.getString(R.string.update)
                        tvContent.text = "下载失败，请重试"
                    }
                    return@Thread
                }

                val body = response.body
                val contentLength = body?.contentLength() ?: 0

                val apkFile = File(
                    context.getExternalFilesDir(null),
                    "update_${System.currentTimeMillis()}.apk"
                )

                val inputStream = body?.byteStream()
                val outputStream = FileOutputStream(apkFile)

                val buffer = ByteArray(4096)
                var totalRead = 0L
                var read: Int

                while (inputStream?.read(buffer).also { read = it ?: -1 } != -1) {
                    outputStream.write(buffer, 0, read)
                    totalRead += read

                    if (contentLength > 0) {
                        val progress = (totalRead * 100 / contentLength).toInt()
                        mainHandler.post {
                            progressBar.progress = progress
                            tvProgress.text = "$progress%"
                        }
                    }
                }

                outputStream.flush()
                outputStream.close()
                inputStream?.close()

                mainHandler.post {
                    isDownloading = false
                    tvProgress.text = context.getString(R.string.download_complete)
                    btnCancel.isEnabled = true
                    installApk(apkFile)
                }

            } catch (e: Exception) {
                mainHandler.post {
                    isDownloading = false
                    btnUpdate.isEnabled = true
                    btnCancel.isEnabled = true
                    btnUpdate.text = context.getString(R.string.update)
                    tvContent.text = "下载失败：${e.message}"
                }
            }
        }.start()
    }

    private fun installApk(apkFile: File) {
        tvProgress.text = "正在安装..."

        // 注册广播接收器接收安装结果
        val action = "com.aibabel.voicetrans.INSTALL_STATUS"
        val installResultReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                val status = intent?.getIntExtra(PackageInstaller.EXTRA_STATUS, -999) ?: -999
                val msg = intent?.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: ""
                context.unregisterReceiver(this)

                mainHandler.post {
                    when (status) {
                        PackageInstaller.STATUS_SUCCESS -> {
                            tvProgress.text = "安装成功，正在重启..."
                            // 延迟1秒后自动重启应用，给系统处理安装的时间
                            mainHandler.postDelayed({
                                dismiss()
                                launchApp()
                            }, 1000)
                        }
                        PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                            // 系统需要用户确认，拉起安装确认界面
                            val confirmIntent = intent?.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                            confirmIntent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            confirmIntent?.let { context.startActivity(it) }
                            dismiss()
                        }
                        else -> {
                            tvProgress.text = "安装失败: $msg"
                            btnUpdate.isEnabled = true
                            btnCancel.isEnabled = true
                            btnUpdate.text = context.getString(R.string.update)
                            Log.e("UpdateDialog", "安装失败 status=$status msg=$msg")
                        }
                    }
                }
            }
        }
        context.registerReceiver(installResultReceiver, IntentFilter(action))

        // 使用PackageInstaller API安装，避免FileProvider和pm命令的兼容性问题
        Thread {
            try {
                val packageInstaller = context.packageManager.packageInstaller
                val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
                params.setAppPackageName(context.packageName)

                val sessionId = packageInstaller.createSession(params)
                val session = packageInstaller.openSession(sessionId)

                // 将APK写入session
                FileInputStream(apkFile).use { input ->
                    session.openWrite("app.apk", 0, apkFile.length()).use { output ->
                        val buffer = ByteArray(8192)
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                        }
                        session.fsync(output)
                    }
                }

                // 提交安装，通过PendingIntent接收结果
                val intent = Intent(action)
                intent.putExtra(PackageInstaller.EXTRA_PACKAGE_NAME, context.packageName)
                val pendingIntent = PendingIntent.getBroadcast(
                    context, sessionId, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                )
                session.commit(pendingIntent.intentSender)
                session.close()

                // 备选方案：延迟3秒后使用am start命令启动应用
                // 因为动态Receiver可能在旧进程被杀掉后无法收到回调
                Thread {
                    try {
                        Thread.sleep(3000)
                        Log.d("UpdateDialog", "尝试使用am start启动应用")
                        val process = Runtime.getRuntime().exec(
                            arrayOf("am", "start", "-n", "${context.packageName}/.ui.MainActivity")
                        )
                        val exitCode = process.waitFor()
                        Log.d("UpdateDialog", "am start exitCode=$exitCode")
                    } catch (e: Exception) {
                        Log.e("UpdateDialog", "am start失败: ${e.message}")
                    }
                }.start()
            } catch (e: Exception) {
                try { context.unregisterReceiver(installResultReceiver) } catch (_: Exception) {}
                mainHandler.post {
                    tvProgress.text = "安装失败：${e.message}"
                    btnUpdate.isEnabled = true
                    btnCancel.isEnabled = true
                    btnUpdate.text = context.getString(R.string.update)
                    Log.e("UpdateDialog", "安装异常", e)
                }
            }
        }.start()
    }

    private fun launchApp() {
        try {
            // 使用ApplicationContext而不是Activity Context，因为Activity可能在安装过程中被销毁
            val appContext = context.applicationContext
            val intent = appContext.packageManager.getLaunchIntentForPackage(appContext.packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                intent.addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                appContext.startActivity(intent)
                Log.d("UpdateDialog", "launchApp成功")
            } else {
                Log.e("UpdateDialog", "无法获取启动Intent")
            }
        } catch (e: Exception) {
            Log.e("UpdateDialog", "启动应用失败: ${e.message}", e)
        }
    }
}
