package com.aibabel.voicetrans.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.aibabel.voicetrans.util.Constants
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Arrays
import java.util.Date
import java.util.Locale

/**
 * 录音管理器（单例）
 *
 * 功能：
 * 1. 使用 AudioRecord 采集 PCM 音频（16kHz / 16bit / 单声道）
 * 2. 分段回调录音数据（3200 字节 / 200ms）
 * 3. 实时保存 PCM 文件到 /storage/emulated/0/Music/
 * 4. 60 秒自动停止
 *
 * 文件名格式：VoiceTrans_{lang}_{yyyyMMdd_HHmmss}.pcm
 */
class AudioRecorderManager private constructor() {

    interface AudioRecordCallback {
        fun onRecordingStart()
        fun onRecordingData(data: ByteArray, size: Int)
        fun onRecordingStop(fullData: ByteArray?)
        fun onRecordingError(error: String)
    }

    private var audioRecord: AudioRecord? = null
    private var bufferSize: Int = 0
    @Volatile
    private var isRecording = false
    private var recordingThread: Thread? = null
    private var callback: AudioRecordCallback? = null
    private var mainHandler: Handler
    private var recordedData: ByteArrayOutputStream? = null
    private var pcmFile: File? = null
    private var pcmFileOutputStream: FileOutputStream? = null
    private var currentLang = "zh"

    init {
        mainHandler = Handler(Looper.getMainLooper())

        val minBufferSize = AudioRecord.getMinBufferSize(
            Constants.SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        bufferSize = maxOf(minBufferSize, Constants.CHUNK_SIZE * 4)
        Log.e(TAG, "Buffer size: $bufferSize")
    }

    fun setCallback(callback: AudioRecordCallback?) {
        this.callback = callback
    }

    fun isRecording(): Boolean = isRecording

    fun setLanguage(lang: String) {
        currentLang = lang
    }

    private fun saveToFile(data: ByteArray, append: Boolean) {
        if (pcmFile == null || pcmFileOutputStream == null) {
            return
        }
        try {
            if (append) {
                pcmFileOutputStream!!.write(data)
            }
        } catch (e: IOException) {
            Log.e(TAG, "Error writing to file: " + e.message)
        }
    }

    fun startRecording() {
        if (isRecording) {
            Log.w(TAG, "Already recording")
            return
        }

        try {
            val storageDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
            if (!storageDir.exists()) {
                storageDir.mkdirs()
            }

            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val fileName = "VoiceTrans_${currentLang}_$timeStamp.pcm"
            pcmFile = File(storageDir, fileName)

            pcmFileOutputStream = FileOutputStream(pcmFile)
            Log.e(TAG, "PCM file created: " + pcmFile!!.absolutePath)

            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                Constants.SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )

            if (audioRecord!!.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord initialization failed")
                mainHandler.post { callback?.onRecordingError("麦克风初始化失败") }
                return
            }

            audioRecord!!.startRecording()
            isRecording = true
            recordedData = ByteArrayOutputStream()

            Log.e(TAG, "Recording started")

            mainHandler.post { callback?.onRecordingStart() }

            recordingThread = Thread { recordingLoop() }
            recordingThread!!.start()
        } catch (e: Exception) {
            Log.e(TAG, "Start recording error: " + e.message)
            mainHandler.post { callback?.onRecordingError(e.message ?: "Unknown error") }
        }
    }

    private fun recordingLoop() {
        val buffer = ByteArray(Constants.CHUNK_SIZE)

        while (isRecording) {
            val bytesRead = audioRecord!!.read(buffer, 0, Constants.CHUNK_SIZE)

            if (bytesRead > 0) {
                val validData = Arrays.copyOf(buffer, bytesRead)

                saveToFile(validData, true)

                recordedData?.write(validData, 0, bytesRead)

                mainHandler.post { callback?.onRecordingData(validData, bytesRead) }
            } else if (bytesRead < 0) {
                Log.e(TAG, "Recording error: $bytesRead")
                break
            }
        }
    }

    fun stopRecording() {
        if (!isRecording) {
            return
        }

        isRecording = false

        try {
            recordingThread?.join(500)
            recordingThread = null

            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null

            pcmFileOutputStream?.close()
            pcmFileOutputStream = null
            Log.e(TAG, "PCM file saved: " + pcmFile!!.absolutePath)

            Log.e(TAG, "Recording stopped")

            val fullData = recordedData?.toByteArray()
            fullData?.let {
                Log.e(TAG, "Total recorded: " + it.size + " bytes")
            }

            mainHandler.post { callback?.onRecordingStop(fullData) }
        } catch (e: Exception) {
            Log.e(TAG, "Stop recording error: " + e.message)
            mainHandler.post { callback?.onRecordingError(e.message ?: "Unknown error") }
        }
    }

    fun release() {
        stopRecording()
        instance = null
    }

    companion object {
        private const val TAG = "AudioRecorderManager"
        private var instance: AudioRecorderManager? = null

        @JvmStatic
        @Synchronized
        fun getInstance(): AudioRecorderManager {
            if (instance == null) {
                instance = AudioRecorderManager()
            }
            return instance!!
        }
    }
}
