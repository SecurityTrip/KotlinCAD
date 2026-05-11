package cad.ui.sketch

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import cad.app.AppViewModel
import cad.domain.feature.SketchEntity
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.round

private const val PX_PER_UNIT = 60f          // масштаб: 60 пикселей на 1 ед.
private const val GRID_STEP = 0.5            // шаг snap-сетки в plane-units

/**
 * 2D-канвас для редактирования Sketch.
 *
 * Координаты:
 *   plane (x, y) — в единицах модели, начало в (0,0), Y вверх.
 *   screen (sx, sy) — пиксели Compose Canvas; (0,0) — левый верх.
 *   Связь: sx = cx + x*PX_PER_UNIT,  sy = cy - y*PX_PER_UNIT, где (cx,cy) — центр канваса.
 */
@Composable
fun SketchCanvas(vm: AppViewModel, modifier: Modifier = Modifier) {
    val tool by vm.activeTool
    val sketch = vm.activeSketch
    var dragStart by remember { mutableStateOf<Offset?>(null) }
    var dragCur by remember { mutableStateOf<Offset?>(null) }
    // Размер канваса в пикселях. Заполняем через onSizeChanged, не через DrawScope
    // (запись в mutableStateOf изнутри draw → рекомпозиция в неподходящий момент).
    var canvasSizePx by remember { mutableStateOf(IntSize.Zero) }

    Box(modifier = modifier.background(Color(0xFF1F2228))) {
        Canvas(
            modifier = Modifier.fillMaxSize().onSizeChanged { canvasSizePx = it }.pointerInput(tool, sketch?.id) {
                detectDragGestures(
                    onDragStart = { offset ->
                        if (tool == SketchTool.SELECT) return@detectDragGestures
                        dragStart = screenToPlane(offset, canvasSizePx)?.let(::snap)
                        dragCur = dragStart
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        dragCur = screenToPlane(change.position, canvasSizePx)?.let(::snap)
                    },
                    onDragEnd = {
                        val a = dragStart;
                        val b = dragCur
                        if (a != null && b != null && a != b) {
                            buildEntity(tool, a, b)?.let(vm::addSketchEntity)
                        }
                        dragStart = null
                        dragCur = null
                    },
                    onDragCancel = {
                        dragStart = null; dragCur = null
                    },
                )
            },
        ) {
            drawGrid()
            drawAxes()
            val sz = Offset(size.width, size.height)
            sketch?.entities?.forEach { e -> drawEntity(e, sz) }
            val a = dragStart;
            val b = dragCur
            if (a != null && b != null && tool != SketchTool.SELECT) {
                drawPreview(tool, a, b, sz)
            }
        }
    }
}

// === Coordinate conversion ===

private fun screenToPlane(offset: Offset, canvasSize: IntSize): Offset? {
    if (canvasSize.width <= 0 || canvasSize.height <= 0) return null
    val cx = canvasSize.width / 2f
    val cy = canvasSize.height / 2f
    val x = (offset.x - cx) / PX_PER_UNIT
    val y = -(offset.y - cy) / PX_PER_UNIT
    return Offset(x, y)
}

private fun planeToScreen(x: Float, y: Float, canvasSize: Offset): Offset {
    val cx = canvasSize.x / 2f
    val cy = canvasSize.y / 2f
    return Offset(cx + x * PX_PER_UNIT, cy - y * PX_PER_UNIT)
}

private fun snap(p: Offset): Offset {
    val gx = GRID_STEP.toFloat()
    return Offset(round(p.x / gx) * gx, round(p.y / gx) * gx)
}

// === Drawing ===

private fun DrawScope.drawGrid() {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val minorStep = (GRID_STEP * PX_PER_UNIT).toFloat()
    val majorStep = minorStep * 2f  // целая единица
    val minor = Color(0xFF3A3F4A)
    val major = Color(0xFF555B6B)
    // Вертикальные
    var x = cx % minorStep
    var idx = ((-cx / minorStep).toInt())
    while (x < size.width) {
        val c = if ((idx + 1000) % 2 == 0) major else minor
        drawLine(c, Offset(x, 0f), Offset(x, size.height), strokeWidth = if (c == major) 1.5f else 1f)
        x += minorStep
        idx++
    }
    // Горизонтальные
    var y = cy % minorStep
    idx = ((-cy / minorStep).toInt())
    while (y < size.height) {
        val c = if ((idx + 1000) % 2 == 0) major else minor
        drawLine(c, Offset(0f, y), Offset(size.width, y), strokeWidth = if (c == major) 1.5f else 1f)
        y += minorStep
        idx++
    }
}

