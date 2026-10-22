package com.parentalcontrol.parent.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.parentalcontrol.parent.ParentApp
import com.parentalcontrol.parent.R
import com.parentalcontrol.parent.ble.BleClientManager
import com.parentalcontrol.parent.databinding.ActivityMainBinding
import com.parentalcontrol.parent.firebase.FirebaseSyncManager
import com.parentalcontrol.parent.model.DeviceSummary

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var firebaseManager: FirebaseSyncManager
    private lateinit var bleManager: BleClientManager
    private val appUsageAdapter = AppUsageAdapter()

    private var isChildOnline = false
    private var isBleConnected = false

    private val bluetoothPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.entries.all { it.value }
        if (granted) {
            bleManager.startScanAndConnect()
        } else {
            Toast.makeText(this, "Izin Bluetooth dibutuhkan untuk kontrol saat Wi-Fi mati.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Terapkan opsi Dark Mode yang disimpan
        val prefs = getSharedPreferences("parent_prefs", Context.MODE_PRIVATE)
        val isDarkMode = prefs.getBoolean("dark_mode", false)
        if (isDarkMode) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }

        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupManagers()
        setupControls()
        setupDarkModeToggle(isDarkMode)
    }

    private fun setupDarkModeToggle(currentIsDark: Boolean) {
        binding.btnDarkMode.setOnClickListener {
            val newIsDark = !currentIsDark
            getSharedPreferences("parent_prefs", Context.MODE_PRIVATE)
                .edit()
                .putBoolean("dark_mode", newIsDark)
                .apply()

            if (newIsDark) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            }
        }
    }

    private fun setupRecyclerView() {
        binding.rvAppUsage.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = appUsageAdapter
        }
    }

    private fun setupManagers() {
        // Inisialisasi Firebase Sync
        firebaseManager = FirebaseSyncManager(ParentApp.TARGET_DEVICE_ID) { summary, isOnline ->
            isChildOnline = isOnline
            updateUiWithDeviceSummary(summary, isOnline)
        }
        firebaseManager.startListening()

        // Inisialisasi Bluetooth Fallback
        bleManager = BleClientManager(this) { isConnected, statusText ->
            isBleConnected = isConnected
            binding.btnConnectBle.text = statusText
            if (isConnected) {
                binding.tvDeviceStatus.text = "Terhubung via Bluetooth (Offline Mode)"
                binding.tvDeviceStatus.setTextColor(ContextCompat.getColor(this, R.color.accent))
                Toast.makeText(this, "Berhasil terhubung ke tablet Leon via Bluetooth!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupControls() {
        // Tombol Hubungkan BLE
        binding.btnConnectBle.setOnClickListener {
            if (isBleConnected) {
                bleManager.disconnect()
            } else {
                checkAndRequestBluetooth()
            }
        }

        // Tombol Kunci Layar Langsung (Lock Now)
        binding.btnLockNow.setOnClickListener {
            dispatchCommand(
                action = "LOCK_NOW",
                bleCode = BleClientManager.CMD_LOCK_NOW
            )
            Toast.makeText(this, "Mengirim perintah kunci layar ke tablet...", Toast.LENGTH_SHORT).show()
        }

        // Tombol Buka Kunci (Menghilangkan Overlay Istirahat)
        binding.btnUnlock.setOnClickListener {
            dispatchCommand(
                action = "HIDE_OVERLAY",
                bleCode = BleClientManager.CMD_HIDE_OVERLAY
            )
            Toast.makeText(this, "Menghilangkan layar istirahat (buka kunci) di tablet...", Toast.LENGTH_SHORT).show()
        }

        // Tombol Tampilkan Layar Istirahat
        binding.btnShowOverlay.setOnClickListener {
            dispatchCommand(
                action = "SHOW_OVERLAY",
                bleCode = BleClientManager.CMD_SHOW_OVERLAY
            )
            Toast.makeText(this, "Menampilkan layar istirahat di tablet Leon...", Toast.LENGTH_SHORT).show()
        }

        // Slider Volume
        binding.sliderVolume.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                val vol = value.toInt()
                binding.tvVolumeLabel.text = getString(R.string.label_volume, vol)
            }
        }
        binding.sliderVolume.addOnSliderTouchListener(object : com.google.android.material.slider.Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: com.google.android.material.slider.Slider) {}
            override fun onStopTrackingTouch(slider: com.google.android.material.slider.Slider) {
                val vol = slider.value.toInt()
                dispatchCommand(
                    action = "SET_VOLUME",
                    param = vol,
                    bleCode = BleClientManager.CMD_SET_VOLUME
                )
            }
        })

        // Slider Brightness
        binding.sliderBrightness.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                val bright = value.toInt()
                binding.tvBrightnessLabel.text = getString(R.string.label_brightness, bright)
            }
        }
        binding.sliderBrightness.addOnSliderTouchListener(object : com.google.android.material.slider.Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: com.google.android.material.slider.Slider) {}
            override fun onStopTrackingTouch(slider: com.google.android.material.slider.Slider) {
                val bright = slider.value.toInt()
                dispatchCommand(
                    action = "SET_BRIGHTNESS",
                    param = bright,
                    bleCode = BleClientManager.CMD_SET_BRIGHTNESS
                )
            }
        })
    }

    /**
     * Mengirim perintah: Prioritas via Bluetooth jika sedang terhubung BLE,
     * jika tidak, kirim lewat Firebase Online.
     */
    private fun dispatchCommand(action: String, param: Int = 0, bleCode: Int) {
        if (isBleConnected) {
            bleManager.sendCommand(bleCode, param)
        } else {
            firebaseManager.sendCommandOnline(action, param) { success, errorMsg ->
                if (!success) {
                    runOnUiThread {
                        Toast.makeText(this, "Gagal kirim via Cloud: $errorMsg (Cek Rules Firebase!)", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun updateUiWithDeviceSummary(summary: DeviceSummary, isOnline: Boolean) {
        runOnUiThread {
            if (!isBleConnected) {
                if (isOnline) {
                    binding.tvDeviceStatus.text = getString(R.string.status_online)
                    binding.tvDeviceStatus.setTextColor(ContextCompat.getColor(this, R.color.success))
                } else {
                    binding.tvDeviceStatus.text = getString(R.string.status_offline)
                    binding.tvDeviceStatus.setTextColor(ContextCompat.getColor(this, R.color.error))
                }
            }

            // Update Durasi YouTube Kids
            val ytMins = summary.youtubeKidsMinutes
            if (ytMins >= 60) {
                val hours = ytMins / 60
                val mins = ytMins % 60
                binding.tvYoutubeKidsDuration.text = getString(R.string.yt_time_format, hours, mins)
            } else {
                binding.tvYoutubeKidsDuration.text = "$ytMins Menit"
            }

            binding.tvTotalScreenTime.text = getString(R.string.total_screen_time_format, summary.totalScreenTimeMinutes)
            binding.pbScreenTime.progress = ytMins.coerceAtMost(120).toInt()

            // Update Volume & Brightness sliders (jika tidak sedang disentuh)
            val step = 5f
            if (!binding.sliderVolume.isPressed) {
                val rawVol = summary.currentVolumePercent.coerceIn(0, 100).toFloat()
                val roundedVol = (Math.round(rawVol / step) * step).coerceIn(0f, 100f)
                binding.sliderVolume.value = roundedVol
                binding.tvVolumeLabel.text = getString(R.string.label_volume, roundedVol.toInt())
            }
            if (!binding.sliderBrightness.isPressed) {
                val rawBright = summary.currentBrightnessPercent.coerceIn(5, 100).toFloat()
                val roundedBright = (Math.round(rawBright / step) * step).coerceIn(5f, 100f)
                binding.sliderBrightness.value = roundedBright
                binding.tvBrightnessLabel.text = getString(R.string.label_brightness, roundedBright.toInt())
            }

            // Update Daftar Aplikasi
            if (summary.appList.isEmpty()) {
                binding.tvEmptyApps.visibility = View.VISIBLE
                binding.rvAppUsage.visibility = View.GONE
            } else {
                binding.tvEmptyApps.visibility = View.GONE
                binding.rvAppUsage.visibility = View.VISIBLE
                appUsageAdapter.submitList(summary.appList)
            }
        }
    }

    private fun checkAndRequestBluetooth() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val hasScan = ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
            val hasConnect = ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

            if (hasScan && hasConnect) {
                bleManager.startScanAndConnect()
            } else {
                bluetoothPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.BLUETOOTH_SCAN,
                        Manifest.permission.BLUETOOTH_CONNECT,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    )
                )
            }
        } else {
            val hasLocation = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            if (hasLocation) {
                bleManager.startScanAndConnect()
            } else {
                bluetoothPermissionLauncher.launch(
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
                )
            }
        }
    }

    override fun onDestroy() {
        firebaseManager.stopListening()
        bleManager.disconnect()
        super.onDestroy()
    }
}
