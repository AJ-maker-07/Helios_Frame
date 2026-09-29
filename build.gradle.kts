// Top-level build file. Shared config lives in module build files.
// OPEN DETAIL: applicationId default is com.helios.frame (see app/build.gradle.kts).
// If you gave a custom ID, replace it there — no other file hardcodes it.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
