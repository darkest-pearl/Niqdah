buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        // The Google Services plugin needs google-services.json. Keep it optional for fresh clones.
        if (file("app/google-services.json").exists()) {
            classpath("com.google.gms:google-services:4.4.2")
        }
    }
}

plugins {
    id("com.android.application") version "8.6.1" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
}
