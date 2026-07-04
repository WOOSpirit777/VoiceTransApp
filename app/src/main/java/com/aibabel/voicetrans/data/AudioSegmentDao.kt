package com.aibabel.voicetrans.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface AudioSegmentDao {

    @Insert
    suspend fun insert(segment: AudioSegment): Long

    @Insert
    suspend fun insertAll(segments: List<AudioSegment>): List<Long>

    @Query("SELECT * FROM audio_segments WHERE sessionId = :sessionId AND isSent = 0 ORDER BY segmentIndex ASC")
    suspend fun getUnsentBySession(sessionId: String): List<AudioSegment>

    @Query("SELECT * FROM audio_segments WHERE sessionId = :sessionId ORDER BY segmentIndex ASC")
    suspend fun getAllBySession(sessionId: String): List<AudioSegment>

    @Query("UPDATE audio_segments SET isSent = 1 WHERE id = :id")
    suspend fun markAsSent(id: Long)

    @Query("SELECT COUNT(*) FROM audio_segments WHERE sessionId = :sessionId AND isSent = 0")
    suspend fun getUnsentCount(sessionId: String): Int

    @Query("DELETE FROM audio_segments WHERE sessionId = :sessionId")
    suspend fun deleteBySession(sessionId: String)

    @Query("DELETE FROM audio_segments WHERE isSent = 1 AND createdAt < :before")
    suspend fun deleteOldSent(before: Long)
}
