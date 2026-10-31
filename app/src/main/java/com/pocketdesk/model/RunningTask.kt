package com.pocketdesk.model

import android.graphics.Bitmap

data class RunningTask(
    val pid: Int,
    val app: AppItem,
    val windowId: Long = 0L,
    val thumbnail: Bitmap? = null,
    val startTime: Long = System.currentTimeMillis(),
    val isFocused: Boolean = false
)
