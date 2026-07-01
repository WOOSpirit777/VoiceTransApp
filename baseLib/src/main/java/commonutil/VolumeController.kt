package commonutil
import android.content.Context
import android.media.AudioManager
import com.apkfuns.logutils.LogUtils
import kotlin.math.max
import kotlin.math.min

class VolumeController(context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    /**
     * 获取系统最大音量值
     */
    fun getMaxVolume(): Int {
        return audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    }

    /**
     * 获取系统最小音量值
     * 注意：getStreamMinVolume需要API 28，为兼容低版本返回固定值0
     */
    fun getMinVolume(): Int {
        return audioManager.getStreamMinVolume(AudioManager.STREAM_MUSIC)
    }

    /**
     * 获取当前音量值
     */
    fun getCurrentVolume(): Int {
        return audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
    }

    /**
     * 计算音量等级区间
     * @return 每个等级对应的音量值增量
     */
    private fun getVolumeStep(): Int {
        val maxVolume = getMaxVolume()
        val minVolume = getMinVolume()
        val totalRange = maxVolume - minVolume

        // 将区间等分成5份
        return if (totalRange > 0) {
            max(1, totalRange / 5)
        } else {
            1
        }
    }

    /**
     * 音量增加一个等级
     */
    fun volumeUp() {
        val currentVolume = getCurrentVolume()
        val maxVolume = getMaxVolume()
        val volumeStep = getVolumeStep()

        // 计算增加一个等级后的音量值
        val newVolume = currentVolume + volumeStep

        // 检查是否超过最大音量值
        if (newVolume >= maxVolume) {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxVolume, 0)
        } else {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, 0)
        }
        getCurrentVolumeLevel()
    }

    /**
     * 音量减少一个等级
     */
    fun volumeDown() {
        val currentVolume = getCurrentVolume()
        val minVolume = getMinVolume()
        val volumeStep = getVolumeStep()

        // 计算减少一个等级后的音量值
        val newVolume = currentVolume - volumeStep

        // 检查是否小于最小音量值
        if (newVolume <= minVolume) {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, minVolume, 0)
        } else {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, 0)
        }
        getCurrentVolumeLevel()
    }

    /**
     * 获取当前音量等级 (0-5)
     * @return 当前音量等级
     */
    fun getCurrentVolumeLevel(): Int {
        val currentVolume = getCurrentVolume()
        val minVolume = getMinVolume()
        val maxVolume = getMaxVolume()
        val volumeStep = getVolumeStep()

        if (maxVolume == minVolume) return 0

        val level = ((currentVolume - minVolume).toFloat() / volumeStep.toFloat()).toInt()
        LogUtils.e("currentVolume:$currentVolume minVolume:$minVolume maxVolume:$maxVolume volumeStep:$volumeStep level:$level")
        return min(5, max(0, level))
    }

    /**
 * 音量增加一个等级（带系统弹窗）
 */
fun volumeUpWithSystemUI() {
    val currentVolume = getCurrentVolume()
    val maxVolume = getMaxVolume()
    val volumeStep = getVolumeStep()

    // 计算增加一个等级后的音量值
    val newVolume = currentVolume + volumeStep

    // 检查是否超过最大音量值
    if (newVolume >= maxVolume) {
        audioManager.setStreamVolume(
            AudioManager.STREAM_MUSIC,
            maxVolume,
            AudioManager.FLAG_SHOW_UI
        )
    } else {
        audioManager.setStreamVolume(
            AudioManager.STREAM_MUSIC,
            newVolume,
            AudioManager.FLAG_SHOW_UI
        )
    }
}

/**
 * 音量减少一个等级（带系统弹窗）
 */
fun volumeDownWithSystemUI() {
    val currentVolume = getCurrentVolume()
    val minVolume = getMinVolume()
    val volumeStep = getVolumeStep()

    // 计算减少一个等级后的音量值
    val newVolume = currentVolume - volumeStep

    // 检查是否小于最小音量值
    if (newVolume <= minVolume) {
        audioManager.setStreamVolume(
            AudioManager.STREAM_MUSIC,
            minVolume,
            AudioManager.FLAG_SHOW_UI
        )
    } else {
        audioManager.setStreamVolume(
            AudioManager.STREAM_MUSIC,
            newVolume,
            AudioManager.FLAG_SHOW_UI
        )
    }
}

}
