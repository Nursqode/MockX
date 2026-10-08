package com.noobexon.xposedfakelocation.manager.ui.map

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.navigation.NavController
import com.noobexon.xposedfakelocation.R
import com.noobexon.xposedfakelocation.data.HOME_WALK_MAX_RADIUS_METERS
import com.noobexon.xposedfakelocation.manager.route.WalkingErrorCode
import com.noobexon.xposedfakelocation.manager.route.WalkingMode
import com.noobexon.xposedfakelocation.manager.route.WalkingPhase
import com.noobexon.xposedfakelocation.manager.route.WalkingRoute
import com.noobexon.xposedfakelocation.manager.route.WalkingSpeedPreset
import com.noobexon.xposedfakelocation.manager.ui.components.AppDialog
import com.noobexon.xposedfakelocation.manager.ui.components.BottomControlPanel
import com.noobexon.xposedfakelocation.manager.ui.components.BlurBackdropBox
import com.noobexon.xposedfakelocation.manager.ui.components.FloatingPanel
import com.noobexon.xposedfakelocation.manager.ui.components.PillActionButton
import com.noobexon.xposedfakelocation.manager.ui.components.StatusChip
import com.noobexon.xposedfakelocation.manager.ui.components.rememberBlurBackdrop
import com.noobexon.xposedfakelocation.manager.ui.navigation.Screen
import com.noobexon.xposedfakelocation.manager.ui.theme.StatusSuccess
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Favorites
import top.yukonga.miuix.kmp.icon.extended.Location
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import top.yukonga.miuix.kmp.icon.extended.MapAlbum
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.icon.extended.Pause
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Play
import top.yukonga.miuix.kmp.icon.extended.Sidebar
import top.yukonga.miuix.kmp.menu.WindowIconDropdownMenu
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint

/**
 * Full-screen immersive map with HyperOS floating chrome: a top status island (drawer trigger,
 * live spoof status, layer switcher, center) and a bottom dashboard panel (primary action,
 * location info, favorites, quick clear). The modal drawer is a Material 3
 * [ModalNavigationDrawer], which handles dragging and predictive back natively.
 */
