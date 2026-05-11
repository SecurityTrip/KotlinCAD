package cad.render

import org.joml.Matrix4f
import org.joml.Vector3f
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Orbital камера. Y — вверх. Yaw — вращение вокруг Y, Pitch — вверх/вниз.
 * Pitch ограничен (±89°), чтобы не вырождать вид.
 */
class Camera {
    var target: Vector3f = Vector3f(0f, 0f, 0f)
    var distance: Float = 6f
    var yaw: Float = Math.toRadians(35.0).toFloat()
    var pitch: Float = Math.toRadians(25.0).toFloat()

    var fovDeg: Float = 50f
    var near: Float = 0.05f
    var far: Float = 500f

    fun orbit(dxPx: Float, dyPx: Float, sensitivity: Float = 0.005f) {
        yaw += dxPx * sensitivity
        pitch = (pitch + dyPx * sensitivity).coerceIn(-MAX_PITCH, MAX_PITCH)
    }

    fun pan(dxPx: Float, dyPx: Float) {
        val right = Vector3f()
        val up = Vector3f()
        computeBasis(right, up, Vector3f())
        val k = distance * 0.0015f
        target.fma(-dxPx * k, right)
        target.fma( dyPx * k, up)
    }

    fun zoom(wheelDelta: Float) {
        distance = (distance * Math.pow(1.1, -wheelDelta.toDouble()).toFloat()).coerceIn(0.1f, 1000f)
    }

    fun viewMatrix(): Matrix4f {
        val eye = eyePosition()
        return Matrix4f().lookAt(eye, target, Vector3f(0f, 1f, 0f))
    }

    fun projectionMatrix(aspect: Float): Matrix4f =
        Matrix4f().perspective(Math.toRadians(fovDeg.toDouble()).toFloat(), aspect, near, far)

    private fun eyePosition(): Vector3f {
        val cp = cos(pitch); val sp = sin(pitch)
        val cy = cos(yaw);   val sy = sin(yaw)
        val dir = Vector3f(cp * sy, sp, cp * cy)
        return Vector3f(target).fma(distance, dir)
    }

    private fun computeBasis(right: Vector3f, up: Vector3f, forward: Vector3f) {
        val eye = eyePosition()
        forward.set(target).sub(eye).normalize()
        right.set(forward).cross(0f, 1f, 0f).normalize()
        up.set(right).cross(forward).normalize()
    }

    companion object {
        private val MAX_PITCH = (PI / 2 - 0.01).toFloat()
    }
}
