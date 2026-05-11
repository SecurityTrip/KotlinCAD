package cad.app

import cad.domain.tree.FeatureTree
import cad.kernel.Kernel
import cad.kernel.stub.StubKernel
import cad.render.Camera
import cad.render.SceneState
import org.koin.dsl.module

val appModule = module {
    single<Kernel> { StubKernel() }
    single { FeatureTree() }
    single { SceneState() }
    single { Camera() }
    single { AppViewModel(get(), get(), get()) }
}
