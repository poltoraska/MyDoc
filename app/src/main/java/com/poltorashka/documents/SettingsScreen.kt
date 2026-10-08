package com.poltorashka.documents

import coil.compose.AsyncImage

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import bounceClick
import com.poltorashka.documents.data.AppDatabase
import com.poltorashka.documents.data.FolderEntity
import com.poltorashka.documents.ui.theme.HomeTitleFontFamily
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    onHomeClick: () -> Unit,
    onSearchClick: () -> Unit,
    viewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModelFactory(
            LocalContext.current.applicationContext,
            AppDatabase.getDatabase(LocalContext.current)
        )
    ),
    onAboutClick: () -> Unit,
    isWideLayout: Boolean? = null
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val prefs = remember { UserPreferences.getInstance(context) }
    var userName by remember { mutableStateOf(prefs.userName) }
    val themeModeState by prefs.themeModeFlow.collectAsState()
    val isDynamicColorState by prefs.dynamicColorFlow.collectAsState()

    var animationsEnabledState by remember { mutableStateOf(prefs.animationsEnabled) }

    val folders by viewModel.folders.collectAsState()
    val localFolders = remember(folders) { folders.toMutableStateList() }

    var showAddDialog by remember { mutableStateOf(false) }
    var folderToEdit by remember { mutableStateOf<FolderEntity?>(null) }
    var folderToDelete by remember { mutableStateOf<FolderEntity?>(null) }
    var revealedFolderId by remember { mutableStateOf<Int?>(null) }

    val listState = rememberLazyListState()
    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var draggingItemOffset by remember { mutableStateOf(0f) }
    val density = LocalDensity.current
    val itemHeightPx = with(density) { 76.dp.toPx() }

    var isPinEnabledState by remember { mutableStateOf(prefs.isPinEnabled) }
    var isBiometricEnabledState by remember { mutableStateOf(prefs.isBiometricEnabled) }
    var showPinSetupDialog by remember { mutableStateOf(false) }

    val screenScrollState = rememberScrollState()

    var showBackupDialog by remember { mutableStateOf(false) }
    var showRestoreDialog by remember { mutableStateOf(false) }
    var backupPasswordTemp by remember { mutableStateOf("") }
    var selectedRestoreUri by remember { mutableStateOf<android.net.Uri?>(null) }

    val haptics = LocalHapticFeedback.current

    // --- НОВЫЙ БЛОК: Вычисляет отступы для планшета ---
    val configuration = LocalConfiguration.current
    val isWideScreen = isWideLayout ?: (configuration.screenWidthDp >= 600)
    val headerHorizontalPadding = if (isWideScreen) 16.dp else 24.dp
    val contentLeftPadding = 16.dp
    val headerScrollBehavior = rememberHeaderScrollBehavior(if (isWideScreen) 36.dp else 76.dp)
    val headerCollapseFraction = headerScrollBehavior.state.collapsedFraction

    val exportLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null && backupPasswordTemp.isNotEmpty()) {
            android.widget.Toast.makeText(context, resources.getString(R.string.ui_sozdanie_kopii), android.widget.Toast.LENGTH_SHORT).show()
            viewModel.createBackup(context, uri, backupPasswordTemp) { success ->
                if (success) {
                    android.widget.Toast.makeText(context, resources.getString(R.string.ui_kopiya_uspeshno_sokhranena), android.widget.Toast.LENGTH_LONG).show()
                } else {
                    android.widget.Toast.makeText(context, resources.getString(R.string.ui_oshibka_sozdaniya_kopii), android.widget.Toast.LENGTH_LONG).show()
                }
                backupPasswordTemp = ""
            }
        }
    }

    val importLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            selectedRestoreUri = uri
            showRestoreDialog = true
        }
    }

    // КОМПОНЕНТЫ БЛОКОВ НАСТРОЕК (Вынесены для удобного перестроения сетки)

    val profileBlock = @Composable {
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(stringResource(R.string.ui_vash_profil), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = userName,
                    onValueChange = { userName = it },
                    label = { Text(stringResource(R.string.ui_imya)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.End).bounceClick { prefs.userName = userName.trim() }
                ) {
                    Row(modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(text = stringResource(R.string.ui_sokhranit), color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    val foldersBlock = @Composable {
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.ui_papki), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text(text = stringResource(R.string.ui_uderzhivayte_chtoby_peremestit_smakhnite_vlevo_c), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        shadowElevation = 2.dp,
                        modifier = Modifier.size(40.dp).bounceClick { showAddDialog = true }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(imageVector = Icons.Filled.Add, contentDescription = stringResource(R.string.ui_dobavit_2), tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(24.dp))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                LazyColumn(
                    state = listState,
                    userScrollEnabled = false,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 2000.dp)
                        .pointerInput(localFolders) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { offset ->
                                    val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { offset.y.toInt() in it.offset..(it.offset + it.size) }
                                    if (item != null) {
                                        draggingIndex = item.index; draggingItemOffset = 0f; revealedFolderId = null
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
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
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    } else if (draggingItemOffset < -threshold && currentIndex > 0) {
                                        val prevIndex = currentIndex - 1
                                        val temp = localFolders[currentIndex]
                                        localFolders[currentIndex] = localFolders[prevIndex]
                                        localFolders[prevIndex] = temp
                                        draggingIndex = prevIndex
                                        draggingItemOffset += itemHeightPx
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                },
                                onDragEnd = { draggingIndex = null; draggingItemOffset = 0f; viewModel.updateFoldersOrder(localFolders) },
                                onDragCancel = { draggingIndex = null; draggingItemOffset = 0f }
                            )
                        }
                ) {
                    itemsIndexed(localFolders, key = { _, folder -> folder.id }) { index, folder ->
                        val modifier = if (index == draggingIndex) Modifier.zIndex(1f).graphicsLayer { translationY = draggingItemOffset; shadowElevation = 16.dp.toPx() } else Modifier.animateItem()
                        val itemShape = when {
                            localFolders.size == 1 -> RoundedCornerShape(28.dp)
                            index == 0 -> RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomStart = 8.dp, bottomEnd = 8.dp)
                            index == localFolders.lastIndex -> RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 28.dp, bottomEnd = 28.dp)
                            else -> RoundedCornerShape(8.dp)
                        }
                        Box(modifier = modifier) {
                            CustomSwipeToRevealItem(
                                folder = folder,
                                shape = itemShape,
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
    }

    val securityBlock = @Composable {
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(stringResource(R.string.ui_bezopasnost), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(text = stringResource(R.string.ui_nastroyte_sposoby_vkhoda_v_prilozhenie), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.ui_vkhod_po_pin_kodu), fontSize = 16.sp)
                    Switch(
                        checked = isPinEnabledState,
                        onCheckedChange = { isChecked ->
                            if (isChecked) { isPinEnabledState = true; showPinSetupDialog = true } else {
                                isPinEnabledState = false; prefs.isPinEnabled = false; prefs.clearPin(); isBiometricEnabledState = false; prefs.isBiometricEnabled = false
                            }
                        }
                    )
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(text = stringResource(R.string.ui_vkhod_po_biometrii), fontSize = 16.sp, color = if (isPinEnabledState) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f))
                    Switch(checked = isBiometricEnabledState, onCheckedChange = { isChecked -> prefs.isBiometricEnabled = isChecked; isBiometricEnabledState = isChecked }, enabled = isPinEnabledState)
                }
            }
        }
    }

    val backupBlock = @Composable {
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(stringResource(R.string.ui_rezervnoe_kopirovanie), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(text = stringResource(R.string.ui_sokhranite_bazu_dokumentov_i_foto_v_zashifrovann), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(20.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f).height(44.dp).bounceClick { showBackupDialog = true }) {
                        Box(contentAlignment = Alignment.Center) { Text(stringResource(R.string.ui_sozdat_kopiyu), color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.weight(1f).height(44.dp).bounceClick { importLauncher.launch(arrayOf("*/*")) }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(stringResource(R.string.ui_vosstanovit), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }

    val appearanceBlock = @Composable {
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(stringResource(R.string.ui_oformlenie), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))

                // Выбор темы (Авто, Светлая, Тёмная)
                ThemeSelectionButtons(
                    selectedMode = themeModeState,
                    animationsEnabled = animationsEnabledState,
                    onModeSelected = { prefs.themeMode = it }
                )

                // Динамические цвета (Android 12+)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.ui_dinamicheskie_tsveta), fontSize = 16.sp)
                            Text(stringResource(R.string.ui_palitra_podstraivaetsya_pod_oboi), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 16.sp)
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        androidx.compose.material3.Switch(
                            checked = isDynamicColorState,
                            onCheckedChange = { prefs.useDynamicColor = it }
                        )
                    }
                }

                // НОВАЯ НАСТРОЙКА: Анимации интерфейса
                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.ui_animatsii_interfeysa), fontSize = 16.sp)
                        Text(stringResource(R.string.ui_plavnoe_poyavlenie_i_effekty_pruzhiny), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 16.sp)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    androidx.compose.material3.Switch(
                        checked = animationsEnabledState,
                        onCheckedChange = {
                            animationsEnabledState = it
                            prefs.animationsEnabled = it // Сохраняем в SharedPreferences
                        }
                    )
                }
            }
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(headerScrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = if (isWideScreen) 16.dp else 0.dp)
                    .then(
                        if (isWideScreen) Modifier.statusBarsPadding().padding(top = 8.dp)
                        else Modifier
                    ),
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = if (isWideScreen) RoundedCornerShape(HeaderCornerRadius)
                else RoundedCornerShape(bottomStart = HeaderCornerRadius, bottomEnd = HeaderCornerRadius),
                shadowElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .headerStatusBarPadding(isWideScreen)
                        .padding(
                            top = androidx.compose.ui.unit.lerp(
                                if (isWideScreen) 24.dp else 24.dp,
                                if (isWideScreen) 16.dp else 8.dp,
                                headerCollapseFraction
                            ),
                            bottom = androidx.compose.ui.unit.lerp(24.dp, 16.dp, headerCollapseFraction),
                            start = headerHorizontalPadding,
                            end = headerHorizontalPadding
                        ),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = if (isWideScreen) Alignment.Top else Alignment.CenterVertically
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = stringResource(R.string.ui_nastroyki),
                                fontSize = (32f - 5f * headerCollapseFraction).sp,
                                fontFamily = HomeTitleFontFamily,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            AsyncImage(
                                model = R.drawable.settings,
                                contentDescription = stringResource(R.string.ui_ikonka_nastroek),
                                modifier = Modifier.size(30.dp)
                            )
                        }
                        CollapsingHeaderContent(headerCollapseFraction) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.ui_upravlenie_prilozheniem),
                                fontSize = 18.sp,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                            )
                        }
                    }

                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(48.dp)
                            .bounceClick { onAboutClick() }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Outlined.Info,
                                contentDescription = stringResource(R.string.ui_o_prilozhenii),
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                // Отступ слева (contentLeftPadding)
                .padding(
                    top = (innerPadding.calculateTopPadding() - HeaderCornerRadius).coerceAtLeast(0.dp),
                    start = contentLeftPadding,
                    end = 16.dp
                )
                .clipToBounds()
                .verticalScroll(screenScrollState)
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { revealedFolderId = null })
                },
        ) {
            Spacer(modifier = Modifier.height(HeaderCornerRadius + 8.dp))

            // --- Адаптивный вывод блоков настроек ---
            if (isWideScreen) {
                // Планшет / Fold: Два столбца
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    // Левая колонка
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        profileBlock()
                        securityBlock()
                        backupBlock()
                    }
                    // Правая колонка
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        foldersBlock()
                        appearanceBlock()
                    }
                }
            } else {
                // Телефон: Один столбец
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    profileBlock()
                    foldersBlock()
                    securityBlock()
                    backupBlock()
                    appearanceBlock()
                }
            }
            // ---------------------------------------------------

            Spacer(modifier = Modifier.height(160.dp + innerPadding.calculateBottomPadding()))
        }
    }

    // --- ДИАЛОГИ ---
    if (showBackupDialog) {
        var passwordInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showBackupDialog = false },
            title = { Text(stringResource(R.string.ui_parol_arkhiva)) },
            text = {
                Column {
                    Text(stringResource(R.string.ui_pridumayte_parol_on_nuzhen_dlya_shifrovaniya_dan), fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = { passwordInput = it },
                        label = { Text(stringResource(R.string.ui_parol)) },
                        singleLine = true,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (passwordInput.isNotBlank()) {
                        backupPasswordTemp = passwordInput
                        showBackupDialog = false
                        exportLauncher.launch("MyDoc_Backup.zip")
                    }
                }) { Text(stringResource(R.string.ui_dalee)) }
            },
            dismissButton = {
                TextButton(onClick = { showBackupDialog = false }) { Text(stringResource(R.string.ui_otmena)) }
            }
        )
    }

    if (showRestoreDialog) {
        var restorePasswordInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showRestoreDialog = false; selectedRestoreUri = null },
            title = { Text(stringResource(R.string.ui_vosstanovlenie)) },
            text = {
                Column {
                    Text(stringResource(R.string.ui_vvedite_parol_kotoryy_vy_ukazyvali_pri_sozdanii), fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = restorePasswordInput,
                        onValueChange = { restorePasswordInput = it },
                        label = { Text(stringResource(R.string.ui_parol)) },
                        singleLine = true,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (restorePasswordInput.isNotBlank() && selectedRestoreUri != null) {
                        showRestoreDialog = false
                        android.widget.Toast.makeText(context, resources.getString(R.string.ui_vosstanovlenie_2), android.widget.Toast.LENGTH_SHORT).show()
                        viewModel.restoreBackup(context, selectedRestoreUri!!, restorePasswordInput) { success ->
                            if (success) {
                                android.widget.Toast.makeText(context, resources.getString(R.string.ui_dannye_vosstanovleny_uspeshno), android.widget.Toast.LENGTH_LONG).show()
                                kotlin.concurrent.thread { Thread.sleep(1500); Runtime.getRuntime().exit(0) }
                            } else {
                                android.widget.Toast.makeText(context, resources.getString(R.string.ui_oshibka_nevernyy_parol_ili_fayl_povrezhden), android.widget.Toast.LENGTH_LONG).show()
                            }
                            selectedRestoreUri = null
                        }
                    }
                }) { Text(stringResource(R.string.ui_vosstanovit)) }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreDialog = false; selectedRestoreUri = null }) { Text(stringResource(R.string.ui_otmena)) }
            }
        )
    }

    if (showPinSetupDialog) {
        Dialog(
            onDismissRequest = { showPinSetupDialog = false; isPinEnabledState = false },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
                dismissOnBackPress = true
            )
        ) {
            var pinInput by remember { mutableStateOf("") }
            var isConfirmMode by remember { mutableStateOf(false) }
            var firstPin by remember { mutableStateOf("") }
            var isError by remember { mutableStateOf(false) }

            LaunchedEffect(isError) {
                if (isError) { delay(500); pinInput = ""; isConfirmMode = false; isError = false }
            }

            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier
                            .widthIn(max = 400.dp)
                            .fillMaxHeight()
                            .systemBarsPadding()
                            .padding(horizontal = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(modifier = Modifier.weight(0.3f))

                        AsyncImage(
                            model = R.drawable.lock,
                            contentDescription = stringResource(R.string.ui_blokirovka),
                            modifier = Modifier.size(56.dp)
                        )

                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = if (isError) stringResource(R.string.ui_pin_kody_ne_sovpadayut) else if (isConfirmMode) stringResource(R.string.ui_povtorite_pin_kod) else stringResource(R.string.ui_sozdayte_pin_kod),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        PinDots(pinLength = pinInput.length, isError = isError)
                        Spacer(modifier = Modifier.weight(1f))
                        CustomNumpad(
                            isBiometricEnabled = false,
                            onBiometricClick = {},
                            onNumberClick = { digit ->
                                if (pinInput.length < 4 && !isError) {
                                    pinInput += digit
                                    if (pinInput.length == 4) {
                                        if (!isConfirmMode) { firstPin = pinInput; pinInput = ""; isConfirmMode = true } else {
                                            if (pinInput == firstPin) {
                                                prefs.setPin(pinInput); prefs.isPinEnabled = true; isPinEnabledState = true; showPinSetupDialog = false
                                            } else { isError = true }
                                        }
                                    }
                                }
                            },
                            onBackspaceClick = { if (pinInput.isNotEmpty() && !isError) pinInput = pinInput.dropLast(1) }
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        TextButton(onClick = { showPinSetupDialog = false; isPinEnabledState = false }, modifier = Modifier.padding(bottom = 24.dp)) {
                            Text(stringResource(R.string.ui_otmena), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp)
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog || folderToEdit != null) {
        var folderNameInput by remember { mutableStateOf(folderToEdit?.name ?: "") }
        AlertDialog(
            onDismissRequest = { showAddDialog = false; folderToEdit = null },
            title = { Text(if (folderToEdit != null) stringResource(R.string.ui_izmenit_papku) else stringResource(R.string.ui_novaya_papka)) },
            text = {
                OutlinedTextField(
                    value = folderNameInput, onValueChange = { folderNameInput = it }, label = { Text(stringResource(R.string.ui_vvedite_nazvanie_papki)) }, singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (folderNameInput.isNotBlank()) {
                        if (folderToEdit != null) viewModel.updateFolderName(folderToEdit!!, folderNameInput)
                        else viewModel.addFolder(folderNameInput)
                    }
                    showAddDialog = false; folderToEdit = null
                }) { Text(stringResource(R.string.ui_sokhranit)) }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false; folderToEdit = null }) { Text(stringResource(R.string.ui_otmena)) }
            }
        )
    }

    if (folderToDelete != null) {
        AlertDialog(
            onDismissRequest = { folderToDelete = null },
            title = { Text(stringResource(R.string.ui_udalit_papku)) },
            text = { Text(stringResource(R.string.folder_delete_message, folderToDelete?.name.orEmpty())) },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteFolder(folderToDelete!!.id); folderToDelete = null }) { Text(stringResource(R.string.ui_udalit), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { folderToDelete = null }) { Text(stringResource(R.string.ui_otmena)) } }
        )
    }
}

