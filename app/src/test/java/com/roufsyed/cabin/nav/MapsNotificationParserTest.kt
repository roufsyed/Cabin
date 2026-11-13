package com.roufsyed.cabin.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MapsNotificationParserTest {

    private fun parse(title: String?, text: String?, subText: String?) =
        MapsNotificationParser.parse(RawNavigation(title, text, subText))

    private val nbsp = " "

    // ---- Samples captured from Google Maps 11.129 navigating on the emulator (US English).

    @Test
    fun `a captured notification at the start of a route`() {
        assertEquals(
            ParsedNavigation("0 ft", "N De Anza Blvd", "Arrive 3:26 PM", "14 min · 8.3 mi"),
            parse("0${nbsp}ft", "toward N De Anza Blvd", "14 min · 8.3${nbsp}mi · 3:26 PM${nbsp}PST ETA"),
        )
    }

    @Test
    fun `a captured notification approaching a junction`() {
        assertEquals(
            ParsedNavigation("200 ft", "I-280 N", "Arrive 3:25 PM", "13 min · 8.1 mi"),
            parse("200${nbsp}ft", "I-280 N", "13 min · 8.1${nbsp}mi · 3:25 PM${nbsp}PST ETA"),
        )
    }

    @Test
    fun `a captured notification with a signposted direction`() {
        assertEquals(
            ParsedNavigation("0.2 mi", "I-280 N / San Francisco", "Arrive 3:25 PM", "13 min · 8.2 mi"),
            parse("0.2${nbsp}mi", "I-280 N  / San Francisco", "13 min · 8.2${nbsp}mi · 3:25 PM${nbsp}PST ETA"),
        )
    }

    // ---- The same layout as it reads in metric units and a 24-hour clock, as on the unit.

    @Test
    fun `metric distances and a 24 hour arrival, as the design shows them`() {
        assertEquals(
            ParsedNavigation("350 m", "Ring Road", "Arrive 10:48", "38 min · 17 km"),
            parse("350${nbsp}m", "toward Ring Road", "38 min · 17${nbsp}km · 10:48 ETA"),
        )
    }

    @Test
    fun `a long trip keeps its hours and minutes together`() {
        assertEquals(
            ParsedNavigation("1.2 km", "Airport Road", "Arrive 11:02 am", "1 hr 5 min · 82 km"),
            parse("1.2 km", "onto Airport Road", "1 hr 5 min · 82 km · 11:02 am IST ETA"),
        )
    }

    @Test
    fun `a decimal comma is a distance too`() {
        assertEquals("1,2 km", parse("1,2 km", "Ring Road", "5 min · 3,4 km · 18.05 ETA")?.distance)
        assertEquals("Arrive 18.05", parse("1,2 km", "Ring Road", "5 min · 3,4 km · 18.05 ETA")?.arrive)
    }

    // ---- Tolerance for a layout that shifts.

    @Test
    fun `other separators and a different order of parts are still read`() {
        assertEquals(
            ParsedNavigation("350 m", "Ring Road", "Arrive 10:48", "38 min · 17 km"),
            parse("350 m", "Ring Road", "10:48 ETA • 17 km • 38 min"),
        )
        assertEquals(
            ParsedNavigation("350 m", "Ring Road", "Arrive 10:48", "38 min · 17 km"),
            parse("350 m", "Ring Road", "38 min - 17 km - 10:48"),
        )
    }

    @Test
    fun `the older layout with distance and road together in the title`() {
        assertEquals(
            ParsedNavigation("200 m", "Ring Road", "Arrive 10:48", "38 min · 17 km"),
            parse("200 m - Ring Road", null, "38 min · 17 km · 10:48 ETA"),
        )
    }

    @Test
    fun `a missing arrival line leaves those two lines blank but keeps the manoeuvre`() {
        assertEquals(ParsedNavigation("350 m", "Ring Road", "", ""), parse("350 m", "Ring Road", null))
        assertEquals(ParsedNavigation("350 m", "Ring Road", "", "38 min"), parse("350 m", "Ring Road", "38 min"))
        assertEquals(ParsedNavigation("350 m", "Ring Road", "", "17 km"), parse("350 m", "Ring Road", "17 km"))
    }

    // ---- Anything that is not a readable manoeuvre means "no route".

    @Test
    fun `a notification without a distance is not a route`() {
        assertNull(parse("Rerouting…", "Ring Road", "38 min · 17 km · 10:48 ETA"))
        assertNull(parse("Searching for GPS", null, null))
        assertNull(parse(null, null, null))
        assertNull(parse("", "", ""))
    }

    @Test
    fun `a distance with no road is not a route`() {
        assertNull(parse("350 m", null, "38 min · 17 km · 10:48 ETA"))
        assertNull(parse("350 m", "   ", null))
    }

    @Test
    fun `other Maps notifications are not routes`() {
        assertNull(parse("Location sharing is on", "Sharing with 2 people", null))
        assertNull(parse("Downloading offline map", "45%", null))
        assertNull(parse("Navigate to work", "24 min with usual traffic", null))
        // A time has the shape of a distance, but it is not one.
        assertNull(parse("24 min", "to work, usual traffic", null))
    }

    // ---- Recent destinations for "Where to?"

    @Test
    fun `the newest destination goes first and only two are kept`() {
        assertEquals(listOf("Airport"), recentPlaces(emptyList(), "Airport"))
        assertEquals(listOf("City Mall", "Airport"), recentPlaces(listOf("Airport"), "City Mall"))
        assertEquals(listOf("Station", "City Mall"), recentPlaces(listOf("City Mall", "Airport"), "Station"))
    }

    @Test
    fun `using a place again moves it to the front instead of listing it twice`() {
        assertEquals(listOf("Airport", "City Mall"), recentPlaces(listOf("City Mall", "Airport"), "airport "))
    }

    @Test
    fun `an empty destination changes nothing`() {
        assertEquals(listOf("Airport"), recentPlaces(listOf("Airport"), "  "))
    }
}
