package com.poltorashka.documents

import android.content.Context
import androidx.sqlite.db.SupportSQLiteOpenHelper
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.io.File
import java.io.InputStream
import java.security.MessageDigest

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

    // Encrypt old unprotected attachments, keeping the source until decryption is verified.
    fun encryptExistingPhotos(context: Context): Boolean {
        val filesDir = context.filesDir
        var complete = true
        filesDir.listFiles { file -> file.name.endsWith(".legacy_migration") }?.forEach { backup ->
            val original = File(filesDir, backup.name.removeSuffix(".legacy_migration"))
            if (original.exists() && !original.delete()) complete = false
            if (!original.exists() && !backup.renameTo(original)) complete = false
        }

        filesDir.listFiles()?.filter(FileSecurity::isLegacyPlaintext)?.forEach { file ->
            val backup = File(filesDir, "${file.name}.legacy_migration")
            var sourceMoved = false
            try {
                check(file.renameTo(backup))
                sourceMoved = true
                val encrypted = FileSecurity.getEncryptedFile(context, file)
                backup.inputStream().use { source ->
                    encrypted.openFileOutput().use { destination -> source.copyTo(destination) }
                }
                val originalHash = backup.inputStream().use(::sha256)
                val encryptedHash = encrypted.openFileInput().use(::sha256)
                check(MessageDigest.isEqual(originalHash, encryptedHash))
                check(backup.delete())
            } catch (error: Exception) {
                complete = false
                error.printStackTrace()
                if (sourceMoved) {
                    file.delete()
                    if (backup.exists()) backup.renameTo(file)
                }
            }
        }
        return complete
    }

    private fun sha256(input: InputStream): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
        return digest.digest()
    }
}
