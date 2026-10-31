package com.pocketdesk

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.os.IBinder
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.core.view.WindowCompat
import com.pocketdesk.model.AppItem
import com.pocketdesk.parser.DesktopEntryParser
import com.pocketdesk.service.LinuxBridgeService
import com.pocketdesk.theme.AdwaitaDarkBg
import com.pocketdesk.theme.PocketDeskTheme
import com.pocketdesk.ui.AppLauncherScreen
import com.pocketdesk.ui.BottomNavigationBar
import com.pocketdesk.ui.KeyboardMode
import com.pocketdesk.ui.KeyboardRibbon
import com.pocketdesk.ui.LinuxSurfaceView
import com.pocketdesk.ui.ShellScreen
import com.pocketdesk.ui.TaskSwitcherScreen
import com.pocketdesk.ui.ToolsBottomSheet
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class DesktopActivity : ComponentActivity() {

    private var bridgeService: LinuxBridgeService? = null
    private var isBound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as? LinuxBridgeService.LocalBinder
            bridgeService = binder?.getService()
            isBound = true

            // Initialize display server with device screen dimensions
            val metrics = resources.displayMetrics
            bridgeService?.initDisplayServer(
                width = metrics.widthPixels,
                height = metrics.heightPixels,
                dpi = metrics.densityDpi
            )
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            bridgeService = null
            isBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Bind LinuxBridgeService
        val serviceIntent = Intent(this, LinuxBridgeService::class.java)
        startService(serviceIntent)
        bindService(serviceIntent, connection, Context.BIND_AUTO_CREATE)

        setContent {
            PocketDeskTheme {
                MainShellUI(
                    getBridgeService = { bridgeService },
                    onSetOrientation = { requestedOrientation = it }
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBound) {
            unbindService(connection)
            isBound = false
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            // Handled in Compose BackHandler
            return super.onKeyDown(keyCode, event)
        }
        bridgeService?.sendKeyEvent(keyCode, true, event?.modifiers ?: 0)
        return true
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            return super.onKeyUp(keyCode, event)
        }
        bridgeService?.sendKeyEvent(keyCode, false, event?.modifiers ?: 0)
        return true
    }
}

