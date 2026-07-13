import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.1.0"
    id("org.jetbrains.compose") version "1.7.3"
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.0"
}

group = "com.neversoft.spotlight"
version = "1.0.0"

// Target 17 bytecode without requiring a specific local JDK (CI uses 17, dev may use newer).
kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    // Reads the Windows Sticky Notes store (plum.sqlite) directly.
    implementation("org.xerial:sqlite-jdbc:3.47.1.0")

    testImplementation("junit:junit:4.13.2")
}

// The androidx.* jars are runtime-only deps of the Compose UI, which no unit
// test loads — excluding them from the test classpath keeps `test` runnable in
// sandboxes where dl.google.com is unreachable. The packaged app still bundles
// them (runtimeClasspath is untouched).
configurations.named("testRuntimeClasspath") {
    exclude(group = "androidx.annotation")
    exclude(group = "androidx.collection")
    exclude(group = "androidx.lifecycle")
    exclude(group = "androidx.arch.core")
}

compose.desktop {
    application {
        mainClass = "com.neversoft.spotlight.desktop.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Msi)
            packageName = "Spotlight"
            packageVersion = "1.0.0"
            vendor = "NeverSoft Services"
            description = "Search your entire PC — apps, files, folders and notes."

            windows {
                menu = true
                shortcut = true
                upgradeUuid = "9c6f2f8e-3a41-4bfa-b1f5-2f5f6f7a1c11"
            }
        }
    }
}
