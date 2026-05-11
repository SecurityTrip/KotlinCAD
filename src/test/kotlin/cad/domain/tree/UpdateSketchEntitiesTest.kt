package cad.domain.tree

import cad.domain.command.Command
import cad.domain.feature.Feature
import cad.domain.feature.FeatureId
import cad.domain.feature.SketchEntity
import cad.domain.feature.SketchPlane
import cad.domain.parameter.Parameter
import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class UpdateSketchEntitiesTest {

    private val sketchId = FeatureId("s1")
    private val extrudeId = FeatureId("e1")

    private fun seed(tree: FeatureTree) {
        val sketch = Feature.Sketch(
            id = sketchId,
            name = "sk",
            plane = SketchPlane.XZ,
            entities = listOf(SketchEntity.Rectangle(0.0, 0.0, 1.0, 1.0)),
        )
        val extrude = Feature.Extrude(
            id = extrudeId,
            name = "ex",
            sketchId = sketchId,
            parameters = mapOf("depth" to Parameter("depth", 1.0)),
        )
        tree.apply(Command.AddFeature(sketch))
        tree.apply(Command.AddFeature(extrude))
    }

    @Test
    fun `replaces entities and marks downstream extrude dirty`() {
        val tree = FeatureTree()
        seed(tree)
        val newEntities = listOf(SketchEntity.Circle(0.0, 0.0, 2.5))
        val r = tree.apply(Command.UpdateSketchEntities(sketchId, newEntities))
        val updated = r.snapshot.features[sketchId] as Feature.Sketch
        assertEquals(newEntities, updated.entities)
        assertContains(r.dirty, sketchId)
        assertContains(r.dirty, extrudeId, "extrude downstream of sketch must be dirty")
    }

    @Test
    fun `no-op when entities did not change`() {
        val tree = FeatureTree()
        seed(tree)
        val same = listOf(SketchEntity.Rectangle(0.0, 0.0, 1.0, 1.0))
        val before = tree.snapshot
        val r = tree.apply(Command.UpdateSketchEntities(sketchId, same))
        assertEquals(before, r.snapshot)
        assertEquals(emptySet(), r.dirty)
    }

    @Test
    fun `update is undoable`() {
        val tree = FeatureTree()
        seed(tree)
        val newEntities = listOf(SketchEntity.Circle(0.0, 0.0, 2.5))
        tree.apply(Command.UpdateSketchEntities(sketchId, newEntities))
        tree.undo()
        val sketch = tree.snapshot.features[sketchId] as Feature.Sketch
        assertEquals(listOf(SketchEntity.Rectangle(0.0, 0.0, 1.0, 1.0)), sketch.entities)
    }
}
