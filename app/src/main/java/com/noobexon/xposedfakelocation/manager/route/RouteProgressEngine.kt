package com.noobexon.xposedfakelocation.manager.route

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Source of walker positions for the walking service: answers "where is the walker after N
 * metres of walking".
 *
 * [RouteProgressEngine] resolves a finite, pre-computed polyline; [HomeWalkEngine] generates an
 * open-ended path around a fixed anchor and reports an infinite total length.
 */
interface WalkEngine {
    /** Total path length in metres, or [Double.POSITIVE_INFINITY] for open-ended paths. */
    val totalDistanceMeters: Double

    /** Position, bearing and arrival flag for [distanceMeters] metres travelled along the path. */
    fun positionAt(distanceMeters: Double): PositionSnapshot
}

/**
 * Geometry for walking along a parsed route.
 *
 * Owns the cumulative-distance table for the route's trajectory points and answers
 * "where am I after N metres of walking" with linear interpolation between the two points
 * bracketing that distance, plus the initial bearing of the segment being walked.
 *
 * Pure Kotlin (no Android framework calls): the service feeds it real elapsed time, unit
 * tests feed fixed values.
 */
class RouteProgressEngine(private val points: List<Coordinate>) : WalkEngine {

    /** Cumulative distance (metres) from the origin to each point; same length as [points]. */
    val cumulativeDistances: DoubleArray

    /** Total path length in metres; always > 0 for a valid route. */
    override val totalDistanceMeters: Double

    init {
        require(points.size >= 2) { "RouteProgressEngine needs at least 2 points" }
        val cumulative = DoubleArray(points.size)
        var total = 0.0
        for (i in 1 until points.size) {
            total += haversineMeters(points[i - 1], points[i])
            cumulative[i] = total
        }
        cumulativeDistances = cumulative
        totalDistanceMeters = total
    }

    /**
     * Position, bearing and arrival flag for [distanceMeters] metres travelled along the route.
     *
     * The distance is clamped to [0, totalDistanceMeters]: values past the end snap to the
     * destination with [PositionSnapshot.arrived] = true. The bearing at the very start uses
     * the first segment; at the end it keeps the bearing of the final segment instead of
     * defaulting to 0 (north).
     */
    override fun positionAt(distanceMeters: Double): PositionSnapshot {
        val target = distanceMeters.coerceIn(0.0, totalDistanceMeters)

        // Binary search for the first point whose cumulative distance >= target.
        var low = 0
        var high = cumulativeDistances.size - 1
        while (low < high) {
            val mid = (low + high) / 2
            if (cumulativeDistances[mid] < target) low = mid + 1 else high = mid
        }
        val endIndex = low.coerceAtLeast(1)
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

        val arrived = distanceMeters >= totalDistanceMeters
        val bearingDegrees = if (arrived && startIndex == endIndex) {
            initialBearingDegrees(points[points.size - 2], points.last())
        } else {
            initialBearingDegrees(from, to)
        }

        return PositionSnapshot(coordinate, bearingDegrees, arrived)
    }

    companion object {
        /**
         * Initial bearing (degrees clockwise from true north) when walking from [from] to [to];
         * `0f` for a degenerate segment.
         */
        fun initialBearingDegrees(from: Coordinate, to: Coordinate): Float {
            if (from == to) return 0f
            val lat1 = Math.toRadians(from.latitude)
            val lat2 = Math.toRadians(to.latitude)
            val deltaLon = Math.toRadians(to.longitude - from.longitude)
            val y = sin(deltaLon) * cos(lat2)
            val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(deltaLon)
            val degrees = Math.toDegrees(atan2(y, x))
            return ((degrees % 360.0) + 360.0).let { if (it >= 360.0) it - 360.0 else it }.toFloat()
        }

        /** Great-circle distance in metres between two WGS-84 points (Haversine formula). */
        fun haversineMeters(a: Coordinate, b: Coordinate): Double {
            val earthRadius = 6371008.8 // mean Earth radius, metres
            val lat1 = Math.toRadians(a.latitude)
            val lat2 = Math.toRadians(b.latitude)
            val dLat = Math.toRadians(b.latitude - a.latitude)
            val dLon = Math.toRadians(b.longitude - a.longitude)
            val h = sin(dLat / 2) * sin(dLat / 2) + cos(lat1) * cos(lat2) * sin(dLon / 2) * sin(dLon / 2)
            return 2 * earthRadius * atan2(sqrt(h), sqrt(1 - h))
        }

        /** Total length of a polyline of [points] in metres; 0.0 for fewer than 2 points. */
        fun totalDistanceMeters(points: List<Coordinate>): Double {
            if (points.size < 2) return 0.0
            var total = 0.0
            for (i in 1 until points.size) total += haversineMeters(points[i - 1], points[i])
            return total
        }
    }
}

/** Result of resolving a travelled distance against the route. */
data class PositionSnapshot(
    val coordinate: Coordinate,
    val bearingDegrees: Float,
    val arrived: Boolean,
)
