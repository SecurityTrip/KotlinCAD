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
import java.io.File
import javax.swing.JFileChooser
import javax.swing.SwingUtilities
import javax.swing.filechooser.FileNameExtensionFilter

@Composable
fun Toolbar(vm: AppViewModel) {
    val canUndo by vm.canUndo
    val canRedo by vm.canRedo
    val selection by vm.selection
    Row(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Button(onClick = { vm.addBox() }) { Text("Add Box") }
        OutlinedButton(onClick = { vm.undo() }, enabled = canUndo) { Text("Undo") }
        OutlinedButton(onClick = { vm.redo() }, enabled = canRedo) { Text("Redo") }
        OutlinedButton(
            onClick = { exportStlDialog(vm) },
            enabled = selection != null,
        ) { Text("Export STL") }
    }
}

private fun exportStlDialog(vm: AppViewModel) {
    // JFileChooser должен крутиться на EDT.
    SwingUtilities.invokeLater {
        val chooser = JFileChooser().apply {
            dialogTitle = "Export STL"
            fileFilter = FileNameExtensionFilter("Stereolithography (*.stl)", "stl")
            selectedFile = File("part.stl")
        }
        if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
            val f = chooser.selectedFile.let {
                if (it.extension.equals("stl", ignoreCase = true)) it
                else File(it.parentFile, it.name + ".stl")
            }
            vm.exportSelectionToStl(f.toPath())
        }
    }
}
