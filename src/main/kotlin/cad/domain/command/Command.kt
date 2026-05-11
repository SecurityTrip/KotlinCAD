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
    data class DeleteFeature(val featureId: FeatureId) : Command
    data class ReorderFeatures(val newOrder: List<FeatureId>) : Command
}
