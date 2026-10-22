package com.parentalcontrol.child.model

enum class AppCategory(val key: String) {
    EDUCATION("edukasi"),
    ENTERTAINMENT("hiburan"),
    GAME("game"),
    OTHER("lainnya")
}

object AppCategoryClassifier {

    // Nilai ApplicationInfo.CATEGORY_* (disalin agar logika ini bebas dependensi Android)
    private const val ANDROID_GAME = 0
    private const val ANDROID_AUDIO = 1
    private const val ANDROID_VIDEO = 2
    private const val ANDROID_IMAGE = 3
    private const val ANDROID_SOCIAL = 4

    private val EDUCATION_HINTS = listOf(
        "duolingo", "classroom", "khan", "photomath", "quipper", "ruangguru", "mathway", "edu", "learn"
    )
    private val GAME_HINTS = listOf("game", "puzzle")
    private val ENTERTAINMENT_HINTS = listOf("youtube", "netflix", "tiktok", "spotify")

    fun classify(packageName: String, androidCategory: Int): AppCategory {
        val pkg = packageName.lowercase()
        return when {
            EDUCATION_HINTS.any { pkg.contains(it) } -> AppCategory.EDUCATION
            ENTERTAINMENT_HINTS.any { pkg.contains(it) } -> AppCategory.ENTERTAINMENT
            androidCategory == ANDROID_GAME || GAME_HINTS.any { pkg.contains(it) } -> AppCategory.GAME
            androidCategory in listOf(ANDROID_AUDIO, ANDROID_VIDEO, ANDROID_IMAGE, ANDROID_SOCIAL) ->
                AppCategory.ENTERTAINMENT
            else -> AppCategory.OTHER
        }
    }
}
