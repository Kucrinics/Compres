package com.kompres.app.analytics

import android.app.Application
import android.content.Context
import com.kompres.app.BuildConfig
import io.appmetrica.analytics.AppMetrica
import io.appmetrica.analytics.AppMetricaConfig
import com.my.tracker.MyTracker

object Analytics {
    private const val PREFS = "kompres_prefs"
    private const val KEY_ENABLED = "analytics_enabled"

    @Volatile
    private var enabled = true

    @Synchronized
    fun init(app: Application) {
        enabled = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, true)
        if (!enabled) return

        try {
            val config = AppMetricaConfig.newConfigBuilder(BuildConfig.APPMETRICA_API_KEY).build()
            AppMetrica.activate(app, config)
        } catch (_: Throwable) {
        }

        try {
            MyTracker.initTracker(BuildConfig.MYTRACKER_SDK_KEY, app)
        } catch (_: Throwable) {
        }
    }

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, value)
            .apply()
        enabled = value
    }

    fun trackEvent(name: String, params: Map<String, String> = emptyMap()) {
        if (!enabled) return
        try {
            AppMetrica.reportEvent(name)
        } catch (_: Throwable) {
        }
        try {
            if (params.isEmpty()) {
                MyTracker.trackEvent(name)
            } else {
                MyTracker.trackEvent(name, params)
            }
        } catch (_: Throwable) {
        }
    }
}
