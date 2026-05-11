package cad.domain.tree

import cad.domain.feature.Feature
import cad.domain.feature.FeatureId
import kotlinx.serialization.Serializable

@Serializable
data class TreeSnapshot(
    val order: List<FeatureId>,
    val features: Map<FeatureId, Feature>,
) {
    val ordered: List<Feature> get() = order.mapNotNull { features[it] }

    companion object {
        val EMPTY = TreeSnapshot(emptyList(), emptyMap())
    }
}

data class RecomputeResult(
    val dirty: Set<FeatureId>,
    val snapshot: TreeSnapshot,
)
