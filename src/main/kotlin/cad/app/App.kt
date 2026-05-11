package cad.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cad.render.Camera
import cad.render.SceneState
import cad.ui.inspector.InspectorPanel
import cad.ui.toolbar.Toolbar
import cad.ui.tree.FeatureTreePanel
import cad.ui.viewport.Viewport
import org.koin.core.context.GlobalContext

@Composable
fun App() {
    val koin = GlobalContext.get()
    val vm = koin.get<AppViewModel>()
    val camera = koin.get<Camera>()
    val sceneState = koin.get<SceneState>()

    MaterialTheme(colorScheme = darkColorScheme()) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                Toolbar(vm)
                HorizontalDivider()
                Row(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.width(240.dp).fillMaxHeight()) {
                        FeatureTreePanel(vm)
                    }
                    VerticalDivider()
                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        Viewport(camera, sceneState, modifier = Modifier.fillMaxSize())
                    }
                    VerticalDivider()
                    Box(modifier = Modifier.width(280.dp).fillMaxHeight()) {
                        InspectorPanel(vm)
                    }
                }
            }
        }
    }
}
