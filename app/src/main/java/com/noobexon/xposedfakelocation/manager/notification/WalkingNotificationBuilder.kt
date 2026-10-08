package com.noobexon.xposedfakelocation.manager.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat
import com.noobexon.xposedfakelocation.R
import com.noobexon.xposedfakelocation.data.repository.PreferencesRepository
import com.noobexon.xposedfakelocation.manager.MainActivity
import com.noobexon.xposedfakelocation.manager.route.WalkingMode
import com.noobexon.xposedfakelocation.manager.route.WalkingPhase
import com.noobexon.xposedfakelocation.manager.walking.WalkingSimulationService

/**
 * Builds the ONE shared walking notification (MockX完整功能规划.md §9.1): a standard
 * `NotificationCompat` base, optionally styled as an Android 16 Live Update on API 36+ and
 * optionally carrying the HyperOS Super Island extras on Xiaomi hardware. All surfaces render
 * from the same [WalkingNotificationState]; there is exactly one notification id and one channel.
 */
class WalkingNotificationBuilder(private val context: Context) {

    private val formatter = NotificationContentFormatter(context)
    private val repository = PreferencesRepository(context)

    fun createChannel() {
        val channel = NotificationChannel(
            WalkingSimulationService.CHANNEL_ID,
            context.getString(R.string.walk_notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        )
        channel.description = context.getString(R.string.walk_notification_channel_description)
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun build(state: WalkingNotificationState): Notification {
        val title = formatter.contentTitle()
        val text = formatter.contentText(state)
        // A home walk has no destination to measure progress against, so its bar is indefinite.
        val isHomeWalk = state.mode == WalkingMode.HOME
        val builder = NotificationCompat.Builder(context, WalkingSimulationService.CHANNEL_ID)
            .setSmallIcon(SMALL_ICON_RES)
            .setContentTitle(title)
            .setContentText(text)
            .setSubText(formatter.routeSummary(state))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .setContentIntent(contentIntent())
            // The legacy bar stays the baseline; on API 36+ the Live Update style replaces it.
            .setProgress(
                if (isHomeWalk) 0 else WalkingNotificationState.PROGRESS_MAX,
                if (isHomeWalk) 0 else state.progressPerMille,
                isHomeWalk,
            )

        // ETA countdown in the header (and in the promoted status chip) while walking.
        if (state.phase == WalkingPhase.WALKING && state.remainingSeconds != null && state.remainingSeconds > 0) {
            builder.setWhen(System.currentTimeMillis() + state.remainingSeconds * 1000)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
        } else {
            builder.setUsesChronometer(false)
        }

        for (action in state.actions()) {
            when (action) {
                WalkingNotificationState.Action.PAUSE -> builder.addAction(
                    0,
                    context.getString(R.string.walk_pause),
                    servicePendingIntent(WalkingSimulationService.ACTION_PAUSE_WALKING, REQUEST_PAUSE),
                )
                WalkingNotificationState.Action.RESUME -> builder.addAction(
                    0,
                    context.getString(R.string.walk_resume),
                    servicePendingIntent(WalkingSimulationService.ACTION_RESUME_WALKING, REQUEST_RESUME),
                )
                WalkingNotificationState.Action.STOP -> builder.addAction(
                    0,
                    context.getString(R.string.walk_stop),
                    servicePendingIntent(WalkingSimulationService.ACTION_STOP_WALKING, REQUEST_STOP),
                )
            }
        }

        // One styled surface per device: Xiaomi hardware renders the HyperOS Super Island from
        // the miui.focus extras; elsewhere the Android 16 Live Update progress style is used.
        // The two must be mutually exclusive — on OS3 the Google style would win the render and
        // the island extras would be ignored (通知体验升级规划.md §9). The user can force either
        // side with the "Notification style" setting; AUTO keeps this device-appropriate default.
        val islandStyle = IslandStyleOption.fromTag(repository.getIslandStyle())
        val isXiaomi = XiaomiIslandAdapter.isEligible(context)
        val useIsland = when (islandStyle) {
            IslandStyleOption.AUTO -> isXiaomi
            IslandStyleOption.SUPER_ISLAND -> isXiaomi
            IslandStyleOption.GOOGLE_LIVE_UPDATE -> false
        }
        val useLiveUpdate = when (islandStyle) {
            IslandStyleOption.AUTO -> !isXiaomi
            IslandStyleOption.SUPER_ISLAND -> false
            IslandStyleOption.GOOGLE_LIVE_UPDATE -> true
        } && Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA

        if (useIsland) {
            XiaomiIslandAdapter.apply(
                builder = builder,
                context = context,
                state = state,
                title = title,
                status = formatter.compactMode(),
                distanceSummary = formatter.routeSummary(state),
                eta = listOfNotNull(formatter.distanceSummary(state), formatter.etaText(state)).joinToString(" · "),
                // Third line: travelled distance · ETA, only when there is useful content.
                expandedLines = formatter.routeLinesShort(state).ifEmpty {
                    listOfNotNull(
                        listOfNotNull(formatter.distanceSummary(state), formatter.etaText(state))
                            .joinToString(" · ")
                            .takeIf { it.isNotBlank() },
                    )
                },
                // The island's progress bar is a travelled/total ratio, which a home walk lacks.
                hideProgress = isHomeWalk,
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
            AndroidLiveUpdateAdapter.styleSpec(state)?.let { spec ->
                AndroidLiveUpdateAdapter.apply(builder, spec, trackerIcon())
            }
        }

        return builder.build()
    }

    private fun contentIntent(): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE,
    )

    private fun servicePendingIntent(action: String, requestCode: Int): PendingIntent =
        PendingIntent.getService(
            context,
            requestCode,
            Intent(context, WalkingSimulationService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE,
        )

    private fun trackerIcon(): IconCompat = IconCompat.createWithResource(context, SMALL_ICON_RES)

    private companion object {
        const val SMALL_ICON_RES = android.R.drawable.ic_menu_mylocation
        const val REQUEST_PAUSE = 1
        const val REQUEST_RESUME = 2
        const val REQUEST_STOP = 3
    }
}
