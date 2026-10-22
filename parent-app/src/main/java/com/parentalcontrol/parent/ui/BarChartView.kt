package com.parentalcontrol.parent.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import androidx.core.content.ContextCompat
import com.parentalcontrol.parent.R

/** Grafik batang sederhana untuk total menit per hari; batang terakhir (hari ini) disorot. */
class BarChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var values: List<Long> = emptyList()
    private var labels: List<String> = emptyList()

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.text_secondary)
        textAlign = Paint.Align.CENTER
        textSize = sp(10f)
    }
    private val rect = RectF()

    fun setData(values: List<Long>, labels: List<String>) {
        this.values = values
        this.labels = labels
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (values.isEmpty()) return

        val labelHeight = sp(14f)
        val chartHeight = height - labelHeight - dp(4f)
        val slot = width.toFloat() / values.size
        val barWidth = (slot * 0.62f).coerceAtMost(dp(28f))
        val maxValue = (values.maxOrNull() ?: 0L).coerceAtLeast(1L)

        // Dengan banyak batang (30 hari) label ditampilkan jarang agar tidak bertumpuk
        val labelStep = if (values.size > 10) 5 else 1

        values.forEachIndexed { index, value ->
            val cx = slot * index + slot / 2f
            val isToday = index == values.lastIndex
            barPaint.color = ContextCompat.getColor(context, R.color.primary)
            barPaint.alpha = if (isToday) 255 else 90
            val barHeight = (value.toFloat() / maxValue * chartHeight).coerceAtLeast(if (value > 0) dp(3f) else dp(2f))
            rect.set(cx - barWidth / 2f, chartHeight - barHeight, cx + barWidth / 2f, chartHeight)
            canvas.drawRoundRect(rect, dp(3f), dp(3f), barPaint)

            val fromEnd = values.lastIndex - index
            if (fromEnd % labelStep == 0 && index < labels.size) {
                canvas.drawText(labels[index], cx, height.toFloat() - dp(2f), labelPaint)
            }
        }
    }

    private fun dp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)
    private fun sp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, resources.displayMetrics)
}
