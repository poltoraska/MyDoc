package com.poltorashka.documents.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

// ИЗМЕНЕНИЕ 1: Добавилен FolderEntity::class и изменили version на 3
@Database(entities = [DocumentEntity::class, FolderEntity::class], version = 3, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun documentDao(): DocumentDao
    // ИЗМЕНЕНИЕ 2: Добавилен FolderDao
    abstract fun folderDao(): FolderDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "documents_database"
                )
                    .fallbackToDestructiveMigration() // Защита от крашей при смене версии
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}