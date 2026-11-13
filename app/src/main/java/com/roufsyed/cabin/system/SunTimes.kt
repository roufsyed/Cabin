package com.roufsyed.cabin.system

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.tan

/**
 * Sunrise and sunset, worked out on the unit from a date and a position. Used to switch between day
 * and night brightness when the car's headlight signal is not available to apps.
 *
 * This is the standard sunrise equation; it is good to a few minutes, which is plenty for dimming a
 * screen. Nothing is fetched from the network.
 */
object SunTimes {

    data class Day(val sunrise: ZonedDateTime, val sunset: ZonedDateTime)

    /** Used when no position is known: a plain 6:30 to 18:30 day. */
    val fallbackSunrise: LocalTime = LocalTime.of(6, 30)
    val fallbackSunset: LocalTime = LocalTime.of(18, 30)

    /**
     * Sunrise and sunset on [date] at the given position, in [zone]. Null where the sun does not rise or
     * does not set that day (polar summer and winter).
     */
    fun of(date: LocalDate, latitude: Double, longitude: Double, zone: ZoneId): Day? {
        val rise = eventUtcHours(date, latitude, longitude, rising = true) ?: return null
        val set = eventUtcHours(date, latitude, longitude, rising = false) ?: return null
        return Day(toZoned(date, rise, zone), toZoned(date, set, zone))
    }

    /** True when [now] is before sunrise or after sunset at the given position. */
    fun isNight(now: ZonedDateTime, latitude: Double?, longitude: Double?): Boolean {
        if (latitude != null && longitude != null) {
            val day = of(now.toLocalDate(), latitude, longitude, now.zone)
            if (day != null) {
                // Compare times of day so a sunrise or sunset computed as falling just past midnight
                // in local time still lands on the right side.
                val time = now.toLocalTime()
                val sunrise = day.sunrise.toLocalTime()
                val sunset = day.sunset.toLocalTime()
                return if (sunrise < sunset) time < sunrise || time >= sunset else time >= sunset && time < sunrise
            }
        }
        val time = now.toLocalTime()
        return time < fallbackSunrise || time >= fallbackSunset
    }

    /**
     * The event's time of day in [zone], placed on [date]. The equation gives a time of day in UTC;
     * far from Greenwich that falls on the neighbouring UTC date, which must not shift the local one.
     */
    private fun toZoned(date: LocalDate, utcHours: Double, zone: ZoneId): ZonedDateTime {
        val seconds = Math.round(utcHours * 3600.0)
        val local = date.atStartOfDay(ZoneId.of("UTC")).plusSeconds(seconds).withZoneSameInstant(zone).toLocalTime()
        return date.atTime(local).atZone(zone)
    }

    /** Hours after 00:00 UTC on [date] at which the sun crosses the horizon. May fall outside 0..24. */
    private fun eventUtcHours(date: LocalDate, latitude: Double, longitude: Double, rising: Boolean): Double? {
        val dayOfYear = date.dayOfYear
        val lngHour = longitude / 15.0
        val t = dayOfYear + ((if (rising) 6.0 else 18.0) - lngHour) / 24.0

        val meanAnomaly = 0.9856 * t - 3.289
        var trueLongitude = meanAnomaly + 1.916 * sin(rad(meanAnomaly)) + 0.020 * sin(rad(2 * meanAnomaly)) + 282.634
        trueLongitude = normalise(trueLongitude, 360.0)

        var rightAscension = deg(kotlin.math.atan(0.91764 * tan(rad(trueLongitude))))
        rightAscension = normalise(rightAscension, 360.0)
        val lQuadrant = floor(trueLongitude / 90.0) * 90.0
        val raQuadrant = floor(rightAscension / 90.0) * 90.0
        rightAscension = (rightAscension + (lQuadrant - raQuadrant)) / 15.0

        val sinDeclination = 0.39782 * sin(rad(trueLongitude))
        val cosDeclination = cos(asin(sinDeclination))

        // 90.833 degrees: the sun's upper edge on the horizon, allowing for refraction.
        val cosHourAngle = (cos(rad(90.833)) - sinDeclination * sin(rad(latitude))) /
            (cosDeclination * cos(rad(latitude)))
        if (cosHourAngle > 1.0 || cosHourAngle < -1.0) return null

        val hourAngle = (if (rising) 360.0 - deg(acos(cosHourAngle)) else deg(acos(cosHourAngle))) / 15.0
        val localMeanTime = hourAngle + rightAscension - 0.06571 * t - 6.622
        return normalise(localMeanTime - lngHour, 24.0)
    }

    private fun rad(degrees: Double) = Math.toRadians(degrees)
    private fun deg(radians: Double) = Math.toDegrees(radians)
    private fun normalise(value: Double, range: Double): Double = ((value % range) + range) % range
}
