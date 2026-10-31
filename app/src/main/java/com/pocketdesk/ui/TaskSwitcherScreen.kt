package com.pocketdesk.ui

import android.graphics.Bitmap
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import com.pocketdesk.model.RunningTask
import com.pocketdesk.theme.AdwaitaCardBg
import com.pocketdesk.theme.AdwaitaCardElevated
import com.pocketdesk.theme.AdwaitaDestructive
import com.pocketdesk.theme.BorderSubtle
import com.pocketdesk.theme.TextMuted
import com.pocketdesk.theme.TextPrimary
import com.pocketdesk.theme.TextSecondary
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun TaskSwitcherScreen(
    tasks: List<RunningTask>,
    onFocusTask: (Int) -> Unit,
    onDismissTask: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AdwaitaCardBg)
            .padding(top = 16.dp, bottom = 80.dp)
    ) {
        if (tasks.isEmpty()) {
            EmptyTasksState(modifier = Modifier.align(Alignment.Center))
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
                    text = stringResource(R.string.task_switcher_hint),
                    color = TextMuted,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                )

                // Current Card Indicator: N / Total · App Name
                val safeIndex = pagerState.currentPage.coerceIn(0, tasks.size - 1)
                val currentTask = tasks[safeIndex]

                Text(
                    text = "${safeIndex + 1} / ${tasks.size} · ${currentTask.app.name}",
                    color = TextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 6.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Multi-tasking Card Deck (HorizontalPager)
                HorizontalPager(
                    state = pagerState,
                    contentPadding = PaddingValues(horizontal = 48.dp),
                    pageSpacing = 20.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) { page ->
                    val task = tasks[page]
                    TaskCarouselCard(
                        task = task,
                        onTap = { onFocusTask(task.pid) },
                        onSwipeUpDismiss = { onDismissTask(task.pid) }
                    )
                }
            }
        }
    }
}

@Composable
private fun TaskCarouselCard(
    task: RunningTask,
    onTap: () -> Unit,
    onSwipeUpDismiss: () -> Unit,
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
                    if (offsetY.value < -220f || velocity < -800f) {
                        // Dismiss gesture triggered (kill SIGTERM)
                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        coroutineScope.launch {
                            offsetY.animateTo(-1200f, tween(200))
                            onSwipeUpDismiss()
                        }
                    } else {
                        // Snap back
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
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = AdwaitaCardElevated),
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(9f / 16f)
                .border(1.5.dp, BorderSubtle, RoundedCornerShape(24.dp))
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top header of card: App title & close button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = task.app.name,
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    IconButton(
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            coroutineScope.launch {
                                offsetY.animateTo(-1200f, tween(200))
                                onSwipeUpDismiss()
                            }
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = AdwaitaDestructive,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Thumbnail area: Live snapshot or placeholder
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 12.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    if (task.thumbnail != null) {
                        Image(
                            bitmap = task.thumbnail.asImageBitmap(),
                            contentDescription = "Window snapshot",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            AppIconRenderer(app = task.app, size = 64.dp)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Active Window (PID ${task.pid})",
                                color = TextMuted,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                // Bottom of card: centered App icon
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    AppIconRenderer(
                        app = task.app,
                        size = 40.dp
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyTasksState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.LayersClear,
            contentDescription = null,
            tint = TextMuted,
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.no_running_tasks),
            color = TextSecondary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Tap Desktop to launch a Mobian app",
            color = TextMuted,
            fontSize = 13.sp
        )
    }
}
