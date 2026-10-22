package com.parentalcontrol.child.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.WindowManager
import android.widget.EditText
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.parentalcontrol.child.ChildApp
import com.parentalcontrol.child.databinding.ActivityLockOverlayBinding

class LockOverlayActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLockOverlayBinding

    private val dismissReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_DISMISS_OVERLAY) {
                finish()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupWindowFlags()

        binding = ActivityLockOverlayBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Cegah tombol back keluar dari overlay
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Jangan lakukan apa-apa agar anak tidak bisa bypass
            }
        })

        binding.btnUnlockWithPin.setOnClickListener {
            showParentPinDialog()
        }

        val filter = IntentFilter(ACTION_DISMISS_OVERLAY)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(dismissReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(dismissReceiver, filter)
        }
    }

    private fun setupWindowFlags() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun showParentPinDialog() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = "Masukkan 4 digit PIN"
        }

        AlertDialog.Builder(this)
            .setTitle("Verifikasi Orang Tua")
            .setMessage("Masukkan PIN orang tua untuk membuka kunci:")
            .setView(input)
            .setPositiveButton("Buka Kunci") { _, _ ->
                val enteredPin = input.text.toString()
                val sharedPrefs = getSharedPreferences(ChildApp.PREFS_NAME, Context.MODE_PRIVATE)
                val savedPin = sharedPrefs.getString(ChildApp.KEY_PARENT_PIN, ChildApp.DEFAULT_PIN)

                if (enteredPin == savedPin) {
                    Toast.makeText(this, "Kunci berhasil dibuka", Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    Toast.makeText(this, "PIN salah! Hanya orang tua yang tahu.", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(dismissReceiver)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        super.onDestroy()
    }

    companion object {
        const val ACTION_DISMISS_OVERLAY = "com.parentalcontrol.child.DISMISS_OVERLAY"
    }
}
