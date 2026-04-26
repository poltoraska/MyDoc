package com.poltorashka.documents

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import bounceClick
import com.poltorashka.documents.data.AppDatabase
import com.poltorashka.documents.ui.theme.DocumentsTheme
import java.util.Calendar
import androidx.compose.foundation.lazy.grid.items as gridItems

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
        in 6..11 -> R.drawable.wolf1   // Утро
        in 12..17 -> R.drawable.wolf2  // День
        in 18..22 -> R.drawable.wolf3  // Вечер
        else -> R.drawable.wolf4       // Ночь
    }
}

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DocumentsTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    val context = LocalContext.current
                    val db = AppDatabase.getDatabase(context)
                    val viewModel: DocumentsViewModel = viewModel(
                        factory = DocumentsViewModelFactory(db.documentDao(), db.folderDao())
                    )

                    val prefs = remember { UserPreferences(context) }
                    val startScreen = if (!prefs.isOnboardingCompleted) "onboarding" else if (prefs.isPinEnabled) "auth" else "main"

                    val tabRoutes = listOf("main", "settings", "search")

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
                            MainScreen(
                                onDocumentClick = { id -> navController.navigate("detail/$id") },
                                onAddClick = { folderId -> navController.navigate("add/$folderId") },
                                onSettingsClick = { navController.navigate("settings") { launchSingleTop = true } },
                                onSearchClick = { navController.navigate("search") { launchSingleTop = true } },
                                viewModel = viewModel
                            )
                        }

                        composable("auth") {
                            AuthScreen(
                                correctPin = prefs.appPin,
                                isBiometricEnabled = prefs.isBiometricEnabled,
                                onSuccess = {
                                    navController.navigate("main") { popUpTo("auth") { inclusive = true } }
                                }
                            )
                        }

                        composable("settings") {
                            SettingsScreen(
                                onBackClick = { navController.popBackStack() },
                                onHomeClick = { navController.navigate("main") { popUpTo("main") { inclusive = false } } },
                                onSearchClick = { navController.navigate("search") { launchSingleTop = true } },
                                onAboutClick = { navController.navigate("about") }
                            )
                        }

                        composable("about") {
                            AboutAppScreen(
                                onBackClick = { navController.popBackStack() }
                            )
                        }

                        composable("search") {
                            SearchScreen(
                                onBackClick = { navController.popBackStack() },
                                onHomeClick = { navController.navigate("main") { popUpTo("main") { inclusive = false } } },
                                onSettingsClick = { navController.navigate("settings") { launchSingleTop = true } },
                                onAddClick = { navController.navigate("add/0") },
                                onDocumentClick = { id -> navController.navigate("detail/$id") }
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
    viewModel: DocumentsViewModel
) {
    val folders by viewModel.folders.collectAsState()
    val manualSelectedId by viewModel.selectedFolderId.collectAsState()
    val docs by viewModel.documents.collectAsState()
    val activeFolderId = manualSelectedId ?: folders.firstOrNull()?.id
    val context = LocalContext.current
    val prefs = remember { UserPreferences(context) }
    val userName = prefs.userName

    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = {
            CustomFloatingToolbar(
                activeTab = 0,
                onHomeClick = { },
                onSearchClick = onSearchClick,
                onSettingsClick = onSettingsClick,
                onAddClick = { activeFolderId?.let { onAddClick(it) } }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
        ) {
            // ШИКАРНЫЙ ВЕРХНИЙ БЛОК В СТИЛЕ MATERIAL EXPRESSIVE
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
                    Column(modifier = Modifier.padding(horizontal = 24.dp)) {
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

                            // --- МАГИЯ АНИМАЦИИ ВОЛКА ---
                            var isAnimated by remember { mutableStateOf(false) }

                            // Запускает анимацию сразу при появлении экрана
                            LaunchedEffect(Unit) {
                                isAnimated = true
                            }

                            // Анимация масштаба (эффект пружинки)
                            val scale by animateFloatAsState(
                                targetValue = if (isAnimated) 1f else 0f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessLow
                                ),
                                label = "wolfScale"
                            )

                            // Анимация прозрачности (плавное появление)
                            val alpha by animateFloatAsState(
                                targetValue = if (isAnimated) 1f else 0f,
                                animationSpec = tween(durationMillis = 600),
                                label = "wolfAlpha"
                            )

                            Image(
                                painter = painterResource(id = getGreetingIconResId()),
                                contentDescription = "Маскот",
                                modifier = Modifier
                                    .size(35.dp)
                                    .graphicsLayer(
                                        scaleX = scale,
                                        scaleY = scale,
                                        alpha = alpha,
                                        transformOrigin = TransformOrigin.Center // Анимация идет ровно из центра картинки
                                    )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    if (folders.isNotEmpty()) {
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 24.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(folders) { folder ->
                                val isSelected = activeFolderId == folder.id

                                val containerColor by animateColorAsState(
                                    targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                    label = "color"
                                )
                                val contentColor by animateColorAsState(
                                    targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                    label = "color"
                                )

                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = containerColor,
                                    modifier = Modifier
                                        .height(48.dp)
                                        .bounceClick { viewModel.selectFolder(folder.id) }
                                ) {
                                    Box(
                                        modifier = Modifier.padding(horizontal = 24.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = folder.name,
                                            color = contentColor,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ОСНОВНОЙ БЛОК С КАРТОЧКАМИ ДОКУМЕНТОВ
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
                    .padding(top = 16.dp)
            ) {
                if (folders.isEmpty()) {
                    // Улучшенное пустое состояние для новых пользователей
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = innerPadding.calculateBottomPadding()),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            shape = RoundedCornerShape(28.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Перед добавлением первого документа необходимо создать папку в настройках приложения.",
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 15.sp,
                                    lineHeight = 22.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.height(24.dp))
                                // Кнопка, которая сразу ведет пользователя куда нужно
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
                                            text = "Открыть настройки",
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimary
                                        )
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
                        columns = GridCells.Fixed(2),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = innerPadding.calculateBottomPadding() + 16.dp)
                    ) {
                        gridItems(docs) { doc ->
                            DocumentCard(title = doc.documentType, onClick = { onDocumentClick(doc.id) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DocumentCard(title: String, onClick: () -> Unit) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp) // Сделал карточки чуть выше
            .clip(RoundedCornerShape(24.dp))
            .bounceClick { onClick() },
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant // Более глубокий цвет подложки
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {

            Image(
                painter = painterResource(id = R.drawable.pattern_guilloche),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(0.2f), // Текстура почти прозрачная
                contentScale = ContentScale.Crop
            )

            // --- СЛОЙ ТЕКСТА ---
            Text(
                // Оставляет фикс висячих предлогов
                text = title.replace(" о ", " о\u00A0"),
                fontWeight = FontWeight.SemiBold, // Сделал чуть жирнее
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(16.dp),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun CustomFloatingToolbar(
    activeTab: Int = 0, // 0 - Главная, 1 - Поиск, 2 - Настройки
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

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding() // ДОБАВИЛИ ЗАЩИТУ: меню всегда будет выше системной полоски
            .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 24.dp), // Слегка уменьшили bottom, так как navigationBarsPadding добавит своего места
        horizontalArrangement = Arrangement.spacedBy(gapBetweenIslands, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // ОСНОВНАЯ ПАНЕЛЬ НАВИГАЦИИ
        Surface(
            modifier = Modifier.height(panelHeight),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant,
            shadowElevation = 4.dp
        ) {
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

        // КНОПКА "+"
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

// Компонент отдельной кнопки меню, который умеет плавно расширяться
@Composable
fun ToolbarNavItem(isSelected: Boolean, icon: ImageVector, label: String, onClick: () -> Unit, scale: Float) {
    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        label = "bgColor"
    )
    val contentColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "contentColor"
    )

    Surface(
        shape = CircleShape,
        color = backgroundColor,
        modifier = Modifier
            .height(48.dp * scale)
            .animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessLow)) // МАГИЯ ПЛАВНОГО РАСШИРЕНИЯ ТУТ
            .bounceClick(onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = if (isSelected) 16.dp * scale else 12.dp * scale),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier.size(24.dp * scale)
            )
            if (isSelected) {
                Spacer(modifier = Modifier.width(8.dp * scale))
                Text(
                    text = label,
                    fontWeight = FontWeight.Bold,
                    color = contentColor,
                    fontSize = 14.sp * scale
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// НОВЫЕ КОМПОНЕНТЫ PIN-КОДА (Они вынесены сюда, чтобы Настройки тоже могли их использовать)
// ---------------------------------------------------------------------------

@Composable
fun PinDots(pinLength: Int, isError: Boolean) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(20.dp), // Чуть раздвинули точки
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
                    .size(20.dp) // Точки чуть-чуть аккуратнее
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

    // Единый размер для всех кнопок (УВЕЛИЧЕНО)
    val buttonSize = 84.dp

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        rows.forEach { row ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                row.forEach { key ->
                    if (key == "bio") {
                        if (isBiometricEnabled) {
                            // Кнопка биометрии
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
                            // Распорка теперь тоже размера buttonSize
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
                                    fontSize = 36.sp, // Увеличили шрифт цифр
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

// ---------------------------------------------------------------------------
// ЭКРАН АВТОРИЗАЦИИ
// ---------------------------------------------------------------------------

@Composable
fun AuthScreen(correctPin: String, isBiometricEnabled: Boolean, onSuccess: () -> Unit) {
    var pinInput by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val activity = context as? FragmentActivity

    LaunchedEffect(Unit) {
        if (isBiometricEnabled && activity != null) {
            showBiometricPrompt(activity, onSuccess)
        }
    }

    LaunchedEffect(isError) {
        if (isError) {
            kotlinx.coroutines.delay(500)
            pinInput = ""
            isError = false
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding() // МАГИЯ 1: Убирает черные системные полосы!
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // МАГИЯ 2: Гибкая распорка, которая толкает заголовок чуть ниже центра
            Spacer(modifier = Modifier.weight(0.3f))

            Icon(
                imageVector = Icons.Filled.Lock, // Заменил шестеренку на замок, так логичнее для экрана входа
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = if (isError) "Неверный PIN-код" else "Введите PIN-код",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(16.dp))
            PinDots(pinLength = pinInput.length, isError = isError)

            // МАГИЯ 3: Главная распорка. Она забирает всё свободное место и толкает клавиатуру вниз!
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

            // Небольшой отступ снизу, чтобы кнопки не прилипали к самому краю экрана
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
                onSuccess() // Если отпечаток совпал — пускает в приложение!
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
