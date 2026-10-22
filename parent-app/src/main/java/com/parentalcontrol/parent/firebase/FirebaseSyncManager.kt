package com.parentalcontrol.parent.firebase

import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.gson.Gson
import com.parentalcontrol.parent.model.AppUsageItem
import com.parentalcontrol.parent.model.DayHistory
import com.parentalcontrol.parent.model.DeviceAlert
import com.parentalcontrol.parent.model.DeviceLocation
import com.parentalcontrol.parent.model.DeviceSettings
import com.parentalcontrol.parent.model.DeviceSummary
import com.parentalcontrol.parent.model.LimitsConfig
import com.parentalcontrol.parent.model.PinCredential

class FirebaseSyncManager(
    private val deviceId: String,
    private val onDeviceUpdated: (summary: DeviceSummary, isOnline: Boolean) -> Unit,
    private val onHistoryUpdated: (List<DayHistory>) -> Unit = {},
    private val onConfigUpdated: (LimitsConfig) -> Unit = {},
    private val onSettingsUpdated: (DeviceSettings) -> Unit = {},
    private val onPinUpdated: (PinCredential?) -> Unit = {},
    private val onLocationUpdated: (DeviceLocation?) -> Unit = {},
    private val onAlert: (DeviceAlert) -> Unit = {}
) {
    private val database = FirebaseDatabase.getInstance()
    private val deviceRef = database.getReference("devices").child(deviceId)
    private val gson = Gson()

    // Listener dipisah per child node agar riwayat/config (yang tumbuh) tidak ikut terunduh tiap
    // ringkasan berubah.
    private val summaryRef = deviceRef.child("summary")
    private val onlineRef = deviceRef.child("isOnline")
    private val historyQuery = deviceRef.child("history").orderByKey().limitToLast(HISTORY_DAYS)
    private val configRef = deviceRef.child("config")
    private val settingsRef = deviceRef.child("settings")
    private val pinRef = deviceRef.child("pin")
    private val locationRef = deviceRef.child("location")
    private val alertRef = deviceRef.child("lastAlert")

    private var summaryListener: ValueEventListener? = null
    private var onlineListener: ValueEventListener? = null
    private var historyListener: ValueEventListener? = null
    private var configListener: ValueEventListener? = null
    private var settingsListener: ValueEventListener? = null
    private var pinListener: ValueEventListener? = null
    private var locationListener: ValueEventListener? = null
    private var alertListener: ValueEventListener? = null

    private var lastSummary = DeviceSummary(deviceId = deviceId)
    private var lastOnline = false

    fun startListening() {
        stopListening()

        summaryListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                lastSummary = parseSummary(snapshot)
                onDeviceUpdated(lastSummary, lastOnline)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Gagal mendengarkan summary: ${error.message}")
            }
        }
        onlineListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                lastOnline = snapshot.getValue(Boolean::class.java) ?: false
                onDeviceUpdated(lastSummary, lastOnline)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Gagal mendengarkan isOnline: ${error.message}")
            }
        }
        historyListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                onHistoryUpdated(snapshot.children.map { parseHistory(it) }.sortedBy { it.date })
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Gagal mendengarkan history: ${error.message}")
            }
        }
        configListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val config = try {
                    snapshot.getValue(String::class.java)?.let { gson.fromJson(it, LimitsConfig::class.java) }
                } catch (e: Exception) {
                    Log.e(TAG, "Config di Firebase tidak bisa dibaca: ${e.message}")
                    null
                }
                onConfigUpdated(config ?: LimitsConfig())
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Gagal mendengarkan config: ${error.message}")
            }
        }

        settingsListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val settings = try {
                    snapshot.getValue(String::class.java)?.let { gson.fromJson(it, DeviceSettings::class.java) }
                } catch (e: Exception) {
                    Log.e(TAG, "Settings di Firebase tidak bisa dibaca: ${e.message}")
                    null
                }
                onSettingsUpdated(settings ?: DeviceSettings())
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Gagal mendengarkan settings: ${error.message}")
            }
        }
        pinListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val salt = snapshot.child("salt").getValue(String::class.java)
                val hash = snapshot.child("hash").getValue(String::class.java)
                onPinUpdated(if (salt != null && hash != null) PinCredential(salt, hash) else null)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Gagal mendengarkan pin: ${error.message}")
            }
        }
        locationListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val lat = snapshot.child("lat").getValue(Double::class.java)
                val lng = snapshot.child("lng").getValue(Double::class.java)
                if (lat == null || lng == null) {
                    onLocationUpdated(null)
                    return
                }
                onLocationUpdated(
                    DeviceLocation(
                        lat = lat,
                        lng = lng,
                        accuracy = snapshot.child("accuracy").getValue(Double::class.java) ?: 0.0,
                        provider = snapshot.child("provider").getValue(String::class.java) ?: "",
                        timestamp = snapshot.child("timestamp").getValue(Long::class.java) ?: 0L
                    )
                )
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Gagal mendengarkan location: ${error.message}")
            }
        }
        alertListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) return
                onAlert(
                    DeviceAlert(
                        id = snapshot.child("id").getValue(String::class.java) ?: "",
                        type = snapshot.child("type").getValue(String::class.java) ?: "",
                        message = snapshot.child("message").getValue(String::class.java) ?: "",
                        timestamp = snapshot.child("timestamp").getValue(Long::class.java) ?: 0L
                    )
                )
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Gagal mendengarkan lastAlert: ${error.message}")
            }
        }

        summaryRef.addValueEventListener(summaryListener as ValueEventListener)
        settingsRef.addValueEventListener(settingsListener as ValueEventListener)
        pinRef.addValueEventListener(pinListener as ValueEventListener)
        locationRef.addValueEventListener(locationListener as ValueEventListener)
        alertRef.addValueEventListener(alertListener as ValueEventListener)
        onlineRef.addValueEventListener(onlineListener as ValueEventListener)
        historyQuery.addValueEventListener(historyListener as ValueEventListener)
        configRef.addValueEventListener(configListener as ValueEventListener)
    }

    private fun parseSummary(summarySnap: DataSnapshot): DeviceSummary {
        val appList = mutableListOf<AppUsageItem>()
        for (child in summarySnap.child("appList").children) {
            val pkgName = child.child("packageName").getValue(String::class.java) ?: ""
            if (pkgName.isEmpty()) continue
            appList.add(
                AppUsageItem(
                    packageName = pkgName,
                    appName = child.child("appName").getValue(String::class.java) ?: pkgName,
                    totalTimeInForegroundMs = child.child("totalTimeInForegroundMs").getValue(Long::class.java) ?: 0L,
                    durationMinutes = child.child("durationMinutes").getValue(Long::class.java) ?: 0L,
                    lastTimeUsed = child.child("lastTimeUsed").getValue(Long::class.java) ?: 0L,
                    category = child.child("category").getValue(String::class.java) ?: "lainnya"
                )
            )
        }

        val totalScreenTime = summarySnap.child("totalScreenTimeMinutes").getValue(Long::class.java) ?: 0L
        return DeviceSummary(
            deviceId = deviceId,
            totalScreenTimeMinutes = totalScreenTime,
            youtubeKidsMinutes = summarySnap.child("youtubeKidsMinutes").getValue(Long::class.java) ?: 0L,
            limitUsedMinutes = summarySnap.child("limitUsedMinutes").getValue(Long::class.java) ?: totalScreenTime,
            // Firebase menyimpan properti Kotlin `isLocked` sebagai "locked" (awalan "is" dibuang)
            isLocked = summarySnap.child("locked").getValue(Boolean::class.java)
                ?: summarySnap.child("isLocked").getValue(Boolean::class.java) ?: false,
            currentVolumePercent = summarySnap.child("currentVolumePercent").getValue(Int::class.java) ?: 50,
            currentBrightnessPercent = summarySnap.child("currentBrightnessPercent").getValue(Int::class.java) ?: 60,
            appList = appList.sortedByDescending { it.durationMinutes },
            categories = parseMinutesMap(summarySnap.child("categories")),
            deviceModel = summarySnap.child("deviceModel").getValue(String::class.java) ?: "",
            androidVersion = summarySnap.child("androidVersion").getValue(String::class.java) ?: "",
            batteryPercent = summarySnap.child("batteryPercent").getValue(Int::class.java) ?: -1,
            deviceAdminActive = summarySnap.child("deviceAdminActive").getValue(Boolean::class.java) ?: true
        )
    }

    private fun parseHistory(snap: DataSnapshot) = DayHistory(
        date = snap.child("date").getValue(String::class.java) ?: (snap.key ?: ""),
        totalMinutes = snap.child("totalMinutes").getValue(Long::class.java) ?: 0L,
        youtubeKidsMinutes = snap.child("youtubeKidsMinutes").getValue(Long::class.java) ?: 0L,
        categories = parseMinutesMap(snap.child("categories"))
    )

    private fun parseMinutesMap(snap: DataSnapshot): Map<String, Long> =
        snap.children.mapNotNull { c ->
            val key = c.key ?: return@mapNotNull null
            key to (c.getValue(Long::class.java) ?: 0L)
        }.toMap()

    /**
     * Kirim perintah ke Realtime Database agar tablet yang sedang terhubung internet mengeksekusinya.
     */
    fun sendCommandOnline(action: String, value: Int = 0, onResult: (Boolean, String?) -> Unit) {
        val commandMap = mapOf(
            "action" to action,
            "value" to value,
            "timestamp" to System.currentTimeMillis()
        )
        deviceRef.child("pendingCommand").setValue(commandMap)
            .addOnSuccessListener {
                Log.d(TAG, "Perintah $action berhasil dikirim ke Cloud")
                onResult(true, null)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Gagal mengirim $action ke Cloud", e)
                onResult(false, e.message)
            }
    }

    /** Tulis seluruh konfigurasi batas waktu sebagai satu string JSON (atomik untuk tablet). */
    fun saveConfig(config: LimitsConfig, onResult: (Boolean, String?) -> Unit) {
        configRef.setValue(gson.toJson(config))
            .addOnSuccessListener { onResult(true, null) }
            .addOnFailureListener { e ->
                Log.e(TAG, "Gagal menyimpan config", e)
                onResult(false, e.message)
            }
    }

    fun saveSettings(settings: DeviceSettings, onResult: (Boolean, String?) -> Unit) {
        settingsRef.setValue(gson.toJson(settings))
            .addOnSuccessListener { onResult(true, null) }
            .addOnFailureListener { e ->
                Log.e(TAG, "Gagal menyimpan settings", e)
                onResult(false, e.message)
            }
    }

    fun savePin(credential: PinCredential, onResult: (Boolean, String?) -> Unit) {
        pinRef.setValue(mapOf("salt" to credential.salt, "hash" to credential.hash))
            .addOnSuccessListener { onResult(true, null) }
            .addOnFailureListener { e ->
                Log.e(TAG, "Gagal menyimpan PIN", e)
                onResult(false, e.message)
            }
    }

    fun stopListening() {
        summaryListener?.let { summaryRef.removeEventListener(it) }
        onlineListener?.let { onlineRef.removeEventListener(it) }
        historyListener?.let { historyQuery.removeEventListener(it) }
        configListener?.let { configRef.removeEventListener(it) }
        settingsListener?.let { settingsRef.removeEventListener(it) }
        pinListener?.let { pinRef.removeEventListener(it) }
        locationListener?.let { locationRef.removeEventListener(it) }
        alertListener?.let { alertRef.removeEventListener(it) }
        settingsListener = null
        pinListener = null
        locationListener = null
        alertListener = null
        summaryListener = null
        onlineListener = null
        historyListener = null
        configListener = null
    }

    companion object {
        private const val TAG = "FirebaseSyncManager"
        private const val HISTORY_DAYS = 30
    }
}
