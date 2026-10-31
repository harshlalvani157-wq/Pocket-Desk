package com.pocketdesk.app.model

import android.graphics.Bitmap

data class RunningWindow(
    val windowId: Long,
    val pid: Int,
    val title: String,
    val app: AppItem,
    val thumbnail: Bitmap? = null,
    val startTime: Long = System.currentTimeMillis()
)
