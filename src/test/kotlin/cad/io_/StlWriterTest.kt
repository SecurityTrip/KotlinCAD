package cad.io_

import cad.kernel.mesh.Primitives
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StlWriterTest {

    @Test
    fun `binary STL has 80-byte header plus uint32 triangle count`() {
        val mesh = Primitives.cube(1f, 1f, 1f)
        val bytes = ByteArrayOutputStream().also { StlWriter.writeBinary(mesh, it) }.toByteArray()

        // header (80) + count (4) + triangles * 50
        val expectedTriCount = mesh.indices.size / 3
        assertEquals(80 + 4 + expectedTriCount * 50, bytes.size)

        val countLE = ByteBuffer.wrap(bytes, 80, 4).order(ByteOrder.LITTLE_ENDIAN).int
        assertEquals(expectedTriCount, countLE)
    }

    @Test
    fun `cube STL contains 12 triangles`() {
        val mesh = Primitives.cube(2f, 3f, 4f)
        val bytes = ByteArrayOutputStream().also { StlWriter.writeBinary(mesh, it) }.toByteArray()
        val triCount = ByteBuffer.wrap(bytes, 80, 4).order(ByteOrder.LITTLE_ENDIAN).int
        assertEquals(12, triCount, "cube must be 6 quads = 12 triangles")
    }

    @Test
    fun `binary STL header contains given name`(@TempDir tmp: Path) {
        val mesh = Primitives.cube(1f, 1f, 1f)
        val file = tmp.resolve("box.stl")
        StlWriter.writeBinary(mesh, file, name = "petcad-test-cube")

        val header = Files.readAllBytes(file).copyOfRange(0, 80)
        val headerStr = header.toString(Charsets.US_ASCII)
        assertContains(headerStr, "petcad-test-cube")
    }

    @Test
    fun `triangle vertices in binary STL bracket the cube bounding box`() {
        val mesh = Primitives.cube(2f, 2f, 2f)
        val bytes = ByteArrayOutputStream().also { StlWriter.writeBinary(mesh, it) }.toByteArray()
        val triCount = ByteBuffer.wrap(bytes, 80, 4).order(ByteOrder.LITTLE_ENDIAN).int

        var minX = Float.POSITIVE_INFINITY;
        var maxX = Float.NEGATIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY;
        var maxY = Float.NEGATIVE_INFINITY
        var minZ = Float.POSITIVE_INFINITY;
        var maxZ = Float.NEGATIVE_INFINITY
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        for (t in 0 until triCount) {
            val off = 84 + t * 50 + 12 // 12 bytes normal -> vertices start
            for (v in 0 until 3) {
                val x = buf.getFloat(off + v * 12)
                val y = buf.getFloat(off + v * 12 + 4)
                val z = buf.getFloat(off + v * 12 + 8)
                if (x < minX) minX = x; if (x > maxX) maxX = x
                if (y < minY) minY = y; if (y > maxY) maxY = y
                if (z < minZ) minZ = z; if (z > maxZ) maxZ = z
            }
        }
        assertEquals(-1f, minX); assertEquals(1f, maxX)
        assertEquals(-1f, minY); assertEquals(1f, maxY)
        assertEquals(-1f, minZ); assertEquals(1f, maxZ)
    }

    @Test
    fun `writeBinary creates parent directories`(@TempDir tmp: Path) {
        val mesh = Primitives.cube(1f, 1f, 1f)
        val nested = tmp.resolve("sub/dir/out.stl")
        StlWriter.writeBinary(mesh, nested)
        assertTrue(Files.exists(nested))
    }
}
