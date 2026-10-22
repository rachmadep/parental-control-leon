package com.parentalcontrol.child.model

import org.junit.Assert.assertEquals
import org.junit.Test

class AppCategoryClassifierTest {

    @Test
    fun `known education apps`() {
        assertEquals(AppCategory.EDUCATION, AppCategoryClassifier.classify("com.duolingo", -1))
        assertEquals(AppCategory.EDUCATION, AppCategoryClassifier.classify("com.google.android.apps.classroom", -1))
    }

    @Test
    fun `youtube is entertainment even without android category`() {
        assertEquals(AppCategory.ENTERTAINMENT, AppCategoryClassifier.classify("com.google.android.apps.youtube.kids", -1))
    }

    @Test
    fun `android category falls back when package gives no hint`() {
        assertEquals(AppCategory.GAME, AppCategoryClassifier.classify("com.foo.bar", 0))
        assertEquals(AppCategory.ENTERTAINMENT, AppCategoryClassifier.classify("com.foo.bar", 2))
        assertEquals(AppCategory.OTHER, AppCategoryClassifier.classify("com.android.settings", -1))
    }

    @Test
    fun `messaging apps are other even though android calls them social`() {
        val androidSocial = 4
        assertEquals(AppCategory.OTHER, AppCategoryClassifier.classify("com.whatsapp", androidSocial))
        assertEquals(AppCategory.OTHER, AppCategoryClassifier.classify("com.whatsapp.w4b", androidSocial))
        assertEquals(AppCategory.OTHER, AppCategoryClassifier.classify("org.telegram.messenger", androidSocial))
        assertEquals(AppCategory.OTHER, AppCategoryClassifier.classify("com.facebook.orca", androidSocial))
        assertEquals(AppCategory.OTHER, AppCategoryClassifier.classify("com.google.android.apps.messaging", androidSocial))
    }

    @Test
    fun `feed based social media stays entertainment`() {
        val androidSocial = 4
        assertEquals(AppCategory.ENTERTAINMENT, AppCategoryClassifier.classify("com.instagram.android", androidSocial))
        assertEquals(AppCategory.ENTERTAINMENT, AppCategoryClassifier.classify("com.facebook.katana", androidSocial))
        assertEquals(AppCategory.ENTERTAINMENT, AppCategoryClassifier.classify("com.zhiliaoapp.musically", -1))
        assertEquals(AppCategory.ENTERTAINMENT, AppCategoryClassifier.classify("com.snapchat.android", androidSocial))
    }

    @Test
    fun `unknown social app falls back to entertainment`() {
        assertEquals(AppCategory.ENTERTAINMENT, AppCategoryClassifier.classify("com.some.newsocial", 4))
    }

    @Test
    fun `puzzle packages are games`() {
        assertEquals(AppCategory.GAME, AppCategoryClassifier.classify("com.studio.puzzlematch", -1))
    }
}
