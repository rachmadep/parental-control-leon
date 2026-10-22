package com.parentalcontrol.parent.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.parentalcontrol.parent.model.AlertTypes
import com.parentalcontrol.parent.model.DeviceAlert

/**
 * Notifikasi lokal untuk peringatan dari tablet. Hanya muncul selama proses aplikasi ini masih hidup
 * (listener Firebase berjalan di dalamnya); tidak ada push server.
 */
object AlertNotifier {

    private const val CHANNEL_ID = "device_alerts"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(CHANNEL_ID, "Peringatan Tablet Leon", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Batas waktu tercapai dan perubahan proteksi di tablet"
        }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    @SuppressLint("MissingPermission") // dicek manual di bawah
    fun show(context: Context, alert: DeviceAlert) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return

        val title = when (alert.type) {
            AlertTypes.LIMIT_REACHED -> "Batas waktu Leon tercapai"
            AlertTypes.ADMIN_DISABLED -> "Proteksi tablet dimatikan"
            else -> "Peringatan tablet Leon"
        }
        val openApp = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(alert.message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(alert.message))
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        // id dari timestamp agar tiap peringatan baru muncul sebagai notifikasi terpisah
        NotificationManagerCompat.from(context).notify(alert.timestamp.toInt(), notification)
    }
}
