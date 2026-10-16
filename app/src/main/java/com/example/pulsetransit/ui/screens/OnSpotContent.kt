package com.example.pulsetransit.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.pulsetransit.R
import com.example.pulsetransit.data.StopPlace
import com.example.pulsetransit.data.TripInfo
import com.example.pulsetransit.data.autoTripOptionCount
import com.example.pulsetransit.data.tripArrival
import com.example.pulsetransit.data.visibleTripOptionCount
import com.example.pulsetransit.platform.LocationAccuracy
import com.example.pulsetransit.ui.components.RefreshingIndicator
import com.example.pulsetransit.ui.components.StopBoardPanel
import com.example.pulsetransit.ui.viewmodel.NearbyStop
import com.example.pulsetransit.ui.viewmodel.OnSpotLocation
import com.example.pulsetransit.ui.viewmodel.StopBoardViewModel

// Heights the auto route count is worked out from: one alternative's row, and a destination
// card's title and padding. Both grow with the font scale.
private const val TRIP_ROW_HEIGHT_DP = 64f
private const val DESTINATION_HEADER_HEIGHT_DP = 72f

private const val METRES_PER_KILOMETRE = 1000
private val LOCATION_PERMISSIONS = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

/**
 * The on-spot board below the header: route alternatives to each destination from where the
 * device is, taking most of the space, and the nearest stop's departures beside or below them.
 */
@Composable
fun OnSpotContent(
    uiState: StopBoardViewModel.UiState,
    viewModel: StopBoardViewModel,
    isLandscape: Boolean,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var askedByUser by remember { mutableStateOf(false) }
    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
            if (grants.values.any { it }) {
                viewModel.retryLocation()
            } else if (askedByUser) {
                // Android stops showing the prompt once the user has refused it for good; the
                // app's system settings page is then the only place access can be granted.
                val activity = context.findActivity()
                if (activity != null && !ActivityCompat.shouldShowRequestPermissionRationale(activity, LOCATION_PERMISSIONS[0])) {
                    context.openAppSettings()
                }
            }
        }

    LaunchedEffect(Unit) {
        if (!context.hasLocationPermission()) permissionLauncher.launch(LOCATION_PERMISSIONS)
    }

    val onRetryLocation = {
        if (context.hasLocationPermission()) {
            viewModel.retryLocation()
        } else {
            askedByUser = true
            permissionLauncher.launch(LOCATION_PERMISSIONS)
        }
    }

    when (val location = uiState.location) {
        is OnSpotLocation.Located -> LocatedContent(uiState, viewModel, isLandscape, onNavigateToSettings, modifier)
        else -> LocationStatus(location, onRetry = onRetryLocation, modifier = modifier)
    }
}

@Composable
private fun LocatedContent(
    uiState: StopBoardViewModel.UiState,
    viewModel: StopBoardViewModel,
    isLandscape: Boolean,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier,
) {
    if (isLandscape) {
        Row(modifier = modifier.padding(6.dp)) {
            RoutesSection(uiState, onNavigateToSettings, Modifier.weight(1.4f).fillMaxHeight())
            NearbyStopSection(uiState, viewModel, Modifier.weight(1f).fillMaxHeight())
        }
    } else {
        Column(modifier = modifier.padding(6.dp)) {
            RoutesSection(uiState, onNavigateToSettings, Modifier.weight(2f).fillMaxWidth())
            NearbyStopSection(uiState, viewModel, Modifier.weight(1f).fillMaxWidth())
        }
    }
}

