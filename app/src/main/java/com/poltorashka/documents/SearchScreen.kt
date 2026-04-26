package com.poltorashka.documents

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.poltorashka.documents.data.AppDatabase

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onBackClick: () -> Unit,
    onHomeClick: () -> Unit,       // НОВОЕ: Переход на главную
    onSettingsClick: () -> Unit,   // НОВОЕ: Переход в настройки
    onAddClick: () -> Unit,        // НОВОЕ: Действие для кнопки "+"
    onDocumentClick: (Int) -> Unit,
    viewModel: DocumentsViewModel = viewModel(
        factory = DocumentsViewModelFactory(
            AppDatabase.getDatabase(LocalContext.current).documentDao(),
            AppDatabase.getDatabase(LocalContext.current).folderDao()
        )
    )
) {
    var searchQuery by remember { mutableStateOf("") }
    val allDocs by viewModel.allDocuments.collectAsState()

    // Логика умного поиска
    val filteredDocs = remember(searchQuery, allDocs) {
        if (searchQuery.isBlank()) {
            emptyList()
        } else {
            allDocs.filter { doc ->
                val matchType = doc.documentType.contains(searchQuery, ignoreCase = true)
                val matchFields = doc.fieldsData.values.any { it.contains(searchQuery, ignoreCase = true) }
                matchType || matchFields
            }
        }
    }

    Scaffold(
        bottomBar = {
            // НОВАЯ ПЛАВАЮЩАЯ ПАНЕЛЬ
            CustomFloatingToolbar(
                activeTab = 1, // 1 - это индекс вкладки "Поиск"
                onHomeClick = onHomeClick,
                onSearchClick = { },
                onSettingsClick = onSettingsClick,
                onAddClick = onAddClick
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(bottom = innerPadding.calculateBottomPadding())
        ) {
            // --- НАША НОВАЯ ШИКАРНАЯ ШАПКА ---
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
                    Text(
                        text = "Поиск",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    Spacer(modifier = Modifier.height(24.dp))

                    // СТРОКА ПОИСКА ВСТРОЕНА ПРЯМО В ШАПКУ
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Название, номер или данные...") },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Filled.Clear, contentDescription = "Очистить", tint = MaterialTheme.colorScheme.onSurfaceVariant)
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

            // --- РЕЗУЛЬТАТЫ ПОИСКА ---
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
                    .padding(top = 24.dp) // Отступ от шапки
            ) {
                if (searchQuery.isBlank()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Введите данные для поиска", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else if (filteredDocs.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("По вашему запросу ничего не найдено", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize(),
                        // Распорка
                        contentPadding = PaddingValues(bottom = innerPadding.calculateBottomPadding() + 16.dp)
                    ) {
                        items(filteredDocs) { doc ->
                            DocumentCard(title = doc.documentType, onClick = { onDocumentClick(doc.id) })
                        }
                    }
                }
            }
        }
    }
}