package com.aibabel.voicetrans.ui

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.FileProvider
import com.aibabel.voicetrans.R
import com.aibabel.voicetrans.util.Constants
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
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
        val intent = Intent(Intent.ACTION_VIEW)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        val apkUri = FileProvider.getUriForFile(
            context,
            context.packageName + ".fileprovider",
            apkFile
        )
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        intent.setDataAndType(apkUri, "application/vnd.android.package-archive")

        context.startActivity(intent)
    }
}
