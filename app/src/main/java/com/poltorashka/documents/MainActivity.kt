package com.poltorashka.documents

import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuOpen
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import bounceClick
import com.poltorashka.documents.data.AppDatabase
import com.poltorashka.documents.ui.theme.DocumentsTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar
import androidx.compose.foundation.lazy.grid.itemsIndexed
import com.poltorashka.documents.data.ReorderScreen
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.ui.text.style.TextOverflow
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.res.stringResource

@androidx.annotation.StringRes
fun getDynamicGreetingRes(): Int {
    val hour = java.time.LocalTime.now().hour
    return when (hour) {
        in 5..11 -> R.string.ui_dobroe_utro
        in 12..16 -> R.string.ui_dobryy_den
        in 17..22 -> R.string.ui_dobryy_vecher
        else -> R.string.ui_dobroy_nochi
    }
}

fun getGreetingIconResId(): Int {
    val calendar = Calendar.getInstance()
    return when (calendar.get(Calendar.HOUR_OF_DAY)) {
        in 6..11 -> R.drawable.wolf1
        in 12..17 -> R.drawable.wolf2
        in 18..22 -> R.drawable.wolf3
        else -> R.drawable.wolf4
    }
}

class MainActivity : FragmentActivity() {
    @OptIn(ExperimentalFoundationApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            ),
            navigationBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            )
        )

        setContent {
            val context = LocalContext.current
            val prefs = remember { UserPreferences(context) }

            DocumentsTheme(
                themeMode = prefs.themeMode,
                dynamicColor = prefs.useDynamicColor
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var isTimeoutLocked by remember { mutableStateOf(false) }
                    var isFirstOnStart by remember { mutableStateOf(true) }
                    val lifecycleOwner = LocalLifecycleOwner.current

                    DisposableEffect(lifecycleOwner) {
                        val observer = LifecycleEventObserver { _, event ->
                            when (event) {
                                Lifecycle.Event.ON_STOP -> {
                                    prefs.backgroundTimestamp = System.currentTimeMillis()
                                }
                                Lifecycle.Event.ON_START -> {
                                    if (!isFirstOnStart) {
                                        val lastTime = prefs.backgroundTimestamp
                                        if (lastTime > 0) {
                                            val timePassed = System.currentTimeMillis() - lastTime
                                            if (timePassed > 120_000 && (prefs.isPinEnabled || prefs.isBiometricEnabled)) {
                                                isTimeoutLocked = true
                                            }
                                        }
                                    }
                                    isFirstOnStart = false
                                }
                                else -> {}
                            }
                        }
                        lifecycleOwner.lifecycle.addObserver(observer)
                        onDispose {
                            lifecycleOwner.lifecycle.removeObserver(observer)
                        }
                    }

                    val db = AppDatabase.getDatabase(context)
                    val viewModel: DocumentsViewModel = viewModel(
                        factory = DocumentsViewModelFactory(db.documentDao(), db.folderDao())
                    )

                    val navController = rememberNavController()
                    val startScreen = if (!prefs.isOnboardingCompleted) "onboarding" else if (prefs.isPinEnabled) "auth" else "main"
                    val tabRoutes = listOf("main")

                    Box(modifier = Modifier.fillMaxSize()) {
                        NavHost(
                            navController = navController,
                            startDestination = startScreen,
                            enterTransition = {
                                if (initialState.destination.route in tabRoutes && targetState.destination.route in tabRoutes) {
                                    fadeIn(animationSpec = tween(300))
                                } else {
                                    slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(400))
                                }
                            },
                            exitTransition = {
                                if (initialState.destination.route in tabRoutes && targetState.destination.route in tabRoutes) {
                                    fadeOut(animationSpec = tween(300))
                                } else {
                                    slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(400))
                                }
                            },
                            popEnterTransition = {
                                slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(400))
                            },
                            popExitTransition = {
                                slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(400))
                            }
                        ) {
                            composable("onboarding") {
                                OnboardingScreen(
                                    onFinish = {
                                        navController.navigate("main") {
                                            popUpTo("onboarding") { inclusive = true }
                                        }
                                    }
                                )
                            }

                            composable("main") {
                                val pagerState = rememberPagerState(pageCount = { 3 })
                                val coroutineScope = rememberCoroutineScope()
                                val configuration = androidx.compose.ui.platform.LocalConfiguration.current
                                val isWideScreen = configuration.screenWidthDp >= 600
                                var isRailExpanded by rememberSaveable { mutableStateOf(false) }
                                val handleAddClick: () -> Unit = {
                                    if (pagerState.currentPage == 0) {
                                        val activeFolderId = viewModel.selectedFolderId.value ?: viewModel.folders.value.firstOrNull()?.id
                                        if (activeFolderId != null) {
                                            navController.navigate("add/$activeFolderId")
                                        }
                                    } else {
                                        navController.navigate("add/0")
                                    }
                                }

                                if (isWideScreen) {
                                    // --- РЕЖИМ ПЛАНШЕТА И FOLD ---
                                    Row(modifier = Modifier.fillMaxSize()) {
                                        CustomSideNavigationRail(
                                            activeTab = pagerState.currentPage,
                                            onHomeClick = { coroutineScope.launch { pagerState.animateScrollToPage(0) } },
                                            onSearchClick = { coroutineScope.launch { pagerState.animateScrollToPage(1) } },
                                            onSettingsClick = { coroutineScope.launch { pagerState.animateScrollToPage(2) } },
                                            onAddClick = handleAddClick,
                                            isExpanded = isRailExpanded,
                                            onExpandedChange = { isRailExpanded = it }
                                        )

                                        HorizontalPager(
                                            state = pagerState,
                                            modifier = Modifier.weight(1f).fillMaxHeight(),
                                            userScrollEnabled = true
                                        ) { page ->
                                            when (page) {
                                                0 -> MainScreen(onDocumentClick = { id -> navController.navigate("detail/$id") }, onAddClick = { folderId -> navController.navigate("add/$folderId") }, onSettingsClick = { coroutineScope.launch { pagerState.animateScrollToPage(2) } }, onSearchClick = { coroutineScope.launch { pagerState.animateScrollToPage(1) } }, onReorderClick = { navController.navigate("reorder") }, viewModel = viewModel, availableWidthDp = configuration.screenWidthDp - (if (isRailExpanded) 256 else 80))
                                                1 -> SearchScreen(onBackClick = { coroutineScope.launch { pagerState.animateScrollToPage(0) } }, onHomeClick = { coroutineScope.launch { pagerState.animateScrollToPage(0) } }, onSettingsClick = { coroutineScope.launch { pagerState.animateScrollToPage(2) } }, onAddClick = { navController.navigate("add/0") }, onDocumentClick = { id -> navController.navigate("detail/$id") }, isWideLayout = true)
                                                2 -> SettingsScreen(onBackClick = { coroutineScope.launch { pagerState.animateScrollToPage(0) } }, onHomeClick = { coroutineScope.launch { pagerState.animateScrollToPage(0) } }, onSearchClick = { coroutineScope.launch { pagerState.animateScrollToPage(1) } }, onAboutClick = { navController.navigate("about") }, isWideLayout = configuration.screenWidthDp - (if (isRailExpanded) 256 else 80) >= 520)
                                            }
                                        }
                                    }
                                } else {
                                    // --- РЕЖИМ СМАРТФОНА (Нижняя панель) ---
                                    Box(modifier = Modifier.fillMaxSize()) {
                                        HorizontalPager(
                                            state = pagerState,
                                            modifier = Modifier.fillMaxSize(),
                                            userScrollEnabled = true
                                        ) { page ->
                                            when (page) {
                                                0 -> MainScreen(
                                                    onDocumentClick = { id -> navController.navigate("detail/$id") },
                                                    onAddClick = { folderId -> navController.navigate("add/$folderId") },
                                                    onSettingsClick = { coroutineScope.launch { pagerState.animateScrollToPage(2) } },
                                                    onSearchClick = { coroutineScope.launch { pagerState.animateScrollToPage(1) } },
                                                    onReorderClick = { navController.navigate("reorder") },
                                                    viewModel = viewModel
                                                )
                                                1 -> SearchScreen(
                                                    onBackClick = { coroutineScope.launch { pagerState.animateScrollToPage(0) } },
                                                    onHomeClick = { coroutineScope.launch { pagerState.animateScrollToPage(0) } },
                                                    onSettingsClick = { coroutineScope.launch { pagerState.animateScrollToPage(2) } },
                                                    onAddClick = { navController.navigate("add/0") },
                                                    onDocumentClick = { id -> navController.navigate("detail/$id") }
                                                )
                                                2 -> SettingsScreen(
                                                    onBackClick = { coroutineScope.launch { pagerState.animateScrollToPage(0) } },
                                                    onHomeClick = { coroutineScope.launch { pagerState.animateScrollToPage(0) } },
                                                    onSearchClick = { coroutineScope.launch { pagerState.animateScrollToPage(1) } },
                                                    onAboutClick = { navController.navigate("about") }
                                                )
                                            }
                                        }

                                        Box(modifier = Modifier.align(Alignment.BottomCenter)) {
                                            CustomFloatingToolbar(
                                                activeTab = pagerState.currentPage,
                                                onHomeClick = { coroutineScope.launch { pagerState.animateScrollToPage(0) } },
                                                onSearchClick = { coroutineScope.launch { pagerState.animateScrollToPage(1) } },
                                                onSettingsClick = { coroutineScope.launch { pagerState.animateScrollToPage(2) } },
                                                onAddClick = handleAddClick
                                            )
                                        }
                                    }
                                }
                            }

                            composable("auth") {
                                AuthScreen(
                                    correctPin = prefs.appPin,
                                    isBiometricEnabled = prefs.isBiometricEnabled,
                                    onSuccess = {
                                        prefs.backgroundTimestamp = System.currentTimeMillis()
                                        navController.navigate("main") { popUpTo("auth") { inclusive = true } }
                                    }
                                )
                            }

                            composable("about") {
                                AboutAppScreen(
                                    onBackClick = { navController.popBackStack() }
                                )
                            }

                            composable("reorder") {
                                ReorderScreen(
                                    onBackClick = { navController.popBackStack() },
                                    viewModel = viewModel
                                )
                            }

                            composable("detail/{id}") { backStackEntry ->
                                val idString = backStackEntry.arguments?.getString("id")
                                val id = idString?.toIntOrNull() ?: 0
                                DocumentDetailScreen(
                                    documentId = id,
                                    onBackClick = { navController.popBackStack() }
                                )
                            }

                            composable("add/{profileId}") { backStackEntry ->
                                val profileIdString = backStackEntry.arguments?.getString("profileId")
                                val profileId = profileIdString?.toIntOrNull() ?: 0
                                AddDocumentScreen(
                                    profileId = profileId,
                                    onBackClick = { navController.popBackStack() },
                                    onSaved = { navController.popBackStack() }
                                )
                            }
                        }

                        if (isTimeoutLocked) {
                            Surface(
                                modifier = Modifier.fillMaxSize(),
                                color = MaterialTheme.colorScheme.background
                            ) {
                                AuthScreen(
                                    correctPin = prefs.appPin,
                                    isBiometricEnabled = prefs.isBiometricEnabled,
                                    onSuccess = {
                                        isTimeoutLocked = false
                                        prefs.backgroundTimestamp = System.currentTimeMillis()
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MainScreen(
    onDocumentClick: (Int) -> Unit,
    onAddClick: (Int) -> Unit,
    onSettingsClick: () -> Unit,
    onSearchClick: () -> Unit,
    onReorderClick: () -> Unit,
    viewModel: DocumentsViewModel,
    availableWidthDp: Int? = null
) {
    val folders by viewModel.folders.collectAsState()
    val manualSelectedId by viewModel.selectedFolderId.collectAsState()
    val docs by viewModel.documents.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    val activeFolderId = manualSelectedId ?: folders.firstOrNull()?.id
    val folderSelectionPulse = remember { Animatable(0f) }
    var pulsingFolderId by remember { mutableStateOf<Int?>(null) }
    val selectionAnimationScope = rememberCoroutineScope()
    val context = LocalContext.current
    val prefs = remember { UserPreferences(context) }
    val userName = prefs.userName
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isWideLayout = availableWidthDp != null

    // Вычисление отступов для планшета
    val headerHorizontalPadding = if (isWideLayout) 16.dp else 24.dp
    val contentLeftPadding = 16.dp
    val minFolderWidth = 112.dp
    val minFolderCompressionWidth = 96.dp
    val maxFolderWidth = (
        (availableWidthDp ?: configuration.screenWidthDp).dp -
            headerHorizontalPadding - headerHorizontalPadding -
            if (isWideLayout) 32.dp else 0.dp
        )
        .coerceAtMost(360.dp)
        .coerceAtLeast(240.dp)
    val folderHorizontalPadding = 24.dp
    val folderLabelStyle = MaterialTheme.typography.labelLarge
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val folderWidths = remember(
        folders,
        folderLabelStyle,
        density,
        textMeasurer,
        maxFolderWidth,
        minFolderWidth,
        folderHorizontalPadding
    ) {
        folders.map { folder ->
            val textWidthPx = textMeasurer.measure(
                text = folder.name,
                style = folderLabelStyle,
                softWrap = false,
                maxLines = 1
            ).size.width
            with(density) {
                (textWidthPx.toDp() + folderHorizontalPadding * 2)
                    .coerceIn(minFolderWidth, maxFolderWidth)
            }
        }
    }
    val activeFolderIndex = folders.indexOfFirst { it.id == activeFolderId }
    val folderSelectionExpansion = remember(folderWidths, activeFolderIndex, maxFolderWidth) {
        if (activeFolderIndex in folderWidths.indices) {
            val neighbors = listOf(activeFolderIndex - 1, activeFolderIndex + 1)
                .filter { it in folderWidths.indices }
            val neighborCompressionCapacity = neighbors.sumOf { index ->
                (folderWidths[index] - minFolderCompressionWidth).value.coerceAtLeast(0f).toDouble()
            }.toFloat()
            minOf(
                24.dp.value,
                folderWidths[activeFolderIndex].value * ButtonGroupDefaults.ExpandedRatio,
                (maxFolderWidth - folderWidths[activeFolderIndex]).value.coerceAtLeast(0f),
                neighborCompressionCapacity
            ).dp
        } else {
            0.dp
        }
    }
    val folderCompressionAmounts = remember(folderWidths, activeFolderIndex, folderSelectionExpansion) {
        val amounts = MutableList(folders.size) { 0.dp }
        if (activeFolderIndex in folderWidths.indices) {
            val neighbors = listOf(activeFolderIndex - 1, activeFolderIndex + 1)
                .filter { it in folderWidths.indices }
            val capacityTotal = neighbors.sumOf { index ->
                (folderWidths[index] - minFolderCompressionWidth).value.coerceAtLeast(0f).toDouble()
            }.toFloat()
            if (capacityTotal > 0f) {
                neighbors.forEach { index ->
                    val capacity = (folderWidths[index] - minFolderCompressionWidth).value.coerceAtLeast(0f)
                    amounts[index] = folderSelectionExpansion * (capacity / capacityTotal)
                }
            }
        }
        amounts
    }
    val gridState = rememberLazyGridState()
    val collapseThresholdPx = with(LocalDensity.current) { 36.dp.toPx() }
    val shouldCollapseHeader by remember(gridState, collapseThresholdPx) {
        derivedStateOf {
            gridState.firstVisibleItemIndex > 0 ||
                gridState.firstVisibleItemScrollOffset > collapseThresholdPx
        }
    }
    val headerCollapseFraction by animateFloatAsState(
        targetValue = if (shouldCollapseHeader) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "headerCollapse"
    )

    LaunchedEffect(activeFolderId) {
        gridState.scrollToItem(0)
    }

    Scaffold(
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
                shape = if (isWideLayout) RoundedCornerShape(32.dp)
                else RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp),
                shadowElevation = 2.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            top = androidx.compose.ui.unit.lerp(
                                if (isWideLayout) 24.dp else 80.dp,
                                if (isWideLayout) 16.dp else 32.dp,
                                headerCollapseFraction
                            ),
                            bottom = androidx.compose.ui.unit.lerp(24.dp, 16.dp, headerCollapseFraction)
                        )
                ) {
                    // Заголовок и кнопка на одной линии
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = headerHorizontalPadding),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.zIndex(1f)) {
                            Text(
                                text = stringResource(R.string.ui_moi_dokumenty),
                                fontSize = (32f - 5f * headerCollapseFraction).sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            AnimatedVisibility(
                                visible = headerCollapseFraction < 0.55f,
                                enter = fadeIn() + expandVertically(),
                                exit = fadeOut() + shrinkVertically()
                            ) {
                                Column {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = if (userName.isNotEmpty()) {
                                                stringResource(R.string.greeting_with_name, stringResource(getDynamicGreetingRes()), userName)
                                            } else {
                                                stringResource(R.string.greeting_without_name, stringResource(getDynamicGreetingRes()))
                                            },
                                            fontSize = 18.sp,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        WolfMascotWithBubble()
                                    }
                                }
                            }
                        }

                        // КНОПКА "ИЗМЕНИТЬ ПОРЯДОК"
                        if (!isLoading && folders.isNotEmpty() && docs.isNotEmpty()) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .size(48.dp)
                                    .bounceClick { onReorderClick() }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Filled.Edit,
                                        contentDescription = stringResource(R.string.ui_izmenit_poryadok_2),
                                        tint = MaterialTheme.colorScheme.onPrimary
                                    )
                                }
                            }
                        }
                    }

                    Spacer(
                        modifier = Modifier.height(
                            androidx.compose.ui.unit.lerp(24.dp, 12.dp, headerCollapseFraction)
                        )
                    )

                    if (!isLoading && folders.isNotEmpty()) {
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = headerHorizontalPadding),
                            horizontalArrangement = Arrangement.spacedBy(
                                ButtonGroupDefaults.ConnectedSpaceBetween
                            )
                        ) {
                            itemsIndexed(folders, key = { _, folder -> folder.id }) { index, folder ->
                                StaggeredSpringAnimation(index = index) {
                                    val selected = activeFolderId == folder.id
                                    val selectedWidth by animateDpAsState(
                                        targetValue = folderWidths[index] +
                                            (if (selected) folderSelectionExpansion else 0.dp) -
                                            folderCompressionAmounts[index],
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioLowBouncy,
                                            stiffness = Spring.StiffnessMediumLow
                                        ),
                                        label = "selectedFolderWidth"
                                    )
                                    val pulse = if (pulsingFolderId == activeFolderId) {
                                        folderSelectionPulse.value
                                    } else {
                                        0f
                                    }
                                    val animatedWidth = (
                                        selectedWidth - folderCompressionAmounts[index] * 0.25f * pulse
                                    ).coerceAtLeast(minFolderCompressionWidth)
                                    val shapes = when {
                                        folders.size == 1 -> ToggleButtonShapes(
                                            shape = RoundedCornerShape(24.dp),
                                            pressedShape = RoundedCornerShape(12.dp),
                                            checkedShape = RoundedCornerShape(24.dp)
                                        )
                                        index == 0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                        index == folders.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                                    }

                                    ToggleButton(
                                        checked = selected,
                                        onCheckedChange = { isChecked ->
                                            if (isChecked) {
                                                viewModel.selectFolder(folder.id)
                                                pulsingFolderId = folder.id
                                                selectionAnimationScope.launch {
                                                    folderSelectionPulse.snapTo(0f)
                                                    folderSelectionPulse.animateTo(
                                                        targetValue = 1f,
                                                        animationSpec = spring(
                                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                                            stiffness = Spring.StiffnessMedium
                                                        )
                                                    )
                                                    folderSelectionPulse.animateTo(
                                                        targetValue = 0f,
                                                        animationSpec = spring(
                                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                                            stiffness = Spring.StiffnessMedium
                                                        )
                                                    )
                                                    if (pulsingFolderId == folder.id) {
                                                        pulsingFolderId = null
                                                    }
                                                }
                                            }
                                        },
                                        shapes = shapes,
                                        colors = ToggleButtonDefaults.colors(
                                            containerColor = MaterialTheme.colorScheme.surface,
                                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                            checkedContainerColor = MaterialTheme.colorScheme.primary,
                                            checkedContentColor = MaterialTheme.colorScheme.onPrimary
                                        ),
                                        elevation = null,
                                        modifier = Modifier
                                            .width(animatedWidth)
                                            .heightIn(min = 48.dp),
                                        contentPadding = PaddingValues(
                                            horizontal = folderHorizontalPadding,
                                            vertical = 12.dp
                                        )
                                    ) {
                                        Text(
                                            text = folder.name,
                                            style = folderLabelStyle,
                                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.widthIn(
                                                max = maxFolderWidth - folderHorizontalPadding * 2
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = contentLeftPadding, end = 16.dp)
        ) {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize().padding(bottom = innerPadding.calculateBottomPadding()), contentAlignment = Alignment.Center) {
                    Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), modifier = Modifier.padding(32.dp)) {
                        Column(modifier = Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp), strokeWidth = 4.dp, strokeCap = StrokeCap.Round)
                            Spacer(modifier = Modifier.height(24.dp))
                            Text(stringResource(R.string.ui_otkryvaem_seyf), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            } else if (folders.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = innerPadding.calculateBottomPadding()),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        modifier = Modifier
                            // ДОБАВЛЕНО: Ограничение ширины, чтобы на Fold карточка не растягивалась
                            .widthIn(max = 400.dp)
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        shape = RoundedCornerShape(28.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // ДОБАВЛЕНО: Изображение спящего волка
                            // ДОБАВЛЕНО: Умное масштабирование без растягивания карточки
                            Image(
                                painter = painterResource(id = R.drawable.sleep_wolf),
                                contentDescription = stringResource(R.string.ui_net_papok),
                                modifier = Modifier
                                    .width(250.dp)
                                    .wrapContentHeight(),
                                contentScale = ContentScale.FillWidth
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = stringResource(R.string.ui_pered_dobavleniem_pervogo_dokumenta_neobkhodimo),
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 15.sp,
                                lineHeight = 22.sp,
                                fontWeight = FontWeight.Medium
                            )

                            Spacer(modifier = Modifier.height(24.dp))

                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .height(44.dp)
                                    .bounceClick { onSettingsClick() }
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(horizontal = 24.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.ui_otkryt_nastroyki),
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                }
                            }
                        }
                    }
                }
            } else if (docs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = innerPadding.calculateBottomPadding()),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.sleep_wolf),
                            contentDescription = stringResource(R.string.ui_papka_pusta),
                            modifier = Modifier
                                .width(250.dp) // Размер спящего волка
                                .wrapContentHeight(),
                            contentScale = ContentScale.FillWidth
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = stringResource(R.string.ui_v_etoy_papke_poka_net_dokumentov),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 16.sp
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Adaptive(minSize = 170.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(
                        top = innerPadding.calculateTopPadding() + 16.dp,
                        bottom = 120.dp
                    )
                ) {
                    itemsIndexed(docs, key = { _, doc -> doc.id }) { index, doc ->
                        StaggeredSpringAnimation(index = index) {
                            DocumentCard(document = doc, onClick = { onDocumentClick(doc.id) })
                        }
                    }
                } // <-- Скобка выровнена
            }
        }
    }
}
// -- Логика невероятных АУФ фраз --
@Composable
fun WolfMascotWithBubble() {
    val wolfPhrases = listOf(
        stringResource(R.string.ui_vashi_dokumenty_pod_moey_nadezhnoy_lapoy),
        stringResource(R.string.ui_splyu_v_polglaza_okhranyayu_vashi_skany),
        stringResource(R.string.ui_ni_odin_chuzhoy_nos_syuda_ne_sunetsya_auuu),
        stringResource(R.string.ui_derzhu_ushki_na_makushke),
        stringResource(R.string.ui_seyf_zapert_klyuch_ya_pozhaluy_zakopayu),
        stringResource(R.string.ui_r_r_r_rabotaet_jetpack_security),
        stringResource(R.string.ui_kto_khoroshiy_malchik_ya_khoroshiy_malchik),
        stringResource(R.string.ui_privet_kak_dela_derzhi_khvost_pistoletom),
        stringResource(R.string.ui_paroli_zashifrovany_khvost_pistoletom)
    )

    var showSpeechBubble by remember { mutableStateOf(false) }
    var currentPhrase by remember { mutableStateOf("") }

    LaunchedEffect(showSpeechBubble) {
        if (showSpeechBubble) {
            delay(3500)
            showSpeechBubble = false
        }
    }

    var isAnimated by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { isAnimated = true }

    val scale by animateFloatAsState(
        targetValue = if (isAnimated) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "wolfScale"
    )

    val alpha by animateFloatAsState(
        targetValue = if (isAnimated) 1f else 0f,
        animationSpec = tween(durationMillis = 600),
        label = "wolfAlpha"
    )

    Box(
        modifier = Modifier.size(35.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Image(
            painter = painterResource(id = getGreetingIconResId()),
            contentDescription = stringResource(R.string.ui_maskot),
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    alpha = alpha,
                    transformOrigin = TransformOrigin.Center
                )
                .bounceClick {
                    currentPhrase = wolfPhrases.random()
                    showSpeechBubble = true
                }
        )

        AnimatedVisibility(
            visible = showSpeechBubble,
            enter = fadeIn(animationSpec = tween(400)),
            exit = fadeOut(animationSpec = tween(400)),
            modifier = Modifier
                .wrapContentSize(unbounded = true)
                .offset(y = (-38).dp, x = (-10).dp)
                .widthIn(max = 220.dp)
                .zIndex(10f)
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primary,
                shadowElevation = 6.dp
            ) {
                Text(
                    text = currentPhrase,
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    lineHeight = 16.sp
                )
            }
        }
    }
}

