package com.poltorashka.documents

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.poltorashka.documents.data.DocumentDao
import com.poltorashka.documents.data.DocumentEntity
import com.poltorashka.documents.data.FolderDao
import com.poltorashka.documents.data.FolderEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DocumentsViewModel(
    private val documentDao: DocumentDao,
    private val folderDao: FolderDao
) : ViewModel() {

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        viewModelScope.launch {
            folderDao.getAllFolders().collect {
                _isLoading.value = false
            }
        }
    }

    val folders: StateFlow<List<FolderEntity>> = folderDao.getAllFolders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allDocuments: StateFlow<List<DocumentEntity>> = documentDao.getAllDocuments()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedFolderId = MutableStateFlow<Int?>(null)
    val selectedFolderId: StateFlow<Int?> = _selectedFolderId

    val documents: StateFlow<List<DocumentEntity>> = combine(
        documentDao.getAllDocuments(),
        _selectedFolderId,
        folders
    ) { allDocs, currentFolderId, allFolders ->
        val activeFolderId = currentFolderId ?: allFolders.firstOrNull()?.id

        if (activeFolderId != null) {
            allDocs.filter { it.profileId == activeFolderId }
        } else {
            emptyList()
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectFolder(folderId: Int) {
        _selectedFolderId.value = folderId
    }

    // --- Логика обновления порядка после перетаскивания (Drag & Drop) ---
    fun updateDocumentsOrder(reorderedDocs: List<DocumentEntity>) {
        viewModelScope.launch(Dispatchers.IO) {
            val updatedList = reorderedDocs.mapIndexed { index, doc ->
                doc.copy(orderIndex = index) // Присваиваивает новый порядковый номер
            }
            documentDao.updateDocuments(updatedList)
        }
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