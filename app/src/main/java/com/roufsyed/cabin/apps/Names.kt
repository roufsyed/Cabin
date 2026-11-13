package com.roufsyed.cabin.apps

/** Matching an app's name against a keyword, strictly enough that "Gramophone" is not a phone app. */
object Names {
    private val separators = Regex("[^a-z0-9]+")

    fun words(label: String): List<String> = label.lowercase().split(separators).filter { it.isNotEmpty() }

    /**
     * True when [key] is a whole word of [label], or, for keys of four letters or more, the start of
     * one ("radio" matches "RadioDroid"). A key of several words must appear as that phrase.
     */
    fun matches(label: String, key: String): Boolean {
        val words = words(label)
        return if (' ' in key) {
            " ${words.joinToString(" ")} ".contains(" $key ")
        } else {
            words.any { it == key || (key.length >= 4 && it.startsWith(key)) }
        }
    }
}
