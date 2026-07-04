package com.aibabel.voicetrans.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.aibabel.voicetrans.util.Constants
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class AudioPlayerManager private constructor() {

    interface AudioPlayerCallback {
        fun onPlaybackStart()
        fun onPlaybackComplete()
        fun onPlaybackError(error: String)
    }

    private var audioTrack: AudioTrack? = null
    @Volatile
    private var isPlaying = false
    @Volatile
    private var isStreaming = false
    private val audioBuffer = java.util.concurrent.LinkedBlockingQueue<ByteArray>()
    private var executor: ExecutorService
    private var mainHandler: Handler
    private var callback: AudioPlayerCallback? = null

    init {
        executor = Executors.newSingleThreadExecutor()
        mainHandler = Handler(Looper.getMainLooper())
    }

    fun setCallback(callback: AudioPlayerCallback?) {
        this.callback = callback
    }

    fun isPlaying(): Boolean = isPlaying

    fun isStreaming(): Boolean = isStreaming

    fun playPcm(pcmData: ByteArray) {
        if (isPlaying) {
            stop()
        }

        executor.execute {
            try {
                val bufferSize = AudioTrack.getMinBufferSize(
                    Constants.SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )

                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(Constants.SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(bufferSize)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()

                audioTrack = track
                track.play()
                isPlaying = true

                mainHandler.post {
                    callback?.onPlaybackStart()
                }

                var offset = 0
                while (isPlaying && offset < pcmData.size) {
                    val length = minOf(bufferSize, pcmData.size - offset)
                    track.write(pcmData, offset, length)
                    offset += length
                }

                Thread.sleep(100)

                mainHandler.post {
                    isPlaying = false
                    callback?.onPlaybackComplete()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Play error: " + e.message)
                mainHandler.post {
                    isPlaying = false
                    callback?.onPlaybackError(e.message ?: "Unknown error")
                }
            }
        }
    }

    fun startStreamingPlayback() {
        if (isPlaying) {
            stop()
        }

        audioBuffer.clear()
        isStreaming = true
        isPlaying = true

        executor.execute {
            try {
                val bufferSize = AudioTrack.getMinBufferSize(
                    Constants.SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )

                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(Constants.SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(bufferSize * 4)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()

                audioTrack = track
                track.play()

                mainHandler.post {
                    callback?.onPlaybackStart()
                }

                val tmpBuffer = ByteArray(bufferSize)
                var tmpOffset = 0

                while (isPlaying && (isStreaming || audioBuffer.isNotEmpty())) {
                    val chunk = audioBuffer.poll(100, java.util.concurrent.TimeUnit.MILLISECONDS)
                    if (chunk != null) {
                        var chunkOffset = 0
                        while (chunkOffset < chunk.size) {
                            val copyLen = minOf(bufferSize - tmpOffset, chunk.size - chunkOffset)
                            System.arraycopy(chunk, chunkOffset, tmpBuffer, tmpOffset, copyLen)
                            tmpOffset += copyLen
                            chunkOffset += copyLen

                            if (tmpOffset >= bufferSize) {
                                track.write(tmpBuffer, 0, bufferSize)
                                tmpOffset = 0
                            }
                        }
                    }
                }

                if (tmpOffset > 0) {
                    track.write(tmpBuffer, 0, tmpOffset)
                }

                Thread.sleep(200)

                mainHandler.post {
                    isPlaying = false
                    isStreaming = false
                    callback?.onPlaybackComplete()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Stream play error: " + e.message)
                mainHandler.post {
                    isPlaying = false
                    isStreaming = false
                    callback?.onPlaybackError(e.message ?: "Unknown error")
                }
            }
        }
    }

    fun feedAudioData(data: ByteArray) {
        if (isStreaming) {
            audioBuffer.offer(data)
        }
    }

    fun stopStreamingPlayback() {
        isStreaming = false
    }

    fun playFromUrl(url: String) {
        if (isPlaying) {
            stop()
        }

        executor.execute {
            var connection: HttpURLConnection? = null
            var inputStream: InputStream? = null
            var outputStream: ByteArrayOutputStream? = null

            try {
                val audioUrl = URL(url)
                connection = audioUrl.openConnection() as HttpURLConnection
                connection.connectTimeout = 5000
                connection.readTimeout = 10000
                connection.connect()

                val responseCode = connection.responseCode
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    mainHandler.post {
                        callback?.onPlaybackError("HTTP $responseCode")
                    }
                    return@execute
                }

                inputStream = connection.inputStream
                val header = ByteArray(4)
                inputStream.read(header)

                val isWav = header[0] == 'R'.code.toByte() &&
                        header[1] == 'I'.code.toByte() &&
                        header[2] == 'F'.code.toByte() &&
                        header[3] == 'F'.code.toByte()

                if (isWav) {
                    val skip = ByteArray(40)
                    inputStream.read(skip)
                } else {
                    inputStream.close()
                    inputStream = connection.inputStream
                }

                outputStream = ByteArrayOutputStream()
                val buffer = ByteArray(4096)
                var bytesRead: Int
                while ((inputStream.read(buffer).also { bytesRead = it }) != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                }

                val audioData = outputStream.toByteArray()
                playPcm(audioData)
            } catch (e: Exception) {
                Log.e(TAG, "Play from URL error: " + e.message)
                mainHandler.post {
                    callback?.onPlaybackError(e.message ?: "Unknown error")
                }
            } finally {
                try {
                    inputStream?.close()
                    outputStream?.close()
                    connection?.disconnect()
                } catch (e: IOException) {
                }
            }
        }
    }

    fun stop() {
        isPlaying = false
        audioTrack?.let {
            try {
                it.stop()
                it.release()
            } catch (e: Exception) {
                Log.e(TAG, "Stop error: " + e.message)
            }
            audioTrack = null
        }
    }

    fun release() {
        stop()
        executor.shutdownNow()
        executor = Executors.newSingleThreadExecutor()
        instance = null
    }

    companion object {
        private const val TAG = "AudioPlayerManager"
        private var instance: AudioPlayerManager? = null

        @JvmStatic
        @Synchronized
        fun getInstance(): AudioPlayerManager {
            if (instance == null) {
                instance = AudioPlayerManager()
            }
            return instance!!
        }
    }
}