/** Finding the device, or why it could not be found, filling the board until it is. */
@Composable
private fun LocationStatus(
    location: OnSpotLocation,
    onRetry: () -> Unit,
    modifier: Modifier,
) {
    Box(modifier = modifier.padding(24.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val failed = location is OnSpotLocation.Unavailable || location is OnSpotLocation.PermissionDenied
            if (failed) {
                Icon(
                    imageVector = Icons.Default.LocationOff,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(40.dp),
                )
            } else {
                CircularProgressIndicator()
            }
            Text(
                text =
                    stringResource(
                        when (location) {
                            OnSpotLocation.PermissionDenied -> R.string.location_denied
                            OnSpotLocation.Unavailable -> R.string.location_unavailable
                            is OnSpotLocation.Locating ->
                                if (location.accuracy == LocationAccuracy.PRECISE) {
                                    R.string.locating_precise
                                } else {
                                    R.string.locating_approximate
                                }
                            else -> R.string.locating_precise
                        },
                    ),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            if (failed) {
                Button(onClick = onRetry) {
                    Text(
                        stringResource(
                            if (location is OnSpotLocation.PermissionDenied) R.string.location_allow else R.string.retry,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun RoutesSection(
    uiState: StopBoardViewModel.UiState,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier,
) {
    if (uiState.destinationStations.isEmpty()) {
        Card(modifier = modifier.padding(6.dp)) {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            ) {
                Text(
                    text = stringResource(R.string.routes_add_destination),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
                Button(onClick = onNavigateToSettings) { Text(stringResource(R.string.add_destination)) }
            }
        }
        return
    }

    // A destination with no route is left out once it has loaded, as on the wall board.
    val destinations =
        uiState.destinationStations.filter {
            it.id in uiState.fastestTrips ||
                it.id in uiState.loadingTripDestinationIds ||
                it.id in uiState.failedTripDestinationIds
        }
    BoxWithConstraints(modifier = modifier) {
        val fontScale = LocalDensity.current.fontScale
        val autoCount =
            autoTripOptionCount(
                availableHeight = maxHeight.value,
                destinationCount = destinations.size,
                rowHeight = TRIP_ROW_HEIGHT_DP * fontScale,
                headerHeight = DESTINATION_HEADER_HEIGHT_DP * fontScale,
            )
        val count = visibleTripOptionCount(uiState.tripOptionsCount, autoCount)
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            destinations.forEach { destination ->
                key(destination.id) {
                    DestinationRoutesCard(
                        destination = destination,
                        trips = uiState.fastestTrips[destination.id],
                        count = count,
                        isRefreshing = destination.id in uiState.loadingTripDestinationIds,
                        isFailed = destination.id in uiState.failedTripDestinationIds,
                        isOnline = uiState.isOnline,
                    )
                }
            }
        }
    }
}

/** One destination's next [count] route alternatives, best first. */
@Composable
private fun DestinationRoutesCard(
    destination: StopPlace,
    trips: List<TripInfo>?,
    count: Int,
    isRefreshing: Boolean,
    isFailed: Boolean,
    isOnline: Boolean,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        when {
            trips == null && isFailed ->
                FastestTripUnavailable(destinationName = destination.name, isOnline = isOnline, isRetrying = isRefreshing)
            trips == null -> FastestTripPlaceholder(destinationName = destination.name)
            else -> {
                Box {
                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                        Text(
                            text = stringResource(R.string.route_to, destination.name),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                        trips.take(count).forEachIndexed { index, trip ->
                            if (index > 0) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f),
                                )
                            }
                            TripOptionRow(trip = trip, isFirst = index == 0)
                        }
                    }
                    if (isRefreshing) {
                        RefreshingIndicator(
                            modifier = Modifier.fillMaxWidth().height(2.dp).align(Alignment.BottomCenter),
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f),
                        )
                    }
                }
            }
        }
    }
}

/** One alternative: when to leave, the legs, when it arrives and how long it takes. */
@Composable
private fun TripOptionRow(
    trip: TripInfo,
    isFirst: Boolean,
) {
    val onContainer = MaterialTheme.colorScheme.onPrimaryContainer
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = trip.expectedStartTime.takeIf { it.length >= 16 }?.substring(11, 16) ?: "--:--",
            style =
                if (isFirst) {
                    MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
                } else {
                    MaterialTheme.typography.titleLarge
                },
            color = onContainer,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = tripRouteText(trip),
                style = MaterialTheme.typography.bodyMedium,
                color = onContainer,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            tripArrival(trip)?.let { arrival ->
                val time = if (arrival.dayOffset > 0) "${arrival.time} +${arrival.dayOffset}" else arrival.time
                Text(
                    text = stringResource(R.string.route_arrival, time),
                    style = MaterialTheme.typography.bodySmall,
                    color = onContainer.copy(alpha = 0.7f),
                )
            }
        }
        Text(
            text = stringResource(R.string.duration_min, trip.durationMinutes),
            style = MaterialTheme.typography.titleMedium,
            color = onContainer,
        )
    }
}

