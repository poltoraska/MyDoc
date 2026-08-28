package com.poltorashka.documents

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
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
import androidx.compose.runtime.toMutableStateList
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import com.poltorashka.documents.data.ReorderScreen
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.cos
import kotlin.math.sin

fun getDynamicGreeting(): String {
    val hour = java.time.LocalTime.now().hour
    return when (hour) {
        in 5..11 -> "Доброе утро,"
        in 12..16 -> "Добрый день,"
        in 17..22 -> "Добрый вечер,"
        else -> "Доброй ночи,"
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
                                    // Box вместо Row, чтобы меню висело поверх контента
                                    Box(modifier = Modifier.fillMaxSize()) {

                                        // 1. Экраны теперь занимают всю ширину, шапки растянутся до левого края
                                        HorizontalPager(
                                            state = pagerState,
                                            modifier = Modifier.fillMaxSize(),
                                            userScrollEnabled = true
                                        ) { page ->
                                            when (page) {
                                                0 -> MainScreen(onDocumentClick = { id -> navController.navigate("detail/$id") }, onAddClick = { folderId -> navController.navigate("add/$folderId") }, onSettingsClick = { coroutineScope.launch { pagerState.animateScrollToPage(2) } }, onSearchClick = { coroutineScope.launch { pagerState.animateScrollToPage(1) } }, onReorderClick = { navController.navigate("reorder") }, viewModel = viewModel)
                                                1 -> SearchScreen(onBackClick = { coroutineScope.launch { pagerState.animateScrollToPage(0) } }, onHomeClick = { coroutineScope.launch { pagerState.animateScrollToPage(0) } }, onSettingsClick = { coroutineScope.launch { pagerState.animateScrollToPage(2) } }, onAddClick = { navController.navigate("add/0") }, onDocumentClick = { id -> navController.navigate("detail/$id") })
                                                2 -> SettingsScreen(onBackClick = { coroutineScope.launch { pagerState.animateScrollToPage(0) } }, onHomeClick = { coroutineScope.launch { pagerState.animateScrollToPage(0) } }, onSearchClick = { coroutineScope.launch { pagerState.animateScrollToPage(1) } }, onAboutClick = { navController.navigate("about") })
                                            }
                                        }

                                        // 2. Островок меню плавает слева поверх экрана
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.CenterStart)
                                                .fillMaxHeight()
                                                .width(100.dp)
                                        ) {
                                            CustomSideNavigationRail(
                                                activeTab = pagerState.currentPage,
                                                onHomeClick = { coroutineScope.launch { pagerState.animateScrollToPage(0) } },
                                                onSearchClick = { coroutineScope.launch { pagerState.animateScrollToPage(1) } },
                                                onSettingsClick = { coroutineScope.launch { pagerState.animateScrollToPage(2) } },
                                                onAddClick = handleAddClick
                                            )
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onDocumentClick: (Int) -> Unit,
    onAddClick: (Int) -> Unit,
    onSettingsClick: () -> Unit,
    onSearchClick: () -> Unit,
    onReorderClick: () -> Unit,
    viewModel: DocumentsViewModel
) {
    val folders by viewModel.folders.collectAsState()
    val manualSelectedId by viewModel.selectedFolderId.collectAsState()
    val docs by viewModel.documents.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    val activeFolderId = manualSelectedId ?: folders.firstOrNull()?.id
    val context = LocalContext.current
    val prefs = remember { UserPreferences(context) }
    val userName = prefs.userName
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isWideScreen = configuration.screenWidthDp >= 600

    // Вычисление отступов для планшета
    val headerLeftPadding = if (isWideScreen) 100.dp else 24.dp
    val contentLeftPadding = if (isWideScreen) 100.dp else 16.dp

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp),
                shadowElevation = 2.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 80.dp, bottom = 24.dp)
                ) {
                    // Заголовок и кнопка на одной линии
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = headerLeftPadding, end = 24.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.zIndex(1f)) {
                            Text(
                                text = "Мои документы",
                                fontSize = 32.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "${getDynamicGreeting()} ${if (userName.isNotEmpty()) "$userName!" else "!"}",
                                    fontSize = 18.sp,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                WolfMascotWithBubble()
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
                                        contentDescription = "Изменить порядок",
                                        tint = MaterialTheme.colorScheme.onPrimary
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    if (!isLoading && folders.isNotEmpty()) {
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(start = headerLeftPadding, end = 24.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(folders) { folder ->
                                val isSelected = activeFolderId == folder.id
                                val containerColor by animateColorAsState(targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface, label = "color")
                                val contentColor by animateColorAsState(targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface, label = "color")

                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = containerColor,
                                    modifier = Modifier.height(48.dp).bounceClick { viewModel.selectFolder(folder.id) }
                                ) {
                                    Box(modifier = Modifier.padding(horizontal = 24.dp), contentAlignment = Alignment.Center) {
                                        Text(text = folder.name, color = contentColor, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium)
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
                            Text("Открываем сейф...", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            } else if (folders.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().padding(bottom = innerPadding.calculateBottomPadding()), contentAlignment = Alignment.Center) {
                    Surface(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)) {
                        Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Перед добавлением первого документа необходимо создать папку в настройках приложения.", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium)
                            Spacer(modifier = Modifier.height(24.dp))
                            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, modifier = Modifier.height(44.dp).bounceClick { onSettingsClick() }) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 24.dp)) {
                                    Text("Открыть настройки", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                                }
                            }
                        }
                    }
                }
            } else if (docs.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().padding(bottom = innerPadding.calculateBottomPadding()), contentAlignment = Alignment.Center) {
                    Text("В этой папке пока нет документов", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 170.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(
                        top = innerPadding.calculateTopPadding() + 16.dp,
                        bottom = 120.dp
                    )
                ) {
                    gridItems(docs, key = { it.id }) { doc ->
                        DocumentCard(document = doc, onClick = { onDocumentClick(doc.id) })
                    }
                }
            }
        }
    }
}

