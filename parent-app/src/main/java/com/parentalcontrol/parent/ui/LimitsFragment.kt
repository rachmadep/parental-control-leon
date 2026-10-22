package com.parentalcontrol.parent.ui

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.Slider
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.parentalcontrol.parent.R
import com.parentalcontrol.parent.analysis.ScheduleFormat
import com.parentalcontrol.parent.analysis.UsageFormat
import com.parentalcontrol.parent.databinding.DialogScheduleEditBinding
import com.parentalcontrol.parent.databinding.DialogSliderBinding
import com.parentalcontrol.parent.databinding.FragmentLimitsBinding
import com.parentalcontrol.parent.databinding.ItemLimitRuleBinding
import com.parentalcontrol.parent.model.DeviceSummary
import com.parentalcontrol.parent.model.LimitRule
import com.parentalcontrol.parent.model.LimitsConfig
import com.parentalcontrol.parent.model.TimeWindow

class LimitsFragment : Fragment() {

    private var _binding: FragmentLimitsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ParentViewModel by activityViewModels()

    // true selama render() mengisi switch agar listener tidak menyimpan balik nilai yang baru dibaca
    private var isRendering = false

    private val config: LimitsConfig get() = viewModel.config.value ?: LimitsConfig()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentLimitsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.switchDaily.setOnCheckedChangeListener { _, checked ->
            if (!isRendering) viewModel.saveConfig(config.copy(dailyLimitEnabled = checked))
        }
        binding.btnAdd15.setOnClickListener { viewModel.grantBonus(15) }
        binding.btnAdd30.setOnClickListener { viewModel.grantBonus(30) }
        binding.btnEditLimit.setOnClickListener {
            showMinutesDialog(
                title = "Batas layar harian",
                current = config.dailyLimitMinutes,
                min = 15, max = 480, step = 15, zeroLabel = null
            ) { minutes -> viewModel.saveConfig(config.copy(dailyLimitMinutes = minutes)) }
        }

        binding.switchBedtime.setOnCheckedChangeListener { _, checked ->
            if (!isRendering) viewModel.saveConfig(config.copy(bedtime = config.bedtime.copy(enabled = checked)))
        }
        binding.switchStudy.setOnCheckedChangeListener { _, checked ->
            if (!isRendering) viewModel.saveConfig(config.copy(study = config.study.copy(enabled = checked)))
        }
        binding.rowBedtime.setOnClickListener {
            showScheduleDialog("Jam Tidur Malam", config.bedtime) { viewModel.saveConfig(config.copy(bedtime = it)) }
        }
        binding.rowStudy.setOnClickListener {
            showScheduleDialog("Waktu Belajar Mandiri", config.study) { viewModel.saveConfig(config.copy(study = it)) }
        }