@Composable
fun DocumentCard(
    document: com.poltorashka.documents.data.DocumentEntity,
    onClick: () -> Unit
) {
    val resources = LocalResources.current
    val otherDocumentType = stringResource(R.string.document_type_other)
    val rawTitle = if (document.documentType == "Другое") {
        document.fieldsData["Название документа"]?.ifBlank { otherDocumentType }
            ?: otherDocumentType
    } else {
        com.poltorashka.documents.data.DocumentTemplates.documentTypeLabel(document.documentType, resources)
    }
    val displayTitle = rawTitle.replace(" о ", " о\u00A0")

    // Достаем эмодзи-тег
    val tagEmoji = document.fieldsData[com.poltorashka.documents.data.DocumentTemplates.tagFieldKey]

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .clip(RoundedCornerShape(24.dp))
            .bounceClick { onClick() },
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Image(
                painter = painterResource(id = R.drawable.pattern_guilloche),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(0.1f),
                contentScale = ContentScale.Crop
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.Start
            ) {
                if (!tagEmoji.isNullOrEmpty()) {
                    Surface(
                        shape = ScallopShape(petals = 9, depth = 0.12f),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(text = tagEmoji, fontSize = 16.sp)
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.size(36.dp))
                }

                Text(
                    text = displayTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Start,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun PinDots(pinLength: Int, isError: Boolean) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 16.dp)
    ) {
        repeat(4) { index ->
            val isFilled = index < pinLength
            val color = if (isError) MaterialTheme.colorScheme.error
            else if (isFilled) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.surfaceVariant

            Box(
                modifier = Modifier
                    .size(20.dp)
                    .background(color, CircleShape)
            )
        }
    }
}

