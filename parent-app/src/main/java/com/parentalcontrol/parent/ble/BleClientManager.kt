package com.parentalcontrol.parent.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import com.parentalcontrol.parent.ParentApp
import java.util.UUID

class BleClientManager(
    private val context: Context,
    private val onConnectionChanged: (isConnected: Boolean, statusText: String) -> Unit
) {
    private val tag = "BleClientManager"
    private val mainHandler = Handler(Looper.getMainLooper())

    private val bluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter
    private var bluetoothGatt: BluetoothGatt? = null
    private var commandCharacteristic: BluetoothGattCharacteristic? = null

    private var isScanning = false

    val isBluetoothEnabled: Boolean
        get() = bluetoothAdapter != null && bluetoothAdapter.isEnabled

    val isConnected: Boolean
        get() = bluetoothGatt != null && commandCharacteristic != null

    @SuppressLint("MissingPermission")
    fun startScanAndConnect() {
        if (!isBluetoothEnabled) {
            onConnectionChanged(false, "Bluetooth belum aktif di HP Anda")
            return
        }

        if (isScanning) return
        isScanning = true
        onConnectionChanged(false, "Sedang mencari tablet Leon via Bluetooth...")

        val scanner = bluetoothAdapter?.bluetoothLeScanner
        val serviceUuid = UUID.fromString(ParentApp.BLE_SERVICE_UUID)

        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(serviceUuid))
            .build()

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        mainHandler.postDelayed({
            if (isScanning) {
                stopScan()
                if (!isConnected) {
                    onConnectionChanged(false, "Tablet Leon tidak ditemukan di sekitar")
                }
            }
        }, 12000L) // Timeout scan 12 detik

        scanner?.startScan(listOf(filter), settings, scanCallback)
    }

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            super.onScanResult(callbackType, result)
            val device = result?.device ?: return
            Log.d(tag, "Menemukan perangkat BLE: ${device.address}")
            stopScan()
            connectToDevice(device)
        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            isScanning = false
            onConnectionChanged(false, "Gagal scan Bluetooth: error $errorCode")
        }
    }

    @SuppressLint("MissingPermission")
    private fun stopScan() {
        if (isScanning) {
            isScanning = false
            bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
        }
    }

    @SuppressLint("MissingPermission")
    private fun connectToDevice(device: BluetoothDevice) {
        onConnectionChanged(false, "Menghubungkan ke tablet Leon...")
        bluetoothGatt = device.connectGatt(context, false, gattCallback)
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            super.onConnectionStateChange(gatt, status, newState)
            if (newState == BluetoothGatt.STATE_CONNECTED) {
                Log.i(tag, "Terhubung ke GATT tablet Leon, mencari service...")
                mainHandler.post { onConnectionChanged(false, "Mencari layanan kontrol...") }
                gatt?.discoverServices()
            } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                Log.w(tag, "Terputus dari Bluetooth tablet Leon")
                bluetoothGatt?.close()
                bluetoothGatt = null
                commandCharacteristic = null
                mainHandler.post { onConnectionChanged(false, "Terputus dari Bluetooth tablet") }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            super.onServicesDiscovered(gatt, status)
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val serviceUuid = UUID.fromString(ParentApp.BLE_SERVICE_UUID)
                val charUuid = UUID.fromString(ParentApp.BLE_COMMAND_CHAR_UUID)

                val service = gatt?.getService(serviceUuid)
                commandCharacteristic = service?.getCharacteristic(charUuid)

                if (commandCharacteristic != null) {
                    Log.i(tag, "Berhasil mendapatkan characteristic kontrol BLE!")
                    mainHandler.post { onConnectionChanged(true, "Terhubung via Bluetooth ✓") }
                } else {
                    mainHandler.post { onConnectionChanged(false, "Layanan kontrol tidak sesuai") }
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun sendCommand(cmdCode: Int, param: Int = 0): Boolean {
        val gatt = bluetoothGatt
        val char = commandCharacteristic
        if (gatt == null || char == null) {
            Log.e(tag, "Tidak bisa mengirim perintah: Bluetooth belum terhubung")
            return false
        }

        val payload = byteArrayOf(cmdCode.toByte(), param.toByte())

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val result = gatt.writeCharacteristic(
                char,
                payload,
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            )
            return result == BluetoothGatt.GATT_SUCCESS
        } else {
            @Suppress("DEPRECATION")
            char.value = payload
            @Suppress("DEPRECATION")
            return gatt.writeCharacteristic(char)
        }
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        stopScan()
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
        commandCharacteristic = null
        onConnectionChanged(false, "Bluetooth Terputus")
    }

    companion object {
        const val CMD_LOCK_NOW = 1
        const val CMD_SHOW_OVERLAY = 2
        const val CMD_HIDE_OVERLAY = 3
        const val CMD_SET_VOLUME = 4
        const val CMD_SET_BRIGHTNESS = 5
        const val CMD_ENABLE_WIFI = 6
    }
}
