package com.pocketdesk.app.ui

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LayersClear
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketdesk.R
import com.pocketdesk.app.model.RunningWindow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun TaskSwitcherScreen(
    tasks: List<RunningWindow>,
    onFocusWindow: (Long) -> Unit,
    onCloseWindow: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF1E1E1E))
            .padding(top = 16.dp, bottom = 80.dp)
    ) {
        if (tasks.isEmpty()) {
            EmptyTaskView(modifier = Modifier.align(Alignment.Center))
        } else {
            val pagerState = rememberPagerState(
                initialPage = 0,
                pageCount = { tasks.size }
            )

            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header instruction label
                Text(
                    text = "Swipe to switch · Tap to open · Swipe up to close",
                    color = Color(0xFF9A9996),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp)
                )

                val safeIndex = pagerState.currentPage.coerceIn(0, tasks.size - 1)
                val currentTask = tasks[safeIndex]

                // Card deck carousel
                HorizontalPager(
                    state = pagerState,
                    contentPadding = PaddingValues(horizontal = 48.dp),
                    pageSpacing = 16.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) { page ->
                    val task = tasks[page]
                    TaskCard(
                        task = task,
                        onTap = { onFocusWindow(task.windowId) },
                        onSwipeUp = { onCloseWindow(task.pid) }
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Bottom Pagination Controls with arrows: < N / Total · App Name >
                Row(
                    modifier = Modifier.padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    IconButton(
                        onClick = {
                            if (pagerState.currentPage > 0) {
                                coroutineScope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                            }
                        },
                        enabled = (pagerState.currentPage > 0)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = "Previous",
                            tint = if (pagerState.currentPage > 0) Color.White else Color(0x33FFFFFF)
                        )
                    }

                    Text(
                        text = "${safeIndex + 1} / ${tasks.size} · ${currentTask.title}",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )

                    IconButton(
                        onClick = {
                            if (pagerState.currentPage < tasks.size - 1) {
                                coroutineScope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                            }
                        },
                        enabled = (pagerState.currentPage < tasks.size - 1)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Next",
                            tint = if (pagerState.currentPage < tasks.size - 1) Color.White else Color(0x33FFFFFF)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskCard(
    task: RunningWindow,
    onTap: () -> Unit,
    onSwipeUp: () -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val coroutineScope = rememberCoroutineScope()
    val offsetY = remember { Animatable(0f) }
    val alphaAnim = remember { Animatable(1f) }

    val draggableState = rememberDraggableState { delta ->
        coroutineScope.launch {
            val newOffset = (offsetY.value + delta).coerceAtMost(0f)
            offsetY.snapTo(newOffset)
            val newAlpha = (1f - (-newOffset / 600f)).coerceIn(0.2f, 1f)
            alphaAnim.snapTo(newAlpha)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .offset { IntOffset(0, offsetY.value.roundToInt()) }
            .alpha(alphaAnim.value)
            .draggable(
                state = draggableState,
                orientation = Orientation.Vertical,
                onDragStopped = { velocity ->
                    if (offsetY.value < -200f || velocity < -800f) {
                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        coroutineScope.launch {
                            offsetY.animateTo(-1200f, tween(200))
                            onSwipeUp()
                        }
                    } else {
                        coroutineScope.launch {
                            offsetY.animateTo(0f, tween(150))
                            alphaAnim.animateTo(1f, tween(150))
                        }
                    }
                }
            )
            .clickable(onClick = onTap),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF262626)),
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(9f / 16f)
                .border(1.5.dp, Color(0x33FFFFFF), RoundedCornerShape(22.dp))
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Card header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = task.title,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    IconButton(
                        onClick = {
                            coroutineScope.launch {
                                offsetY.animateTo(-1200f, tween(200))
                                onSwipeUp()
                            }
                        },
                        modifier = Modifier.size(26.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color(0xFFE01B24),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // Window Snapshot Body
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 10.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    if (task.thumbnail != null) {
                        Image(
                            bitmap = task.thumbnail.asImageBitmap(),
                            contentDescription = task.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            AppIconBadge(app = task.app, size = 60.dp)
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Running [PID ${task.pid}]",
                                color = Color(0xFF9A9996),
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                // Centered App icon badge at the bottom of the card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    AppIconBadge(app = task.app, size = 36.dp)
                }
            }
        }
    }
}

@Composable
private fun EmptyTaskView(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.LayersClear,
            contentDescription = null,
            tint = Color(0xFF77767B),
            modifier = Modifier.size(56.dp)
        )
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = "No open apps",
            color = Color.LightGray,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Tap Desktop to open an app",
            color = Color(0xFF9A9996),
            fontSize = 13.sp
        )
    }
}
