package com.dualcam.app.presentation

import android.view.Surface
import android.view.SurfaceHolder
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.dualcam.app.domain.DualCamState
import com.dualcam.app.domain.LayoutMode
import com.dualcam.app.core.media.DualCameraCaptureManager.PhysicalStreamConfig

@Composable
fun DualCameraPreviewLayout(
    state: DualCamState,
    viewModel: CameraSelectionViewModel
) {
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp
    val screenWidth = configuration.screenWidthDp.dp

    val isSplitScreen = state.layoutMode == LayoutMode.SPLIT_SCREEN

    // We need to collect both surfaces before opening the logical camera
    var mainSurface by remember { mutableStateOf<Surface?>(null) }
    var subSurface by remember { mutableStateOf<Surface?>(null) }

    // Launch camera when surfaces and configuration changes
    LaunchedEffect(
        mainSurface, subSurface,
        state.mainCameraId, state.subCameraId,
        state.logicalCameraId, state.isSecondarySlotDisabled,
        viewModel.state.value.isRecording // re-trigger on record state change to pass recording surface
    ) {
        val logicalId = state.logicalCameraId
        if (logicalId != null && mainSurface != null) {
            val mainConfig = PhysicalStreamConfig(
                physicalCameraId = state.mainCameraId,
                previewSurface = mainSurface!!,
                recordingSurface = viewModel.getMainRecordingSurface()
            )

            val subConfig = if (!state.isSecondarySlotDisabled && subSurface != null) {
                PhysicalStreamConfig(
                    physicalCameraId = state.subCameraId,
                    previewSurface = subSurface!!,
                    recordingSurface = viewModel.getSubRecordingSurface()
                )
            } else null

            viewModel.cameraManager.openLogicalCamera(
                logicalCameraId = logicalId,
                mainConfig = mainConfig,
                subConfig = subConfig,
                onOpened = {}
            )
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.cameraManager.closeAll()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {

        // --- 16:9 Slot (Main) ---
        val mainHeightFraction by animateFloatAsState(
            targetValue = if (isSplitScreen) 0.5f else 1f,
            animationSpec = tween(500)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(mainHeightFraction)
                .align(Alignment.TopCenter)
                .background(Color.DarkGray)
        ) {
            CameraPreviewContainer(
                previewAspectRatio = state.mainCameraAspectRatio,
                targetAspectRatio = 16f / 9f,
                onSurfaceReady = { mainSurface = it },
                onSurfaceDestroyed = { mainSurface = null }
            )
        }

        // --- 9:16 Slot (Sub) ---
        if (state.isLogicalMultiCameraSupported && !state.isSecondarySlotDisabled) {
            val subWidth by animateDpAsState(
                targetValue = if (isSplitScreen) screenWidth else 120.dp,
                animationSpec = tween(500)
            )
            val subHeight by animateDpAsState(
                targetValue = if (isSplitScreen) screenHeight * 0.5f else 213.dp,
                animationSpec = tween(500)
            )
            val subPaddingTop by animateDpAsState(
                targetValue = if (isSplitScreen) screenHeight * 0.5f else 64.dp, // Moved down slightly for HUD
                animationSpec = tween(500)
            )
            val subPaddingEnd by animateDpAsState(
                targetValue = if (isSplitScreen) 0.dp else 16.dp,
                animationSpec = tween(500)
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(1f)
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = subPaddingTop, end = subPaddingEnd)
                        .width(subWidth)
                        .height(subHeight)
                        .background(Color.Black)
                ) {
                    CameraPreviewContainer(
                        previewAspectRatio = state.subCameraAspectRatio,
                        targetAspectRatio = if (isSplitScreen) 16f/9f else 9f/16f,
                        onSurfaceReady = { subSurface = it },
                        onSurfaceDestroyed = { subSurface = null }
                    )
                }
            }
        }
    }
}

@Composable
fun CameraPreviewContainer(
    previewAspectRatio: Float,
    targetAspectRatio: Float,
    onSurfaceReady: (Surface) -> Unit,
    onSurfaceDestroyed: () -> Unit
) {
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
            .onSizeChanged { containerSize = it }
    ) {
        if (containerSize.width > 0 && containerSize.height > 0) {
            val containerAspectRatio = containerSize.width.toFloat() / containerSize.height.toFloat()

            var scaleX = 1f
            var scaleY = 1f

            val adjustedPreviewAspect = if (targetAspectRatio < 1f) {
                if (previewAspectRatio > 1f) 1f / previewAspectRatio else previewAspectRatio
            } else {
                if (previewAspectRatio < 1f) 1f / previewAspectRatio else previewAspectRatio
            }

            if (adjustedPreviewAspect > containerAspectRatio) {
                scaleX = adjustedPreviewAspect / containerAspectRatio
            } else {
                scaleY = containerAspectRatio / adjustedPreviewAspect
            }

            CameraSurfaceView(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scaleX,
                        scaleY = scaleY
                    ),
                onSurfaceCreated = { holder ->
                    onSurfaceReady(holder.surface)
                },
                onSurfaceDestroyed = {
                    onSurfaceDestroyed()
                }
            )
        }
    }
}
