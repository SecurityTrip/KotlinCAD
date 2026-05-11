package cad.ui.inspector

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import cad.app.AppViewModel

@Composable
fun InspectorPanel(vm: AppViewModel, modifier: Modifier = Modifier) {
    val snap by vm.snapshot
    val selection by vm.selection
    Column(modifier = modifier.fillMaxSize().padding(8.dp)) {
        Text("Inspector", style = MaterialTheme.typography.titleSmall)
        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
        val feature = selection?.let { snap.features[it] }
        if (feature == null) {
            Text("(no selection)", color = Color.Gray)
            return@Column
        }
        Text(feature.name, style = MaterialTheme.typography.bodyMedium)
        Text(feature.id.value, color = Color.Gray, style = MaterialTheme.typography.labelSmall)
        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
        if (feature.parameters.isEmpty()) {
            Text("no parameters", color = Color.Gray)
        }
        for ((name, p) in feature.parameters) {
            ParameterRow(name, p.value, onCommit = { newValue ->
                vm.updateParameter(feature.id, name, newValue)
            })
        }
    }
}

@Composable
private fun ParameterRow(
    name: String,
    value: Double,
    onCommit: (Double) -> Unit,
) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = { newText ->
            text = newText
            newText.toDoubleOrNull()?.let(onCommit)
        },
        label = { Text(name) },
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        singleLine = true,
    )
}
