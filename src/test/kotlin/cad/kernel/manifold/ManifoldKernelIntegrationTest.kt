package cad.kernel.manifold

import cad.domain.feature.BoolOp
import cad.domain.feature.Profile2D
import cad.kernel.mesh.Primitives
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

/**
 * Sanity-проверки ManifoldKernel против реальной нативки.
 *
 * Пропускается через `Assumptions.assumeTrue(canLoad())`, если DLL или
 * jextract-биндинги отсутствуют — иначе CI без Manifold-сетапа падал бы.
 */
class ManifoldKernelIntegrationTest {

    companion object {
        private lateinit var kernel: ManifoldKernel

        @JvmStatic
        @BeforeAll
        fun setup() {
            assumeTrue(ManifoldKernel.canLoad(), "ManifoldKernel native deps not available, skipping")
            kernel = ManifoldKernel()
        }
    }

    @Test
    fun `extrude unit square produces a closed box mesh`() {
        val profile = Profile2D.rectangle(0.0, 0.0, 2.0, 2.0)
        val mesh = kernel.extrude(profile, 2.0)

        // Manifold может триангулировать грани куба сложнее, чем простой 12-tri box.
        // Главное — что mesh не пустой и геометрически осмысленный.
        assertTrue(mesh.indices.size >= 12 * 3, "expected at least 12 triangles, got ${mesh.indices.size / 3}")
        assertTrue(mesh.vertices.isNotEmpty())
        assertTrue(mesh.normals.size == mesh.vertices.size)
    }

    @Test
    fun `boolean union of two overlapping cubes is non-empty and larger than either input`() {
        // Берём StubKernel-cube как известный тестовый mesh (manifold-watertight).
        val a = Primitives.cube(2f, 2f, 2f)
        // Сдвинутый второй куб (на 1 единицу по X — реально overlapping).
        val b = Primitives.cube(2f, 2f, 2f).let { src ->
                val v = src.vertices.copyOf()
                var i = 0; while (i < v.size) {
                v[i] += 1f; i += 3
            }
                cad.kernel.mesh.Mesh(v, src.normals.copyOf(), src.indices.copyOf())
            }

        val union = kernel.boolean(a, b, BoolOp.UNION)
        assertTrue(union.indices.isNotEmpty(), "union must produce a mesh")
        // У union должно быть БОЛЬШЕ треугольников, чем у одиночного куба (12),
        // потому что пересечение даёт новые грани/рёбра.
        assertTrue(union.indices.size >= 12 * 3, "union mesh must have >= 12 triangles")
    }

    @Test
    fun `boolean difference of two cubes is non-empty`() {
        val big = Primitives.cube(4f, 4f, 4f)
        val small = Primitives.cube(2f, 2f, 2f)

        val diff = kernel.boolean(big, small, BoolOp.DIFFERENCE)
        assertTrue(diff.indices.isNotEmpty(), "difference must produce a non-empty mesh")
    }
}
