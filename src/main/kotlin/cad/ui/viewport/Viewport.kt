package cad.ui.viewport

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import cad.render.Camera
import cad.render.GlRenderer
import cad.render.SceneState
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GLCapabilities
import org.lwjgl.opengl.awt.AWTGLCanvas
import org.lwjgl.opengl.awt.GLData
import org.slf4j.LoggerFactory
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseMotionAdapter
import java.awt.event.MouseWheelEvent
import javax.swing.SwingUtilities
import javax.swing.Timer

@Composable
fun Viewport(
    camera: Camera,
    sceneState: SceneState,
    modifier: Modifier = Modifier,
) {
    val canvasHolder = remember { CanvasHolder(camera, sceneState) }

    DisposableEffect(Unit) {
        onDispose { canvasHolder.dispose() }
    }

    SwingPanel(
        modifier = modifier,
        factory = { canvasHolder.create() },
    )
}

private class CanvasHolder(
    private val camera: Camera,
    private val sceneState: SceneState,
) {
    private val log = LoggerFactory.getLogger(CanvasHolder::class.java)
    private var canvas: AWTGLCanvas? = null
    private var timer: Timer? = null
    private var renderer: GlRenderer? = null
    private var caps: GLCapabilities? = null

    fun create(): AWTGLCanvas {
        val data = GLData().apply {
            majorVersion = 3
            minorVersion = 3
            profile = GLData.Profile.CORE
            forwardCompatible = true
            depthSize = 24
            samples = 4
        }
        val r = GlRenderer()
        val c = object : AWTGLCanvas(data) {
            override fun initGL() {
                caps = GL.createCapabilities()
                r.init()
            }

            override fun paintGL() {
                val w = (width * 1.0).toInt()
                val h = (height * 1.0).toInt()
                r.render(w, h, camera, sceneState.scene.value)
                swapBuffers()
            }
        }
        renderer = r
        attachInteractions(c)
        // Render-loop через Swing Timer: ~60 FPS, render идёт на EDT — это требование AWTGLCanvas.
        val t = Timer(16) {
            if (c.isValid) {
                c.render()
            }
        }
        t.start()
        canvas = c
        timer = t
        return c
    }

    fun dispose() {
        timer?.stop()
        timer = null
        val c = canvas
        if (c != null) {
            SwingUtilities.invokeLater {
                c.runInContext {
                    renderer?.dispose()
                }
            }
        }
        renderer = null
        canvas = null
    }

    private fun attachInteractions(c: AWTGLCanvas) {
        var lastX = 0; var lastY = 0; var button = 0
        c.addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                lastX = e.x; lastY = e.y; button = e.button
                c.requestFocusInWindow()
            }
        })
        c.addMouseMotionListener(object : MouseMotionAdapter() {
            override fun mouseDragged(e: MouseEvent) {
                val dx = (e.x - lastX).toFloat()
                val dy = (e.y - lastY).toFloat()
                lastX = e.x; lastY = e.y
                when (button) {
                    MouseEvent.BUTTON1 -> camera.orbit(dx, dy)
                    MouseEvent.BUTTON2 -> camera.pan(dx, dy)
                    MouseEvent.BUTTON3 -> camera.pan(dx, dy)
                }
            }
        })
        c.addMouseWheelListener { e: MouseWheelEvent ->
            camera.zoom(-e.preciseWheelRotation.toFloat())
        }
    }
}
