package com.medidorble.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import com.medidorble.data.Point2D
import com.medidorble.data.Project
import com.medidorble.data.Room
import kotlin.math.min

/**
 * Canvas 2D que renderitza el plànol en temps real.
 */
class FloorPlanView @JvmOverloads constructor(
    ctx: Context, attrs: AttributeSet? = null
) : View(ctx, attrs) {

    private var project: Project? = null
    private var activeRoom: Room? = null

    private val pWall = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1565C0"); strokeWidth = 5f
        style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND
    }
    private val pActive = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF6F00"); strokeWidth = 5f
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(20f, 10f), 0f)
    }
    private val pFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#BBD9F7"); style = Paint.Style.FILL
    }
    private val pActiveFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFE0B2"); style = Paint.Style.FILL
    }
    private val pText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#0D47A1"); textSize = 28f; textAlign = Paint.Align.CENTER
    }
    private val pDim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#B71C1C"); textSize = 20f; textAlign = Paint.Align.CENTER
    }
    private val pGrid = Paint().apply {
        color = Color.parseColor("#E8E8E8"); strokeWidth = 1f; style = Paint.Style.STROKE
    }
    private val pDot = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F44336"); style = Paint.Style.FILL
    }

    fun setProject(proj: Project, active: Room?) {
        project = proj; activeRoom = active; invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.WHITE)
        drawGrid(canvas)

        val proj = project ?: return
        val allVerts = proj.rooms.flatMap { it.vertices() }
        if (allVerts.isEmpty()) {
            canvas.drawText("Cap habitació", width / 2f, height / 2f, pText); return
        }

        val minX = allVerts.minOf { it.x }; val maxX = allVerts.maxOf { it.x }
        val minY = allVerts.minOf { it.y }; val maxY = allVerts.maxOf { it.y }
        val W = (maxX - minX).coerceAtLeast(0.5)
        val H = (maxY - minY).coerceAtLeast(0.5)
        val margin = 64f
        val scale = min((width - 2 * margin) / W.toFloat(), (height - 2 * margin) / H.toFloat())

        fun sx(x: Double) = margin + ((x - minX) * scale).toFloat()
        fun sy(y: Double) = height - margin - ((y - minY) * scale).toFloat()

        for (room in proj.rooms) {
            val verts = room.vertices()
            if (verts.size < 2) continue
            val isActive = room === activeRoom
            val path = Path().apply {
                moveTo(sx(verts[0].x), sy(verts[0].y))
                for (i in 1 until verts.size) lineTo(sx(verts[i].x), sy(verts[i].y))
            }
            if (room.isClosed()) { path.close(); canvas.drawPath(path, if (isActive) pActiveFill else pFill) }
            canvas.drawPath(path, if (isActive) pActive else pWall)

            // Cotes de cada paret
            for ((i, wall) in room.walls.withIndex()) {
                if (i + 1 >= verts.size) break
                val v1 = verts[i]; val v2 = verts[i + 1]
                canvas.drawText("${"%.2f".format(wall.length)}m",
                    (sx(v1.x) + sx(v2.x)) / 2f,
                    (sy(v1.y) + sy(v2.y)) / 2f - 12f, pDim)
            }

            // Etiqueta central
            val cx = verts.map { sx(it.x) }.average().toFloat()
            val cy = verts.map { sy(it.y) }.average().toFloat()
            canvas.drawText(room.name, cx, cy - 10f, pText)
            if (room.area() > 0.0)
                canvas.drawText("${"%.2f".format(room.area())} m²", cx, cy + 24f, pDim)
        }

        // Punt d'origen
        canvas.drawCircle(sx(0.0), sy(0.0), 7f, pDot)
    }

    private fun drawGrid(canvas: Canvas) {
        val s = 40f
        var x = 0f; while (x <= width)  { canvas.drawLine(x, 0f, x, height.toFloat(), pGrid); x += s }
        var y = 0f; while (y <= height) { canvas.drawLine(0f, y, width.toFloat(), y, pGrid); y += s }
    }
}
