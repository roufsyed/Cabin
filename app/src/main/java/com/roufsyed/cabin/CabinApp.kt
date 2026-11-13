package com.roufsyed.cabin

import android.app.Application

class CabinApp : Application() {

    /**
     * Debug builds only: set before the first screen is drawn to show the design's own values instead
     * of live data, so the app can be compared with the design pixel for pixel.
     */
    @Volatile
    var showDesignValues = false

    @Volatile
    private var current: AppContainer? = null

    val container: AppContainer
        get() = current ?: synchronized(this) {
            current ?: create().also { current = it }
        }

    /** For tests: start the next screen from a known set of values. */
    fun replaceContainer(container: AppContainer) {
        current = container
    }

    private fun create(): AppContainer =
        if (BuildConfig.DEBUG && showDesignValues) AppContainer.design() else AppContainer.live(this)
}
