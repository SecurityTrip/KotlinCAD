package cad.domain.command

import cad.domain.feature.Feature
import cad.domain.feature.FeatureId

sealed interface Command {
    data class AddFeature(val feature: Feature, val atIndex: Int? = null) : Command
    data class UpdateParameter(
        val featureId: FeatureId,
        val parameterName: String,
        val newValue: Double,
    ) : Command

    /**
     * Установить формулу для параметра. `null` или пустая строка — снять
     * формулу (параметр становится литералом с текущим [Parameter.value]).
     */
    data class SetParameterFormula(
        val featureId: FeatureId,
        val parameterName: String,
        val formula: String?,
    ) : Command

    data class DeleteFeature(val featureId: FeatureId) : Command
    data class ReorderFeatures(val newOrder: List<FeatureId>) : Command
}
