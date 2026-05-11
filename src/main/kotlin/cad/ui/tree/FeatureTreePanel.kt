package cad.ui.tree

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import cad.app.AppViewModel
import cad.domain.feature.Feature

@Composable
fun FeatureTreePanel(vm: AppViewModel, modifier: Modifier = Modifier) {
    val snap by vm.snapshot
    val selection by vm.selection
    Column(modifier = modifier.fillMaxSize().padding(8.dp)) {
        Text("Features", style = MaterialTheme.typography.titleSmall)
        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
        val items = snap.ordered
        if (items.isEmpty()) {
            Text("(empty) — use Add Box", color = Color.Gray)
        } else {
            LazyColumn {
                items(items, key = { it.id.value }) { f ->
                    val isSelected = f.id == selection
                    val bg = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                             else Color.Transparent
                    Text(
                        text = labelFor(f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(bg)
                            .clickable { vm.select(f.id) }
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}

private fun labelFor(f: Feature): String {
    val kind = when (f) {
        is Feature.Sketch -> "Sketch"
        is Feature.Extrude -> "Extrude"
        is Feature.Revolve -> "Revolve"
        is Feature.BooleanFeature -> "Boolean"
    }
    return "$kind · ${f.name}"
}
