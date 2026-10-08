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
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import bounceClick
import coil.compose.AsyncImage
import com.poltorashka.documents.data.AppDatabase
import com.poltorashka.documents.ui.theme.HomeTitleFontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import androidx.compose.ui.res.stringResource

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

private fun openFileExternally(context: Context, path: String) {
    try {
        val isPdf = path.endsWith(".pdf", ignoreCase = true)
        val extension = if (isPdf) "pdf" else "jpg"
        val mimeType = if (isPdf) "application/pdf" else "image/jpeg"

        val encryptedFile = File(path)
        val decryptedBytes = FileSecurity.decryptFile(context, encryptedFile)

        val tempFile = File(context.cacheDir, "temp_preview.$extension")
        tempFile.writeBytes(decryptedBytes)

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            tempFile
        )

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(intent)

    } catch (e: Exception) {
        Toast.makeText(context, context.getString(R.string.document_open_error, e.message.orEmpty()), Toast.LENGTH_LONG).show()
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
            contentDescription = stringResource(R.string.ui_rasshifrovannyy_skan),
            modifier = finalModifier,
            contentScale = contentScale
        )
    } else if (hasError) {
        Box(modifier = finalModifier.background(MaterialTheme.colorScheme.errorContainer), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.ui_oshibka), tint = MaterialTheme.colorScheme.onErrorContainer)
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
    val resources = LocalResources.current

    val configuration = LocalConfiguration.current
    val isWideScreen = configuration.screenWidthDp >= 600
    val columnsCount = if (isWideScreen) 2 else 1
    val headerScrollBehavior = rememberNavigationHeaderScrollBehavior()

    var imageToDelete by remember { mutableStateOf<String?>(null) }
    var isEditing by remember { mutableStateOf(false) }
    var showDeleteDocDialog by remember { mutableStateOf(false) }
    val editedFields = remember { mutableStateMapOf<String, String>() }

    LaunchedEffect(isEditing) {
        if (isEditing) {
            document?.fieldsData?.let { data ->
                editedFields.clear()
                data.forEach { (key, value) ->
                    if (com.poltorashka.documents.data.DocumentTemplates.isDateField(key)) {
                        editedFields[key] = value.filter { it.isDigit() }
                    } else {
                        editedFields[key] = value
                    }
                }
            }
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            document?.let { doc ->
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
            title = { Text(stringResource(R.string.ui_udalit_fayl)) },
            text = { Text(stringResource(R.string.ui_eto_deystvie_nelzya_budet_otmenit)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        document?.let { doc -> viewModel.removePhoto(doc, imageToDelete!!) }
                        imageToDelete = null
                    }
                ) { Text(stringResource(R.string.ui_udalit), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { imageToDelete = null }) { Text(stringResource(R.string.ui_otmena)) } }
        )
    }

    if (showDeleteDocDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDocDialog = false },
            title = { Text(stringResource(R.string.ui_udalit_dokument)) },
            text = { Text(stringResource(R.string.ui_dokument_i_vse_prikreplennye_k_nemu_fayly_budut)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        document?.let { doc ->
                            viewModel.deleteDocument(doc, onDeleted = onBackClick)
                        }
                        showDeleteDocDialog = false
                    }
                ) { Text(stringResource(R.string.ui_udalit_navsegda), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDeleteDocDialog = false }) { Text(stringResource(R.string.ui_otmena)) } }
        )
    }

    Scaffold(
        modifier = Modifier.then(if (isWideScreen) Modifier else Modifier.nestedScroll(headerScrollBehavior.nestedScrollConnection)),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            val titleText = if (isEditing) stringResource(R.string.ui_redaktirovanie) else document?.let {
                com.poltorashka.documents.data.DocumentTemplates.documentTypeLabel(it.documentType, resources)
                    .replace(" о ", " о\u00A0")
            } ?: stringResource(R.string.ui_zagruzka)
            if (isWideScreen) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp),
                    shadowElevation = 2.dp
                ) {
                    // РЕЖИМ ПЛАНШЕТА: Компактно, в одну строку
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 56.dp, bottom = 16.dp, start = 16.dp, end = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape, color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.size(44.dp).bounceClick { if (isEditing) isEditing = false else onBackClick() }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(if (isEditing) Icons.Filled.Close else Icons.AutoMirrored.Filled.ArrowBack, contentDescription = if (isEditing) stringResource(R.string.ui_otmena) else stringResource(R.string.ui_nazad), tint = MaterialTheme.colorScheme.onSurface)
                            }
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Text(
                            text = titleText, fontSize = 24.sp, fontFamily = HomeTitleFontFamily, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (isEditing) {
                                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, modifier = Modifier.height(44.dp).bounceClick { val formattedFields = editedFields.mapValues { (key, value) -> if (com.poltorashka.documents.data.DocumentTemplates.isDateField(key)) value.toDateString() else value }; document?.let { doc -> viewModel.updateFields(doc, formattedFields) }; isEditing = false }) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 20.dp)) { Text(stringResource(R.string.ui_sokhranit), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary) }
                                }
                            } else {
                                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.size(44.dp).bounceClick { showDeleteDocDialog = true }) {
                                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.ui_udalit), tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(20.dp)) }
                                }
                                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(44.dp).bounceClick { isEditing = true }) {
                                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.ui_redaktirovat), tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(20.dp)) }
                                }
                            }
                        }
                    }
                }
            } else {
                CollapsingNavigationHeader(
                    title = titleText,
                    scrollBehavior = headerScrollBehavior,
                    navigationIcon = {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.size(44.dp).bounceClick {
                                if (isEditing) isEditing = false else onBackClick()
                            }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (isEditing) Icons.Filled.Close else Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = if (isEditing) stringResource(R.string.ui_otmena) else stringResource(R.string.ui_nazad),
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    },
                    actions = {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (isEditing) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.height(44.dp).bounceClick {
                                        val formattedFields = editedFields.mapValues { (key, value) ->
                                            if (com.poltorashka.documents.data.DocumentTemplates.isDateField(key)) value.toDateString() else value
                                        }
                                        document?.let { doc -> viewModel.updateFields(doc, formattedFields) }
                                        isEditing = false
                                    }
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 20.dp)) {
                                        Text(stringResource(R.string.ui_sokhranit), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                                    }
                                }
                            } else {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.errorContainer,
                                    modifier = Modifier.size(44.dp).bounceClick { showDeleteDocDialog = true }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.ui_udalit), tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(20.dp))
                                    }
                                }
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(44.dp).bounceClick { isEditing = true }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.ui_redaktirovat), tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(20.dp))
                                    }
                                }
                            }
                        }
                    }
                )
            }
        },
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
                            val orderedLabels = com.poltorashka.documents.data.DocumentTemplates.getFieldsForType(doc.documentType).map { it.storageKey }
                            val extraLabels = doc.fieldsData.keys.filter { !orderedLabels.contains(it) }
                            val finalLabels = orderedLabels + extraLabels

                            val shareText = StringBuilder().apply {
                                append("${com.poltorashka.documents.data.DocumentTemplates.documentTypeLabel(doc.documentType, resources)}\n\n")
                                finalLabels.forEach { key ->
                                    val value = doc.fieldsData[key]
                                    if (!value.isNullOrBlank()) {
                                        append("${com.poltorashka.documents.data.DocumentTemplates.fieldLabel(key, resources)}: $value\n")
                                    }
                                }
                            }.toString()

                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, shareText)
                            }
                            context.startActivity(Intent.createChooser(intent, resources.getString(R.string.ui_podelitsya_dokumentom)))
                        }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.Share,
                            contentDescription = stringResource(R.string.ui_podelitsya),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        document?.let { doc ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
                    .then(
                        if (isWideScreen) Modifier else Modifier
                            .padding(top = (innerPadding.calculateTopPadding() - HeaderCornerRadius).coerceAtLeast(0.dp))
                            .clipToBounds()
                    )
                    .verticalScroll(rememberScrollState())
            ) {
                Spacer(modifier = Modifier.height(
                    if (isWideScreen) innerPadding.calculateTopPadding() + 24.dp
                    else HeaderCornerRadius + 24.dp
                ))

                ElevatedCard(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                    shape = RoundedCornerShape(28.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        val orderedLabels = com.poltorashka.documents.data.DocumentTemplates.getFieldsForType(doc.documentType).map { it.storageKey }
                        val extraLabels = doc.fieldsData.keys.filter { !orderedLabels.contains(it) }
                        val finalLabels = orderedLabels + extraLabels

                        val displayLabels = finalLabels.filter { it != com.poltorashka.documents.data.DocumentTemplates.tagFieldKey }

                        if (isEditing) {
                            Text(
                                text = stringResource(R.string.document_form_data),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            val currentTag = editedFields[com.poltorashka.documents.data.DocumentTemplates.tagFieldKey] ?: ""
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
                                DocumentFormTextField(
                                    value = currentTag,
                                    onValueChange = { newValue ->
                                        emojiTagFromInput(newValue)?.let { tag ->
                                            editedFields[com.poltorashka.documents.data.DocumentTemplates.tagFieldKey] = tag
                                        }
                                    },
                                    label = { Text(stringResource(R.string.ui_emodzi_teg)) },
                                    placeholder = { Text(stringResource(R.string.ui_naprimer)) },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            displayLabels.chunked(columnsCount).forEach { rowLabels ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    rowLabels.forEach { label ->
                                        val isDateField = com.poltorashka.documents.data.DocumentTemplates.isDateField(label)
                                        DocumentFormTextField(
                                            value = editedFields[label] ?: "",
                                            onValueChange = { newValue ->
                                                if (isDateField) {
                                                    val digits = newValue.filter { it.isDigit() }.take(8)
                                                    editedFields[label] = digits
                                                } else {
                                                    editedFields[label] = newValue
                                                }
                                            },
                                            label = { Text(com.poltorashka.documents.data.DocumentTemplates.fieldLabel(label, resources)) },
                                            visualTransformation = if (isDateField) DateTransformation() else VisualTransformation.None,
                                            keyboardOptions = if (isDateField) KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default,
                                            modifier = Modifier.weight(1f).padding(bottom = 8.dp)
                                        )
                                    }
                                    if (rowLabels.size < columnsCount) {
                                        repeat(columnsCount - rowLabels.size) {
                                            Spacer(modifier = Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                        } else {
                            displayLabels.chunked(columnsCount).forEach { rowLabels ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    rowLabels.forEach { label ->
                                        Box(modifier = Modifier.weight(1f)) {
                                            if (doc.fieldsData.containsKey(label)) {
                                                val value = doc.fieldsData[label] ?: ""
                                                DetailField(
                                                    label = com.poltorashka.documents.data.DocumentTemplates.fieldLabel(label, resources),
                                                    value = value,
                                                    onCopy = { textToCopy ->
                                                        if (textToCopy.isNotBlank()) {
                                                            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                                            val clip = android.content.ClipData.newPlainText(resources.getString(R.string.ui_skopirovano), textToCopy)
                                                            clipboard.setPrimaryClip(clip)
                                                            Toast.makeText(context, resources.getString(R.string.ui_skopirovano), Toast.LENGTH_SHORT).show()
                                                        }
                                                    }
                                                )
                                            }
                                        }
                                    }
                                    if (rowLabels.size < columnsCount) {
                                        repeat(columnsCount - rowLabels.size) {
                                            Spacer(modifier = Modifier.weight(1f))
                                        }
                                    }
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
                    Text(stringResource(R.string.detail_files_count, doc.photoUris.size), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)

                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier
                            .height(36.dp)
                            .bounceClick { filePickerLauncher.launch(arrayOf("image/*", "application/pdf")) }
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 16.dp)) {
                            Text(stringResource(R.string.ui_dobavit), fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                    }
                }

                if (doc.photoUris.isEmpty()) {
                    Text(stringResource(R.string.ui_fayly_eshche_ne_dobavleny), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
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
                                    openFileExternally(context, path)
                                }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(80.dp + innerPadding.calculateBottomPadding()))
            }
        }
    }
}

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
                    Text(stringResource(R.string.ui_pdf_dokument), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
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