@Composable
fun CustomNumpad(
    isBiometricEnabled: Boolean,
    onBiometricClick: () -> Unit,
    onNumberClick: (String) -> Unit,
    onBackspaceClick: () -> Unit
) {
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("bio", "0", "⌫")
    )

    val buttonSize = 84.dp

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        rows.forEach { row ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                row.forEach { key ->
                    if (key == "bio") {
                        if (isBiometricEnabled) {
                            Surface(
                                shape = CircleShape,
                                color = Color.Transparent,
                                modifier = Modifier
                                    .size(buttonSize)
                                    .bounceClick { onBiometricClick() }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_fingerprint),
                                        contentDescription = stringResource(R.string.ui_biometriya),
                                        modifier = Modifier.size(50.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        } else {
                            Spacer(modifier = Modifier.size(buttonSize))
                        }
                    } else if (key == "⌫") {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .size(buttonSize)
                                .bounceClick { onBackspaceClick() }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.ui_steret), modifier = Modifier.size(32.dp))
                            }
                        }
                    } else {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier
                                .size(buttonSize)
                                .bounceClick { onNumberClick(key) }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = key,
                                    fontSize = 36.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AuthScreen(correctPin: String, isBiometricEnabled: Boolean, onSuccess: () -> Unit) {
    var pinInput by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val activity = context as? FragmentActivity

    LaunchedEffect(Unit) {
        if (isBiometricEnabled && activity != null) {
            delay(300)
            showBiometricPrompt(activity, onSuccess)
        }
    }

    LaunchedEffect(isError) {
        if (isError) {
            delay(500)
            pinInput = ""
            isError = false
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        // ДОБАВЛЕНО: Обертка Box для центрирования на широких экранах
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    // ИЗМЕНЕНО: Ограничение максимальной ширины.
                    // На смартфонах займет всю ширину, на Fold - не разъедется!
                    .widthIn(max = 400.dp)
                    .fillMaxHeight()
                    .systemBarsPadding()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.weight(0.3f))

                Image(
                    painter = painterResource(id = R.drawable.lock),
                    contentDescription = stringResource(R.string.ui_blokirovka),
                    modifier = Modifier.size(56.dp)
                )
                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = if (isError) stringResource(R.string.ui_nevernyy_pin_kod) else stringResource(R.string.ui_vvedite_pin_kod),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(16.dp))
                PinDots(pinLength = pinInput.length, isError = isError)

                Spacer(modifier = Modifier.weight(1f))

                CustomNumpad(
                    isBiometricEnabled = isBiometricEnabled,
                    onBiometricClick = { if (activity != null) showBiometricPrompt(activity, onSuccess) },
                    onNumberClick = { digit ->
                        if (pinInput.length < 4 && !isError) {
                            pinInput += digit
                            if (pinInput.length == 4) {
                                if (pinInput == correctPin) onSuccess()
                                else isError = true
                            }
                        }
                    },
                    onBackspaceClick = { if (pinInput.isNotEmpty() && !isError) pinInput = pinInput.dropLast(1) }
                )

                Spacer(modifier = Modifier.height(48.dp))
            }
        }
    }
}

