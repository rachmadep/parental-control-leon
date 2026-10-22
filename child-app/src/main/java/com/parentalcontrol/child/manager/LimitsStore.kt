package com.parentalcontrol.child.manager

import android.content.Context
import com.google.gson.Gson
import com.parentalcontrol.child.ChildApp
import com.parentalcontrol.child.model.LimitsConfig

/**
 * Menyimpan konfigurasi batas waktu terakhir dari parent di SharedPreferences,
 * sehingga penegakan tetap jalan saat tablet offline atau baru reboot.
 */
class LimitsStore(context: Context) {

    private val prefs = context.getSharedPreferences(ChildApp.PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    fun saveJson(json: String) {
        prefs.edit().putString(KEY_LIMITS_JSON, json).apply()
    }

    fun load(): LimitsConfig {
        val json = prefs.getString(KEY_LIMITS_JSON, null) ?: return LimitsConfig()
        return try {
            gson.fromJson(json, LimitsConfig::class.java) ?: LimitsConfig()
        } catch (e: Exception) {
            LimitsConfig()
        }
    }

    /** Beri jeda penegakan setelah orang tua membuka kunci secara manual (remote atau PIN). */
    fun snoozeFor(minutes: Int) {
        prefs.edit().putLong(KEY_SNOOZE_UNTIL, System.currentTimeMillis() + minutes * 60_000L).apply()
    }

    fun isSnoozed(now: Long = System.currentTimeMillis()): Boolean =
        now < prefs.getLong(KEY_SNOOZE_UNTIL, 0L)

    companion object {
        const val SNOOZE_MINUTES = 10
        private const val KEY_LIMITS_JSON = "limits_json"
        private const val KEY_SNOOZE_UNTIL = "snooze_until"
    }
}
