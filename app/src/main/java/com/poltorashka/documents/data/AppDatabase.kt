package com.poltorashka.documents.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.poltorashka.documents.DatabaseMigrator
import java.security.SecureRandom
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

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
                System.loadLibrary("sqlcipher")
                // 1. Создает Мастер-ключ, который живет в аппаратном чипе Keystore
                val masterKey = MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()

                // 2. Создает зашифрованное хранилище настроек
                val sharedPreferences = EncryptedSharedPreferences.create(
                    context,
                    "secure_db_prefs",
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )

                // 3. Достает пароль от базы. Если его нет - генерируем новый
                var dbPassword = sharedPreferences.getString("db_password", null)
                if (dbPassword == null) {
                    val random = SecureRandom()
                    val bytes = ByteArray(32)
                    random.nextBytes(bytes) // Генерирует 256 бит случайных данных
                    dbPassword = Base64.encodeToString(bytes, Base64.NO_WRAP)

                    sharedPreferences.edit().putString("db_password", dbPassword).apply()
                }

                val passwordBytes = dbPassword.toByteArray()

                // Запускает проверку и перенос данных
                DatabaseMigrator.encryptIfNeeded(context, "documents_db", passwordBytes)

                // 4. Создёт фабрику и саму базу как обычно
                val factory = SupportOpenHelperFactory(passwordBytes)

                // 5. Строит базу Room, используя фабрику шифрования
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "documents_db"
                )
                    .openHelperFactory(factory) // МАГИЯ ШИФРОВАНИЯ
                    .build()

                INSTANCE = instance
                instance
            }
        }
    }
}