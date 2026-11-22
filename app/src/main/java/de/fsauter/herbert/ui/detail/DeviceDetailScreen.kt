package de.fsauter.herbert.ui.detail

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import de.fsauter.herbert.R
import de.fsauter.herbert.ble.BleConnectionState
import de.fsauter.herbert.ble.DeviceTime
import de.fsauter.herbert.ble.HerbertProtocol
import de.fsauter.herbert.ble.WaterLevel
import de.fsauter.herbert.ui.theme.HerbertBlue
import de.fsauter.herbert.ui.theme.HerbertConnectedGreen
import de.fsauter.herbert.ui.theme.HerbertDivider
import de.fsauter.herbert.ui.theme.HerbertTextDark
import de.fsauter.herbert.ui.theme.HerbertTextGray
import de.fsauter.herbert.ui.theme.HerbertTextLight
import de.fsauter.herbert.ui.theme.HerbertWarningRed
import java.io.File
import kotlin.math.roundToInt

private enum class TimeTarget {
    SUNRISE,
    SUNSET,
}

@Composable
fun DeviceDetailScreen(
    viewModel: DeviceDetailViewModel,
    onBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var showRenameDialog by remember { mutableStateOf(false) }
    var showPhotoDialog by remember { mutableStateOf(false) }
    var activeTimeTarget by remember { mutableStateOf<TimeTarget?>(null) }
    var pendingCameraFile by remember { mutableStateOf<File?>(null) }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            viewModel.savePhotoFromGallery(uri)
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
    ) { success ->
        val file = pendingCameraFile
        pendingCameraFile = null
        if (success && file != null) {
            viewModel.savePhotoFromCamera(file)
        } else {
            file?.delete()
        }
    }

    val snackbarMessage = uiState.messageRes?.let { stringResource(it) }
    LaunchedEffect(snackbarMessage) {
        if (snackbarMessage != null) {
            snackbarHostState.showSnackbar(snackbarMessage)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.navigationBars,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            HeroHeader(
                deviceName = uiState.device.name,
                photoPath = uiState.device.photoPath,
                canRename = uiState.controlsEnabled,
                onBack = onBack,
                onRenameClick = { showRenameDialog = true },
                onCameraClick = { showPhotoDialog = true },
            )

            AnimatedVisibility(visible = uiState.showWaterLevel) {
                Column {
                    WaterLevelRow(waterLevel = uiState.device.waterLevel)
                    SectionDivider()
                }
            }

            ConnectionStatusRow(
                connectionState = uiState.connectionState,
                isSyncing = uiState.isSyncing,
                onReconnect = { viewModel.connectAndSync() },
            )

            SectionDivider()

            BrightnessSection(
                brightness = uiState.displayedBrightness,
                enabled = uiState.controlsEnabled,
                onValueChange = { viewModel.onBrightnessDragging(it) },
                onValueChangeFinished = { viewModel.commitBrightness() },
            )

            SectionDivider()

            LightModeSection(
                uiState = uiState,
                onAutoChanged = { enabled ->
                    viewModel.updateLightMode { it.copy(auto = enabled) }
                },
                onSmoothChanged = { enabled ->
                    viewModel.updateLightMode { it.copy(smoothChange = enabled) }
                },
                onVacationChanged = { enabled ->
                    viewModel.updateLightMode { it.copy(holiday = enabled) }
                },
                onManualLightChanged = { enabled ->
                    viewModel.updateLightMode { it.copy(manualOn = enabled) }
                },
                onSunriseClick = { activeTimeTarget = TimeTarget.SUNRISE },
                onSunsetClick = { activeTimeTarget = TimeTarget.SUNSET },
            )
        }
    }

    if (showRenameDialog) {
        RenameDeviceDialog(
            initialName = uiState.device.name,
            onDismiss = { showRenameDialog = false },
            onConfirm = { newName ->
                showRenameDialog = false
                viewModel.renameDevice(newName)
            },
        )
    }

    if (showPhotoDialog) {
        PhotoSourceDialog(
            hasExistingPhoto = uiState.device.photoPath != null,
            onDismiss = { showPhotoDialog = false },
            onTakePhoto = {
                showPhotoDialog = false
                val (uri, file) = viewModel.createCameraCapture()
                pendingCameraFile = file
                cameraLauncher.launch(uri)
            },
            onChooseGallery = {
                showPhotoDialog = false
                galleryLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            },
            onRemovePhoto = {
                showPhotoDialog = false
                viewModel.removePhoto()
            },
        )
    }

    val target = activeTimeTarget
    if (target != null) {
        val initialTime = when (target) {
            TimeTarget.SUNRISE -> uiState.device.sunriseTime
            TimeTarget.SUNSET -> uiState.device.sunsetTime
        }
        val title = when (target) {
            TimeTarget.SUNRISE -> stringResource(R.string.control_sunrise_label)
            TimeTarget.SUNSET -> stringResource(R.string.control_sunset_label)
        }
        HerbertTimePickerDialog(
            title = title,
            initialTime = initialTime,
            onDismiss = { activeTimeTarget = null },
            onConfirm = { hour, minute ->
                activeTimeTarget = null
                when (target) {
                    TimeTarget.SUNRISE -> viewModel.setSunriseTime(hour, minute)
                    TimeTarget.SUNSET -> viewModel.setSunsetTime(hour, minute)
                }
            },
        )
    }
}

