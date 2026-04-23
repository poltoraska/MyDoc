package com.poltorashka.documents

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.poltorashka.documents.data.DocumentDao
import com.poltorashka.documents.data.DocumentEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

class DocumentDetailViewModel(private val dao: DocumentDao) : ViewModel() {
    private val _document = MutableStateFlow<DocumentEntity?>(null)
    val document = _document.asStateFlow()

    fun loadDocument(id: Int) {
        viewModelScope.launch {
            _document.value = dao.getDocumentById(id)
        }
    }

    fun addPhoto(doc: DocumentEntity, photoUri: String) {
        val updatedList = doc.photoUris + photoUri
        val updatedDoc = doc.copy(photoUris = updatedList)
        viewModelScope.launch {
            dao.updateDocument(updatedDoc)
            _document.value = updatedDoc
        }
    }

    fun removePhoto(doc: DocumentEntity, photoUriToRemove: String) {
        val updatedList = doc.photoUris - photoUriToRemove
        val updatedDoc = doc.copy(photoUris = updatedList)
        viewModelScope.launch {
            dao.updateDocument(updatedDoc)
            _document.value = updatedDoc
            try { File(photoUriToRemove).delete() } catch (e: Exception) { e.printStackTrace() }
        }
    }

    // НОВОЕ: Сохранение измененных текстовых полей
    fun updateFields(doc: DocumentEntity, newFields: Map<String, String>) {
        val updatedDoc = doc.copy(fieldsData = newFields)
        viewModelScope.launch {
            dao.updateDocument(updatedDoc)
            _document.value = updatedDoc
        }
    }

    // НОВОЕ: Полное удаление документа и очистка памяти
    fun deleteDocument(doc: DocumentEntity, onDeleted: () -> Unit) {
        viewModelScope.launch {
            // Сначала удаляет все прикрепленные сканы из памяти телефона
            doc.photoUris.forEach { path ->
                try { File(path).delete() } catch (e: Exception) { e.printStackTrace() }
            }
            // Удаляет запись из БД
            dao.deleteDocument(doc.id)
            // Возвращает на предыдущий экран
            onDeleted()
        }
    }
}

class DocumentDetailViewModelFactory(private val dao: DocumentDao) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DocumentDetailViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return DocumentDetailViewModel(dao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}