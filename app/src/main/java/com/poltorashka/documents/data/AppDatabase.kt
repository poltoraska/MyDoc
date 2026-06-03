package com.poltorashka.documents.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import android.util.Base64
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.poltorashka.documents.DatabaseMigrator
import java.security.SecureRandom
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

// Версия БД до 4
@Database(entities = [DocumentEntity::class, FolderEntity::class], version = 4, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun documentDao(): DocumentDao
    abstract fun folderDao(): FolderDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // Правило миграции с 3 на 4 версию (добавление колонки orderIndex)
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Новая колонка без потери существующих документов
                database.execSQL("ALTER TABLE documents ADD COLUMN orderIndex INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                System.loadLibrary("sqlcipher")

                val masterKey = MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()

                val sharedPreferences = EncryptedSharedPreferences.create(
                    context,
                    "secure_db_prefs",
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )

                var dbPassword = sharedPreferences.getString("db_password", null)
                if (dbPassword == null) {
                    val random = SecureRandom()
                    val bytes = ByteArray(32)
                    random.nextBytes(bytes)
                    dbPassword = Base64.encodeToString(bytes, Base64.NO_WRAP)

                    sharedPreferences.edit().putString("db_password", dbPassword).apply()
                }

                val passwordBytes = dbPassword.toByteArray()

                DatabaseMigrator.encryptIfNeeded(context, "documents_db", passwordBytes)

                val factory = SupportOpenHelperFactory(passwordBytes)

                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "documents_db"
                )
                    .openHelperFactory(factory)
                    .addMigrations(MIGRATION_3_4) // Подключение правило миграции
                    .build()

                INSTANCE = instance
                instance
            }
        }
    }
}