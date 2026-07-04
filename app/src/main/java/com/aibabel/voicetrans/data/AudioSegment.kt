package com.aibabel.voicetrans.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 录音分段数据实体
 * @param sessionId 会话ID（每次录音一个）
 * @param segmentIndex 分段序号
 * @param audioData 音频数据
 * @param isSent 是否已发送
 * @param createdAt 创建时间
 */
@Entity(tableName = "audio_segments")
data class AudioSegment(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sessionId: String,
    val segmentIndex: Int,
    val audioData: ByteArray,
    val isSent: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AudioSegment) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}