@Composable
fun CustomSwipeToRevealItem(
    folder: FolderEntity,
    shape: RoundedCornerShape,
    isRevealed: Boolean,
    onRevealChange: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val revealWidth = 144.dp
    val density = LocalDensity.current
    val revealWidthPx = with(density) { revealWidth.toPx() }
    val swipeThresholdPx = with(density) { 40.dp.toPx() }
    val animatedOffset = remember(folder.id, revealWidthPx) {
        Animatable(if (isRevealed) -revealWidthPx else 0f)
    }
    val animationScope = rememberCoroutineScope()
    var isDraggingHorizontally by remember { mutableStateOf(false) }
    var dragOffsetPx by remember { mutableFloatStateOf(0f) }
    var dragStartOffsetPx by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(isRevealed, revealWidthPx) {
        if (!isDraggingHorizontally) {
            animatedOffset.animateTo(
                targetValue = if (isRevealed) -revealWidthPx else 0f,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
            )
        }
    }

    Box(modifier = Modifier.fillMaxWidth().height(72.dp).clipToBounds()) {
        Row(
            modifier = Modifier.align(Alignment.CenterEnd).width(revealWidth).fillMaxHeight().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                onClick = onDelete,
                modifier = Modifier.size(64.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.ui_udalit), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
            Surface(
                onClick = onEdit,
                modifier = Modifier.size(64.dp),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.primary
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.ui_izmenit), tint = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = if (isDraggingHorizontally) dragOffsetPx else animatedOffset.value
                }
                .pointerInput(isRevealed, revealWidthPx) {
                    detectHorizontalDragGestures(
                        onDragStart = {
                            dragStartOffsetPx = animatedOffset.value.coerceIn(-revealWidthPx, 0f)
                            dragOffsetPx = dragStartOffsetPx
                            isDraggingHorizontally = true
                        },
                        onDragEnd = {
                            val shouldReveal = when {
                                dragOffsetPx - dragStartOffsetPx < -swipeThresholdPx -> true
                                dragOffsetPx - dragStartOffsetPx > swipeThresholdPx -> false
                                else -> isRevealed
                            }
                            val releaseOffsetPx = dragOffsetPx
                            animationScope.launch {
                                animatedOffset.snapTo(releaseOffsetPx)
                                isDraggingHorizontally = false
                                onRevealChange(shouldReveal)
                                if (shouldReveal == isRevealed) {
                                    animatedOffset.animateTo(
                                        targetValue = if (shouldReveal) -revealWidthPx else 0f,
                                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                                    )
                                }
                            }
                        },
                        onDragCancel = {
                            val releaseOffsetPx = dragOffsetPx
                            animationScope.launch {
                                animatedOffset.snapTo(releaseOffsetPx)
                                isDraggingHorizontally = false
                                animatedOffset.animateTo(
                                    targetValue = if (isRevealed) -revealWidthPx else 0f,
                                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                                )
                            }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            dragOffsetPx = (dragOffsetPx + dragAmount).coerceIn(-revealWidthPx, 0f)
                        }
                    )
                }
                .clickable(enabled = isRevealed) { onRevealChange(false) },
            shape = shape,
            color = MaterialTheme.colorScheme.surface
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(start = 20.dp, end = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = folder.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { onRevealChange(!isRevealed) }) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = stringResource(if (isRevealed) R.string.folder_hide_actions else R.string.folder_show_actions),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
