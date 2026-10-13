plugins {
    id("dev.kikugie.stonecutter")
    // Both Loom variants come from the same artifact: "-remap" for obfuscated 1.21.x, plain for 26.x.
    id("net.fabricmc.fabric-loom-remap") version "1.17.20" apply false
    id("net.fabricmc.fabric-loom") version "1.17.20" apply false
}

stonecutter active "1.21.11"
