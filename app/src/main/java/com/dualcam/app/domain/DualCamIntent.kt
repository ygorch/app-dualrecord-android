package com.dualcam.app.domain

sealed class DualCamIntent {
    data class ChangeLayoutMode(val mode: LayoutMode) : DualCamIntent()
    data class ChangeResolution(val resolution: String) : DualCamIntent()
    data class ChangeFps(val fps: Int) : DualCamIntent()

    data class SelectMainCamera(val id: String) : DualCamIntent()
    data class SelectSubCamera(val id: String) : DualCamIntent()

    object StartRecording : DualCamIntent()
    object StopRecording : DualCamIntent()
    object DismissError : DualCamIntent()
}
