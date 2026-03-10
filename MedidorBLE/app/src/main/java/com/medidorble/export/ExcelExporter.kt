package com.medidorble.export

import com.medidorble.data.Project
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Genera un fitxer XLSX (Office Open XML) manualment, sense libraries externes.
 * Compatible amb Excel 2007+, LibreOffice Calc, Google Sheets.
 *
 * Columnes:
 *   A: Habitació   B: Paret   C: Longitud (m)   D: Superfície (m²)   E: Perímetre (m)
 */
object ExcelExporter {

    fun export(project: Project, file: File) {
        val ss = mutableListOf<String>()          // shared strings
        val sheetXml = buildSheet(project, ss)

        FileOutputStream(file).use { fos ->
            ZipOutputStream(fos).use { zip ->
                zip.put("[Content_Types].xml", contentTypes())
                zip.put("_rels/.rels", rels())
                zip.put("xl/_rels/workbook.xml.rels", wbRels())
                zip.put("xl/workbook.xml", workbook())
                zip.put("xl/styles.xml", styles())
                zip.put("xl/sharedStrings.xml", sharedStrings(ss))
                zip.put("xl/worksheets/sheet1.xml", sheetXml)
            }
        }
    }

    // ── Sheet builder ─────────────────────────────────────────────────────

    private fun buildSheet(project: Project, ss: MutableList<String>): String {
        fun si(s: String): Int { val i = ss.indexOf(s); if (i >= 0) return i; ss.add(s); return ss.size - 1 }
        fun cs(r: Int, c: Int, idx: Int, style: Int = 0) =
            """<c r="${col(c)}$r" t="s" s="$style"><v>$idx</v></c>"""
        fun cn(r: Int, c: Int, v: Double, style: Int = 2) =
            """<c r="${col(c)}$r" s="$style"><v>${"%.3f".format(v)}</v></c>"""

        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>""")

        var row = 1

        // Header
        sb.append("""<row r="$row">""")
        for ((c, h) in listOf(1 to "Habitació", 2 to "Paret", 3 to "Longitud (m)",
                               4 to "Superfície (m²)", 5 to "Perímetre (m)"))
            sb.append(cs(row, c, si(h), style = 1))
        sb.append("</row>"); row++

        for (room in project.rooms) {
            // Room summary row (bold italic)
            sb.append("""<row r="$row">""")
            sb.append(cs(row, 1, si(room.name), style = 3))
            sb.append(cs(row, 2, si("${room.walls.size} parets"), style = 3))
            sb.append(cn(row, 4, room.area(), style = 4))
            sb.append(cn(row, 5, room.perimeter(), style = 4))
            sb.append("</row>"); row++

            // Walls
            for ((i, wall) in room.walls.withIndex()) {
                val label = wall.label.ifBlank { "Paret ${i + 1}" }
                sb.append("""<row r="$row">""")
                sb.append(cs(row, 1, si(room.name)))
                sb.append(cs(row, 2, si(label)))
                sb.append(cn(row, 3, wall.length))
                sb.append("</row>"); row++
            }
        }

        // Total if multiple rooms
        if (project.rooms.size > 1) {
            sb.append("""<row r="$row">""")
            sb.append(cs(row, 1, si("TOTAL"), style = 1))
            sb.append(cs(row, 2, si(""), style = 1))
            sb.append(cn(row, 4, project.totalArea(), style = 4))
            sb.append("</row>"); row++
        }

        sb.append("""</sheetData><autoFilter ref="A1:E1"/>""")
        sb.append("""<colPr><col min="1" max="1" width="18" customWidth="1"/>""")
        sb.append("""<col min="2" max="2" width="16" customWidth="1"/>""")
        sb.append("""<col min="3" max="5" width="14" customWidth="1"/></colPr>""")
        sb.append("</worksheet>")
        return sb.toString()
    }

    private fun col(c: Int) = arrayOf("", "A", "B", "C", "D", "E", "F")[c]

    // ── Static XML ────────────────────────────────────────────────────────

    private fun contentTypes() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
  <Override PartName="/xl/sharedStrings.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sharedStrings+xml"/>
  <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
</Types>"""

    private fun rels() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""

    private fun wbRels() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/sharedStrings" Target="sharedStrings.xml"/>
  <Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>"""

    private fun workbook() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"
  xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <sheets><sheet name="Superfícies" sheetId="1" r:id="rId1"/></sheets>
</workbook>"""

    private fun styles() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <fonts>
    <font><sz val="11"/><name val="Calibri"/></font>
    <font><sz val="11"/><b/><color rgb="FFFFFFFF"/><name val="Calibri"/></font>
    <font><sz val="11"/><b/><i/><name val="Calibri"/></font>
    <font><sz val="11"/><b/><name val="Calibri"/></font>
  </fonts>
  <fills>
    <fill><patternFill patternType="none"/></fill>
    <fill><patternFill patternType="gray125"/></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FF1565C0"/></patternFill></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FFE3F2FD"/></patternFill></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FFFFF9C4"/></patternFill></fill>
  </fills>
  <borders><border><left/><right/><top/><bottom/><diagonal/></border></borders>
  <cellStyleXfs><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
  <cellXfs>
    <xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
    <xf numFmtId="0" fontId="1" fillId="2" borderId="0" xfId="0"><alignment horizontal="center"/></xf>
    <xf numFmtId="2" fontId="0" fillId="0" borderId="0" xfId="0"/>
    <xf numFmtId="0" fontId="2" fillId="3" borderId="0" xfId="0"/>
    <xf numFmtId="2" fontId="3" fillId="4" borderId="0" xfId="0"/>
  </cellXfs>
</styleSheet>"""

    private fun sharedStrings(ss: List<String>): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("""<sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" count="${ss.size}" uniqueCount="${ss.size}">""")
        for (s in ss) sb.append("<si><t>${s.esc()}</t></si>")
        sb.append("</sst>")
        return sb.toString()
    }

    private fun String.esc() = replace("&","&amp;").replace("<","&lt;").replace(">","&gt;")

    private fun ZipOutputStream.put(name: String, content: String) {
        putNextEntry(ZipEntry(name))
        write(content.toByteArray(Charsets.UTF_8))
        closeEntry()
    }
}
