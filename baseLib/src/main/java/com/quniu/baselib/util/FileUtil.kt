package com.quniu.baselib.util

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.util.Log
import com.quniu.baselib.BaseApplication
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.text.DecimalFormat
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream


object FileUtil {

    const val SIZETYPE_B = 1 //获取文件大小单位为B的double值
    const val SIZETYPE_KB = 2 //获取文件大小单位为KB的double值
    const val SIZETYPE_MB = 3 //获取文件大小单位为MB的double值
    const val SIZETYPE_GB = 4 //获取文件大小单位为GB的double值
    /**
     * 保存音频到文件
     * @param data 音频数据
     * @param isOpus 是否为opus格式
     * @return 文件路径或null
     */
    fun saveAudioToFile(data: ByteArray, isOpus: Boolean = true): String? {
        return try {
            val fileName = if (isOpus) {
                "audio_${System.currentTimeMillis()}.opus"
            } else {
                "audio_${System.currentTimeMillis()}.pcm"
            }

            val file = File(BaseApplication.mContext.cacheDir, fileName)
            FileOutputStream(file).use { it.write(data) }
            file.absolutePath
        } catch (_: Exception) {
            null
        }
    }
    /**
     * 将文件转为byte[]
     *
     * @param filePath 文件路径
     * @return
     */
    fun getBytes(filePath: String?): ByteArray {
        if (filePath.isNullOrBlank()) return ByteArray(0)
        val file = File(filePath)
        try {
            if (file.exists()) {
                FileInputStream(file).use { fis ->
                    ByteArrayOutputStream().use { baos ->
                        val buffer = ByteArray(1024)
                        var bytesRead: Int
                        while (fis.read(buffer).also { bytesRead = it } != -1) {
                            baos.write(buffer, 0, bytesRead)
                        }
                        return baos.toByteArray()
                    }
                }
            }
        } catch (e: FileNotFoundException) {
            e.printStackTrace()
        } catch (e: IOException) {
            e.printStackTrace()
        }

        // fallback: try via ContentResolver (e.g., content:// or file descriptor)
        val contentResolver: ContentResolver = BaseApplication.mContext.contentResolver
        try {
            val uri = try {
                Uri.parse(filePath)
            } catch (t: Throwable) {
                null
            }
            if (uri != null) {
                contentResolver.openInputStream(uri)?.use { istream ->
                    ByteArrayOutputStream().use { baos ->
                        val buffer = ByteArray(1024)
                        var bytesRead: Int
                        while (istream.read(buffer).also { bytesRead = it } != -1) {
                            baos.write(buffer, 0, bytesRead)
                        }
                        return baos.toByteArray()
                    }
                }
            }
        } catch (e: FileNotFoundException) {
            e.printStackTrace()
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return ByteArray(0)
    }

    /**
     * 获取文件指定文件的指定单位的大小
     *
     * @param filePath 文件路径
     * @param sizeType 获取大小的类型1为B、2为KB、3为MB、4为GB
     * @return double值的大小
     */
    fun getFileOrFilesSize(filePath: String?, sizeType: Int): Double {
        if (filePath.isNullOrBlank()) return 0.0
        val file = File(filePath)
        var blockSize: Long = 0
        try {
            blockSize = if (file.isDirectory) {
                getFileSizes(file)
            } else {
                getFileSize(file)
            }
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
            Log.e("获取文件大小", "获取失败!")
        }
        return FormetFileSize(blockSize, sizeType)
    }

    /**
     * 调用此方法自动计算指定文件或指定文件夹的大小
     *
     * @param filePath 文件路径
     * @return 计算好的带B、KB、MB、GB的字符串
     */
    fun getAutoFileOrFilesSize(filePath: String?): String {
        if (filePath.isNullOrBlank()) return "0B"
        val file = File(filePath)
        var blockSize: Long = 0
        try {
            blockSize = if (file.isDirectory) {
                getFileSizes(file)
            } else {
                getFileSize(file)
            }
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
            Log.e("获取文件大小", "获取失败!")
        }
        return FormetFileSize(blockSize)
    }

    /**
     * 获取指定文件大小
     *
     * @param
     * @return
     * @throws Exception
     */
    @Throws(java.lang.Exception::class)
    private fun getFileSize(file: File): Long {
        if (!file.exists()) return 0L
        return file.length()
    }

    /**
     * 获取指定文件夹
     *
     * @param f
     * @return
     * @throws Exception
     */
    @Throws(java.lang.Exception::class)
    private fun getFileSizes(f: File): Long {
        var size: Long = 0
        val flist = f.listFiles() ?: return 0L
        for (child in flist) {
            size = if (child.isDirectory) {
                size + getFileSizes(child)
            } else {
                size + getFileSize(child)
            }
        }
        return size
    }

    /**
     * 转换文件大小
     *
     * @param fileS
     * @return
     */
    private fun FormetFileSize(fileS: Long): String {
        val df = DecimalFormat("#.00")
        var fileSizeString = ""
        val wrongSize = "0B"
        if (fileS == 0L) {
            return wrongSize
        }
        fileSizeString = if (fileS < 1024) {
            df.format(fileS.toDouble()) + "B"
        } else if (fileS < 1048576) {
            df.format(fileS.toDouble() / 1024) + "KB"
        } else if (fileS < 1073741824) {
            df.format(fileS.toDouble() / 1048576) + "MB"
        } else {
            df.format(fileS.toDouble() / 1073741824) + "GB"
        }
        return fileSizeString
    }

    /**
     * 转换文件大小,指定转换的类型
     *
     * @param fileS
     * @param sizeType
     * @return
     */
    private fun FormetFileSize(fileS: Long, sizeType: Int): Double {
        try {
            val df = DecimalFormat("#.00")
            var fileSizeLong = 0.0
            when (sizeType) {
                SIZETYPE_B -> fileSizeLong = java.lang.Double.valueOf(df.format(fileS.toDouble()))
                SIZETYPE_KB -> fileSizeLong =
                    java.lang.Double.valueOf(df.format(fileS.toDouble() / 1024))
                SIZETYPE_MB -> fileSizeLong =
                    java.lang.Double.valueOf(df.format(fileS.toDouble() / 1048576))
                SIZETYPE_GB -> fileSizeLong =
                    java.lang.Double.valueOf(df.format(fileS.toDouble() / 1073741824))
                else -> {
                }
            }
            return fileSizeLong
        }catch (_: Exception){
            return 0.00
        }


    }

    /**
     *
     * 删除文件夹
     *
     * @param folderPath String 文件夹路径及名称 如c:/fqf
     *
     * @param fileContent String
     *
     * @return boolean
     */
    fun delFolder(folderPath: String): Boolean {
        try {
            delAllFile(folderPath) //删除完里面所有内容
            return true
//            var filePath = folderPath
//            filePath = filePath
//            val myFilePath = File(filePath)
//            myFilePath.delete() //删除空文件夹
        } catch (_: Exception) {
            println("删除文件夹操作出错")
        }
        return false
    }

    /**
     *
     * 删除文件夹里面的所有文件
     *
     * @param path String 文件夹路径 如 c:/fqf
     */
    fun delAllFile(path: String) {
        val file = File(path)
        if (!file.exists()) {
            return
        }
        if (!file.isDirectory) {
            return
        }
        val tempList = file.list()
        var temp: File?
        if (tempList != null) {
            for (i in tempList.indices) {
                temp = if (path.endsWith(File.separator)) {
                    File(path + tempList[i])
                } else {
                    File(path + File.separator + tempList[i])
                }
                if (temp.isFile) {
                    temp.delete()
                }
                if (temp.isDirectory) {
                    delAllFile(path + "/" + tempList[i]) //先删除文件夹里面的文件
                    delFolder(path + "/" + tempList[i]) //再删除空文件夹
                }
            }
        }
    }


    private val hexDigits =
        charArrayOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f')

    fun copyAssetsToStorage(
        context: Context,
        dir: String,
        files: Array<String>,
        loadSuccess: () -> Unit
    ) {
        var outputStream: OutputStream
        var inputStream: InputStream
        val buf = ByteArray(4096)
        files.forEach {
            try {
                if (File("$dir/$it").exists()) {
                    return@forEach
                }
                inputStream = context.assets.open(it)
                outputStream = FileOutputStream("$dir/$it")
                var length = inputStream.read(buf)
                while (length > 0) {
                    outputStream.write(buf, 0, length)
                    length = inputStream.read(buf)
                }
                outputStream.close()
                inputStream.close()
            } catch (_: IOException) {
                return
            }
        }
        loadSuccess.invoke()

    }

    fun getFileMD5(file: File): String? {
        if (!file.exists() || !file.isFile || file.length() <= 0) {
            return null
        }
        var inputStream: InputStream? = null
        try {
            val md = MessageDigest.getInstance("MD5")
            inputStream = FileInputStream(file)
            val dataBytes = ByteArray(4096)
            var iRd: Int
            iRd = inputStream.read(dataBytes)
            while (iRd != -1) {
                md.update(dataBytes, 0, iRd)
                iRd = inputStream.read(dataBytes)
            }
            inputStream.close()
            val digest = md.digest()
            if (digest != null) {
                return bufferToHex(digest)
            }
        } catch (t: Throwable) {
            t.printStackTrace()
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close()
                } catch (_: Throwable) {
                }

            }
        }
        return null
    }

    private fun bufferToHex(bytes: ByteArray): String {
        return bufferToHex(bytes, 0, bytes.size)
    }

    private fun bufferToHex(bytes: ByteArray, m: Int, n: Int): String {
        val sb = StringBuffer(2 * n)
        val k = m + n
        for (l in m until k) {
            appendHexPair(bytes[l], sb)
        }
        return sb.toString()
    }

    private fun appendHexPair(bt: Byte, sb: StringBuffer) {
        val c0 = hexDigits[bt.toInt() and 0xf0 ushr 4]
        val c1 = hexDigits[bt.toInt() and 0x0f]
        sb.append(c0)
        sb.append(c1)
    }

    /**
     * 同步解压（可在协程里调用）
     * @param zipFile  下载得到的 zip 绝对路径
     * @param targetDir 解压目标文件夹
     * @return 解压后的根目录（便于跳转）
     */
