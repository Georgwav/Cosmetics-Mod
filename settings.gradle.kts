pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/") { name = "Fabric" }
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    id("dev.kikugie.stonecutter") version "0.9.8"
}

stonecutter {
    create(rootProject) {
        // One jar per entry. Each jar covers the Minecraft range set in versions/<entry>/gradle.properties
        // (mod.mc_dep), and CI runs it in-game on every patch version of that range (ci/versions.json).
        versions("1.21.1", "1.21.3", "1.21.4", "1.21.5", "1.21.8", "1.21.10", "1.21.11", "26.1.2", "26.2", "26.3")
        // The version the committed sources are written for.
        vcsVersion = "1.21.11"
        // Groovy, so one script can apply the remapping Loom (1.21.x) or the plain one (26.x).
        centralScript = "build.gradle"
        kotlinController = true
    }
}

rootProject.name = "cosmeticsmod"
