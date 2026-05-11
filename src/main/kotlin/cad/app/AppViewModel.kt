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
            ),
        )
        dispatch(Command.AddFeature(sketch))
        dispatch(Command.AddFeature(extrude))
        select(extrudeId)
    }

    fun updateParameter(featureId: FeatureId, name: String, newValue: Double) {
        dispatch(Command.UpdateParameter(featureId, name, newValue))
    }

    private fun refreshFromTree(snap: TreeSnapshot) {
        _snapshot.value = snap
        _canUndo.value = tree.canUndo()
        _canRedo.value = tree.canRedo()
        // Если выделение указывает на удалённую фичу — сбрасываем.
        if (_selection.value != null && _selection.value !in snap.features) {
            _selection.value = null
        }
        rebuildScene(snap)
    }

    private fun rebuildScene(snap: TreeSnapshot) {
        // На MVP пересчитываем всё. Позже — только dirty.
        val meshes = snap.ordered
            .filter { it is Feature.Extrude || it is Feature.BooleanFeature || it is Feature.Revolve }
            .map { f -> SceneMesh(f.id, kernel.tessellate(f, snap)) }
        sceneState.update(meshes, _selection.value)
    }

    private fun shortLabel(id: FeatureId): String = id.value.take(4)
}
