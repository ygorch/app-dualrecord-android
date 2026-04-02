package com.dualcam.app.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.dualcam.app.core.permissions.RequirePermissions
import com.dualcam.app.domain.DualCamIntent
import com.dualcam.app.domain.LayoutMode

@Composable
fun MainScreen(viewModel: DualCamViewModel = hiltViewModel()) {
    RequirePermissions {
        val state by viewModel.state.collectAsState()

        Box(modifier = Modifier.fillMaxSize()) {

            // Layout logic
            when (state.layoutMode) {
                LayoutMode.PIP -> PiPLayout(viewModel, state.isConcurrentSupported)
                LayoutMode.SPLIT_SCREEN -> SplitScreenLayout(viewModel, state.isConcurrentSupported)
                LayoutMode.STACKED -> StackedLayout(viewModel, state.isConcurrentSupported)
            }

            // Controls Overlay
            ControlsOverlay(
                modifier = Modifier.align(Alignment.BottomCenter),
                isRecording = state.isRecording,
                onRecordClick = {
                    if (state.isRecording) viewModel.onIntent(DualCamIntent.StopRecording)
                    else viewModel.onIntent(DualCamIntent.StartRecording)
                },
                onLayoutChange = { viewModel.onIntent(DualCamIntent.ChangeLayoutMode(it)) }
            )

            // Error Snackbar
            if (state.errorMessage != null) {
                Snackbar(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(16.dp),
                    action = {
                        TextButton(onClick = { viewModel.onIntent(DualCamIntent.DismissError) }) {
                            Text("OK")
                        }
                    }
                ) {
                    Text(state.errorMessage!!)
                }
            }
        }
    }
}

@Composable
fun PiPLayout(viewModel: DualCamViewModel, isConcurrentSupported: Boolean) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Main Camera (16:9) - Full Screen
        CameraSurfaceView(
            modifier = Modifier.fillMaxSize(),
            onSurfaceCreated = { holder ->
                // Delay opening to ensure surface is ready and we get state
                val state = viewModel.state.value
                viewModel.cameraManager.openCamera(
                    cameraId = state.mainCameraId,
                    previewSurface = holder.surface,
                    recordingSurface = viewModel.getMainRecordingSurface(),
                    onOpened = {}
                )
            },
            onSurfaceDestroyed = {
                viewModel.cameraManager.closeCamera(viewModel.state.value.mainCameraId)
            }
        )

        // Sub Camera (9:16) - PiP
        if (isConcurrentSupported) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .width(120.dp)
                    .height(213.dp) // 9:16 aspect ratio
                    .background(Color.Black)
            ) {
                CameraSurfaceView(
                    modifier = Modifier.fillMaxSize(),
                    onSurfaceCreated = { holder ->
                        val state = viewModel.state.value
                        viewModel.cameraManager.openCamera(
                            cameraId = state.subCameraId,
                            previewSurface = holder.surface,
                            recordingSurface = viewModel.getSubRecordingSurface(),
                            onOpened = {}
                        )
                    },
                    onSurfaceDestroyed = {
                        viewModel.cameraManager.closeCamera(viewModel.state.value.subCameraId)
                    }
                )
            }
        }
    }
}

@Composable
fun SplitScreenLayout(viewModel: DualCamViewModel, isConcurrentSupported: Boolean) {
    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            CameraSurfaceView(
                modifier = Modifier.fillMaxSize(),
                onSurfaceCreated = { holder ->
                    viewModel.cameraManager.openCamera(
                        cameraId = viewModel.state.value.mainCameraId,
                        previewSurface = holder.surface,
                        recordingSurface = viewModel.getMainRecordingSurface(),
                        onOpened = {}
                    )
                },
                onSurfaceDestroyed = {
                    viewModel.cameraManager.closeCamera(viewModel.state.value.mainCameraId)
                }
            )
        }
        if (isConcurrentSupported) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                CameraSurfaceView(
                    modifier = Modifier.fillMaxSize(),
                    onSurfaceCreated = { holder ->
                        viewModel.cameraManager.openCamera(
                            cameraId = viewModel.state.value.subCameraId,
                            previewSurface = holder.surface,
                            recordingSurface = viewModel.getSubRecordingSurface(),
                            onOpened = {}
                        )
                    },
                    onSurfaceDestroyed = {
                        viewModel.cameraManager.closeCamera(viewModel.state.value.subCameraId)
                    }
                )
            }
        }
    }
}

@Composable
fun StackedLayout(viewModel: DualCamViewModel, isConcurrentSupported: Boolean) {
    // Similar to PiP for MVP simplicity, just demonstrating dynamic switching
    PiPLayout(viewModel, isConcurrentSupported)
}

@Composable
fun ControlsOverlay(
    modifier: Modifier = Modifier,
    isRecording: Boolean,
    onRecordClick: () -> Unit,
    onLayoutChange: (LayoutMode) -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(onClick = { onLayoutChange(LayoutMode.PIP) }) { Text("PiP") }
            Button(onClick = { onLayoutChange(LayoutMode.SPLIT_SCREEN) }) { Text("Split") }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onRecordClick,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isRecording) Color.Red else Color.DarkGray
            )
        ) {
            Text(if (isRecording) "Stop Recording" else "Start Recording")
        }
    }
}
