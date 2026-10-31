package com.pocketdesk.app.ui

import android.content.Context
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.pocketdesk.app.runtime.NativeBridge

@Composable
fun LinuxSurfaceView(
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = { context ->
            PocketDeskSurfaceView(context)
        },
        modifier = modifier.fillMaxSize()
    )
}

private class PocketDeskSurfaceView(context: Context) : SurfaceView(context), SurfaceHolder.Callback {

    init {
        holder.addCallback(this)
        isFocusable = true
        isFocusableInTouchMode = true
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        NativeBridge.setSurface(holder.surface)
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        val density = resources.displayMetrics.densityDpi
        NativeBridge.setSurface(holder.surface)
        NativeBridge.resizeDisplay(width, height, density)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        NativeBridge.setSurface(null)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                NativeBridge.sendPointerEvent(MotionEvent.ACTION_DOWN, x, y, button = 1)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                NativeBridge.sendPointerEvent(MotionEvent.ACTION_MOVE, x, y, button = 1)
                return true
            }
            MotionEvent.ACTION_UP -> {
                NativeBridge.sendPointerEvent(MotionEvent.ACTION_UP, x, y, button = 1)
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                NativeBridge.sendPointerEvent(MotionEvent.ACTION_CANCEL, x, y, button = 1)
                return true
            }
        }

        return super.onTouchEvent(event)
    }
}
