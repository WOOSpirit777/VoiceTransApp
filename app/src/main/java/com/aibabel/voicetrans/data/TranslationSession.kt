package com.aibabel.voicetrans.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 翻译会话数据实体
 * 以sessionId为主键，相同sessionId的数据追加更新
 * @param sessionId 会话ID（与录音sessionId一致）
 * @param sourceText 源文本（累积追加）
 * @param targetText 目标文本（累积追加）
 * @param ttsAudioData TTS音频数据（累积追加）
 * @param createdAt 创建时间
 * @param updatedAt 最后更新时间
 */
@Entity(tableName = "translation_session")
data class TranslationSession(
    @PrimaryKey
    val sessionId: String,
    val sourceLang: String? = null,
    val targetLang: String? = null,
    val sourceText: String? = null,
    val targetText: String? = null,
    val ttsAudioData: ByteArray? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TranslationSession) return false
        return sessionId == other.sessionId
    }

    override fun hashCode(): Int = sessionId.hashCode()
}