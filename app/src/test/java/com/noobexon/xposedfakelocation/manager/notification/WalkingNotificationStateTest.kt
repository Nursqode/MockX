package com.noobexon.xposedfakelocation.manager.notification

import com.noobexon.xposedfakelocation.manager.route.Coordinate
import com.noobexon.xposedfakelocation.manager.route.WalkingMode
import com.noobexon.xposedfakelocation.manager.route.WalkingPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WalkingNotificationStateTest {

    @Test
    fun `progress is zero at start, half way and clamped at the end`() {
        assertEquals(0, WalkingNotificationState.progressPerMille(0.0, 1000.0))
        assertEquals(500, WalkingNotificationState.progressPerMille(500.0, 1000.0))
        assertEquals(1000, WalkingNotificationState.progressPerMille(1000.0, 1000.0))
    }

    @Test
    fun `progress guards non-finite and non-positive totals`() {
        assertEquals(0, WalkingNotificationState.progressPerMille(10.0, 0.0))
        assertEquals(0, WalkingNotificationState.progressPerMille(10.0, -5.0))
        assertEquals(0, WalkingNotificationState.progressPerMille(Double.NaN, 100.0))
        assertEquals(0, WalkingNotificationState.progressPerMille(10.0, Double.POSITIVE_INFINITY))
    }

    @Test
    fun `progress never leaves the per-mille range`() {
        assertEquals(0, WalkingNotificationState.progressPerMille(-20.0, 100.0))
        assertEquals(1000, WalkingNotificationState.progressPerMille(120.0, 100.0))
    }

    @Test
    fun `percent derives from per-mille`() {
        val state = WalkingNotificationState(WalkingPhase.WALKING, 555, 555.0, 1000.0, null, 1)
        assertEquals(55, state.progressPercent)
    }

    @Test
    fun `walking state carries an ETA from remaining distance and speed`() {
        val state = WalkingNotificationState.fromSession(
            phase = WalkingPhase.WALKING,
            travelledMeters = 250.0,
            totalMeters = 1000.0,
            speedMetersPerSecond = 1.5f,
            sequence = 7,
        )
        assertEquals(((750.0) / 1.5).toLong(), state.remainingSeconds)
        assertEquals(7, state.sequence)
    }

    @Test
    fun `paused and arrived carry no ETA`() {
        val paused = WalkingNotificationState.fromSession(WalkingPhase.PAUSED, 250.0, 1000.0, 1.5f, 1)
        assertNull(paused.remainingSeconds)
        val arrived = WalkingNotificationState.fromSession(WalkingPhase.ARRIVED, 1000.0, 1000.0, 1.5f, 2)
        assertNull(arrived.remainingSeconds)
    }

    @Test
    fun `arrived pins the bar to full progress`() {
        val state = WalkingNotificationState.fromSession(WalkingPhase.ARRIVED, 998.7, 1000.0, 1.4f, 3)
        assertEquals(WalkingNotificationState.PROGRESS_MAX, state.progressPerMille)
        assertTrue(state.isActiveSession)
    }

    @Test
    fun `zero-speed walking has no ETA instead of dividing by zero`() {
        val state = WalkingNotificationState.fromSession(WalkingPhase.WALKING, 0.0, 100.0, 0f, 4)
        assertNull(state.remainingSeconds)
    }

    @Test
    fun `home walk carries no progress fraction and no ETA`() {
        val state = WalkingNotificationState.fromSession(
            phase = WalkingPhase.WALKING,
            travelledMeters = 120.0,
            totalMeters = 0.0,
            speedMetersPerSecond = 1.4f,
            sequence = 5,
            destination = Coordinate(55.751244, 37.618423),
            mode = WalkingMode.HOME,
        )

        assertEquals(0, state.progressPerMille)
        assertNull(state.remainingSeconds)
        assertEquals(WalkingMode.HOME, state.mode)
        assertTrue(state.isActiveSession)
        assertEquals(Coordinate(55.751244, 37.618423), state.destination)
    }

    @Test
    fun `action mapping follows the phase table`() {
        assertEquals(
            listOf(WalkingNotificationState.Action.PAUSE, WalkingNotificationState.Action.STOP),
            WalkingNotificationState.fromSession(WalkingPhase.WALKING, 0.0, 100.0, 1.4f, 1).actions(),
        )
        assertEquals(
            listOf(WalkingNotificationState.Action.RESUME, WalkingNotificationState.Action.STOP),
            WalkingNotificationState.fromSession(WalkingPhase.PAUSED, 0.0, 100.0, 1.4f, 2).actions(),
        )
        assertEquals(
            listOf(WalkingNotificationState.Action.STOP),
            WalkingNotificationState.fromSession(WalkingPhase.ARRIVED, 100.0, 100.0, 1.4f, 3).actions(),
        )
        assertTrue(WalkingNotificationState.fromSession(WalkingPhase.IDLE, 0.0, 0.0, 1.4f, 4).actions().isEmpty())
        assertTrue(WalkingNotificationState.fromSession(WalkingPhase.FAILED, 0.0, 0.0, 1.4f, 5).actions().isEmpty())
    }

    @Test
    fun `only session phases count as active`() {
        assertTrue(WalkingNotificationState.fromSession(WalkingPhase.WALKING, 0.0, 100.0, 1.4f, 1).isActiveSession)
        assertTrue(WalkingNotificationState.fromSession(WalkingPhase.PAUSED, 0.0, 100.0, 1.4f, 2).isActiveSession)
        assertTrue(WalkingNotificationState.fromSession(WalkingPhase.ARRIVED, 0.0, 100.0, 1.4f, 3).isActiveSession)
        assertFalse(WalkingNotificationState.fromSession(WalkingPhase.READY, 0.0, 100.0, 1.4f, 4).isActiveSession)
        assertFalse(WalkingNotificationState.fromSession(WalkingPhase.FAILED, 0.0, 100.0, 1.4f, 5).isActiveSession)
    }
}
