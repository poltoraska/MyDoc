package com.poltorashka.documents

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.poltorashka.documents.data.AppDatabase
import com.poltorashka.documents.data.DocumentEntity
import com.poltorashka.documents.data.DocumentTemplates
import kotlinx.coroutines.launch
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddDocumentScreen(profileId: Int, onBackClick: () -> Unit, onSaved: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    val documentTypes = DocumentTemplates.supportedDocumentTypes
    var selectedType by remember { mutableStateOf(documentTypes[0]) }

    val inputValues = remember { mutableStateMapOf<String, String>() }
    val currentFields = DocumentTemplates.getFieldsForType(selectedType)

    var selectedImages by remember { mutableStateOf<List<Uri>>(emptyList()) }

    // Разрешает выбирать только 1 файл за раз
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            selectedImages = selectedImages + uri
        }
    }

    val context = LocalContext.current
    val dao = remember { AppDatabase.getDatabase(context).documentDao() }
    val coroutineScope = rememberCoroutineScope()

    // --- Вычисляет количество колонок ---
    val configuration = LocalConfiguration.current
    val isWideScreen = configuration.screenWidthDp >= 600
    val columnsCount = if (isWideScreen) 2 else 1

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
                        val savedPaths = selectedImages.mapNotNull { uri ->
                            saveEncryptedFile(context, uri)
                        }

                        // Перед сохранением в БД превращает цифры в дату
                        val formattedFields = inputValues.mapValues { (key, value) ->
                            if (key.contains("Дата", ignoreCase = true)) value.toDateString() else value
                        }

                        val newDocument = DocumentEntity(
                            profileId = profileId,
                            documentType = selectedType,
                            photoUris = savedPaths,
                            fieldsData = formattedFields
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

            // Поле ввода для Эмодзи-тега ---
            val currentTag = inputValues["Тег"] ?: ""

            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = ScallopShape(petals = 9, depth = 0.12f),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(56.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = currentTag.ifEmpty { "🐺" },
                            fontSize = 24.sp,
                            modifier = Modifier.alpha(if (currentTag.isEmpty()) 0.4f else 1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                OutlinedTextField(
                    value = currentTag,
                    onValueChange = { newValue ->
                        if (newValue.isEmpty()) {
                            inputValues["Тег"] = ""
                        } else {
                            // Удаляет обычный текст, цифры и пунктуацию
                            val filtered = newValue.replace(Regex("[A-Za-zА-Яа-я0-9\\s\\.,!?\\-()'\"#@]"), "")
                            if (filtered.isNotEmpty()) {
                                // Ровно 1 символ включая сложные смайлики
                                val codePoint = filtered.codePointAt(0)
                                inputValues["Тег"] = String(Character.toChars(codePoint))
                            }
                        }
                    },
                    label = { Text("Эмодзи-тег") },
                    placeholder = { Text("Например: 🚗") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            // --- Сетка полей (1 или 2 колонки) ---
            currentFields.chunked(columnsCount).forEach { rowFields ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    rowFields.forEach { fieldLabel ->
                        val isDateField = fieldLabel.contains("Дата", ignoreCase = true)

                        OutlinedTextField(
                            value = inputValues[fieldLabel] ?: "",
                            onValueChange = { newValue ->
                                if (isDateField) {
                                    // Только цифры и не больше 8
                                    val digits = newValue.filter { it.isDigit() }.take(8)
                                    inputValues[fieldLabel] = digits
                                } else {
                                    inputValues[fieldLabel] = newValue
                                }
                            },
                            label = { Text(fieldLabel) },
                            // Маска и цифровая клавиатура для дат
                            visualTransformation = if (isDateField) DateTransformation() else VisualTransformation.None,
                            keyboardOptions = if (isDateField) KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default,
                            modifier = Modifier.weight(1f).padding(bottom = 8.dp)
                        )
                    }
                    // Заполняет пустоту, если в последнем ряду меньше элементов, чем колонок
                    if (rowFields.size < columnsCount) {
                        repeat(columnsCount - rowFields.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedButton(
                onClick = { filePickerLauncher.launch(arrayOf("image/*", "application/pdf")) },
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) { Text("Добавить файлы (${selectedImages.size})") }

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