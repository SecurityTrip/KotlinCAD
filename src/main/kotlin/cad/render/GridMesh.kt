package cad.render

internal object GridMesh {

    /** Линии сетки на плоскости Y=0 в виде GL_LINES (пары точек). */
    fun build(half: Int = 10, step: Float = 1f): FloatArray {
        val lines = ArrayList<Float>((half * 2 + 1) * 4 * 3)
        val extent = half * step
        for (i in -half..half) {
            val p = i * step
            // Параллельные оси X (меняется Z)
            lines += -extent; lines += 0f; lines += p
            lines +=  extent; lines += 0f; lines += p
            // Параллельные оси Z (меняется X)
            lines += p; lines += 0f; lines += -extent
            lines += p; lines += 0f; lines +=  extent
        }
        return lines.toFloatArray()
    }
}
