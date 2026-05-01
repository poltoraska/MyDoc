package com.poltorashka.documents

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import bounceClick
import coil.compose.AsyncImage
import com.poltorashka.documents.data.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

private fun saveEncryptedFile(context: Context, uri: Uri): String? {
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

private fun openPdfFile(context: Context, path: String) {
    try {
        val encryptedFile = File(path)
        val decryptedBytes = FileSecurity.decryptFile(context, encryptedFile)

        val tempFile = File(context.cacheDir, "temp_preview.pdf")
        tempFile.writeBytes(decryptedBytes)

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            tempFile
        )

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(intent)

    } catch (e: Exception) {
        Toast.makeText(context, "Ошибка при открытии PDF: ${e.message}", Toast.LENGTH_LONG).show()
    }
}

@Composable
fun DecryptedAsyncImage(
    path: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    onClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var imageBytes by remember(path) { mutableStateOf<ByteArray?>(null) }
    var hasError by remember(path) { mutableStateOf(false) }

    LaunchedEffect(path) {
        withContext(Dispatchers.IO) {
            try {
                imageBytes = FileSecurity.decryptFile(context, File(path))
            } catch (e: Exception) {
                hasError = true
            }
        }
    }

    val finalModifier = if (onClick != null) modifier.clickable { onClick() } else modifier

    if (imageBytes != null) {
        AsyncImage(
            model = imageBytes,
            contentDescription = "Расшифрованный скан",
            modifier = finalModifier,
            contentScale = contentScale
        )
    } else if (hasError) {
        Box(modifier = finalModifier.background(MaterialTheme.colorScheme.errorContainer), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Close, contentDescription = "Ошибка", tint = MaterialTheme.colorScheme.onErrorContainer)
        }
    } else {
        Box(modifier = finalModifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
                strokeWidth = 3.dp,
                strokeCap = StrokeCap.Round
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentDetailScreen(
    documentId: Int,
    onBackClick: () -> Unit,
    viewModel: DocumentDetailViewModel = viewModel(
        factory = DocumentDetailViewModelFactory(AppDatabase.getDatabase(LocalContext.current).documentDao())
    )
) {
    LaunchedEffect(documentId) {
        viewModel.loadDocument(documentId)
    }

    val document by viewModel.document.collectAsState()
    val context = LocalContext.current

    var imageToShow by remember { mutableStateOf<String?>(null) }
    var imageToDelete by remember { mutableStateOf<String?>(null) }

    var isEditing by remember { mutableStateOf(false) }
    var showDeleteDocDialog by remember { mutableStateOf(false) }
    val editedFields = remember { mutableStateMapOf<String, String>() }

    LaunchedEffect(isEditing) {
        if (isEditing) {
            document?.fieldsData?.let { data ->
                editedFields.clear()
                data.forEach { (key, value) ->
                    if (key.contains("Дата", ignoreCase = true)) {
                        editedFields[key] = value.filter { it.isDigit() }
                    } else {
                        editedFields[key] = value
                    }
                }
            }
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        document?.let { doc ->
            uris.forEach { uri ->
                val savedPath = saveEncryptedFile(context, uri)
                if (savedPath != null) {
                    viewModel.addPhoto(doc, savedPath)
                }
            }
        }
    }

    if (imageToDelete != null) {
        AlertDialog(
            onDismissRequest = { imageToDelete = null },
            title = { Text("Удалить файл?") },
            text = { Text("Это действие нельзя будет отменить.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        document?.let { doc -> viewModel.removePhoto(doc, imageToDelete!!) }
                        imageToDelete = null
                    }
                ) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { imageToDelete = null }) { Text("Отмена") } }
        )
    }

    if (showDeleteDocDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDocDialog = false },
            title = { Text("Удалить документ?") },
            text = { Text("Документ и все прикрепленные к нему файлы будут удалены без возможности восстановления.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        document?.let { doc ->
                            viewModel.deleteDocument(doc, onDeleted = onBackClick)
                        }
                        showDeleteDocDialog = false
                    }
                ) { Text("Удалить навсегда", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDeleteDocDialog = false }) { Text("Отмена") } }
        )
    }

    if (imageToShow != null) {
        Dialog(
            onDismissRequest = { imageToShow = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black).clickable { imageToShow = null }) {
                DecryptedAsyncImage(
                    path = imageToShow!!,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.5f),
                    modifier = Modifier.align(Alignment.TopEnd).padding(16.dp).size(48.dp).bounceClick { imageToShow = null }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Close, contentDescription = "Закрыть", tint = Color.White)
                    }
                }
            }
        }
    }

    Scaffold(
        floatingActionButton = {
            if (!isEditing && document != null) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shadowElevation = 4.dp,
                    modifier = Modifier
                        .size(64.dp)
                        .bounceClick {
                            val doc = document!!
                            val orderedLabels = com.poltorashka.documents.data.DocumentTemplates.getFieldsForType(doc.documentType)
                            val extraLabels = doc.fieldsData.keys.filter { !orderedLabels.contains(it) }
                            val finalLabels = orderedLabels + extraLabels

                            val shareText = StringBuilder().apply {
                                append("${doc.documentType}\n\n")
                                finalLabels.forEach { key ->
                                    val value = doc.fieldsData[key]
                                    if (!value.isNullOrBlank()) {
                                        append("$key: $value\n")
                                    }
                                }
                            }.toString()

                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, shareText)
                            }
                            context.startActivity(Intent.createChooser(intent, "Поделиться документом"))
                        }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.Share,
                            contentDescription = "Поделиться",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(bottom = innerPadding.calculateBottomPadding())
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp),
                shadowElevation = 2.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 80.dp, bottom = 24.dp, start = 24.dp, end = 24.dp)
                ) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .size(44.dp)
                                .bounceClick { if (isEditing) isEditing = false else onBackClick() }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (isEditing) Icons.Filled.Close else Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = if (isEditing) "Отмена" else "Назад",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.align(Alignment.CenterEnd),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isEditing) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .height(44.dp)
                                        .bounceClick {
                                            // Перед сохранением конвертируем цифры обратно в формат DD.MM.YYYY
                                            val formattedFields = editedFields.mapValues { (key, value) ->
                                                if (key.contains("Дата", ignoreCase = true)) value.toDateString() else value
                                            }
                                            document?.let { doc -> viewModel.updateFields(doc, formattedFields) }
                                            isEditing = false
                                        }
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 20.dp)) {
                                        Text("Сохранить", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                                    }
                                }
                            } else {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(44.dp).bounceClick { showDeleteDocDialog = true }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Filled.Delete, contentDescription = "Удалить", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(20.dp))
                                    }
                                }
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(44.dp).bounceClick { isEditing = true }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Filled.Edit, contentDescription = "Редактировать", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(20.dp))
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = if (isEditing) "Редактирование" else (document?.documentType?.replace(" о ", " о\u00A0") ?: "Загрузка..."),
                        fontSize = 32.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }

            document?.let { doc ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                        .padding(top = 24.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                        shape = RoundedCornerShape(28.dp)
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            val orderedLabels = com.poltorashka.documents.data.DocumentTemplates.getFieldsForType(doc.documentType)
                            val extraLabels = doc.fieldsData.keys.filter { !orderedLabels.contains(it) }
                            val finalLabels = orderedLabels + extraLabels

                            if (isEditing) {
                                finalLabels.forEach { label ->
                                    val isDateField = label.contains("Дата", ignoreCase = true)

                                    OutlinedTextField(
                                        value = editedFields[label] ?: "",
                                        onValueChange = { newValue ->
                                            if (isDateField) {
                                                // Для даты разрешаем только цифры и максимум 8 штук
                                                val digits = newValue.filter { it.isDigit() }.take(8)
                                                editedFields[label] = digits
                                            } else {
                                                editedFields[label] = newValue
                                            }
                                        },
                                        label = { Text(label) },
                                        visualTransformation = if (isDateField) DateTransformation() else VisualTransformation.None,
                                        keyboardOptions = if (isDateField) KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default,
                                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                                    )
                                }
                            } else {
                                finalLabels.forEach { label ->
                                    if (doc.fieldsData.containsKey(label)) {
                                        val value = doc.fieldsData[label] ?: ""
                                        DetailField(
                                            label = label,
                                            value = value,
                                            onCopy = { textToCopy ->
                                                if (textToCopy.isNotBlank()) {
                                                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                                    val clip = android.content.ClipData.newPlainText("Скопировано", textToCopy)
                                                    clipboard.setPrimaryClip(clip)
                                                    Toast.makeText(context, "Скопировано", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Файлы (${doc.photoUris.size})", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)

                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier
                                .height(36.dp)
                                .bounceClick { filePickerLauncher.launch(arrayOf("image/*", "application/pdf")) }
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 16.dp)) {
                                Text("+ Добавить", fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                            }
                        }
                    }

                    if (doc.photoUris.isEmpty()) {
                        Text("Файлы еще не добавлены", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
                    } else {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(vertical = 8.dp)
                        ) {
                            items(doc.photoUris) { path ->
                                FileItem(
                                    path = path,
                                    onDelete = { imageToDelete = path },
                                    onOpen = {
                                        if (path.endsWith(".pdf", ignoreCase = true)) {
                                            openPdfFile(context, path)
                                        } else {
                                            imageToShow = path
                                        }
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(80.dp))
                }
            }
        }
    }
}

// КОМПОНЕНТ ДЛЯ КАРТОЧКИ ФАЙЛА С КРЕСТИКОМ ПОВЕРХ ВСЕГО
@Composable
fun FileItem(path: String, onDelete: () -> Unit, onOpen: () -> Unit) {
    val isPdf = path.endsWith(".pdf", ignoreCase = true)

    Box(modifier = Modifier.size(160.dp)) {
        Surface(
            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)).bounceClick { onOpen() },
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            if (isPdf) {
                Column(verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(painterResource(id = R.drawable.ic_pdf_file), null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("PDF Документ", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            } else {
                DecryptedAsyncImage(path = path, modifier = Modifier.fillMaxSize())
            }
        }

        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.errorContainer,
            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(32.dp).bounceClick { onDelete() },
            shadowElevation = 2.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Close, null, tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
fun DetailField(label: String, value: String, onCopy: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .bounceClick { onCopy(value) }
            .padding(vertical = 8.dp, horizontal = 4.dp)
    ) {
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text(text = value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
    }
}