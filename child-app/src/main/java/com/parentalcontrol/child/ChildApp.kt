package com.parentalcontrol.child

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class ChildApp : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Layanan Proteksi Parental Control",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notifikasi sistem untuk menjaga proteksi tetap aktif di tablet Leon"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID = "parental_control_protection_channel"
        const val BLE_SERVICE_UUID = "0000fff0-0000-1000-8000-00805f9b34fb"
        const val BLE_COMMAND_CHAR_UUID = "0000fff1-0000-1000-8000-00805f9b34fb"
        const val PREFS_NAME = "parental_child_prefs"
        const val KEY_PARENT_PIN = "parent_pin"
        const val DEFAULT_PIN = "1234"
    }
}