@Composable
private fun NearbyStopSection(
    uiState: StopBoardViewModel.UiState,
    viewModel: StopBoardViewModel,
    modifier: Modifier,
) {
    val stop = uiState.favorites.firstOrNull()
    if (stop == null) {
        Card(modifier = modifier.padding(6.dp)) {
            Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                if (uiState.isSearchingNearby || uiState.nearbyStops.isNotEmpty()) {
                    CircularProgressIndicator()
                } else {
                    Text(stringResource(R.string.no_stops_nearby), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        return
    }
    StopBoardPanel(
        stopPlace = stop,
        departureGroups = uiState.departures.find { it.stopId == stop.id }?.groups,
        showDelays = uiState.showDelays,
        showMessages = uiState.showMessages,
        isLoading = stop.id in uiState.loadingStopIds,
        isExtended = stop.id in uiState.extendedStopIds,
        isFailed = stop.id in uiState.failedStopIds,
        isOnline = uiState.isOnline,
        canLoadMore = stop.id in uiState.canLoadMoreStopIds,
        isLoadingMore = stop.id in uiState.loadingMoreStopIds,
        onFilterOut = viewModel::filterOutLine,
        onSearchFurtherAhead = { viewModel.searchFurtherAhead(stop.id) },
        onRetry = viewModel::refreshData,
        onLoadMore = { viewModel.loadMoreDepartures(stop.id) },
        loadJourney = { departure -> viewModel.journeyStopsAhead(departure, stop.id) },
        modifier = modifier,
        header = {
            StopPicker(
                selected = stop,
                nearbyStops = uiState.nearbyStops,
                isPinned = uiState.isStopPinned,
                onSelect = viewModel::selectStop,
                onFollowNearest = viewModel::followNearest,
            )
        },
    )
}

/** The shown stop's name, opening a menu of the other stops nearby; and a way back to the nearest. */
@Composable
private fun RowScope.StopPicker(
    selected: StopPlace,
    nearbyStops: List<NearbyStop>,
    isPinned: Boolean,
    onSelect: (String) -> Unit,
    onFollowNearest: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val distance = nearbyStops.firstOrNull { it.stop.id == selected.id }?.distanceMeters
    Box(modifier = Modifier.weight(1f)) {
        Row(
            modifier = Modifier.clickable(enabled = nearbyStops.size > 1) { expanded = true },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f, fill = false)) {
                Text(
                    text = selected.name,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = listOfNotNull(distance?.let { distanceText(it) }, selected.locality).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            if (nearbyStops.size > 1) {
                Icon(Icons.Default.ArrowDropDown, contentDescription = stringResource(R.string.choose_stop))
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            nearbyStops.forEach { nearby ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(
                                text = nearby.stop.name,
                                fontWeight = if (nearby.stop.id == selected.id) FontWeight.Bold else FontWeight.Normal,
                            )
                            nearby.distanceMeters?.let {
                                Text(
                                    text = distanceText(it),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    },
                    onClick = {
                        expanded = false
                        onSelect(nearby.stop.id)
                    },
                )
            }
        }
    }
    if (isPinned) {
        AssistChip(
            onClick = onFollowNearest,
            label = { Text(stringResource(R.string.back_to_nearest)) },
            leadingIcon = { Icon(Icons.Default.MyLocation, contentDescription = null, Modifier.size(AssistChipDefaults.IconSize)) },
        )
    }
}

@Composable
private fun distanceText(meters: Int): String =
    if (meters < METRES_PER_KILOMETRE) {
        stringResource(R.string.distance_meters, meters)
    } else {
        val kilometres = meters / METRES_PER_KILOMETRE.toFloat()
        stringResource(R.string.distance_kilometers, String.format(LocalLocale.current.platformLocale, "%.1f", kilometres))
    }

private fun Context.hasLocationPermission(): Boolean =
    LOCATION_PERMISSIONS.any { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }

private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

private fun Context.openAppSettings() {
    startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}
