package com.dualcam.app.presentation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.dualcam.app.core.permissions.RequirePermissions
import com.dualcam.app.domain.DualCamIntent
import com.dualcam.app.domain.LayoutMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PremiumCameraScreen(viewModel: CameraSelectionViewModel = hiltViewModel()) {
    RequirePermissions {
        val state by viewModel.state.collectAsState()

        Scaffold(
            containerColor = Color.Transparent,
            modifier = Modifier.fillMaxSize(),
            snackbarHost = {
                if (state.hardwareLimitationMessage != null) {
                    Snackbar(
                        modifier = Modifier.padding(16.dp),
                        action = {
                            TextButton(onClick = { viewModel.onIntent(DualCamIntent.DismissError) }) {
                                Text("OK")
                            }
                        }
                    ) {
                        Text(state.hardwareLimitationMessage!!)
                    }
                } else if (state.errorMessage != null) {
                    Snackbar(
                        modifier = Modifier.padding(16.dp),
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
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                // Background Preview (Takes full screen)
                DualCameraPreviewLayout(state = state, viewModel = viewModel)

                // Premium HUD Overlay
                PremiumCameraHUD(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(innerPadding)
                        .padding(bottom = 32.dp, start = 16.dp, end = 16.dp),
                    isRecording = state.isRecording,
                    onRecordClick = {
                        if (state.isRecording) viewModel.onIntent(DualCamIntent.StopRecording)
                        else viewModel.onIntent(DualCamIntent.StartRecording)
                    },
                    onLayoutChange = { viewModel.onIntent(DualCamIntent.ChangeLayoutMode(it)) },
                    availablePhysicalCameras = state.availablePhysicalCameras,
                    mainCameraId = state.mainCameraId,
                    subCameraId = state.subCameraId,
                    isSecondarySlotDisabled = state.isSecondarySlotDisabled,
                    onMainCameraSelect = { viewModel.onIntent(DualCamIntent.SelectMainCamera(it)) },
                    onSubCameraSelect = { viewModel.onIntent(DualCamIntent.SelectSubCamera(it)) }
                )
            }
        }
    }
}

@Composable
fun PremiumCameraHUD(
    modifier: Modifier = Modifier,
    isRecording: Boolean,
    onRecordClick: () -> Unit,
    onLayoutChange: (LayoutMode) -> Unit,
    availablePhysicalCameras: List<String>,
    mainCameraId: String,
    subCameraId: String,
    isSecondarySlotDisabled: Boolean,
    onMainCameraSelect: (String) -> Unit,
    onSubCameraSelect: (String) -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Color.Black.copy(alpha = 0.6f))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Lens Selection (Segmented Pills)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            LensSelector(
                label = "Main 16:9",
                selectedId = mainCameraId,
                availableIds = availablePhysicalCameras,
                onSelect = onMainCameraSelect,
                disabledIds = listOf(subCameraId) // Mutual Exclusion UI hint
            )

            if (!isSecondarySlotDisabled) {
                LensSelector(
                    label = "Sub 9:16",
                    selectedId = subCameraId,
                    availableIds = availablePhysicalCameras,
                    onSelect = onSubCameraSelect,
                    disabledIds = listOf(mainCameraId) // Mutual Exclusion UI hint
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Bottom Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Layout toggles
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.DarkGray.copy(alpha = 0.5f))
            ) {
                Text(
                    text = "PiP",
                    modifier = Modifier
                        .clickable { onLayoutChange(LayoutMode.PIP) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Split",
                    modifier = Modifier
                        .clickable { onLayoutChange(LayoutMode.SPLIT_SCREEN) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }

            // Animated Record Button
            RecordButton(isRecording = isRecording, onClick = onRecordClick)

            // Spacer to balance layout
            Spacer(modifier = Modifier.width(64.dp))
        }
    }
}

@Composable
fun LensSelector(
    label: String,
    selectedId: String,
    availableIds: List<String>,
    onSelect: (String) -> Unit,
    disabledIds: List<String>
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, color = Color.LightGray, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(Color.DarkGray.copy(alpha = 0.5f))
                .padding(2.dp)
        ) {
            availableIds.forEach { id ->
                val isSelected = id == selectedId
                val isDisabled = id in disabledIds && !isSelected
                val backgroundColor by animateColorAsState(
                    targetValue = if (isSelected) Color.White else Color.Transparent,
                    animationSpec = tween(300)
                )
                val textColor by animateColorAsState(
                    targetValue = if (isSelected) Color.Black else if (isDisabled) Color.Gray else Color.White,
                    animationSpec = tween(300)
                )

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(backgroundColor)
                        .clickable(enabled = !isDisabled) { onSelect(id) }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    // Map physical IDs to dummy labels for premium feel
                    // In a real app, map based on focal lengths from CameraCharacteristics
                    val lensLabel = when(id) {
                        "2" -> "0.6x"
                        "0" -> "1x"
                        "6" -> "3x"
                        else -> "${id}x"
                    }
                    Text(
                        text = lensLabel,
                        color = textColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

@Composable
fun RecordButton(isRecording: Boolean, onClick: () -> Unit) {
    val size by animateDpAsState(targetValue = if (isRecording) 32.dp else 48.dp, animationSpec = tween(300))
    val cornerRadius by animateDpAsState(targetValue = if (isRecording) 8.dp else 24.dp, animationSpec = tween(300))

    Box(
        modifier = Modifier
            .size(64.dp)
            .border(4.dp, Color.White, CircleShape)
            .padding(8.dp)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(cornerRadius))
                .background(Color.Red)
        )
    }
}
