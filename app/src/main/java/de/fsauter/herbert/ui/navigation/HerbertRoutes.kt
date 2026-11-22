package de.fsauter.herbert.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
data object HomeRoute

@Serializable
data class DeviceDetailRoute(val macAddress: String)
