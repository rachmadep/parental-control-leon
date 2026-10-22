package com.parentalcontrol.child.manager

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.firebase.database.FirebaseDatabase

/**
 * Mengirim lokasi tablet ke `devices/{id}/location` tiap beberapa menit selama fitur Lokasi diaktifkan
 * orang tua. Memakai LocationManager (tanpa Google Play Services). Akses dari service latar belakang
 * butuh izin lokasi "sepanjang waktu" (ACCESS_BACKGROUND_LOCATION) pada Android 10+.
 */
class LocationReporter(private val context: Context, private val deviceId: String) {

    private val tag = "LocationReporter"
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    private var running = false

    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            report(location)
        }

        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}

        // Wajib di-override: pada Android < 11 metode ini abstrak dan dipanggil sistem
        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    }

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /** Dipanggil berkala: menyalakan atau mematikan pelaporan sesuai [enabled] dan izin yang ada. */
    @Synchronized
    fun sync(enabled: Boolean) {
        if (enabled) start() else stop()
    }

    @SuppressLint("MissingPermission")
    private fun start() {
        val manager = locationManager ?: return
        if (running || !hasPermission()) return

        val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .filter { manager.isProviderEnabled(it) }
        if (providers.isEmpty()) {
            Log.w(tag, "Layanan lokasi di tablet mati, dicoba lagi pada sinkronisasi berikutnya")
            return
        }

        try {
            providers.forEach { manager.requestLocationUpdates(it, UPDATE_INTERVAL_MS, 0f, listener, Looper.getMainLooper()) }
            running = true
            providers.mapNotNull { manager.getLastKnownLocation(it) }.maxByOrNull { it.time }?.let { report(it) }
        } catch (e: SecurityException) {
            Log.e(tag, "Izin lokasi ditolak sistem: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    @Synchronized
    fun stop() {
        if (!running) return
        locationManager?.removeUpdates(listener)
        running = false
    }

    private fun report(location: Location) {
        FirebaseDatabase.getInstance().getReference("devices").child(deviceId).child("location").setValue(
            mapOf(
                "lat" to location.latitude,
                "lng" to location.longitude,
                "accuracy" to location.accuracy.toDouble(),
                "provider" to (location.provider ?: ""),
                "timestamp" to location.time
            )
        )
    }

    companion object {
        private const val UPDATE_INTERVAL_MS = 10 * 60_000L
    }
}
