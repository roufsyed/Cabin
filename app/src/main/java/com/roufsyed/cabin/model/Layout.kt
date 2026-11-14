package com.roufsyed.cabin.model

/** Which edge of the screen the side bar is on: Cabin's own rail, and the bar kept over other apps. */
enum class BarSide(val label: String) { Right("Right"), Left("Left") }

/** The three panels of the Home screen, in the order the design has them from left to right. */
enum class HomePanel(val label: String) {
    Clock("Clock and date"),
    Navigation("Navigation"),
    NowPlaying("Now playing"),
}

/** Where the Home panels go. Kept apart from the screen so it can be tested. */
object LayoutRules {

    /** The design's order: clock, navigation, now playing. */
    val DESIGN_ORDER: List<HomePanel> = HomePanel.entries.toList()

    /** The three places on Home, from left to right. */
    private val PLACES = listOf("Left", "Middle", "Right")

    /**
     * [order] with [panel] moved one place to the right, or from the right-hand place round to the
     * left. The other two keep their order, so three moves of one panel put everything back.
     */
    fun moved(order: List<HomePanel>, panel: HomePanel): List<HomePanel> {
        val from = order.indexOf(panel)
        if (from < 0) return order
        val others = order - panel
        val to = (from + 1) % order.size
        return others.take(to) + panel + others.drop(to)
    }

    /** A saved order, if it names each panel once; otherwise the design's. */
    fun valid(order: List<HomePanel>): List<HomePanel> =
        if (order.size == DESIGN_ORDER.size && order.toSet().size == DESIGN_ORDER.size) order else DESIGN_ORDER

    /** The name of the place [panel] has in [order]. */
    fun place(order: List<HomePanel>, panel: HomePanel): String = PLACES.getOrElse(order.indexOf(panel)) { PLACES.first() }
}
