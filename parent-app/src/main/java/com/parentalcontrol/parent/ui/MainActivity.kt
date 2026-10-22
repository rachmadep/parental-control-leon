package com.parentalcontrol.parent.ui

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.parentalcontrol.parent.R
import com.parentalcontrol.parent.analysis.AppLockPolicy
import com.parentalcontrol.parent.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

/**
 * Shell dengan navigasi bawah 4 tab. Koneksi Firebase/BLE dimiliki [ParentViewModel] bersama,
 * sehingga tiap tab hanya merender dan mengirim perintah. Jika kunci biometrik aktif, isi aplikasi
 * ditutup [ActivityMainBinding.lockCover] sampai autentikasi berhasil.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: ParentViewModel by viewModels()
    private var biometricPromptShowing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        // Terapkan opsi Dark Mode yang disimpan
        val isDarkMode = prefs().getBoolean(KEY_DARK_MODE, false)
        AppCompatDelegate.setDefaultNightMode(
            if (isDarkMode) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        )

        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.bottomNav.setOnItemSelectedListener { item ->
            showTab(item.itemId)
            true
        }
        binding.btnUnlockApp.setOnClickListener { showBiometricPrompt() }
        if (savedInstanceState == null) showTab(R.id.nav_dashboard)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.messages.collect { Toast.makeText(this@MainActivity, it, Toast.LENGTH_LONG).show() }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        checkAppLock()
    }

    override fun onStop() {
        // Jangan perbarui saat sedang terkunci, agar keluar-masuk cepat tidak melewati kunci
        if (!viewModel.appLocked) viewModel.appLastActiveAt = System.currentTimeMillis()
        super.onStop()
    }

    fun selectTab(itemId: Int) {
        binding.bottomNav.selectedItemId = itemId
    }

    private fun showTab(itemId: Int) {
        val fragment: Fragment = when (itemId) {
            R.id.nav_activity -> ActivityFragment()
            R.id.nav_limits -> LimitsFragment()
            R.id.nav_settings -> SettingsFragment()
            else -> DashboardFragment()
        }
        supportFragmentManager.commit {
            setReorderingAllowed(true)
            replace(R.id.fragmentContainer, fragment)
        }
    }

    private fun checkAppLock() {
        val enabled = prefs().getBoolean(KEY_BIOMETRIC_LOCK, false)
        val mustLock = viewModel.appLocked || AppLockPolicy.shouldLock(
            enabled, viewModel.appUnlockedOnce, viewModel.appLastActiveAt, System.currentTimeMillis()
        )
        if (mustLock) {
            viewModel.appLocked = true
            binding.lockCover.visibility = View.VISIBLE
            showBiometricPrompt()
        } else {
            binding.lockCover.visibility = View.GONE
        }
    }

    private fun showBiometricPrompt() {
        if (biometricPromptShowing) return

        val authenticators = BIOMETRIC_WEAK or DEVICE_CREDENTIAL
        if (BiometricManager.from(this).canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
            // Kunci layar HP dihapus: jangan mengunci orang tua dari aplikasinya sendiri
            prefs().edit().putBoolean(KEY_BIOMETRIC_LOCK, false).apply()
            viewModel.appLocked = false
            binding.lockCover.visibility = View.GONE
            Toast.makeText(this, "Kunci aplikasi dimatikan: HP tidak punya kunci layar/biometrik.", Toast.LENGTH_LONG).show()
            return
        }

        biometricPromptShowing = true
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    biometricPromptShowing = false
                    viewModel.appLocked = false
                    viewModel.appUnlockedOnce = true
                    viewModel.appLastActiveAt = System.currentTimeMillis()
                    binding.lockCover.visibility = View.GONE
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    // Dibatalkan/error: cover tetap tampil, tombol "Buka Kunci" untuk mencoba lagi
                    biometricPromptShowing = false
                }
            }
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Buka Leon Parental")
                .setSubtitle("Verifikasi untuk mengakses kontrol tablet Leon")
                .setAllowedAuthenticators(authenticators)
                .build()
        )
    }

    private fun prefs() = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        const val PREFS_NAME = "parent_prefs"
        const val KEY_DARK_MODE = "dark_mode"
        const val KEY_NOTIFICATIONS = "notifications_enabled"
        const val KEY_BIOMETRIC_LOCK = "biometric_lock"
        const val KEY_LAST_ALERT_TS = "last_alert_ts"
    }
}