@Composable
private fun MainShellUI(
    getBridgeService: () -> LinuxBridgeService?,
    onSetOrientation: (Int) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val bridge = getBridgeService()

    // Screen density auto-calculation
    val density = LocalDensity.current
    val displayMetrics = context.resources.displayMetrics
    val autoScaleFactor = (displayMetrics.densityDpi / 160f).coerceIn(1.5f, 3.0f)
    val autoXftDpi = (96 * autoScaleFactor).toInt()

    var scaleFactor by remember { mutableFloatStateOf(autoScaleFactor) }
    var currentOrientation by remember { mutableIntStateOf(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) }
    var keyboardMode by remember { mutableStateOf(KeyboardMode.ANDROID_HOST_IME) }
    var showToolsSheet by remember { mutableStateOf(false) }

    var currentScreen by remember { mutableStateOf(ShellScreen.LAUNCHER) }
    var launchingApp by remember { mutableStateOf<AppItem?>(null) }
    var installedApps by remember { mutableStateOf<List<AppItem>>(emptyList()) }

    // Collect running tasks from service
    val runningTasks by (bridge?.runningTasks?.collectAsState() ?: remember { mutableStateOf(emptyList()) })

    // Load .desktop entries
    LaunchedEffect(Unit) {
        val rootfsPath = "${context.filesDir.absolutePath}/debian-rootfs"
        installedApps = DesktopEntryParser.parseInstalledApps(rootfsPath)
    }

    // Handle back button interactions
    BackHandler {
        when (currentScreen) {
            ShellScreen.SURFACE_DESKTOP -> {
                // Injects Esc event into Linux window
                bridge?.sendKeyEvent(KeyEvent.KEYCODE_ESCAPE, true)
                bridge?.sendKeyEvent(KeyEvent.KEYCODE_ESCAPE, false)
                // If user presses back again, switch to Launcher
                currentScreen = ShellScreen.LAUNCHER
            }
            ShellScreen.TASKS -> {
                currentScreen = if (runningTasks.isNotEmpty()) ShellScreen.SURFACE_DESKTOP else ShellScreen.LAUNCHER
            }
            ShellScreen.LAUNCHER -> {
                if (runningTasks.isNotEmpty()) {
                    currentScreen = ShellScreen.SURFACE_DESKTOP
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(AdwaitaDarkBg)) {
        // Main view content
        AnimatedContent(
            targetState = currentScreen,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "ShellScreenTransition"
        ) { screen ->
            when (screen) {
                ShellScreen.SURFACE_DESKTOP -> {
                    LinuxSurfaceView(bridgeService = bridge)
                }
                ShellScreen.LAUNCHER -> {
                    AppLauncherScreen(
                        apps = installedApps,
                        launchingApp = launchingApp,
                        onAppClick = { app ->
                            launchingApp = app
                            coroutineScope.launch {
                                val rootfsPath = "${context.filesDir.absolutePath}/debian-rootfs"
                                val xft = (96 * scaleFactor).toInt()
                                bridge?.launchApp(
                                    app = app,
                                    rootfsPath = rootfsPath,
                                    scaleFactor = scaleFactor,
                                    xftDpi = xft
                                )
                                delay(600)
                                launchingApp = null
                                currentScreen = ShellScreen.SURFACE_DESKTOP
                            }
                        }
                    )
                }
                ShellScreen.TASKS -> {
                    TaskSwitcherScreen(
                        tasks = runningTasks,
                        onFocusTask = { pid ->
                            bridge?.focusTask(pid)
                            currentScreen = ShellScreen.SURFACE_DESKTOP
                        },
                        onDismissTask = { pid ->
                            bridge?.terminateTask(pid)
                        }
                    )
                }
            }
        }

        // Overlay Chrome: Keyboard accessory ribbon + Persistent Bottom Navigation
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
        ) {
            // Accessory keyboard ribbon visible when in Desktop surface mode
            if (currentScreen == ShellScreen.SURFACE_DESKTOP) {
                KeyboardRibbon(
                    bridgeService = bridge,
                    onToggleKeyboardMode = {
                        keyboardMode = if (keyboardMode == KeyboardMode.ANDROID_HOST_IME) {
                            KeyboardMode.LINUX_SQUEEKBOARD
                        } else {
                            KeyboardMode.ANDROID_HOST_IME
                        }
                    }
                )
            }

            // Bottom Navigation Bar (Persistent)
            BottomNavigationBar(
                currentScreen = currentScreen,
                onBackClick = {
                    bridge?.sendKeyEvent(KeyEvent.KEYCODE_ESCAPE, true)
                    bridge?.sendKeyEvent(KeyEvent.KEYCODE_ESCAPE, false)
                },
                onDesktopClick = {
                    currentScreen = ShellScreen.LAUNCHER
                },
                onTasksClick = {
                    currentScreen = ShellScreen.TASKS
                },
                onToolsClick = {
                    showToolsSheet = true
                }
            )
        }

        // Quick Controls Modal Bottom Sheet
        if (showToolsSheet) {
            ToolsBottomSheet(
                currentScale = scaleFactor,
                currentOrientation = currentOrientation,
                currentKeyboardMode = keyboardMode,
                onScaleChange = { newScale ->
                    scaleFactor = newScale
                    val xft = (96 * newScale).toInt()
                    val metrics = context.resources.displayMetrics
                    bridge?.resizeDisplay(metrics.widthPixels, metrics.heightPixels, (160 * newScale).toInt())
                },
                onOrientationChange = { newOrientation ->
                    currentOrientation = newOrientation
                    onSetOrientation(newOrientation)
                },
                onKeyboardModeChange = { newMode ->
                    keyboardMode = newMode
                },
                onTerminateAll = {
                    runningTasks.forEach { task ->
                        bridge?.terminateTask(task.pid)
                    }
                    currentScreen = ShellScreen.LAUNCHER
                },
                onDismiss = { showToolsSheet = false }
            )
        }
    }
}
