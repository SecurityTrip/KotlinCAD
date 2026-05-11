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
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FeatureTreeTest {

    private fun sketch(id: String = "s1"): Feature.Sketch = Feature.Sketch(
        id = FeatureId(id),
        name = "sk",
        plane = SketchPlane.XZ,
        entities = listOf(SketchEntity.Rectangle(0.0, 0.0, 1.0, 1.0)),
    )

    private fun extrude(id: String = "e1", sketchId: String = "s1", depth: Double = 1.0): Feature.Extrude =
        Feature.Extrude(
            id = FeatureId(id),
            name = "ex",
            sketchId = FeatureId(sketchId),
            parameters = mapOf(
                "width" to Parameter("width", 2.0),
                "depth" to Parameter("depth", depth),
            ),
        )

    @Test
    fun `apply AddFeature adds feature and marks it dirty`() {
        val tree = FeatureTree()
        val s = sketch()
        val result = tree.apply(Command.AddFeature(s))

        assertEquals(setOf(s.id), result.dirty)
        assertEquals(listOf(s.id), result.snapshot.order)
        assertNotNull(result.snapshot.features[s.id])
        assertTrue(tree.canUndo())
        assertFalse(tree.canRedo())
    }

    @Test
    fun `undo reverses last command`() {
        val tree = FeatureTree()
        val s = sketch()
        tree.apply(Command.AddFeature(s))

        val undo = tree.undo()
        assertEquals(emptyList(), undo.snapshot.order)
        assertNull(undo.snapshot.features[s.id])
        assertContains(undo.dirty, s.id)
        assertTrue(tree.canRedo())
        assertFalse(tree.canUndo())
    }

    @Test
    fun `redo replays undone command`() {
        val tree = FeatureTree()
        val s = sketch()
        tree.apply(Command.AddFeature(s))
        tree.undo()

        val redo = tree.redo()
        assertEquals(listOf(s.id), redo.snapshot.order)
        assertNotNull(redo.snapshot.features[s.id])
        assertContains(redo.dirty, s.id)
    }

    @Test
    fun `UpdateParameter on Sketch marks downstream Extrude dirty`() {
        val tree = FeatureTree()
        val s = sketch()
        // Sketch ещё не имеет параметров — навесим один для теста распространения.
        val sWithParam = s.withParameters(mapOf("size" to Parameter("size", 1.0)))
        val e = extrude(sketchId = s.id.value)
        tree.apply(Command.AddFeature(sWithParam))
        tree.apply(Command.AddFeature(e))

        val r = tree.apply(Command.UpdateParameter(s.id, "size", 5.0))

        assertContains(r.dirty, s.id)
        assertContains(r.dirty, e.id, "downstream Extrude must be dirty when its Sketch changes")
        assertEquals(5.0, r.snapshot.features[s.id]?.parameters?.get("size")?.value)
    }

    @Test
    fun `UpdateParameter with same value is no-op`() {
        val tree = FeatureTree()
        val e = extrude(depth = 3.0)
        // Сначала привязываем фейковый sketch, чтобы Extrude ссылка резолвилась
        tree.apply(Command.AddFeature(sketch()))
        tree.apply(Command.AddFeature(e))

        val r = tree.apply(Command.UpdateParameter(e.id, "depth", 3.0))
        assertTrue(r.dirty.isEmpty(), "no-op update must not produce dirty set")
    }
}
