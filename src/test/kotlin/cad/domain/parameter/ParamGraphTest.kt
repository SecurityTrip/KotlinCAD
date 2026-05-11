package cad.domain.parameter

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ParamGraphTest {

    private fun p(name: String, value: Double = 0.0, formula: String? = null) =
        Parameter(name, value, formula)

    @Test fun `literal-only params are unchanged`() {
        val src = mapOf("a" to p("a", 1.0), "b" to p("b", 2.0))
        val out = ParamGraph.recompute(src)
        assertEquals(src, out)
    }

    @Test fun `simple formula computes from literal`() {
        val src = mapOf(
            "w" to p("w", 5.0),
            "d" to p("d", formula = "w * 2"),
        )
        val out = ParamGraph.recompute(src)
        assertEquals(10.0, out["d"]?.value)
    }

    @Test fun `chained formulas evaluate in topological order`() {
        val src = mapOf(
            "a" to p("a", 2.0),
            "b" to p("b", formula = "a + 1"),       // 3
            "c" to p("c", formula = "b * 2 + a"),   // 8
        )
        val out = ParamGraph.recompute(src)
        assertEquals(3.0, out["b"]?.value)
        assertEquals(8.0, out["c"]?.value)
    }

    @Test fun `direct cycle is detected`() {
        val src = mapOf(
            "x" to p("x", formula = "x + 1"),
        )
        val ex = assertFailsWith<ParamCycleException> { ParamGraph.recompute(src) }
        assertEquals(listOf("x", "x"), ex.cycle)
    }

    @Test fun `indirect cycle is detected`() {
        val src = mapOf(
            "a" to p("a", formula = "b"),
            "b" to p("b", formula = "c"),
            "c" to p("c", formula = "a"),
        )
        assertFailsWith<ParamCycleException> { ParamGraph.recompute(src) }
    }

    @Test fun `bad formula keeps previous value, no exception`() {
        val warnings = mutableListOf<Pair<String, String>>()
        val src = mapOf(
            "a" to p("a", 7.0),
            "b" to p("b", 42.0, formula = "1 + )"),
        )
        val out = ParamGraph.recompute(src) { name, msg -> warnings += name to msg }
        // b остался 42.0 — формулу не смогли применить.
        assertEquals(42.0, out["b"]?.value)
        assertEquals(1, warnings.size)
        assertEquals("b", warnings.first().first)
    }

    @Test fun `eval error (unknown ref) is reported via warn`() {
        val warnings = mutableListOf<Pair<String, String>>()
        val src = mapOf(
            "a" to p("a", 7.0),
            "b" to p("b", 1.0, formula = "nope + 1"),
        )
        val out = ParamGraph.recompute(src) { name, msg -> warnings += name to msg }
        assertEquals(1.0, out["b"]?.value)
        assertEquals(1, warnings.size)
    }
}
