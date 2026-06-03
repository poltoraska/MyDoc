package com.poltorashka.documents.data

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import bounceClick
import com.poltorashka.documents.DocumentsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReorderScreen(
    onBackClick: () -> Unit,
    viewModel: DocumentsViewModel
) {
    val docs by viewModel.documents.collectAsState()
    val localDocs = remember(docs) { docs.toMutableStateList() }

    val listState = rememberLazyListState()
    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var draggingItemOffset by remember { mutableStateOf(0f) }

    val density = LocalDensity.current
    val itemHeightPx = with(density) { 64.dp.toPx() }

    // ПОДКЛЮЧАЕМ СЛУЖБУ ВИБРАЦИИ
    val haptics = LocalHapticFeedback.current

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
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
                        .padding(top = 64.dp, bottom = 24.dp, start = 24.dp, end = 24.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                        modifier = Modifier
                            .size(48.dp)
                            .bounceClick { onBackClick() }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Назад",
                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Порядок документов",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 24.dp)
        ) {
            Text(
                text = "Удерживайте иконку справа, чтобы перетащить документ и изменить его положение.",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp).padding(bottom = 16.dp)
            )

            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .pointerInput(localDocs) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { offset ->
                                    val item = listState.layoutInfo.visibleItemsInfo.firstOrNull {
                                        offset.y.toInt() in it.offset..(it.offset + it.size)
                                    }
                                    if (item != null) {
                                        draggingIndex = item.index
                                        draggingItemOffset = 0f

                                        // ВИБРАЦИЯ 1: При захвате карточки (Тяжелый отклик)
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    }
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    val currentIndex = draggingIndex ?: return@detectDragGesturesAfterLongPress
                                    draggingItemOffset += dragAmount.y

                                    val threshold = itemHeightPx * 0.5f
                                    if (draggingItemOffset > threshold && currentIndex < localDocs.size - 1) {
                                        val nextIndex = currentIndex + 1
                                        val temp = localDocs[currentIndex]
                                        localDocs[currentIndex] = localDocs[nextIndex]
                                        localDocs[nextIndex] = temp
                                        draggingIndex = nextIndex
                                        draggingItemOffset -= itemHeightPx

                                        // ВИБРАЦИЯ 2: При смещении вниз (Легкий щелчок)
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)

                                    } else if (draggingItemOffset < -threshold && currentIndex > 0) {
                                        val prevIndex = currentIndex - 1
                                        val temp = localDocs[currentIndex]
                                        localDocs[currentIndex] = localDocs[prevIndex]
                                        localDocs[prevIndex] = temp
                                        draggingIndex = prevIndex
                                        draggingItemOffset += itemHeightPx

                                        // ВИБРАЦИЯ 2: При смещении вверх (Легкий щелчок)
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                },
                                onDragEnd = {
                                    draggingIndex = null
                                    draggingItemOffset = 0f
                                    viewModel.updateDocumentsOrder(localDocs)
                                },
                                onDragCancel = {
                                    draggingIndex = null
                                    draggingItemOffset = 0f
                                }
                            )
                        }
                ) {
                    itemsIndexed(localDocs, key = { _, doc -> doc.id }) { index, doc ->
                        val isDragging = index == draggingIndex
                        val modifier = if (isDragging) {
                            Modifier
                                .zIndex(1f)
                                .graphicsLayer {
                                    translationY = draggingItemOffset
                                    scaleX = 1.02f
                                    scaleY = 1.02f
                                    shadowElevation = 8.dp.toPx()
                                }
                        } else {
                            Modifier.animateItem()
                        }

                        val rawTitle = if (doc.documentType == "Другое") doc.fieldsData["Название документа"] ?: "Другое" else doc.documentType

                        Box(modifier = modifier) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(64.dp)
                                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
                                    .padding(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = rawTitle,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    imageVector = Icons.Filled.Menu,
                                    contentDescription = "Перетащить",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}