package com.parentalcontrol.parent.firebase

import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.parentalcontrol.parent.model.AppUsageItem
import com.parentalcontrol.parent.model.DeviceSummary

class FirebaseSyncManager(
    private val deviceId: String,
    private val onDeviceUpdated: (summary: DeviceSummary, isOnline: Boolean) -> Unit
) {
    private val database = FirebaseDatabase.getInstance()
    private val deviceRef = database.getReference("devices").child(deviceId)

    private var valueListener: ValueEventListener? = null

    fun startListening() {
        stopListening()

        valueListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val isOnline = snapshot.child("isOnline").getValue(Boolean::class.java) ?: false
                val summarySnap = snapshot.child("summary")

                val totalScreenTime = summarySnap.child("totalScreenTimeMinutes").getValue(Long::class.java) ?: 0L
                val ytMinutes = summarySnap.child("youtubeKidsMinutes").getValue(Long::class.java) ?: 0L
                val currentVol = summarySnap.child("currentVolumePercent").getValue(Int::class.java) ?: 50
                val currentBright = summarySnap.child("currentBrightnessPercent").getValue(Int::class.java) ?: 60

                val appList = mutableListOf<AppUsageItem>()
                for (child in summarySnap.child("appList").children) {
                    val pkgName = child.child("packageName").getValue(String::class.java) ?: ""
                    val appName = child.child("appName").getValue(String::class.java) ?: pkgName
                    val duration = child.child("durationMinutes").getValue(Long::class.java) ?: 0L
                    val totalMs = child.child("totalTimeInForegroundMs").getValue(Long::class.java) ?: 0L
                    val lastUsed = child.child("lastTimeUsed").getValue(Long::class.java) ?: 0L

                    if (pkgName.isNotEmpty()) {
                        appList.add(
                            AppUsageItem(
                                packageName = pkgName,
                                appName = appName,
                                totalTimeInForegroundMs = totalMs,
                                durationMinutes = duration,
                                lastTimeUsed = lastUsed
                            )
                        )
                    }
                }

                val summary = DeviceSummary(
                    deviceId = deviceId,
                    totalScreenTimeMinutes = totalScreenTime,
                    youtubeKidsMinutes = ytMinutes,
                    currentVolumePercent = currentVol,
                    currentBrightnessPercent = currentBright,
                    appList = appList.sortedByDescending { it.durationMinutes }
                )

                onDeviceUpdated(summary, isOnline)
            }

            override fun onCancelled(error: DatabaseError) {
                // Handle database error
            }
        }

        deviceRef.addValueEventListener(valueListener as ValueEventListener)
    }

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
                Log.d("FirebaseSyncManager", "Perintah $action berhasil dikirim ke Cloud")
                onResult(true, null)
            }
            .addOnFailureListener { e ->
                Log.e("FirebaseSyncManager", "Gagal mengirim $action ke Cloud", e)
                onResult(false, e.message)
            }
    }

    fun stopListening() {
        valueListener?.let { deviceRef.removeEventListener(it) }
        valueListener = null
    }
}
