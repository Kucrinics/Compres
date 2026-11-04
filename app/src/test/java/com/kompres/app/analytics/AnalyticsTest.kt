package com.kompres.app.analytics

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AnalyticsTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun enabledByDefault() {
        assertTrue(Analytics.isEnabled(context))
    }

    @Test
    fun toggleIsPersisted() {
        Analytics.setEnabled(context, false)
        assertFalse(Analytics.isEnabled(context))

        Analytics.setEnabled(context, true)
        assertTrue(Analytics.isEnabled(context))
    }
}
