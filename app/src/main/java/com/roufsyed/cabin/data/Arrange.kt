package com.roufsyed.cabin.data

/** The rules behind "tap one, then move or hide it", shared by the Apps grid and the source row. */
object Arrange {

    /**
     * [present] in the [saved] order. Anything not in the saved order (a newly installed app) follows,
     * in the order it is given. Saved entries that are no longer present are dropped.
     */
    fun <T> ordered(saved: List<T>, present: List<T>): List<T> {
        val presentSet = present.toSet()
        val known = LinkedHashSet<T>()
        for (item in saved) if (item in presentSet) known.add(item)
        return known.toList() + present.filter { it !in known }
    }

    /** Swaps [item] with its neighbour [delta] places away (-1 earlier, +1 later). No-op at the ends. */
    fun <T> move(order: List<T>, item: T, delta: Int): List<T> {
        val from = order.indexOf(item)
        val to = from + delta
        if (from < 0 || to !in order.indices) return order
        return order.toMutableList().also {
            it[from] = order[to]
            it[to] = item
        }
    }

    fun <T> toggle(hidden: Set<T>, item: T): Set<T> = if (item in hidden) hidden - item else hidden + item

    /** A source can be hidden only while another one stays visible. */
    fun <T> canHide(order: List<T>, hidden: Set<T>, item: T): Boolean =
        item in hidden || order.any { it != item && it !in hidden }
}
