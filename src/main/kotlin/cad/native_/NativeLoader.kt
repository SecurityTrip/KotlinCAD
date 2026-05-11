package cad.native_

import org.slf4j.LoggerFactory
import java.net.URI
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicReference
import kotlin.io.path.absolutePathString
import kotlin.io.path.createDirectories

/**
 * Распаковывает нативные либы из classpath в temp-каталог и зовёт System.load.
 *
 * Раскладка ресурсов:
 *   src/main/resources/native/<platform>/<libfile>
 *
 * Распаковываются ВСЕ файлы из соответствующего каталога — это критично, потому что
 * у `manifoldc.dll` есть runtime-зависимости (tbb12.dll и т.д.), которые Windows
 * ищет в той же папке, что и основной DLL.
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
            val mainLib = manifoldLibFileName(platform)
            val targetDir = extractAll("/native/$platform")
            val mainPath = targetDir.resolve(mainLib)
            require(Files.exists(mainPath)) { "Main native lib missing after extraction: $mainPath" }

            // Windows не добавляет каталог распакованной DLL в search-path
            // автоматически, поэтому транзитивные зависимости (manifold.dll, tbb*.dll)
            // надо подгрузить руками ДО главной либы. Порядок: всё в каталоге,
            // кроме main, затем main.
            val extra = Files.list(targetDir).use { stream ->
                stream.filter { it.fileName.toString().endsWith(".dll", ignoreCase = true) }
                    .filter { it.fileName.toString() != mainLib }.toList()
            }
            // tbb12 должен быть раньше tbbmalloc'ов; manifold.dll — раньше manifoldc.
            // Сортируем так, чтобы зависимые шли в конце: tbb12 → tbbmalloc* → manifold → manifoldc.
            val ordered = extra.sortedBy { p ->
                val n = p.fileName.toString().lowercase()
                when {
                    n == "tbb12.dll" -> 0
                    n.startsWith("tbbmalloc") -> 1
                    n == "manifold.dll" -> 2
                    else -> 3
                }
            }
            for (p in ordered) {
                runCatching { System.load(p.absolutePathString()) }.onSuccess { log.debug("Preloaded {}", p.fileName) }
                    .onFailure {
                        log.debug(
                            "Preload failed for {}: {} (may be already loaded)", p.fileName, it.message
                        )
                    }
            }

            System.load(mainPath.absolutePathString())
            log.info("Manifold loaded: {}", mainPath)
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

    /**
     * Распаковывает все файлы из classpath-каталога [resourceDir] в %TEMP%/cad/native.
     * Работает и из jar (через jar:URI), и из dev-classpath (через файловый URI).
     */
    private fun extractAll(resourceDir: String): Path {
        val targetDir = Path.of(System.getProperty("java.io.tmpdir"), "cad", "native").createDirectories()
        val cl = NativeLoader::class.java
        // Найдём папку через любой известный файл внутри неё (не знаем, что лежит,
        // поэтому обходим через URL основной либы и поднимаемся к каталогу).
        val probe = "$resourceDir/" + when {
            resourceDir.contains("windows") -> "manifoldc.dll"
            resourceDir.contains("macos") -> "libmanifoldc.dylib"
            else -> "libmanifoldc.so"
        }
        val probeUrl = cl.getResource(probe) ?: error("native dir not on classpath: $resourceDir")
        val files: List<String> = when (probeUrl.protocol) {
            "file" -> {
                val dirPath = Path.of(probeUrl.toURI()).parent
                Files.list(dirPath).use { it.map { p -> p.fileName.toString() }.toList() }
            }

            "jar" -> {
                val uri = URI(probeUrl.toString().substringBefore("!"))
                FileSystems.newFileSystem(uri, emptyMap<String, Any>()).use { fs ->
                    val dirPath = fs.getPath(resourceDir)
                    Files.list(dirPath).use { it.map { p -> p.fileName.toString() }.toList() }
                }
            }

            else -> error("Unsupported resource URL protocol: ${probeUrl.protocol}")
        }

        for (fn in files) {
            val resPath = "$resourceDir/$fn"
            val input = cl.getResourceAsStream(resPath) ?: continue
            val target = targetDir.resolve(fn)
            input.use { Files.copy(it, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING) }
            log.debug("Extracted {}", target)
        }
        return targetDir
    }
}
