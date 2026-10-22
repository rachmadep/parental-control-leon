package com.parentalcontrol.child.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.parentalcontrol.child.ChildApp
import com.parentalcontrol.child.R
import com.parentalcontrol.child.manager.AppUsageManager
import com.parentalcontrol.child.manager.BleServerManager
import com.parentalcontrol.child.manager.DeviceControlManager
import com.parentalcontrol.child.model.DeviceUsageSummary
import com.parentalcontrol.child.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import android.widget.Toast
import android.os.Handler
import android.os.Looper

class ChildMonitoringService : Service() {

    private val tag = "ChildMonitoringService"
    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    private lateinit var appUsageManager: AppUsageManager
    private lateinit var deviceControlManager: DeviceControlManager
    private var bleServerManager: BleServerManager? = null

    private lateinit var deviceId: String
    private val database = FirebaseDatabase.getInstance()
    private var commandListener: ValueEventListener? = null

    override fun onCreate() {
        super.onCreate()
        appUsageManager = AppUsageManager(this)
        deviceControlManager = DeviceControlManager(this)

        deviceId = "leon_tablet" // Hardcode agar identik dengan ParentApp

        try {
            startForeground(NOTIFICATION_ID, buildForegroundNotification())
        } catch (e: SecurityException) {
            Log.e(tag, "Gagal startForeground karena permission belum lengkap", e)
            stopSelf()
            return
        }
        
        try {
            initBleServer()
        } catch (e: Exception) {
            Log.e(tag, "Gagal init BLE server", e)
        }
        startPeriodicSync()
        listenForCommands()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SYNC_STATS_NOW -> {
                serviceScope.launch { syncUsageStatsToFirebase() }
            }
            ACTION_UPDATE_FCM_TOKEN -> {
                val token = intent.getStringExtra("token")
                if (token != null) {
                    database.getReference("devices").child(deviceId).child("fcmToken").setValue(token)
                }
            }
        }
        return START_STICKY
    }

    private fun initBleServer() {
        bleServerManager = BleServerManager(this) { cmdCode, param ->
            handleBleCommand(cmdCode, param)
        }
        bleServerManager?.startServer()
    }

    private fun handleBleCommand(cmdCode: Int, param: Int) {
        when (cmdCode) {
            BleServerManager.CMD_LOCK_NOW -> {
                deviceControlManager.lockScreenInstantly()
            }
            BleServerManager.CMD_SHOW_OVERLAY -> {
                deviceControlManager.showLockOverlay()
            }
            BleServerManager.CMD_HIDE_OVERLAY -> {
                deviceControlManager.hideLockOverlay()
            }
            BleServerManager.CMD_SET_VOLUME -> {
                deviceControlManager.setVolumePercent(param)
            }
            BleServerManager.CMD_SET_BRIGHTNESS -> {
                deviceControlManager.setBrightnessPercent(param)
            }
            BleServerManager.CMD_ENABLE_WIFI -> {
                deviceControlManager.setWifiEnabled(true)
            }
        }
        serviceScope.launch {
            delay(300)
            syncUsageStatsToFirebase()
        }
    }

    private fun listenForCommands() {
        val commandRef = database.getReference("devices").child(deviceId).child("pendingCommand")
        commandListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) return

                val action = snapshot.child("action").getValue(String::class.java) ?: return
                val value = snapshot.child("value").getValue(Int::class.java) ?: 0
                
                Log.d(tag, "Menerima perintah online dari Parent: $action ($value)")
                
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(this@ChildMonitoringService, "Menerima perintah: $action", Toast.LENGTH_SHORT).show()
                }
                
                when (action) {
                    "LOCK_NOW" -> deviceControlManager.lockScreenInstantly()
                    "SHOW_OVERLAY" -> deviceControlManager.showLockOverlay()
                    "HIDE_OVERLAY" -> deviceControlManager.hideLockOverlay()
                    "SET_VOLUME" -> deviceControlManager.setVolumePercent(value)
                    "SET_BRIGHTNESS" -> deviceControlManager.setBrightnessPercent(value)
                    "SET_WIFI" -> {
                        val success = deviceControlManager.setWifiEnabled(value == 1)
                        if (!success) {
                            Handler(Looper.getMainLooper()).post {
                                Toast.makeText(this@ChildMonitoringService, "Sistem Android memblokir kontrol Wi-Fi!", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                    "SYNC_STATS" -> serviceScope.launch { syncUsageStatsToFirebase() }
                }
                
                // Segera update status terbaru (volume/brightness baru) ke Firebase
                serviceScope.launch {
                    delay(300) // Beri waktu 300ms agar sistem Android selesai menerapkan volume baru
                    syncUsageStatsToFirebase()
                }

                // Hapus command setelah dieksekusi agar tidak dijalankan berulang kali
                commandRef.removeValue()
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(tag, "Gagal mendengarkan pendingCommand: ${error.message}")
            }
        }
        commandRef.addValueEventListener(commandListener as ValueEventListener)
    }

    private fun startPeriodicSync() {
        serviceScope.launch {
            while (isActive) {
                syncUsageStatsToFirebase()
                delay(SYNC_INTERVAL_MS)
            }
        }
    }

    private fun syncUsageStatsToFirebase() {
        try {
            val appList = appUsageManager.getDailyUsageStats()
            val ytMinutes = appUsageManager.getYoutubeKidsUsageMinutes()
            val totalMinutes = appUsageManager.getTotalScreenTimeMinutes()

            val summary = DeviceUsageSummary(
                deviceId = deviceId,
                totalScreenTimeMinutes = totalMinutes,
                youtubeKidsMinutes = ytMinutes,
                isLocked = false,
                currentVolumePercent = deviceControlManager.getCurrentVolumePercent(),
                currentBrightnessPercent = deviceControlManager.getCurrentBrightnessPercent(),
                appList = appList
            )

            val deviceRef = database.getReference("devices").child(deviceId)
            deviceRef.child("summary").setValue(summary)
            deviceRef.child("lastHeartbeat").setValue(System.currentTimeMillis())
            deviceRef.child("isOnline").setValue(true)
            deviceRef.child("isOnline").onDisconnect().setValue(false)

            Log.d(tag, "Statistik penggunaan berhasil disinkronkan ke Firebase. YouTube Kids: $ytMinutes menit")
        } catch (e: Exception) {
            Log.e(tag, "Gagal sinkronisasi data ke Firebase: ${e.message}")
        }
    }

    private fun buildForegroundNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, ChildApp.CHANNEL_ID)
            .setContentTitle("Proteksi Leon Aktif")
            .setContentText("Tablet sedang dipantau oleh Parental Control")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        serviceJob.cancel()
        bleServerManager?.stopServer()
        commandListener?.let {
            database.getReference("devices").child(deviceId).child("pendingCommand").removeEventListener(it)
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val NOTIFICATION_ID = 1001
        const val ACTION_SYNC_STATS_NOW = "com.parentalcontrol.child.SYNC_NOW"
        const val ACTION_UPDATE_FCM_TOKEN = "com.parentalcontrol.child.UPDATE_TOKEN"
        private const val SYNC_INTERVAL_MS = 60_000L // Sinkronisasi berkala setiap 1 menit
    }
}
