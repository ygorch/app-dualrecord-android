package com.dualcam.app.presentation

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

@Composable
fun DualCameraPreviewLayout(
    state: DualCamState,
    viewModel: DualCamViewModel
) {
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp
    val screenWidth = configuration.screenWidthDp.dp

    val isSplitScreen = state.layoutMode == LayoutMode.SPLIT_SCREEN

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {

        // --- 16:9 Slot (Main) ---
        // Animate height to half screen when split
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
                cameraId = state.mainCameraId,
                previewAspectRatio = state.mainCameraAspectRatio,
                targetAspectRatio = 16f / 9f,
                viewModel = viewModel,
                isMain = true
            )
        }

        // --- 9:16 Slot (Sub) ---
        if (state.isConcurrentSupported) {
            // Determine animated modifier parameters
            // Width: Full width when split, PIP width when PIP
            val subWidth by animateDpAsState(
                targetValue = if (isSplitScreen) screenWidth else 120.dp,
                animationSpec = tween(500)
            )
            // Height: Half screen when split, PIP height when PIP
            val subHeight by animateDpAsState(
                targetValue = if (isSplitScreen) screenHeight * 0.5f else 213.dp,
                animationSpec = tween(500)
            )
            // Padding from top/right
            val subPaddingTop by animateDpAsState(
                targetValue = if (isSplitScreen) screenHeight * 0.5f else 32.dp,
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
                        cameraId = state.subCameraId,
                        previewAspectRatio = state.subCameraAspectRatio,
                        targetAspectRatio = if (isSplitScreen) 16f/9f else 9f/16f,
                        viewModel = viewModel,
                        isMain = false
                    )
                }
            }
        }
    }
}

@Composable
fun CameraPreviewContainer(
    cameraId: String,
    previewAspectRatio: Float,
    targetAspectRatio: Float,
    viewModel: DualCamViewModel,
    isMain: Boolean
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
                    viewModel.cameraManager.openCamera(
                        cameraId = cameraId,
                        previewSurface = holder.surface,
                        recordingSurface = if (isMain) viewModel.getMainRecordingSurface() else viewModel.getSubRecordingSurface(),
                        onOpened = {}
                    )
                },
                onSurfaceDestroyed = {
                    viewModel.cameraManager.closeCamera(cameraId)
                }
            )
        }
    }
}
