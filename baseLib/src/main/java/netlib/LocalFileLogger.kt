package netlib

import okhttp3.logging.HttpLoggingInterceptor
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LocalFileLogger(private val logFile: File) : HttpLoggingInterceptor.Logger {
    val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
    override fun log(message: String) {
        try {
            val parentDir = logFile.parentFile
            if (!parentDir.exists()) {
                val isCreated = parentDir.mkdirs()
                if (!isCreated) {
                    throw IOException("Failed to create directory: ${parentDir.absolutePath}")
                }
            }
            FileWriter(logFile, true).use { writer ->
                writer.write("$timestamp--"+message + "\n")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
