package com.parentalcontrol.parent.firebase

import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.gson.Gson
import com.parentalcontrol.parent.model.AppUsageItem
import com.parentalcontrol.parent.model.DayHistory
import com.parentalcontrol.parent.model.DeviceSummary
import com.parentalcontrol.parent.model.LimitsConfig

class FirebaseSyncManager(
    private val deviceId: String,
    private val onDeviceUpdated: (summary: DeviceSummary, isOnline: Boolean) -> Unit,
    private val onHistoryUpdated: (List<DayHistory>) -> Unit = {},
    private val onConfigUpdated: (LimitsConfig) -> Unit = {}
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

    private var summaryListener: ValueEventListener? = null
    private var onlineListener: ValueEventListener? = null
    private var historyListener: ValueEventListener? = null
    private var configListener: ValueEventListener? = null

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

        summaryRef.addValueEventListener(summaryListener as ValueEventListener)
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
            isLocked = summarySnap.child("isLocked").getValue(Boolean::class.java) ?: false,
            currentVolumePercent = summarySnap.child("currentVolumePercent").getValue(Int::class.java) ?: 50,
            currentBrightnessPercent = summarySnap.child("currentBrightnessPercent").getValue(Int::class.java) ?: 60,
            appList = appList.sortedByDescending { it.durationMinutes },
            categories = parseMinutesMap(summarySnap.child("categories"))
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

    fun stopListening() {
        summaryListener?.let { summaryRef.removeEventListener(it) }
        onlineListener?.let { onlineRef.removeEventListener(it) }
        historyListener?.let { historyQuery.removeEventListener(it) }
        configListener?.let { configRef.removeEventListener(it) }
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
