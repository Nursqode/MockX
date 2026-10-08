package com.noobexon.xposedfakelocation.manager.walking

import com.noobexon.xposedfakelocation.manager.route.Coordinate
import com.noobexon.xposedfakelocation.manager.route.RouteProgressEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests the tick-advance behaviour of [WalkingSimulationService.advance] in isolation.
 * The service instance is never started — [advance] only touches the travelled-distance
 * cursor and the given engine, so it is safe to exercise on a bare object.
 */
class WalkingAdvanceTest {

    // ~222 m straight north.
    private val engine = RouteProgressEngine(
        listOf(Coordinate(0.0000, 0.0000), Coordinate(0.0020, 0.0000))
    )

    @Test
    fun `one second at walking speed advances by that distance`() {
        val service = WalkingSimulationService()

        val snapshot = service.advance(engine, elapsedSeconds = 1.0, speedMetersPerSecond = 1.4f)

        assertEquals(1.4, serviceDistance(service), 1e-6)
        assertFalse(snapshot.arrived)
    }

    @Test
    fun `zero elapsed (paused) does not move`() {
        val service = WalkingSimulationService()

        service.advance(engine, 1.0, 1.4f)
        val paused = service.advance(engine, 0.0, 1.4f)

        assertEquals(1.4, serviceDistance(service), 1e-6)
        assertFalse(paused.arrived)
    }

    @Test
    fun `very long delay is capped instead of teleporting`() {
        val service = WalkingSimulationService()

        // One hour of scheduler starvation must not jump 5 km.
        service.advance(engine, elapsedSeconds = 3600.0, speedMetersPerSecond = 1.4f)

        assertEquals(30.0 * 1.4, serviceDistance(service), 1e-6)
    }

    @Test
    fun `travelled distance never exceeds the route length`() {
        val service = WalkingSimulationService()

        repeat(200) { service.advance(engine, 1.0, 1.4f) }
        val snapshot = service.advance(engine, 1.0, 1.4f)

        assertEquals(engine.totalDistanceMeters, serviceDistance(service), 1e-6)
        assertTrue(snapshot.arrived)
        // After arrival the resolved position must sit exactly on the route's final point.
        assertEquals(engine.positionAt(engine.totalDistanceMeters).coordinate, snapshot.coordinate)
    }

    @Test
    fun `an endless home walk never arrives and stays near its anchor`() {
        val service = WalkingSimulationService()
        val anchor = Coordinate(55.751244, 37.618423)
        val homeEngine = HomeWalkEngine(anchor)

        // One minute of continuous walking, then a capped catch-up tick.
        repeat(60) { service.advance(homeEngine, 1.0, 1.4f) }
        val snapshot = service.advance(homeEngine, elapsedSeconds = 3600.0, speedMetersPerSecond = 1.4f)

        assertFalse(snapshot.arrived)
        // 60 one-second ticks plus one 30 s catch-up tick at 1.4 m/s (float speed accumulates).
        assertEquals(60.0 * 1.4 + 30.0 * 1.4, serviceDistance(service), 1e-4)
        val fromAnchor = RouteProgressEngine.haversineMeters(anchor, snapshot.coordinate)
        assertTrue("distance from anchor=$fromAnchor", fromAnchor in 1.0..20.0)
    }

    private fun serviceDistance(service: WalkingSimulationService): Double {
        val field = WalkingSimulationService::class.java.getDeclaredField("distanceTravelledMeters")
        field.isAccessible = true
        return field.getDouble(service)
    }
}
