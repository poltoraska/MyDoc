package com.poltorashka.documents

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.poltorashka.documents.data.AppDatabase
import com.poltorashka.documents.data.DocumentEntity
import com.poltorashka.documents.data.DocumentTemplates
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

private fun saveEncryptedFile(context: Context, uri: Uri): String? {
    // Умное определение расширения: если PDF - пишет .pdf, иначе .jpg
    val mimeType = context.contentResolver.getType(uri)
    val extension = if (mimeType == "application/pdf") "pdf" else "jpg"

    val fileName = "doc_${UUID.randomUUID()}.$extension"
    val file = File(context.filesDir, fileName)

    return try {
        val inputStream = context.contentResolver.openInputStream(uri)
        val encryptedFile = FileSecurity.getEncryptedFile(context, file)
        val outputStream = encryptedFile.openFileOutput()

        inputStream?.use { input ->
            outputStream.use { output ->
                input.copyTo(output)
            }
        }
        file.absolutePath
    } catch (e: Exception) {
        null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddDocumentScreen(profileId: Int, onBackClick: () -> Unit, onSaved: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    val documentTypes = DocumentTemplates.supportedDocumentTypes
    var selectedType by remember { mutableStateOf(documentTypes[0]) }

    val inputValues = remember { mutableStateMapOf<String, String>() }
    val currentFields = DocumentTemplates.getFieldsForType(selectedType)

    var selectedImages by remember { mutableStateOf<List<Uri>>(emptyList()) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        // Теперь здесь могут быть и фото, и PDF
        selectedImages = selectedImages + uris
    }

    val context = LocalContext.current
    val dao = remember { AppDatabase.getDatabase(context).documentDao() }
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Новый документ") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад") }
                }
            )
        },
        bottomBar = {
            Button(
                onClick = {
                    coroutineScope.launch {
                        // Использует новую функцию с новым названием
                        val savedPaths = selectedImages.mapNotNull { uri ->
                            saveEncryptedFile(context, uri)
                        }

                        val newDocument = DocumentEntity(
                            profileId = profileId,
                            documentType = selectedType,
                            photoUris = savedPaths,
                            fieldsData = inputValues.toMap()
                        )

                        dao.insertDocument(newDocument)
                        onSaved()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(16.dp)
                    .height(50.dp)
            ) { Text("Сохранить") }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it }
            ) {
                OutlinedTextField(
                    value = selectedType,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Тип документа") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    documentTypes.forEach { selectionOption ->
                        DropdownMenuItem(
                            text = { Text(selectionOption) },
                            onClick = { selectedType = selectionOption; expanded = false; inputValues.clear() }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            currentFields.forEach { fieldLabel ->
                OutlinedTextField(
                    value = inputValues[fieldLabel] ?: "",
                    onValueChange = { inputValues[fieldLabel] = it },
                    label = { Text(fieldLabel) },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedButton(
                onClick = { filePickerLauncher.launch(arrayOf("image/*", "application/pdf")) },
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Text("Добавить файлы (${selectedImages.size})")
            }

            Spacer(modifier = Modifier.height(16.dp))

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(selectedImages) { uri ->
                    Box {
                        AsyncImage(
                            model = uri,
                            contentDescription = null,
                            modifier = Modifier.size(120.dp).clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop
                        )
                        IconButton(
                            onClick = { selectedImages = selectedImages - uri },
                            modifier = Modifier.align(Alignment.TopEnd)
                        ) {
                            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.errorContainer) {
                                Icon(Icons.Filled.Close, contentDescription = "Удалить", tint = MaterialTheme.colorScheme.onErrorContainer)
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}