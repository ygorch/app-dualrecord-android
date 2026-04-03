package expo.modules.dualcammodule

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.SurfaceTexture
import android.util.Log
import android.view.Surface
import android.view.TextureView
import android.widget.FrameLayout
import expo.modules.kotlin.AppContext
import expo.modules.kotlin.viewevent.EventDispatcher
import expo.modules.kotlin.views.ExpoView
import expo.modules.dualcammodule.core.media.DualCameraManager
import expo.modules.dualcammodule.core.media.MultiplexerEngine
import expo.modules.dualcammodule.core.storage.MediaStoreManager
import expo.modules.dualcammodule.core.media.AudioEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@SuppressLint("ViewConstructor")
class DualcamModuleView(context: Context, appContext: AppContext) : ExpoView(context, appContext) {

  private val onCamerasReady by EventDispatcher()
  private val onError by EventDispatcher()
  private val onRecordingStarted by EventDispatcher()
  private val onRecordingStopped by EventDispatcher()

  private var cameraManager: DualCameraManager? = null
  private var mediaStoreManager: MediaStoreManager? = null
  private var multiplexerEngine: MultiplexerEngine? = null
  private var audioEngine: AudioEngine? = null

  private val mainTextureView: TextureView
  private val subTextureView: TextureView

  private var mainSurface: Surface? = null
  private var subSurface: Surface? = null

  private var isSplitScreen = false
  private var isRecording = false

  init {
    setBackgroundColor(Color.BLACK)

    mediaStoreManager = MediaStoreManager(context)
    multiplexerEngine = MultiplexerEngine()
    audioEngine = AudioEngine()
    cameraManager = DualCameraManager(context)

    mainTextureView = TextureView(context)
    mainTextureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
      override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        mainSurface = Surface(surface)
        checkAndStartCamera()
      }
      override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}
      override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        mainSurface?.release()
        mainSurface = null
        return true
      }
      override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
    }

    subTextureView = TextureView(context)
    subTextureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
      override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        subSurface = Surface(surface)
        checkAndStartCamera()
      }
      override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}
      override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        subSurface?.release()
        subSurface = null
        return true
      }
      override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
    }

    addView(mainTextureView)
    addView(subTextureView)
  }

  fun setSplitScreen(split: Boolean) {
    isSplitScreen = split
    requestLayout()
  }

  override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
    super.onLayout(changed, left, top, right, bottom)
    val width = right - left
    val height = bottom - top

    if (isSplitScreen) {
      mainTextureView.layout(0, 0, width, height / 2)
      subTextureView.layout(0, height / 2, width, height)
    } else {
      mainTextureView.layout(0, 0, width, height)
      // PiP mode
      val subWidth = width / 3
      val subHeight = height / 4
      subTextureView.layout(width - subWidth - 40, 100, width - 40, 100 + subHeight)
      subTextureView.bringToFront()
    }
  }

  private fun checkAndStartCamera() {
    if (mainSurface != null && subSurface != null) {
      startCamera()
    }
  }

  private fun startCamera() {
    val logicalId = cameraManager?.getLogicalBackCameraId()
    if (logicalId == null) {
      onError(mapOf("message" to "No logical multi-camera found"))
      return
    }

    val physicalIds = cameraManager?.getPhysicalBackCameras(logicalId) ?: emptyList()
    if (physicalIds.size < 2) {
      onError(mapOf("message" to "Not enough physical cameras for dual stream"))
      return
    }

    val mainConfig = DualCameraManager.PhysicalStreamConfig(
      physicalCameraId = physicalIds[0],
      previewSurface = mainSurface!!,
      recordingSurface = multiplexerEngine?.getMainInputSurface()
    )

    val subConfig = DualCameraManager.PhysicalStreamConfig(
      physicalCameraId = physicalIds[1],
      previewSurface = subSurface!!,
      recordingSurface = multiplexerEngine?.getSubInputSurface()
    )

    cameraManager?.openLogicalCamera(
      logicalCameraId = logicalId,
      mainConfig = mainConfig,
      subConfig = subConfig,
      onOpened = {
        onCamerasReady(mapOf("status" to "opened"))
      }
    )
  }

  fun startRecording() {
    if (isRecording) return
    isRecording = true

    CoroutineScope(Dispatchers.IO).launch {
        try {
            val mainUri = mediaStoreManager?.createVideoUri("MainCam")
            val subUri = mediaStoreManager?.createVideoUri("SubCam")

            if (mainUri != null && subUri != null) {
                val mainFd = mediaStoreManager?.getFileDescriptor(mainUri, "w")
                val subFd = mediaStoreManager?.getFileDescriptor(subUri, "w")

                if (mainFd != null && subFd != null) {
                    multiplexerEngine?.startRecording(mainFd.fileDescriptor, subFd.fileDescriptor)
                    audioEngine?.startRecording { buffer, size, presentationTimeUs ->
                        multiplexerEngine?.writeAudioSample(buffer, size, presentationTimeUs)
                    }

                    // Restart camera to inject recording surfaces
                    launch(Dispatchers.Main) {
                        startCamera()
                        onRecordingStarted(mapOf("status" to "started"))
                    }
                } else {
                    launch(Dispatchers.Main) {
                       onError(mapOf("message" to "Failed to get file descriptors"))
                       isRecording = false
                    }
                }
            }
        } catch (e: Exception) {
            launch(Dispatchers.Main) {
                onError(mapOf("message" to "Error starting record: ${e.message}"))
                isRecording = false
            }
        }
    }
  }

  fun stopRecording() {
    if (!isRecording) return
    isRecording = false

    CoroutineScope(Dispatchers.IO).launch {
        try {
            audioEngine?.stopRecording()
            multiplexerEngine?.stopRecording()

            launch(Dispatchers.Main) {
                // Restart camera to remove recording surfaces and avoid dropping frames
                startCamera()
                onRecordingStopped(mapOf("status" to "stopped"))
            }
        } catch (e: Exception) {
             launch(Dispatchers.Main) {
                 onError(mapOf("message" to "Error stopping record: ${e.message}"))
             }
        }
    }
  }

  fun stopCamera() {
    cameraManager?.closeAll()
  }
}
