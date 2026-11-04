package com.kompres.app

import android.app.Application
import com.kompres.app.analytics.Analytics
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

class ArchiveApp : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            PDFBoxResourceLoader.init(this)
        } catch (_: Throwable) {
        }
        Analytics.init(this)
        Analytics.trackEvent("app_open")
    }
}
