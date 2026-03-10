package com.medidorble.ble

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.os.Handler
import android.os.Looper
import java.util.UUID

/**
 * BLE manager for laser distance meters.
 *
 * Compatible amb:
 *  • Popoman LMBT60  (chip HM-10 → servei FFE0, char FFE1)
 *  • Bosch GLM BLE, Leica DISTO BLE (mateixa família UUID)
 *  • Genèrics via notificació FFE1 o Nordic UART
 */
@SuppressLint("MissingPermission")
class BleManager(private val context: Context) {

    interface Listener {
        fun onDeviceFound(name: String, address: String)
        fun onConnected(name: String)
        fun onDisconnected()
        fun onMeasurement(meters: Double)
        fun onError(message: String)
    }

    companion object {
        val SVC_FFE0: UUID  = UUID.fromString("0000FFE0-0000-1000-8000-00805F9B34FB")
        val CHAR_FFE1: UUID = UUID.fromString("0000FFE1-0000-1000-8000-00805F9B34FB")
        val SVC_UART: UUID  = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")
        val CHAR_UART_RX: UUID = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E")
        val DESC_NOTIF: UUID   = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")
        private const val SCAN_MS = 12_000L
    }

    private var listener: Listener? = null
    private var scanner: BluetoothLeScanner? = null
    private var gatt: BluetoothGatt? = null
    private val handler = Handler(Looper.getMainLooper())
    private var scanning = false

    fun setListener(l: Listener) { listener = l }

    // ── Scan ──────────────────────────────────────────────────────────────

    private val scanCb = object : ScanCallback() {
        override fun onScanResult(type: Int, result: ScanResult) {
            val name = result.device.name?.takeIf { it.isNotBlank() } ?: return
            handler.post { listener?.onDeviceFound(name, result.device.address) }
        }
        override fun onScanFailed(err: Int) {
            handler.post { listener?.onError("Error de scan BLE: $err") }
        }
    }

    fun startScan(adapter: BluetoothAdapter) {
        if (scanning) return
        scanner = adapter.bluetoothLeScanner
        scanning = true
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        // Scan sense filtre → compatibilitat màxima
        scanner?.startScan(emptyList(), settings, scanCb)
        handler.postDelayed({ stopScan() }, SCAN_MS)
    }

    fun stopScan() {
        if (!scanning) return
        scanning = false
        scanner?.stopScan(scanCb)
    }

    // ── Connect ───────────────────────────────────────────────────────────

    fun connect(address: String, adapter: BluetoothAdapter) {
        val device = adapter.getRemoteDevice(address)
        gatt = device.connectGatt(context, false, gattCb, BluetoothDevice.TRANSPORT_LE)
    }

    fun disconnect() {
        gatt?.disconnect(); gatt?.close(); gatt = null
    }

    // ── GATT callback ─────────────────────────────────────────────────────

    private val gattCb = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, state: Int) {
            handler.post {
                when (state) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        listener?.onConnected(g.device.name ?: g.device.address)
                        g.discoverServices()
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> listener?.onDisconnected()
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                handler.post { listener?.onError("GATT discovery failed ($status)") }
                return
            }
            val char = g.getService(SVC_FFE0)?.getCharacteristic(CHAR_FFE1)
                ?: g.getService(SVC_UART)?.getCharacteristic(CHAR_UART_RX)
            if (char == null) {
                handler.post { listener?.onError("Servei BLE no compatible. Usa entrada manual.") }
                return
            }
            enableNotify(g, char)
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(g: BluetoothGatt, ch: BluetoothGattCharacteristic) {
            parseDistance(ch.value)?.let { m ->
                handler.post { listener?.onMeasurement(m) }
            }
        }
    }

    private fun enableNotify(g: BluetoothGatt, ch: BluetoothGattCharacteristic) {
        g.setCharacteristicNotification(ch, true)
        ch.getDescriptor(DESC_NOTIF)?.let { d ->
            @Suppress("DEPRECATION")
            d.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            @Suppress("DEPRECATION")
            g.writeDescriptor(d)
        }
    }

    // ── Protocol parser ───────────────────────────────────────────────────
    /**
     * Intenta múltiples formats del protocol BLE dels làsers barats:
     *
     * 1. ASCII decimal: "1.234", "1234mm", "123.4cm" ...
     * 2. Binary 6 bytes (Popoman/Uni-T): bytes[4..5] = mm (big-endian int16)
     * 3. Binary 4 bytes little-endian: int32 = mm
     * 4. Binary 2 bytes big-endian: int16 = mm
     */
    private fun parseDistance(data: ByteArray?): Double? {
        if (data.isNullOrEmpty()) return null

        // 1) ASCII
        runCatching {
            val s = String(data, Charsets.UTF_8).trim()
            val m = Regex("([0-9]+\.?[0-9]*)\s*(mm|cm|m)?").find(s)
            if (m != null) {
                val v = m.groupValues[1].toDouble()
                val unit = m.groupValues[2].lowercase()
                val metres = when {
                    unit == "mm"  -> v / 1000.0
                    unit == "cm"  -> v / 100.0
                    v > 100       -> v / 1000.0   // sense unitat, probablement mm
                    else          -> v
                }
                if (metres in 0.01..100.0) return metres
            }
        }

        // 2) 6-byte binary (Popoman LMBT60 típic)
        if (data.size >= 6) {
            val mm = ((data[4].toInt() and 0xFF) shl 8) or (data[5].toInt() and 0xFF)
            if (mm in 10..50000) return mm / 1000.0
            val mm2 = ((data[2].toInt() and 0xFF) shl 8) or (data[3].toInt() and 0xFF)
            if (mm2 in 10..50000) return mm2 / 1000.0
        }

        // 3) 4-byte little-endian int32 mm
        if (data.size >= 4) {
            val mm = (data[0].toInt() and 0xFF) or
                     ((data[1].toInt() and 0xFF) shl 8) or
                     ((data[2].toInt() and 0xFF) shl 16) or
                     ((data[3].toInt() and 0xFF) shl 24)
            if (mm in 10..50000) return mm / 1000.0
        }

        // 4) 2-byte big-endian int16 mm
        if (data.size >= 2) {
            val mm = ((data[0].toInt() and 0xFF) shl 8) or (data[1].toInt() and 0xFF)
            if (mm in 10..50000) return mm / 1000.0
        }

        return null
    }
}
