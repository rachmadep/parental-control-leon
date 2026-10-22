package com.parentalcontrol.child.manager

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import com.parentalcontrol.child.ChildApp
import java.util.UUID

class BleServerManager(
    private val context: Context,
    private val onCommandReceived: (commandType: Int, param: Int) -> Unit
) {
    private val tag = "BleServerManager"

    private val bluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter
    private var gattServer: BluetoothGattServer? = null
    private var advertiser: BluetoothLeAdvertiser? = null

    val isBluetoothAvailable: Boolean
        get() = bluetoothAdapter != null && bluetoothAdapter.isEnabled

    @SuppressLint("MissingPermission")
    fun startServer() {
        if (!isBluetoothAvailable) {
            Log.w(tag, "Bluetooth tidak tersedia atau tidak menyala")
            return
        }

        stopServer()

        val serviceUuid = UUID.fromString(ChildApp.BLE_SERVICE_UUID)
        val charUuid = UUID.fromString(ChildApp.BLE_COMMAND_CHAR_UUID)

        // Setup GATT Server Callback
        val gattCallback = object : BluetoothGattServerCallback() {
            override fun onCharacteristicWriteRequest(
                device: BluetoothDevice?,
                requestId: Int,
                characteristic: BluetoothGattCharacteristic?,
                preparedWrite: Boolean,
                responseNeeded: Boolean,
                offset: Int,
                value: ByteArray?
            ) {
                super.onCharacteristicWriteRequest(
                    device, requestId, characteristic, preparedWrite, responseNeeded, offset, value
                )

                if (characteristic?.uuid == charUuid && value != null && value.isNotEmpty()) {
                    val cmdCode = value[0].toInt()
                    val param = if (value.size > 1) value[1].toInt() else 0
                    Log.d(tag, "Menerima perintah BLE: cmd=$cmdCode, param=$param")
                    onCommandReceived(cmdCode, param)
                }

                if (responseNeeded) {
                    gattServer?.sendResponse(
                        device,
                        requestId,
                        BluetoothGatt.GATT_SUCCESS,
                        offset,
                        byteArrayOf(1)
                    )
                }
            }
        }

        gattServer = bluetoothManager?.openGattServer(context, gattCallback)

        // Buat GATT Service & Characteristic
        val service = BluetoothGattService(
            serviceUuid,
            BluetoothGattService.SERVICE_TYPE_PRIMARY
        )
        val commandChar = BluetoothGattCharacteristic(
            charUuid,
            BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_WRITE or BluetoothGattCharacteristic.PERMISSION_READ
        )
        service.addCharacteristic(commandChar)
        gattServer?.addService(service)

        // Mulai BLE Advertising agar HP Orang Tua bisa mendeteksi tablet Leon
        advertiser = bluetoothAdapter?.bluetoothLeAdvertiser
        if (advertiser != null) {
            val settings = AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
                .setConnectable(true)
                .setTimeout(0)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
                .build()

            val data = AdvertiseData.Builder()
                .setIncludeDeviceName(false)
                .addServiceUuid(ParcelUuid(serviceUuid))
                .build()

            advertiser?.startAdvertising(settings, data, advertiseCallback)
            Log.i(tag, "BLE GATT Server & Advertiser berhasil dimulai")
        }
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            super.onStartSuccess(settingsInEffect)
            Log.i(tag, "BLE Advertising berhasil berjalan")
        }

        override fun onStartFailure(errorCode: Int) {
            super.onStartFailure(errorCode)
            Log.e(tag, "Gagal memulai BLE Advertising: error code $errorCode")
        }
    }

    @SuppressLint("MissingPermission")
    fun stopServer() {
        try {
            advertiser?.stopAdvertising(advertiseCallback)
            gattServer?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        gattServer = null
        advertiser = null
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
