package com.aibabel.voicetrans.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * 翻译会话数据仓库
 * 负责接收数据的保存、追加、查询、清理
 */
class TranslationSessionRepository(context: Context) {

    private val dao = AppDatabase.getInstance(context).translationSessionDao()

    /**
     * 创建新会话
     */
    suspend fun createSession(sessionId: String, sourceLang: String, targetLang: String) {
        withContext(Dispatchers.IO) {
            val session = TranslationSession(
                sessionId = sessionId,
                sourceLang = sourceLang,
                targetLang = targetLang
            )
            dao.insert(session)
            Log.i(TAG, "Session created: $sessionId")
        }
    }

    /**
     * 追加源文本
     */
    suspend fun appendSourceText(sessionId: String, text: String) {
        withContext(Dispatchers.IO) {
            dao.appendSourceText(sessionId, text, System.currentTimeMillis())
            Log.i(TAG, "Source text appended: $sessionId, length=${text.length}")
        }
    }

    /**
     * 追加目标文本
     */
    suspend fun appendTargetText(sessionId: String, text: String) {
        withContext(Dispatchers.IO) {
            dao.appendTargetText(sessionId, text, System.currentTimeMillis())
            Log.i(TAG, "Target text appended: $sessionId, length=${text.length}")
        }
    }

    /**
     * 追加TTS音频数据
     */
    suspend fun appendTtsAudio(sessionId: String, audioData: ByteArray) {
        withContext(Dispatchers.IO) {
            val existing = dao.getTtsAudioData(sessionId)
            val combined = if (existing != null) {
                ByteArrayOutputStream().use {
                    it.write(existing)
                    it.write(audioData)
                    it.toByteArray()
                }
            } else {
                audioData
            }

            val session = dao.getBySessionId(sessionId)
            if (session != null) {
                dao.update(session.copy(ttsAudioData = combined, updatedAt = System.currentTimeMillis()))
                Log.i(TAG, "TTS audio appended: $sessionId, total=${combined.size} bytes")
            }
        }
    }

    /**
     * 获取会话数据
     */
    suspend fun getSession(sessionId: String): TranslationSession? {
        return withContext(Dispatchers.IO) {
            dao.getBySessionId(sessionId)
        }
    }

    /**
     * 删除会话
     */
    suspend fun deleteSession(sessionId: String) {
        withContext(Dispatchers.IO) {
            dao.deleteBySessionId(sessionId)
            Log.i(TAG, "Session deleted: $sessionId")
        }
    }

    /**
     * 清理旧会话
     */
    suspend fun cleanupOldSessions(maxAgeMs: Long = 24 * 60 * 60 * 1000L) {
        withContext(Dispatchers.IO) {
            val before = System.currentTimeMillis() - maxAgeMs
            dao.deleteOldSessions(before)
        }
    }

    companion object {
        private const val TAG = "TranslationSessionRepo"
    }
}