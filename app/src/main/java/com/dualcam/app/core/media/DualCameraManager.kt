package com.dualcam.app.core.media

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import android.hardware.camera2.CameraCharacteristics
import android.graphics.ImageFormat
import android.util.Size
import java.util.Collections
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages Camera2 API for concurrent dual-streaming.
 */
@Singleton
class DualCameraManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    // State to inform ViewModel if hardware supports concurrency
    private val _isConcurrentSupported = MutableStateFlow(false)
    val isConcurrentSupported: StateFlow<Boolean> = _isConcurrentSupported

    private val activeCameras = mutableMapOf<String, CameraDevice>()
    private val activeSessions = mutableMapOf<String, CameraCaptureSession>()

    private val backgroundThread = HandlerThread("CameraBackground").apply { start() }
    private val backgroundHandler = Handler(backgroundThread.looper)

    init {
        checkConcurrencySupport()
    }

    private fun checkConcurrencySupport() {
        val hasFeature = context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_CONCURRENT)
        if (hasFeature) {
            try {
                val concurrentCameraIds = cameraManager.concurrentCameraIds
                _isConcurrentSupported.value = concurrentCameraIds.isNotEmpty()
            } catch (e: Exception) {
                _isConcurrentSupported.value = false
            }
        } else {
            _isConcurrentSupported.value = false
        }
    }

    /**
     * Opens a camera.
     * @param cameraId The ID of the camera to open.
     * @param previewSurface The surface for rendering the preview on screen.
     * @param recordingSurface Optional surface from MediaCodec for recording.
     * @param onOpened Callback when camera is ready and streaming.
     */
    @SuppressLint("MissingPermission") // Caller (UI) must ensure permissions
    fun openCamera(
        cameraId: String,
        previewSurface: Surface,
        recordingSurface: Surface? = null,
        onOpened: () -> Unit
    ) {
        try {
            cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    activeCameras[cameraId] = camera
                    startPreviewAndRecording(camera, previewSurface, recordingSurface)
                    onOpened()
                }

                override fun onDisconnected(camera: CameraDevice) {
                    closeCamera(cameraId)
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    Log.e("CameraManager", "Error opening camera $cameraId: $error")
                    closeCamera(cameraId)
                }
            }, backgroundHandler)
        } catch (e: Exception) {
            Log.e("CameraManager", "Exception opening camera $cameraId", e)
        }
    }

    private fun startPreviewAndRecording(
        camera: CameraDevice,
        previewSurface: Surface,
        recordingSurface: Surface?
    ) {
        try {
            val surfaces = mutableListOf(previewSurface)
            if (recordingSurface != null) {
                surfaces.add(recordingSurface)
            }

            camera.createCaptureSession(surfaces, object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    activeSessions[camera.id] = session
                    val captureRequest = camera.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
                        addTarget(previewSurface)
                        recordingSurface?.let { addTarget(it) }
                        // For MVP: Default auto-focus and auto-exposure
                        set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
                    }.build()

                    session.setRepeatingRequest(captureRequest, null, backgroundHandler)
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    Log.e("CameraManager", "Failed to configure capture session for ${camera.id}")
                }
            }, backgroundHandler)
        } catch (e: Exception) {
            Log.e("CameraManager", "Exception creating capture session", e)
        }
    }

    fun closeCamera(cameraId: String) {
        activeSessions[cameraId]?.close()
        activeSessions.remove(cameraId)
        activeCameras[cameraId]?.close()
        activeCameras.remove(cameraId)
    }

    fun closeAll() {
        val keys = activeCameras.keys.toList()
        keys.forEach { closeCamera(it) }
    }

    fun getAvailableBackCameras(): List<String> {
        val backCameras = mutableListOf<String>()
        try {
            for (id in cameraManager.cameraIdList) {
                val characteristics = cameraManager.getCameraCharacteristics(id)
                val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                if (facing == CameraCharacteristics.LENS_FACING_BACK) {
                    backCameras.add(id)
                }
            }
        } catch (e: Exception) {
            Log.e("CameraManager", "Error getting back cameras", e)
        }
        return backCameras
    }

    fun getPreviewAspectRatio(cameraId: String): Float {
        try {
            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
            val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            if (map != null) {
                val sizes = map.getOutputSizes(android.graphics.SurfaceTexture::class.java)
                if (sizes != null && sizes.isNotEmpty()) {
                    // Usually find the largest 16:9 or use the largest available
                    val largest = Collections.max(sizes.toList(), CompareSizesByArea())
                    // Landscape aspect ratio Width/Height
                    return largest.width.toFloat() / largest.height.toFloat()
                }
            }
        } catch (e: Exception) {
            Log.e("CameraManager", "Error getting preview aspect ratio", e)
        }
        return 4f / 3f // default fallback
    }

}

internal class CompareSizesByArea : Comparator<Size> {
    override fun compare(lhs: Size, rhs: Size): Int {
        // We cast here to ensure the multiplications won't overflow
        return java.lang.Long.signum(lhs.width.toLong() * lhs.height - rhs.width.toLong() * rhs.height)
    }
}
