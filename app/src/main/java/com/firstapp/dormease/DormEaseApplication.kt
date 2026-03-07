package com.firstapp.dormease

// FILE PATH: app/src/main/java/com/firstapp/dormease/DormEaseApplication.kt

import android.app.Application
import com.firstapp.dormease.utils.NotificationHelper

/**
 * Custom Application class.
 * Creates the notification channel on app startup.
 *
 * ⚠️ You MUST register this in AndroidManifest.xml:
 *    android:name=".DormEaseApplication"  on the <application> tag
 */
class DormEaseApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannel(this)
    }
}