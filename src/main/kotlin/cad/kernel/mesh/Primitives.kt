package cad.kernel.mesh

object Primitives {

    fun cube(sizeX: Float = 1f, sizeY: Float = 1f, sizeZ: Float = 1f): Mesh {
        val hx = sizeX / 2f; val hy = sizeY / 2f; val hz = sizeZ / 2f
        val p000 = floatArrayOf(-hx, -hy, -hz)
        val p100 = floatArrayOf( hx, -hy, -hz)
        val p110 = floatArrayOf( hx,  hy, -hz)
        val p010 = floatArrayOf(-hx,  hy, -hz)
        val p001 = floatArrayOf(-hx, -hy,  hz)
        val p101 = floatArrayOf( hx, -hy,  hz)
        val p111 = floatArrayOf( hx,  hy,  hz)
        val p011 = floatArrayOf(-hx,  hy,  hz)
        val b = MeshBuilder()
        // Винт CCW наружу (-Y up как right-handed, Y вверх).
        b.addQuad(p001, p101, p111, p011) // +Z front
        b.addQuad(p100, p000, p010, p110) // -Z back
        b.addQuad(p101, p100, p110, p111) // +X right
        b.addQuad(p000, p001, p011, p010) // -X left
        b.addQuad(p011, p111, p110, p010) // +Y top
        b.addQuad(p000, p100, p101, p001) // -Y bottom
        return b.build()
    }
}
