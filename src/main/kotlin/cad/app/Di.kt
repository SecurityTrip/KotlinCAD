package cad.app

import cad.domain.tree.FeatureTree
import cad.kernel.Kernel
import cad.kernel.manifold.ManifoldKernel
import cad.kernel.stub.StubKernel
import cad.render.Camera
import cad.render.SceneState
import org.koin.dsl.module
import org.slf4j.LoggerFactory

val appModule = module {
    single<Kernel> { resolveKernel() }
    single { FeatureTree() }
    single { SceneState() }
    single { Camera() }
    single { AppViewModel(get(), get(), get()) }
}

/**
 * Выбор ядра:
 *   - По умолчанию пробуем [ManifoldKernel] (полноценный CSG через manifoldc + Panama).
 *   - Если нативная либа недоступна или биндинги не сгенерированы — fallback на
 *     [StubKernel] (extrude по полигону, boolean — no-op).
 *   - Чтобы насильно вернуться к stub'у: `-Dcad.kernel=stub` или `CAD_KERNEL=stub`.
 */
private fun resolveKernel(): Kernel {
    val log = LoggerFactory.getLogger("cad.app.Kernel")
    val choice = (System.getProperty("cad.kernel") ?: System.getenv("CAD_KERNEL") ?: "manifold").lowercase()
    if (choice != "stub") {
        runCatching { ManifoldKernel() }.onSuccess {
                log.info("Kernel: ManifoldKernel")
                return it
            }.onFailure { log.warn("ManifoldKernel init failed: {} — falling back to StubKernel", it.message) }
    }
    log.info("Kernel: StubKernel")
    return StubKernel()
}
