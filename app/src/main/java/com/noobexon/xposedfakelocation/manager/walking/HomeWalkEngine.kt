package com.noobexon.xposedfakelocation.manager.walking

import com.noobexon.xposedfakelocation.data.HOME_WALK_MAX_RADIUS_METERS
import com.noobexon.xposedfakelocation.data.HOME_WALK_MIN_RADIUS_METERS
import com.noobexon.xposedfakelocation.manager.route.Coordinate
import com.noobexon.xposedfakelocation.manager.route.PositionSnapshot
import com.noobexon.xposedfakelocation.manager.route.RouteProgressEngine
import com.noobexon.xposedfakelocation.manager.route.WalkEngine
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Open-ended "walk around the pinned coordinate" path (the home-walk mode).
 *
 * Waypoints are drawn from the annulus [minRadiusMeters]…[maxRadiusMeters] around [anchor] and
 * walked to in a straight line, so the published position always advances at the configured
 * walking speed and never leaves the radius — no route planning, network access or API key.
 *
 * The path is generated lazily: [positionAt] extends it only as far as the travelled distance
 * requires, and the same travelled distance always resolves to the same coordinate for a given
 * seed, keeping the service's tick loop (which only ever moves forward) fully deterministic.
 */
class HomeWalkEngine(
    private val anchor: Coordinate,
    private val minRadiusMeters: Double = HOME_WALK_MIN_RADIUS_METERS,
    private val maxRadiusMeters: Double = HOME_WALK_MAX_RADIUS_METERS,
    private val random: Random = Random.Default,
) : WalkEngine {

    init {
        require(anchor.isValid()) { "HomeWalkEngine needs a valid anchor" }
        require(minRadiusMeters > 0.0 && maxRadiusMeters >= minRadiusMeters) {
            "HomeWalkEngine needs 0 < minRadius <= maxRadius"
        }
    }

    /** The walk never ends on its own, so there is no finite total to report. */
    override val totalDistanceMeters: Double = Double.POSITIVE_INFINITY

    /** Waypoints from the anchor onwards; the first entry is the anchor itself. */
    private val points = mutableListOf(anchor)

    /** Cumulative distance in metres from the anchor to each entry of [points]. */
    private val cumulativeDistances = mutableListOf(0.0)

    override fun positionAt(distanceMeters: Double): PositionSnapshot {
        val target = distanceMeters.coerceAtLeast(0.0)
        while (cumulativeDistances.last() < target) appendWaypoint()

        if (points.size < 2) return PositionSnapshot(anchor, 0f, arrived = false)

        var endIndex = 1
        while (endIndex < cumulativeDistances.size - 1 && cumulativeDistances[endIndex] < target) endIndex++
        val startIndex = endIndex - 1

        val segmentStart = cumulativeDistances[startIndex]
        val segmentLength = cumulativeDistances[endIndex] - segmentStart
        val from = points[startIndex]
        val to = points[endIndex]

        val coordinate = if (segmentLength <= 0.0) {
            to
        } else {
            val fraction = ((target - segmentStart) / segmentLength).coerceIn(0.0, 1.0)
            Coordinate(
                latitude = from.latitude + (to.latitude - from.latitude) * fraction,
                longitude = from.longitude + (to.longitude - from.longitude) * fraction,
            )
        }

        return PositionSnapshot(
            coordinate = keepInsideAnnulus(coordinate),
            bearingDegrees = RouteProgressEngine.initialBearingDegrees(from, to),
            arrived = false,
        )
    }

    /**
     * Keeps the published position at least [minRadiusMeters] away from the anchor.
     *
     * A straight segment between two waypoints of the annulus may cut inside it, so the
     * interpolated point is pushed back out to the minimum radius along its own bearing. The
     * upper bound needs no clamping: a chord between two points of the outer circle stays inside
     * it.
     */
    private fun keepInsideAnnulus(coordinate: Coordinate): Coordinate {
        val distance = RouteProgressEngine.haversineMeters(anchor, coordinate)
        if (distance >= minRadiusMeters) return coordinate
        val bearing = if (distance <= 0.0) 0.0 else bearingFromAnchor(coordinate)
        return pointAroundAnchor(minRadiusMeters, bearing)
    }

    /**
     * Appends one waypoint drawn from the annulus around the anchor, skipping the degenerate
     * draw that would land on the current position (a zero-length segment would stall the walk).
     */
    private fun appendWaypoint() {
        repeat(MAX_WAYPOINT_DRAWS) {
            val candidate = pointAroundAnchor(random.nextDouble(minRadiusMeters, maxRadiusMeters), random.nextDouble(0.0, 2 * PI))
            val step = RouteProgressEngine.haversineMeters(points.last(), candidate)
            if (step > MIN_STEP_METERS) {
                points.add(candidate)
                cumulativeDistances.add(cumulativeDistances.last() + step)
                return
            }
        }
        // Deterministic fallback: diametrically opposite the current position at the mean radius.
        val previous = points.last()
        val bearing = if (previous == anchor) random.nextDouble(0.0, 2 * PI) else bearingFromAnchor(previous) + PI
        val fallback = pointAroundAnchor((minRadiusMeters + maxRadiusMeters) / 2.0, bearing)
        val step = RouteProgressEngine.haversineMeters(previous, fallback).coerceAtLeast(MIN_STEP_METERS)
        points.add(fallback)
        cumulativeDistances.add(cumulativeDistances.last() + step)
    }

    /** A point [distanceMeters] from the anchor at [bearingRadians] (clockwise from north). */
    private fun pointAroundAnchor(distanceMeters: Double, bearingRadians: Double): Coordinate {
        val angularDistance = distanceMeters / EARTH_RADIUS_METERS
        val latRad = Math.toRadians(anchor.latitude)
        val lonRad = Math.toRadians(anchor.longitude)

        val sinLat = sin(latRad) * cos(angularDistance) +
            cos(latRad) * sin(angularDistance) * cos(bearingRadians)
        val newLatRad = asin(sinLat.coerceIn(-1.0, 1.0))
        val newLonRad = lonRad + atan2(
            sin(bearingRadians) * sin(angularDistance) * cos(latRad),
            cos(angularDistance) - sin(latRad) * sin(newLatRad),
        )

        return Coordinate(
            latitude = Math.toDegrees(newLatRad).coerceIn(-90.0, 90.0),
            longitude = normalizeLongitude(Math.toDegrees(newLonRad)),
        )
    }

    /** Bearing from the anchor to [point], in radians clockwise from north. */
    private fun bearingFromAnchor(point: Coordinate): Double =
        Math.toRadians(
            RouteProgressEngine.initialBearingDegrees(anchor, point).toDouble()
        )

    private fun normalizeLongitude(degrees: Double): Double =
        ((degrees + 180.0) % 360.0 + 360.0) % 360.0 - 180.0

    private companion object {
        /**
         * Mean Earth radius — the same one [RouteProgressEngine.haversineMeters] uses, so a
         * waypoint generated "20 m out" measures exactly 20 m when the path is measured.
         */
        const val EARTH_RADIUS_METERS = 6371008.8

        /** Minimum segment length in metres; anything shorter would leave the walker standing. */
        const val MIN_STEP_METERS = 0.05

        /** Bounded retry budget for the random draw before the deterministic fallback kicks in. */
        const val MAX_WAYPOINT_DRAWS = 8
    }
}
