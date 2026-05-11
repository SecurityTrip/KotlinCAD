package cad.domain.feature

import cad.domain.parameter.Parameter
import kotlinx.serialization.Serializable

@JvmInline
@Serializable
value class FeatureId(val value: String)

enum class SketchPlane { XY, XZ, YZ }

@Serializable
sealed interface SketchEntity {
    @Serializable
    data class Rectangle(
        val centerX: Double,
        val centerY: Double,
        val width: Double,
        val height: Double,
    ) : SketchEntity

    @Serializable
    data class Circle(
        val centerX: Double,
        val centerY: Double,
        val radius: Double,
    ) : SketchEntity

    @Serializable
    data class Line(
        val x1: Double, val y1: Double,
        val x2: Double, val y2: Double,
    ) : SketchEntity
}

enum class BoolOp { UNION, DIFFERENCE, INTERSECTION }

@Serializable
sealed interface Feature {
    val id: FeatureId
    val name: String
    val parameters: Map<String, Parameter>

    fun withParameters(newParameters: Map<String, Parameter>): Feature
    fun referencedFeatures(): Set<FeatureId>

    @Serializable
    data class Sketch(
        override val id: FeatureId,
        override val name: String,
        val plane: SketchPlane,
        val entities: List<SketchEntity>,
        override val parameters: Map<String, Parameter> = emptyMap(),
    ) : Feature {
        override fun withParameters(newParameters: Map<String, Parameter>) =
            copy(parameters = newParameters)

        override fun referencedFeatures(): Set<FeatureId> = emptySet()
    }

    @Serializable
    data class Extrude(
        override val id: FeatureId,
        override val name: String,
        val sketchId: FeatureId,
        override val parameters: Map<String, Parameter>,
    ) : Feature {
        val depth: Double get() = parameters["depth"]?.value ?: 1.0

        override fun withParameters(newParameters: Map<String, Parameter>) =
            copy(parameters = newParameters)

        override fun referencedFeatures(): Set<FeatureId> = setOf(sketchId)
    }

    @Serializable
    data class Revolve(
        override val id: FeatureId,
        override val name: String,
        val sketchId: FeatureId,
        val axis: RevolveAxis,
        override val parameters: Map<String, Parameter>,
    ) : Feature {
        val angleDeg: Double get() = parameters["angle"]?.value ?: 360.0

        override fun withParameters(newParameters: Map<String, Parameter>) =
            copy(parameters = newParameters)

        override fun referencedFeatures(): Set<FeatureId> = setOf(sketchId)
    }

    @Serializable
    data class BooleanFeature(
        override val id: FeatureId,
        override val name: String,
        val left: FeatureId,
        val right: FeatureId,
        val op: BoolOp,
        override val parameters: Map<String, Parameter> = emptyMap(),
    ) : Feature {
        override fun withParameters(newParameters: Map<String, Parameter>) =
            copy(parameters = newParameters)

        override fun referencedFeatures(): Set<FeatureId> = setOf(left, right)
    }
}

@Serializable
enum class RevolveAxis { X, Y, Z }
