package cad.ui.toolbar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cad.app.AppViewModel
import cad.domain.feature.Feature
import cad.ui.sketch.SketchTool
import java.io.File
import javax.swing.JFileChooser
import javax.swing.SwingUtilities
import javax.swing.filechooser.FileNameExtensionFilter

@Composable
fun Toolbar(vm: AppViewModel) {
    val canUndo by vm.canUndo
    val canRedo by vm.canRedo
    val selection by vm.selection
    val snap by vm.snapshot
    val sketchMode by vm.sketchMode

    val selectedIsSketch = selection?.let { snap.features[it] } is Feature.Sketch

    Row(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (sketchMode) {
            SketchToolbar(vm)
        } else {
            Button(onClick = { vm.addBox() }) { Text("Add Box") }
            OutlinedButton(onClick = { vm.addEmptySketch() }) { Text("Add Sketch") }
            Spacer(Modifier.width(12.dp))
            OutlinedButton(onClick = { vm.undo() }, enabled = canUndo) { Text("Undo") }
            OutlinedButton(onClick = { vm.redo() }, enabled = canRedo) { Text("Redo") }
            Spacer(Modifier.width(12.dp))
            OutlinedButton(
                onClick = { vm.enterSketchMode() },
                enabled = selectedIsSketch,
            ) { Text("Edit Sketch") }
            OutlinedButton(
                onClick = { vm.deleteSelected() },
                enabled = selection != null,
            ) { Text("Delete") }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(
                onClick = { exportStlDialog(vm) },
                enabled = selection != null,
            ) { Text("Export STL") }
        }
    }
}

@Composable
private fun SketchToolbar(vm: AppViewModel) {
    val tool by vm.activeTool
    val canUndo by vm.canUndo
    val canRedo by vm.canRedo

    Text("Sketch:", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(end = 4.dp))
    ToolButton("Select", tool == SketchTool.SELECT) { vm.setActiveTool(SketchTool.SELECT) }
    ToolButton("Rectangle", tool == SketchTool.RECTANGLE) { vm.setActiveTool(SketchTool.RECTANGLE) }
    ToolButton("Circle", tool == SketchTool.CIRCLE) { vm.setActiveTool(SketchTool.CIRCLE) }
    ToolButton("Line", tool == SketchTool.LINE) { vm.setActiveTool(SketchTool.LINE) }
    Spacer(Modifier.width(12.dp))
    OutlinedButton(onClick = { vm.clearActiveSketch() }) { Text("Clear") }
    Spacer(Modifier.width(12.dp))
    OutlinedButton(onClick = { vm.undo() }, enabled = canUndo) { Text("Undo") }
    OutlinedButton(onClick = { vm.redo() }, enabled = canRedo) { Text("Redo") }
    Spacer(Modifier.width(12.dp))
    Button(onClick = { vm.exitSketchMode() }) { Text("Done") }
}

@Composable
private fun ToolButton(label: String, active: Boolean, onClick: () -> Unit) {
    if (active) {
        FilledTonalButton(
            onClick = onClick,
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ),
        ) { Text(label) }
    } else {
        OutlinedButton(onClick = onClick) { Text(label) }
    }
}

private fun exportStlDialog(vm: AppViewModel) {
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
