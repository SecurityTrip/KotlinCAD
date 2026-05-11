package cad.kernel.stub

import cad.domain.feature.BoolOp
import cad.domain.feature.Feature
import cad.domain.feature.Profile2D
import cad.domain.feature.SketchEntity
import cad.domain.tree.TreeSnapshot
import cad.kernel.Kernel
import cad.kernel.mesh.Mesh
import cad.kernel.mesh.MeshBuilder
import cad.kernel.mesh.Primitives
import cad.kernel.mesh.translated
import org.slf4j.LoggerFactory

/**
 * Кernel без зависимости от нативных либ. Полноценный extrude по 2D-профилю
 * (прямоугольник или круг из Sketch), но boolean — no-op. Этого достаточно,
 * чтобы UI можно было крутить без работающего Manifold.
 */
class StubKernel : Kernel {
    private val log = LoggerFactory.getLogger(StubKernel::class.java)

    override fun tessellate(feature: Feature, snapshot: TreeSnapshot): Mesh = when (feature) {
        is Feature.Extrude -> {
            val sketch = snapshot.features[feature.sketchId] as? Feature.Sketch
            val profile = sketch?.let(::sketchToProfile)
            val depth = feature.depth.toFloat()
            val base = if (profile != null) extrudePolygonMesh(profile, depth)
            else Primitives.cube(1f, 1f, depth)
            val px = (feature.parameters["posX"]?.value ?: 0.0).toFloat()
            val py = (feature.parameters["posY"]?.value ?: 0.0).toFloat()
            val pz = (feature.parameters["posZ"]?.value ?: 0.0).toFloat()
            base.translated(px, py, pz)
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

    override fun extrude(profile: Profile2D, depth: Double): Mesh = extrudePolygonMesh(profile, depth.toFloat())

    private fun sketchToProfile(sketch: Feature.Sketch): Profile2D? = sketch.entities.firstNotNullOfOrNull { e ->
        when (e) {
            is SketchEntity.Rectangle -> Profile2D.rectangle(e.centerX, e.centerY, e.width, e.height)
            is SketchEntity.Circle -> Profile2D.circle(e.centerX, e.centerY, e.radius)
            is SketchEntity.Line -> null
        }
    }
}

/**
 * Призма по 2D-полигону: side-quads + top/bottom caps. Полигон должен быть
 * выпуклым и CCW (мы триангулируем fan'ом, для невыпуклых получится бяка —
 * это уже работа Manifold'а).
 */
internal fun extrudePolygonMesh(profile: Profile2D, depth: Float): Mesh {
    val pts = profile.points
    val n = pts.size
    if (n < 3) return Mesh.EMPTY
    val builder = MeshBuilder()

    // Side faces (CCW снаружи, если profile CCW)
    for (i in 0 until n) {
        val a = pts[i]
        val b = pts[(i + 1) % n]
        val ax = a.x.toFloat();
        val ay = a.y.toFloat()
        val bx = b.x.toFloat();
        val by = b.y.toFloat()
        builder.addQuad(
            floatArrayOf(ax, ay, 0f),
            floatArrayOf(bx, by, 0f),
            floatArrayOf(bx, by, depth),
            floatArrayOf(ax, ay, depth),
        )
    }
    // Top cap — fan по pts[0] на z = depth (normal +Z)
    for (i in 1 until n - 1) {
        builder.addTriangle(
            pts[0].x.toFloat(), pts[0].y.toFloat(), depth,
            pts[i].x.toFloat(), pts[i].y.toFloat(), depth,
            pts[i + 1].x.toFloat(), pts[i + 1].y.toFloat(), depth,
        )
    }
    // Bottom cap — fan в обратном порядке (normal -Z)
    for (i in 1 until n - 1) {
        builder.addTriangle(
            pts[0].x.toFloat(), pts[0].y.toFloat(), 0f,
            pts[i + 1].x.toFloat(), pts[i + 1].y.toFloat(), 0f,
            pts[i].x.toFloat(), pts[i].y.toFloat(), 0f,
        )
    }
    return builder.build()
}
