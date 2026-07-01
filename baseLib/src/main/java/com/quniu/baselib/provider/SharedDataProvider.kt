package com.quniu.baselib.provider

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import com.quniu.baselib.util.MMKVConst
import com.quniu.baselib.util.MMKVUtils

/**
 * A minimal ContentProvider to expose simple shared values (like speech_count) to other processes.
 * - URI format: content://{authority}/speech_count
 * - Insert/Update expects ContentValues with key "value" (Int)
 * - Query returns a cursor with single column "value" and one row containing the int value
 */
class SharedDataProvider : ContentProvider() {

    companion object {
        private const val PATH_SPEECH_COUNT = "speech_count"
    }

    override fun onCreate(): Boolean {
        // Ensure MMKV is initialized (MMKVUtils.initialize is normally called by Application)
        try {
            context?.applicationContext?.let { MMKVUtils.initialize(it) }
        } catch (_: Throwable) {}
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        val lastPath = uri.lastPathSegment ?: return null
        if (lastPath == PATH_SPEECH_COUNT) {
            val value = try {
                // read from multi-process MMKV instance
                MMKVUtils.getMultiInt(MMKVConst.SPEECH_COUNT, 0, MMKVConst.MMKV_ID)
            } catch (_: Throwable) { 0 }
            val cursor = android.database.MatrixCursor(arrayOf("value"))
            cursor.addRow(arrayOf(value))
            return cursor
        }
        return null
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        val lastPath = uri.lastPathSegment ?: return null
        if (lastPath == PATH_SPEECH_COUNT) {
            val v = values?.getAsInteger("value") ?: return null
            try {
                MMKVUtils.putMultiInt(MMKVConst.SPEECH_COUNT, v, MMKVConst.MMKV_ID)
            } catch (_: Throwable) {}
            return uri
        }
        return null
    }

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int {
        val lastPath = uri.lastPathSegment ?: return 0
        if (lastPath == PATH_SPEECH_COUNT) {
            val v = values?.getAsInteger("value") ?: return 0
            return try {
                MMKVUtils.putMultiInt(MMKVConst.SPEECH_COUNT, v, MMKVConst.MMKV_ID)
                1
            } catch (_: Throwable) { 0 }
        }
        return 0
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        val lastPath = uri.lastPathSegment ?: return 0
        if (lastPath == PATH_SPEECH_COUNT) {
            try {
                MMKVUtils.putMultiInt(MMKVConst.SPEECH_COUNT, 0, MMKVConst.MMKV_ID)
                return 1
            } catch (_: Throwable) {}
        }
        return 0
    }

    override fun getType(uri: Uri): String? = null
}
