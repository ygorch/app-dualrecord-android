package com.dualcam.app.core.media

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
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
import java.util.concurrent.Executor
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages Camera2 API for concurrent dual-streaming via Logical Multi-Camera.
 */
@Singleton
class DualCameraManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    private val _isLogicalMultiCameraSupported = MutableStateFlow(false)
    val isLogicalMultiCameraSupported: StateFlow<Boolean> = _isLogicalMultiCameraSupported

    private var activeLogicalCamera: CameraDevice? = null
    private var activeSession: CameraCaptureSession? = null

    private val backgroundThread = HandlerThread("CameraBackground").apply { start() }
    private val backgroundHandler = Handler(backgroundThread.looper)

    private val executor = Executor { command -> backgroundHandler.post(command) }

    init {
        checkLogicalMultiCameraSupport()
    }

    private fun checkLogicalMultiCameraSupport() {
        var isSupported = false
        try {
            for (id in cameraManager.cameraIdList) {
                val characteristics = cameraManager.getCameraCharacteristics(id)
                val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                if (facing == CameraCharacteristics.LENS_FACING_BACK) {
                    val capabilities = characteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
                    val isLogical = capabilities?.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA) == true
                    if (isLogical) {
                        val physicalIds = characteristics.physicalCameraIds
                        if (physicalIds.size >= 2) {
                            isSupported = true
                            break
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("CameraManager", "Error checking logical multi camera", e)
        }

        _isLogicalMultiCameraSupported.value = isSupported
    }

    data class PhysicalStreamConfig(
        val physicalCameraId: String,
        val previewSurface: Surface,
        val recordingSurface: Surface?
    )

    /**
     * Opens a logical camera and sets up physical streams.
     */
    @SuppressLint("MissingPermission") // Caller (UI) must ensure permissions
    fun openLogicalCamera(
        logicalCameraId: String,
        mainConfig: PhysicalStreamConfig,
        subConfig: PhysicalStreamConfig?,
        onOpened: () -> Unit
    ) {
        closeAll() // Ensure previous sessions are closed

        try {
            cameraManager.openCamera(logicalCameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    activeLogicalCamera = camera
                    startLogicalPreviewAndRecording(camera, mainConfig, subConfig)
                    onOpened()
                }

                override fun onDisconnected(camera: CameraDevice) {
                    closeAll()
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    Log.e("CameraManager", "Error opening camera $logicalCameraId: $error")
                    closeAll()
                }
            }, backgroundHandler)
        } catch (e: Exception) {
            Log.e("CameraManager", "Exception opening camera $logicalCameraId", e)
        }
    }

    private fun startLogicalPreviewAndRecording(
        camera: CameraDevice,
        mainConfig: PhysicalStreamConfig,
        subConfig: PhysicalStreamConfig?
    ) {
        try {
            val outputConfigs = mutableListOf<OutputConfiguration>()
            val captureRequest = camera.createCaptureRequest(CameraDevice.TEMPLATE_RECORD)

            // Setup Main Physical Stream
            val mainPreviewOut = OutputConfiguration(mainConfig.previewSurface)
            mainPreviewOut.setPhysicalCameraId(mainConfig.physicalCameraId)
            outputConfigs.add(mainPreviewOut)
            captureRequest.addTarget(mainConfig.previewSurface)

            if (mainConfig.recordingSurface != null) {
                val mainRecordOut = OutputConfiguration(mainConfig.recordingSurface)
                mainRecordOut.setPhysicalCameraId(mainConfig.physicalCameraId)
                outputConfigs.add(mainRecordOut)
                captureRequest.addTarget(mainConfig.recordingSurface)
            }

            // Setup Sub Physical Stream
            if (subConfig != null) {
                val subPreviewOut = OutputConfiguration(subConfig.previewSurface)
                subPreviewOut.setPhysicalCameraId(subConfig.physicalCameraId)
                outputConfigs.add(subPreviewOut)
                captureRequest.addTarget(subConfig.previewSurface)

                if (subConfig.recordingSurface != null) {
                    val subRecordOut = OutputConfiguration(subConfig.recordingSurface)
                    subRecordOut.setPhysicalCameraId(subConfig.physicalCameraId)
                    outputConfigs.add(subRecordOut)
                    captureRequest.addTarget(subConfig.recordingSurface)
                }
            }

            captureRequest.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)

            val sessionConfig = SessionConfiguration(
                SessionConfiguration.SESSION_REGULAR,
                outputConfigs,
                executor,
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        activeSession = session
                        session.setRepeatingRequest(captureRequest.build(), null, backgroundHandler)
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        Log.e("CameraManager", "Failed to configure capture session for ${camera.id}")
                    }
                }
            )

            camera.createCaptureSession(sessionConfig)

        } catch (e: Exception) {
            Log.e("CameraManager", "Exception creating logical capture session", e)
        }
    }

    fun closeAll() {
        activeSession?.close()
        activeSession = null
        activeLogicalCamera?.close()
        activeLogicalCamera = null
    }

    fun getLogicalBackCameraId(): String? {
        try {
            for (id in cameraManager.cameraIdList) {
                val characteristics = cameraManager.getCameraCharacteristics(id)
                val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                if (facing == CameraCharacteristics.LENS_FACING_BACK) {
                    val capabilities = characteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
                    val isLogical = capabilities?.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA) == true
                    if (isLogical) {
                        return id
                    }
                }
            }
        } catch (e: Exception) {
             Log.e("CameraManager", "Error getting logical camera id", e)
        }
        return null
    }

    fun getPhysicalBackCameras(logicalId: String): List<String> {
        val physicalCameras = mutableListOf<String>()
        try {
            val characteristics = cameraManager.getCameraCharacteristics(logicalId)
            val physicalIds = characteristics.physicalCameraIds
            physicalCameras.addAll(physicalIds)
        } catch (e: Exception) {
            Log.e("CameraManager", "Error getting physical cameras", e)
        }
        return physicalCameras
    }

    fun getPreviewAspectRatio(cameraId: String): Float {
        try {
            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
            val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            if (map != null) {
                val sizes = map.getOutputSizes(android.graphics.SurfaceTexture::class.java)
                if (sizes != null && sizes.isNotEmpty()) {
                    val largest = Collections.max(sizes.toList(), CompareSizesByArea())
                    return largest.width.toFloat() / largest.height.toFloat()
                }
            }
        } catch (e: Exception) {
            Log.e("CameraManager", "Error getting preview aspect ratio", e)
        }
        return 16f / 9f // Default to 16:9 instead of 4:3 for video apps
    }
}

internal class CompareSizesByArea : Comparator<Size> {
    override fun compare(lhs: Size, rhs: Size): Int {
        // We cast here to ensure the multiplications won't overflow
        return java.lang.Long.signum(lhs.width.toLong() * lhs.height - rhs.width.toLong() * rhs.height)
    }
}
