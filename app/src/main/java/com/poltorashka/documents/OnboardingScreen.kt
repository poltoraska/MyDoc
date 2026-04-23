package com.poltorashka.documents

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { UserPreferences(context) }
    var currentStep by remember { mutableIntStateOf(1) }
    var nameInput by remember { mutableStateOf("") }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(64.dp))

            if (currentStep == 1) {
                // ПЕРВАЯ СТРАНИЦА: ПРИВЕТСТВИЕ
                Text(
                    text = "Здравствуйте!",
                    fontSize = 32.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(48.dp))

                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Text(
                            text = "Добро пожаловать в ваше\nприложение для документов.",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Text("Вам доступно:", fontWeight = FontWeight.Medium)
                        Spacer(modifier = Modifier.height(8.dp))

                        val features = listOf(
                            "быстрое управление вашими документами",
                            "управляйте документами ваших близких",
                            "загружайте файлы",
                            "доступ без интернета",
                            "данные хранятся только на вашем устройстве"
                        )
                        features.forEach { feature ->
                            Row(modifier = Modifier.padding(vertical = 4.dp)) {
                                Text("• ", fontWeight = FontWeight.Bold)
                                Text(feature, lineHeight = 20.sp)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                Button(
                    onClick = { currentStep = 2 },
                    modifier = Modifier.padding(bottom = 32.dp),
                    shape = RoundedCornerShape(50)
                ) {
                    Text("→ Начать", modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
                }

            } else {
                // ВТОРАЯ СТРАНИЦА: ВВОД ИМЕНИ
                Text(
                    text = "Давайте знакомиться!",
                    fontSize = 32.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(100.dp))

                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    label = { Text("Имя") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    trailingIcon = {
                        if (nameInput.isNotEmpty()) {
                            IconButton(onClick = { nameInput = "" }) {
                                Icon(Icons.Filled.Clear, contentDescription = "Очистить")
                            }
                        }
                    }
                )
                Text(
                    text = "Ваше имя нужно для отображения в главном меню.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp, start = 8.dp)
                )

                Spacer(modifier = Modifier.weight(1f))

                Button(
                    onClick = {
                        prefs.userName = nameInput.trim()
                        prefs.isOnboardingCompleted = true
                        onFinish() // Переход на главный экран
                    },
                    modifier = Modifier.padding(bottom = 32.dp),
                    shape = RoundedCornerShape(50)
                ) {
                    Text("→ Далее", modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
                }
            }
        }
    }
}