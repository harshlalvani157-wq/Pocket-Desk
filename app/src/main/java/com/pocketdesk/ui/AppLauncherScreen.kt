package com.pocketdesk.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import com.pocketdesk.R
import com.pocketdesk.model.AppItem
import com.pocketdesk.theme.AdwaitaBlue
import com.pocketdesk.theme.AdwaitaCardBg
import com.pocketdesk.theme.AdwaitaCardElevated
import com.pocketdesk.theme.AdwaitaCardHover
import com.pocketdesk.theme.BorderSubtle
import com.pocketdesk.theme.OverlayDark
import com.pocketdesk.theme.TextMuted
import com.pocketdesk.theme.TextPrimary
import com.pocketdesk.theme.TextSecondary
import java.io.File

@Composable
fun AppLauncherScreen(
    apps: List<AppItem>,
    launchingApp: AppItem?,
    onAppClick: (AppItem) -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    val filteredApps = remember(apps, searchQuery) {
        if (searchQuery.isBlank()) {
            apps
        } else {
            val q = searchQuery.trim().lowercase()
            apps.filter {
                it.name.lowercase().contains(q) ||
                it.comment.lowercase().contains(q) ||
                it.exec.lowercase().contains(q)
            }
        }
    }

    Box(modifier = modifier.fillMaxSize().background(AdwaitaCardBg)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // Search Input Bar
            TextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = {
                    Text(
                        text = stringResource(R.string.search_hint),
                        color = TextMuted,
                        fontSize = 15.sp
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = AdwaitaBlue
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear",
                                tint = TextSecondary
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = AdwaitaCardElevated,
                    unfocusedContainerColor = AdwaitaCardElevated,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    cursorColor = AdwaitaBlue
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // App Launcher Grid
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 92.dp),
                contentPadding = PaddingValues(bottom = 96.dp, top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filteredApps, key = { it.id }) { app ->
                    AppGridTile(
                        app = app,
                        onClick = { onAppClick(app) }
                    )
                }
            }
        }

        // Animated App Launch Splash Screen
        AnimatedVisibility(
            visible = (launchingApp != null),
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            if (launchingApp != null) {
                AppLaunchSplashScreen(app = launchingApp)
            }
        }
    }
}

@Composable
private fun AppGridTile(
    app: AppItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        AppIconRenderer(
            app = app,
            size = 56.dp
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = app.name,
            color = TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun AppIconRenderer(
    app: AppItem,
    size: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(14.dp))
            .background(AdwaitaCardElevated),
        contentAlignment = Alignment.Center
    ) {
        if (!app.iconPath.isNullOrEmpty() && File(app.iconPath).exists()) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(File(app.iconPath))
                    .decoderFactory(SvgDecoder.Factory())
                    .crossfade(true)
                    .build(),
                contentDescription = app.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(6.dp)
            )
        } else {
            // High-fidelity fallback vector icons matching Mobian apps
            val vector = getFallbackIcon(app.id)
            Icon(
                imageVector = vector,
                contentDescription = app.name,
                tint = AdwaitaBlue,
                modifier = Modifier.size(size * 0.55f)
            )
        }
    }
}

private fun getFallbackIcon(appId: String): ImageVector {
    return when {
        appId.contains("Portfolio", ignoreCase = true) -> Icons.Default.Folder
        appId.contains("Console", ignoreCase = true) || appId.contains("Terminal", ignoreCase = true) -> Icons.Default.Terminal
        appId.contains("Epiphany", ignoreCase = true) || appId.contains("Web", ignoreCase = true) -> Icons.Default.Language
        appId.contains("Papers", ignoreCase = true) -> Icons.Default.PictureAsPdf
        appId.contains("TextEditor", ignoreCase = true) -> Icons.Default.Description
        appId.contains("FileRoller", ignoreCase = true) -> Icons.Default.Widgets
        appId.contains("code", ignoreCase = true) -> Icons.Default.Code
        else -> Icons.Default.Widgets
    }
}

@Composable
private fun AppLaunchSplashScreen(
    app: AppItem,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(OverlayDark),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp)
        ) {
            AppIconRenderer(
                app = app,
                size = 96.dp
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = app.name,
                color = TextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.launching_app),
                color = TextSecondary,
                fontSize = 14.sp
            )

            Spacer(modifier = Modifier.height(28.dp))

            CircularProgressIndicator(
                color = AdwaitaBlue,
                strokeWidth = 3.dp,
                modifier = Modifier.size(36.dp)
            )
        }
    }
}