// -- Логика невероятных АУФ фраз --
@Composable
fun WolfMascotWithBubble() {
    val wolfPhrases = listOf(
        "Ваши документы под\u00A0моей надежной лапой!",
        "Сплю в\u00A0полглаза, охраняю ваши сканы.",
        "Крепко держу папки! Подушечки лап строго вниз, чтобы ничего не выпало.",
        "Ни\u00A0один чужой нос сюда не\u00A0сунется! Аууу!",
        "Держу ушки на макушке!",
        "Аптечка первой помощи собрана, бэкап сделан. Мы готовы ко всему!",
        "Сейф заперт. Ключ я, пожалуй, закопаю.",
        "Р-р-р... Работает Jetpack Security!",
        "Кто хороший мальчик? Я\u00A0хороший мальчик!",
        "Привет, как дела? Держи хвост пистолетом!",
        "Пароли зашифрованы, хвост пистолетом!"
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
            contentDescription = "Маскот",
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
    val rawTitle = if (document.documentType == "Другое") {
        document.fieldsData["Название документа"]?.ifBlank { "Другое" } ?: "Другое"
    } else {
        document.documentType
    }
    val displayTitle = rawTitle.replace(" о ", " о\u00A0")

    // Достаем эмодзи-тег
    val tagEmoji = document.fieldsData["Тег"]

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
                    .alpha(0.2f),
                contentScale = ContentScale.Crop
            )

            Text(
                text = displayTitle,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 16.dp),
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )

            // --- НОВЫЙ БЛОК: Отрисовка тега-печеньки ---
            if (!tagEmoji.isNullOrEmpty()) {
                Surface(
                    shape = ScallopShape(petals = 9, depth = 0.12f),
                    color = MaterialTheme.colorScheme.primary, // Фирменный акцентный цвет
                    modifier = Modifier
                        .padding(12.dp)
                        .size(36.dp)
                        .align(Alignment.TopStart)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(text = tagEmoji, fontSize = 16.sp)
                    }
                }
            }
        }
    }
}

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
    val iconSpacing = 4.dp * scale
    val itemSize = 48.dp * scale

    val indicatorOffset by animateDpAsState(
        targetValue = innerPadding + (activeTab * (itemSize.value + iconSpacing.value)).dp,
        animationSpec = spring(
            stiffness = Spring.StiffnessMediumLow,
            dampingRatio = Spring.DampingRatioLowBouncy
        ),
        label = "indicatorOffset"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(gapBetweenIslands, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.height(panelHeight),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant,
            shadowElevation = 4.dp
        ) {
            Box(contentAlignment = Alignment.CenterStart) {
                Box(
                    modifier = Modifier
                        .offset(x = indicatorOffset)
                        .size(itemSize)
                        .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)
                )

                Row(
                    modifier = Modifier.padding(horizontal = innerPadding),
                    horizontalArrangement = Arrangement.spacedBy(iconSpacing),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ToolbarNavItem(
                        isSelected = activeTab == 0,
                        icon = Icons.Filled.Home,
                        label = "Главная",
                        onClick = onHomeClick,
                        scale = scale
                    )
                    ToolbarNavItem(
                        isSelected = activeTab == 1,
                        icon = Icons.Filled.Search,
                        label = "Поиск",
                        onClick = onSearchClick,
                        scale = scale
                    )
                    ToolbarNavItem(
                        isSelected = activeTab == 2,
                        icon = Icons.Filled.Settings,
                        label = "Настройки",
                        onClick = onSettingsClick,
                        scale = scale
                    )
                }
            }
        }

        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            shadowElevation = 4.dp,
            modifier = Modifier
                .size(plusButtonSize)
                .bounceClick(onAddClick)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "Добавить",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(plusIconSize)
                )
            }
        }
    }
}

