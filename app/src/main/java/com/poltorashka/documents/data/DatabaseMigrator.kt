package com.poltorashka.documents

import android.content.Context
import androidx.sqlite.db.SupportSQLiteOpenHelper
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.io.File

object DatabaseMigrator {

    fun encryptIfNeeded(context: Context, dbName: String, password: ByteArray) {
        val dbFile = context.getDatabasePath(dbName)

        // 1. Если файла нет или он уже зашифрован — ничего не делает
        if (!dbFile.exists() || isDatabaseEncrypted(context, dbName)) return

        // Создает временный файл В ТОЙ ЖЕ ПАПКЕ, что и база (это критично для renameTo)
        val tempDbFile = File(dbFile.parent, "temp_migration.db")
        if (tempDbFile.exists()) tempDbFile.delete()

        try {
            // 2. Открываем старую базу через стандартный SQLite (без SQLCipher)
            // Это надежнее для чтения старых WAL файлов
            val oldDb = android.database.sqlite.SQLiteDatabase.openDatabase(
                dbFile.absolutePath,
                null,
                android.database.sqlite.SQLiteDatabase.OPEN_READWRITE
            )

            // Принудительно сбрасывает WAL в основной файл базы перед шифрованием
            oldDb.rawQuery("PRAGMA wal_checkpoint(FULL)", null).use { it.moveToFirst() }

            // 3. Подключает SQLCipher и экспортируем данные
            val passwordString = String(password, Charsets.UTF_8).replace("'", "''")

            // Выполняет экспорт средствами SQLite
            oldDb.execSQL("ATTACH DATABASE '${tempDbFile.absolutePath}' AS encrypted KEY '$passwordString'")
            oldDb.execSQL("SELECT sqlcipher_export('encrypted')")
            oldDb.execSQL("DETACH DATABASE encrypted")
            oldDb.close()

            // 4. ФИНАЛЬНЫЙ ОБМЕН ФАЙЛАМИ
            if (tempDbFile.exists() && tempDbFile.length() > 0) {
                dbFile.delete()
                // Теперь renameTo сработает мгновенно и надежно
                if (tempDbFile.renameTo(dbFile)) {
                    // Чистит старые хвосты
                    File(dbFile.path + "-wal").delete()
                    File(dbFile.path + "-shm").delete()
                    File(dbFile.path + "-journal").delete()
                }
            }
        } catch (e: Exception) {
            // Если что-то пошло не так, не удаляет оригинал!
            // Приложение упадет, но данные останутся для следующей попытки
            tempDbFile.delete()
            throw e
        }
    }

    private fun isDatabaseEncrypted(context: Context, dbName: String): Boolean {
        val dbFile = context.getDatabasePath(dbName)
        if (!dbFile.exists()) return false
        return try {
            dbFile.inputStream().use { input ->
                val header = ByteArray(16)
                input.read(header, 0, 16)
                String(header) != "SQLite format 3\u0000"
            }
        } catch (_: Exception) { true }
    }

    // Метод шифрования фото остается без изменений
    fun encryptExistingPhotos(context: Context) {
        val filesDir = context.filesDir
        val photos = filesDir.listFiles { file -> file.extension == "jpg" } ?: return
        photos.forEach { file ->
            if (!isFileEncrypted(file)) {
                try {
                    val tempFile = File(context.cacheDir, "temp_mig_${file.name}")
                    file.renameTo(tempFile)
                    val encryptedFile = FileSecurity.getEncryptedFile(context, file)
                    tempFile.inputStream().use { input ->
                        encryptedFile.openFileOutput().use { output -> input.copyTo(output) }
                    }
                    tempFile.delete()
                } catch (_: Exception) {}
            }
        }
    }

    private fun isFileEncrypted(file: File): Boolean {
        return try {
            file.inputStream().use { input ->
                val firstBytes = ByteArray(2)
                input.read(firstBytes, 0, 2)
                val isJpg = firstBytes[0] == 0xFF.toByte() && firstBytes[1] == 0xD8.toByte()
                !isJpg
            }
        } catch (_: Exception) { true }
    }
}