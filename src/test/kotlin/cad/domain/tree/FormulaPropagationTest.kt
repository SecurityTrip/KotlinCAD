package cad.domain.tree

import cad.domain.command.Command
import cad.domain.feature.Feature
import cad.domain.feature.FeatureId
import cad.domain.parameter.Parameter
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FormulaPropagationTest {

    private fun extrude(
        params: Map<String, Parameter>,
        id: String = "e1",
        sketchId: String = "s1",
    ) = Feature.Extrude(
        id = FeatureId(id),
        name = "ex",
        sketchId = FeatureId(sketchId),
        parameters = params,
    )

    @Test
    fun `AddFeature evaluates formulas on insertion`() {
        val tree = FeatureTree()
        val e = extrude(
            mapOf(
                "w" to Parameter("w", 5.0),
                "d" to Parameter("d", 0.0, formula = "w * 2"),
            )
        )
        val r = tree.apply(Command.AddFeature(e))
        val stored = r.snapshot.features[e.id] as Feature.Extrude
        assertEquals(5.0, stored.parameters["w"]?.value)
        assertEquals(10.0, stored.parameters["d"]?.value)
    }

    @Test
    fun `UpdateParameter recomputes downstream formula in same feature`() {
        val tree = FeatureTree()
        val e = extrude(
            mapOf(
                "w" to Parameter("w", 2.0),
                "d" to Parameter("d", 0.0, formula = "w + 3"),
            )
        )
        tree.apply(Command.AddFeature(e))

        val r = tree.apply(Command.UpdateParameter(e.id, "w", 7.0))
        val stored = r.snapshot.features[e.id] as Feature.Extrude
        assertEquals(7.0, stored.parameters["w"]?.value)
        assertEquals(10.0, stored.parameters["d"]?.value)
    }

    @Test
    fun `UpdateParameter on a formula param drops its formula`() {
        val tree = FeatureTree()
        val e = extrude(
            mapOf(
                "w" to Parameter("w", 2.0),
                "d" to Parameter("d", 0.0, formula = "w + 3"),
            )
        )
        tree.apply(Command.AddFeature(e))

        val r = tree.apply(Command.UpdateParameter(e.id, "d", 100.0))
        val stored = r.snapshot.features[e.id] as Feature.Extrude
        assertEquals(100.0, stored.parameters["d"]?.value)
        assertNull(stored.parameters["d"]?.formula, "explicit numeric edit must clear the formula")
    }

    @Test
    fun `SetParameterFormula propagates immediately`() {
        val tree = FeatureTree()
        val e = extrude(
            mapOf(
                "w" to Parameter("w", 4.0),
                "d" to Parameter("d", 0.0),
            )
        )
        tree.apply(Command.AddFeature(e))

        val r = tree.apply(Command.SetParameterFormula(e.id, "d", "w * w"))
        val stored = r.snapshot.features[e.id] as Feature.Extrude
        assertEquals("w * w", stored.parameters["d"]?.formula)
        assertEquals(16.0, stored.parameters["d"]?.value)
    }

    @Test
    fun `SetParameterFormula that introduces a cycle is rejected`() {
        val tree = FeatureTree()
        val e = extrude(
            mapOf(
                "a" to Parameter("a", 1.0, formula = "b + 1"),
                "b" to Parameter("b", 1.0),
            )
        )
        tree.apply(Command.AddFeature(e))

        // Цикл: a := b + 1, b := a → должен быть отброшен, дерево не меняется.
        val before = tree.snapshot
        val r = tree.apply(Command.SetParameterFormula(e.id, "b", "a"))
        assertEquals(before, r.snapshot, "cycle-introducing command must be a no-op")
    }
}
