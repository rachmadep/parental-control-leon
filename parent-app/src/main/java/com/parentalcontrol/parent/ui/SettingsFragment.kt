package com.parentalcontrol.parent.ui

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.parentalcontrol.parent.ParentApp
import com.parentalcontrol.parent.R
import com.parentalcontrol.parent.databinding.FragmentSettingsBinding

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ParentViewModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val prefs = requireContext().getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
        binding.switchDarkMode.isChecked = prefs.getBoolean(MainActivity.KEY_DARK_MODE, false)
        binding.switchDarkMode.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(MainActivity.KEY_DARK_MODE, isChecked).apply()
            AppCompatDelegate.setDefaultNightMode(
                if (isChecked) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
            )
        }

        binding.tvSettingsDevice.text = "Device ID: ${ParentApp.TARGET_DEVICE_ID}"
        @Suppress("DEPRECATION")
        val versionName = requireContext().packageManager
            .getPackageInfo(requireContext().packageName, 0).versionName
        binding.tvSettingsVersion.text = "Versi aplikasi $versionName"

        viewModel.device.observe(viewLifecycleOwner) { renderStatus() }
        viewModel.bleConnected.observe(viewLifecycleOwner) { renderStatus() }
    }

    private fun renderStatus() {
        val ctx = requireContext()
        val online = viewModel.device.value?.isOnline == true
        val (text, color) = when {
            viewModel.bleConnected.value == true -> "Terhubung via Bluetooth" to R.color.accent
            online -> getString(R.string.status_online) to R.color.success
            else -> getString(R.string.status_offline) to R.color.error
        }
        binding.tvSettingsStatus.text = text
        binding.tvSettingsStatus.setTextColor(ContextCompat.getColor(ctx, color))
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
