package com.dualcam.app.domain

enum class LayoutMode {
    PIP,
    SPLIT_SCREEN,
    STACKED
}

data class DualCamState(
    val isLogicalMultiCameraSupported: Boolean = false,
    val isRecording: Boolean = false,
    val layoutMode: LayoutMode = LayoutMode.PIP,

    val logicalCameraId: String? = null,
    val availablePhysicalCameras: List<String> = emptyList(),

    // 16:9 slot configuration (stores physical ID)
    val mainCameraId: String = "0",
    val mainCameraAspectRatio: Float = 16f/9f,

    // 9:16 slot configuration (stores physical ID)
    val subCameraId: String = "1",
    val subCameraAspectRatio: Float = 16f/9f,

    val selectedResolution: String = "1080p", // 720p, 1080p, 1440p, 2160p
    val selectedFps: Int = 30, // 24, 30, 48, 60

    val errorMessage: String? = null,

    // Hardware fallback state
    val isSecondarySlotDisabled: Boolean = false,
    val hardwareLimitationMessage: String? = null
)
