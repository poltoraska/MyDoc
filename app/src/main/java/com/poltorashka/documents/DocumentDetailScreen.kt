package com.poltorashka.documents

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import bounceClick
import coil.compose.AsyncImage
import com.poltorashka.documents.data.AppDatabase
import java.io.File

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
    val clipboardManager = LocalClipboardManager.current // Менеджер буфера обмена

    // Состояния интерфейса
    var imageToShow by remember { mutableStateOf<String?>(null) }
    var imageToDelete by remember { mutableStateOf<String?>(null) }

    // Состояния для редактирования и удаления документа
    var isEditing by remember { mutableStateOf(false) }
    var showDeleteDocDialog by remember { mutableStateOf(false) }
    val editedFields = remember { mutableStateMapOf<String, String>() }

    // Когда включается режим редактирования, копирует текущие данные в редактируемый Map
    LaunchedEffect(isEditing) {
        if (isEditing) {
            document?.fieldsData?.let { data ->
                editedFields.clear()
                editedFields.putAll(data)
            }
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        document?.let { doc ->
            uris.forEach { uri ->
                val savedPath = saveImageToInternalStorage(context, uri)
                if (savedPath != null) {
                    viewModel.addPhoto(doc, savedPath)
                }
            }
        }
    }

    // Диалог удаления отдельного файла
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

    // Диалог удаления всего документа
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

    // Полноэкранный просмотр
    if (imageToShow != null) {
        Dialog(
            onDismissRequest = { imageToShow = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black).clickable { imageToShow = null }
            ) {
                AsyncImage(
                    model = File(imageToShow!!),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
                // Кнопка закрытия с пружинкой и фоном
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.5f),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                        .size(48.dp)
                        .bounceClick { imageToShow = null }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Close, contentDescription = "Закрыть", tint = Color.White)
                    }
                }
            }
        }
    }

    Scaffold(
        // НОВАЯ КНОПКА ПОДЕЛИТЬСЯ (FAB)
        floatingActionButton = {
            if (!isEditing && document != null) {
                Surface(
                    shape = RoundedCornerShape(16.dp), // Скругленный квадрат как на скриншоте
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shadowElevation = 4.dp,
                    modifier = Modifier
                        .size(64.dp)
                        .bounceClick {
                            // Собираем все данные в один красивый текст
                            val shareText = StringBuilder().apply {
                                append("${document!!.documentType}\n\n")
                                document!!.fieldsData.forEach { (key, value) ->
                                    if (value.isNotBlank()) {
                                        append("$key: $value\n")
                                    }
                                }
                            }.toString()

                            // Вызываем системное меню "Поделиться"
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
            // --- ШАПКА В СТИЛЕ MATERIAL EXPRESSIVE ---
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
                    // Панель с кнопками навигации и действий
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
                                    imageVector = if (isEditing) Icons.Filled.Close else Icons.Filled.ArrowBack,
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
                                            document?.let { doc -> viewModel.updateFields(doc, editedFields.toMap()) }
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
                                    modifier = Modifier
                                        .size(44.dp)
                                        .bounceClick { showDeleteDocDialog = true }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Filled.Delete,
                                            contentDescription = "Удалить",
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .size(44.dp)
                                        .bounceClick { isEditing = true }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Filled.Edit,
                                            contentDescription = "Редактировать",
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Заголовок (Тип документа) с приклеенным "о"
                    Text(
                        text = if (isEditing) "Редактирование" else (document?.documentType?.replace(" о ", " о\u00A0") ?: "Загрузка..."),
                        fontSize = 32.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }

            // --- ОСНОВНОЙ КОНТЕНТ ---
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
                            // 1. Берем эталонный порядок полей из нашего шаблона
                            val orderedLabels = com.poltorashka.documents.data.DocumentTemplates.getFieldsForType(doc.documentType)

                            // 2. Добавляем поля, которые есть в документе, но вдруг не попали в шаблон (защита от багов)
                            val extraLabels = doc.fieldsData.keys.filter { !orderedLabels.contains(it) }
                            val finalLabels = orderedLabels + extraLabels

                            if (isEditing) {
                                // Отрисовываем поля для редактирования в правильном порядке
                                finalLabels.forEach { label ->
                                    OutlinedTextField(
                                        value = editedFields[label] ?: "",
                                        onValueChange = { newValue -> editedFields[label] = newValue },
                                        label = { Text(label) },
                                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                                    )
                                }
                            } else {
                                // Отрисовываем поля для просмотра в правильном порядке
                                finalLabels.forEach { label ->
                                    // Показываем поле только если в нем реально есть текст (или если оно есть в ключах)
                                    if (doc.fieldsData.containsKey(label)) {
                                        val value = doc.fieldsData[label] ?: ""
                                        DetailField(
                                            label = label,
                                            value = value,
                                            onCopy = { textToCopy ->
                                                if (textToCopy.isNotBlank()) {
                                                    clipboardManager.setText(AnnotatedString(textToCopy))
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
                                .bounceClick { photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) }
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 16.dp)) {
                                Text("+ Добавить", fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                            }
                        }
                    }

                    if (doc.photoUris.isEmpty()) {
                        Text("Фотографии еще не добавлены", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
                    } else {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(vertical = 8.dp)
                        ) {
                            items(doc.photoUris) { path ->
                                Box {
                                    AsyncImage(
                                        model = File(path),
                                        contentDescription = "Скан",
                                        modifier = Modifier
                                            .size(160.dp)
                                            .clip(RoundedCornerShape(16.dp))
                                            .clickable { imageToShow = path },
                                        contentScale = ContentScale.Crop
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.errorContainer,
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(6.dp)
                                            .size(32.dp)
                                            .bounceClick { imageToDelete = path }
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                Icons.Filled.Close,
                                                contentDescription = "Удалить",
                                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Дополнительный отступ снизу, чтобы текст не прятался за кнопкой "Поделиться"
                    Spacer(modifier = Modifier.height(80.dp))
                }
            }
        }
    }
}

// ОБНОВЛЕННЫЙ DETAIL FIELD С ФУНКЦИЕЙ КОПИРОВАНИЯ
@Composable
fun DetailField(label: String, value: String, onCopy: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .bounceClick { onCopy(value) } // Наша пружинка при клике на строчку!
            .padding(vertical = 8.dp, horizontal = 4.dp)
    ) {
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text(text = value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
    }
}