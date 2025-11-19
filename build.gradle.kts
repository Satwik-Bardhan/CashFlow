// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.google.gms.google.services) apply false

    // Updated to recent stable versions
    id("com.google.firebase.crashlytics") version "3.0.2" apply false
    id("com.google.firebase.firebase-perf") version "1.4.2" apply false
}

// NO repositories block here anymore - moved to settings.gradle.kts

tasks.register("clean", Delete::class) {
    // [FIX] Updated to use the new Gradle API (buildDir is deprecated)
    delete(rootProject.layout.buildDirectory)
}