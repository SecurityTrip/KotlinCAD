package cad.ui.toolbar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cad.app.AppViewModel

@Composable
fun Toolbar(vm: AppViewModel) {
    val canUndo by vm.canUndo
    val canRedo by vm.canRedo
    Row(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Button(onClick = { vm.addBox() }) { Text("Add Box") }
        OutlinedButton(onClick = { vm.undo() }, enabled = canUndo) { Text("Undo") }
        OutlinedButton(onClick = { vm.redo() }, enabled = canRedo) { Text("Redo") }
    }
}
