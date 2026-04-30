package com.poltorashka.documents

import android.content.Context
import androidx.sqlite.db.SupportSQLiteOpenHelper
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.io.File

object DatabaseMigrator {

    // Основной метод миграции базы данных
    fun encryptIfNeeded(context: Context, dbName: String, password: ByteArray) {
        val dbFile = context.getDatabasePath(dbName)

        // Проверяет если файла нет или он уже зашифрован — выходит
        if (!dbFile.exists() || isDatabaseEncrypted(context, dbName)) return

        val tempDbFile = File(context.cacheDir, "temp_encrypted.db")
        if (tempDbFile.exists()) tempDbFile.delete()

        try {
            val factory = SupportOpenHelperFactory("".toByteArray())
            val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {}
                    override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
                })
                .build()

            val helper = factory.create(configuration)
            val db = helper.writableDatabase

            val passwordString = password.toString(Charsets.UTF_8).replace("'", "''")
            db.execSQL("ATTACH DATABASE '${tempDbFile.absolutePath}' AS encrypted KEY '$passwordString'")

            db.query("SELECT sqlcipher_export('encrypted')").use { cursor ->
                cursor.moveToFirst()
            }

            db.execSQL("DETACH DATABASE encrypted")
            db.close()

            dbFile.delete()
            tempDbFile.renameTo(dbFile)
        } catch (_: Exception) {
        }
    }

    // Шифрование старых фотографий
    fun encryptExistingPhotos(context: Context) {
        val filesDir = context.filesDir
        // Находит все старые JPG сканы
        val photos = filesDir.listFiles { file -> file.extension == "jpg" } ?: return

        photos.forEach { file ->
            // Если файл еще НЕ зашифрован (обычный JPG) — шифрует его
            if (!isFileEncrypted(file)) {
                try {
                    val tempFile = File(context.cacheDir, "temp_photo_mig")
                    file.renameTo(tempFile)

                    val encryptedFile = FileSecurity.getEncryptedFile(context, file)
                    tempFile.inputStream().use { input ->
                        encryptedFile.openFileOutput().use { output ->
                            input.copyTo(output)
                        }
                    }
                    tempFile.delete()
                } catch (_: Exception) {}
            }
        }
    }

    // Проверка заголовка БД
    private fun isDatabaseEncrypted(context: Context, dbName: String): Boolean {
        val dbFile = context.getDatabasePath(dbName)
        if (!dbFile.exists()) return false

        return try {
            dbFile.inputStream().use { input ->
                val header = ByteArray(16)
                input.read(header, 0, 16)
                val headerString = String(header)
                headerString != "SQLite format 3\u0000"
            }
        } catch (_: Exception) {
            true
        }
    }

    // Добавляет проверку для обычных файлов
    private fun isFileEncrypted(file: File): Boolean {
        return try {
            file.inputStream().use { input ->
                val firstBytes = ByteArray(2)
                input.read(firstBytes, 0, 2)
                // JPG файлы всегда начинаются с байтов FF D8.
                // Если байты другие — значит файл зашифрован или поврежден
                val isJpg = firstBytes[0] == 0xFF.toByte() && firstBytes[1] == 0xD8.toByte()
                !isJpg
            }
        } catch (_: Exception) {
            true
        }
    }
}