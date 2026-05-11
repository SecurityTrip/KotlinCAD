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
import cad.domain.feature.FeatureId
import cad.domain.parameter.ExprParseException
import cad.domain.parameter.ExprParser
import cad.domain.parameter.Parameter

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
            ParameterRow(feature.id, name, p, vm)
        }
    }
}

@Composable
private fun ParameterRow(
    featureId: FeatureId,
    name: String,
    p: Parameter,
    vm: AppViewModel,
) {
    // Источник истины — текстовое поле; меняется при выборе фичи (key = id + name + formula/value).
    var text by remember(featureId, name, p.formula, p.value) {
        mutableStateOf(p.formula ?: p.value.toString())
    }
    var error by remember(featureId, name) { mutableStateOf<String?>(null) }

    OutlinedTextField(
        value = text,
        onValueChange = { newText ->
            text = newText
            commit(newText, featureId, name, p, vm, onError = { error = it }, onOk = { error = null })
        },
        label = { Text(name) },
        supportingText = {
            when {
                error != null -> Text(error ?: "", color = MaterialTheme.colorScheme.error)
                p.formula != null -> Text("= ${"%.4f".format(p.value)}", color = Color.Gray)
                else -> {}
            }
        },
        isError = error != null,
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        singleLine = true,
    )
}

private fun commit(
    text: String,
    featureId: FeatureId,
    paramName: String,
    current: Parameter,
    vm: AppViewModel,
    onError: (String) -> Unit,
    onOk: () -> Unit,
) {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return // не диспатчим пустоту

    // Сначала пробуем как литерал (число).
    val asDouble = trimmed.toDoubleOrNull()
    if (asDouble != null) {
        onOk()
        if (current.formula != null || current.value != asDouble) {
            vm.updateParameter(featureId, paramName, asDouble)
        }
        return
    }
    // Иначе — как формула. Валидируем парсингом.
    try {
        ExprParser.parse(trimmed)
    } catch (e: ExprParseException) {
        onError(e.message ?: "parse error")
        return
    }
    onOk()
    if (current.formula != trimmed) {
        vm.setParameterFormula(featureId, paramName, trimmed)
    }
}
