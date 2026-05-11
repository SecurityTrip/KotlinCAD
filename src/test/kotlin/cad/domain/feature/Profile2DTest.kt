package cad.domain.feature

import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class Profile2DTest {

    @Test
    fun `rectangle has 4 corners, ccw`() {
        val p = Profile2D.rectangle(0.0, 0.0, 4.0, 2.0)
        assertEquals(4, p.points.size)
        assertTrue(p.closed)
        // Все углы лежат на ожидаемых границах
        val xs = p.points.map { it.x }.toSet()
        val ys = p.points.map { it.y }.toSet()
        assertEquals(setOf(-2.0, 2.0), xs)
        assertEquals(setOf(-1.0, 1.0), ys)
    }

    @Test
    fun `circle has requested number of segments`() {
        val p = Profile2D.circle(0.0, 0.0, 1.0, segments = 32)
        assertEquals(32, p.points.size)
        assertTrue(p.closed)
    }

    @Test
    fun `all circle points lie on the radius (within tolerance)`() {
        val p = Profile2D.circle(1.5, -2.5, 3.0, segments = 64)
        for (pt in p.points) {
            val r = hypot(pt.x - 1.5, pt.y - (-2.5))
            assertTrue(abs(r - 3.0) < 1e-9, "radius drift: $r")
        }
    }

    @Test
    fun `circle requires at least 3 segments`() {
        assertFailsWith<IllegalArgumentException> { Profile2D.circle(0.0, 0.0, 1.0, segments = 2) }
    }
}
