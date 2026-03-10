package com.medidorble.export

import com.medidorble.data.Point2D
import com.medidorble.data.Project
import com.medidorble.data.Room
import java.io.File

/**
 * Exporta un Project a DXF R12 (ASCII, compatibil·itat màxima amb AutoCAD/LibreCAD).
 *
 * Capes:
 *   WALLS  – polilínies de cada habitació (color blanc)
 *   DIMS   – cotes de cada paret          (color vermell)
 *   TEXT   – nom i superfície             (color verd)
 */
object DxfExporter {

    fun export(project: Project, file: File) {
        val sb = StringBuilder()
        writeHeader(sb)
        writeLayers(sb)
        sb.appendLine("0
SECTION
2
ENTITIES")

        var offsetX = 0.0
        for (room in project.rooms) {
            val verts = room.vertices()
            if (verts.size < 2) continue
            writeRoom(sb, room, verts, offsetX)
            val w = verts.maxOf { it.x } - verts.minOf { it.x }
            offsetX += w + 2.0
        }

        sb.appendLine("0
ENDSEC
0
EOF")
        file.writeText(sb.toString())
    }

    // ── Room ──────────────────────────────────────────────────────────────

    private fun writeRoom(sb: StringBuilder, room: Room, verts: List<Point2D>, ox: Double) {
        // Poliline tancada o oberta
        sb.appendLine("0
POLYLINE
8
WALLS
66
1
70
${if (room.isClosed()) 1 else 0}")
        for (v in verts) {
            sb.appendLine("0
VERTEX
8
WALLS")
            sb.appendLine("10
${fmt(v.x + ox)}
20
${fmt(v.y)}
30
0.0")
        }
        sb.appendLine("0
SEQEND")

        // Cotes
        for ((i, wall) in room.walls.withIndex()) {
            if (i + 1 >= verts.size) break
            val v1 = verts[i]; val v2 = verts[i + 1]
            val mx = (v1.x + v2.x) / 2.0 + ox
            val my = (v1.y + v2.y) / 2.0 + 0.06
            writeText(sb, mx, my, 0.07, "${fmtM(wall.length)} m", "DIMS")
        }

        // Nom + superfície al centroide
        val cx = verts.map { it.x + ox }.average()
        val cy = verts.map { it.y }.average()
        writeText(sb, cx, cy + 0.15, 0.12, room.name, "TEXT")
        if (room.area() > 0.0)
            writeText(sb, cx, cy, 0.10, "S = ${fmtM(room.area())} m²", "TEXT")
        writeText(sb, cx, cy - 0.15, 0.08, "P = ${fmtM(room.perimeter())} m", "TEXT")
    }

    // ── DXF structure ─────────────────────────────────────────────────────

    private fun writeHeader(sb: StringBuilder) {
        sb.appendLine("0
SECTION
2
HEADER")
        sb.appendLine("9
\$ACADVER
1
AC1009")   // R12
        sb.appendLine("9
\$INSUNITS
70
4")       // 4 = metres
        sb.appendLine("9
\$LIMMAX
10
100.0
20
100.0")
        sb.appendLine("0
ENDSEC")
    }

    private fun writeLayers(sb: StringBuilder) {
        sb.appendLine("0
SECTION
2
TABLES
0
TABLE
2
LAYER
70
3")
        for ((name, color) in listOf("WALLS" to 7, "DIMS" to 1, "TEXT" to 3)) {
            sb.appendLine("0
LAYER
2
$name
70
0
62
$color
6
CONTINUOUS")
        }
        sb.appendLine("0
ENDTABLE
0
ENDSEC")
    }

    private fun writeText(sb: StringBuilder, x: Double, y: Double,
                          h: Double, text: String, layer: String) {
        sb.appendLine("0
TEXT
8
$layer")
        sb.appendLine("10
${fmt(x)}
20
${fmt(y)}
30
0.0")
        sb.appendLine("40
${fmt(h)}
1
$text
72
1")
    }

    private fun fmt(d: Double)  = "%.6f".format(d)
    private fun fmtM(d: Double) = "%.3f".format(d)
}
