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

// jextract task — генерирует Java-биндинги для manifoldc.h, если:
//   1. установлен jextract (есть в PATH),
//   2. в репо лежит native/include/manifoldc.h.
// Иначе — пропускается с предупреждением, чтобы билд оставался работоспособным
// без нативной части (StubKernel-only mode).
val jextractIncludeDir = layout.projectDirectory.dir("native/include")
val jextractHeader = jextractIncludeDir.file("manifold/manifoldc.h")
val jextractOutDir = layout.buildDirectory.dir("generated/sources/jextract/java")

val jextract by tasks.registering {
    val header = jextractHeader.asFile
    outputs.dir(jextractOutDir)

    onlyIf {
        if (!header.exists()) {
            logger.warn("jextract: header not found at {}, skipping (Manifold integration disabled)", header)
            return@onlyIf false
        }
        val exe = findJextract()
        if (exe == null) {
            logger.warn(
                "jextract: executable not found in PATH or JEXTRACT_HOME, skipping. " + "Install jextract and re-run."
            )
            return@onlyIf false
        }
        true
    }

    doLast {
        val out = jextractOutDir.get().asFile
        out.mkdirs()
        val jextractExe = findJextract() ?: error("jextract disappeared between onlyIf and doLast")
        exec {
            commandLine(
                jextractExe.absolutePath,
                "--output", out.absolutePath,
                "--target-package", "cad.native_.manifold",
                "--header-class-name", "Manifoldc",
                "-l", "manifoldc",
                "-I", jextractIncludeDir.asFile.absolutePath,
                header.absolutePath,
            )
        }
    }
}

fun findJextract(): File? {
    val exeName = if (org.gradle.internal.os.OperatingSystem.current().isWindows) "jextract.bat" else "jextract"
    // 1. Persistent install via setup-manifold.ps1
    val bundled = file("tools/jextract/bin/$exeName")
    if (bundled.exists()) return bundled
    // 2. Env var (set by current shell session)
    System.getenv("JEXTRACT_HOME")?.let { home ->
        val f = file("$home/bin/$exeName")
        if (f.exists()) return f
    }
    // 3. PATH
    val pathDirs = System.getenv("PATH")?.split(File.pathSeparator).orEmpty()
    for (d in pathDirs) {
        val f = file("$d/$exeName")
        if (f.exists()) return f
    }
    return null
}

sourceSets.main {
    java.srcDir(jextractOutDir)
}

tasks.named("compileKotlin") { dependsOn(jextract) }
tasks.named("compileJava") { dependsOn(jextract) }

// Прокидываем `-Pcad.kernel=manifold` (или системные `-Dcad.*`) в JVM приложения.
// В Compose run task system properties Gradle'а не наследуются автоматически.
val cadProps = (project.properties.filterKeys { it.startsWith("cad.") }
    .mapValues { it.value.toString() } + System.getProperties().entries.filter { it.key.toString().startsWith("cad.") }
    .associate { it.key.toString() to it.value.toString() })

compose.desktop {
    application {
        mainClass = "cad.app.MainKt"
        jvmArgs += listOf(
            "--enable-native-access=ALL-UNNAMED",
            "--add-opens=java.desktop/sun.awt=ALL-UNNAMED",
            "--add-opens=java.desktop/java.awt.peer=ALL-UNNAMED",
            // Принудительно пишем hs_err_pid в корень проекта при нативном краше.
            "-XX:ErrorFile=${rootDir.absolutePath.replace("\\", "/")}/hs_err_pid%p.log",
        ) + cadProps.map { (k, v) -> "-D$k=$v" }
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "cad"
            packageVersion = "1.0.0"
        }
    }
}
