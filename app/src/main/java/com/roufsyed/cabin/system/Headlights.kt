package com.roufsyed.cabin.system

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Whether the car's headlights are on, as far as the unit tells apps. The unit learns it from the
 * illumination wire; how, or whether, it passes that on is specific to each vendor, so the rest of
 * Cabin only ever sees this.
 */
interface HeadlightSignal {
    /** True or false while the unit reports the headlights; null when it offers no such signal. */
    val on: StateFlow<Boolean?>
}

/**
 * A unit that gives apps no headlight signal. Auto brightness then goes by sunrise and sunset. This
 * is what is used until the real unit has been looked at: nothing about its signal is assumed.
 */
object NoHeadlightSignal : HeadlightSignal {
    override val on: StateFlow<Boolean?> = MutableStateFlow(null)
}

/** Headlights switched by hand: for the demo launch and for tests. */
class FixedHeadlights(initial: Boolean? = false) : HeadlightSignal {
    private val state = MutableStateFlow(initial)
    override val on: StateFlow<Boolean?> = state

    fun set(value: Boolean?) {
        state.value = value
    }
}
