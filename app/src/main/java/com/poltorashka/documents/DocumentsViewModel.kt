package com.poltorashka.documents

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.poltorashka.documents.data.DocumentDao
import com.poltorashka.documents.data.DocumentEntity
import com.poltorashka.documents.data.FolderDao
import com.poltorashka.documents.data.FolderEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class DocumentsViewModel(
    private val documentDao: DocumentDao,
    private val folderDao: FolderDao
) : ViewModel() {

    // Получает все папки из БД
    val folders: StateFlow<List<FolderEntity>> = folderDao.getAllFolders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Отдает все документы без фильтрации по папкам для экрана поиска
    val allDocuments: StateFlow<List<DocumentEntity>> = documentDao.getAllDocuments()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Хранит ID выбранной папки
    private val _selectedFolderId = MutableStateFlow<Int?>(null)
    val selectedFolderId: StateFlow<Int?> = _selectedFolderId

    // Фильтрует документы
    val documents: StateFlow<List<DocumentEntity>> = combine(
        documentDao.getAllDocuments(),
        _selectedFolderId,
        folders
    ) { allDocs, currentFolderId, allFolders ->
        // Если папка не выбрана вручную, берет первую из списка. Если папок нет — null
        val activeFolderId = currentFolderId ?: allFolders.firstOrNull()?.id

        if (activeFolderId != null) {
            allDocs.filter { it.profileId == activeFolderId }
        } else {
            emptyList() // Если папок нет, документов тоже нет
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectFolder(folderId: Int) {
        _selectedFolderId.value = folderId
    }
}

class DocumentsViewModelFactory(
    private val docDao: DocumentDao,
    private val folderDao: FolderDao
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DocumentsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return DocumentsViewModel(docDao, folderDao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}