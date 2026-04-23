package com.poltorashka.documents.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "folders")
data class FolderEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val orderIndex: Int // Для сохранения порядка при перетягивании
)