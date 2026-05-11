package cad.kernel.mesh

/**
 * Triangle mesh: vertices (xyz triples), per-vertex normals (xyz triples), indices.
 * Никаких UV/цветов — добавим, когда понадобятся. Equals/hashCode по содержимому
 * специально не реализованы: меши большие, сравнение — это не наш use case.
 */
class Mesh(
    val vertices: FloatArray,
    val normals: FloatArray,
    val indices: IntArray,
) {
    val triangleCount: Int get() = indices.size / 3
    val vertexCount: Int get() = vertices.size / 3

    init {
        require(vertices.size % 3 == 0) { "vertices length must be multiple of 3" }
        require(normals.size == vertices.size) { "normals must match vertices length" }
        require(indices.size % 3 == 0) { "indices length must be multiple of 3" }
    }

    companion object {
        val EMPTY = Mesh(FloatArray(0), FloatArray(0), IntArray(0))
    }
}

class MeshBuilder {
    private val verts = ArrayList<Float>()
    private val norms = ArrayList<Float>()
    private val idx = ArrayList<Int>()

    /** Добавляет треугольник, вычисляя face-normal. */
    fun addTriangle(
        ax: Float, ay: Float, az: Float,
        bx: Float, by: Float, bz: Float,
        cx: Float, cy: Float, cz: Float,
    ) {
        val ux = bx - ax; val uy = by - ay; val uz = bz - az
        val vx = cx - ax; val vy = cy - ay; val vz = cz - az
        var nx = uy * vz - uz * vy
        var ny = uz * vx - ux * vz
        var nz = ux * vy - uy * vx
        val len = kotlin.math.sqrt(nx * nx + ny * ny + nz * nz)
        if (len > 0f) {
            nx /= len; ny /= len; nz /= len
        }
        val base = verts.size / 3
        verts += ax; verts += ay; verts += az
        verts += bx; verts += by; verts += bz
        verts += cx; verts += cy; verts += cz
        repeat(3) { norms += nx; norms += ny; norms += nz }
        idx += base; idx += base + 1; idx += base + 2
    }

    fun addQuad(
        a: FloatArray, b: FloatArray, c: FloatArray, d: FloatArray,
    ) {
        addTriangle(a[0], a[1], a[2], b[0], b[1], b[2], c[0], c[1], c[2])
        addTriangle(a[0], a[1], a[2], c[0], c[1], c[2], d[0], d[1], d[2])
    }

    fun build(): Mesh = Mesh(
        verts.toFloatArray(),
        norms.toFloatArray(),
        idx.toIntArray(),
    )
}
