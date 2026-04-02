package com.dualcam.app.domain

enum class LayoutMode {
    PIP,
    SPLIT_SCREEN,
    STACKED
}

data class DualCamState(
    val isConcurrentSupported: Boolean = false,
    val isRecording: Boolean = false,
    val layoutMode: LayoutMode = LayoutMode.PIP,

    // 16:9 slot configuration
    val mainCameraId: String = "0", // Usually back camera
    // 9:16 slot configuration
    val subCameraId: String = "1",  // Usually front camera

    val selectedResolution: String = "1080p", // 720p, 1080p, 1440p, 2160p
    val selectedFps: Int = 30, // 24, 30, 48, 60

    val errorMessage: String? = null
)
