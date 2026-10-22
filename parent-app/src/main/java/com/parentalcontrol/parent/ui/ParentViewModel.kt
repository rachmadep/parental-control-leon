package com.parentalcontrol.parent.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.parentalcontrol.parent.ParentApp
import com.parentalcontrol.parent.R
import com.parentalcontrol.parent.ble.BleClientManager
import com.parentalcontrol.parent.firebase.FirebaseSyncManager
import com.parentalcontrol.parent.model.DayHistory
import com.parentalcontrol.parent.model.DeviceSummary
import com.parentalcontrol.parent.model.LimitsConfig
import kotlinx.coroutines.flow.MutableSharedFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class DeviceState(val summary: DeviceSummary = DeviceSummary(), val isOnline: Boolean = false)

/**
 * Satu-satunya pemilik koneksi Firebase dan BLE untuk semua tab, supaya tab berganti atau layar
 * berputar tidak memutus koneksi.
 */
class ParentViewModel(application: Application) : AndroidViewModel(application) {

    private val _device = MutableLiveData(DeviceState())
    val device: LiveData<DeviceState> = _device

    private val _history = MutableLiveData<List<DayHistory>>(emptyList())
    val history: LiveData<List<DayHistory>> = _history

    private val _config = MutableLiveData(LimitsConfig())
    val config: LiveData<LimitsConfig> = _config

    private val _bleConnected = MutableLiveData(false)
    val bleConnected: LiveData<Boolean> = _bleConnected

    private val _bleStatus = MutableLiveData(application.getString(R.string.btn_connect_ble))
    val bleStatus: LiveData<String> = _bleStatus

    /** Pesan singkat untuk Toast; dikumpulkan oleh MainActivity. */
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 8)

    private val firebase = FirebaseSyncManager(
        deviceId = ParentApp.TARGET_DEVICE_ID,
        onDeviceUpdated = { summary, isOnline -> _device.postValue(DeviceState(summary, isOnline)) },
        onHistoryUpdated = { _history.postValue(it) },
        onConfigUpdated = { _config.postValue(it) }
    )

    private val ble = BleClientManager(application) { isConnected, statusText ->
        _bleConnected.postValue(isConnected)
        _bleStatus.postValue(statusText)
        if (isConnected) messages.tryEmit("Berhasil terhubung ke tablet Leon via Bluetooth!")
    }

    init {
        firebase.startListening()
    }

    /**
     * Mengirim perintah: Prioritas via Bluetooth jika sedang terhubung BLE,
     * jika tidak, kirim lewat Firebase Online.
     */
    fun dispatchCommand(action: String, param: Int = 0, bleCode: Int) {
        if (_bleConnected.value == true) {
            ble.sendCommand(bleCode, param)
        } else {
            firebase.sendCommandOnline(action, param) { success, errorMsg ->
                if (!success) messages.tryEmit("Gagal kirim via Cloud: $errorMsg (Cek Rules Firebase!)")
            }
        }
    }

    val isBluetoothEnabled: Boolean get() = ble.isBluetoothEnabled

    fun startBleScan() = ble.startScanAndConnect()

    fun disconnectBle() = ble.disconnect()

    fun saveConfig(newConfig: LimitsConfig) {
        _config.value = newConfig // optimistis; listener Firebase menimpa dengan nilai tersimpan
        firebase.saveConfig(newConfig) { success, errorMsg ->
            if (!success) messages.tryEmit("Gagal menyimpan pengaturan: $errorMsg")
        }
    }

    /** Tambah waktu layar untuk hari ini saja (akumulatif jika sudah ada bonus hari ini). */
    fun grantBonus(minutes: Int) {
        val current = _config.value ?: LimitsConfig()
        val today = todayKey()
        val newBonus = (if (current.bonusDate == today) current.bonusMinutes else 0) + minutes
        saveConfig(current.copy(bonusDate = today, bonusMinutes = newBonus))
        messages.tryEmit("Ditambah $minutes menit untuk hari ini")
    }

    override fun onCleared() {
        firebase.stopListening()
        ble.disconnect()
    }

    companion object {
        fun todayKey(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    }
}