private fun DrawScope.drawAxes() {
    val cx = size.width / 2f
    val cy = size.height / 2f
    drawLine(Color(0xFFE05A5A), Offset(0f, cy), Offset(size.width, cy), strokeWidth = 2.5f)   // X — red
    drawLine(Color(0xFF5AE05A), Offset(cx, 0f), Offset(cx, size.height), strokeWidth = 2.5f)  // Y — green
}

private fun DrawScope.drawEntity(e: SketchEntity, canvasSize: Offset) {
    val stroke = Stroke(width = 2f)
    val color = Color(0xFFE0E2E6)
    when (e) {
        is SketchEntity.Rectangle -> {
            val hw = e.width.toFloat() / 2f
            val hh = e.height.toFloat() / 2f
            val cx = e.centerX.toFloat()
            val cy = e.centerY.toFloat()
            val p1 = planeToScreen(cx - hw, cy - hh, canvasSize)
            val p2 = planeToScreen(cx + hw, cy - hh, canvasSize)
            val p3 = planeToScreen(cx + hw, cy + hh, canvasSize)
            val p4 = planeToScreen(cx - hw, cy + hh, canvasSize)
            drawLine(color, p1, p2, strokeWidth = stroke.width)
            drawLine(color, p2, p3, strokeWidth = stroke.width)
            drawLine(color, p3, p4, strokeWidth = stroke.width)
            drawLine(color, p4, p1, strokeWidth = stroke.width)
        }

        is SketchEntity.Circle -> {
            val center = planeToScreen(e.centerX.toFloat(), e.centerY.toFloat(), canvasSize)
            drawCircle(color, e.radius.toFloat() * PX_PER_UNIT, center, style = stroke)
        }

        is SketchEntity.Line -> {
            val p1 = planeToScreen(e.x1.toFloat(), e.y1.toFloat(), canvasSize)
            val p2 = planeToScreen(e.x2.toFloat(), e.y2.toFloat(), canvasSize)
            drawLine(color, p1, p2, strokeWidth = stroke.width)
        }
    }
}

private fun DrawScope.drawPreview(tool: SketchTool, a: Offset, b: Offset, canvasSize: Offset) {
    val color = Color(0xFFFFB347)
    val dash = PathEffect.dashPathEffect(floatArrayOf(6f, 4f))
    when (tool) {
        SketchTool.RECTANGLE -> {
            val p1 = planeToScreen(a.x, a.y, canvasSize)
            val p3 = planeToScreen(b.x, b.y, canvasSize)
            val p2 = Offset(p3.x, p1.y)
            val p4 = Offset(p1.x, p3.y)
            drawLine(color, p1, p2, strokeWidth = 1.5f, pathEffect = dash)
            drawLine(color, p2, p3, strokeWidth = 1.5f, pathEffect = dash)
            drawLine(color, p3, p4, strokeWidth = 1.5f, pathEffect = dash)
            drawLine(color, p4, p1, strokeWidth = 1.5f, pathEffect = dash)
        }

        SketchTool.CIRCLE -> {
            val center = planeToScreen(a.x, a.y, canvasSize)
            val r = hypot((b.x - a.x).toDouble(), (b.y - a.y).toDouble()).toFloat() * PX_PER_UNIT
            drawCircle(color, r, center, style = Stroke(1.5f, pathEffect = dash))
        }

        SketchTool.LINE -> {
            val p1 = planeToScreen(a.x, a.y, canvasSize)
            val p2 = planeToScreen(b.x, b.y, canvasSize)
            drawLine(color, p1, p2, strokeWidth = 1.5f, pathEffect = dash)
        }

        SketchTool.SELECT -> {}
    }
}

// === Tool → entity ===

private fun buildEntity(tool: SketchTool, a: Offset, b: Offset): SketchEntity? = when (tool) {
    SketchTool.RECTANGLE -> {
        val w = abs(b.x - a.x).toDouble()
        val h = abs(b.y - a.y).toDouble()
        if (w < 1e-6 || h < 1e-6) null
        else SketchEntity.Rectangle((a.x + b.x) / 2.0, (a.y + b.y) / 2.0, w, h)
    }

    SketchTool.CIRCLE -> {
        val r = hypot((b.x - a.x).toDouble(), (b.y - a.y).toDouble())
        if (r < 1e-6) null else SketchEntity.Circle(a.x.toDouble(), a.y.toDouble(), r)
    }

    SketchTool.LINE -> SketchEntity.Line(a.x.toDouble(), a.y.toDouble(), b.x.toDouble(), b.y.toDouble())
    SketchTool.SELECT -> null
}
