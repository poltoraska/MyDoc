package com.poltorashka.documents.utils

import android.content.Context
import android.net.Uri
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
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
            try {
                com.poltorashka.documents.data.AppDatabase.getDatabase(context).close()
            } catch (e: Exception) { e.printStackTrace() }

            val dbFolder = File(context.applicationInfo.dataDir, "databases")
            val filesFolder = context.filesDir
            val prefsFolder = File(context.applicationInfo.dataDir, "shared_prefs")

            val masterKey = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()

            val sharedPreferences = EncryptedSharedPreferences.create(
                context, "secure_db_prefs", masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            val dbPassword = sharedPreferences.getString("db_password", null)

            val salt = ByteArray(SALT_LENGTH).apply { SecureRandom().nextBytes(this) }
            val iv = ByteArray(IV_LENGTH).apply { SecureRandom().nextBytes(this) }
            val secretKey = generateKey(password, salt)

            val cipher = Cipher.getInstance(ALGORITHM)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, IvParameterSpec(iv))

            context.contentResolver.openOutputStream(destinationUri)?.use { outputStream ->
                outputStream.write(salt)
                outputStream.write(iv)

                CipherOutputStream(outputStream, cipher).use { cipherOut ->
                    ZipOutputStream(cipherOut).use { zipOut ->
                        if (dbFolder.exists()) zipFolder(dbFolder, "databases", zipOut)

                        // МАГИЯ 1: Распаковываем файлы из аппаратного шифрования для переноса
                        if (filesFolder.exists()) zipEncryptedFiles(context, filesFolder, "files", zipOut, masterKey)

                        if (prefsFolder.exists()) {
                            zipFolder(prefsFolder, "shared_prefs", zipOut) { file ->
                                !file.name.contains("secure_db_prefs") && !file.name.contains("androidx_security")
                            }
                        }

                        if (dbPassword != null) {
                            zipOut.putNextEntry(ZipEntry("keys/db_password.txt"))
                            zipOut.write(dbPassword.toByteArray())
                            zipOut.closeEntry()
                        }
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
            val dbFolder = File(context.applicationInfo.dataDir, "databases")
            if (dbFolder.exists()) dbFolder.listFiles()?.forEach { it.delete() }

            val prefsFolder = File(context.applicationInfo.dataDir, "shared_prefs")
            if (prefsFolder.exists()) prefsFolder.listFiles()?.forEach { it.delete() }

            val filesFolder = context.filesDir
            if (filesFolder.exists()) filesFolder.listFiles()?.forEach { it.delete() }

            var restoredDbPassword: String? = null
            val masterKey = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()

            context.contentResolver.openInputStream(sourceUri)?.use { inputStream ->
                val salt = ByteArray(SALT_LENGTH)
                val iv = ByteArray(IV_LENGTH)

                if (inputStream.read(salt) != SALT_LENGTH || inputStream.read(iv) != IV_LENGTH) return@withContext false

                val secretKey = generateKey(password, salt)
                val cipher = Cipher.getInstance(ALGORITHM)
                cipher.init(Cipher.DECRYPT_MODE, secretKey, IvParameterSpec(iv))

                CipherInputStream(inputStream, cipher).use { cipherIn ->
                    ZipInputStream(cipherIn).use { zipIn ->
                        var entry = zipIn.nextEntry
                        while (entry != null) {
                            if (entry.name == "keys/db_password.txt") {
                                restoredDbPassword = String(zipIn.readBytes())
                            } else if (entry.name.startsWith("files/") && !entry.isDirectory) {
                                // МАГИЯ 2: Заново шифруем файлы новым аппаратным ключом телефона
                                val targetFile = File(context.applicationInfo.dataDir, entry.name)
                                targetFile.parentFile?.mkdirs()
                                if (targetFile.exists()) targetFile.delete()

                                try {
                                    val encryptedFile = EncryptedFile.Builder(
                                        context, targetFile, masterKey,
                                        EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
                                    ).build()
                                    encryptedFile.openFileOutput().use { fos ->
                                        zipIn.copyTo(fos)
                                    }
                                } catch (e: Exception) {
                                    // Если что-то пошло не так, сохраняем как обычный файл
                                    FileOutputStream(targetFile).use { fos -> zipIn.copyTo(fos) }
                                }
                            } else {
                                val targetFile = File(context.applicationInfo.dataDir, entry.name)
                                if (entry.isDirectory) {
                                    targetFile.mkdirs()
                                } else {
                                    targetFile.parentFile?.mkdirs()
                                    FileOutputStream(targetFile).use { fos ->
                                        zipIn.copyTo(fos)
                                    }
                                }
                            }
                            zipIn.closeEntry()
                            entry = zipIn.nextEntry
                        }
                    }
                }
            }

            if (restoredDbPassword != null) {
                val sharedPreferences = EncryptedSharedPreferences.create(
                    context, "secure_db_prefs", masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
                sharedPreferences.edit().putString("db_password", restoredDbPassword).apply()
            }

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun generateKey(password: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATION_COUNT, KEY_LENGTH)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val bytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(bytes, "AES")
    }

    private fun zipFolder(folder: File, parentName: String, zipOut: ZipOutputStream, filter: ((File) -> Boolean)? = null) {
        folder.listFiles()?.forEach { file ->
            if (filter != null && !filter(file)) return@forEach
            val entryName = "$parentName/${file.name}"
            if (file.isDirectory) {
                zipOut.putNextEntry(ZipEntry("$entryName/"))
                zipOut.closeEntry()
                zipFolder(file, entryName, zipOut, filter)
            } else {
                FileInputStream(file).use { fis ->
                    zipOut.putNextEntry(ZipEntry(entryName))
                    fis.copyTo(zipOut)
                    zipOut.closeEntry()
                }
            }
        }
    }

    // Специальная функция для упаковки зашифрованных файлов
    private fun zipEncryptedFiles(context: Context, folder: File, parentName: String, zipOut: ZipOutputStream, masterKey: MasterKey) {
        folder.listFiles()?.forEach { file ->
            if (file.isDirectory) return@forEach

            val entryName = "$parentName/${file.name}"
            zipOut.putNextEntry(ZipEntry(entryName))
            try {
                val encryptedFile = EncryptedFile.Builder(
                    context, file, masterKey,
                    EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
                ).build()
                encryptedFile.openFileInput().use { fis ->
                    fis.copyTo(zipOut)
                }
            } catch (e: Exception) {
                // Если файл почему-то не зашифрован аппаратно, читает как обычный
                FileInputStream(file).use { fis ->
                    fis.copyTo(zipOut)
                }
            }
            zipOut.closeEntry()
        }
    }
}