package com.poltorashka.documents

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

// Функция копирует выбранное фото во внутреннюю память приложения и возвращает путь
fun saveImageToInternalStorage(context: Context, uri: Uri): String? {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return null

        // Создает уникальное имя файла, чтобы они не перезаписывали друг друга
        val fileName = "doc_${UUID.randomUUID()}.jpg"
        val file = File(context.filesDir, fileName)
        val outputStream = FileOutputStream(file)

        inputStream.copyTo(outputStream)

        inputStream.close()
        outputStream.close()

        file.absolutePath // Возвращает путь для сохранения в БД
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}