fun unzipSync(zipFile: String, targetDir: String): File {
    val file = File(zipFile)
        file.parentFile?.mkdirs()
    // 添加验证逻辑
    if (!file.exists()) {
        throw FileNotFoundException("Zip file does not exist: $zipFile")
    }

    if (!file.isFile) {
        throw IllegalArgumentException("Path is not a file: $zipFile")
    }

    val rootDir = File(targetDir)
    rootDir.mkdirs()

    val canonicalRoot = rootDir.canonicalPath + File.separator

    ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zin ->
        var entry: ZipEntry?
        while (zin.nextEntry.also { entry = it } != null) {
            entry?.let { e ->
                val outputFile = File(rootDir, e.name)
                val canonicalDest = try { outputFile.canonicalPath } catch (_: Throwable) { null }
                if (canonicalDest == null || !canonicalDest.startsWith(canonicalRoot)) {
                    // 路径穿越：跳过该条目以保证安全
                    zin.closeEntry()
                    return@let
                }

                if (e.isDirectory) {
                    outputFile.mkdirs()
                } else {
                    outputFile.parentFile?.mkdirs()
                    BufferedOutputStream(FileOutputStream(outputFile)).use { out ->
                        // 使用内置的拷贝，避免错误地写入多余字节
                        zin.copyTo(out)
                    }
                }
                zin.closeEntry()
            }
        }
    }
    return rootDir
}


}