        viewModel.config.observe(viewLifecycleOwner) { render() }
        viewModel.device.observe(viewLifecycleOwner) { render() }
    }

    private fun render() {
        val config = config
        val summary = viewModel.device.value?.summary ?: DeviceSummary()
        val today = ParentViewModel.todayKey()

        isRendering = true
        renderDaily(config, summary, today)
        renderSchedules(config)
        renderRules(config, summary)
        isRendering = false
    }

    private fun renderDaily(config: LimitsConfig, summary: DeviceSummary, today: String) {
        val ctx = requireContext()
        val limit = config.effectiveDailyMinutes(today).toLong().coerceAtLeast(1L)
        val used = summary.limitUsedMinutes
        val remaining = limit - used
        val ratio = (used * 100 / limit).toInt().coerceIn(0, 100)

        binding.switchDaily.isChecked = config.dailyLimitEnabled
        binding.progressRing.setProgressCompat(ratio, true)
        binding.progressRing.setIndicatorColor(
            ContextCompat.getColor(
                ctx, when {
                    remaining <= 0 -> R.color.error
                    ratio >= 85 -> R.color.warning
                    else -> R.color.primary
                }
            )
        )
        binding.tvRingLimit.text = UsageFormat.duration(limit)
        binding.tvRingUsed.text = "Terpakai ${UsageFormat.duration(used)}"
        binding.tvRemaining.text = when {
            !config.dailyLimitEnabled -> "Batas harian nonaktif"
            remaining > 0 -> "${UsageFormat.duration(remaining)} tersisa"
            else -> "Batas hari ini tercapai"
        }
        binding.tvBonusInfo.text = if (config.bonusDate == today && config.bonusMinutes > 0) {
            "Termasuk bonus +${config.bonusMinutes} menit hari ini"
        } else {
            ""
        }

        val alpha = if (config.dailyLimitEnabled) 1f else 0.45f
        binding.progressRing.alpha = alpha
        binding.btnAdd15.isEnabled = config.dailyLimitEnabled
        binding.btnAdd30.isEnabled = config.dailyLimitEnabled
    }

    private fun renderSchedules(config: LimitsConfig) {
        binding.switchBedtime.isChecked = config.bedtime.enabled
        binding.tvBedtimeSub.text = ScheduleFormat.summary(config.bedtime)
        binding.switchStudy.isChecked = config.study.enabled
        binding.tvStudySub.text = ScheduleFormat.summary(config.study)
    }

    private fun renderRules(config: LimitsConfig, summary: DeviceSummary) {
        val ctx = requireContext()
        binding.llRules.removeAllViews()

        for (rule in config.appLimits) {
            val row = ItemLimitRuleBinding.inflate(layoutInflater, binding.llRules, false)
            val used = usedMinutes(rule, summary)

            row.tvRuleName.text = rule.label
            row.tvRuleLimit.text = if (rule.minutes > 0) {
                "Batas ${UsageFormat.duration(rule.minutes.toLong())} per hari"
            } else {
                "Tanpa batas"
            }

            val statusColor: Int
            if (rule.minutes > 0) {
                val remaining = rule.minutes - used
                row.tvRuleStatus.text = if (remaining > 0) "Sisa ${UsageFormat.duration(remaining)}" else "Terlampaui"
                statusColor = if (remaining > 0) R.color.success else R.color.error
                row.progressRule.setProgressCompat((used * 100 / rule.minutes).toInt().coerceIn(0, 100), false)
            } else {
                row.tvRuleStatus.text = "Terpakai ${UsageFormat.duration(used)}"
                statusColor = R.color.text_secondary
                row.progressRule.setProgressCompat(0, false)
            }
            row.tvRuleStatus.setTextColor(ContextCompat.getColor(ctx, statusColor))
            row.progressRule.setIndicatorColor(ContextCompat.getColor(ctx, statusColor))

            row.root.setOnClickListener {
                showMinutesDialog(
                    title = "Batas ${rule.label}",
                    current = rule.minutes,
                    min = 0, max = 240, step = 5, zeroLabel = "Tanpa batas"
                ) { minutes ->
                    val updated = this.config.appLimits.map { if (it.id == rule.id) it.copy(minutes = minutes) else it }
                    viewModel.saveConfig(this.config.copy(appLimits = updated))
                }
            }
            binding.llRules.addView(row.root)
        }
    }

    private fun usedMinutes(rule: LimitRule, summary: DeviceSummary): Long =
        if (rule.type == LimitRule.TYPE_PACKAGE) {
            summary.appList.firstOrNull { it.packageName == rule.target }?.durationMinutes ?: 0L
        } else {
            rule.target.split(',').map { it.trim() }.filter { it.isNotEmpty() }
                .sumOf { summary.categories[it] ?: 0L }
        }

    private fun showMinutesDialog(
        title: String,
        current: Int,
        min: Int,
        max: Int,
        step: Int,
        zeroLabel: String?,
        onSave: (Int) -> Unit
    ) {
        val dialogBinding = DialogSliderBinding.inflate(layoutInflater)
        // Slider menolak nilai yang tidak sejajar dengan stepSize, jadi bulatkan dulu
        val aligned = (min + ((current - min + step / 2) / step) * step).coerceIn(min, max)

        fun format(value: Int) =
            if (value == 0 && zeroLabel != null) zeroLabel else UsageFormat.duration(value.toLong())

        dialogBinding.dialogSlider.apply {
            valueFrom = min.toFloat()
            valueTo = max.toFloat()
            stepSize = step.toFloat()
            value = aligned.toFloat()
            addOnChangeListener { _, v, _ -> dialogBinding.tvSliderValue.text = format(v.toInt()) }
        }
        dialogBinding.tvSliderValue.text = format(aligned)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setView(dialogBinding.root)
            .setPositiveButton("Simpan") { _, _ -> onSave(dialogBinding.dialogSlider.value.toInt()) }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun showScheduleDialog(title: String, window: TimeWindow, onSave: (TimeWindow) -> Unit) {
        val dialogBinding = DialogScheduleEditBinding.inflate(layoutInflater)
        var start = window.start
        var end = window.end

        fun refreshTimes() {
            dialogBinding.btnStartTime.text = "Mulai  $start"
            dialogBinding.btnEndTime.text = "Selesai  $end"
        }
        refreshTimes()

        fun pickTime(current: String, label: String, onPicked: (String) -> Unit) {
            val (h, m) = current.split(':').let { (it.getOrNull(0)?.toIntOrNull() ?: 0) to (it.getOrNull(1)?.toIntOrNull() ?: 0) }
            val picker = MaterialTimePicker.Builder()
                .setTimeFormat(TimeFormat.CLOCK_24H)
                .setHour(h)
                .setMinute(m)
                .setTitleText(label)
                .build()
            picker.addOnPositiveButtonClickListener {
                onPicked("%02d:%02d".format(picker.hour, picker.minute))
                refreshTimes()
            }
            picker.show(childFragmentManager, "timePicker")
        }
        dialogBinding.btnStartTime.setOnClickListener { pickTime(start, "Jam mulai") { start = it } }
        dialogBinding.btnEndTime.setOnClickListener { pickTime(end, "Jam selesai") { end = it } }

        val selected = window.days.toSet().ifEmpty { ScheduleFormat.DAY_ORDER.toSet() }
        val chips = ScheduleFormat.DAY_ORDER.associateWith { day ->
            (layoutInflater.inflate(R.layout.chip_day, dialogBinding.chipGroupDays, false) as Chip).apply {
                text = ScheduleFormat.dayName(day)
                isChecked = day in selected
                dialogBinding.chipGroupDays.addView(this)
            }
        }

        val dialog: Dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setView(dialogBinding.root)
            .setPositiveButton("Simpan", null)
            .setNegativeButton("Batal", null)
            .show()

        // Tombol Simpan ditangani manual agar dialog tetap terbuka saat input tidak valid
        (dialog as androidx.appcompat.app.AlertDialog).getButton(Dialog.BUTTON_POSITIVE).setOnClickListener {
            val days = chips.filterValues { it.isChecked }.keys.toList()
            when {
                start == end -> toast("Jam mulai dan selesai tidak boleh sama")
                days.isEmpty() -> toast("Pilih minimal satu hari")
                else -> {
                    // Semua hari terpilih disimpan sebagai daftar kosong = setiap hari
                    onSave(window.copy(start = start, end = end, days = if (days.size == 7) emptyList() else days.sorted()))
                    dialog.dismiss()
                }
            }
        }
    }

    private fun toast(message: String) = Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
