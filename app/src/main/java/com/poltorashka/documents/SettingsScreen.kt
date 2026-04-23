package com.poltorashka.documents

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import bounceClick
import com.poltorashka.documents.data.AppDatabase
import com.poltorashka.documents.data.FolderEntity
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    onHomeClick: () -> Unit,
    onSearchClick: () -> Unit,
    viewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModelFactory(
            AppDatabase.getDatabase(LocalContext.current).folderDao(),
            AppDatabase.getDatabase(LocalContext.current).documentDao()
        )
    )
) {
    val context = LocalContext.current
    val prefs = remember { UserPreferences(context) }
    var userName by remember { mutableStateOf(prefs.userName) }

    val folders by viewModel.folders.collectAsState()
    val localFolders = remember(folders) { folders.toMutableStateList() }

    var showAddDialog by remember { mutableStateOf(false) }
    var folderToEdit by remember { mutableStateOf<FolderEntity?>(null) }
    var folderToDelete by remember { mutableStateOf<FolderEntity?>(null) }
    var revealedFolderId by remember { mutableStateOf<Int?>(null) }

    // Состояния для Drag & Drop
    val listState = rememberLazyListState()
    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var draggingItemOffset by remember { mutableStateOf(0f) }
    val density = LocalDensity.current
    val itemHeightPx = with(density) { 72.dp.toPx() }

    // Состояния безопасности
    var isPinEnabledState by remember { mutableStateOf(prefs.isPinEnabled) }
    var isBiometricEnabledState by remember { mutableStateOf(prefs.isBiometricEnabled) }
    var showPinSetupDialog by remember { mutableStateOf(false) }

    // Глобальный скролл экрана
    val screenScrollState = rememberScrollState()

    Scaffold(
        bottomBar = {
            CustomFloatingToolbar(
                activeTab = 2, // Вкладка Настройки
                onHomeClick = onHomeClick,
                onSearchClick = onSearchClick,
                onSettingsClick = { },
                // По нажатию на + в настройках можно сразу вызывать окно создания папки
                onAddClick = { showAddDialog = true }
            )
        }
    ) { innerPadding ->
        // ГЛАВНЫЙ КОНТЕЙНЕР ЭКРАНА
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {

            // 1. НОВАЯ ШИКАРНАЯ ШАПКА
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp),
                shadowElevation = 2.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 80.dp, bottom = 24.dp, start = 24.dp, end = 24.dp) // Обычные отступы шапки
                ) {
                    Text(
                        text = "Настройки",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Управление приложением",
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                    )
                }
            }

            // 2. БЛОК С САМИМИ НАСТРОЙКАМИ (Он скроллится и имеет отступ снизу под панель!)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(screenScrollState)
                    .padding(horizontal = 16.dp)
                    .padding(top = 24.dp, bottom = innerPadding.calculateBottomPadding() + 16.dp)
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures { _, _ -> revealedFolderId = null }
                    },
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {

                // --- БЛОК 1: ПРОФИЛЬ ---
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text("Ваш профиль", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedTextField(
                            value = userName,
                            onValueChange = { userName = it },
                            label = { Text("Имя") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        // НОВАЯ КНОПКА "СОХРАНИТЬ" (С пружинкой)
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier
                                .align(Alignment.End)
                                .bounceClick { prefs.userName = userName.trim() }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Сохранить", color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // --- БЛОК 2: ПАПКИ ---
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Папки", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                Text("Удерживайте, чтобы переместить", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            // Кнопку "Добавить" можно тоже обновить по желанию, но иконка смотрится гармонично
                            IconButton(onClick = { showAddDialog = true }) {
                                Icon(Icons.Filled.Add, contentDescription = "Добавить")
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))

                        LazyColumn(
                            state = listState,
                            userScrollEnabled = false,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 2000.dp)
                                .pointerInput(localFolders) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = { offset ->
                                            val item = listState.layoutInfo.visibleItemsInfo.firstOrNull {
                                                offset.y.toInt() in it.offset..(it.offset + it.size)
                                            }
                                            if (item != null) {
                                                draggingIndex = item.index
                                                draggingItemOffset = 0f
                                                revealedFolderId = null
                                            }
                                        },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            val currentIndex = draggingIndex ?: return@detectDragGesturesAfterLongPress
                                            draggingItemOffset += dragAmount.y

                                            val threshold = itemHeightPx * 0.5f
                                            if (draggingItemOffset > threshold && currentIndex < localFolders.size - 1) {
                                                val nextIndex = currentIndex + 1
                                                val temp = localFolders[currentIndex]
                                                localFolders[currentIndex] = localFolders[nextIndex]
                                                localFolders[nextIndex] = temp
                                                draggingIndex = nextIndex
                                                draggingItemOffset -= itemHeightPx
                                            } else if (draggingItemOffset < -threshold && currentIndex > 0) {
                                                val prevIndex = currentIndex - 1
                                                val temp = localFolders[currentIndex]
                                                localFolders[currentIndex] = localFolders[prevIndex]
                                                localFolders[prevIndex] = temp
                                                draggingIndex = prevIndex
                                                draggingItemOffset += itemHeightPx
                                            }
                                        },
                                        onDragEnd = {
                                            draggingIndex = null
                                            draggingItemOffset = 0f
                                            viewModel.updateFoldersOrder(localFolders)
                                        },
                                        onDragCancel = {
                                            draggingIndex = null
                                            draggingItemOffset = 0f
                                        }
                                    )
                                }
                        ) {
                            itemsIndexed(localFolders, key = { _, folder -> folder.id }) { index, folder ->
                                val modifier = if (index == draggingIndex) {
                                    Modifier.zIndex(1f).graphicsLayer {
                                        translationY = draggingItemOffset
                                        shadowElevation = 16.dp.toPx()
                                    }
                                } else {
                                    Modifier.animateItem()
                                }

                                Box(modifier = modifier) {
                                    CustomSwipeToRevealItem(
                                        folder = folder,
                                        isRevealed = revealedFolderId == folder.id,
                                        onRevealChange = { isRevealed -> revealedFolderId = if (isRevealed) folder.id else null },
                                        onEdit = { folderToEdit = folder; revealedFolderId = null },
                                        onDelete = { folderToDelete = folder; revealedFolderId = null }
                                    )
                                }
                            }
                        }
                    }
                }

                // --- БЛОК 3: БЕЗОПАСНОСТЬ ---
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text("Безопасность", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = "Настройте способы входа в приложение",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Вход по PIN-коду", fontSize = 16.sp)
                            Switch(
                                checked = isPinEnabledState,
                                onCheckedChange = { isChecked ->
                                    if (isChecked) {
                                        showPinSetupDialog = true
                                    } else {
                                        prefs.isPinEnabled = false
                                        prefs.appPin = ""
                                        isPinEnabledState = false
                                    }
                                }
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Вход по биометрии", fontSize = 16.sp)
                            Switch(
                                checked = isBiometricEnabledState,
                                onCheckedChange = { isChecked ->
                                    prefs.isBiometricEnabled = isChecked
                                    isBiometricEnabledState = isChecked
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // ПОЛНОЭКРАННОЕ СОЗДАНИЕ PIN-КОДА (Без черных полос)
    if (showPinSetupDialog) {
        var pinInput by remember { mutableStateOf("") }
        var isConfirmMode by remember { mutableStateOf(false) } // Режим подтверждения
        var firstPin by remember { mutableStateOf("") } // Храним первый введенный пароль
        var isError by remember { mutableStateOf(false) }

        // Сброс ошибки через полсекунды
        LaunchedEffect(isError) {
            if (isError) {
                delay(500)
                pinInput = ""
                isError = false
            }
        }

        // Перехватываем системную кнопку "Назад", чтобы просто закрыть создание PIN-кода
        androidx.activity.compose.BackHandler {
            showPinSetupDialog = false
            isPinEnabledState = false
        }

        // Вместо Dialog используем обычный Surface поверх всего экрана!
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding() // Убираем черные полосы сверху и снизу
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Распорка сверху
                Spacer(modifier = Modifier.weight(0.3f))

                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = if (isError) "PIN-коды не совпадают"
                    else if (isConfirmMode) "Повторите PIN-код"
                    else "Создайте PIN-код",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onBackground
                )

                Spacer(modifier = Modifier.height(16.dp))
                PinDots(pinLength = pinInput.length, isError = isError)

                // Главная распорка, выдавливающая клавиатуру вниз
                Spacer(modifier = Modifier.weight(1f))

                CustomNumpad(
                    isBiometricEnabled = false, // При создании пароля биометрия не нужна
                    onBiometricClick = {},
                    onNumberClick = { digit ->
                        if (pinInput.length < 4 && !isError) {
                            pinInput += digit
                            if (pinInput.length == 4) {
                                if (!isConfirmMode) {
                                    // Переходим в режим подтверждения
                                    firstPin = pinInput
                                    pinInput = ""
                                    isConfirmMode = true
                                } else {
                                    // Проверяем совпадение
                                    if (pinInput == firstPin) {
                                        prefs.appPin = pinInput
                                        prefs.isPinEnabled = true
                                        isPinEnabledState = true
                                        showPinSetupDialog = false
                                    } else {
                                        isError = true
                                        isConfirmMode = false
                                    }
                                }
                            }
                        }
                    },
                    onBackspaceClick = {
                        if (pinInput.isNotEmpty() && !isError) pinInput = pinInput.dropLast(1)
                    }
                )

                Spacer(modifier = Modifier.height(24.dp))

                TextButton(
                    onClick = {
                        showPinSetupDialog = false
                        isPinEnabledState = false
                    },
                    modifier = Modifier.padding(bottom = 24.dp)
                ) {
                    Text("Отмена", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp)
                }
            }
        }
    }

    // ДИАЛОГИ ПАПОК
    if (showAddDialog || folderToEdit != null) {
        var folderNameInput by remember { mutableStateOf(folderToEdit?.name ?: "") }
        AlertDialog(
            onDismissRequest = { showAddDialog = false; folderToEdit = null },
            title = { Text(if (folderToEdit != null) "Изменить папку" else "Новая папка") },
            text = {
                OutlinedTextField(
                    value = folderNameInput,
                    onValueChange = { folderNameInput = it },
                    label = { Text("Название") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (folderNameInput.isNotBlank()) {
                        if (folderToEdit != null) viewModel.updateFolderName(folderToEdit!!, folderNameInput)
                        else viewModel.addFolder(folderNameInput)
                    }
                    showAddDialog = false; folderToEdit = null
                }) { Text("Сохранить") }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false; folderToEdit = null }) { Text("Отмена") }
            }
        )
    }

    if (folderToDelete != null) {
        AlertDialog(
            onDismissRequest = { folderToDelete = null },
            title = { Text("Удалить папку?") },
            text = { Text("Папка «${folderToDelete?.name}» и все документы внутри неё будут удалены навсегда.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteFolder(folderToDelete!!.id)
                    folderToDelete = null
                }) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { folderToDelete = null }) { Text("Отмена") } }
        )
    }
}

@Composable
fun CustomSwipeToRevealItem(
    folder: FolderEntity,
    isRevealed: Boolean,
    onRevealChange: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val offset by animateDpAsState(
        targetValue = if (isRevealed) (-120).dp else 0.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
    )

    Box(
        modifier = Modifier.fillMaxWidth().height(64.dp)
    ) {
        Row(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(120.dp)
                .fillMaxHeight(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onEdit,
                modifier = Modifier.background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
            ) {
                Icon(Icons.Filled.Edit, contentDescription = "Изменить", tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier.background(MaterialTheme.colorScheme.errorContainer, CircleShape)
            ) {
                Icon(Icons.Filled.Delete, contentDescription = "Удалить", tint = MaterialTheme.colorScheme.onErrorContainer)
            }
        }

        ElevatedCard(
            modifier = Modifier
                .fillMaxSize()
                .offset(x = offset)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures { _, dragAmount ->
                        if (dragAmount < -5) onRevealChange(true)
                        if (dragAmount > 5) onRevealChange(false)
                    }
                }
                .clickable { if (isRevealed) onRevealChange(false) },
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(folder.name, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Icon(Icons.Filled.Menu, contentDescription = "Перетащить", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}