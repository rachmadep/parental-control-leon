package com.parentalcontrol.parent

import android.app.Application
import com.parentalcontrol.parent.ui.AlertNotifier

class ParentApp : Application() {

    override fun onCreate() {
        super.onCreate()
        AlertNotifier.ensureChannel(this)
    }

    companion object {
        const val BLE_SERVICE_UUID = "0000fff0-0000-1000-8000-00805f9b34fb"
        const val BLE_COMMAND_CHAR_UUID = "0000fff1-0000-1000-8000-00805f9b34fb"
        // Kunci service data iklan BLE tablet yang membawa penanda keluarga (lihat BleFamilyTag)
        const val BLE_FAMILY_TAG_UUID = "0000fff2-0000-1000-8000-00805f9b34fb"
    }
}
