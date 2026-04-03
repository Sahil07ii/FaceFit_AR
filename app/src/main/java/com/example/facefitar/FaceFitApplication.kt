package com.example.facefitar

import android.app.Application

class FaceFitApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize Firebase and other global components if needed manually
        // Firebase is typically auto-initialized via the google-services Gradle plugin and content provider.
    }
}
