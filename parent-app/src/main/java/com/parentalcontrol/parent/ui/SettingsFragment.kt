package com.parentalcontrol.parent.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.parentalcontrol.parent.ParentApp
import com.parentalcontrol.parent.R
import com.parentalcontrol.parent.analysis.PinChange
import com.parentalcontrol.parent.analysis.RelativeTime
import com.parentalcontrol.parent.databinding.DialogChangePinBinding
import com.parentalcontrol.parent.databinding.FragmentSettingsBinding

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ParentViewModel by activityViewModels()

    // true selama kode mengisi switch agar listener tidak menyimpan balik nilai yang baru dibaca
    private var isRendering = false

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            prefs().edit().putBoolean(MainActivity.KEY_NOTIFICATIONS, true).apply()
        } else {
            setSwitch(binding.switchNotify, false)
            toast("Izin notifikasi dibutuhkan untuk menampilkan peringatan.")
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        setupLocalPreferences()
        setupDeviceSettings()

        binding.tvSettingsDevice.text = "Device ID: ${ParentApp.TARGET_DEVICE_ID}"
        @Suppress("DEPRECATION")
        val versionName = requireContext().packageManager
            .getPackageInfo(requireContext().packageName, 0).versionName
        binding.tvSettingsVersion.text = "Versi aplikasi $versionName"

        binding.rowChangePin.setOnClickListener { showChangePinDialog() }

        binding.tvAccountEmail.text = FirebaseAuth.getInstance().currentUser?.email ?: "-"
        binding.btnSignOut.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Keluar dari akun?")
                .setMessage("Anda harus masuk lagi untuk memantau dan mengontrol tablet Leon. Tablet tetap menegakkan batas waktu.")
                .setPositiveButton("Keluar") { _, _ ->
                    // MainActivity mengamati sesi dan otomatis kembali ke layar login
                    FirebaseAuth.getInstance().signOut()
                }
                .setNegativeButton("Batal", null)
                .show()
        }

        viewModel.device.observe(viewLifecycleOwner) { renderDevice() }
        viewModel.bleConnected.observe(viewLifecycleOwner) { renderDevice() }
        viewModel.pinCredential.observe(viewLifecycleOwner) { renderPinStatus() }
        viewModel.settings.observe(viewLifecycleOwner) { renderSettings() }
        viewModel.location.observe(viewLifecycleOwner) { renderLocation() }
    }

    /** Pengaturan yang hanya berlaku di HP ini (SharedPreferences). */
    private fun setupLocalPreferences() {
        val prefs = prefs()

        setSwitch(binding.switchDarkMode, prefs.getBoolean(MainActivity.KEY_DARK_MODE, false))
        binding.switchDarkMode.setOnCheckedChangeListener { _, isChecked ->
            if (isRendering) return@setOnCheckedChangeListener
            prefs.edit().putBoolean(MainActivity.KEY_DARK_MODE, isChecked).apply()
            AppCompatDelegate.setDefaultNightMode(
                if (isChecked) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
            )
        }

        setSwitch(binding.switchBiometric, prefs.getBoolean(MainActivity.KEY_BIOMETRIC_LOCK, false))
        binding.switchBiometric.setOnCheckedChangeListener { _, isChecked ->
            if (isRendering) return@setOnCheckedChangeListener
            if (isChecked && BiometricManager.from(requireContext())
                    .canAuthenticate(BIOMETRIC_WEAK or DEVICE_CREDENTIAL) != BiometricManager.BIOMETRIC_SUCCESS
            ) {
                setSwitch(binding.switchBiometric, false)
                toast("HP ini belum punya kunci layar atau biometrik. Atur dulu di pengaturan HP.")
                return@setOnCheckedChangeListener
            }
            prefs.edit().putBoolean(MainActivity.KEY_BIOMETRIC_LOCK, isChecked).apply()
            // Pengguna sudah ada di dalam aplikasi, jangan langsung menguncinya
            viewModel.appUnlockedOnce = true
            viewModel.appLastActiveAt = System.currentTimeMillis()
        }

        setSwitch(binding.switchNotify, prefs.getBoolean(MainActivity.KEY_NOTIFICATIONS, false))
        binding.switchNotify.setOnCheckedChangeListener { _, isChecked ->
            if (isRendering) return@setOnCheckedChangeListener
            if (isChecked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                // Disimpan setelah izin diberikan (lihat notificationPermissionLauncher)
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                prefs.edit().putBoolean(MainActivity.KEY_NOTIFICATIONS, isChecked).apply()
            }
        }
    }

    /** Pengaturan yang dikirim ke tablet lewat Firebase. */
    private fun setupDeviceSettings() {
        binding.switchLocation.setOnCheckedChangeListener { _, isChecked ->
            if (!isRendering) viewModel.setLocationEnabled(isChecked)
        }
        binding.switchUninstall.setOnCheckedChangeListener { _, isChecked ->
            if (!isRendering) viewModel.setUninstallProtection(isChecked)
        }
        binding.btnOpenMaps.setOnClickListener { openMaps() }
    }

    private fun renderDevice() {
        val ctx = requireContext()
        val state = viewModel.device.value ?: return
        val summary = state.summary

        val model = summary.deviceModel.ifBlank { "Perangkat belum melapor" }
        binding.tvProfileDevice.text =
            if (summary.androidVersion.isNotBlank()) "$model • Android ${summary.androidVersion}" else model
        binding.tvProfileBattery.text =
            if (summary.batteryPercent >= 0) "Baterai ${summary.batteryPercent}%" else "Baterai tidak diketahui"

        val (statusText, statusColor) = when {
            viewModel.bleConnected.value == true -> "Terhubung via Bluetooth" to R.color.accent
            state.isOnline -> getString(R.string.status_online) to R.color.success
            else -> getString(R.string.status_offline) to R.color.error
        }
        binding.tvSettingsStatus.text = statusText
        binding.tvSettingsStatus.setTextColor(ContextCompat.getColor(ctx, statusColor))

        if (summary.deviceAdminActive) {
            binding.tvAdminStatus.text = "Device Admin tablet: Aktif ✓"
            binding.tvAdminStatus.setTextColor(ContextCompat.getColor(ctx, R.color.success))
        } else {
            binding.tvAdminStatus.text = "Device Admin tablet: NONAKTIF — kunci layar jarak jauh mati dan aplikasi bisa dihapus"
            binding.tvAdminStatus.setTextColor(ContextCompat.getColor(ctx, R.color.error))
        }
    }

    private fun renderPinStatus() {
        binding.tvPinStatus.text = if (viewModel.pinCredential.value == null) {
            "Masih memakai PIN bawaan (${PinChange.DEFAULT_PIN}). Disarankan diganti."
        } else {
            "PIN kustom aktif. Dipakai untuk membuka layar istirahat di tablet."
        }
    }

    private fun renderSettings() {
        val settings = viewModel.settings.value ?: return
        setSwitch(binding.switchLocation, settings.locationEnabled)
        setSwitch(binding.switchUninstall, settings.uninstallProtection)
        renderLocation()
    }

    private fun renderLocation() {
        val enabled = viewModel.settings.value?.locationEnabled == true
        val location = viewModel.location.value

        if (location == null) {
            binding.tvLocationInfo.text = if (enabled) {
                "Menunggu lokasi dari tablet. Pastikan izin lokasi \"sepanjang waktu\" sudah diberikan di tablet."
            } else {
                "Fitur lokasi mati."
            }
            binding.btnOpenMaps.visibility = View.GONE
            return
        }

        val age = RelativeTime.format(System.currentTimeMillis(), location.timestamp)
        val note = if (enabled) "" else " (fitur dimatikan, ini data terakhir)"
        binding.tvLocationInfo.text = "Terakhir $age, akurasi ±${location.accuracy.toInt()} m$note"
        binding.btnOpenMaps.visibility = View.VISIBLE
    }

    private fun openMaps() {
        val loc = viewModel.location.value ?: return
        val geo = Uri.parse("geo:${loc.lat},${loc.lng}?q=${loc.lat},${loc.lng}(Tablet Leon)")
        try {
            startActivity(Intent(Intent.ACTION_VIEW, geo))
        } catch (e: ActivityNotFoundException) {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://maps.google.com/?q=${loc.lat},${loc.lng}")))
        }
    }

    private fun showChangePinDialog() {
        val dialogBinding = DialogChangePinBinding.inflate(layoutInflater)
        dialogBinding.tvPinHint.text = if (viewModel.pinCredential.value == null) {
            "Jika PIN belum pernah diubah, PIN lama adalah ${PinChange.DEFAULT_PIN}."
        } else {
            "Masukkan PIN yang sekarang berlaku di tablet."
        }

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Ubah PIN Orang Tua")
            .setView(dialogBinding.root)
            .setPositiveButton("Simpan", null)
            .setNegativeButton("Batal", null)
            .show()

        // Ditangani manual agar dialog tetap terbuka saat input tidak valid
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val error = viewModel.changePin(
                dialogBinding.etOldPin.text.toString(),
                dialogBinding.etNewPin.text.toString(),
                dialogBinding.etConfirmPin.text.toString()
            )
            if (error == null) {
                dialog.dismiss()
            } else {
                dialogBinding.tvPinError.text = error
                dialogBinding.tvPinError.visibility = View.VISIBLE
            }
        }
    }

    private fun setSwitch(switch: com.google.android.material.switchmaterial.SwitchMaterial, checked: Boolean) {
        isRendering = true
        switch.isChecked = checked
        isRendering = false
    }

    private fun prefs() = requireContext().getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)

    private fun toast(message: String) = Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
