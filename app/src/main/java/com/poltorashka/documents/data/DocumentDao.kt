package com.poltorashka.documents.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {
    @Query("SELECT * FROM documents")
    fun getAllDocuments(): Flow<List<DocumentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(document: DocumentEntity)

    @Query("SELECT * FROM documents WHERE id = :documentId")
    suspend fun getDocumentById(documentId: Int): DocumentEntity?

    @Query("DELETE FROM documents WHERE id = :documentId")
    suspend fun deleteDocument(documentId: Int)

    // НОВОЕ: Универсальное обновление всего документа
    @Update
    suspend fun updateDocument(document: DocumentEntity)

    @Query("DELETE FROM documents WHERE profileId = :profileId")
    suspend fun deleteDocumentsByProfileId(profileId: Int)
}
