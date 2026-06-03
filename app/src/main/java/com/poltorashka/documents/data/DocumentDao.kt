package com.poltorashka.documents.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {
    // ИЗМЕНЕНИЕ: Добавлена сортировка ORDER BY orderIndex ASC
    @Query("SELECT * FROM documents ORDER BY orderIndex ASC")
    fun getAllDocuments(): Flow<List<DocumentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(document: DocumentEntity)

    @Query("SELECT * FROM documents WHERE id = :documentId")
    suspend fun getDocumentById(documentId: Int): DocumentEntity?

    @Query("DELETE FROM documents WHERE id = :documentId")
    suspend fun deleteDocument(documentId: Int)

    // Универсальное обновление всего документа
    @Update
    suspend fun updateDocument(document: DocumentEntity)

    // НОВОЕ: Массовое обновление списка документов (нужно для сохранения порядка)
    @Update
    suspend fun updateDocuments(documents: List<DocumentEntity>)

    @Query("DELETE FROM documents WHERE profileId = :profileId")
    suspend fun deleteDocumentsByProfileId(profileId: Int)
}