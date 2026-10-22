package com.parentalcontrol.parent.ui

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.parentalcontrol.parent.analysis.UsageFormat
import com.parentalcontrol.parent.databinding.ItemRecentActivityBinding
import com.parentalcontrol.parent.model.AppUsageItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RecentActivityAdapter : RecyclerView.Adapter<RecentActivityAdapter.ViewHolder>() {

    private val items = mutableListOf<AppUsageItem>()

    fun submitList(newItems: List<AppUsageItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        ItemRecentActivityBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(items[position])

    override fun getItemCount() = items.size

    class ViewHolder(private val binding: ItemRecentActivityBinding) : RecyclerView.ViewHolder(binding.root) {

        private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

        fun bind(item: AppUsageItem) {
            val color = ContextCompat.getColor(binding.root.context, CategoryStyle.color(item.category))
            binding.viewCategoryDot.backgroundTintList = ColorStateList.valueOf(color)
            binding.tvRecentName.text = item.appName

            val lastUsed = if (item.lastTimeUsed > 0) timeFormat.format(Date(item.lastTimeUsed)) else "-"
            binding.tvRecentMeta.text = "Terakhir $lastUsed • ${CategoryStyle.label(item.category)}"
            binding.tvRecentDuration.text = UsageFormat.duration(item.durationMinutes)
        }
    }
}
