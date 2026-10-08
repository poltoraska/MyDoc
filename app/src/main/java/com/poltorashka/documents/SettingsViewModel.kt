package com.poltorashka.documents

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.poltorashka.documents.data.AppDatabase
import com.poltorashka.documents.data.DocumentDao
import com.poltorashka.documents.data.FolderDao
import com.poltorashka.documents.data.FolderEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class SettingsViewModel(
    private val context: Context,
    private val database: AppDatabase
) : ViewModel() {
    private val folderDao: FolderDao = database.folderDao()
    private val documentDao: DocumentDao = database.documentDao()

    val folders: StateFlow<List<FolderEntity>> = folderDao.getAllFolders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addFolder(name: String) {
        viewModelScope.launch {
            val currentCount = folders.value.size
            folderDao.insertFolder(FolderEntity(name = name, orderIndex = currentCount))
        }
    }

    fun updateFolderName(folder: FolderEntity, newName: String) {
        viewModelScope.launch {
            folderDao.updateFolder(folder.copy(name = newName))
        }
    }

    fun deleteFolder(folderId: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val pathsToDelete = database.withTransaction {
                val deletedDocuments = documentDao.getDocumentsByProfileId(folderId)
                documentDao.deleteDocumentsByProfileId(folderId)
                folderDao.deleteFolder(folderId)
                val remainingPaths = documentDao.getAllDocumentsSnapshot()
                    .flatMap { it.photoUris }.map { File(it).canonicalPath }.toSet()
                deletedDocuments.flatMap { it.photoUris }
                    .distinct().filterNot { File(it).canonicalPath in remainingPaths }
            }
            val filesDirectory = context.filesDir.canonicalFile
            pathsToDelete.forEach { path ->
                runCatching {
                    val attachment = File(path).canonicalFile
                    if (attachment.parentFile == filesDirectory) attachment.delete()
                }
            }
        }
    }

    // Сохраняет новый порядок папок в БД после завершения перетягивания
    fun updateFoldersOrder(orderedFolders: List<FolderEntity>) {
        viewModelScope.launch {
            val updatedList = orderedFolders.mapIndexed { index, folder ->
                folder.copy(orderIndex = index)
            }
            folderDao.updateFolders(updatedList)
        }
    }

    // --- ФУНКЦИИ РЕЗЕРВНОГО КОПИРОВАНИЯ  ---

    fun createBackup(context: android.content.Context, uri: android.net.Uri, password: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val success = com.poltorashka.documents.utils.BackupManager.createBackup(context, uri, password)
            onResult(success)
        }
    }

    fun restoreBackup(context: android.content.Context, uri: android.net.Uri, password: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val success = com.poltorashka.documents.utils.BackupManager.restoreBackup(context, uri, password)
            onResult(success)
        }
    }
}

class SettingsViewModelFactory(private val context: Context, private val database: AppDatabase) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST") return SettingsViewModel(context.applicationContext, database) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
