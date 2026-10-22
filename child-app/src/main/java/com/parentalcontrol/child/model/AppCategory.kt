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

    // Aplikasi chat dihitung "Lainnya": anak perlu bisa menghubungi keluarga, jadi tidak ikut terkena
    // batas Game & Hiburan atau blokir waktu belajar. Android menandainya SOCIAL, makanya perlu daftar ini.
    private val MESSAGING_HINTS = listOf(
        "whatsapp", "telegram", "org.thoughtcrime.securesms", "jp.naver.line", "com.facebook.orca",
        "com.google.android.apps.messaging", "com.google.android.apps.dynamite", "com.samsung.android.messaging",
        "com.android.mms", "com.skype", "com.viber", "com.discord"
    )

    // Media sosial berbasis feed tetap "Hiburan". Aplikasi sosial yang tidak dikenal juga jatuh ke
    // Hiburan lewat kategori Android (default yang lebih ketat).
    private val ENTERTAINMENT_HINTS = listOf(
        "youtube", "netflix", "tiktok", "spotify", "com.zhiliaoapp", "com.ss.android.ugc",
        "com.instagram", "com.facebook.katana", "com.snapchat", "com.twitter", "com.reddit", "com.pinterest"
    )

    fun classify(packageName: String, androidCategory: Int): AppCategory {
        val pkg = packageName.lowercase()
        return when {
            EDUCATION_HINTS.any { pkg.contains(it) } -> AppCategory.EDUCATION
            MESSAGING_HINTS.any { pkg.contains(it) } -> AppCategory.OTHER
            ENTERTAINMENT_HINTS.any { pkg.contains(it) } -> AppCategory.ENTERTAINMENT
            androidCategory == ANDROID_GAME || GAME_HINTS.any { pkg.contains(it) } -> AppCategory.GAME
            androidCategory in listOf(ANDROID_AUDIO, ANDROID_VIDEO, ANDROID_IMAGE, ANDROID_SOCIAL) ->
                AppCategory.ENTERTAINMENT
            else -> AppCategory.OTHER
        }
    }
}
