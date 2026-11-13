package com.example.pulsetransit.ui.components

import androidx.compose.ui.graphics.vector.ImageVector
import com.example.pulsetransit.theme.PulseIcons
import com.example.pulsetransit.ui.resources.*

/** Why a fetch failed, as an icon: no connection on the device, or no answer from Entur. */
fun loadFailureIcon(isOnline: Boolean): ImageVector = if (isOnline) PulseIcons.CloudOff else PulseIcons.WifiOff
