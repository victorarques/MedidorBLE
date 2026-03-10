package com.medidorble.ui

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.medidorble.ble.BleManager
import com.medidorble.data.*
import com.medidorble.databinding.ActivityRoomEditorBinding
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
    private var wallCnt = 1
    private var roomCnt = 1

    private val foundDevices = LinkedHashMap<String, String>() // name → address

    private var onPermsGranted: (() -> Unit)? = null
    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) onPermsGranted?.invoke()
        else toast("Permisos BLE necessaris")
    }

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        b = ActivityRoomEditorBinding.inflate(layoutInflater)
        setContentView(b.root)

        val idx = intent.getIntExtra("PROJECT_IDX", -1)
        // Correcció: Accés segur al repositori
        project = if (idx >= 0 && idx < ProjectRepository.projects.size) {
            ProjectRepository.projects[idx]
        } else {
            Project(name = "Projecte")
        }
        title = project.name

        val bluetoothManager = getSystemService(BluetoothManager::class.java)
        btAdapter = bluetoothManager?.adapter
        
        setupBle()
        setupUi()
    }

    override fun onDestroy() {
        super.onDestroy()
        ble.disconnect()
    }

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
                b.tvBleStatus.setTextColor(ContextCompat.getColor(this@RoomEditorActivity, android.R.color.holo_green_dark))
                b.btnScan.text = "Desconnectar"
            }
            override fun onDisconnected() = runOnUiThread {
                b.tvBleStatus.text = "Desconnectat"
                b.tvBleStatus.setTextColor(ContextCompat.getColor(this@RoomEditorActivity, android.R.color.holo_red_dark))
                b.btnScan.text = "Cercar Làser BLE"
            }
            override fun onMeasurement(meters: Double) = runOnUiThread { receiveMeasure(meters) }
            override fun onError(msg: String) = runOnUiThread { toast(msg) }
        })
    }

    private fun showDeviceChooser() {
        if (foundDevices.isEmpty()) return
        val names = foundDevices.keys.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Dispositius BLE trobats")
            .setItems(names) { _, i ->
                ble.stopScan()
                btAdapter?.let { ble.connect(foundDevices[names[i]]!!, it) }
            }.show()
    }

    private fun setupUi() {
        b.btnScan.setOnClickListener { onScanClick() }
        b.btnNewRoom.setOnClickListener { newRoomDialog() }
        b.btnManualInput.setOnClickListener { manualInputDialog() }
        b.btnAddWall.setOnClickListener { addWall() }
        b.btnUndo.setOnClickListener { undoWall() }
        b.btnCloseRoom.setOnClickListener { closeRoom() }
        // b.btnExportDxf.setOnClickListener { exportDxf() } // Implementar si DxfExporter existeix
        // b.btnExportXlsx.setOnClickListener { exportXlsx() }

        b.sliderAngle.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                turnAngle = (progress * 5).toDouble()
                b.tvAngleValue.text = "$turnAngle°"
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
        b.sliderAngle.progress = 18 // 18 * 5 = 90 graus per defecte
        refreshUi()
    }

    private fun onScanClick() {
        val adapter = btAdapter ?: run { toast("Bluetooth no disponible"); return }
        if (!adapter.isEnabled) { toast("Activa el Bluetooth"); return }
        if (b.btnScan.text == "Desconnectar") { 
            ble.disconnect() 
            return 
        }
        requestBle {
            foundDevices.clear()
            b.tvBleStatus.text = "Cercant…"
            ble.startScan(adapter)
        }
    }

    private fun newRoomDialog() {
        val et = EditText(this).apply { 
            hint = "Nom de l'habitació"
            setPadding(60, 40, 60, 40)
        }
        AlertDialog.Builder(this)
            .setTitle("Nova habitació")
            .setView(et)
            .setPositiveButton("Crear") { _, _ ->
                val name = et.text.toString().trim().ifBlank { "Habitació $roomCnt" }
                roomCnt++
                wallCnt = 1
                val room = Room(project.rooms.size, name)
                project.rooms.add(room)
                currentRoom = room
                refreshUi()
                title = "${project.name} › $name"
            }
            .setNegativeButton("Cancel·lar", null)
            .show()
    }

    private fun manualInputDialog() {
        val et = EditText(this).apply {
            hint = "Longitud en metres (ex: 3.45)"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setPadding(60, 40, 60, 40)
        }
        AlertDialog.Builder(this)
            .setTitle("Entrada manual")
            .setView(et)
            .setPositiveButton("Afegir") { _, _ ->
                et.text.toString().toDoubleOrNull()
                    ?.takeIf { it > 0.01 }
                    ?.let { receiveMeasure(it) }
                    ?: toast("Valor no vàlid")
            }
            .setNegativeButton("Cancel·lar", null)
            .show()
    }

    private fun receiveMeasure(m: Double) {
        pendingMeasure = m
        b.tvMeasurement.text = String.format("%.3f m", m)
        b.btnAddWall.isEnabled = currentRoom != null
        toast("Mesura rebuda: $m m")
    }

    private fun addWall() {
        val room = currentRoom ?: return
        val len = pendingMeasure ?: return
        val turn = if (room.walls.isEmpty()) 0.0 else turnAngle
        
        room.walls.add(Wall(room.walls.size, "P${wallCnt++}", len, turn))
        pendingMeasure = null
        b.tvMeasurement.text = "— esperant mesura —"
        
        if (room.isClosed()) toast("✓ Habitació tancada automàticament")
        refreshUi()
    }

    private fun undoWall() {
        val walls = currentRoom?.walls
        if (walls != null && walls.isNotEmpty()) {
            walls.removeAt(walls.size - 1)
            if (wallCnt > 1) wallCnt--
            refreshUi()
        }
    }

    private fun closeRoom() {
        val room = currentRoom ?: run { toast("Primer crea una habitació"); return }
        if (room.walls.size < 3) { 
            toast("Necessites almenys 3 parets")
            return 
        }
        
        AlertDialog.Builder(this)
            .setTitle("Tancar \"${room.name}\"")
            .setMessage("""
                Superfície: ${String.format("%.3f", room.area())} m²
                Perímetre: ${String.format("%.3f", room.perimeter())} m
            """.trimIndent())
            .setPositiveButton("Confirmar") { _, _ ->
                currentRoom = null
                title = project.name
                refreshUi()
            }
            .setNegativeButton("Seguir mesurant", null)
            .show()
    }

    private fun refreshUi() {
        b.floorPlanView.setProject(project, currentRoom)
        val room = currentRoom
        
        b.tvWallList.text = if (room == null) {
            project.rooms.joinToString("\n") { r ->
                "${r.name}: ${String.format("%.3f", r.area())} m²"
            }.ifEmpty { "Cap habitació creada" }
        } else {
            room.walls.joinToString("\n") { w ->
                "${w.label}: ${w.length} m (${w.turnAngle.toInt()}°)"
            } + "\n\nS = ${String.format("%.3f", room.area())} m²"
        }

        val hasRoom = room != null
        val hasWalls = room?.walls?.isNotEmpty() == true
        val canExport = project.rooms.any { it.walls.size >= 2 }

        b.btnAddWall.isEnabled = hasRoom && pendingMeasure != null
        b.btnUndo.isEnabled = hasWalls
        b.btnCloseRoom.isEnabled = hasWalls
        b.btnExportDxf.isEnabled = canExport
        b.btnExportXlsx.isEnabled = canExport
    }

    private fun requestBle(block: () -> Unit) {
        val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        
        val needed = perms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (needed.isEmpty()) {
            block()
        } else {
            onPermsGranted = block
            permLauncher.launch(needed.toTypedArray())
        }
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}
