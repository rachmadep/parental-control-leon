package com.parentalcontrol.child.receiver

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.parentalcontrol.child.manager.AlertWriter
import com.parentalcontrol.child.manager.SettingsStore
import com.parentalcontrol.child.model.AlertTypes

class AdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Toast.makeText(context, "Proteksi Device Admin Leon Aktif", Toast.LENGTH_SHORT).show()
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence? {
        return if (SettingsStore(context).loadSettings().uninstallProtection) {
            "Orang tua akan diberi tahu jika proteksi ini dimatikan."
        } else {
            null
        }
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Toast.makeText(context, "Proteksi Device Admin Dinonaktifkan", Toast.LENGTH_SHORT).show()

        if (SettingsStore(context).loadSettings().uninstallProtection) {
            AlertWriter.send(
                "leon_tablet", // Hardcode agar identik dengan ParentApp
                AlertTypes.ADMIN_DISABLED,
                "Proteksi Device Admin di tablet Leon dimatikan. Aplikasi sekarang bisa dihapus."
            )
        }
    }
}
