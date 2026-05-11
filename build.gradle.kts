import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.1.21"
    kotlin("plugin.serialization") version "2.1.21"
    id("org.jetbrains.compose") version "1.8.2"
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.21"
}

group = "cad"
version = "0.1.0-SNAPSHOT"

kotlin {
    jvmToolchain(23)
}

val lwjglVersion = "3.3.4"
val jomlVersion = "1.10.8"

val lwjglNatives = listOf(
    "natives-windows",
    "natives-linux",
    "natives-macos",
    "natives-macos-arm64",
)

val lwjglModules = listOf(
    "lwjgl",
    "lwjgl-opengl",
    // GLFW не используется в Compose-режиме (контекст создаёт AWTGLCanvas),
    // но многие модули LWJGL тянут общие нативы — оставлять ничего лишнего не нужно.
)

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)

    // LWJGL: основная либа + opengl, natives на три ОС
    implementation(platform("org.lwjgl:lwjgl-bom:$lwjglVersion"))
    lwjglModules.forEach { mod ->
        implementation("org.lwjgl:$mod")
        lwjglNatives.forEach { classifier ->
            runtimeOnly("org.lwjgl:$mod::$classifier")
        }
    }

    implementation("org.joml:joml:$jomlVersion")

    // AWTGLCanvas — Swing-компонент с OpenGL-контекстом. Используется через SwingPanel в Compose.
    implementation("org.lwjglx:lwjgl3-awt:0.1.8")

    implementation("io.insert-koin:koin-core:4.0.0")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.9.0")

    implementation("org.slf4j:slf4j-api:2.0.16")
    runtimeOnly("org.slf4j:slf4j-simple:2.0.16")

    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

compose.desktop {
    application {
        mainClass = "cad.app.MainKt"
        jvmArgs += listOf(
            "--enable-native-access=ALL-UNNAMED",
            "--add-opens=java.desktop/sun.awt=ALL-UNNAMED",
            "--add-opens=java.desktop/java.awt.peer=ALL-UNNAMED",
        )
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "cad"
            packageVersion = "1.0.0"
        }
    }
}
