package com.poltorashka.documents

import coil.compose.AsyncImage

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import bounceClick
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.res.stringResource
import com.poltorashka.documents.ui.theme.HomeTitleFontFamily

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutAppScreen(onBackClick: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    val navigationBarBottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    // --- Вычисляет ширину экрана ---
    val configuration = LocalConfiguration.current
    val isWideScreen = configuration.screenWidthDp >= 600
    val headerScrollBehavior = rememberNavigationHeaderScrollBehavior()
    val scrollState = rememberScrollState()
    // ------------------------------------------

    Scaffold(
        modifier = Modifier
            .then(if (isWideScreen) Modifier else Modifier.nestedScroll(headerScrollBehavior.nestedScrollConnection)),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            if (isWideScreen) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp),
                    shadowElevation = 2.dp
                ) {
                    // РЕЖИМ ПЛАНШЕТА В одну строку
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(top = 24.dp, bottom = 24.dp, start = 24.dp, end = 24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier
                                .size(44.dp)
                                .bounceClick { onBackClick() }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(R.string.ui_nazad),
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Text(
                            text = stringResource(R.string.ui_o_prilozhenii),
                            fontSize = 24.sp, // Чуть меньше для однострочного дизайна
                            fontFamily = HomeTitleFontFamily,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            } else {
                CollapsingNavigationHeader(
                    title = stringResource(R.string.ui_o_prilozhenii),
                    scrollBehavior = headerScrollBehavior,
                    navigationIcon = {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.size(44.dp).bounceClick { onBackClick() }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(R.string.ui_nazad),
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        // --- КОНТЕНТ ---
        val headerOverlap = if (isWideScreen) 0.dp else HeaderCornerRadius
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = (innerPadding.calculateTopPadding() - headerOverlap).coerceAtLeast(0.dp))
                .clipToBounds()
                .verticalScroll(scrollState) // Включает вертикальную прокрутку
                .padding(start = 24.dp, top = headerOverlap + 24.dp, end = 24.dp, bottom = 24.dp + navigationBarBottomPadding)
        ) {
            // Карточка с благодарностью
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp)
                ) {
                    // КАРТИНКА ВОЛКА
                    AsyncImage(
                        model = R.drawable.sleep_wolf,
                        contentDescription = stringResource(R.string.ui_maskot),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                            .align(Alignment.CenterHorizontally) // Волк по центру
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = stringResource(R.string.ui_spasibo_chto_vybrali_moi_dokumenty_etot_proekt_s),
                        fontSize = 15.sp,
                        lineHeight = 22.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Карточка GitHub
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .bounceClick { uriHandler.openUri("https://github.com/poltoraska") },
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = R.drawable.img_avatar,
                        contentDescription = stringResource(R.string.ui_profil_github),
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "poltoraska",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.ui_profil_na_github),
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Карточка Политики конфиденциальности
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .bounceClick {
                        uriHandler.openUri("https://gist.github.com/poltoraska/ce7d88dd68e768e4addda4766e416f97")
                    },
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.Info,
                                contentDescription = stringResource(R.string.ui_politika_konfidentsialnosti),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.ui_politika_konfidentsialnosti),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.ui_i_usloviya_ispolzovaniya),
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