@Composable
private fun HeroHeader(
    deviceName: String,
    photoPath: String?,
    canRename: Boolean,
    onBack: () -> Unit,
    onRenameClick: () -> Unit,
    onCameraClick: () -> Unit,
) {
    val photoFile = photoPath?.let { File(it) }?.takeIf { it.exists() }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(3f / 2f),
    ) {
        if (photoFile != null) {
            AsyncImage(
                model = photoFile,
                contentDescription = deviceName,
                contentScale = ContentScale.Crop,
                placeholder = painterResource(R.drawable.default_herbert_banner),
                error = painterResource(R.drawable.default_herbert_banner),
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Image(
                painter = painterResource(R.drawable.default_herbert_banner),
                contentDescription = deviceName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.0f to Color.Black.copy(alpha = 0.58f),
                        0.45f to Color.Black.copy(alpha = 0.14f),
                        1.0f to Color.Black.copy(alpha = 0.28f),
                    ),
                ),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.cd_back),
                    tint = Color.White,
                )
            }

            Row(
                modifier = Modifier
                    .weight(1f)
                    .clickable(enabled = canRename, onClick = onRenameClick)
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = deviceName,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Rounded.Edit,
                    contentDescription = stringResource(R.string.cd_edit_name),
                    tint = if (canRename) Color.White else Color.White.copy(alpha = 0.55f),
                    modifier = Modifier.size(18.dp),
                )
            }

            IconButton(onClick = onCameraClick) {
                Icon(
                    imageVector = Icons.Rounded.PhotoCamera,
                    contentDescription = stringResource(R.string.cd_change_photo),
                    tint = Color.White,
                )
            }
        }
    }
}

@Composable
private fun WaterLevelRow(waterLevel: WaterLevel) {
    val tint = if (waterLevel.isWarning) HerbertWarningRed else HerbertBlue
    val textRes = when (waterLevel) {
        WaterLevel.EMPTY -> R.string.water_level_empty
        WaterLevel.LOW -> R.string.water_level_low
        WaterLevel.OK -> R.string.water_level_ok
        WaterLevel.FULL -> R.string.water_level_full
        WaterLevel.UNKNOWN -> R.string.water_level_unknown
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.WaterDrop,
            contentDescription = stringResource(R.string.cd_water_level),
            tint = tint,
            modifier = Modifier.size(22.dp),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = stringResource(textRes),
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ConnectionStatusRow(
    connectionState: BleConnectionState,
    isSyncing: Boolean,
    onReconnect: () -> Unit,
) {
    val statusText = when (connectionState) {
        BleConnectionState.CONNECTED -> stringResource(R.string.connection_connected)
        BleConnectionState.CONNECTING -> stringResource(R.string.connection_connecting)
        BleConnectionState.DISCONNECTED -> stringResource(R.string.connection_disconnected)
    }
    val statusColor = when (connectionState) {
        BleConnectionState.CONNECTED -> HerbertConnectedGreen
        BleConnectionState.CONNECTING,
        BleConnectionState.DISCONNECTED,
        -> HerbertBlue
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    enabled = connectionState == BleConnectionState.DISCONNECTED,
                    onClick = onReconnect,
                )
                .padding(horizontal = 16.dp, vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = statusText,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.6.sp,
                color = statusColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (connectionState == BleConnectionState.CONNECTING || isSyncing) {
            LinearProgressIndicator(
                color = HerbertBlue,
                trackColor = HerbertBlue.copy(alpha = 0.16f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
            )
        }
    }
}

@Composable
private fun BrightnessSection(
    brightness: Int,
    enabled: Boolean,
    onValueChange: (Int) -> Unit,
    onValueChangeFinished: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .alpha(if (enabled) 1f else 0.45f),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.control_brightness_label),
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = HerbertTextDark,
            )
            Text(
                text = stringResource(R.string.control_brightness_percent, brightness),
                fontSize = 14.sp,
                color = HerbertTextLight,
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Slider(
            value = brightness.toFloat(),
            onValueChange = { onValueChange(it.roundToInt()) },
            onValueChangeFinished = onValueChangeFinished,
            valueRange = 0f..HerbertProtocol.MAX_BRIGHTNESS.toFloat(),
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = HerbertBlue,
                activeTrackColor = HerbertBlue,
            ),
        )
    }
}

