package com.parentalcontrol.parent.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.slider.Slider
import com.parentalcontrol.parent.R
import com.parentalcontrol.parent.analysis.UsageFormat
import com.parentalcontrol.parent.ble.BleClientManager
import com.parentalcontrol.parent.databinding.FragmentDashboardBinding
import com.parentalcontrol.parent.model.DeviceSummary
import com.parentalcontrol.parent.model.LimitRule
import com.parentalcontrol.parent.model.LimitsConfig

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ParentViewModel by activityViewModels()
    private val appUsageAdapter = AppUsageAdapter()

    private val bluetoothPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.entries.all { it.value }) {
            viewModel.startBleScan()
        } else {
            Toast.makeText(requireContext(), "Izin Bluetooth dibutuhkan untuk kontrol saat Wi-Fi mati.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.rvAppUsage.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = appUsageAdapter
        }
        binding.tvSeeAllApps.setOnClickListener {
            (requireActivity() as MainActivity).selectTab(R.id.nav_activity)
        }
        setupControls()

        viewModel.device.observe(viewLifecycleOwner) { state ->
            renderStatus()
            renderSummary(state.summary)
            renderYoutubeLimit()
        }
        viewModel.config.observe(viewLifecycleOwner) { renderYoutubeLimit() }
        viewModel.bleConnected.observe(viewLifecycleOwner) { renderStatus() }
        viewModel.bleStatus.observe(viewLifecycleOwner) { binding.btnConnectBle.text = it }
    }

    private fun setupControls() {
        binding.btnConnectBle.setOnClickListener {
            if (viewModel.bleConnected.value == true) {
                viewModel.disconnectBle()
            } else {
                checkAndRequestBluetooth()
            }
        }

        binding.btnLockNow.setOnClickListener {
            viewModel.dispatchCommand("LOCK_NOW", bleCode = BleClientManager.CMD_LOCK_NOW)
            toast("Mengirim perintah kunci layar ke tablet...")
        }
        binding.btnUnlock.setOnClickListener {
            viewModel.dispatchCommand("HIDE_OVERLAY", bleCode = BleClientManager.CMD_HIDE_OVERLAY)
            toast("Menghilangkan layar istirahat (buka kunci) di tablet...")
        }
        binding.btnShowOverlay.setOnClickListener {
            viewModel.dispatchCommand("SHOW_OVERLAY", bleCode = BleClientManager.CMD_SHOW_OVERLAY)
            toast("Menampilkan layar istirahat di tablet Leon...")
        }

        binding.sliderVolume.addOnChangeListener { _, value, fromUser ->
            if (fromUser) binding.tvVolumeLabel.text = getString(R.string.label_volume, value.toInt())
        }
        binding.sliderVolume.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {}
            override fun onStopTrackingTouch(slider: Slider) {
                viewModel.dispatchCommand("SET_VOLUME", slider.value.toInt(), BleClientManager.CMD_SET_VOLUME)
            }
        })

        binding.sliderBrightness.addOnChangeListener { _, value, fromUser ->
            if (fromUser) binding.tvBrightnessLabel.text = getString(R.string.label_brightness, value.toInt())
        }
        binding.sliderBrightness.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {}
            override fun onStopTrackingTouch(slider: Slider) {
                viewModel.dispatchCommand("SET_BRIGHTNESS", slider.value.toInt(), BleClientManager.CMD_SET_BRIGHTNESS)
            }
        })
    }

    private fun renderStatus() {
        val state = viewModel.device.value ?: return
        val ctx = requireContext()
        if (viewModel.bleConnected.value == true) {
            binding.tvDeviceStatus.text = "Terhubung via Bluetooth (Offline Mode)"
            binding.tvDeviceStatus.setTextColor(ContextCompat.getColor(ctx, R.color.accent))
        } else if (state.isOnline) {
            binding.tvDeviceStatus.text = getString(R.string.status_online)
            binding.tvDeviceStatus.setTextColor(ContextCompat.getColor(ctx, R.color.success))
        } else {
            binding.tvDeviceStatus.text = getString(R.string.status_offline)
            binding.tvDeviceStatus.setTextColor(ContextCompat.getColor(ctx, R.color.error))
        }
        binding.tvLockState.visibility = if (state.summary.isLocked) View.VISIBLE else View.GONE
    }

    private fun renderSummary(summary: DeviceSummary) {
        val ytMins = summary.youtubeKidsMinutes
        binding.tvYoutubeKidsDuration.text = if (ytMins >= 60) {
            getString(R.string.yt_time_format, ytMins / 60, ytMins % 60)
        } else {
            "$ytMins Menit"
        }
        binding.tvTotalScreenTime.text = getString(R.string.total_screen_time_format, summary.totalScreenTimeMinutes)

        // Volume & brightness: jangan timpa saat slider sedang disentuh
        val step = 5f
        if (!binding.sliderVolume.isPressed) {
            val rounded = (Math.round(summary.currentVolumePercent.coerceIn(0, 100) / step) * step).coerceIn(0f, 100f)
            binding.sliderVolume.value = rounded
            binding.tvVolumeLabel.text = getString(R.string.label_volume, rounded.toInt())
        }
        if (!binding.sliderBrightness.isPressed) {
            val rounded = (Math.round(summary.currentBrightnessPercent.coerceIn(5, 100) / step) * step).coerceIn(5f, 100f)
            binding.sliderBrightness.value = rounded
            binding.tvBrightnessLabel.text = getString(R.string.label_brightness, rounded.toInt())
        }

        if (summary.appList.isEmpty()) {
            binding.tvEmptyApps.visibility = View.VISIBLE
            binding.rvAppUsage.visibility = View.GONE
        } else {
            binding.tvEmptyApps.visibility = View.GONE
            binding.rvAppUsage.visibility = View.VISIBLE
            appUsageAdapter.submitList(summary.appList.take(DASHBOARD_APP_COUNT))
        }
    }

    /** Progress bar mengikuti batas YouTube Kids dari tab Batas Waktu; tanpa batas, skala 2 jam. */
    private fun renderYoutubeLimit() {
        val summary = viewModel.device.value?.summary ?: return
        val config = viewModel.config.value ?: LimitsConfig()
        val limit = config.appLimits.firstOrNull { it.id == "youtube_kids" || it.target == LimitsConfig.YOUTUBE_KIDS_PACKAGE }
            ?.takeIf { it.type == LimitRule.TYPE_PACKAGE && it.minutes > 0 }?.minutes

        val ytMins = summary.youtubeKidsMinutes
        if (limit != null) {
            binding.pbScreenTime.max = limit
            binding.pbScreenTime.progress = ytMins.coerceAtMost(limit.toLong()).toInt()
            binding.tvLimitCaption.text =
                "${UsageFormat.duration(ytMins)} dari batas ${UsageFormat.duration(limit.toLong())} per hari"
        } else {
            binding.pbScreenTime.max = 120
            binding.pbScreenTime.progress = ytMins.coerceAtMost(120).toInt()
            binding.tvLimitCaption.text = "Belum ada batas YouTube Kids. Atur di tab Batas Waktu."
        }
    }

    private fun checkAndRequestBluetooth() {
        val ctx = requireContext()
        fun granted(permission: String) =
            ContextCompat.checkSelfPermission(ctx, permission) == PackageManager.PERMISSION_GRANTED

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (granted(Manifest.permission.BLUETOOTH_SCAN) && granted(Manifest.permission.BLUETOOTH_CONNECT)) {
                viewModel.startBleScan()
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
            if (granted(Manifest.permission.ACCESS_FINE_LOCATION)) {
                viewModel.startBleScan()
            } else {
                bluetoothPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION))
            }
        }
    }

    private fun toast(message: String) = Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        private const val DASHBOARD_APP_COUNT = 4
    }
}
