package com.medidorble.export

import com.medidorble.data.Project
import com.medidorble.data.Room
import com.medidorble.data.Point2D
import java.io.File
import kotlin.math.*

object DxfExporter {

    fun export(project: Project, file: File) {
        val sb = StringBuilder()
        writeHeader(sb)
        writeLayers(sb)
        sb.append("0\nSECTION\n2\nENTITIES\n")
        var offsetX = 0.0
        for (room in project.rooms) {
            val verts = room.vertices()
            if (verts.size < 2) continue
            writeRoom(sb, room, verts, offsetX)
            val w = verts.maxOf { it.x } - verts.minOf { it.x }
            offsetX += w + 2.0
        }
        sb.append("0\nENDSEC\n0\nEOF\n")
        file.writeText(sb.toString())
    }

    private fun writeRoom(sb: StringBuilder, room: Room, verts: List<Point2D>, ox: Double) {
        val closed = if (room.isClosed()) 1 else 0
        sb.append("0\nPOLYLINE\n8\nWALLS\n66\n1\n70\n$closed\n")
        for (v in verts) {
            sb.append("0\nVERTEX\n8\nWALLS\n")
            sb.append("10\n${fmt(v.x + ox)}\n20\n${fmt(v.y)}\n30\n0.0\n")
        }
        sb.append("0\nSEQEND\n")
        for ((i, wall) in room.walls.withIndex()) {
            if (i + 1 >= verts.size) break
            val v1 = verts[i]; val v2 = verts[i + 1]
            val mx = (v1.x + v2.x) / 2.0 + ox
            val my = (v1.y + v2.y) / 2.0 + 0.06
            writeText(sb, mx, my, 0.07, "${fmtM(wall.length)} m", "DIMS")
        }
        val cx = verts.map { it.x + ox }.average()
        val cy = verts.map { it.y }.average()
        writeText(sb, cx, cy + 0.15, 0.12, room.name, "TEXT")
        if (room.area() > 0.0)
            writeText(sb, cx, cy, 0.10, "S = ${fmtM(room.area())} m2", "TEXT")
        writeText(sb, cx, cy - 0.15, 0.08, "P = ${fmtM(room.perimeter())} m", "TEXT")
    }

    private fun writeHeader(sb: StringBuilder) {
        sb.append("0\nSECTION\n2\nHEADER\n")
        sb.append("9\n\$ACADVER\n1\nAC1009\n")
        sb.append("9\n\$INSUNITS\n70\n4\n")
        sb.append("0\nENDSEC\n")
    }

    private fun writeLayers(sb: StringBuilder) {
        sb.append("0\nSECTION\n2\nTABLES\n0\nTABLE\n2\nLAYER\n70\n3\n")
        for ((name, color) in listOf("WALLS" to 7, "DIMS" to 1, "TEXT" to 3)) {
            sb.append("0\nLAYER\n2\n$name\n70\n0\n62\n$color\n6\nCONTINUOUS\n")
        }
        sb.append("0\nENDTABLE\n0\nENDSEC\n")
    }

    private fun writeText(sb: StringBuilder, x: Double, y: Double, h: Double, text: String, layer: String) {
        sb.append("0\nTEXT\n8\n$layer\n")
        sb.append("10\n${fmt(x)}\n20\n${fmt(y)}\n30\n0.0\n")
        sb.append("40\n${fmt(h)}\n1\n$text\n72\n1\n")
    }

    private fun fmt(d: Double) = "%.6f".format(d)
    private fun fmtM(d: Double) = "%.3f".format(d)
}
