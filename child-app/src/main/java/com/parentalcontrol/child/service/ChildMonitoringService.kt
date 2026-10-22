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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.parentalcontrol.child.ChildApp
import com.parentalcontrol.child.R
import com.parentalcontrol.child.manager.AppUsageManager
import com.parentalcontrol.child.manager.BleServerManager
import com.parentalcontrol.child.manager.DeviceControlManager
import com.parentalcontrol.child.manager.AlertWriter
import com.parentalcontrol.child.manager.LimitEnforcer
import com.parentalcontrol.child.manager.LimitsStore
import com.parentalcontrol.child.manager.LocationReporter
import com.parentalcontrol.child.manager.SettingsStore
import com.parentalcontrol.child.manager.UsageSnapshot
import com.parentalcontrol.child.model.AlertTypes
import com.parentalcontrol.child.model.AppCategory
import com.parentalcontrol.child.model.AppUsageInfo
import com.parentalcontrol.child.model.DeviceSettings
import com.parentalcontrol.child.model.DeviceUsageSummary
import com.parentalcontrol.child.model.LimitsConfig
import com.parentalcontrol.child.ui.LockOverlayActivity
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

    private lateinit var limitsStore: LimitsStore
    @Volatile
    private var limitsConfig = LimitsConfig()
    private var configListener: ValueEventListener? = null
    // true jika overlay yang sedang tampil dipicu penegakan batas (bukan perintah manual parent)
    private var enforcementLockActive = false

    private lateinit var settingsStore: SettingsStore
    private lateinit var locationReporter: LocationReporter
    @Volatile
    private var deviceSettings = DeviceSettings()
    private var settingsListener: ValueEventListener? = null
    private var pinListener: ValueEventListener? = null

    // Listener Firebase baru dipasang setelah akun keluarga masuk (database dilindungi aturan auth)
    private val auth = FirebaseAuth.getInstance()
    private val retryPending = java.util.concurrent.atomic.AtomicBoolean(false)
    private val authListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        if (firebaseAuth.currentUser != null) startFirebaseSession() else stopFirebaseSession()
    }

    override fun onCreate() {
        super.onCreate()
        appUsageManager = AppUsageManager(this)
        deviceControlManager = DeviceControlManager(this)
        limitsStore = LimitsStore(this)
        limitsConfig = limitsStore.load()

        deviceId = "leon_tablet" // Hardcode agar identik dengan ParentApp
        settingsStore = SettingsStore(this)
        deviceSettings = settingsStore.loadSettings()
        locationReporter = LocationReporter(this, deviceId)

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
        startEnforcementLoop() // memakai config cache lokal, jalan walau belum masuk / offline
        auth.addAuthStateListener(authListener) // memanggil startFirebaseSession() saat sudah masuk
    }

    private fun startFirebaseSession() {
        stopFirebaseSession()
        listenForCommands()
        listenForLimitsConfig()
        listenForSettingsAndPin()
        serviceScope.launch { syncUsageStatsToFirebase() }
    }

    private fun stopFirebaseSession() {
        val deviceRef = database.getReference("devices").child(deviceId)
        commandListener?.let { deviceRef.child("pendingCommand").removeEventListener(it) }
        configListener?.let { deviceRef.child("config").removeEventListener(it) }
        settingsListener?.let { deviceRef.child("settings").removeEventListener(it) }
        pinListener?.let { deviceRef.child("pin").removeEventListener(it) }
        commandListener = null
        configListener = null
        settingsListener = null
        pinListener = null
    }

    /**
     * Listener yang dibatalkan karena akses ditolak (mis. aturan baru baru diterapkan atau token
     * belum siap) tidak dipasang ulang oleh SDK, jadi mulai ulang sesi setelah jeda.
     */
    private fun onListenerCancelled(name: String, error: DatabaseError) {
        Log.e(tag, "Gagal mendengarkan $name: ${error.message}")
        if (error.code == DatabaseError.PERMISSION_DENIED && auth.currentUser != null &&
            retryPending.compareAndSet(false, true)
        ) {
            serviceScope.launch {
                delay(RETRY_DELAY_MS)
                retryPending.set(false)
                if (auth.currentUser != null) startFirebaseSession()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SYNC_STATS_NOW -> {
                serviceScope.launch { syncUsageStatsToFirebase() }
            }
            ACTION_UPDATE_FCM_TOKEN -> {
                val token = intent.getStringExtra("token")
                if (token != null && auth.currentUser != null) {
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
                onListenerCancelled("pendingCommand", error)
            }
        }
        commandRef.addValueEventListener(commandListener as ValueEventListener)
    }

    /**
     * Konfigurasi batas waktu dari parent. Disimpan lokal supaya penegakan tetap jalan saat offline.
     */
    private fun listenForLimitsConfig() {
        val configRef = database.getReference("devices").child(deviceId).child("config")
        configListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val json = try {
                    snapshot.getValue(String::class.java)
                } catch (e: Exception) {
                    Log.e(tag, "Format config tidak valid: ${e.message}")
                    null
                } ?: return

                limitsStore.saveJson(json)
                limitsConfig = limitsStore.load()
                serviceScope.launch { enforceLimits() }
            }

            override fun onCancelled(error: DatabaseError) {
                onListenerCancelled("config", error)
            }
        }
        configRef.addValueEventListener(configListener as ValueEventListener)
    }

    /**
     * `settings` (lokasi, proteksi uninstall) dan `pin` (salt + hash PIN orang tua) dari parent.
     * Keduanya disimpan lokal agar tetap berlaku saat offline.
     */
    private fun listenForSettingsAndPin() {
        val deviceRef = database.getReference("devices").child(deviceId)

        settingsListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val json = try {
                    snapshot.getValue(String::class.java)
                } catch (e: Exception) {
                    Log.e(tag, "Format settings tidak valid: ${e.message}")
                    null
                } ?: return

                settingsStore.saveSettingsJson(json)
                deviceSettings = settingsStore.loadSettings()
                serviceScope.launch { locationReporter.sync(deviceSettings.locationEnabled) }
            }

            override fun onCancelled(error: DatabaseError) {
                onListenerCancelled("settings", error)
            }
        }
        deviceRef.child("settings").addValueEventListener(settingsListener as ValueEventListener)

        pinListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val salt = snapshot.child("salt").getValue(String::class.java)
                val hash = snapshot.child("hash").getValue(String::class.java)
                if (salt != null && hash != null) settingsStore.savePin(salt, hash) else settingsStore.clearPin()
            }

            override fun onCancelled(error: DatabaseError) {
                onListenerCancelled("pin", error)
            }
        }
        deviceRef.child("pin").addValueEventListener(pinListener as ValueEventListener)
    }

    private fun startEnforcementLoop() {
        serviceScope.launch {
            while (isActive) {
                enforceLimits()
                delay(ENFORCE_INTERVAL_MS)
            }
        }
    }

    private fun enforceLimits() {
        try {
            val violation = if (limitsStore.isSnoozed()) {
                null
            } else {
                LimitEnforcer.evaluate(limitsConfig, buildUsageSnapshot(), LimitEnforcer.currentClock())
            }

            if (violation != null) {
                if (!enforcementLockActive) {
                    AlertWriter.send(deviceId, AlertTypes.LIMIT_REACHED, violation.reason)
                }
                enforcementLockActive = true
                if (!LockOverlayActivity.isShowing) {
                    Log.i(tag, "Batas waktu tercapai: ${violation.reason}")
                    deviceControlManager.showLockOverlay(violation.reason)
                }
            } else if (enforcementLockActive) {
                // Penyebab kunci sudah hilang (bonus waktu, batas diubah, jam tidur selesai)
                enforcementLockActive = false
                deviceControlManager.hideLockOverlay(snooze = false)
            }
        } catch (e: Exception) {
            Log.e(tag, "Gagal menegakkan batas waktu: ${e.message}")
        }
    }

    private fun buildUsageSnapshot(): UsageSnapshot {
        // Pemakaian aplikasi ini sendiri (termasuk overlay) tidak dihitung ke batas anak
        val apps = appUsageManager.getDailyUsageStats().filter { it.packageName != packageName }
        val foreground = appUsageManager.foregroundPackage
        return UsageSnapshot(
            totalMs = apps.sumOf { it.totalTimeInForegroundMs },
            packageMs = apps.associate { it.packageName to it.totalTimeInForegroundMs },
            categoryMs = apps.groupBy { it.category }
                .mapValues { (_, list) -> list.sumOf { it.totalTimeInForegroundMs } },
            foregroundPackage = foreground,
            foregroundCategory = apps.firstOrNull { it.packageName == foreground }?.category
        )
    }

    private fun categoryMinutes(appList: List<AppUsageInfo>): Map<String, Long> {
        val result = AppCategory.values().associate { it.key to 0L }.toMutableMap()
        for (app in appList) {
            val minutes = Math.ceil(app.totalTimeInForegroundMs / 60000.0).toLong()
            result[app.category] = (result[app.category] ?: 0L) + minutes
        }
        return result
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
        // Tanpa akun masuk, penulisan ke database pasti ditolak aturan; lewati dan coba lagi nanti
        if (auth.currentUser == null) return
        try {
            val appList = appUsageManager.getDailyUsageStats()
            val ytMinutes = appUsageManager.getYoutubeKidsUsageMinutes()
            val totalMinutes = appUsageManager.getTotalScreenTimeMinutes()

            val categories = categoryMinutes(appList)
            val summary = DeviceUsageSummary(
                deviceId = deviceId,
                totalScreenTimeMinutes = totalMinutes,
                youtubeKidsMinutes = ytMinutes,
                isLocked = LockOverlayActivity.isShowing,
                currentVolumePercent = deviceControlManager.getCurrentVolumePercent(),
                currentBrightnessPercent = deviceControlManager.getCurrentBrightnessPercent(),
                appList = appList,
                categories = categories,
                limitUsedMinutes = appList.filter { it.packageName != packageName }
                    .sumOf { Math.ceil(it.totalTimeInForegroundMs / 60000.0).toLong() },
                deviceModel = deviceControlManager.getDeviceModel(),
                androidVersion = deviceControlManager.getAndroidVersion(),
                batteryPercent = deviceControlManager.getBatteryPercent(),
                deviceAdminActive = deviceControlManager.isDeviceAdminActive()
            )
            // Cek ulang tiap sinkronisasi: izin lokasi/layanan lokasi bisa baru diaktifkan setelah service jalan
            locationReporter.sync(deviceSettings.locationEnabled)

            val deviceRef = database.getReference("devices").child(deviceId)
            deviceRef.child("summary").setValue(summary)
            writeDailyHistory(deviceRef.child("history"), totalMinutes, ytMinutes, categories)
            deviceRef.child("lastHeartbeat").setValue(System.currentTimeMillis())
            deviceRef.child("isOnline").setValue(true)
            deviceRef.child("isOnline").onDisconnect().setValue(false)

            Log.d(tag, "Statistik penggunaan berhasil disinkronkan ke Firebase. YouTube Kids: $ytMinutes menit")
        } catch (e: Exception) {
            Log.e(tag, "Gagal sinkronisasi data ke Firebase: ${e.message}")
        }
    }

    /**
     * Simpan ringkasan harian ke `history/{yyyy-MM-dd}` (ditimpa tiap sinkronisasi) untuk layar Aktivitas
     * di parent. Entri yang sudah lewat masa simpan dihapus satu per satu setiap hari.
     */
    private fun writeDailyHistory(
        historyRef: com.google.firebase.database.DatabaseReference,
        totalMinutes: Long,
        ytMinutes: Long,
        categories: Map<String, Long>
    ) {
        val today = LimitEnforcer.dateKey()
        historyRef.child(today).setValue(
            mapOf(
                "date" to today,
                "totalMinutes" to totalMinutes,
                "youtubeKidsMinutes" to ytMinutes,
                "categories" to categories,
                "timestamp" to System.currentTimeMillis()
            )
        )
        historyRef.child(LimitEnforcer.dateKey(-HISTORY_RETENTION_DAYS)).removeValue()
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
        auth.removeAuthStateListener(authListener)
        stopFirebaseSession()
        locationReporter.stop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val NOTIFICATION_ID = 1001
        const val ACTION_SYNC_STATS_NOW = "com.parentalcontrol.child.SYNC_NOW"
        const val ACTION_UPDATE_FCM_TOKEN = "com.parentalcontrol.child.UPDATE_TOKEN"
        private const val SYNC_INTERVAL_MS = 60_000L // Sinkronisasi berkala setiap 1 menit
        private const val ENFORCE_INTERVAL_MS = 15_000L // Cek batas waktu tiap 15 detik
        private const val HISTORY_RETENTION_DAYS = 35
        private const val RETRY_DELAY_MS = 30_000L
    }
}
