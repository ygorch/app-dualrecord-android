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

            // Dynamic layout that handles state and aspect ratios
            DualCameraPreviewLayout(state = state, viewModel = viewModel)

            // Controls Overlay
            ControlsOverlay(
                modifier = Modifier.align(Alignment.BottomCenter),
                isRecording = state.isRecording,
                onRecordClick = {
                    if (state.isRecording) viewModel.onIntent(DualCamIntent.StopRecording)
                    else viewModel.onIntent(DualCamIntent.StartRecording)
                },
                onLayoutChange = { viewModel.onIntent(DualCamIntent.ChangeLayoutMode(it)) },
                availableCameras = state.availableCameras,
                mainCameraId = state.mainCameraId,
                subCameraId = state.subCameraId,
                isSecondarySlotDisabled = state.isSecondarySlotDisabled,
                onMainCameraSelect = { viewModel.onIntent(DualCamIntent.SelectMainCamera(it)) },
                onSubCameraSelect = { viewModel.onIntent(DualCamIntent.SelectSubCamera(it)) }
            )

            // Hardware Limitation Snackbar
            if (state.hardwareLimitationMessage != null) {
                Snackbar(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(16.dp)
                ) {
                    Text(state.hardwareLimitationMessage!!)
                }
            }

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ControlsOverlay(
    modifier: Modifier = Modifier,
    isRecording: Boolean,
    onRecordClick: () -> Unit,
    onLayoutChange: (LayoutMode) -> Unit,
    availableCameras: List<String>,
    mainCameraId: String,
    subCameraId: String,
    isSecondarySlotDisabled: Boolean,
    onMainCameraSelect: (String) -> Unit,
    onSubCameraSelect: (String) -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.5f))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Camera Selectors
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            CameraSelector(
                label = "Main (16:9)",
                selectedCameraId = mainCameraId,
                availableCameras = availableCameras,
                onCameraSelect = onMainCameraSelect
            )

            if (!isSecondarySlotDisabled) {
                CameraSelector(
                    label = "Sub (9:16)",
                    selectedCameraId = subCameraId,
                    availableCameras = availableCameras,
                    onCameraSelect = onSubCameraSelect
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Layout Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(onClick = { onLayoutChange(LayoutMode.PIP) }) { Text("PiP") }
            Button(onClick = { onLayoutChange(LayoutMode.SPLIT_SCREEN) }) { Text("Split") }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Record Button
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraSelector(
    label: String,
    selectedCameraId: String,
    availableCameras: List<String>,
    onCameraSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = Modifier.width(140.dp)
    ) {
        OutlinedTextField(
            value = "Cam $selectedCameraId",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            availableCameras.forEach { id ->
                DropdownMenuItem(
                    text = { Text("Camera $id") },
                    onClick = {
                        onCameraSelect(id)
                        expanded = false
                    }
                )
            }
        }
    }
}
