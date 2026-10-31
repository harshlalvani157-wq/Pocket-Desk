package com.pocketdesk.ui

import android.content.Context
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.pocketdesk.service.LinuxBridgeService

@Composable
fun LinuxSurfaceView(
    bridgeService: LinuxBridgeService?,
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = { context ->
            TouchTrackingSurfaceView(context, bridgeService)
        },
        update = { view ->
            view.updateBridgeService(bridgeService)
        },
        modifier = modifier.fillMaxSize()
    )
}

private class TouchTrackingSurfaceView(
    context: Context,
    private var bridgeService: LinuxBridgeService?
) : SurfaceView(context), SurfaceHolder.Callback {

    init {
        holder.addCallback(this)
        isFocusable = true
        isFocusableInTouchMode = true
    }

    fun updateBridgeService(service: LinuxBridgeService?) {
        this.bridgeService = service
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        bridgeService?.setSurface(holder.surface)
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        val metrics = resources.displayMetrics
        bridgeService?.setSurface(holder.surface)
        bridgeService?.resizeDisplay(width, height, metrics.densityDpi)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        bridgeService?.setSurface(null)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val service = bridgeService ?: return super.onTouchEvent(event)

        val x = event.x
        val y = event.y

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                service.sendPointerEvent(MotionEvent.ACTION_DOWN, x, y, button = 1)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                service.sendPointerEvent(MotionEvent.ACTION_MOVE, x, y, button = 1)
                return true
            }
            MotionEvent.ACTION_UP -> {
                service.sendPointerEvent(MotionEvent.ACTION_UP, x, y, button = 1)
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                service.sendPointerEvent(MotionEvent.ACTION_CANCEL, x, y, button = 1)
                return true
            }
        }

        return super.onTouchEvent(event)
    }
}
