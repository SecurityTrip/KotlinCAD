package cad.app

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import cad.domain.command.Command
import cad.domain.feature.Feature
import cad.domain.feature.FeatureId
import cad.domain.feature.SketchEntity
import cad.domain.feature.SketchPlane
import cad.domain.parameter.Parameter
import cad.domain.tree.FeatureTree
import cad.domain.tree.TreeSnapshot
import cad.kernel.Kernel
import cad.kernel.mesh.Mesh
import cad.render.SceneMesh
import cad.render.SceneState
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * Тонкая обёртка над FeatureTree + Kernel + SceneState для Compose UI.
 * Держит Compose-наблюдаемое состояние (snapshot, selection) и пересчитывает
 * меши через Kernel при каждом изменении.
 */
class AppViewModel(
    private val tree: FeatureTree,
    private val kernel: Kernel,
    private val sceneState: SceneState,
) {
    private val log = LoggerFactory.getLogger(AppViewModel::class.java)

    private val _snapshot = mutableStateOf<TreeSnapshot>(TreeSnapshot.EMPTY)
    val snapshot: State<TreeSnapshot> = _snapshot

    private val _selection = mutableStateOf<FeatureId?>(null)
    val selection: State<FeatureId?> = _selection

    private val _canUndo = mutableStateOf(false)
    val canUndo: State<Boolean> = _canUndo

    private val _canRedo = mutableStateOf(false)
    val canRedo: State<Boolean> = _canRedo

    /**
     * Кэш меша на фичу. Ключ — featureId; значение — пара (hashCode фичи, mesh).
     * При rebuildScene сравниваем `feature.hashCode()` с закэшированным: если
     * совпало, переиспользуем `Mesh`-инстанс. Это критично для GPU-diff
     * (см. GlRenderer): тот же Mesh-инстанс → нет повторной заливки в VBO.
     */
    private data class MeshCacheEntry(val featureHash: Int, val mesh: Mesh)

    private val meshCache = HashMap<FeatureId, MeshCacheEntry>()

    fun dispatch(command: Command) {
        val result = tree.apply(command)
        refreshFromTree(result.snapshot)
    }

    fun undo() {
        val r = tree.undo()
        refreshFromTree(r.snapshot)
    }

    fun redo() {
        val r = tree.redo()
        refreshFromTree(r.snapshot)
    }

    fun select(id: FeatureId?) {
        _selection.value = id
        sceneState.setSelection(id)
    }

    fun addBox() {
        val sketchId = FeatureId(UUID.randomUUID().toString())
        val extrudeId = FeatureId(UUID.randomUUID().toString())

        // Сместить новый box по X, чтобы он не накладывался на предыдущие.
        // Считаем число уже существующих Extrude в дереве.
        val existingExtrudes = _snapshot.value.ordered.count { it is Feature.Extrude }
        val posX = existingExtrudes * 1.5

        val sketch = Feature.Sketch(
            id = sketchId,
            name = "Sketch ${shortLabel(sketchId)}",
            plane = SketchPlane.XZ,
            entities = listOf(SketchEntity.Rectangle(0.0, 0.0, 1.0, 1.0)),
        )
        val extrude = Feature.Extrude(
            id = extrudeId,
            name = "Box ${shortLabel(extrudeId)}",
            sketchId = sketchId,
            parameters = mapOf(
                "width" to Parameter("width", 1.0),
                "height" to Parameter("height", 1.0),
                "depth" to Parameter("depth", 1.0),
                "posX" to Parameter("posX", posX),
                "posY" to Parameter("posY", 0.0),
                "posZ" to Parameter("posZ", 0.0),
            ),
        )
        dispatch(Command.AddFeature(sketch))
        dispatch(Command.AddFeature(extrude))
        select(extrudeId)
    }

    fun updateParameter(featureId: FeatureId, name: String, newValue: Double) {
        dispatch(Command.UpdateParameter(featureId, name, newValue))
    }

    fun setParameterFormula(featureId: FeatureId, name: String, formula: String?) {
        dispatch(Command.SetParameterFormula(featureId, name, formula))
    }

    /**
     * Экспорт меша выделенной фичи в STL. Возвращает null, если экспортировать
     * нечего (нет выделения или фича не образует mesh).
     */
    fun exportSelectionToStl(path: java.nio.file.Path): java.nio.file.Path? {
        val sel = _selection.value ?: return null
        val feature = _snapshot.value.features[sel] ?: return null
        val mesh = meshFor(feature, _snapshot.value)
        if (mesh.indices.isEmpty()) {
            log.warn("Selected feature {} produced empty mesh; nothing to export", feature.id.value)
            return null
        }
        cad.io_.StlWriter.writeBinary(mesh, path, name = feature.name)
        log.info("Exported {} → {}", feature.id.value, path)
        return path
    }

    private fun refreshFromTree(snap: TreeSnapshot) {
        _snapshot.value = snap
        _canUndo.value = tree.canUndo()
        _canRedo.value = tree.canRedo()
        if (_selection.value != null && _selection.value !in snap.features) {
            _selection.value = null
        }
        rebuildScene(snap)
    }

    private fun rebuildScene(snap: TreeSnapshot) {
        // Удаляем из кэша всё, чего нет в текущем snapshot.
        meshCache.keys.retainAll(snap.features.keys)

        var recomputed = 0
        val meshes =
            snap.ordered.filter { it is Feature.Extrude || it is Feature.BooleanFeature || it is Feature.Revolve }
                .map { f ->
                    val (mesh, wasRecomputed) = meshForTracked(f, snap)
                    if (wasRecomputed) recomputed++
                    SceneMesh(f.id, mesh)
                }
        if (recomputed > 0) {
            log.debug("rebuildScene: {} meshes recomputed, {} cached", recomputed, meshes.size - recomputed)
        }
        sceneState.update(meshes, _selection.value)
    }

    /** Возвращает меш фичи (из кэша или вычислив заново) + recomputed-флаг. */
    private fun meshForTracked(feature: Feature, snap: TreeSnapshot): Pair<Mesh, Boolean> {
        val hash = feature.hashCode()
        val cached = meshCache[feature.id]
        if (cached != null && cached.featureHash == hash) {
            return cached.mesh to false
        }
        val mesh = kernel.tessellate(feature, snap)
        meshCache[feature.id] = MeshCacheEntry(hash, mesh)
        return mesh to true
    }

    private fun meshFor(feature: Feature, snap: TreeSnapshot): Mesh = meshForTracked(feature, snap).first

    private fun shortLabel(id: FeatureId): String = id.value.take(4)
}
