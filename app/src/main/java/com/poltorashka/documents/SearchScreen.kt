package com.poltorashka.documents

import coil.compose.AsyncImage

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.poltorashka.documents.data.AppDatabase
import com.poltorashka.documents.data.DocumentTemplates
import com.poltorashka.documents.ui.theme.HomeTitleFontFamily
import androidx.compose.ui.res.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onBackClick: () -> Unit,
    onHomeClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onAddClick: () -> Unit,
    onDocumentClick: (Int) -> Unit,
    isWideLayout: Boolean = false,
    viewModel: DocumentsViewModel = viewModel(
        factory = DocumentsViewModelFactory(
            AppDatabase.getDatabase(LocalContext.current).documentDao(),
            AppDatabase.getDatabase(LocalContext.current).folderDao()
        )
    )
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val allDocs by viewModel.allDocuments.collectAsState()
    val resources = LocalResources.current
    val configuration = LocalConfiguration.current
    val gridState = rememberLazyGridState()
    val emptyScrollState = rememberScrollState()
    val headerScrollBehavior = rememberHeaderScrollBehavior(if (isWideLayout) 36.dp else 76.dp)
    val headerCollapseFraction = headerScrollBehavior.state.collapsedFraction

    LaunchedEffect(searchQuery) { gridState.scrollToItem(0) }

    val filteredDocs = remember(searchQuery, allDocs, configuration) {
        if (searchQuery.isBlank()) {
            emptyList()
        } else {
            allDocs.filter { doc ->
                val localizedType = DocumentTemplates.documentTypeLabel(doc.documentType, resources)
                val matchType = doc.documentType.contains(searchQuery, ignoreCase = true) ||
                    localizedType.contains(searchQuery, ignoreCase = true)
                val matchFields =
                    doc.fieldsData.values.any { it.contains(searchQuery, ignoreCase = true) }
                matchType || matchFields
            }
        }
    }

    // --- НОВЫЙ БЛОК: Вычисляем отступы для планшета ---
    val headerHorizontalPadding = if (isWideLayout) 16.dp else 24.dp
    val contentLeftPadding = 16.dp
    // ---------------------------------------------------

    Scaffold(
        modifier = Modifier.nestedScroll(headerScrollBehavior.nestedScrollConnection),
        containerColor = Color.Transparent,
        topBar = {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = if (isWideLayout) 16.dp else 0.dp)
                    .then(
                        if (isWideLayout) Modifier.statusBarsPadding().padding(top = 8.dp)
                        else Modifier
                    ),
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = if (isWideLayout) RoundedCornerShape(HeaderCornerRadius)
                else RoundedCornerShape(bottomStart = HeaderCornerRadius, bottomEnd = HeaderCornerRadius),
                shadowElevation = 2.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .headerStatusBarPadding(isWideLayout)
                        .padding(
                            top = androidx.compose.ui.unit.lerp(
                                if (isWideLayout) 24.dp else 24.dp,
                                if (isWideLayout) 16.dp else 8.dp,
                                headerCollapseFraction
                            ),
                            bottom = androidx.compose.ui.unit.lerp(24.dp, 16.dp, headerCollapseFraction),
                            start = headerHorizontalPadding,
                            end = headerHorizontalPadding
                        )
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.ui_poisk),
                            fontSize = (32f - 5f * headerCollapseFraction).sp,
                            fontFamily = HomeTitleFontFamily,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(modifier = Modifier.width(12.dp))

                        AsyncImage(
                            model = R.drawable.serch_icon,
                            contentDescription = stringResource(R.string.ui_ikonka_prilozheniya),
                            modifier = Modifier.size(30.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(androidx.compose.ui.unit.lerp(24.dp, 12.dp, headerCollapseFraction)))

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(stringResource(R.string.ui_nazvanie_nomer_ili_dannye)) },
                        leadingIcon = {
                            Icon(
                                Icons.Filled.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(
                                        Icons.Filled.Clear,
                                        contentDescription = stringResource(R.string.ui_ochistit),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        },
                        shape = RoundedCornerShape(24.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent
                        )
                    )
                }
            }
        }

    ) { innerPadding ->
        // --- РЕЗУЛЬТАТЫ ПОИСКА ---
        Column(
            modifier = Modifier
                .fillMaxSize()
                // Добавлен адаптивный отступ контента (contentLeftPadding)
                .padding(
                    top = (innerPadding.calculateTopPadding() - HeaderCornerRadius).coerceAtLeast(0.dp),
                    start = contentLeftPadding,
                    end = 16.dp
                )
                .clipToBounds()
        ) {
            if (searchQuery.isBlank() || filteredDocs.isEmpty()) {
                val hasNoResults = searchQuery.isNotBlank()
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            top = HeaderCornerRadius,
                            bottom = innerPadding.calculateBottomPadding() + if (isWideLayout) 16.dp else 96.dp
                        )
                        .verticalScroll(emptyScrollState),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    AsyncImage(
                        model = R.drawable.img_wolf_keyboard,
                        contentDescription = stringResource(R.string.ui_illyustratsiya_poiska),
                        modifier = Modifier
                            .size(160.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )

                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = stringResource(
                            if (hasNoResults) R.string.ui_po_vashemu_zaprosu_nichego_ne_naydeno
                            else R.string.ui_vvedite_dannye_dlya_poiska
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.widthIn(max = 360.dp)
                    )
                    if (hasNoResults) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.search_try_another_query),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.widthIn(max = 320.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        TextButton(onClick = { searchQuery = "" }) {
                            Text(stringResource(R.string.search_clear_query))
                        }
                    }
                }
            } else {
                LazyVerticalGrid(
                    state = gridState,
                    // Адаптивная сетка вместо жестких двух колонок!
                    columns = GridCells.Adaptive(minSize = 170.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        top = HeaderCornerRadius + 16.dp,
                        bottom = innerPadding.calculateBottomPadding() + if (isWideLayout) 24.dp else 120.dp
                    )
                ) {
                    items(filteredDocs) { doc ->
                        DocumentCard(document = doc, onClick = { onDocumentClick(doc.id) })
                    }
                }
            }
        }
    }
}
