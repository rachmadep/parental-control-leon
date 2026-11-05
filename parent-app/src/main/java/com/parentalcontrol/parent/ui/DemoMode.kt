package com.parentalcontrol.parent.ui

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo

/**
 * Mode demo: data contoh lokal tanpa Firebase dan tanpa akun, untuk merekam video/screenshot.
 * Hanya aktif pada build **debug** (debuggable) dan jika MainActivity dibuka dengan extra `demo=true`:
 *
 *     adb shell am start -n com.parentalcontrol.parent/.ui.MainActivity --ez demo true
 *
 * Build rilis mengabaikan extra ini.
 */
object DemoMode {

    const val EXTRA_DEMO = "demo"

    @Volatile
    var enabled = false
        private set

    fun init(context: Context, intent: Intent) {
        val debuggable = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        enabled = debuggable && intent.getBooleanExtra(EXTRA_DEMO, false)
    }
}
