package com.parentalcontrol.parent.ui

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.parentalcontrol.parent.R
import com.parentalcontrol.parent.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

/**
 * Shell dengan navigasi bawah 4 tab. Koneksi Firebase/BLE dimiliki [ParentViewModel] bersama,
 * sehingga tiap tab hanya merender dan mengirim perintah.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: ParentViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // Terapkan opsi Dark Mode yang disimpan
        val isDarkMode = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(KEY_DARK_MODE, false)
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
        if (savedInstanceState == null) showTab(R.id.nav_dashboard)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.messages.collect { Toast.makeText(this@MainActivity, it, Toast.LENGTH_LONG).show() }
            }
        }
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

    companion object {
        const val PREFS_NAME = "parent_prefs"
        const val KEY_DARK_MODE = "dark_mode"
    }
}
