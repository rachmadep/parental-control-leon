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
        const val TARGET_DEVICE_ID = "leon_tablet"
    }
}
