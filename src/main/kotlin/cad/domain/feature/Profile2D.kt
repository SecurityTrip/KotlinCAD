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
    }
}
