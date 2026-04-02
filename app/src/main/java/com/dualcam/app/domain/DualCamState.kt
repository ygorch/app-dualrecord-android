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

    val availableCameras: List<String> = emptyList(),

    // 16:9 slot configuration
    val mainCameraId: String = "0", // Usually back camera
    val mainCameraAspectRatio: Float = 16f/9f,

    // 9:16 slot configuration
    val subCameraId: String = "1",  // Usually front camera
    val subCameraAspectRatio: Float = 16f/9f,

    val selectedResolution: String = "1080p", // 720p, 1080p, 1440p, 2160p
    val selectedFps: Int = 30, // 24, 30, 48, 60

    val errorMessage: String? = null,

    // Hardware fallback state
    val isSecondarySlotDisabled: Boolean = false,
    val hardwareLimitationMessage: String? = null
)
