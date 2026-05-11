package cad.kernel.manifold

import cad.domain.feature.BoolOp
import cad.domain.feature.Feature
import cad.domain.feature.Profile2D
import cad.domain.feature.SketchEntity
import cad.domain.tree.TreeSnapshot
import cad.kernel.Kernel
import cad.kernel.mesh.Mesh
import cad.kernel.mesh.translated
import cad.native_.NativeLoader
import org.slf4j.LoggerFactory
import java.lang.foreign.Arena
import java.lang.foreign.MemorySegment

/**
 * Реализация Kernel через Manifold (https://github.com/elalish/manifold) и
 * Panama FFI (`java.lang.foreign`).
 *
 * Конструктор проверяет, что нативка загрузилась и jextract-биндинги
 * сгенерированы; если нет — бросает исключение, по которому DI откатывается
 * на StubKernel.
 *
 * Lifetime:
 *   - На каждый публичный вызов создаётся свой [Arena.ofConfined],
 *     внутрь арены складываются placement-new буферы и временные данные.
 *   - Manifold-объекты живут до явного `manifold_delete_*`, который зовётся
 *     в `finally`.
 *   - Если вызов кидает — [wrap] возвращает [Mesh.EMPTY] и логирует, чтобы
 *     приложение не падало из-за невалидного скетча.
 */
class ManifoldKernel : Kernel {
    private val log = LoggerFactory.getLogger(ManifoldKernel::class.java)

    init {
        NativeLoader.ensureManifoldLoaded().getOrThrow()
        require(jextractBindingsPresent()) {
            "jextract bindings (cad.native_.manifold.Manifoldc) not on classpath. " + "Run `gradlew jextract` after placing native/include/manifold/manifoldc.h."
        }
        log.info(
            "Manifold sizes: simple_polygon={}, polygons={}, manifold={}, meshgl={}",
            cad.native_.manifold.Manifoldc.manifold_simple_polygon_size(),
            cad.native_.manifold.Manifoldc.manifold_polygons_size(),
            cad.native_.manifold.Manifoldc.manifold_manifold_size(),
            cad.native_.manifold.Manifoldc.manifold_meshgl_size(),
        )
        log.info("ManifoldKernel ready")
    }

    override fun tessellate(feature: Feature, snapshot: TreeSnapshot): Mesh = wrap {
        when (feature) {
            is Feature.Sketch -> Mesh.EMPTY
            is Feature.Extrude -> {
                val sketch = snapshot.features[feature.sketchId] as? Feature.Sketch ?: return@wrap Mesh.EMPTY
                val profile = sketchToProfile(sketch) ?: return@wrap Mesh.EMPTY
                val raw = extrude(profile, feature.depth)
                val px = (feature.parameters["posX"]?.value ?: 0.0).toFloat()
                val py = (feature.parameters["posY"]?.value ?: 0.0).toFloat()
                val pz = (feature.parameters["posZ"]?.value ?: 0.0).toFloat()
                raw.translated(px, py, pz)
            }

            is Feature.BooleanFeature -> {
                val a = snapshot.features[feature.left]?.let { tessellate(it, snapshot) } ?: Mesh.EMPTY
                val b = snapshot.features[feature.right]?.let { tessellate(it, snapshot) } ?: Mesh.EMPTY
                if (a.indices.isEmpty() || b.indices.isEmpty()) {
                    // boolean с пустым операндом — вернуть непустой
                    if (a.indices.isNotEmpty()) a else b
                } else {
                    boolean(a, b, feature.op)
                }
            }

            is Feature.Revolve -> Mesh.EMPTY // TODO(phase 3): manifold revolve
        }
    }

    override fun extrude(profile: Profile2D, depth: Double): Mesh = wrap {
        Arena.ofConfined().use { arena ->
            val polygons = ManifoldFfi.polygonsFromSimplePolygon(arena, profile)
            val m = ManifoldFfi.extrude(arena, polygons, depth)
            try {
                ManifoldFfi.toMesh(arena, m)
            } finally {
                ManifoldFfi.deleteManifold(m)
            }
        }
    }

    override fun boolean(a: Mesh, b: Mesh, op: BoolOp): Mesh = wrap {
        Arena.ofConfined().use { arena ->
            val ma: MemorySegment = ManifoldFfi.manifoldOfMesh(arena, a)
            val mb: MemorySegment = ManifoldFfi.manifoldOfMesh(arena, b)
            try {
                val res = ManifoldFfi.boolean(arena, ma, mb, op)
                try {
                    ManifoldFfi.toMesh(arena, res)
                } finally {
                    ManifoldFfi.deleteManifold(res)
                }
            } finally {
                ManifoldFfi.deleteManifold(ma)
                ManifoldFfi.deleteManifold(mb)
            }
        }
    }

    private fun sketchToProfile(sketch: Feature.Sketch): Profile2D? {
        // MVP: первая Rectangle/Circle берётся как профиль extrude. Полноценную
        // сборку polygon из множества entity (с дырками, объединениями линий)
        // делаем в Phase 3 — там нужны honest constraints/boolean на 2D.
        return sketch.entities.firstNotNullOfOrNull { e ->
            when (e) {
                is SketchEntity.Rectangle -> Profile2D.rectangle(e.centerX, e.centerY, e.width, e.height)
                is SketchEntity.Circle -> Profile2D.circle(e.centerX, e.centerY, e.radius)
                is SketchEntity.Line -> null
            }
        }
    }

    private inline fun wrap(block: () -> Mesh): Mesh = try {
        block()
    } catch (t: Throwable) {
        log.error("ManifoldKernel call failed, returning empty mesh", t)
        Mesh.EMPTY
    }

    companion object {
        fun jextractBindingsPresent(): Boolean = try {
            Class.forName("cad.native_.manifold.Manifoldc")
            true
        } catch (_: ClassNotFoundException) {
            false
        }

        /**
         * Не бросая, проверяет — можно ли вообще инстанциировать ManifoldKernel
         * в текущем окружении. Используется в тестах через `assumeTrue`,
         * чтобы integration-тесты пропускались на машинах без DLL.
         */
        fun canLoad(): Boolean = NativeLoader.ensureManifoldLoaded().isSuccess && jextractBindingsPresent()
    }
}
