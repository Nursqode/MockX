package com.noobexon.xposedfakelocation.manager.notification

import com.noobexon.xposedfakelocation.manager.route.WalkingMode
import com.noobexon.xposedfakelocation.manager.route.WalkingPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationContentFormatterTest {

    private val texts = WalkingNotificationTexts(
        walkingTemplate = "Walked %s / %s",
        homeWalkingTemplate = "Around the spot · %s",
        paused = "Walk paused — position held",
        arrived = "Arrived at the destination",
        statusWalking = "Simulating walk",
        statusPaused = "Walk paused",
        statusArrived = "Arrived at destination",
    )

    @Test
    fun `distance formats metres below one kilometre`() {
        assertEquals("0 m", NotificationContentFormatter.formatDistance(0.0))
        assertEquals("850 m", NotificationContentFormatter.formatDistance(850.0))
        assertEquals("999 m", NotificationContentFormatter.formatDistance(999.4))
    }

    @Test
    fun `distance formats kilometres from one kilometre up`() {
        assertEquals("1.00 km", NotificationContentFormatter.formatDistance(1000.0))
        assertEquals("1.23 km", NotificationContentFormatter.formatDistance(1234.5))
        assertEquals("1.25 km", NotificationContentFormatter.formatDistance(1250.0))
    }

    @Test
    fun `non-finite or negative distance collapses to zero metres`() {
        assertEquals("0 m", NotificationContentFormatter.formatDistance(-3.0))
        assertEquals("0 m", NotificationContentFormatter.formatDistance(Double.NaN))
        assertEquals("0 m", NotificationContentFormatter.formatDistance(Double.POSITIVE_INFINITY))
    }

    @Test
    fun `walking text fills the distance template`() {
        val state = WalkingNotificationState(WalkingPhase.WALKING, 250, 250.0, 1000.0, null, 1)
        assertEquals("Walked 250 m / 1.00 km", NotificationTextResolver.contentText(state, texts))
    }

    @Test
    fun `paused and arrived use their dedicated lines`() {
        val paused = WalkingNotificationState(WalkingPhase.PAUSED, 250, 250.0, 1000.0, null, 1)
        assertEquals(texts.paused, NotificationTextResolver.contentText(paused, texts))
        val arrived = WalkingNotificationState(WalkingPhase.ARRIVED, 1000, 1000.0, 1000.0, null, 2)
        assertEquals(texts.arrived, NotificationTextResolver.contentText(arrived, texts))
    }

    @Test
    fun `home walk reports the travelled distance without a total`() {
        val state = WalkingNotificationState(
            phase = WalkingPhase.WALKING,
            progressPerMille = 0,
            travelledMeters = 250.0,
            totalMeters = 0.0,
            remainingSeconds = null,
            sequence = 1,
            mode = WalkingMode.HOME,
        )

        assertEquals("Around the spot · 250 m", NotificationTextResolver.contentText(state, texts))
        assertEquals("250 m", NotificationTextResolver.distanceSummary(state))
    }

    @Test
    fun `status label follows the phase`() {
        assertEquals(
            texts.statusWalking,
            NotificationTextResolver.statusLabel(
                WalkingNotificationState(WalkingPhase.WALKING, 0, 0.0, 100.0, null, 1),
                texts,
            ),
        )
        assertEquals(
            texts.statusPaused,
            NotificationTextResolver.statusLabel(
                WalkingNotificationState(WalkingPhase.PAUSED, 0, 0.0, 100.0, null, 2),
                texts,
            ),
        )
        assertEquals(
            texts.statusArrived,
            NotificationTextResolver.statusLabel(
                WalkingNotificationState(WalkingPhase.ARRIVED, 0, 0.0, 100.0, null, 3),
                texts,
            ),
        )
    }

    @Test
    fun `distance summary is the bare travelled-total pair`() {
        assertEquals(
            "250 m / 1.00 km",
            NotificationTextResolver.distanceSummary(250.0, 1000.0),
        )
    }

    @Test
    fun `eta under a minute produces no text line`() {
        val formatter = EtaProbe()
        assertNull(formatter.eta(WalkingNotificationState(WalkingPhase.WALKING, 0, 0.0, 100.0, 45, 1)))
        assertEquals(2L, formatter.eta(WalkingNotificationState(WalkingPhase.WALKING, 0, 0.0, 100.0, 130, 2)))
    }

    /** Exposes the minutes computation without needing an Android Context. */
    private class EtaProbe {
        fun eta(state: WalkingNotificationState): Long? =
            state.remainingSeconds?.takeIf { it >= 60 }?.let { it / 60 }
    }
}
