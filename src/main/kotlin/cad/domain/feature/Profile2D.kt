package cad.domain.feature

import kotlinx.serialization.Serializable

@Serializable
data class Point2D(val x: Double, val y: Double)

@Serializable
data class Profile2D(
    val points: List<Point2D>,
    val closed: Boolean = true,
) {
    companion object {
        fun rectangle(centerX: Double, centerY: Double, width: Double, height: Double): Profile2D {
            val hw = width / 2.0
            val hh = height / 2.0
            return Profile2D(
                points = listOf(
                    Point2D(centerX - hw, centerY - hh),
                    Point2D(centerX + hw, centerY - hh),
                    Point2D(centerX + hw, centerY + hh),
                    Point2D(centerX - hw, centerY + hh),
                ),
                closed = true,
            )
        }

        /** Полигональная аппроксимация окружности. 32 сегмента — компромисс между гладкостью и числом треугольников. */
        fun circle(centerX: Double, centerY: Double, radius: Double, segments: Int = 32): Profile2D {
            require(segments >= 3) { "circle needs at least 3 segments" }
            val pts = ArrayList<Point2D>(segments)
            for (i in 0 until segments) {
                val a = i * 2.0 * Math.PI / segments
                pts += Point2D(centerX + radius * kotlin.math.cos(a), centerY + radius * kotlin.math.sin(a))
            }
            return Profile2D(pts, closed = true)
        }
    }
}
