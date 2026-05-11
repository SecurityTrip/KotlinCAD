package cad.domain.tree

import cad.domain.feature.FeatureId

object DependencyGraph {
    /**
     * Возвращает множество фич, которые ссылаются (прямо или транзитивно) на [root].
     * Используется для dirty-распространения при изменении параметра/состава фичи.
     */
    fun downstreamOf(root: FeatureId, snapshot: TreeSnapshot): Set<FeatureId> {
        val dependents = mutableMapOf<FeatureId, MutableSet<FeatureId>>()
        for (f in snapshot.features.values) {
            for (ref in f.referencedFeatures()) {
                dependents.getOrPut(ref) { mutableSetOf() }.add(f.id)
            }
        }
        val visited = mutableSetOf<FeatureId>()
        val stack = ArrayDeque<FeatureId>()
        stack.addLast(root)
        while (stack.isNotEmpty()) {
            val cur = stack.removeLast()
            val next = dependents[cur] ?: continue
            for (n in next) {
                if (visited.add(n)) stack.addLast(n)
            }
        }
        return visited
    }
}
