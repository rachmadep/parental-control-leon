package com.parentalcontrol.parent.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.parentalcontrol.parent.R
import com.parentalcontrol.parent.databinding.ItemAppUsageBinding
import com.parentalcontrol.parent.model.AppUsageItem

class AppUsageAdapter : RecyclerView.Adapter<AppUsageAdapter.ViewHolder>() {

    private val items = mutableListOf<AppUsageItem>()

    fun submitList(newItems: List<AppUsageItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAppUsageBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    class ViewHolder(private val binding: ItemAppUsageBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: AppUsageItem) {
            val context = binding.root.context
            binding.tvAppName.text = item.appName
            binding.tvPackageName.text = item.packageName

            val durationText = if (item.durationMinutes >= 60) {
                val hours = item.durationMinutes / 60
                val mins = item.durationMinutes % 60
                "$hours Jam $mins Menit"
            } else {
                "${item.durationMinutes} Menit"
            }
            binding.tvAppDuration.text = durationText

            // Highlight khusus YouTube Kids
            if (item.isYoutubeKids) {
                binding.tvAppName.setTextColor(ContextCompat.getColor(context, R.color.yt_kids_red))
                binding.tvAppDuration.setTextColor(ContextCompat.getColor(context, R.color.yt_kids_red))
            } else {
                binding.tvAppName.setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                binding.tvAppDuration.setTextColor(ContextCompat.getColor(context, R.color.primary))
            }
        }
    }
}
