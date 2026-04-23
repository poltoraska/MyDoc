package com.poltorashka.documents.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface FolderDao {
    // Получаем все папки, отсортированные по их порядку
    @Query("SELECT * FROM folders ORDER BY orderIndex ASC")
    fun getAllFolders(): Flow<List<FolderEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolder(folder: FolderEntity)

    @Update
    suspend fun updateFolder(folder: FolderEntity)

    @Update
    suspend fun updateFolders(folders: List<FolderEntity>) // Для массового обновления порядка

    @Query("DELETE FROM folders WHERE id = :folderId")
    suspend fun deleteFolder(folderId: Int)
}