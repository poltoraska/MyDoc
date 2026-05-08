package com.poltorashka.documents.utils

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.SecureRandom
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object BackupManager {
    private const val ALGORITHM = "AES/CBC/PKCS5Padding"
    private const val ITERATION_COUNT = 10000
    private const val KEY_LENGTH = 256
    private const val SALT_LENGTH = 16
    private const val IV_LENGTH = 16

    // 1. СОЗДАНИЕ РЕЗЕРВНОЙ КОПИИ
    suspend fun createBackup(context: Context, destinationUri: Uri, password: String): Boolean = withContext(Dispatchers.IO) {
        try {
            // Папки, которые нужно сохранить
            val dbFolder = File(context.applicationInfo.dataDir, "databases")
            val filesFolder = context.filesDir

            val salt = ByteArray(SALT_LENGTH).apply { SecureRandom().nextBytes(this) }
            val iv = ByteArray(IV_LENGTH).apply { SecureRandom().nextBytes(this) }
            val secretKey = generateKey(password, salt)

            val cipher = Cipher.getInstance(ALGORITHM)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, IvParameterSpec(iv))

            context.contentResolver.openOutputStream(destinationUri)?.use { outputStream ->
                // Записывает соль и IV в начало файла, чтобы потом суметь расшифровать
                outputStream.write(salt)
                outputStream.write(iv)

                CipherOutputStream(outputStream, cipher).use { cipherOut ->
                    ZipOutputStream(cipherOut).use { zipOut ->
                        if (dbFolder.exists()) zipFolder(dbFolder, "databases", zipOut)
                        if (filesFolder.exists()) zipFolder(filesFolder, "files", zipOut)
                    }
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // 2. ВОССТАНОВЛЕНИЕ ИЗ КОПИИ
    suspend fun restoreBackup(context: Context, sourceUri: Uri, password: String): Boolean = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(sourceUri)?.use { inputStream ->
                val salt = ByteArray(SALT_LENGTH)
                val iv = ByteArray(IV_LENGTH)

                // Читает соль и IV из начала файла
                if (inputStream.read(salt) != SALT_LENGTH || inputStream.read(iv) != IV_LENGTH) return@withContext false

                val secretKey = generateKey(password, salt)
                val cipher = Cipher.getInstance(ALGORITHM)
                cipher.init(Cipher.DECRYPT_MODE, secretKey, IvParameterSpec(iv))

                CipherInputStream(inputStream, cipher).use { cipherIn ->
                    ZipInputStream(cipherIn).use { zipIn ->
                        var entry = zipIn.nextEntry
                        while (entry != null) {
                            val targetFile = File(context.applicationInfo.dataDir, entry.name)
                            if (entry.isDirectory) {
                                targetFile.mkdirs()
                            } else {
                                targetFile.parentFile?.mkdirs()
                                FileOutputStream(targetFile).use { fos ->
                                    zipIn.copyTo(fos)
                                }
                            }
                            zipIn.closeEntry()
                            entry = zipIn.nextEntry
                        }
                    }
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false // Неверный пароль или поврежденный файл
        }
    }

    // Вспомогательная функция генерации ключа из пароля
    private fun generateKey(password: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATION_COUNT, KEY_LENGTH)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val bytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(bytes, "AES")
    }

    // Вспомогательная функция для рекурсивной упаковки папок в Zip
    private fun zipFolder(folder: File, parentName: String, zipOut: ZipOutputStream) {
        folder.listFiles()?.forEach { file ->
            val entryName = "$parentName/${file.name}"
            if (file.isDirectory) {
                zipOut.putNextEntry(ZipEntry("$entryName/"))
                zipOut.closeEntry()
                zipFolder(file, entryName, zipOut)
            } else {
                FileInputStream(file).use { fis ->
                    zipOut.putNextEntry(ZipEntry(entryName))
                    fis.copyTo(zipOut)
                    zipOut.closeEntry()
                }
            }
        }
    }
}