fun showBiometricPrompt(activity: FragmentActivity, onSuccess: () -> Unit) {
    val executor = ContextCompat.getMainExecutor(activity)
    val biometricPrompt = BiometricPrompt(
        activity,
        executor,
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                onSuccess()
            }
        }
    )

    val promptInfo = BiometricPrompt.PromptInfo.Builder()
        .setTitle(activity.getString(R.string.ui_vkhod_v_dokumenty))
        .setSubtitle(activity.getString(R.string.ui_prilozhite_palets_ili_posmotrite_na_ekran))
        .setNegativeButtonText(activity.getString(R.string.ui_ispolzovat_pin_kod))
        .build()

    biometricPrompt.authenticate(promptInfo)
}

@Composable
fun StaggeredSpringAnimation(
    index: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {

    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { UserPreferences(context) }

    if (!prefs.animationsEnabled) {
        Box(modifier = modifier) {
            content()
        }
        return
    }
    var isVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(index * 40L)
        isVisible = true
    }

    val scale by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0.8f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "scale_animation"
    )

    val alpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 300),
        label = "alpha_animation"
    )

    Box(
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
            this.alpha = alpha
        }
    ) {
        content()
    }
}

// -- Навигационные панели --

@Composable
fun CustomFloatingToolbar(
    activeTab: Int = 0,
    onHomeClick: () -> Unit,
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onAddClick: () -> Unit
) {
    val scale = 1.10f
    val panelHeight = 64.dp * scale
    val plusButtonSize = 64.dp * scale
    val plusIconSize = 28.dp * scale
    val gapBetweenIslands = 12.dp * scale
    val innerPadding = 8.dp * scale

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(gapBetweenIslands, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // --- Основная панель навигации (Капсула) ---
        Surface(
            modifier = Modifier.height(panelHeight),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant,
            shadowElevation = 4.dp
        ) {
            Row(
                // Внутренние отступы капсулы
                modifier = Modifier.padding(horizontal = innerPadding, vertical = innerPadding),
                horizontalArrangement = Arrangement.spacedBy(4.dp * scale),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ToolbarNavItem(isSelected = activeTab == 0, icon = Icons.Filled.Home, label = stringResource(R.string.ui_glavnaya), onClick = onHomeClick, scale = scale)
                ToolbarNavItem(isSelected = activeTab == 1, icon = Icons.Filled.Search, label = stringResource(R.string.ui_poisk), onClick = onSearchClick, scale = scale)
                ToolbarNavItem(isSelected = activeTab == 2, icon = Icons.Filled.Settings, label = stringResource(R.string.ui_nastroyki), onClick = onSettingsClick, scale = scale)
            }
        }

        // --- Кнопка FAB в форме суперэллипса (Squircle) ---
        // Это характерная черта стиля Material Expressive
        Surface(
            shape = RoundedCornerShape(16.dp * scale),
            color = MaterialTheme.colorScheme.primaryContainer,
            shadowElevation = 4.dp,
            modifier = Modifier
                .size(plusButtonSize)
                .bounceClick(onAddClick)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = stringResource(R.string.ui_dobavit_2),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(plusIconSize)
                )
            }
        }
    }
}

