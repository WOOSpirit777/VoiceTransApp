package com.aibabel.voicetrans.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 录音分段数据仓库
 * 负责音频分段的入库、查询、标记、清理
 */
class AudioSegmentRepository(context: Context) {

    private val dao = AppDatabase.getInstance(context).audioSegmentDao()

    suspend fun insertSegment(sessionId: String, segmentIndex: Int, audioData: ByteArray): Long {
        return withContext(Dispatchers.IO) {
            val segment = AudioSegment(
                sessionId = sessionId,
                segmentIndex = segmentIndex,
                audioData = audioData
            )
            dao.insert(segment)
        }
    }

    suspend fun getUnsentSegments(sessionId: String): List<AudioSegment> {
        return withContext(Dispatchers.IO) {
            dao.getUnsentBySession(sessionId)
        }
    }

    suspend fun markAsSent(id: Long) {
        withContext(Dispatchers.IO) {
            dao.markAsSent(id)
        }
    }

    suspend fun getUnsentCount(sessionId: String): Int {
        return withContext(Dispatchers.IO) {
            dao.getUnsentCount(sessionId)
        }
    }

    suspend fun deleteBySession(sessionId: String) {
        withContext(Dispatchers.IO) {
            dao.deleteBySession(sessionId)
        }
    }

    suspend fun cleanupOldSent(before: Long) {
        withContext(Dispatchers.IO) {
            val count = dao.deleteOldSent(before)
//            if (count > 0) Log.i(TAG, "Cleaned up $count old sent segments")
        }
    }

    companion object {
        private const val TAG = "AudioSegmentRepo"
    }
}
