package com.roufsyed.cabin.gesture

import kotlin.math.abs
import kotlin.math.roundToInt

/** What a finger movement on one of Cabin's own screens means. */
sealed interface Gesture {
    /** One finger, mostly vertical or horizontal, past the swipe distance. Fired once per touch. */
    data object SwipeDown : Gesture
    data object SwipeUp : Gesture
    data object SwipeLeft : Gesture
    data object SwipeRight : Gesture

    /**
     * Two fingers started dragging: the readout should appear. The drag changes the volume unless
     * Settings > Gestures gives it something else to change, hence the names.
     */
    data object VolumeBegin : Gesture

    /** Two fingers have moved [steps] steps from where the drag began; up is positive. */
    data class Volume(val steps: Int) : Gesture

    /** The fingers lifted: the readout should go. */
    data object VolumeEnd : Gesture
}

/**
 * Turns raw touches into [Gesture]s, with the design's distances: a touch counts as a drag after 12
 * pixels, a swipe fires at 70, and two fingers move one step every 24.
 *
 * It has no Android in it. Distances are in whatever unit the caller's coordinates use, so the caller
 * passes thresholds scaled to the screen.
 *
 * The rule that a swipe must never press the button under it is enforced through [claimed]: once it
 * is true the caller keeps the touch away from the buttons. For a short while after a swipe, new
 * touches are claimed too, so a finger landing at the end of a swipe cannot tap anything either.
 */
class GestureRecognizer(
    private val slop: Float = 12f,
    private val swipeDistance: Float = 70f,
    private val volumeStep: Float = 24f,
    private val tapBlockMs: Long = 400L,
) {
    private class Touch(val primary: Long, val startX: Float, val startY: Float, val blocked: Boolean) {
        val pointers = ArrayList<Long>(2).also { it.add(primary) }
        var y = startY
        var moved = false
        var fired = false
        var twoFingers = false
        var volumeFromY = startY
        var volumeShown = false
        var lastSteps = 0
    }

    private var touch: Touch? = null
    private var swipedAtMs = Long.MIN_VALUE / 2

    /**
     * True while the current touch belongs to a gesture and must not reach the buttons: it has moved
     * past the drag distance, a second finger is down, or it began just after a swipe.
     */
    val claimed: Boolean
        get() = touch?.let { it.moved || it.twoFingers || it.blocked } ?: false

    fun down(pointer: Long, x: Float, y: Float, timeMs: Long): List<Gesture> {
        val current = touch
        if (current == null || pointer in current.pointers) {
            // A first finger, or the system re-reporting one after losing track: start afresh.
            touch = Touch(pointer, x, y, blocked = timeMs - swipedAtMs <= tapBlockMs)
            return emptyList()
        }
        current.pointers.add(pointer)
        if (!current.twoFingers && !current.fired) {
            // A second finger turns the touch into a two-finger drag, measured from where the first one is now.
            current.twoFingers = true
            current.volumeFromY = current.y
        }
        return emptyList()
    }

    fun move(pointer: Long, x: Float, y: Float, timeMs: Long): List<Gesture> {
        val current = touch ?: return emptyList()
        if (pointer != current.primary) return emptyList()
        val dx = x - current.startX
        val dy = y - current.startY
        current.y = y
        if (abs(dx) > slop || abs(dy) > slop) current.moved = true
        if (!current.moved || current.fired) return emptyList()

        if (current.twoFingers) {
            val steps = ((current.volumeFromY - y) / volumeStep).roundToInt()
            val events = ArrayList<Gesture>(2)
            if (!current.volumeShown) {
                current.volumeShown = true
                events += Gesture.VolumeBegin
            }
            if (steps != current.lastSteps) {
                current.lastSteps = steps
                events += Gesture.Volume(steps)
            }
            return events
        }

        val ax = abs(dx)
        val ay = abs(dy)
        if (maxOf(ax, ay) < swipeDistance) return emptyList()
        current.fired = true
        swipedAtMs = timeMs
        return listOf(
            when {
                ay > ax -> if (dy > 0) Gesture.SwipeDown else Gesture.SwipeUp
                dx < 0 -> Gesture.SwipeLeft
                else -> Gesture.SwipeRight
            },
        )
    }

    fun up(pointer: Long, timeMs: Long): List<Gesture> {
        val current = touch ?: return emptyList()
        if (!current.pointers.remove(pointer)) return emptyList()
        if (current.pointers.isNotEmpty()) return emptyList()
        touch = null
        if (current.moved) swipedAtMs = timeMs
        return if (current.volumeShown) listOf(Gesture.VolumeEnd) else emptyList()
    }

    /** The system took the touch away (for example to show its own bars). */
    fun cancel(): List<Gesture> {
        val current = touch ?: return emptyList()
        touch = null
        return if (current.volumeShown) listOf(Gesture.VolumeEnd) else emptyList()
    }
}