// --- Компонент вкладки для Смартфона (Раскрывается по горизонтали) ---
@Composable
fun ToolbarNavItem(isSelected: Boolean, icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit, scale: Float) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current

    // Плавная смена цветов
    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
        label = "nav_bg"
    )
    val contentColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "nav_content"
    )

    Row(
        modifier = Modifier
            .height(48.dp * scale)
            .clip(CircleShape)
            .background(backgroundColor)
            .bounceClick {
                if (!isSelected) {
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                }
                onClick()
            }
            // Динамические отступы: больше места, если вкладка открыта
            .padding(horizontal = if (isSelected) (16.dp * scale) else (12.dp * scale))
            // МАГИЯ: Автоматическая пружинная анимация изменения ширины
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessLow
                )
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = contentColor,
            modifier = Modifier.size(24.dp * scale)
        )

        // Плавно показывает текст только у активной вкладки
        AnimatedVisibility(visible = isSelected) {
            Row {
                Spacer(modifier = Modifier.width(8.dp * scale))
                Text(
                    text = label,
                    color = contentColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }
    }
}

// --- Панель для Планшетов и Fold ---
@Composable
fun CustomSideNavigationRail(
    activeTab: Int = 0,
    onHomeClick: () -> Unit,
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onAddClick: () -> Unit,
    isExpanded: Boolean,
    onExpandedChange: (Boolean) -> Unit
) {
    val railWidth by animateDpAsState(
        targetValue = if (isExpanded) 256.dp else 80.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "navigationRailWidth"
    )
    val railHorizontalPadding = if (isExpanded) 20.dp else 8.dp
    val addToDestinationsSpacing = 32.dp

    Surface(
        modifier = Modifier
            .fillMaxHeight()
            .width(railWidth),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = railHorizontalPadding),
            horizontalAlignment = if (isExpanded) Alignment.Start else Alignment.CenterHorizontally
        ) {
            IconButton(
                onClick = { onExpandedChange(!isExpanded) },
                modifier = Modifier
                    .align(if (isExpanded) Alignment.Start else Alignment.CenterHorizontally)
                    .padding(top = 8.dp)
                    .size(48.dp)
            ) {
                Icon(
                    imageVector = if (isExpanded) Icons.AutoMirrored.Filled.MenuOpen else Icons.Filled.Menu,
                    contentDescription = stringResource(
                        if (isExpanded) R.string.navigation_rail_collapse
                        else R.string.navigation_rail_expand
                    ),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Surface(
                modifier = (if (isExpanded) {
                    Modifier.fillMaxWidth().height(56.dp)
                } else {
                    Modifier.size(56.dp)
                }).bounceClick(onAddClick),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                if (isExpanded) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = stringResource(R.string.ui_dobavit_2),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = stringResource(R.string.ui_dobavit_2),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                } else {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = stringResource(R.string.ui_dobavit_2),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(addToDestinationsSpacing))

            Column(
                modifier = Modifier
                    .fillMaxWidth(),
                horizontalAlignment = if (isExpanded) Alignment.Start else Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                RailNavItem(
                    isSelected = activeTab == 0,
                    expanded = isExpanded,
                    icon = Icons.Filled.Home,
                    label = stringResource(R.string.ui_glavnaya),
                    onClick = onHomeClick
                )
                RailNavItem(
                    isSelected = activeTab == 1,
                    expanded = isExpanded,
                    icon = Icons.Filled.Search,
                    label = stringResource(R.string.ui_poisk),
                    onClick = onSearchClick
                )
                RailNavItem(
                    isSelected = activeTab == 2,
                    expanded = isExpanded,
                    icon = Icons.Filled.Settings,
                    label = stringResource(R.string.ui_nastroyki),
                    onClick = onSettingsClick
                )
            }
        }
    }
}

// --- Элемент навигации планшета и Fold ---
@Composable
fun RailNavItem(
    isSelected: Boolean,
    expanded: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        label = "rail_bg"
    )
    val contentColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "rail_content"
    )
    val labelColor = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
    val handleClick = {
        if (!isSelected) {
            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
        }
        onClick()
    }

    if (expanded) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(backgroundColor)
                .bounceClick(handleClick)
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = label,
                color = if (isSelected) contentColor else labelColor,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1
            )
        }
    } else {
        Column(
            modifier = Modifier
                .width(64.dp)
                .bounceClick(handleClick),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(width = 56.dp, height = 40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(backgroundColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = contentColor,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                color = labelColor,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
