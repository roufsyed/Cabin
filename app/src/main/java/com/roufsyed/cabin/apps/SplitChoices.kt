package com.roufsyed.cabin.apps

import com.roufsyed.cabin.model.AppEntry
import com.roufsyed.cabin.model.AppTile
import com.roufsyed.cabin.sources.SourceRules

/** What the chooser for a second app offers, and how it lays its tiles out in half a screen. */
object SplitChoices {

    /** The space between tiles, and a tile's height, as in the Apps grid. */
    const val GAP = 13f
    const val TILE_HEIGHT = 154f

    /** The narrowest a tile may be for its label to stay readable. In the Apps grid they are 197. */
    private const val NARROWEST_TILE = 150f

    /** The Apps grid has five tiles across; a full screen gets no more. */
    private const val MOST_COLUMNS = 5

    /**
     * The apps that can be opened beside what is on screen: the ones the Apps grid shows, in its
     * order, without Cabin itself and without anything of the packages [onScreen], which are there
     * already.
     */
    fun of(visible: List<AppTile>, ownId: String, onScreen: Collection<String>): List<AppEntry> =
        visible.map { it.app }.filter { app -> app.id != ownId && SourceRules.packageOf(app.id) !in onScreen }

    /** How many tiles fit across and down in a space [width] by [height] design pixels. Never none. */
    fun grid(width: Float, height: Float): Grid = Grid(
        columns = ((width + GAP) / (NARROWEST_TILE + GAP)).toInt().coerceIn(1, MOST_COLUMNS),
        rows = ((height + GAP) / (TILE_HEIGHT + GAP)).toInt().coerceAtLeast(1),
    )

    data class Grid(val columns: Int, val rows: Int) {
        val perPage: Int get() = columns * rows

        fun pages(count: Int): Int = if (count == 0) 1 else (count + perPage - 1) / perPage

        fun <T> page(items: List<T>, page: Int): List<T> = items.drop(page * perPage).take(perPage)
    }
}
