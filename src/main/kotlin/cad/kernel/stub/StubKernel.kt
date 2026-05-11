package cad.kernel.stub

import cad.domain.feature.BoolOp
import cad.domain.feature.Feature
import cad.domain.feature.Profile2D
import cad.domain.tree.TreeSnapshot
import cad.kernel.Kernel
import cad.kernel.mesh.Mesh
import cad.kernel.mesh.Primitives
import cad.kernel.mesh.translated
import org.slf4j.LoggerFactory

/**
 * Минимальная реализация Kernel: всё, что extrude — это куб с размерами из параметров.
 * Boolean — no-op (возвращает a). Этого достаточно, чтобы UI и domain были запускаемы
 * без нативных либ.
 */
class StubKernel : Kernel {
    private val log = LoggerFactory.getLogger(StubKernel::class.java)

    override fun tessellate(feature: Feature, snapshot: TreeSnapshot): Mesh = when (feature) {
        is Feature.Extrude -> {
            val w = feature.parameters["width"]?.value?.toFloat() ?: 1f
            val h = feature.parameters["height"]?.value?.toFloat() ?: 1f
            val d = feature.depth.toFloat()
            val px = (feature.parameters["posX"]?.value ?: 0.0).toFloat()
            val py = (feature.parameters["posY"]?.value ?: 0.0).toFloat()
            val pz = (feature.parameters["posZ"]?.value ?: 0.0).toFloat()
            Primitives.cube(w, h, d).translated(px, py, pz)
        }

        is Feature.Sketch -> Mesh.EMPTY
        is Feature.Revolve -> Primitives.cube(1f, 1f, 1f)
        is Feature.BooleanFeature -> {
            val l = snapshot.features[feature.left]
            val r = snapshot.features[feature.right]
            val lm = l?.let { tessellate(it, snapshot) } ?: Mesh.EMPTY
            val rm = r?.let { tessellate(it, snapshot) } ?: Mesh.EMPTY
            boolean(lm, rm, feature.op)
        }
    }

    override fun boolean(a: Mesh, b: Mesh, op: BoolOp): Mesh {
        log.warn("StubKernel.boolean is no-op; install ManifoldKernel for real CSG")
        return a
    }

    override fun extrude(profile: Profile2D, depth: Double): Mesh {
        // По BB профиля строим коробку нужного размера.
        if (profile.points.isEmpty()) return Primitives.cube(1f, 1f, depth.toFloat())
        val xs = profile.points.map { it.x }
        val ys = profile.points.map { it.y }
        val w = ((xs.max() - xs.min()).coerceAtLeast(1e-6)).toFloat()
        val h = ((ys.max() - ys.min()).coerceAtLeast(1e-6)).toFloat()
        return Primitives.cube(w, h, depth.toFloat())
    }
}
