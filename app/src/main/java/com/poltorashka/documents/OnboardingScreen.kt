package com.poltorashka.documents

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import bounceClick
import kotlinx.coroutines.launch
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    var currentStep by remember { mutableIntStateOf(0) }
    val context = LocalContext.current
    val prefs = remember { UserPreferences(context) }

    Crossfade(targetState = currentStep, label = "onboarding_animation") { step ->
        when (step) {
            0 -> WelcomeStep(
                onNext = { currentStep = 1 }
            )
            1 -> NameSetupStep(
                onFinish = { name ->
                    if (name.isNotBlank()) {
                        prefs.userName = name.trim()
                    }
                    prefs.isOnboardingCompleted = true
                    onFinish()
                }
            )
        }
    }
}

// --- ШАГ 1: ЭКРАН ПРИВЕТСТВИЯ ---
@Composable
fun WelcomeStep(onNext: () -> Unit) {
    val uriHandler = LocalUriHandler.current

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val screenHeight = maxHeight
        val scrollState = rememberScrollState()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = screenHeight),
                verticalArrangement = Arrangement.SpaceBetween
            ) {

                // --- ВЕРХНЯЯ ЧАСТЬ (Текст, Волк, Волна) ---
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(24.dp)) // Уменьшили с 48.dp

                    Text(
                        text = "Здравствуйте!",
                        fontSize = 36.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(16.dp)) // Уменьшили с 32.dp

                    Image(
                        painter = painterResource(id = R.drawable.img_wolf),
                        contentDescription = "Иллюстрация волка",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(170.dp) // Немного убавили размер волка (был 200.dp)
                            .padding(horizontal = 32.dp),
                        contentScale = ContentScale.Fit
                    )

                    Spacer(modifier = Modifier.height(16.dp)) // Уменьшили с 24.dp

                    AnimatedWavyLine(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(32.dp)
                            .padding(horizontal = 32.dp)
                    )

                    Spacer(modifier = Modifier.height(24.dp)) // Уменьшили с 32.dp
                }

                // --- НИЖНЯЯ ЧАСТЬ (Карточка с преимуществами) ---
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            modifier = Modifier
                                .widthIn(max = 500.dp)
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .padding(horizontal = 32.dp, vertical = 28.dp), // Уменьшили с 40.dp
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Ваши документы в одном месте",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(24.dp)) // Уменьшили с 32.dp

                            FeatureItem(
                                iconRes = R.drawable.ic_shield_check,
                                title = "Абсолютная безопасность",
                                description = "Вход по PIN-коду или\u00A0биометрии. Зашифрованные резервные копии."
                            )
                            Spacer(modifier = Modifier.height(16.dp)) // Уменьшили с 24.dp

                            FeatureItem(
                                iconRes = R.drawable.ic_family_docs,
                                title = "Идеальный порядок",
                                description = "Сортируйте документы по\u00A0папкам, добавляйте теги и\u00A0находите нужное за\u00A0секунду."
                            )
                            Spacer(modifier = Modifier.height(16.dp)) // Уменьшили с 24.dp

                            FeatureItem(
                                iconRes = R.drawable.ic_cloud_off,
                                title = "Полная независимость",
                                description = "Никаких серверов. Документы всегда под\u00A0рукой, даже без\u00A0интернета."
                            )

                            Spacer(modifier = Modifier.height(24.dp)) // Уменьшили с 40.dp

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "Продолжая, вы принимаете условия",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Политики конфиденциальности",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .bounceClick {
                                            uriHandler.openUri("https://gist.github.com/poltoraska/ce7d88dd68e768e4addda4766e416f97")
                                        }
                                        .padding(vertical = 4.dp, horizontal = 8.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp)) // Уменьшили с 24.dp

                            Surface(
                                shape = RoundedCornerShape(32.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(72.dp) // Сделали кнопку чуть тоньше (было 76.dp)
                                    .bounceClick { onNext() }
                            ) {
                                Row(
                                    modifier = Modifier.padding(start = 28.dp, end = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Поехали!",
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )

                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(56.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                                contentDescription = "Начать",
                                                tint = MaterialTheme.colorScheme.onPrimary,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FeatureItem(iconRes: Int, title: String, description: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Иконка
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            modifier = Modifier.size(36.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.width(16.dp))

        // Текст (Заголовок + Описание)
        Column {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = description,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp
            )
        }
    }
}

// --- ШАГ 2: ЭКРАН ВВОДА ИМЕНИ С ВОССТАНОВЛЕНИЕМ ---
@Composable
fun NameSetupStep(onFinish: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { UserPreferences(context) }

    // Состояния для восстановления
    var showRestoreDialog by remember { mutableStateOf(false) }
    var restorePasswordInput by remember { mutableStateOf("") }
    var selectedRestoreUri by remember { mutableStateOf<android.net.Uri?>(null) }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            selectedRestoreUri = uri
            showRestoreDialog = true
        }
    }

    // Делаем экран умным: узнаем его высоту и добавляем скролл
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
    ) {
        val screenHeight = maxHeight
        val scrollState = rememberScrollState()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Центральная колонка с ограничением ширины для планшетов
            Column(
                modifier = Modifier
                    .widthIn(max = 500.dp)
                    .heightIn(min = screenHeight)
                    .padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // --- ВЕРХНИЙ БЛОК (Заголовок, Поле ввода, Картинка) ---
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(modifier = Modifier.height(48.dp))

                    Text(
                        text = "Давайте знакомиться!",
                        fontSize = 32.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(48.dp))

                    TextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Имя") },
                        placeholder = { Text("Введите ваше имя") },
                        trailingIcon = {
                            if (name.isNotEmpty()) {
                                IconButton(onClick = { name = "" }) {
                                    Icon(Icons.Filled.Clear, contentDescription = "Очистить")
                                }
                            }
                        },
                        singleLine = true,
                        // Слегка скруглим углы для большей гармонии со стилем Expressive
                        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                        modifier = Modifier.fillMaxWidth(),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                            unfocusedIndicatorColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Ваше имя нужно для отображения в главном меню приложения.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Start,
                        lineHeight = 16.sp
                    )

                    Spacer(modifier = Modifier.height(48.dp))

                    Image(
                        painter = painterResource(id = R.drawable.img_wolf_keyboard),
                        contentDescription = "Лапки на клавиатуре",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        contentScale = ContentScale.Fit
                    )
                }

                // --- НИЖНИЙ БЛОК (Кнопка Далее и Восстановление) ---
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(32.dp))

                    // Новая кнопка в стиле Material Expressive (как на первом экране)
                    Surface(
                        shape = RoundedCornerShape(32.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(72.dp)
                            .bounceClick { onFinish(name) }
                    ) {
                        Row(
                            modifier = Modifier.padding(start = 28.dp, end = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Готово!",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )

                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(56.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = "Продолжить",
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Кнопка восстановления
                    Text(
                        text = "У меня уже есть резервная копия",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .bounceClick { importLauncher.launch(arrayOf("*/*")) }
                            .padding(8.dp)
                    )

                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }

    // ДИАЛОГ ВОССТАНОВЛЕНИЯ
    if (showRestoreDialog) {
        AlertDialog(
            onDismissRequest = { showRestoreDialog = false; selectedRestoreUri = null },
            title = { Text("Восстановление") },
            text = {
                Column {
                    Text("Введите пароль, который вы указывали при создании этой резервной копии.", fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = restorePasswordInput,
                        onValueChange = { restorePasswordInput = it },
                        label = { Text("Пароль") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (restorePasswordInput.isNotBlank() && selectedRestoreUri != null) {
                        showRestoreDialog = false
                        android.widget.Toast.makeText(context, "Распаковка архива...", android.widget.Toast.LENGTH_SHORT).show()

                        try {
                            com.poltorashka.documents.data.AppDatabase.getDatabase(context).close()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }

                        scope.launch {
                            val success = com.poltorashka.documents.utils.BackupManager.restoreBackup(context, selectedRestoreUri!!, restorePasswordInput)
                            if (success) {
                                android.widget.Toast.makeText(context, "Данные восстановлены успешно!", android.widget.Toast.LENGTH_LONG).show()

                                kotlin.concurrent.thread {
                                    Thread.sleep(1500)
                                    Runtime.getRuntime().exit(0)
                                }
                            } else {
                                android.widget.Toast.makeText(context, "Ошибка! Неверный пароль или файл поврежден.", android.widget.Toast.LENGTH_LONG).show()
                            }
                            selectedRestoreUri = null
                            restorePasswordInput = ""
                        }
                    }
                }) { Text("Восстановить") }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreDialog = false; selectedRestoreUri = null }) { Text("Отмена") }
            }
        )
    }
}

@Composable
fun AnimatedWavyLine(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "wave_transition")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f, // Один полный цикл анимации
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_phase"
    )

    val waveColor = MaterialTheme.colorScheme.primary

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height

        // Защита от нулевого размера при первичной отрисовке
        if (width <= 0f || height <= 0f) return@Canvas

        val strokeWidthPx = 4.dp.toPx()
        val waveWidthPx = 140.dp.toPx() // Жесткая ширина одного витка волны

        // Вычитаем толщину линии, чтобы волна не срезалась сверху и снизу
        val amplitude = (height - strokeWidthPx) / 2f
        val centerY = height / 2f

        val path = Path()

        // Сдвиг фазы в радианах (от 0 до 2π)
        val phaseShift = phase * 2 * Math.PI
        // Частота: один полный цикл (2π) каждые 140.dp
        val frequency = (2 * Math.PI) / waveWidthPx

        var isFirst = true

        // Рисуем волну поточечно (с шагом 3 пикселя для идеальной плавности)
        for (x in 0..width.toInt() step 3) {
            // Формула синуса, которая идеально вписывается в заданные рамки
            val y = centerY + (Math.sin(x * frequency - phaseShift).toFloat() * amplitude)

            if (isFirst) {
                path.moveTo(x.toFloat(), y) // Начинаем СТРОГО от нуля!
                isFirst = false
            } else {
                path.lineTo(x.toFloat(), y)
            }
        }

        drawPath(
            path = path,
            color = waveColor,
            style = Stroke(
                width = strokeWidthPx,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
    }
}