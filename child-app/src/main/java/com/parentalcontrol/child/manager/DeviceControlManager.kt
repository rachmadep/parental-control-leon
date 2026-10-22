package com.parentalcontrol.child.manager

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.BatteryManager
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import com.parentalcontrol.child.receiver.AdminReceiver
import com.parentalcontrol.child.ui.LockOverlayActivity

class DeviceControlManager(private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val devicePolicyManager =
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val adminComponent = ComponentName(context, AdminReceiver::class.java)

    // ========== PERMISSION CHECKS ==========

    fun isDeviceAdminActive(): Boolean {
        return devicePolicyManager.isAdminActive(adminComponent)
    }

    fun canWriteSettings(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.System.canWrite(context)
        } else {
            true
        }
    }

    fun canDrawOverlays(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    // ========== DEVICE INFO ==========

    fun getBatteryPercent(): Int {
        val battery = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val percent = battery?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
        return if (percent in 0..100) percent else -1
    }

    fun getDeviceModel(): String = "${Build.MANUFACTURER} ${Build.MODEL}".trim()

    fun getAndroidVersion(): String = Build.VERSION.RELEASE ?: ""

    // ========== HARDWARE & SYSTEM CONTROLS ==========

    /**
     * Kunci layar sistem secara instan menggunakan DevicePolicyManager.
     */
    fun lockScreenInstantly(): Boolean {
        return if (isDeviceAdminActive()) {
            devicePolicyManager.lockNow()
            true
        } else {
            false
        }
    }

    /**
     * Tampilkan overlay layar penuh yang ramah anak ("Waktunya Istirahat, Leon!").
     */
    fun showLockOverlay(reason: String? = null) {
        val intent = Intent(context, LockOverlayActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            reason?.let { putExtra(LockOverlayActivity.EXTRA_REASON, it) }
        }
        context.startActivity(intent)
    }

    /**
     * Menghilangkan overlay dengan mengirimkan broadcast.
     *
     * @param snooze true untuk pembukaan manual oleh orang tua: penegakan batas waktu dijeda
     * [LimitsStore.SNOOZE_MINUTES] menit agar overlay tidak langsung muncul lagi.
     */
    fun hideLockOverlay(snooze: Boolean = true) {
        if (snooze) LimitsStore(context).snoozeFor(LimitsStore.SNOOZE_MINUTES)
        val intent = Intent(LockOverlayActivity.ACTION_DISMISS_OVERLAY)
        intent.setPackage(context.packageName) // Agar dapat diterima oleh RECEIVER_NOT_EXPORTED
        context.sendBroadcast(intent)
    }

    /**
     * Atur volume media (STREAM_MUSIC) dari 0 hingga 100%.
     */
    fun setVolumePercent(percent: Int) {
        val safePercent = percent.coerceIn(0, 100)
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val targetVolume = (safePercent * maxVolume) / 100
        audioManager.setStreamVolume(
            AudioManager.STREAM_MUSIC,
            targetVolume,
            AudioManager.FLAG_SHOW_UI
        )
    }

    fun getCurrentVolumePercent(): Int {
        val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        return if (max > 0) (current * 100) / max else 0
    }

    /**
     * Atur kecerahan layar dari 0 hingga 100%.
     */
    fun setBrightnessPercent(percent: Int): Boolean {
        if (!canWriteSettings()) return false
        val safePercent = percent.coerceIn(5, 100) // Minimum 5% agar layar tidak mati total
        val brightnessValue = (safePercent * 255) / 100

        try {
            // Matikan auto-brightness jika menyala agar nilai manual berlaku
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            )
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                brightnessValue
            )
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    fun getCurrentBrightnessPercent(): Int {
        return try {
            val brightness = Settings.System.getInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS
            )
            (brightness * 100) / 255
        } catch (e: Exception) {
            50
        }
    }

    /**
     * Kontrol Wi-Fi (Catatan: Pada Android 10+, aplikasi biasa diarahkan ke Panel Pengaturan
     * atau mengandalkan VPN lokal / Device Owner).
     */
    @Suppress("DEPRECATION")
    fun setWifiEnabled(enabled: Boolean): Boolean {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        return try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                wifiManager?.setWifiEnabled(enabled) ?: false
            } else {
                // Di Android 10+, arahkan ke panel atau andalkan mode block
                false
            }
        } catch (e: Exception) {
            false
        }
    }
}
