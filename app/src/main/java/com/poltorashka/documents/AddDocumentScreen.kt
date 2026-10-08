package com.poltorashka.documents

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.room.withTransaction
import coil.compose.AsyncImage
import com.poltorashka.documents.data.AppDatabase
import com.poltorashka.documents.data.DocumentEntity
import com.poltorashka.documents.data.DocumentTemplates
import com.poltorashka.documents.data.FolderEntity
import com.poltorashka.documents.ui.theme.HomeTitleFontFamily
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

private fun saveEncryptedFile(context: Context, uri: Uri): String {
    val extension = if (context.contentResolver.getType(uri) == "application/pdf") "pdf" else "jpg"
    val file = File(context.filesDir, "doc_" + UUID.randomUUID() + "." + extension)
    try {
        val input = checkNotNull(context.contentResolver.openInputStream(uri))
        input.use {
            FileSecurity.getEncryptedFile(context, file).openFileOutput().use { output ->
                it.copyTo(output)
            }
        }
        return file.absolutePath
    } catch (error: Exception) {
        file.delete()
        throw error
    }
}

@Composable
fun AddDocumentScreen(profileId: Int, onBackClick: () -> Unit, onSaved: () -> Unit) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val database = remember(context) { AppDatabase.getDatabase(context) }
    val dao = remember(database) { database.documentDao() }
    val folderDao = remember(database) { database.folderDao() }
    val folders by produceState<List<FolderEntity>?>(initialValue = null, key1 = folderDao) {
        folderDao.getAllFolders().collect { value = it }
    }
    val destinationFolder = folders?.firstOrNull { it.id == profileId }
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val headerScrollBehavior = rememberNavigationHeaderScrollBehavior()
    val windowSize = LocalWindowInfo.current.containerSize
    val isWideScreen = with(LocalDensity.current) { windowSize.width.toDp() >= 600.dp }
    val columnsCount = if (isWideScreen) 2 else 1

    val documentTypes = DocumentTemplates.supportedDocumentTypes
    var selectedType by remember { mutableStateOf(documentTypes.first().storageValue) }
    var expanded by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    val inputValues = remember { mutableStateMapOf<String, String>() }
    val currentFields = DocumentTemplates.getFieldsForType(selectedType)
    var selectedImages by remember { mutableStateOf<List<Uri>>(emptyList()) }
    val filePickerLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null && !isSaving && uri !in selectedImages) selectedImages =
                selectedImages + uri
        }
    BackHandler(enabled = isSaving) { /* Finish saving before leaving the form. */ }

    val navigationButton = @Composable {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.size(44.dp)
        ) {
            IconButton(
                onClick = onBackClick,
                enabled = !isSaving,
                modifier = Modifier.fillMaxSize()
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.ui_nazad)
                )
            }
        }
    }

    val documentBlock = @Composable {
        DocumentFormSection(title = stringResource(R.string.document_form_document)) {
            if (destinationFolder != null) {
                Text(
                    text = stringResource(R.string.document_form_folder, destinationFolder.name),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { if (!isSaving) expanded = it }) {
                DocumentFormTextField(
                    value = documentTypes.firstOrNull { it.storageValue == selectedType }
                        ?.let { stringResource(it.labelRes) } ?: selectedType,
                    onValueChange = {},
                    readOnly = true,
                    enabled = !isSaving,
                    singleLine = true,
                    label = { Text(stringResource(R.string.ui_tip_dokumenta)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(
                            ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                            enabled = !isSaving
                        )
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    documentTypes.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(stringResource(option.labelRes)) },
                            enabled = !isSaving,
                            onClick = {
                                selectedType = option.storageValue
                                expanded = false
                                inputValues.clear()
                            }
                        )
                    }
                }
            }
            val currentTag = inputValues[DocumentTemplates.tagFieldKey] ?: ""
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Surface(
                    shape = ScallopShape(petals = 9, depth = 0.12f),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(56.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            currentTag.ifEmpty { "🐺" },
                            fontSize = 24.sp,
                            modifier = Modifier.alpha(if (currentTag.isEmpty()) 0.4f else 1f)
                        )
                    }
                }
                DocumentFormTextField(
                    value = currentTag,
                    onValueChange = { newValue ->
                        emojiTagFromInput(newValue)?.let { tag ->
                            inputValues[DocumentTemplates.tagFieldKey] = tag
                        }
                    },
                    label = { Text(stringResource(R.string.ui_emodzi_teg)) },
                    placeholder = { Text(stringResource(R.string.ui_naprimer)) },
                    enabled = !isSaving,
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }

    val attachmentsBlock = @Composable {
        DocumentFormSection(title = stringResource(R.string.document_form_attachments)) {
            if (selectedImages.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Filled.AttachFile,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                        Text(
                            stringResource(R.string.ui_fayly_eshche_ne_dobavleny),
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            stringResource(R.string.document_form_files_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(selectedImages, key = { it.toString() }) { uri ->
                        PendingAttachmentPreview(
                            uri,
                            enabled = !isSaving,
                            onRemove = { selectedImages = selectedImages - uri })
                    }
                }
            }
            FilledTonalButton(
                onClick = { filePickerLauncher.launch(arrayOf("image/*", "application/pdf")) },
                enabled = !isSaving,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
            ) {
                Icon(
                    Icons.Filled.AttachFile,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.files_add_count, selectedImages.size))
            }
        }
    }

    Scaffold(
        modifier = Modifier.then(
            if (isWideScreen) Modifier else Modifier.nestedScroll(
                headerScrollBehavior.nestedScrollConnection
            )
        ),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            if (isWideScreen) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(
                        bottomStart = HeaderCornerRadius,
                        bottomEnd = HeaderCornerRadius
                    ),
                    shadowElevation = 2.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(24.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        navigationButton()
                        Text(
                            stringResource(R.string.ui_novyy_dokument),
                            fontFamily = HomeTitleFontFamily,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            } else {
                CollapsingNavigationHeader(
                    title = stringResource(R.string.ui_novyy_dokument),
                    scrollBehavior = headerScrollBehavior,
                    navigationIcon = navigationButton
                )
            }
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.background) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Button(
                        enabled = !isSaving && destinationFolder != null,
                        onClick = {
                            if (!isSaving) {
                                // Snapshot the form before starting any suspending work.
                                val typeToSave = selectedType
                                val imagesToSave = selectedImages.toList()
                                val fieldsToSave = inputValues.toMap().mapValues { (key, value) ->
                                    if (DocumentTemplates.isDateField(key)) value.toDateString() else value
                                }
                                isSaving = true
                                expanded = false
                                coroutineScope.launch {
                                    try {
                                        val saved = withContext(Dispatchers.IO) {
                                            if (folderDao.getAllFoldersSnapshot()
                                                    .none { it.id == profileId }
                                            ) {
                                                false
                                            } else {
                                                val savedPaths = mutableListOf<String>()
                                                try {
                                                    imagesToSave.forEach {
                                                        savedPaths += saveEncryptedFile(
                                                            context,
                                                            it
                                                        )
                                                    }
                                                    database.withTransaction {
                                                        check(
                                                            folderDao.getAllFoldersSnapshot()
                                                                .any { it.id == profileId })
                                                        dao.insertDocument(
                                                            DocumentEntity(
                                                                profileId = profileId,
                                                                documentType = typeToSave,
                                                                photoUris = savedPaths.toList(),
                                                                fieldsData = fieldsToSave
                                                            )
                                                        )
                                                    }
                                                    true
                                                } catch (error: Exception) {
                                                    savedPaths.forEach { File(it).delete() }
                                                    throw error
                                                }
                                            }
                                        }
                                        if (saved) onSaved()
                                        else Toast.makeText(
                                            context,
                                            resources.getString(R.string.create_folder_first),
                                            Toast.LENGTH_LONG
                                        ).show()
                                    } catch (cancelled: CancellationException) {
                                        throw cancelled
                                    } catch (_: Exception) {
                                        Toast.makeText(
                                            context,
                                            resources.getString(R.string.document_save_error),
                                            Toast.LENGTH_LONG
                                        ).show()
                                    } finally {
                                        isSaving = false
                                    }
                                }
                            }
                        },
                        modifier = Modifier
                            .widthIn(max = 600.dp)
                            .fillMaxWidth()
                            .height(56.dp)
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = LocalContentColor.current,
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(12.dp))
                        }
                        Text(stringResource(if (isSaving) R.string.document_saving else R.string.ui_sokhranit))
                    }
                }
            }
        }
    ) { innerPadding ->
        val headerOverlap = if (isWideScreen) 0.dp else HeaderCornerRadius
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    top = (innerPadding.calculateTopPadding() - headerOverlap).coerceAtLeast(0.dp),
                    bottom = innerPadding.calculateBottomPadding()
                )
                .consumeWindowInsets(innerPadding)
                .clipToBounds()
                .verticalScroll(scrollState)
                .padding(start = 16.dp, end = 16.dp, top = headerOverlap + 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            if (isWideScreen) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(Modifier.weight(1f)) { documentBlock() }
                    Box(Modifier.weight(1f)) { attachmentsBlock() }
                }
            } else {
                documentBlock()
            }
            DocumentFormSection(title = stringResource(R.string.document_form_data)) {
                currentFields.chunked(columnsCount).forEach { rowFields ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        rowFields.forEach { field ->
                            val isDateField = DocumentTemplates.isDateField(field.storageKey)
                            DocumentFormTextField(
                                value = inputValues[field.storageKey] ?: "",
                                onValueChange = { newValue ->
                                    inputValues[field.storageKey] =
                                        if (isDateField) newValue.filter(Char::isDigit)
                                            .take(8) else newValue
                                },
                                label = { Text(stringResource(field.labelRes)) },
                                visualTransformation = if (isDateField) DateTransformation() else VisualTransformation.None,
                                keyboardOptions = if (isDateField) KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default,
                                enabled = !isSaving,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        repeat(columnsCount - rowFields.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
            if (!isWideScreen) attachmentsBlock()
            if (folders != null && destinationFolder == null) {
                Text(
                    stringResource(R.string.create_folder_first),
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

private data class PendingAttachmentInfo(val name: String? = null, val isPdf: Boolean = false)

@Composable
private fun PendingAttachmentPreview(uri: Uri, enabled: Boolean, onRemove: () -> Unit) {
    val context = LocalContext.current
    val info by produceState(PendingAttachmentInfo(), uri, context) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val name = context.contentResolver.query(
                    uri,
                    arrayOf(OpenableColumns.DISPLAY_NAME),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(
                        cursor.getColumnIndexOrThrow(
                            OpenableColumns.DISPLAY_NAME
                        )
                    ) else null
                }
                PendingAttachmentInfo(
                    name,
                    context.contentResolver.getType(uri) == "application/pdf" || name?.endsWith(
                        ".pdf",
                        ignoreCase = true
                    ) == true
                )
            }.getOrDefault(PendingAttachmentInfo())
        }
    }
    Column(modifier = Modifier.width(144.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(modifier = Modifier.size(144.dp)) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxSize()
            ) {
                if (info.isPdf) {
                    Column(
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Filled.Description,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.ui_pdf_dokument),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                } else {
                    AsyncImage(
                        model = uri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(16.dp))
                    )
                }
            }
            IconButton(
                onClick = onRemove,
                enabled = enabled,
                modifier = Modifier.align(Alignment.TopEnd)
            ) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.errorContainer) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = stringResource(R.string.ui_udalit),
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier
                            .padding(6.dp)
                            .size(20.dp)
                    )
                }
            }
        }
        Text(
            info.name ?: stringResource(R.string.document_form_file),
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
