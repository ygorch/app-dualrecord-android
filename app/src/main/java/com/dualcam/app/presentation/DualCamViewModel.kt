package com.dualcam.app.presentation

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dualcam.app.core.media.AudioCodecConfig
import com.dualcam.app.core.media.AudioEngine
import com.dualcam.app.core.media.DualCameraManager
import com.dualcam.app.core.media.MultiplexerEngine
import com.dualcam.app.core.media.VideoCodecConfig
import com.dualcam.app.core.storage.MediaStoreManager
import com.dualcam.app.domain.DualCamIntent
import com.dualcam.app.domain.DualCamState
import com.dualcam.app.domain.LayoutMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DualCamViewModel @Inject constructor(
    val cameraManager: DualCameraManager, // Exposing for UI to bind Surface (simplified for MVP)
    private val audioEngine: AudioEngine,
    private val mediaStoreManager: MediaStoreManager
) : ViewModel() {

    private val _state = MutableStateFlow(DualCamState())
    val state: StateFlow<DualCamState> = _state.asStateFlow()

    private var muxerMain: MultiplexerEngine? = null
    private var muxerSub: MultiplexerEngine? = null

    private var mainVideoUri: Uri? = null
    private var subVideoUri: Uri? = null

    private var audioLoopJob: Job? = null
    private var muxerDrainJob: Job? = null

    init {
        val logicalId = cameraManager.getLogicalBackCameraId()
        val physicalCameras = logicalId?.let { cameraManager.getPhysicalBackCameras(it) } ?: emptyList()

        val defaultMainId = physicalCameras.firstOrNull() ?: "0"
        val defaultSubId = physicalCameras.filter { it != defaultMainId }.firstOrNull() ?: defaultMainId

        viewModelScope.launch {
            cameraManager.isLogicalMultiCameraSupported.collect { supported ->
                val needsFallback = !supported || physicalCameras.size < 2

                _state.update {
                    it.copy(
                        isLogicalMultiCameraSupported = supported,
                        logicalCameraId = logicalId,
                        availablePhysicalCameras = physicalCameras,
                        isSecondarySlotDisabled = needsFallback,
                        hardwareLimitationMessage = if (needsFallback) "Hardware Limit: Native dual logical back-camera recording not supported." else null,
                        mainCameraId = defaultMainId,
                        subCameraId = defaultSubId,
                        mainCameraAspectRatio = if (physicalCameras.isNotEmpty()) cameraManager.getPreviewAspectRatio(defaultMainId) else 16f/9f,
                        subCameraAspectRatio = if (physicalCameras.isNotEmpty()) cameraManager.getPreviewAspectRatio(defaultSubId) else 16f/9f
                    )
                }
            }
        }
    }

    fun onIntent(intent: DualCamIntent) {
        when (intent) {
            is DualCamIntent.ChangeLayoutMode -> _state.update { it.copy(layoutMode = intent.mode) }
            is DualCamIntent.ChangeResolution -> _state.update { it.copy(selectedResolution = intent.resolution) }
            is DualCamIntent.ChangeFps -> _state.update { it.copy(selectedFps = intent.fps) }
            is DualCamIntent.SelectMainCamera -> handleSelectMainCamera(intent.id)
            is DualCamIntent.SelectSubCamera -> handleSelectSubCamera(intent.id)
            is DualCamIntent.StartRecording -> startRecording()
            is DualCamIntent.StopRecording -> stopRecording()
            is DualCamIntent.DismissError -> _state.update { it.copy(errorMessage = null) }
        }
    }


    private fun handleSelectMainCamera(newMainId: String) {
        val currentState = _state.value
        if (newMainId == currentState.mainCameraId) return

        val newAspectRatio = cameraManager.getPreviewAspectRatio(newMainId)

        // Mutual exclusion: if new main is same as current sub, change sub
        if (newMainId == currentState.subCameraId) {
            val availableForSub = currentState.availablePhysicalCameras.filter { it != newMainId }
            val newSubId = availableForSub.firstOrNull() ?: newMainId
            val newSubAspectRatio = if (newSubId == newMainId) newAspectRatio else cameraManager.getPreviewAspectRatio(newSubId)

            _state.update {
                it.copy(
                    mainCameraId = newMainId,
                    mainCameraAspectRatio = newAspectRatio,
                    subCameraId = newSubId,
                    subCameraAspectRatio = newSubAspectRatio
                )
            }
        } else {
            _state.update {
                it.copy(
                    mainCameraId = newMainId,
                    mainCameraAspectRatio = newAspectRatio
                )
            }
        }
    }

    private fun handleSelectSubCamera(newSubId: String) {
        val currentState = _state.value
        if (newSubId == currentState.subCameraId) return

        val newAspectRatio = cameraManager.getPreviewAspectRatio(newSubId)

        // Mutual exclusion: if new sub is same as current main, change main
        if (newSubId == currentState.mainCameraId) {
            val availableForMain = currentState.availablePhysicalCameras.filter { it != newSubId }
            val newMainId = availableForMain.firstOrNull() ?: newSubId
            val newMainAspectRatio = if (newMainId == newSubId) newAspectRatio else cameraManager.getPreviewAspectRatio(newMainId)

            _state.update {
                it.copy(
                    subCameraId = newSubId,
                    subCameraAspectRatio = newAspectRatio,
                    mainCameraId = newMainId,
                    mainCameraAspectRatio = newMainAspectRatio
                )
            }
        } else {
            _state.update {
                it.copy(
                    subCameraId = newSubId,
                    subCameraAspectRatio = newAspectRatio
                )
            }
        }
    }

    private fun startRecording() {
        val currentState = _state.value
        if (currentState.isRecording) return

        viewModelScope.launch {
            try {
                // Determine resolution
                val (width, height) = when (currentState.selectedResolution) {
                    "720p" -> Pair(1280, 720)
                    "1080p" -> Pair(1920, 1080)
                    "1440p" -> Pair(2560, 1440)
                    "2160p" -> Pair(3840, 2160)
                    else -> Pair(1920, 1080)
                }
                val fps = currentState.selectedFps

                val audioConfig = AudioCodecConfig()

                // MAIN: 16:9
                val mainVideoConfig = VideoCodecConfig(width, height, fps)
                val mainFdPair = mediaStoreManager.createVideoFileDescriptor("main_16x9_${System.currentTimeMillis()}.mp4")
                if (mainFdPair != null) {
                    mainVideoUri = mainFdPair.first
                    muxerMain = MultiplexerEngine(mainVideoConfig, audioConfig, mainFdPair.second, isPortrait = false)
                    muxerMain?.prepare()
                }

                // SUB: 9:16 (Fallback logic if not concurrent or no secondary slot)
                if (currentState.isLogicalMultiCameraSupported && !currentState.isSecondarySlotDisabled) {
                    // Note: for 9:16 we usually swap width/height in config or use orientation hint.
                    // The MultiplexerEngine handles orientationHint, so config is same physical dimensions.
                    val subVideoConfig = VideoCodecConfig(width, height, fps)
                    val subFdPair = mediaStoreManager.createVideoFileDescriptor("sub_9x16_${System.currentTimeMillis()}.mp4")
                    if (subFdPair != null) {
                        subVideoUri = subFdPair.first
                        muxerSub = MultiplexerEngine(subVideoConfig, audioConfig, subFdPair.second, isPortrait = true)
                        muxerSub?.prepare()
                    }
                }

                // Start Encoders
                muxerMain?.start()
                muxerSub?.start()

                // Start Audio and distribute to both muxers
                audioEngine.startRecording(audioConfig) { buffer, size, pts ->
                    // Multiplexing same PCM bytes to both MP4s synchronously
                    muxerMain?.encodeAudio(buffer, size, pts)
                    muxerSub?.encodeAudio(buffer, size, pts)
                }

                audioLoopJob = launch { audioEngine.readAudioLoop() }

                muxerDrainJob = launch {
                    launch { muxerMain?.drainEncoders() }
                    launch { muxerSub?.drainEncoders() }
                }

                _state.update { it.copy(isRecording = true) }
            } catch (e: Exception) {
                Log.e("ViewModel", "Failed to start recording", e)
                _state.update { it.copy(errorMessage = "Erro ao iniciar gravação.") }
                stopRecording()
            }
        }
    }

    private fun stopRecording() {
        audioEngine.stopRecording()
        audioLoopJob?.cancel()

        muxerMain?.stop()
        muxerSub?.stop()
        muxerDrainJob?.cancel()

        mainVideoUri?.let { mediaStoreManager.markVideoReady(it) }
        subVideoUri?.let { mediaStoreManager.markVideoReady(it) }

        _state.update { it.copy(isRecording = false) }
    }

    override fun onCleared() {
        super.onCleared()
        cameraManager.closeAll()
        if (_state.value.isRecording) stopRecording()
    }

    // Helper for UI to get the recording surface from the Multiplexer
    fun getMainRecordingSurface() = muxerMain?.inputSurface
    fun getSubRecordingSurface() = muxerSub?.inputSurface
}
