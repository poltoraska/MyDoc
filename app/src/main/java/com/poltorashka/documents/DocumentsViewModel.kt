package com.poltorashka.documents

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.poltorashka.documents.data.DocumentDao
import com.poltorashka.documents.data.DocumentEntity
import com.poltorashka.documents.data.FolderDao
import com.poltorashka.documents.data.FolderEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeDocumentsState(
    val isLoading: Boolean = true,
    val hasError: Boolean = false,
    val activeFolderId: Int? = null,
    val folders: List<FolderEntity> = emptyList(),
    val documents: List<DocumentEntity> = emptyList(),
    val allDocuments: List<DocumentEntity> = emptyList()
)

class DocumentsViewModel internal constructor(
    private val openRepository: suspend () -> Pair<DocumentDao, FolderDao>
) : ViewModel() {
    constructor(documentDao: DocumentDao, folderDao: FolderDao) : this({ documentDao to folderDao })

    private val selected = MutableStateFlow<Int?>(null)
    val selectedFolderId: StateFlow<Int?> = selected.asStateFlow()
    private val mutableHomeState = MutableStateFlow(HomeDocumentsState())
    val homeState: StateFlow<HomeDocumentsState> = mutableHomeState.asStateFlow()
    val folders = homeState.map { it.folders }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val allDocuments = homeState.map { it.allDocuments }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val documents = homeState.map { it.documents }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private var folderDao: FolderDao? = null
    private var documentDao: DocumentDao? = null
    private var loadJob: Job? = null

    init { startLoading() }

    fun retry() {
        if (mutableHomeState.value.hasError) startLoading()
    }

    private fun startLoading() {
        loadJob?.cancel()
        mutableHomeState.value = HomeDocumentsState()
        loadJob = viewModelScope.launch {
            try {
                val (docs, folders) = openRepository()
                documentDao = docs
                folderDao = folders
                observeHomeState(folders.getAllFolders(), docs.getAllDocuments(), selected)
                    .collect { mutableHomeState.value = it }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.e("DocumentsStartup", "Document preparation failed", error)
                documentDao = null
                folderDao = null
                mutableHomeState.value = HomeDocumentsState(isLoading = true, hasError = true)
            }
        }
    }

    fun selectFolder(folderId: Int) { selected.value = folderId }

    suspend fun activeFolderId(): Int? {
        val currentFolders = folderDao?.getAllFolders()?.first() ?: return null
        return selected.value?.takeIf { id -> currentFolders.any { it.id == id } } ?: currentFolders.firstOrNull()?.id
    }

    fun updateDocumentsOrder(reorderedDocs: List<DocumentEntity>) {
        val dao = documentDao ?: return
        viewModelScope.launch(Dispatchers.IO) {
            dao.updateDocuments(reorderedDocs.mapIndexed { index, doc -> doc.copy(orderIndex = index) })
        }
    }
}

internal fun observeHomeState(
    folderFlow: Flow<List<FolderEntity>>,
    documentFlow: Flow<List<DocumentEntity>>,
    selected: Flow<Int?>
): Flow<HomeDocumentsState> = combine(folderFlow, documentFlow, selected) { allFolders, allDocs, selectedId ->
    val activeId = selectedId?.takeIf { id -> allFolders.any { it.id == id } } ?: allFolders.firstOrNull()?.id
    HomeDocumentsState(
        isLoading = false,
        activeFolderId = activeId,
        folders = allFolders,
        documents = if (activeId == null) emptyList() else allDocs.filter { it.profileId == activeId },
        allDocuments = allDocs
    )
}

class DocumentsViewModelFactory(
    private val docDao: DocumentDao,
    private val folderDao: FolderDao
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(DocumentsViewModel::class.java))
        @Suppress("UNCHECKED_CAST")
        return DocumentsViewModel(docDao, folderDao) as T
    }
}

internal class StartupDocumentsViewModelFactory(context: Context) : ViewModelProvider.Factory {
    private val context = context.applicationContext
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(DocumentsViewModel::class.java))
        @Suppress("UNCHECKED_CAST")
        return DocumentsViewModel {
            val database = StartupMaintenance.prepareDocuments(context)
            database.documentDao() to database.folderDao()
        } as T
    }
}
