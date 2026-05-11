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

private fun resolveKernel(): Kernel {
    val log = LoggerFactory.getLogger("cad.app.Kernel")
    return runCatching { ManifoldKernel() }.onSuccess { log.info("Kernel: ManifoldKernel") }.getOrElse { t ->
            log.warn("Falling back to StubKernel: {}", t.message)
            StubKernel()
        }
}