@Composable
fun ToolbarNavItem(isSelected: Boolean, icon: ImageVector, label: String, onClick: () -> Unit, scale: Float) {
    val contentColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "contentColor"
    )

    Box(
        modifier = Modifier
            .size(48.dp * scale)
            .clip(CircleShape)
            .bounceClick(onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = contentColor,
            modifier = Modifier.size(24.dp * scale)
        )
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
                                        contentDescription = "Биометрия",
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
                                Icon(Icons.Filled.Clear, contentDescription = "Стереть", modifier = Modifier.size(32.dp))
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.weight(0.3f))

            Image(
                painter = painterResource(id = R.drawable.lock),
                contentDescription = "Блокировка",
                modifier = Modifier.size(56.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = if (isError) "Неверный PIN-код" else "Введите PIN-код",
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
        .setTitle("Вход в Документы")
        .setSubtitle("Приложите палец или посмотрите на экран")
        .setNegativeButtonText("Использовать PIN-код")
        .build()

    biometricPrompt.authenticate(promptInfo)
}

@Composable
fun CustomSideNavigationRail(
    activeTab: Int = 0,
    onHomeClick: () -> Unit,
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onAddClick: () -> Unit
) {
    val scale = 1.10f
    val panelWidth = 64.dp * scale
    val plusButtonSize = 64.dp * scale
    val plusIconSize = 28.dp * scale
    val innerPadding = 8.dp * scale
    val iconSpacing = 8.dp * scale
    val itemSize = 48.dp * scale

    // Анимация вертикального ползунка
    val indicatorOffset by animateDpAsState(
        targetValue = innerPadding + (activeTab * (itemSize.value + iconSpacing.value)).dp,
        animationSpec = spring(
            stiffness = Spring.StiffnessMediumLow,
            dampingRatio = Spring.DampingRatioLowBouncy
        ),
        label = "indicatorOffsetVertical"
    )

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(100.dp) // Ширина всей боковой зоны
            .systemBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(32.dp, Alignment.CenterVertically)
    ) {
        // Кнопка с плюсом наверху
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            shadowElevation = 4.dp,
            modifier = Modifier
                .size(plusButtonSize)
                .bounceClick(onAddClick)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "Добавить",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(plusIconSize)
                )
            }
        }

        // Вертикальный островок навигации
        Surface(
            modifier = Modifier.width(panelWidth),
            shape = RoundedCornerShape(50), // Закругленные края
            color = MaterialTheme.colorScheme.surfaceVariant,
            shadowElevation = 4.dp
        ) {
            Box(contentAlignment = Alignment.TopCenter) {
                // Плавающий фон активной вкладки
                Box(
                    modifier = Modifier
                        .offset(y = indicatorOffset)
                        .size(itemSize)
                        .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)
                )

                Column(
                    modifier = Modifier.padding(vertical = innerPadding),
                    verticalArrangement = Arrangement.spacedBy(iconSpacing),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    ToolbarNavItem(isSelected = activeTab == 0, icon = Icons.Filled.Home, label = "Главная", onClick = onHomeClick, scale = scale)
                    ToolbarNavItem(isSelected = activeTab == 1, icon = Icons.Filled.Search, label = "Поиск", onClick = onSearchClick, scale = scale)
                    ToolbarNavItem(isSelected = activeTab == 2, icon = Icons.Filled.Settings, label = "Настройки", onClick = onSettingsClick, scale = scale)
                }
            }
        }
    }
}