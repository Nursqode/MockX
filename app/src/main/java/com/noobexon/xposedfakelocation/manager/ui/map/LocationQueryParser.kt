package com.noobexon.xposedfakelocation.manager.ui.map

/** Valid latitude values accepted by the "Go to point" and "Add to favorites" dialogs. */
private val LATITUDE_RANGE = -90.0..90.0

/** Valid longitude values accepted by the "Add to favorites" dialog. */
private val LONGITUDE_RANGE = -180.0..180.0

/** How the combined coordinates-or-IP "Go to point" field was interpreted. */
sealed interface LocationQuery {
    /** Two in-range numbers, ready to be placed on the map. */
    data class Coordinates(val latitude: Double, val longitude: Double) : LocationQuery

    /** An IP literal whose location still has to be resolved. */
    data class IpAddress(val value: String) : LocationQuery

    /** Neither coordinates nor an IP literal; the dialog shows a validation error. */
    data object Invalid : LocationQuery
}

/**
 * Parser for the single "Go to point" input field, which accepts either coordinates
 * ("-15.029552, 40.2055142", also split by `;` or whitespace) or an IP address whose approximate
 * location is looked up afterwards.
 *
 * Pure Kotlin so the accepted formats are unit-testable without Android or network access.
 */
object LocationQueryParser {

    /**
     * Deliberately a shape check rather than DNS resolution: host names and city names must not be
     * sent to the lookup service, and the field is documented as coordinates-or-IP. A malformed
     * numeric literal that slips through is rejected by the lookup itself.
     */
    private val IP_LITERAL_REGEX = Regex("^[0-9A-Fa-f:.]+$")

    fun parse(query: String): LocationQuery {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return LocationQuery.Invalid

        parseCoordinates(trimmed)?.let { return it }
        return if (isIpAddress(trimmed)) LocationQuery.IpAddress(trimmed) else LocationQuery.Invalid
    }

    private fun parseCoordinates(query: String): LocationQuery.Coordinates? {
        val parts = query
            .replace(';', ',')
            .split(Regex("[,\\s]+"))
            .filter { it.isNotBlank() }
        if (parts.size != 2) return null

        val latitude = parts[0].toDoubleOrNull() ?: return null
        val longitude = parts[1].toDoubleOrNull() ?: return null
        if (latitude !in LATITUDE_RANGE || longitude !in LONGITUDE_RANGE) return null

        return LocationQuery.Coordinates(latitude, longitude)
    }

    private fun isIpAddress(value: String): Boolean =
        value.matches(IP_LITERAL_REGEX) && (value.contains('.') || value.contains(':'))
}