@Composable
fun MapScreen(
    navController: NavController,
    mapViewModel: MapViewModel
) {
    val context = LocalContext.current
    val uiState by mapViewModel.uiState.collectAsStateWithLifecycle()
    val isPlaying = uiState.isPlaying
    val isFabClickable = uiState.isFabClickable
    val clickedLocation = uiState.lastClickedLocation
    val showGoToPointDialog = uiState.isGoToPointDialogVisible
    val showAddToFavoritesDialog = uiState.isAddToFavoritesDialogVisible
    val showMapSourceDialog = uiState.isMapSourceDialogVisible
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val colorScheme = MiuixTheme.colorScheme
    val backdrop = rememberBlurBackdrop()
    val blurEnabled = backdrop != null
    val fakeLocationSet = stringResource(R.string.toast_fake_location_set)
    val fakeLocationUnset = stringResource(R.string.toast_unset_fake_location)

    // ---- Walking simulation ----
    val walkingPhase = uiState.walkingPhase
    val isWalkingActive = uiState.isWalkingActive

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> mapViewModel.startWalking() }

    fun beginWalkingWithPermissionCheck() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            mapViewModel.startWalking()
        }
    }

    LaunchedEffect(mapViewModel.walkingEvents) {
        mapViewModel.walkingEvents.collect { event ->
            when (event) {
                is WalkingEvent.WalkFailed ->
                    Toast.makeText(context, context.getString(walkingErrorRes(event.errorCode)), Toast.LENGTH_LONG).show()
                is WalkingEvent.RouteReady ->
                    Toast.makeText(context, context.getString(R.string.walk_route_ready_toast), Toast.LENGTH_SHORT).show()
                is WalkingEvent.HomeWalkStarted ->
                    Toast.makeText(context, context.getString(R.string.walk_home_started_toast), Toast.LENGTH_SHORT).show()
            }
        }
    }

    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }

    LaunchedEffect(Unit) {
        if (mapViewModel.consumeReopenDrawerRequest()) {
            drawerState.open()
        }
    }

    LaunchedEffect(mapViewModel.navigateToFavoritesEvent) {
        mapViewModel.navigateToFavoritesEvent.collect {
            navController.navigate(Screen.Favorites.route) { launchSingleTop = true }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        // The M3 drawer arms its drag detector on the whole screen; while closed that hijacks
        // every horizontal pan of the map. Enable the gestures only once the drawer is open —
        // drag-to-close keeps working, and the map pans freely while closed.
        gesturesEnabled = !drawerState.isClosed,
        scrimColor = Color.Black.copy(alpha = 0.45f),
        drawerContent = {
            ModalDrawerSheet(
                // The drawerState overload owns back handling: it closes on back on every
                // Android version and animates the predictive-back preview on Android 14+.
                drawerState = drawerState,
                modifier = Modifier.width(300.dp),
                drawerShape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp),
                drawerContainerColor = colorScheme.surface,
            ) {
                DrawerContent(
                    onCloseDrawer = { scope.launch { drawerState.close() } },
                    onNavigate = { mapViewModel.requestReopenDrawer() },
                    navController = navController
                )
            }
        },
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // 1. Full-screen immersive map canvas, captured as the blur backdrop for the panels
            BlurBackdropBox(backdrop = backdrop) {
            MapViewContainer(
                isLoading = uiState.isLoading,
                lastClickedLocation = uiState.lastClickedLocation,
                userLocation = uiState.userLocation,
                mapZoom = uiState.mapZoom,
                mapSource = uiState.mapSource,
                tiandituToken = uiState.tiandituToken,
                hasResolvedInitialLocation = uiState.hasResolvedInitialLocation,
                walkingRoute = uiState.walkingRoute,
                walkingCurrentPosition = uiState.walkingCurrentPosition,
                goToPointEvent = mapViewModel.goToPointEvent,
                centerMapEvent = mapViewModel.centerMapEvent,
                onClickedLocationChange = mapViewModel::updateClickedLocation,
                onUserLocationChange = mapViewModel::updateUserLocation,
                onMapZoomChange = mapViewModel::updateMapZoom,
                onLoadingFinished = mapViewModel::setLoadingFinished,
                onInitialLocationResolved = mapViewModel::markInitialLocationResolved,
                isMapInteractionEnabled = !uiState.isMapInteractionLocked,
            )
            }

            // 2. HyperOS floating top status island
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                FloatingPanel(
                    backdrop = backdrop,
                    blurEnabled = blurEnabled,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Drawer trigger button
                    IconButton(
                        onClick = { scope.launch { drawerState.open() } },
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Sidebar,
                            contentDescription = stringResource(R.string.cd_menu),
                        )
                    }

                    // Middle status & location summary
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                    ) {
                        // Live status indicator dot
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        walkingPhase == WalkingPhase.WALKING -> StatusSuccess
                                        walkingPhase == WalkingPhase.PAUSED -> Color(0xFFFF9500)
                                        isPlaying -> StatusSuccess
                                        isFabClickable -> colorScheme.primary
                                        else -> colorScheme.onSurfaceVariantSummary
                                    }
                                )
                        )

                        Spacer(modifier = Modifier.width(6.dp))

                        Column {
                            Text(
                                text = walkingStatusText(walkingPhase, isPlaying, isFabClickable, uiState.walkingMode),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (clickedLocation != null) {
                                Text(
                                    text = String.format(Locale.US, "%.4f, %.4f", clickedLocation.latitude, clickedLocation.longitude),
                                    fontSize = 11.sp,
                                    color = colorScheme.onSurfaceVariantSummary,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    // Action icons: center, switch source, more
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { mapViewModel.triggerCenterMapEvent() },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = MiuixIcons.Location,
                                contentDescription = stringResource(R.string.cd_center),
                                tint = colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        IconButton(
                            onClick = { mapViewModel.showMapSourceDialog() },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = MiuixIcons.MapAlbum,
                                contentDescription = stringResource(R.string.map_switch_source),
                                tint = colorScheme.onSurfaceVariantSummary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        val menuItems = remember(isFabClickable, colorScheme) {
                            listOf(
                                DropdownItem(
                                    text = context.getString(R.string.map_go_to_point),
                                    icon = { modifier ->
                                        Icon(
                                            imageVector = MiuixIcons.Location,
                                            contentDescription = null,
                                            tint = colorScheme.primary,
                                            modifier = modifier.size(20.dp)
                                        )
                                    },
                                    onClick = { mapViewModel.showGoToPointDialog() }
                                ),
                                DropdownItem(
                                    text = context.getString(R.string.map_add_to_favorites),
                                    icon = { modifier ->
                                        Icon(
                                            imageVector = MiuixIcons.Favorites,
                                            contentDescription = null,
                                            tint = colorScheme.primary,
                                            modifier = modifier.size(20.dp)
                                        )
                                    },
                                    enabled = isFabClickable,
                                    onClick = { mapViewModel.showAddToFavoritesDialog() }
                                ),
                                DropdownItem(
                                    text = context.getString(R.string.map_clear_location),
                                    icon = { modifier ->
                                        Icon(
                                            imageVector = MiuixIcons.Delete,
                                            contentDescription = null,
                                            tint = colorScheme.error,
                                            modifier = modifier.size(20.dp)
                                        )
                                    },
                                    enabled = isFabClickable,
                                    onClick = { mapViewModel.updateClickedLocation(null) }
                                )
                            )
                        }

                        WindowIconDropdownMenu(
                            entry = DropdownEntry(items = menuItems),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = MiuixIcons.More,
                                contentDescription = stringResource(R.string.cd_options),
                                tint = colorScheme.onSurfaceVariantSummary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            // 3. HyperOS bottom floating dashboard panel
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 16.dp)
            ) {
                BottomControlPanel(
                    backdrop = backdrop,
                    blurEnabled = blurEnabled
                ) {
                    if (clickedLocation != null) {
                        // Location info & actions row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.map_spoof_target),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = colorScheme.onSurfaceVariantSummary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = String.format(Locale.US, "%.6f°, %.6f°", clickedLocation.latitude, clickedLocation.longitude),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colorScheme.onSurface
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                StatusChip(
                                    text = stringResource(R.string.map_chip_favorite),
                                    isActive = true,
                                    modifier = Modifier.clickable { mapViewModel.showAddToFavoritesDialog() }
                                )
                                StatusChip(
                                    text = "步行",
                                    isActive = true,
                                    modifier = Modifier.clickable {
                                        if (!isWalkingActive && walkingPhase != WalkingPhase.PLANNING) {
                                            mapViewModel.planWalkingRoute()
                                        }
                                    }
                                )
                                StatusChip(
                                    text = stringResource(R.string.map_chip_home_walk),
                                    isActive = uiState.isHomeWalkingActive,
                                    modifier = Modifier.clickable(
                                        enabled = uiState.isHomeWalkingActive ||
                                            (!isWalkingActive && walkingPhase != WalkingPhase.PLANNING)
                                    ) { mapViewModel.toggleHomeWalking() }
                                )
                                StatusChip(
                                    text = stringResource(R.string.map_chip_clear),
                                    isActive = false,
                                    modifier = Modifier.clickable { mapViewModel.updateClickedLocation(null) }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                    } else {
                        // Helpful prompt when no pin is placed
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = MiuixIcons.Location,
                                contentDescription = null,
                                tint = colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.map_hint_place_pin),
                                fontSize = 13.sp,
                                color = colorScheme.onSurfaceVariantSummary
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    // Walking simulation control card (hidden while idle without a route)
                    if (walkingPhase != WalkingPhase.IDLE) {
                        WalkingControlCard(
                            uiState = uiState,
                            onPlanRoute = mapViewModel::planWalkingRoute,
                            onStart = ::beginWalkingWithPermissionCheck,
                            onPause = mapViewModel::pauseWalking,
                            onResume = mapViewModel::resumeWalking,
                            onStop = mapViewModel::stopWalking,
                            onSpeedChange = mapViewModel::setWalkingSpeedPreset,
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                    }

                    // Main action pill button
                    PillActionButton(
                        text = when {
                            isWalkingActive -> stringResource(R.string.walk_active_pill)
                            walkingPhase == WalkingPhase.PLANNING -> stringResource(R.string.walk_planning_pill)
                            isPlaying -> stringResource(R.string.map_stop_spoof)
                            isFabClickable -> stringResource(R.string.map_start_spoof)
                            else -> stringResource(R.string.map_select_location_first)
                        },
                        icon = when {
                            walkingPhase == WalkingPhase.PLANNING -> MiuixIcons.Location
                            isPlaying -> MiuixIcons.Close
                            else -> MiuixIcons.Play
                        },
                        onClick = {
                            if (isFabClickable || isPlaying) {
                                val wasPlaying = uiState.isPlaying
                                mapViewModel.togglePlaying()
                                Toast.makeText(
                                    context,
                                    if (!wasPlaying) fakeLocationSet else fakeLocationUnset,
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        isPrimary = !isPlaying,
                        isDestructive = isPlaying,
                        enabled = !isWalkingActive && walkingPhase != WalkingPhase.PLANNING && (isFabClickable || isPlaying),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // Dialogs
        if (showGoToPointDialog) {
            val goToPoint = uiState.goToPointState
            GoToPointDialog(
                query = goToPoint.query.value,
                queryErrorRes = goToPoint.query.errorMessageRes,
                onQueryChange = mapViewModel::onGoToPointQueryChange,
                onConfirm = mapViewModel::confirmGoToPoint,
                onDismissRequest = mapViewModel::hideGoToPointDialog,
            )
        }

        if (showAddToFavoritesDialog) {
            val favorite = uiState.addToFavoritesState
            AddToFavoritesDialog(
                name = favorite.name.value,
                description = favorite.description.value,
                latitude = favorite.latitude.value,
                longitude = favorite.longitude.value,
                nameErrorRes = favorite.name.errorMessageRes,
                latitudeErrorRes = favorite.latitude.errorMessageRes,
                longitudeErrorRes = favorite.longitude.errorMessageRes,
                onNameChange = mapViewModel::onFavoriteNameChange,
                onDescriptionChange = mapViewModel::onFavoriteDescriptionChange,
                onLatitudeChange = mapViewModel::onFavoriteLatitudeChange,
                onLongitudeChange = mapViewModel::onFavoriteLongitudeChange,
                onConfirm = mapViewModel::confirmAddFavorite,
                onDismissRequest = mapViewModel::hideAddToFavoritesDialog,
            )
        }

        if (showMapSourceDialog) {
            MapSourceSelectionDialog(
                selectedSource = uiState.mapSource,
                onSourceSelected = { option ->
                    mapViewModel.setMapSource(option)
                    mapViewModel.hideMapSourceDialog()
                },
                onDismiss = mapViewModel::hideMapSourceDialog
            )
        }

        if (uiState.isReplaceSessionDialogVisible) {
            AppDialog(
                title = stringResource(R.string.walk_replace_title),
                onDismissRequest = mapViewModel::dismissReplaceSessionDialog,
                confirmText = stringResource(R.string.walk_replace_confirm),
                onConfirm = mapViewModel::confirmStartWalking,
                dismissText = stringResource(R.string.action_cancel),
            ) {
                Text(
                    text = stringResource(R.string.walk_replace_message),
                    color = colorScheme.onSurfaceVariantSummary,
                )
            }
        }
    }
}

/**
 * Status-island title for the current walking phase, falling back to the fixed-spoof labels.
 * A running home walk reports its own title instead of the route-walk one.
 */
@Composable
private fun walkingStatusText(
    walkingPhase: WalkingPhase,
    isPlaying: Boolean,
    isFabClickable: Boolean,
    walkingMode: WalkingMode = WalkingMode.ROUTE,
): String {
    if (walkingMode == WalkingMode.HOME &&
        (walkingPhase == WalkingPhase.WALKING || walkingPhase == WalkingPhase.PAUSED)
    ) {
        return stringResource(R.string.walk_status_home)
    }
    return when (walkingPhase) {
        WalkingPhase.PLANNING -> stringResource(R.string.walk_status_planning)
        WalkingPhase.READY -> stringResource(R.string.walk_status_ready)
        WalkingPhase.WALKING -> stringResource(R.string.walk_status_walking)
        WalkingPhase.PAUSED -> stringResource(R.string.walk_status_paused)
        WalkingPhase.ARRIVED -> stringResource(R.string.walk_status_arrived)
        WalkingPhase.STOPPING -> stringResource(R.string.walk_status_stopping)
        WalkingPhase.FAILED -> stringResource(R.string.walk_status_failed)
        else -> when {
            isPlaying -> stringResource(R.string.map_status_spoofing)
            isFabClickable -> stringResource(R.string.map_status_selected)
            else -> stringResource(R.string.app_name)
        }
    }
}

/** Localized, user-safe message for a walking failure code. */
@Composable
private fun walkingErrorText(errorCode: WalkingErrorCode): String =
    stringResource(walkingErrorRes(errorCode))

/** Maps a [WalkingErrorCode] to its message resource; usable from non-composable contexts. */
private fun walkingErrorRes(errorCode: WalkingErrorCode): Int = when (errorCode) {
    WalkingErrorCode.MISSING_API_KEY -> R.string.walk_error_missing_api_key
    WalkingErrorCode.LOCATION_UNAVAILABLE -> R.string.walk_error_location_unavailable
    WalkingErrorCode.INVALID_DESTINATION -> R.string.walk_error_invalid_destination
    WalkingErrorCode.NETWORK_TIMEOUT -> R.string.walk_error_network_timeout
    WalkingErrorCode.NETWORK_UNAVAILABLE -> R.string.walk_error_network_unavailable
    WalkingErrorCode.API_AUTH_FAILED -> R.string.walk_error_api_auth_failed
    WalkingErrorCode.API_QUOTA_EXCEEDED -> R.string.walk_error_api_quota_exceeded
    WalkingErrorCode.NO_ROUTE -> R.string.walk_error_no_route
    WalkingErrorCode.INVALID_ROUTE_DATA -> R.string.walk_error_invalid_route_data
    WalkingErrorCode.PREFERENCE_UNAVAILABLE -> R.string.walk_error_preference_unavailable
    WalkingErrorCode.SERVICE_START_FAILED -> R.string.walk_error_service_start_failed
    WalkingErrorCode.LOCATION_STATE_STALE -> R.string.walk_error_location_state_stale
}

/**
 * Compact control card for the walking simulation, rendered above the main pill button.
 *
 * Renders according to [MapUiState.walkingPhase]:
 *  - PLANNING: an indeterminate "planning" row.
 *  - READY: route summary (total distance + estimated time), speed preset chips and the
 *    start action.
 *  - WALKING / PAUSED / ARRIVED: live progress bar, remaining controls (pause/resume/stop).
 *  - FAILED: the sanitized error message plus a "re-plan" action; the chosen destination is
 *    kept so planning can simply be retried.
 */
@Composable
private fun WalkingControlCard(
    uiState: MapUiState,
    onPlanRoute: () -> Unit,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onSpeedChange: (WalkingSpeedPreset) -> Unit,
) {
    val phase = uiState.walkingPhase
    val route = uiState.walkingRoute

    Column(modifier = Modifier.fillMaxWidth()) {
        // Status row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = MiuixIcons.Location,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = walkingStatusText(
                    walkingPhase = phase,
                    isPlaying = false,
                    isFabClickable = true,
                    walkingMode = uiState.walkingMode,
                ),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MiuixTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            if (route != null && phase != WalkingPhase.PLANNING) {
                Text(
                    text = walkingRouteSummary(route, uiState.walkingSpeedPreset),
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
            }
        }

        when (phase) {
            WalkingPhase.PLANNING -> {
                Spacer(modifier = Modifier.height(10.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            WalkingPhase.READY -> {
                Spacer(modifier = Modifier.height(10.dp))
                WalkingEndpoints(route)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(
                        R.string.walk_arrival_format,
                        formatArrivalTime((route?.expectedDurationSeconds ?: 0).toLong()),
                    ),
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                Spacer(modifier = Modifier.height(10.dp))
                WalkingSpeedChipRow(uiState.walkingSpeedPreset, onSpeedChange)
                Spacer(modifier = Modifier.height(10.dp))
                PillActionButton(
                    text = stringResource(R.string.walk_start),
                    icon = MiuixIcons.Play,
                    onClick = onStart,
                    isPrimary = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            WalkingPhase.WALKING, WalkingPhase.PAUSED, WalkingPhase.ARRIVED -> {
                val isHomeWalk = uiState.walkingMode == WalkingMode.HOME
                val total = route?.totalDistanceMeters ?: 0.0
                Spacer(modifier = Modifier.height(10.dp))
                if (isHomeWalk) {
                    // A home walk has no destination or total; the anchor is the reference point.
                    HomeWalkSpotLine(anchor = uiState.lastClickedLocation)
                } else {
                    LinearProgressIndicator(
                        progress = { if (total > 0) (uiState.walkingDistanceTravelled / total).toFloat().coerceIn(0f, 1f) else 0f },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    WalkingEndpoints(route)
                }
                Text(
                    text = if (isHomeWalk) {
                        stringResource(R.string.walk_home_progress_format, formatWalkingDistance(uiState.walkingDistanceTravelled))
                    } else {
                        stringResource(
                            R.string.walk_progress_format,
                            formatWalkingDistance(uiState.walkingDistanceTravelled),
                            formatWalkingDistance(total),
                        )
                    },
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
                if (!isHomeWalk && phase != WalkingPhase.ARRIVED && route != null && uiState.walkingSpeedPreset.metersPerSecond > 0f) {
                    val remaining = ((total - uiState.walkingDistanceTravelled) / uiState.walkingSpeedPreset.metersPerSecond)
                        .toLong().coerceAtLeast(0)
                    Text(
                        text = stringResource(R.string.walk_arrival_format, formatArrivalTime(remaining)),
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
                if (phase != WalkingPhase.ARRIVED) {
                    Spacer(modifier = Modifier.height(8.dp))
                    WalkingSpeedChipRow(uiState.walkingSpeedPreset, onSpeedChange)
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (phase == WalkingPhase.PAUSED) {
                        PillActionButton(
                            text = stringResource(R.string.walk_resume),
                            icon = MiuixIcons.Play,
                            onClick = onResume,
                            isPrimary = true,
                            modifier = Modifier.weight(1f)
                        )
                    } else if (phase == WalkingPhase.WALKING) {
                        PillActionButton(
                            text = stringResource(R.string.walk_pause),
                            icon = MiuixIcons.Pause,
                            onClick = onPause,
                            isPrimary = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    PillActionButton(
                        text = stringResource(R.string.walk_stop),
                        icon = MiuixIcons.Close,
                        onClick = onStop,
                        isDestructive = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            WalkingPhase.FAILED -> {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = uiState.walkingErrorCode?.let { walkingErrorText(it) } ?: stringResource(R.string.walk_status_failed),
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(8.dp))
                PillActionButton(
                    text = stringResource(R.string.walk_replan),
                    icon = MiuixIcons.Refresh,
                    onClick = onPlanRoute,
                    isPrimary = false,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            WalkingPhase.IDLE, WalkingPhase.STOPPING -> Unit
        }
    }
}

/** The three walking-pace presets as selectable chips. */
@Composable
private fun WalkingSpeedChipRow(
    selected: WalkingSpeedPreset,
    onSpeedChange: (WalkingSpeedPreset) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        WalkingSpeedPreset.entries.forEach { preset ->
            StatusChip(
                text = stringResource(preset.labelRes),
                isActive = preset == selected,
                modifier = Modifier
                    .weight(1f)
                    .clickable { onSpeedChange(preset) }
            )
        }
    }
}

@Composable
private fun WalkingEndpoints(route: WalkingRoute?) {
    if (route == null) return
    Text(
        text = stringResource(R.string.walk_origin) + " " + formatCoordinate(route.origin) +
            " → " + stringResource(R.string.walk_destination) + " " + formatCoordinate(route.destination),
        fontSize = 12.sp,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Reference line for a home walk: the pinned anchor and the radius the walker stays inside. */
@Composable
private fun HomeWalkSpotLine(anchor: GeoPoint?) {
    if (anchor == null) return
    Text(
        text = stringResource(
            R.string.walk_home_spot_format,
            HOME_WALK_MAX_RADIUS_METERS.toInt(),
            String.format(Locale.US, "%.5f, %.5f", anchor.latitude, anchor.longitude),
        ),
        fontSize = 12.sp,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

private fun formatCoordinate(coordinate: com.noobexon.xposedfakelocation.manager.route.Coordinate): String =
    String.format(Locale.US, "%.5f, %.5f", coordinate.latitude, coordinate.longitude)

private fun formatArrivalTime(remainingSeconds: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(System.currentTimeMillis() + remainingSeconds * 1000L))

/** "1.24 km · 约 15 分钟" style summary; the ETA prefers the API-provided duration. */
@Composable
private fun walkingRouteSummary(route: WalkingRoute, preset: WalkingSpeedPreset): String {
    val durationSeconds = route.expectedDurationSeconds
        ?: route.totalDistanceMeters.div(preset.metersPerSecond).toInt()
    val minutes = (durationSeconds / 60).coerceAtLeast(1)
    return "${formatWalkingDistance(route.totalDistanceMeters)} · ${stringResource(R.string.walk_eta_format, minutes)}"
}

/** Human-readable distance: metres below 1 km, kilometres above. */
private fun formatWalkingDistance(meters: Double): String = when {
    meters >= 1000.0 -> String.format(Locale.US, "%.2f km", meters / 1000.0)
    else -> String.format(Locale.US, "%.0f m", meters)
}
