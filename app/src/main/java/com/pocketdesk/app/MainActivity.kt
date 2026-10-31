package com.pocketdesk.app

import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import com.pocketdesk.app.model.AppItem
import com.pocketdesk.app.runtime.LinuxProcessManager
import com.pocketdesk.app.runtime.NativeBridge
import com.pocketdesk.app.ui.AppLauncherScreen
import com.pocketdesk.app.ui.BottomNavOverlay
import com.pocketdesk.app.ui.KeyboardRibbon
import com.pocketdesk.app.ui.LinuxSurfaceView
import com.pocketdesk.app.ui.ShellMode
import com.pocketdesk.app.ui.TaskSwitcherScreen
import com.pocketdesk.theme.PocketDeskTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Initialize Native display server
        val metrics = resources.displayMetrics
        NativeBridge.initDisplayServer(
            socketPath = "${filesDir.absolutePath}/tmp/.X11-unix/X0",
            width = metrics.widthPixels,
            height = metrics.heightPixels,
            dpi = metrics.densityDpi
        )

        LinuxProcessManager.init(this)

        setContent {
            PocketDeskTheme {
                MainShell()
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            return super.onKeyDown(keyCode, event)
        }
        NativeBridge.sendKeyEvent(keyCode, true, event?.modifiers ?: 0)
        return true
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            return super.onKeyUp(keyCode, event)
        }
        NativeBridge.sendKeyEvent(keyCode, false, event?.modifiers ?: 0)
        return true
    }
}

@Composable
private fun MainShell() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val displayMetrics = context.resources.displayMetrics
    val autoScaleFactor = (displayMetrics.densityDpi / 160f).coerceIn(1.5f, 3.0f)
    var scaleFactor by remember { mutableFloatStateOf(autoScaleFactor) }

    var currentMode by remember { mutableStateOf(ShellMode.DESKTOP_GRID) }
    var launchingApp by remember { mutableStateOf<AppItem?>(null) }
    var installedApps by remember { mutableStateOf(AppItem.MOBIAN_DEFAULT_APPS) }

    val runningTasks by LinuxProcessManager.runningWindows.collectAsState()

    LaunchedEffect(Unit) {
        val rootfsDir = "${context.filesDir.absolutePath}/debian-rootfs"
        installedApps = LinuxProcessManager.loadDesktopApps(rootfsDir)
    }

    BackHandler {
        when (currentMode) {
            ShellMode.SURFACE_WINDOW -> {
                NativeBridge.sendKeyEvent(KeyEvent.KEYCODE_ESCAPE, true, 0)
                NativeBridge.sendKeyEvent(KeyEvent.KEYCODE_ESCAPE, false, 0)
                currentMode = ShellMode.DESKTOP_GRID
            }
            ShellMode.TASKS_CAROUSEL -> {
                currentMode = if (runningTasks.isNotEmpty()) ShellMode.SURFACE_WINDOW else ShellMode.DESKTOP_GRID
            }
            ShellMode.DESKTOP_GRID -> {
                if (runningTasks.isNotEmpty()) {
                    currentMode = ShellMode.SURFACE_WINDOW
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF1E1E1E))) {
        // Main Mode Transition
        AnimatedContent(
            targetState = currentMode,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "ShellModeAnim"
        ) { mode ->
            when (mode) {
                ShellMode.SURFACE_WINDOW -> {
                    LinuxSurfaceView()
                }
                ShellMode.DESKTOP_GRID -> {
                    AppLauncherScreen(
                        apps = installedApps,
                        launchingApp = launchingApp,
                        onAppClick = { app ->
                            launchingApp = app
                            coroutineScope.launch {
                                val xftDpi = (96 * scaleFactor).toInt()
                                LinuxProcessManager.launchApp(app, scaleFactor.toInt(), xftDpi)
                                delay(500)
                                launchingApp = null
                                currentMode = ShellMode.SURFACE_WINDOW
                            }
                        }
                    )
                }
                ShellMode.TASKS_CAROUSEL -> {
                    TaskSwitcherScreen(
                        tasks = runningTasks,
                        onFocusWindow = { winId ->
                            LinuxProcessManager.focusWindow(winId)
                            currentMode = ShellMode.SURFACE_WINDOW
                        },
                        onCloseWindow = { pid ->
                            LinuxProcessManager.terminateProcess(pid)
                        }
                    )
                }
            }
        }

        // Overlay Navigation and Keyboard bar
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
        ) {
            if (currentMode == ShellMode.SURFACE_WINDOW) {
                KeyboardRibbon(
                    onToggleKeyboard = {
                        // Soft keyboard toggle
                    }
                )
            }

            BottomNavOverlay(
                currentMode = currentMode,
                onBackClick = {
                    NativeBridge.sendKeyEvent(KeyEvent.KEYCODE_ESCAPE, true, 0)
                    NativeBridge.sendKeyEvent(KeyEvent.KEYCODE_ESCAPE, false, 0)
                },
                onDesktopClick = {
                    currentMode = ShellMode.DESKTOP_GRID
                },
                onTasksClick = {
                    currentMode = ShellMode.TASKS_CAROUSEL
                },
                onToolsClick = {
                    // Open Quick settings
                }
            )
        }
    }
}
