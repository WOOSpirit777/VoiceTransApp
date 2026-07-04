package com.aibabel.voicetrans.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [AudioSegment::class, TranslationSession::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun audioSegmentDao(): AudioSegmentDao
    abstract fun translationSessionDao(): TranslationSessionDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "voice_trans.db"
                ).fallbackToDestructiveMigration().build().also {
                    INSTANCE = it
                }
            }
        }
    }
}
