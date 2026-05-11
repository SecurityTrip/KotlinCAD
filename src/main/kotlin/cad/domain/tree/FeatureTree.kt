package cad.domain.tree

import cad.domain.command.Command
import cad.domain.feature.Feature
import cad.domain.feature.FeatureId

/**
 * Изменяемое дерево фич с историей undo/redo.
 *
 * История держится как стек снапшотов "до" применения команды. Альтернатива
 * (replay команд) красивее, но требует чистоты и идемпотентности всех команд —
 * на MVP проще хранить снимки.
 */
class FeatureTree {

    private var current: TreeSnapshot = TreeSnapshot.EMPTY
    private val undoStack = ArrayDeque<TreeSnapshot>()
    private val redoStack = ArrayDeque<TreeSnapshot>()

    val snapshot: TreeSnapshot get() = current

    fun apply(command: Command): RecomputeResult {
        val before = current
        val (after, dirty) = applyTo(before, command)
        if (after == before) return RecomputeResult(emptySet(), before)
        undoStack.addLast(before)
        redoStack.clear()
        current = after
        return RecomputeResult(dirty, after)
    }

    fun undo(): RecomputeResult {
        val prev = undoStack.removeLastOrNull() ?: return RecomputeResult(emptySet(), current)
        redoStack.addLast(current)
        val changed = diffIds(current, prev)
        current = prev
        return RecomputeResult(changed, prev)
    }

    fun redo(): RecomputeResult {
        val next = redoStack.removeLastOrNull() ?: return RecomputeResult(emptySet(), current)
        undoStack.addLast(current)
        val changed = diffIds(current, next)
        current = next
        return RecomputeResult(changed, next)
    }

    fun canUndo(): Boolean = undoStack.isNotEmpty()
    fun canRedo(): Boolean = redoStack.isNotEmpty()

    private fun applyTo(s: TreeSnapshot, command: Command): Pair<TreeSnapshot, Set<FeatureId>> {
        return when (command) {
            is Command.AddFeature -> {
                val id = command.feature.id
                if (id in s.features) {
                    s to emptySet()
                } else {
                    val newOrder = s.order.toMutableList().apply {
                        val idx = command.atIndex ?: size
                        add(idx.coerceIn(0, size), id)
                    }
                    val newFeatures = s.features + (id to command.feature)
                    TreeSnapshot(newOrder, newFeatures) to setOf(id)
                }
            }
            is Command.UpdateParameter -> {
                val feature = s.features[command.featureId]
                val param = feature?.parameters?.get(command.parameterName)
                if (feature == null || param == null || param.value == command.newValue) {
                    s to emptySet()
                } else {
                    val updated = feature.withParameters(
                        feature.parameters + (command.parameterName to param.withValue(command.newValue))
                    )
                    val newSnap = s.copy(features = s.features + (feature.id to updated))
                    val dirty = setOf(feature.id) + DependencyGraph.downstreamOf(feature.id, newSnap)
                    newSnap to dirty
                }
            }
            is Command.DeleteFeature -> {
                if (command.featureId !in s.features) {
                    s to emptySet()
                } else {
                    val downstream = DependencyGraph.downstreamOf(command.featureId, s)
                    val toRemove = downstream + command.featureId
                    val newOrder = s.order.filter { it !in toRemove }
                    val newFeatures = s.features - toRemove
                    TreeSnapshot(newOrder, newFeatures) to toRemove
                }
            }
            is Command.ReorderFeatures -> {
                if (command.newOrder.toSet() != s.order.toSet()) {
                    s to emptySet()
                } else {
                    s.copy(order = command.newOrder) to emptySet()
                }
            }
        }
    }

    private fun diffIds(a: TreeSnapshot, b: TreeSnapshot): Set<FeatureId> {
        val ids = a.features.keys + b.features.keys
        return ids.filter { a.features[it] != b.features[it] }.toSet()
    }
}
