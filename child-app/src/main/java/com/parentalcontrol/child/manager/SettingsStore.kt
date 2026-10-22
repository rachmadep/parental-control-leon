package com.parentalcontrol.child.manager

import android.content.Context
import com.google.gson.Gson
import com.parentalcontrol.child.ChildApp
import com.parentalcontrol.child.model.DeviceSettings

/**
 * Cache lokal untuk pengaturan dan PIN dari parent, supaya tetap berlaku saat tablet offline.
 */
class SettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences(ChildApp.PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    fun saveSettingsJson(json: String) {
        prefs.edit().putString(KEY_SETTINGS_JSON, json).apply()
    }

    fun loadSettings(): DeviceSettings {
        val json = prefs.getString(KEY_SETTINGS_JSON, null) ?: return DeviceSettings()
        return try {
            gson.fromJson(json, DeviceSettings::class.java) ?: DeviceSettings()
        } catch (e: Exception) {
            DeviceSettings()
        }
    }

    fun savePin(salt: String, hash: String) {
        prefs.edit().putString(KEY_PIN_SALT, salt).putString(KEY_PIN_HASH, hash).apply()
    }

    /** Kembali ke PIN bawaan ([ChildApp.DEFAULT_PIN]). */
    fun clearPin() {
        prefs.edit().remove(KEY_PIN_SALT).remove(KEY_PIN_HASH).apply()
    }

    fun verifyPin(entered: String): Boolean {
        val salt = prefs.getString(KEY_PIN_SALT, null)
        val hash = prefs.getString(KEY_PIN_HASH, null)
        if (salt != null && hash != null) return PinHasher.verify(entered, salt, hash)
        return entered == prefs.getString(ChildApp.KEY_PARENT_PIN, ChildApp.DEFAULT_PIN)
    }

    companion object {
        private const val KEY_SETTINGS_JSON = "settings_json"
        private const val KEY_PIN_SALT = "pin_salt"
        private const val KEY_PIN_HASH = "pin_hash"
    }
}
