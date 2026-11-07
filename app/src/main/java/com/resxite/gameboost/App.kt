package com.resxite.gameboost

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import org.lsposed.hiddenapibypass.HiddenApiBypass

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Store.init(this)
        try {
            HiddenApiBypass.addHiddenApiExemptions("L")
        } catch (e: Throwable) {
        }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel("boost", "Game Booster", NotificationManager.IMPORTANCE_HIGH)
        )
    }
}
