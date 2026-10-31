package com.pocketdesk.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Binder
import android.os.IBinder
import android.util.Log
import android.view.Surface
import androidx.core.app.NotificationCompat
import com.pocketdesk.DesktopActivity
import com.pocketdesk.PocketDeskApp
import com.pocketdesk.R
import com.pocketdesk.model.AppItem
import com.pocketdesk.model.RunningTask
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

class LinuxBridgeService : Service() {

    companion object {
        private const val TAG = "LinuxBridgeService"
        private const val NOTIFICATION_ID = 1001

        init {
            try {
                System.loadLibrary("pocketdesk_bridge")
                Log.i(TAG, "Loaded libpocketdesk_bridge.so successfully")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Failed to load libpocketdesk_bridge.so: ${e.message}")
            }
        }
    }

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())

    private var nativeDisplayContext: Long = 0L
    private val _runningTasks = MutableStateFlow<List<RunningTask>>(emptyList())
    val runningTasks: StateFlow<List<RunningTask>> = _runningTasks.asStateFlow()

    private val _activeTask = MutableStateFlow<RunningTask?>(null)
    val activeTask: StateFlow<RunningTask?> = _activeTask.asStateFlow()

    private val _isServerReady = MutableStateFlow(false)
    val isServerReady: StateFlow<Boolean> = _isServerReady.asStateFlow()

    inner class LocalBinder : Binder() {
        fun getService(): LinuxBridgeService = this@LinuxBridgeService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, createNotification())
        startMonitorLoop()
    }

    private fun createNotification(): Notification {
        val notificationIntent = Intent(this, DesktopActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, PocketDeskApp.CHANNEL_ID)
            .setContentTitle(getString(R.string.service_notification_title))
            .setContentText(getString(R.string.service_notification_desc))
            .setSmallIcon(android.R.drawable.ic_menu_agenda)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    fun initDisplayServer(width: Int, height: Int, dpi: Int, socketPath: String = "/data/data/com.pocketdesk/files/tmp/.X11-unix/X0") {
        if (nativeDisplayContext != 0L) return

        val socketFile = File(socketPath)
        socketFile.parentFile?.mkdirs()

        nativeDisplayContext = nativeCreateDisplayContext(socketPath, width, height, dpi)
        _isServerReady.value = (nativeDisplayContext != 0L)
        Log.i(TAG, "Initialized display server with handle: $nativeDisplayContext")
    }

    fun setSurface(surface: Surface?): Boolean {
        if (nativeDisplayContext == 0L) return false
        return nativeSetSurface(nativeDisplayContext, surface)
    }

    fun resizeDisplay(width: Int, height: Int, dpi: Int) {
        if (nativeDisplayContext != 0L) {
            nativeResizeDisplay(nativeDisplayContext, width, height, dpi)
        }
    }

    fun sendPointerEvent(action: Int, x: Float, y: Float, button: Int = 1) {
        if (nativeDisplayContext != 0L) {
            nativeSendPointerEvent(nativeDisplayContext, action, x, y, button)
        }
    }

    fun sendKeyEvent(keycode: Int, isDown: Boolean, modifiers: Int = 0) {
        if (nativeDisplayContext != 0L) {
            nativeSendKeyEvent(nativeDisplayContext, keycode, isDown, modifiers)
        }
    }

    fun captureSnapshot(): Bitmap? {
        if (nativeDisplayContext == 0L) return null
        return nativeCaptureSnapshot(nativeDisplayContext)
    }

    fun launchApp(
        app: AppItem,
        rootfsPath: String = "${filesDir.absolutePath}/debian-rootfs",
        prootBin: String = "${applicationInfo.nativeLibraryDir}/libproot.so",
        scaleFactor: Float = 2.0f,
        xftDpi: Int = 192
    ): Int {
        val envList = mutableListOf(
            "DISPLAY=:0",
            "WAYLAND_DISPLAY=wayland-0",
            "XDG_RUNTIME_DIR=/tmp",
            "GDK_SCALE=${scaleFactor.toInt().coerceAtLeast(1)}",
            "GDK_DPI_SCALE=1",
            "XFT_DPI=$xftDpi",
            "GTK_THEME=Adwaita:dark",
            "QT_SCALE_FACTOR=${scaleFactor.toInt().coerceAtLeast(1)}",
            "PULSE_SERVER=127.0.0.1:4713",
            "LANG=C.UTF-8",
            "LC_ALL=C.UTF-8"
        )

        val pid = nativeForkExec(
            prootBin = prootBin,
            rootfsPath = rootfsPath,
            command = app.exec,
            envVars = envList.toTypedArray(),
            workDir = "/root"
        )

        if (pid > 0) {
            val task = RunningTask(
                pid = pid,
                app = app,
                windowId = pid.toLong(),
                thumbnail = null,
                startTime = System.currentTimeMillis(),
                isFocused = true
            )
            val currentList = _runningTasks.value.toMutableList()
            currentList.removeAll { it.pid == pid }
            currentList.add(0, task)
            _runningTasks.value = currentList
            _activeTask.value = task
            Log.i(TAG, "Launched application: ${app.name} [PID $pid]")
        } else {
            Log.e(TAG, "Failed to launch application: ${app.name}")
        }

        return pid
    }

    fun terminateTask(pid: Int) {
        serviceScope.launch {
            Log.i(TAG, "Sending SIGTERM to process PID: $pid")
            nativeSendSignal(pid, 15) // SIGTERM

            // Remove immediately from UI task carousel
            val updated = _runningTasks.value.filter { it.pid != pid }
            _runningTasks.value = updated
            if (_activeTask.value?.pid == pid) {
                _activeTask.value = updated.firstOrNull()
            }

            // Grace period check: if still alive after 2 seconds, escalate to SIGKILL
            delay(2000)
            if (nativeIsProcessAlive(pid)) {
                Log.w(TAG, "Process PID $pid did not terminate with SIGTERM, escalating to SIGKILL")
                nativeSendSignal(pid, 9) // SIGKILL
            }
        }
    }

    fun focusTask(pid: Int) {
        val task = _runningTasks.value.find { it.pid == pid }
        if (task != null) {
            val updated = _runningTasks.value.map {
                it.copy(isFocused = (it.pid == pid))
            }
            _runningTasks.value = updated
            _activeTask.value = task
            Log.i(TAG, "Focused task: ${task.app.name} [PID $pid]")
        }
    }

    private fun startMonitorLoop() {
        serviceScope.launch {
            while (isActive) {
                delay(1500)
                // 1. Reap zombie child processes
                nativeReapChildProcesses()

                // 2. Verify running tasks liveness
                val currentTasks = _runningTasks.value
                val aliveTasks = currentTasks.filter { task ->
                    nativeIsProcessAlive(task.pid)
                }
                if (aliveTasks.size != currentTasks.size) {
                    _runningTasks.value = aliveTasks
                    if (_activeTask.value != null && aliveTasks.none { it.pid == _activeTask.value?.pid }) {
                        _activeTask.value = aliveTasks.firstOrNull()
                    }
                }

                // 3. Update active thumbnail if displaying
                if (_activeTask.value != null && nativeDisplayContext != 0L) {
                    val snapshot = captureSnapshot()
                    if (snapshot != null) {
                        val activePid = _activeTask.value?.pid
                        _runningTasks.value = _runningTasks.value.map { t ->
                            if (t.pid == activePid) t.copy(thumbnail = snapshot) else t
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (nativeDisplayContext != 0L) {
            nativeDestroyDisplayContext(nativeDisplayContext)
            nativeDisplayContext = 0L
        }
    }

    // Native JNI Declarations
    private external fun nativeCreateDisplayContext(socketPath: String, width: Int, height: Int, dpi: Int): Long
    private external fun nativeSetSurface(nativePtr: Long, surface: Surface?): Boolean
    private external fun nativeResizeDisplay(nativePtr: Long, width: Int, height: Int, dpi: Int)
    private external fun nativeSendPointerEvent(nativePtr: Long, action: Int, x: Float, y: Float, button: Int)
    private external fun nativeSendKeyEvent(nativePtr: Long, keycode: Int, isDown: Boolean, modifiers: Int)
    private external fun nativeCaptureSnapshot(nativePtr: Long): Bitmap?
    private external fun nativeDestroyDisplayContext(nativePtr: Long)

    private external fun nativeForkExec(prootBin: String, rootfsPath: String, command: String, envVars: Array<String>, workDir: String): Int
    private external fun nativeSendSignal(pid: Int, signal: Int): Int
    private external fun nativeIsProcessAlive(pid: Int): Boolean
    private external fun nativeReapChildProcesses(): Int
}
