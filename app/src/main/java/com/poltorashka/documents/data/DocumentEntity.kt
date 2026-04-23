package com.poltorashka.documents.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

@Entity(tableName = "documents")
data class DocumentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val profileId: Int,
    val documentType: String,
    val photoUris: List<String> = emptyList(), // ТЕПЕРЬ ЭТО СПИСОК (по умолчанию пустой)
    val fieldsData: Map<String, String>
)

class Converters {
    // Конвертеры для Map
    @TypeConverter
    fun fromStringMap(value: String): Map<String, String> {
        val mapType = object : TypeToken<Map<String, String>>() {}.type
        return Gson().fromJson(value, mapType) ?: emptyMap()
    }

    @TypeConverter
    fun toStringMap(map: Map<String, String>): String {
        return Gson().toJson(map)
    }

    // НОВЫЕ: Конвертеры для List<String>
    @TypeConverter
    fun fromStringList(value: String): List<String> {
        val listType = object : TypeToken<List<String>>() {}.type
        return Gson().fromJson(value, listType) ?: emptyList()
    }

    @TypeConverter
    fun toStringList(list: List<String>): String {
        return Gson().toJson(list)
    }
}
