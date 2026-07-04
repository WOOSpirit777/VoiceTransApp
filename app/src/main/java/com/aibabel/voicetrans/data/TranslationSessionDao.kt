package com.aibabel.voicetrans.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface TranslationSessionDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(session: TranslationSession): Long

    @Query("SELECT * FROM translation_session WHERE sessionId = :sessionId")
    suspend fun getBySessionId(sessionId: String): TranslationSession?

    @Update
    suspend fun update(session: TranslationSession)

    /**
     * 追加源文本
     */
    @Query("UPDATE translation_session SET sourceText = sourceText || :text, updatedAt = :timestamp WHERE sessionId = :sessionId")
    suspend fun appendSourceText(sessionId: String, text: String, timestamp: Long)

    /**
     * 追加目标文本
     */
    @Query("UPDATE translation_session SET targetText = targetText || :text, updatedAt = :timestamp WHERE sessionId = :sessionId")
    suspend fun appendTargetText(sessionId: String, text: String, timestamp: Long)

    /**
     * 获取当前TTS音频数据长度
     */
    @Query("SELECT ttsAudioData FROM translation_session WHERE sessionId = :sessionId")
    suspend fun getTtsAudioData(sessionId: String): ByteArray?

    @Query("DELETE FROM translation_session WHERE sessionId = :sessionId")
    suspend fun deleteBySessionId(sessionId: String)

    @Query("DELETE FROM translation_session WHERE updatedAt < :before")
    suspend fun deleteOldSessions(before: Long)
}