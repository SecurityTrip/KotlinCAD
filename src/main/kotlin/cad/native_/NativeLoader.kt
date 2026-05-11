package cad.native_

import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicReference
import kotlin.io.path.absolutePathString
import kotlin.io.path.createDirectories

/**
 * Распаковывает нативную либу из classpath в temp-каталог и зовёт System.load.
 *
 * Раскладка ресурсов:
 *   src/main/resources/native/<platform>/<libfile>
 * где platform = windows-x86_64 | linux-x86_64 | macos-x86_64 | macos-aarch64,
 * libfile = manifoldc.dll | libmanifoldc.so | libmanifoldc.dylib.
 *
 * Идемпотентность: повторные вызовы возвращают тот же кэшированный Result.
 */
object NativeLoader {
    private val log = LoggerFactory.getLogger(NativeLoader::class.java)
    private val manifoldResult = AtomicReference<Result<Unit>?>(null)

    fun ensureManifoldLoaded(): Result<Unit> {
        manifoldResult.get()?.let { return it }
        val r = runCatching {
            val platform = detectPlatform()
            val libFile = manifoldLibFileName(platform)
            val resourcePath = "/native/$platform/$libFile"
            val extracted = extractToTemp(resourcePath, libFile)
            System.load(extracted.absolutePathString())
            log.info("Manifold loaded: {}", extracted)
        }
        manifoldResult.compareAndSet(null, r)
        return manifoldResult.get() ?: r
    }

    private fun detectPlatform(): String {
        val os = System.getProperty("os.name").lowercase()
        val arch = System.getProperty("os.arch").lowercase()
        val osTag = when {
            os.contains("win") -> "windows"
            os.contains("mac") || os.contains("darwin") -> "macos"
            os.contains("nix") || os.contains("nux") -> "linux"
            else -> error("Unsupported OS: $os")
        }
        val archTag = when (arch) {
            "amd64", "x86_64" -> "x86_64"
            "aarch64", "arm64" -> "aarch64"
            else -> error("Unsupported arch: $arch")
        }
        return "$osTag-$archTag"
    }

    private fun manifoldLibFileName(platform: String): String = when {
        platform.startsWith("windows") -> "manifoldc.dll"
        platform.startsWith("macos") -> "libmanifoldc.dylib"
        platform.startsWith("linux") -> "libmanifoldc.so"
        else -> error("Unsupported platform: $platform")
    }

    private fun extractToTemp(resourcePath: String, fileName: String): Path {
        val stream = NativeLoader::class.java.getResourceAsStream(resourcePath)
            ?: error("Native library not found in classpath: $resourcePath")
        val dir = Path.of(System.getProperty("java.io.tmpdir"), "cad", "native")
            .createDirectories()
        val target = dir.resolve(fileName)
        stream.use { input -> Files.copy(input, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING) }
        return target
    }
}
