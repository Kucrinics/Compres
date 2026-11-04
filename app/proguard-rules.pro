# MyTracker
-keep class com.my.tracker.** { *; }
-keep class com.my.tracker.obfuscated.** { *; }
-dontwarn com.my.tracker.**

# AppMetrica
-keep class io.appmetrica.analytics.** { *; }
-dontwarn io.appmetrica.analytics.**

# AndroidPdfViewer / Pdfium
-keep class com.shockwave.** { *; }
-dontwarn com.shockwave.**

# Tesseract4Android
-keep class com.googlecode.tesseract.** { *; }
-dontwarn com.googlecode.tesseract.**

# PDFBox-Android
-keep class com.tom_roush.** { *; }
-dontwarn com.tom_roush.**
