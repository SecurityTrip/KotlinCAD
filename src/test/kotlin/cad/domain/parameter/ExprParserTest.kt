package cad.domain.parameter

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ExprParserTest {

    private fun eval(src: String, env: Map<String, Double> = emptyMap()): Double {
        val expr = ExprParser.parse(src)
        return Evaluator.eval(expr) { env[it] }
    }

    @Test
    fun `parses and evaluates a plain number`() {
        assertEquals(42.0, eval("42"))
        assertEquals(3.14, eval("3.14"))
    }

    @Test
    fun `precedence multiplication binds tighter than addition`() {
        assertEquals(7.0, eval("1 + 2 * 3"))
        assertEquals(9.0, eval("(1 + 2) * 3"))
    }

    @Test
    fun `power is right-associative`() {
        // 2^3^2 == 2^(3^2) == 2^9 = 512
        assertEquals(512.0, eval("2 ^ 3 ^ 2"))
    }

    @Test
    fun `unary minus`() {
        assertEquals(-5.0, eval("-5"))
        assertEquals(-7.0, eval("-(3 + 4)"))
        assertEquals(-6.0, eval("-2 * 3"))
    }

    @Test
    fun `identifiers resolve via env`() {
        assertEquals(15.0, eval("a * b", mapOf("a" to 3.0, "b" to 5.0)))
    }

    @Test
    fun `function calls`() {
        assertEquals(2.0, eval("sqrt(4)"))
        assertEquals(7.0, eval("max(1, 7, 3)"))
        assertEquals(1.0, eval("min(5, 1, 9)"))
    }

    @Test
    fun `references extraction is accurate`() {
        val refs = ExprParser.parse("a + b * sin(c) - 2").references()
        assertEquals(setOf("a", "b", "c"), refs)
    }

    @Test
    fun `unknown identifier throws`() {
        assertFailsWith<ExprEvalException> {
            eval("foo + 1")
        }
    }

    @Test
    fun `syntax error reports position`() {
        val e = assertFailsWith<ExprParseException> { ExprParser.parse("1 + * 2") }
        assertTrue(e.position in 0..6)
    }

    @Test
    fun `division by zero throws`() {
        assertFailsWith<ExprEvalException> { eval("1 / 0") }
    }
}