@Composable
private fun LightModeSection(
    uiState: DeviceDetailUiState,
    onAutoChanged: (Boolean) -> Unit,
    onSmoothChanged: (Boolean) -> Unit,
    onVacationChanged: (Boolean) -> Unit,
    onManualLightChanged: (Boolean) -> Unit,
    onSunriseClick: () -> Unit,
    onSunsetClick: () -> Unit,
) {
    val enabled = uiState.controlsEnabled
    val mode = uiState.device.lightMode

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .alpha(if (enabled) 1f else 0.45f),
    ) {
        Text(
            text = stringResource(R.string.control_lighting_section),
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = HerbertTextDark,
        )
        Spacer(modifier = Modifier.height(10.dp))

        SwitchRow(
            label = stringResource(R.string.control_schedule_enabled),
            checked = mode.auto,
            enabled = enabled,
            onCheckedChange = onAutoChanged,
        )

        if (mode.auto) {
            SwitchRow(
                label = stringResource(R.string.control_smooth_fade),
                checked = mode.smoothChange,
                enabled = enabled,
                onCheckedChange = onSmoothChanged,
            )

            TimeRow(
                label = stringResource(R.string.control_sunrise_label),
                time = uiState.device.sunriseTime,
                enabled = enabled,
                onClick = onSunriseClick,
            )

            TimeRow(
                label = stringResource(R.string.control_sunset_label),
                time = uiState.device.sunsetTime,
                enabled = enabled,
                onClick = onSunsetClick,
            )

            SwitchRow(
                label = stringResource(R.string.control_vacation_mode),
                checked = mode.holiday,
                enabled = enabled,
                onCheckedChange = onVacationChanged,
            )
        } else {
            SwitchRow(
                label = stringResource(R.string.control_manual_light),
                checked = mode.manualOn,
                enabled = enabled,
                onCheckedChange = onManualLightChanged,
            )
        }
    }
}

@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontSize = 15.sp,
            color = HerbertTextLight,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp),
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = HerbertBlue,
            ),
        )
    }
}

@Composable
private fun TimeRow(
    label: String,
    time: DeviceTime,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontSize = 15.sp,
            color = HerbertTextLight,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = stringResource(R.string.format_time_24h, time.hour, time.minute),
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = HerbertTextDark,
        )
        Spacer(modifier = Modifier.width(6.dp))
        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = HerbertTextGray,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun SectionDivider() {
    HorizontalDivider(
        color = HerbertDivider,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
}

@Composable
private fun RenameDeviceDialog(
    initialName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember(initialName) { mutableStateOf(initialName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.cd_edit_name)) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { updated ->
                        if (updated.length <= HerbertProtocol.MAX_NAME_LENGTH) {
                            text = updated
                        }
                    },
                    singleLine = true,
                    label = { Text(stringResource(R.string.rename_device_hint)) },
                    supportingText = {
                        Text("${text.length} / ${HerbertProtocol.MAX_NAME_LENGTH}")
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(text) },
                enabled = text.isNotBlank(),
            ) {
                Text(stringResource(R.string.action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
private fun PhotoSourceDialog(
    hasExistingPhoto: Boolean,
    onDismiss: () -> Unit,
    onTakePhoto: () -> Unit,
    onChooseGallery: () -> Unit,
    onRemovePhoto: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.photo_picker_title)) },
        text = {
            Column {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.photo_source_camera)) },
                    leadingContent = {
                        Icon(Icons.Rounded.PhotoCamera, contentDescription = null)
                    },
                    modifier = Modifier.clickable(onClick = onTakePhoto),
                )
                ListItem(
                    headlineContent = { Text(stringResource(R.string.photo_source_gallery)) },
                    leadingContent = {
                        Icon(Icons.Rounded.PhotoLibrary, contentDescription = null)
                    },
                    modifier = Modifier.clickable(onClick = onChooseGallery),
                )
                if (hasExistingPhoto) {
                    ListItem(
                        headlineContent = {
                            Text(
                                text = stringResource(R.string.photo_source_remove),
                                color = HerbertWarningRed,
                            )
                        },
                        leadingContent = {
                            Icon(
                                Icons.Rounded.DeleteOutline,
                                contentDescription = null,
                                tint = HerbertWarningRed,
                            )
                        },
                        modifier = Modifier.clickable(onClick = onRemovePhoto),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HerbertTimePickerDialog(
    title: String,
    initialTime: DeviceTime,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int) -> Unit,
) {
    val state = rememberTimePickerState(
        initialHour = initialTime.hour,
        initialMinute = initialTime.minute,
        is24Hour = true,
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title) },
        text = {
            TimePicker(state = state)
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour, state.minute) }) {
                Text(stringResource(R.string.action_set_time))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}
