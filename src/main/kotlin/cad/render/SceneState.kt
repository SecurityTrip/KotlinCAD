package cad.render

import cad.domain.feature.FeatureId
import cad.kernel.mesh.Mesh
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class SceneMesh(
    val featureId: FeatureId,
    val mesh: Mesh,
)

data class Scene(
    val meshes: List<SceneMesh> = emptyList(),
    val selected: FeatureId? = null,
    // monotonic счётчик: render-loop сравнивает его с последним, чтобы решать нужно ли
    // перезаливать VBO. Без него equals по списку мешей дорогой.
    val revision: Long = 0,
)

class SceneState {
    private val _scene = MutableStateFlow(Scene())
    val scene: StateFlow<Scene> = _scene

    fun update(meshes: List<SceneMesh>, selected: FeatureId?) {
        _scene.value = Scene(meshes, selected, _scene.value.revision + 1)
    }

    fun setSelection(selected: FeatureId?) {
        val cur = _scene.value
        _scene.value = cur.copy(selected = selected, revision = cur.revision + 1)
    }
}
