plugins {
    alias(libs.plugins.android.application) apply false
    // Not applied anywhere: it only pins the Kotlin version used by the Android plugin's built-in Kotlin support.
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
