package com.parentalcontrol.child.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.parentalcontrol.child.R
import com.parentalcontrol.child.databinding.ActivityMainBinding
import com.parentalcontrol.child.manager.AppUsageManager
import com.parentalcontrol.child.manager.DeviceControlManager
import com.parentalcontrol.child.receiver.AdminReceiver
import com.parentalcontrol.child.service.ChildMonitoringService

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var usageManager: AppUsageManager
    private lateinit var controlManager: DeviceControlManager

    private val bluetoothPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        updatePermissionStatus()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        usageManager = AppUsageManager(this)
        controlManager = DeviceControlManager(this)

        val deviceId = "leon_tablet" // Hardcode agar identik dengan ParentApp
        binding.tvDeviceId.text = "Device ID: $deviceId"

        setupClickListeners()
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatus()
    }

    private fun setupClickListeners() {
        // 1. Izin Usage Stats
        binding.btnPermUsage.setOnClickListener {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }

        // 2. Izin Device Administrator
        binding.btnPermAdmin.setOnClickListener {
            val component = ComponentName(this, AdminReceiver::class.java)
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, component)
                putExtra(
                    DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    "Aktifkan agar HP Orang Tua dapat mengunci layar tablet Leon."
                )
            }
            startActivity(intent)
        }

        // 3. Izin Overlay (Tampilkan di atas aplikasi lain)
        binding.btnPermOverlay.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            }
        }

        // 4. Izin Write Settings (Brightness)
        binding.btnPermSettings.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_WRITE_SETTINGS,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            }
        }

        // 5. Izin Bluetooth (BLE)
        binding.btnPermBluetooth.setOnClickListener {
            requestBluetoothPermissions()
        }

        // Tombol Start/Stop Service
        binding.btnToggleService.setOnClickListener {
            if (areAllPermissionsGranted()) {
                startMonitoringService()
                Toast.makeText(this, "Layanan Proteksi Leon Berhasil Dijalankan!", Toast.LENGTH_SHORT).show()
                updatePermissionStatus()
            } else {
                Toast.makeText(this, "Mohon lengkapi semua izin terlebih dahulu!", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun requestBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            bluetoothPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.BLUETOOTH_ADVERTISE,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
            )
        } else {
            bluetoothPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
            )
        }
    }

    private fun updatePermissionStatus() {
        val usageGranted = usageManager.isPermissionGranted()
        val adminGranted = controlManager.isDeviceAdminActive()
        val overlayGranted = controlManager.canDrawOverlays()
        val settingsGranted = controlManager.canWriteSettings()
        val btGranted = isBluetoothPermissionGranted()

        updateButtonState(binding.btnPermUsage, usageGranted)
        updateButtonState(binding.btnPermAdmin, adminGranted)
        updateButtonState(binding.btnPermOverlay, overlayGranted)
        updateButtonState(binding.btnPermSettings, settingsGranted)
        updateButtonState(binding.btnPermBluetooth, btGranted)

        val allGranted = usageGranted && adminGranted && overlayGranted && settingsGranted && btGranted
        if (allGranted) {
            binding.tvDeviceStatus.text = "Status Proteksi: Siap Aktif ✓"
            binding.tvDeviceStatus.setTextColor(ContextCompat.getColor(this, R.color.success))
        } else {
            binding.tvDeviceStatus.text = "Status Proteksi: Menunggu Izin"
            binding.tvDeviceStatus.setTextColor(ContextCompat.getColor(this, R.color.error))
        }
    }

    private fun updateButtonState(button: android.widget.Button, isGranted: Boolean) {
        if (isGranted) {
            button.text = getString(R.string.status_granted)
            button.setTextColor(ContextCompat.getColor(this, R.color.success))
            button.isEnabled = false
        } else {
            button.text = getString(R.string.status_need_action)
            button.setTextColor(ContextCompat.getColor(this, R.color.primary))
            button.isEnabled = true
        }
    }

    private fun areAllPermissionsGranted(): Boolean {
        return usageManager.isPermissionGranted() &&
               controlManager.isDeviceAdminActive() &&
               controlManager.canDrawOverlays() &&
               controlManager.canWriteSettings() &&
               isBluetoothPermissionGranted()
    }

    private fun isBluetoothPermissionGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun startMonitoringService() {
        val intent = Intent(this, ChildMonitoringService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }
}
