package com.poltorashka.documents

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.poltorashka.documents.data.DocumentDao
import com.poltorashka.documents.data.FolderDao
import com.poltorashka.documents.data.FolderEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val folderDao: FolderDao,
    private val documentDao: DocumentDao
) : ViewModel() {

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
        viewModelScope.launch {
            documentDao.deleteDocumentsByProfileId(folderId)
            folderDao.deleteFolder(folderId)
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
}

class SettingsViewModelFactory(private val folderDao: FolderDao, private val docDao: DocumentDao) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST") return SettingsViewModel(folderDao, docDao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}