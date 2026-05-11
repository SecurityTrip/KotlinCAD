package cad.kernel

import cad.domain.feature.BoolOp
import cad.domain.feature.Feature
import cad.domain.feature.Profile2D
import cad.domain.tree.TreeSnapshot
import cad.kernel.mesh.Mesh

/**
 * Геометрическое ядро. Реализации — StubKernel (захардкоженный куб) и
 * ManifoldKernel (будущая реализация через Panama FFI к manifoldc).
 *
 * Контракт: domain не знает про реализацию, передаёт фичу + текущий снапшот
 * (чтобы можно было разрезолвить ссылки на другие фичи — например Extrude → Sketch).
 */
interface Kernel {

    /** Построить меш для отдельной фичи в контексте всего дерева. */
    fun tessellate(feature: Feature, snapshot: TreeSnapshot): Mesh

    fun boolean(a: Mesh, b: Mesh, op: BoolOp): Mesh

    fun extrude(profile: Profile2D, depth: Double): Mesh
}
