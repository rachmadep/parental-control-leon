package com.parentalcontrol.child.service

import android.content.Intent
import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.parentalcontrol.child.manager.DeviceControlManager

class ChildFcmService : FirebaseMessagingService() {

    private val tag = "ChildFcmService"

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.i(tag, "FCM Token baru tablet Leon: $token")
        
        // Langsung update ke Firebase agar tidak perlu startService yang bisa crash jika izin belum lengkap
        val deviceId = "leon_tablet" // Hardcode agar sinkron dengan Parent App
        com.google.firebase.database.FirebaseDatabase.getInstance()
            .getReference("devices").child(deviceId).child("fcmToken").setValue(token)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        val data = remoteMessage.data
        if (data.isEmpty()) return

        Log.d(tag, "Menerima pesan FCM: $data")
        val action = data["action"] ?: return
        val value = data["value"]?.toIntOrNull() ?: 0

        val deviceManager = DeviceControlManager(this)

        when (action) {
            "LOCK_NOW" -> {
                deviceManager.lockScreenInstantly()
            }
            "SHOW_OVERLAY" -> {
                deviceManager.showLockOverlay()
            }
            "HIDE_OVERLAY" -> {
                deviceManager.hideLockOverlay()
            }
            "SET_VOLUME" -> {
                deviceManager.setVolumePercent(value)
            }
            "SET_BRIGHTNESS" -> {
                deviceManager.setBrightnessPercent(value)
            }
            "SET_WIFI" -> {
                deviceManager.setWifiEnabled(value == 1)
            }
            "SYNC_STATS" -> {
                try {
                    val intent = Intent(this, ChildMonitoringService::class.java).apply {
                        this.action = ChildMonitoringService.ACTION_SYNC_STATS_NOW
                    }
                    startService(intent)
                } catch (e: Exception) {
                    Log.e(tag, "Gagal start service SYNC_STATS", e)
                }
            }
        }
    }
}
