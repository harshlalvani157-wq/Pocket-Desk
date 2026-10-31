package com.pocketdesk.app.runtime

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.pocketdesk.app.model.AppItem
import com.pocketdesk.app.model.RunningWindow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

object LinuxProcessManager {
    private const val TAG = "LinuxProcessManager"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _runningWindows = MutableStateFlow<List<RunningWindow>>(emptyList())
    val runningWindows: StateFlow<List<RunningWindow>> = _runningWindows.asStateFlow()

    private var appContext: Context? = null
    private var rootfsDirectory: String = ""

    fun init(context: Context) {
        appContext = context.applicationContext
        rootfsDirectory = "${context.filesDir.absolutePath}/debian-rootfs"

        // Background monitor for zombie reaping and snapshot thumbnail capture
        scope.launch {
            while (isActive) {
                delay(1000)
                reapAndRefresh()
            }
        }
    }

    fun loadDesktopApps(rootfsDir: String): List<AppItem> {
        val appsDir = File(rootfsDir, "usr/share/applications")
        if (!appsDir.exists() || !appsDir.isDirectory) {
            return AppItem.MOBIAN_DEFAULT_APPS
        }

        val foundApps = mutableListOf<AppItem>()
        val files = appsDir.listFiles { _, name -> name.endsWith(".desktop") } ?: emptyArray()

        for (file in files) {
            try {
                var name = ""
                var exec = ""
                var icon = ""
                var comment = ""
                var noDisplay = false
                var inDesktopEntry = false

                file.forEachLine { rawLine ->
                    val line = rawLine.trim()
                    if (line.startsWith("[Desktop Entry]")) {
                        inDesktopEntry = true
                    } else if (line.startsWith("[") && line.endsWith("]")) {
                        inDesktopEntry = false
                    } else if (inDesktopEntry) {
                        when {
                            line.startsWith("Name=") && name.isEmpty() -> {
                                name = line.substringAfter("Name=").trim()
                            }
                            line.startsWith("Exec=") && exec.isEmpty() -> {
                                exec = line.substringAfter("Exec=")
                                    .replace("%u", "")
                                    .replace("%U", "")
                                    .replace("%f", "")
                                    .replace("%F", "")
                                    .trim()
                            }
                            line.startsWith("Icon=") && icon.isEmpty() -> {
                                icon = line.substringAfter("Icon=").trim()
                            }
                            line.startsWith("Comment=") && comment.isEmpty() -> {
                                comment = line.substringAfter("Comment=").trim()
                            }
                            line.startsWith("NoDisplay=") -> {
                                noDisplay = line.substringAfter("NoDisplay=").trim().equals("true", ignoreCase = true)
                            }
                        }
                    }
                }

                if (!noDisplay && name.isNotEmpty() && exec.isNotEmpty()) {
                    foundApps.add(
                        AppItem(
                            id = file.nameWithoutExtension,
                            name = name,
                            exec = exec,
                            iconName = icon,
                            comment = comment
                        )
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed parsing desktop file ${file.name}: ${e.message}")
            }
        }

        if (foundApps.isEmpty()) {
            return AppItem.MOBIAN_DEFAULT_APPS
        }

        // Merge defaults with found apps (avoid duplicates by exec or id)
        val result = ArrayList<AppItem>()
        val existingExecs = foundApps.map { it.exec.split(" ").firstOrNull() ?: it.exec }.toSet()

        for (defaultApp in AppItem.MOBIAN_DEFAULT_APPS) {
            val baseCmd = defaultApp.exec.split(" ").firstOrNull() ?: defaultApp.exec
            if (!existingExecs.contains(baseCmd)) {
                result.add(defaultApp)
            }
        }
        result.addAll(foundApps)
        return result
    }

    fun launchApp(app: AppItem, scale: Int = 2, xftDpi: Int = 192): RunningWindow? {
        val rootfs = rootfsDirectory
        val envVars = arrayOf(
            "POCKETDESK_APP_ID=${app.id}",
            "POCKETDESK_SCALE=$scale"
        )

        Log.i(TAG, "Launching app ${app.name} (${app.exec}) with scale=$scale, dpi=$xftDpi")
        val pid = NativeBridge.launchProcess(
            command = app.exec,
            rootfsPath = rootfs,
            envVars = envVars,
            scale = scale,
            xftDpi = xftDpi
        )

        if (pid <= 0) {
            Log.e(TAG, "Failed to launch process for ${app.name}")
            return null
        }

        val window = RunningWindow(
            windowId = pid.toLong(),
            pid = pid,
            title = app.name,
            app = app
        )

        val updated = _runningWindows.value.toMutableList()
        updated.add(0, window) // Place newest at front
        _runningWindows.value = updated
        return window
    }

    fun terminateProcess(pid: Int): Int {
        Log.i(TAG, "Terminating process with PID $pid")
        val res = NativeBridge.terminateProcess(pid)
        val updated = _runningWindows.value.filter { it.pid != pid }
        _runningWindows.value = updated
        return res
    }

    fun focusWindow(windowId: Long) {
        val currentList = _runningWindows.value
        val window = currentList.find { it.windowId == windowId } ?: return

        // Move to front
        val updated = currentList.toMutableList()
        updated.remove(window)
        updated.add(0, window)
        _runningWindows.value = updated
    }

    fun updateActiveWindowThumbnail(thumbnail: Bitmap?) {
        val currentList = _runningWindows.value
        if (currentList.isEmpty() || thumbnail == null) return

        val front = currentList.first()
        val updatedFront = front.copy(thumbnail = thumbnail)
        val updatedList = currentList.toMutableList()
        updatedList[0] = updatedFront
        _runningWindows.value = updatedList
    }

    private fun reapAndRefresh() {
        val reaped = NativeBridge.reapChildProcesses()
        val current = _runningWindows.value
        if (current.isEmpty()) return

        // Filter out dead processes
        val alive = current.filter { NativeBridge.isProcessAlive(it.pid) }
        if (alive.size != current.size) {
            Log.i(TAG, "Reaped dead windows (reaped: $reaped). Before: ${current.size}, Alive: ${alive.size}")
            _runningWindows.value = alive
        }

        // Periodically capture thumbnail for the focused/front window if active
        if (alive.isNotEmpty()) {
            try {
                val snapshot = NativeBridge.captureSnapshot()
                if (snapshot != null) {
                    val updated = alive.toMutableList()
                    updated[0] = updated[0].copy(thumbnail = snapshot)
                    _runningWindows.value = updated
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error capturing snapshot: ${e.message}")
            }
        }
    }
}
