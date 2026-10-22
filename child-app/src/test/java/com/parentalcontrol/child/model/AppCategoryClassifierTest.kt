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
    fun `puzzle packages are games`() {
        assertEquals(AppCategory.GAME, AppCategoryClassifier.classify("com.studio.puzzlematch", -1))
    }
}
