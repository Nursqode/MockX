package com.noobexon.xposedfakelocation.manager.walking

import com.noobexon.xposedfakelocation.data.HOME_WALK_MAX_RADIUS_METERS
import com.noobexon.xposedfakelocation.data.HOME_WALK_MIN_RADIUS_METERS
import com.noobexon.xposedfakelocation.manager.route.Coordinate
import com.noobexon.xposedfakelocation.manager.route.RouteProgressEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Tests the home-walk path generator: the walker must stay 1…20 m from the anchor, keep moving
 * smoothly at the configured pace and never report an arrival.
 */
class HomeWalkEngineTest {

    private val anchor = Coordinate(55.751244, 37.618423)

    private fun engine(seed: Int = 42) = HomeWalkEngine(anchor, random = Random(seed))

    @Test
    fun `every published position stays inside the home radius of the anchor`() {
        val engine = engine()
        var distance = 0.0

        repeat(2_000) {
            distance += 1.4
            val fromAnchor = RouteProgressEngine.haversineMeters(anchor, engine.positionAt(distance).coordinate)

            // The generator clamps the interpolated point back onto the annulus, so the bounds hold
            // exactly; the epsilon only absorbs double rounding.
            assertTrue("distance from anchor=$fromAnchor", fromAnchor >= HOME_WALK_MIN_RADIUS_METERS - 1e-6)
            assertTrue("distance from anchor=$fromAnchor", fromAnchor <= HOME_WALK_MAX_RADIUS_METERS + 1e-6)
        }
    }

    @Test
    fun `the walker never jumps further than the distance it actually walked`() {
        val engine = engine()
        var previous = engine.positionAt(1.0).coordinate

        for (step in 2..60) {
            val current = engine.positionAt(step.toDouble()).coordinate
            val moved = RouteProgressEngine.haversineMeters(previous, current)

            // One metre walked, plus at most one clamp correction onto the minimum radius.
            assertTrue("moved=$moved", moved <= 1.0 + HOME_WALK_MIN_RADIUS_METERS + 1e-6)
            previous = current
        }
    }

    @Test
    fun `an open-ended walk reports no total distance and never arrives`() {
        val engine = engine()

        assertTrue(engine.totalDistanceMeters.isInfinite())
        assertFalse(engine.positionAt(0.0).arrived)
        assertFalse(engine.positionAt(1_000_000.0).arrived)
    }

    @Test
    fun `the same travelled distance always resolves to the same coordinate`() {
        val engine = engine()
        val first = engine.positionAt(37.5).coordinate

        engine.positionAt(500.0)

        assertEquals(first, engine.positionAt(37.5).coordinate)
    }

    @Test
    fun `different seeds produce different paths`() {
        val first = engine(seed = 1).positionAt(30.0).coordinate
        val second = engine(seed = 2).positionAt(30.0).coordinate

        assertTrue(first != second)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a non-finite anchor is rejected`() {
        HomeWalkEngine(Coordinate(Double.NaN, 0.0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `an inverted radius range is rejected`() {
        HomeWalkEngine(anchor, minRadiusMeters = 20.0, maxRadiusMeters = 1.0)
    }
}
