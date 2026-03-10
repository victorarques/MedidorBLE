package com.medidorble.ui

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.medidorble.ble.BleManager
import com.medidorble.data.*
import com.medidorble.databinding.ActivityRoomEditorBinding
import com.medidorble.export.DxfExporter
import com.medidorble.export.ExcelExporter
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class RoomEditorActivity : AppCompatActivity() {

    private lateinit var b: ActivityRoomEditorBinding
    private lateinit var ble: BleManager
    private var btAdapter: BluetoothAdapter? = null

    private lateinit var project: Project
    private var currentRoom: Room? = null
    private var pendingMeasure: Double? = null
    private var turnAngle = 90.0
    private var wallCnt = 1; private var roomCnt = 1

    private val foundDevices = LinkedHashMap<String, String>()   // name → address

    // ── Permissions launcher (class-level) ───────────────────────────────
    private var onPermsGranted: (() -> Unit)? = null
    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) onPermsGranted?.invoke()
        else toast("Permisos BLE necessaris")
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        b = ActivityRoomEditorBinding.inflate(layoutInflater)
        setContentView(b.root)

        val idx = intent.getIntExtra("PROJECT_IDX", -1)
        project = if (idx >= 0) ProjectRepository.projects[idx]
                  else Project(name = "Projecte")
        title = project.name

        btAdapter = (getSystemService(BluetoothManager::class.java)).adapter
        setupBle()
        setupUi()
    }

    override fun onDestroy() { super.onDestroy(); ble.disconnect() }

    // ── BLE ───────────────────────────────────────────────────────────────

    private fun setupBle() {
        ble = BleManager(this)
        ble.setListener(object : BleManager.Listener {
            override fun onDeviceFound(name: String, address: String) {
                if (!foundDevices.containsKey(name)) {
                    foundDevices[name] = address
                    runOnUiThread { showDeviceChooser() }
                }
            }
            override fun onConnected(name: String) = runOnUiThread {
                b.tvBleStatus.text = "✓ $name"
                b.tvBleStatus.setTextColor(getColor(com.medidorble.R.color.green))
                b.btnScan.text = "Desconnectar"
            }
            override fun onDisconnected() = runOnUiThread {
                b.tvBleStatus.text = "Desconnectat"
                b.tvBleStatus.setTextColor(getColor(com.medidorble.R.color.red))
                b.btnScan.text = "Cercar Làser BLE"
            }
            override fun onMeasurement(meters: Double) = runOnUiThread { receiveMeasure(meters) }
            override fun onError(msg: String) = runOnUiThread { toast(msg) }
        })
    }

    private fun showDeviceChooser() {
        if (foundDevices.isEmpty()) return
        val names = foundDevices.keys.toTypedArray()
        AlertDialog.Builder(this).setTitle("Dispositius BLE trobats")
            .setItems(names) { _, i ->
                ble.stopScan()
                ble.connect(foundDevices[names[i]]!!, btAdapter!!)
            }.show()
    }

    // ── UI setup ──────────────────────────────────────────────────────────

    private fun setupUi() {
        b.btnScan.setOnClickListener { onScanClick() }
        b.btnNewRoom.setOnClickListener { newRoomDialog() }
        b.btnManualInput.setOnClickListener { manualInputDialog() }
        b.btnAddWall.setOnClickListener { addWall() }
        b.btnUndo.setOnClickListener { undoWall() }
        b.btnCloseRoom.setOnClickListener { closeRoom() }
        b.btnExportDxf.setOnClickListener { exportDxf() }
        b.btnExportXlsx.setOnClickListener { exportXlsx() }

        b.sliderAngle.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
    override fun onProgressChanged(sb: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
        turnAngle = (progress * 5).toDouble()
        b.tvAngleValue.text = "${progress * 5}°"
    }
    override fun onStartTrackingTouch(sb: android.widget.SeekBar?) {}
    override fun onStopTrackingTouch(sb: android.widget.SeekBar?) {}
})
        b.sliderAngle.value = 90f
        refreshUi()
    }

    private fun onScanClick() {
        val a = btAdapter ?: run { toast("Bluetooth no disponible"); return }
        if (!a.isEnabled) { toast("Activa el Bluetooth"); return }
        if (b.btnScan.text == "Desconnectar") { ble.disconnect(); return }
        requestBle {
            foundDevices.clear()
            b.tvBleStatus.text = "Cercant…"
            ble.startScan(a)
        }
    }

    // ── Room / wall logic ─────────────────────────────────────────────────

    private fun newRoomDialog() {
        val et = EditText(this).apply { hint = "Nom de l'habitació"; setPadding(48,16,48,16) }
        AlertDialog.Builder(this).setTitle("Nova habitació").setView(et)
            .setPositiveButton("Crear") { _, _ ->
                val name = et.text.toString().trim().ifBlank { "Habitació $roomCnt" }
                roomCnt++; wallCnt = 1
                val room = Room(project.rooms.size, name)
                project.rooms.add(room)
                currentRoom = room
                refreshUi(); title = "${project.name} › $name"
            }.setNegativeButton("Cancel·lar", null).show()
    }

    private fun manualInputDialog() {
        val et = EditText(this).apply {
            hint = "Longitud en metres (ex: 3.45)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                        android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            setPadding(48, 16, 48, 16)
        }
        AlertDialog.Builder(this).setTitle("Entrada manual").setView(et)
            .setPositiveButton("Afegir") { _, _ ->
                et.text.toString().toDoubleOrNull()
                    ?.takeIf { it > 0.01 }
                    ?.let { receiveMeasure(it) }
                    ?: toast("Valor no vàlid")
            }.setNegativeButton("Cancel·lar", null).show()
    }

    private fun receiveMeasure(m: Double) {
        pendingMeasure = m
        b.tvMeasurement.text = "${"%.3f".format(m)} m"
        b.btnAddWall.isEnabled = currentRoom != null
        toast("Mesura: ${"%.3f".format(m)} m")
    }

    private fun addWall() {
        val room = currentRoom ?: return
        val len  = pendingMeasure ?: return
        val turn = if (room.walls.isEmpty()) 0.0 else turnAngle
        room.walls.add(Wall(room.walls.size, "P${wallCnt++}", len, turn))
        pendingMeasure = null
        b.tvMeasurement.text = "— esperant mesura —"
        if (room.isClosed()) toast("✓ Habitació tancada automàticament")
        refreshUi()
    }

    private fun undoWall() {
        currentRoom?.walls?.removeLastOrNull()
        if (wallCnt > 1) wallCnt--
        refreshUi()
    }

    private fun closeRoom() {
        val room = currentRoom ?: run { toast("Primer crea una habitació"); return }
        if (room.walls.size < 3) { toast("Necessites almenys 3 parets"); return }
        AlertDialog.Builder(this)
            .setTitle("Tancar "${room.name}"")
            .setMessage("Superfície: ${"%.3f".format(room.area())} m²
Perímetre: ${"%.3f".format(room.perimeter())} m")
            .setPositiveButton("Confirmar") { _, _ ->
                currentRoom = null; title = project.name; refreshUi()
            }.setNegativeButton("Seguir mesurant", null).show()
    }

    // ── Refresh ───────────────────────────────────────────────────────────

    private fun refreshUi() {
        b.floorPlanView.setProject(project, currentRoom)
        val room = currentRoom
        b.tvWallList.text = if (room == null) {
            project.rooms.joinToString("
") { r ->
                "${r.name}: ${"%.3f".format(r.area())} m²"
            }.ifEmpty { "Cap habitació" }
        } else {
            room.walls.joinToString("
") { w ->
                "${w.label}: ${"%.3f".format(w.length)} m  (gir ${w.turnAngle.toInt()}°)"
            } + "
S = ${"%.3f".format(room.area())} m²"
        }
        val hasRoom   = room != null
        val hasWalls  = hasRoom && (room?.walls?.isNotEmpty() == true)
        val canExport = project.rooms.any { it.walls.size >= 2 }
        b.btnAddWall.isEnabled    = hasRoom && pendingMeasure != null
        b.btnUndo.isEnabled       = hasWalls
        b.btnCloseRoom.isEnabled  = hasWalls
        b.btnExportDxf.isEnabled  = canExport
        b.btnExportXlsx.isEnabled = canExport
    }

    // ── Export ────────────────────────────────────────────────────────────

    private fun exportDxf() = export("dxf") { f -> DxfExporter.export(project, f) }
    private fun exportXlsx() = export("xlsx") { f -> ExcelExporter.export(project, f) }

    private fun export(ext: String, block: (File) -> Unit) {
        val ts = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
        val file = File(getExternalFilesDir(null), "${project.name}_$ts.$ext")
        try {
            block(file)
            val uri: Uri = FileProvider.getUriForFile(this, "$packageName.provider", file)
            val mime = if (ext == "dxf") "application/dxf"
                       else "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            startActivity(Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = mime; putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }, "Exportar $ext"))
        } catch (e: Exception) {
            toast("Error exportant: ${e.message}")
        }
    }

    // ── Permissions ───────────────────────────────────────────────────────

    private fun requestBle(block: () -> Unit) {
        val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        val needed = perms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isEmpty()) { block(); return }
        onPermsGranted = block
        permLauncher.launch(needed.toTypedArray())
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
