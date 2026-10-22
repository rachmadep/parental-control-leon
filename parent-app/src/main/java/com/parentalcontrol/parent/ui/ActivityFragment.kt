package com.parentalcontrol.parent.ui

import android.graphics.Typeface
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.parentalcontrol.parent.R
import com.parentalcontrol.parent.analysis.Insight
import com.parentalcontrol.parent.analysis.InsightLevel
import com.parentalcontrol.parent.analysis.UsageAnalysis
import com.parentalcontrol.parent.analysis.UsageFormat
import com.parentalcontrol.parent.databinding.FragmentActivityBinding
import com.parentalcontrol.parent.model.CategoryKeys
import com.parentalcontrol.parent.model.LimitsConfig
import java.text.SimpleDateFormat
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.Date
import java.util.Locale

class ActivityFragment : Fragment() {

    private var _binding: FragmentActivityBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ParentViewModel by activityViewModels()
    private val recentAdapter = RecentActivityAdapter()

    private var rangeDays = 1

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentActivityBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.rvRecent.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = recentAdapter
        }
        binding.tvActivityDate.text = "Hari ini, " +
            SimpleDateFormat("d MMM yyyy", Locale("id", "ID")).format(Date())

        binding.toggleRange.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            rangeDays = when (checkedId) {
                R.id.btnRange7 -> 7
                R.id.btnRange30 -> 30
                else -> 1
            }
            render()
        }

        viewModel.device.observe(viewLifecycleOwner) { render() }
        viewModel.history.observe(viewLifecycleOwner) { render() }
        viewModel.config.observe(viewLifecycleOwner) { render() }
    }

    private fun render() {
        val state = viewModel.device.value ?: return
        val config = viewModel.config.value ?: LimitsConfig()
        val summary = state.summary
        val todayKey = ParentViewModel.todayKey()

        val history = UsageAnalysis.withLiveToday(viewModel.history.value.orEmpty(), summary, todayKey)
        val stats = UsageAnalysis.stats(UsageAnalysis.lastDays(history, rangeDays, todayKey))

        binding.tvTotalLabel.text = when (rangeDays) {
            1 -> "Total Waktu Layar Hari Ini"
            else -> "Total Waktu Layar $rangeDays Hari Terakhir"
        }
        binding.tvTotalTime.text = UsageFormat.duration(stats.totalMinutes)
        binding.tvTotalSub.text = when {
            rangeDays > 1 -> "Rata-rata ${UsageFormat.duration(stats.averagePerDay)} per hari (${stats.daysWithData} hari berdata)"
            config.dailyLimitEnabled -> "Batas harian ${UsageFormat.duration(config.effectiveDailyMinutes(todayKey).toLong())}"
            else -> ""
        }

        renderChart(history, todayKey)
        renderCategories(stats.categories)
        renderInsights(UsageAnalysis.buildInsights(history, summary, config, todayKey))

        val recent = summary.appList.sortedByDescending { it.lastTimeUsed }.take(RECENT_COUNT)
        binding.tvEmptyRecent.visibility = if (recent.isEmpty()) View.VISIBLE else View.GONE
        binding.rvRecent.visibility = if (recent.isEmpty()) View.GONE else View.VISIBLE
        recentAdapter.submitList(recent)
    }

    private fun renderChart(history: List<com.parentalcontrol.parent.model.DayHistory>, todayKey: String) {
        if (rangeDays == 1) {
            binding.barChart.visibility = View.GONE
            return
        }
        val totals = UsageAnalysis.dailyTotals(history, rangeDays, todayKey)
        val labels = totals.map { (date, _) ->
            val d = LocalDate.parse(date)
            if (rangeDays <= 7) dayInitial(d.dayOfWeek) else d.dayOfMonth.toString()
        }
        binding.barChart.setData(totals.map { it.second }, labels)
        binding.barChart.visibility = View.VISIBLE
    }

    private fun renderCategories(categories: Map<String, Long>) {
        val ctx = requireContext()
        val bar = binding.llCategoryBar
        bar.removeAllViews()

        val total = categories.values.sum()
        if (total == 0L) {
            bar.addView(segment(R.color.card_stroke, 1f))
        } else {
            for (key in CategoryKeys.ALL) {
                val minutes = categories[key] ?: 0L
                if (minutes > 0) bar.addView(segment(CategoryStyle.color(key), minutes.toFloat()))
            }
        }

        fun legend(view: TextView, key: String) {
            val color = ContextCompat.getColor(ctx, CategoryStyle.color(key))
            val text = SpannableStringBuilder("● ")
            text.setSpan(ForegroundColorSpan(color), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            text.append("${CategoryStyle.label(key)}  ${UsageFormat.duration(categories[key] ?: 0L)}")
            view.text = text
        }
        legend(binding.tvLegendEdu, CategoryKeys.EDUCATION)
        legend(binding.tvLegendFun, CategoryKeys.ENTERTAINMENT)
        legend(binding.tvLegendGame, CategoryKeys.GAME)
        legend(binding.tvLegendOther, CategoryKeys.OTHER)
    }

    private fun segment(colorRes: Int, weight: Float) = View(requireContext()).apply {
        setBackgroundColor(ContextCompat.getColor(requireContext(), colorRes))
        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, weight)
    }

    private fun renderInsights(insights: List<Insight>) {
        val ctx = requireContext()
        binding.llInsights.removeAllViews()
        binding.tvNoInsights.visibility = if (insights.isEmpty()) View.VISIBLE else View.GONE

        for (insight in insights) {
            val titleColor = when (insight.level) {
                InsightLevel.WARNING -> R.color.warning
                InsightLevel.OK -> R.color.success
                InsightLevel.INFO -> R.color.text_primary
            }
            val title = TextView(ctx).apply {
                text = insight.title
                setTextColor(ContextCompat.getColor(ctx, titleColor))
                textSize = 14f
                setTypeface(typeface, Typeface.BOLD)
            }
            val detail = TextView(ctx).apply {
                text = insight.detail
                setTextColor(ContextCompat.getColor(ctx, R.color.text_secondary))
                textSize = 12f
            }
            binding.llInsights.addView(LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, dp(10), 0, 0)
                addView(title)
                addView(detail)
            })
        }
    }

    private fun dayInitial(day: DayOfWeek) = when (day) {
        DayOfWeek.MONDAY -> "Sen"
        DayOfWeek.TUESDAY -> "Sel"
        DayOfWeek.WEDNESDAY -> "Rab"
        DayOfWeek.THURSDAY -> "Kam"
        DayOfWeek.FRIDAY -> "Jum"
        DayOfWeek.SATURDAY -> "Sab"
        DayOfWeek.SUNDAY -> "Min"
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        private const val RECENT_COUNT = 10
    }
}
