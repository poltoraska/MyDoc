package com.poltorashka.documents

import android.content.Context
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import java.io.File
import java.io.InputStream
import java.io.OutputStream

object FileSecurity {

    private fun getMasterKey(context: Context): MasterKey {
        return MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    // Создает зашифрованный файл для записи
    fun getEncryptedFile(context: Context, file: File): EncryptedFile {
        return EncryptedFile.Builder(
            context,
            file,
            getMasterKey(context),
            EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
        ).build()
    }

    // Читает зашифрованный файл и возвращает байты для отображения
    fun decryptFile(context: Context, file: File): ByteArray {
        val encryptedFile = getEncryptedFile(context, file)
        encryptedFile.openFileInput().use { inputStream ->
            return inputStream.readBytes()
        }
    }
}