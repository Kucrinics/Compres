package com.kompres.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "en")
class LocalizationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val defaultLocale: Locale = Locale.getDefault()

    @After
    fun tearDown() {
        LanguagePrefs.setTag(context, "")
        Locale.setDefault(defaultLocale)
    }

    @Test
    fun savedLanguageIsAppliedByWrap() {
        LanguagePrefs.setTag(context, "de")
        assertEquals("de", LanguagePrefs.currentTag(context))
        val wrapped = LanguagePrefs.wrap(context)
        assertEquals("de", wrapped.resources.configuration.locales[0].language)
    }

    @Test
    fun systemDefaultKeepsOriginalContext() {
        LanguagePrefs.setTag(context, "")
        assertSame(context, LanguagePrefs.wrap(context))
    }
}
