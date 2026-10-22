package com.parentalcontrol.parent.ui

import androidx.annotation.ColorRes
import com.parentalcontrol.parent.R
import com.parentalcontrol.parent.model.CategoryKeys

object CategoryStyle {

    fun label(key: String): String = when (key) {
        CategoryKeys.EDUCATION -> "Edukasi"
        CategoryKeys.ENTERTAINMENT -> "Hiburan"
        CategoryKeys.GAME -> "Game"
        else -> "Lainnya"
    }

    @ColorRes
    fun color(key: String): Int = when (key) {
        CategoryKeys.EDUCATION -> R.color.cat_edukasi
        CategoryKeys.ENTERTAINMENT -> R.color.cat_hiburan
        CategoryKeys.GAME -> R.color.cat_game
        else -> R.color.cat_lainnya
    }
}
