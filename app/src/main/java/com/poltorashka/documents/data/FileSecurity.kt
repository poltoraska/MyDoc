package com.poltorashka.documents

import android.content.Context
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import java.io.File

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
        if (isLegacyPlaintext(file)) return file.readBytes()
        val encryptedFile = getEncryptedFile(context, file)
        encryptedFile.openFileInput().use { inputStream ->
            return inputStream.readBytes()
        }
    }

    fun isLegacyPlaintext(file: File): Boolean {
        if (file.extension.lowercase() !in setOf("jpg", "jpeg", "png", "pdf")) return false
        if (!file.isFile) return false
        return file.inputStream().use { input ->
            val header = ByteArray(12)
            var count = 0
            while (count < header.size) {
                val read = input.read(header, count, header.size - count)
                if (read < 0) break
                count += read
            }
            count >= 4 && (
                (header[0] == 0xFF.toByte() && header[1] == 0xD8.toByte()) ||
                    (header[0] == '%'.code.toByte() && header[1] == 'P'.code.toByte() &&
                        header[2] == 'D'.code.toByte() && header[3] == 'F'.code.toByte()) ||
                    (header[0] == 'G'.code.toByte() && header[1] == 'I'.code.toByte() &&
                        header[2] == 'F'.code.toByte()) ||
                    (header[0] == 'B'.code.toByte() && header[1] == 'M'.code.toByte()) ||
                    (count >= 8 && header.copyOfRange(0, 8).contentEquals(byteArrayOf(
                        0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(),
                        0x0D, 0x0A, 0x1A, 0x0A
                    ))) ||
                    (count >= 12 && String(header, 0, 4, Charsets.US_ASCII) == "RIFF" &&
                        String(header, 8, 4, Charsets.US_ASCII) == "WEBP") ||
                    (count >= 12 && String(header, 4, 4, Charsets.US_ASCII) == "ftyp" &&
                        String(header, 8, 4, Charsets.US_ASCII) in setOf("heic", "heix", "hevc", "mif1"))
                )
        }
    }
}
