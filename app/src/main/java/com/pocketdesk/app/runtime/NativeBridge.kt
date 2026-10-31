package com.pocketdesk.app.runtime

import android.graphics.Bitmap
import android.util.Log
import android.view.Surface

object NativeBridge {
    private const val TAG = "NativeBridge"

    init {
        try {
            System.loadLibrary("pocketdesk_bridge")
            Log.i(TAG, "libpocketdesk_bridge.so loaded successfully")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to load libpocketdesk_bridge.so: ${e.message}")
        }
    }

    @JvmStatic
    external fun initDisplayServer(socketPath: String, width: Int, height: Int, dpi: Int): Boolean

    @JvmStatic
    external fun setSurface(surface: Surface?): Boolean

    @JvmStatic
    external fun resizeDisplay(width: Int, height: Int, dpi: Int)

    @JvmStatic
    external fun sendPointerEvent(action: Int, x: Float, y: Float, button: Int)

    @JvmStatic
    external fun sendKeyEvent(keyCode: Int, isDown: Boolean, modifiers: Int)

    @JvmStatic
    external fun captureSnapshot(): Bitmap?

    @JvmStatic
    external fun stopDisplayServer()

    @JvmStatic
    external fun launchProcess(
        command: String,
        rootfsPath: String,
        envVars: Array<String>,
        scale: Int,
        xftDpi: Int
    ): Int

    @JvmStatic
    external fun terminateProcess(pid: Int): Int

    @JvmStatic
    external fun isProcessAlive(pid: Int): Boolean

    @JvmStatic
    external fun reapChildProcesses(): Int
}
