package cad.domain.tree

import cad.domain.command.Command
import cad.domain.feature.Feature
import cad.domain.feature.FeatureId
import cad.domain.parameter.ParamCycleException
import cad.domain.parameter.ParamGraph
import org.slf4j.LoggerFactory

/**
 * Изменяемое дерево фич с историей undo/redo.
 *
 * История держится как стек снапшотов "до" применения команды. Альтернатива
 * (replay команд) красивее, но требует чистоты и идемпотентности всех команд —
 * на MVP проще хранить снимки.
 */
class FeatureTree {

    private val log = LoggerFactory.getLogger(FeatureTree::class.java)
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
                    val featureWithRecomputedFormulas = recomputeFormulas(command.feature)
                    val newOrder = s.order.toMutableList().apply {
                        val idx = command.atIndex ?: size
                        add(idx.coerceIn(0, size), id)
                    }
                    val newFeatures = s.features + (id to featureWithRecomputedFormulas)
                    TreeSnapshot(newOrder, newFeatures) to setOf(id)
                }
            }

            is Command.UpdateParameter -> {
                val feature = s.features[command.featureId]
                val param = feature?.parameters?.get(command.parameterName)
                if (feature == null || param == null || param.value == command.newValue) {
                    s to emptySet()
                } else {
                    // Если параметр был под формулой — UpdateParameter снимает её
                    // (пользователь явно ввёл число поверх). Иначе формула бы не дала
                    // изменению эффекта на следующем recompute.
                    val patched = param.withValue(command.newValue).copy(formula = null)
                    val withParam = feature.withParameters(
                        feature.parameters + (command.parameterName to patched)
                    )
                    val updated = recomputeFormulas(withParam)
                    val newSnap = s.copy(features = s.features + (feature.id to updated))
                    val dirty = setOf(feature.id) + DependencyGraph.downstreamOf(feature.id, newSnap)
                    newSnap to dirty
                }
            }

            is Command.SetParameterFormula -> applySetFormula(s, command)

            is Command.UpdateSketchEntities -> {
                val feature = s.features[command.sketchId] as? Feature.Sketch
                if (feature == null || feature.entities == command.newEntities) {
                    s to emptySet()
                } else {
                    val updated = feature.copy(entities = command.newEntities)
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

    private fun applySetFormula(
        s: TreeSnapshot,
        command: Command.SetParameterFormula,
    ): Pair<TreeSnapshot, Set<FeatureId>> {
        val feature = s.features[command.featureId] ?: return s to emptySet()
        val param = feature.parameters[command.parameterName] ?: return s to emptySet()
        val normalised = command.formula?.trim()?.takeIf { it.isNotEmpty() }
        if (param.formula == normalised) return s to emptySet()
        val patched = param.copy(formula = normalised)
        val withParam = feature.withParameters(feature.parameters + (command.parameterName to patched))
        val updated = try {
            recomputeFormulas(withParam)
        } catch (e: ParamCycleException) {
            log.warn(
                "Formula introduces a cycle on {}: {}; command ignored", feature.id.value, e.cycle.joinToString(" -> ")
            )
            return s to emptySet()
        }
        val newSnap = s.copy(features = s.features + (feature.id to updated))
        val dirty = setOf(feature.id) + DependencyGraph.downstreamOf(feature.id, newSnap)
        return newSnap to dirty
    }

    /**
     * Прогоняет параметры фичи через [ParamGraph]. При невалидной формуле/eval-ошибке —
     * параметр сохраняет старое значение, ошибка идёт в лог. Циклы пробрасываются
     * наверх как [ParamCycleException].
     */
    private fun recomputeFormulas(feature: Feature): Feature {
        if (feature.parameters.none { it.value.formula != null }) return feature
        val recomputed = ParamGraph.recompute(feature.parameters) { name, msg ->
            log.warn("Formula on '{}' in feature {} invalid: {}", name, feature.id.value, msg)
        }
        return if (recomputed == feature.parameters) feature else feature.withParameters(recomputed)
    }

    private fun diffIds(a: TreeSnapshot, b: TreeSnapshot): Set<FeatureId> {
        val ids = a.features.keys + b.features.keys
        return ids.filter { a.features[it] != b.features[it] }.toSet()
    }
}
