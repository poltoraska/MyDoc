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

// --- ШАГ 1: ЭКРАН ПРИВЕТСТВИЯ (БЕЗ ИЗМЕНЕНИЙ) ---
@Composable
fun WelcomeStep(onNext: () -> Unit) {
    val uriHandler = LocalUriHandler.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .statusBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Здравствуйте!",
                fontSize = 36.sp,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(48.dp))
            Image(
                painter = painterResource(id = R.drawable.img_wolf),
                contentDescription = "Иллюстрация волка",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .padding(horizontal = 32.dp),
                contentScale = ContentScale.Fit
            )
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 32.dp, vertical = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Ваши документы в одном месте",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(32.dp))
                FeatureItem(iconRes = R.drawable.ic_shield_check, text = "Данные хранятся только локально на\u00A0вашем устройстве.")
                Spacer(modifier = Modifier.height(24.dp))
                FeatureItem(iconRes = R.drawable.ic_family_docs, text = "Управляйте своими документами и\u00A0файлами близких.")
                Spacer(modifier = Modifier.height(24.dp))
                FeatureItem(iconRes = R.drawable.ic_cloud_off, text = "Загружайте файлы и\u00A0получайте к\u00A0ним доступ без\u00A0интернета.")

                Spacer(modifier = Modifier.height(40.dp))

                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .height(48.dp)
                        .bounceClick { onNext() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 24.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Начать", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onPrimary)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

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
            }
        }
    }
}

@Composable
fun FeatureItem(iconRes: Int, text: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(painter = painterResource(id = iconRes), contentDescription = null, modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.width(16.dp))
        Text(text = text, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 22.sp)
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(48.dp))

        Text(
            text = "Давайте знакомиться!",
            fontSize = 32.sp,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(64.dp))

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
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
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
            modifier = Modifier.padding(horizontal = 32.dp),
            lineHeight = 16.sp
        )

        Spacer(modifier = Modifier.height(48.dp))

        Image(
            painter = painterResource(id = R.drawable.img_wolf_keyboard),
            contentDescription = "Лапки на клавиатуре",
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .padding(horizontal = 32.dp),
            contentScale = ContentScale.Fit
        )

        Spacer(modifier = Modifier.weight(1f))

        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .height(48.dp)
                .bounceClick { onFinish(name) }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Далее",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

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

                        // Превентивно закрываем БД (хотя она тут скорее всего и не открывалась)
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