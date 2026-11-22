package de.fsauter.herbert.ui.home

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.BluetoothDisabled
import androidx.compose.material.icons.rounded.LocationOff
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import de.fsauter.herbert.R
import de.fsauter.herbert.ui.permissions.BlePermissions
import de.fsauter.herbert.ui.theme.HerbertBlue
import de.fsauter.herbert.ui.theme.HerbertBlueLight
import de.fsauter.herbert.ui.theme.HerbertDivider
import de.fsauter.herbert.ui.theme.HerbertTextDark
import de.fsauter.herbert.ui.theme.HerbertTextGray
import de.fsauter.herbert.ui.theme.HerbertTextLight
import de.fsauter.herbert.ui.theme.HerbertToolbarGray
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onDeviceClick: (String) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val snackbarHostState = remember { SnackbarHostState() }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        viewModel.refreshEnvironment(triggerScanIfReady = true)
    }

    val enableBluetoothLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) {
        viewModel.refreshEnvironment(triggerScanIfReady = true)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshEnvironment(triggerScanIfReady = true)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        if (!uiState.isMockModeEnabled && !BlePermissions.hasAllScanPermissions(context)) {
            permissionLauncher.launch(BlePermissions.requiredPermissions())
        } else {
            viewModel.refreshEnvironment(triggerScanIfReady = true)
        }
    }

    val errorMessage = uiState.errorMessageRes?.let { stringResource(it) }
    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            snackbarHostState.showSnackbar(errorMessage)
            viewModel.clearError()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.home_title),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Medium,
                        color = HerbertTextDark,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = HerbertToolbarGray,
                    titleContentColor = HerbertTextDark,
                    actionIconContentColor = HerbertTextDark,
                ),
                actions = {
                    if (uiState.isMockSupported) {
                        FilterChip(
                            selected = uiState.isMockModeEnabled,
                            onClick = { viewModel.toggleMockMode() },
                            label = {
                                Text(
                                    text = stringResource(
                                        if (uiState.isMockModeEnabled) {
                                            R.string.mock_chip_on
                                        } else {
                                            R.string.mock_chip_off
                                        },
                                    ),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = HerbertBlueLight,
                                selectedLabelColor = HerbertBlue,
                            ),
                            modifier = Modifier.padding(end = 4.dp),
                        )
                    }
                    IconButton(onClick = { viewModel.startScan() }) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = stringResource(R.string.home_scan_action),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            RequirementBanner(
                uiState = uiState,
                onRequestPermissions = {
                    permissionLauncher.launch(BlePermissions.requiredPermissions())
                },
                onEnableBluetooth = {
                    if (BlePermissions.hasConnectPermission(context)) {
                        enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                    } else {
                        permissionLauncher.launch(BlePermissions.requiredPermissions())
                    }
                },
                onOpenLocationSettings = {
                    context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                },
            )

            PullToRefreshBox(
                isRefreshing = uiState.isScanning,
                onRefresh = { viewModel.startScan() },
                modifier = Modifier.fillMaxSize(),
            ) {
                if (uiState.devices.isEmpty()) {
                    EmptyScanHint(isScanning = uiState.isScanning)
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 4.dp),
                    ) {
                        items(
                            items = uiState.devices,
                            key = { it.device.macAddress },
                        ) { item ->
                            DeviceListRow(
                                item = item,
                                onClick = { onDeviceClick(item.device.macAddress) },
                            )
                            HorizontalDivider(
                                color = HerbertDivider,
                                modifier = Modifier.padding(start = 102.dp, end = 16.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RequirementBanner(
    uiState: HomeUiState,
    onRequestPermissions: () -> Unit,
    onEnableBluetooth: () -> Unit,
    onOpenLocationSettings: () -> Unit,
) {
    when {
        !uiState.hasPermissions -> {
            ActionBanner(
                icon = Icons.Rounded.BluetoothDisabled,
                message = stringResource(
                    if (uiState.usesModernBlePermission) {
                        R.string.permission_ble_rationale
                    } else {
                        R.string.permission_location_rationale
                    },
                ),
                actionLabel = stringResource(R.string.permission_grant_button),
                onAction = onRequestPermissions,
            )
        }

        !uiState.isBluetoothEnabled -> {
            ActionBanner(
                icon = Icons.Rounded.BluetoothDisabled,
                message = stringResource(R.string.bluetooth_disabled_message),
                actionLabel = stringResource(R.string.bluetooth_enable_button),
                onAction = onEnableBluetooth,
            )
        }

        uiState.needsLocationService -> {
            ActionBanner(
                icon = Icons.Rounded.LocationOff,
                message = stringResource(R.string.location_disabled_message),
                actionLabel = stringResource(R.string.location_settings_button),
                onAction = onOpenLocationSettings,
            )
        }
    }
}

@Composable
private fun ActionBanner(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = HerbertBlueLight),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = HerbertBlue,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = HerbertTextDark,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = onAction,
                colors = ButtonDefaults.textButtonColors(contentColor = HerbertBlue),
            ) {
                Text(text = actionLabel, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun EmptyScanHint(isScanning: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (!isScanning) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.ArrowDownward,
                    contentDescription = null,
                    tint = HerbertTextGray,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.home_pull_to_scan),
                    fontSize = 15.sp,
                    color = HerbertTextGray,
                )
            }
        }
    }
}

@Composable
private fun DeviceListRow(
    item: HomeDeviceUiItem,
    onClick: () -> Unit,
) {
    val photoFile = item.device.photoPath?.let { File(it) }?.takeIf { it.exists() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (photoFile != null) {
            AsyncImage(
                model = photoFile,
                contentDescription = item.device.name,
                contentScale = ContentScale.Crop,
                placeholder = painterResource(R.drawable.ic_herbert_placeholder),
                error = painterResource(R.drawable.ic_herbert_placeholder),
                modifier = Modifier
                    .size(70.dp)
                    .clip(CircleShape),
            )
        } else {
            Image(
                painter = painterResource(R.drawable.ic_herbert_placeholder),
                contentDescription = item.device.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(70.dp)
                    .clip(CircleShape),
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.device.name,
                fontSize = 18.sp,
                fontWeight = FontWeight.Normal,
                color = HerbertTextDark,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.home_device_subtitle, item.displayIndex),
                    fontSize = 12.sp,
                    color = HerbertTextLight,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (item.isNearby) {
                        " · ${stringResource(R.string.home_device_nearby)}"
                    } else {
                        " · ${stringResource(R.string.home_device_saved)}"
                    },
                    fontSize = 12.sp,
                    color = if (item.isNearby) HerbertBlue else HerbertTextGray,
                    maxLines = 1,
                )
            }
        }

        Icon(
            imageVector = Icons.Outlined.Info,
            contentDescription = stringResource(R.string.cd_device_info),
            tint = HerbertTextLight,
        )
    }
}
