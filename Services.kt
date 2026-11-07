package com.resxite.gameboost

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.RemoteInput
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView

class BoostService : Service() {
    @Volatile
    private var running = false
    private var bubble: View? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val i = intent ?: return START_NOT_STICKY
        when (i.action) {
            "pair" -> {
                fg(1, pairNotif())
                Adb.startPairDiscovery(this)
            }
            "boost" -> startBoost(i.getStringExtra("pkg") ?: "", i.getIntExtra("dpi", 720))
            "restore" -> Thread { doRestore() }.start()
        }
        return START_NOT_STICKY
    }

    private fun fg(id: Int, n: Notification) {
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(id, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(id, n)
        }
    }

    private fun pairNotif(): Notification {
        val ri = RemoteInput.Builder("code").setLabel("Kode 6 digit").build()
        val pi = PendingIntent.getBroadcast(
            this, 0, Intent(this, CodeReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        val act = Notification.Action.Builder(
            Icon.createWithResource(this, android.R.drawable.ic_menu_edit), "Masukkan kode", pi
        ).addRemoteInput(ri).build()
        return Notification.Builder(this, "boost")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("Masukkan kode pairing")
            .setContentText("Ketik 6 digit dari layar Wireless Debugging")
            .setOngoing(true)
            .addAction(act)
            .build()
    }

    private fun boostNotif(title: String, text: String): Notification {
        val pi = PendingIntent.getService(
            this, 1, Intent(this, BoostService::class.java).setAction("restore"),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val act = Notification.Action.Builder(
            Icon.createWithResource(this, android.R.drawable.ic_menu_revert), "Pulihkan", pi
        ).build()
        return Notification.Builder(this, "boost")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .addAction(act)
            .build()
    }

    private fun startBoost(pkg: String, dpi: Int) {
        val base = resources.configuration.densityDpi
        fg(2, boostNotif("Boost aktif", "Density $dpi · ketuk Pulihkan untuk kembali normal"))
        if (pkg.isEmpty()) {
            stopSelf()
            return
        }
        if (running) return
        running = true
        Thread {
            if (!Adb.connected(this)) Adb.connect(this)
            Adb.run(this, "wm density $dpi")
            if (Store.floating && Settings.canDrawOverlays(this)) showBubble(base)
            Adb.run(this, "monkey -p $pkg -c android.intent.category.LAUNCHER 1")
            var seen = false
            var away = 0
            var waited = 0
            while (running) {
                Thread.sleep(1200)
                val out = Adb.run(this, "dumpsys window | grep mCurrentFocus")
                val now = Regex("\\s([\\w.]+)/").find(out)?.groupValues?.get(1)
                if (now == pkg) {
                    seen = true
                    away = 0
                } else if (now != null && !out.contains("systemui")) {
                    if (seen) away++ else waited++
                }
                if ((seen && away >= 4) || (!seen && waited >= 20)) break
            }
            doRestore()
        }.start()
    }

    private fun doRestore() {
        running = false
        if (!Adb.connected(this)) Adb.connect(this)
        Adb.run(this, "wm density reset")
        Handler(Looper.getMainLooper()).post {
            removeBubble()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun showBubble(base: Int) {
        Handler(Looper.getMainLooper()).post {
            fun px(dp: Float) = dp * base / 160f
            val wm = getSystemService(WindowManager::class.java)
            val accent = Store.ACCENTS[Store.theme]
            val tv = TextView(this)
            tv.text = "Pulihkan"
            tv.setTextColor(0xFF08090A.toInt())
            tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(13f))
            val pad = px(12f).toInt()
            tv.setPadding(pad, pad / 2, pad, pad / 2)
            val bg = GradientDrawable()
            bg.cornerRadius = px(18f)
            bg.setColor(accent)
            tv.background = bg
            val lp = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            )
            lp.gravity = Gravity.TOP or Gravity.START
            lp.x = 20
            lp.y = px(120f).toInt()
            var sx = 0
            var sy = 0
            var tx = 0f
            var ty = 0f
            var moved = false
            tv.setOnTouchListener { v, e ->
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> {
                        sx = lp.x
                        sy = lp.y
                        tx = e.rawX
                        ty = e.rawY
                        moved = false
                    }
                    MotionEvent.ACTION_MOVE -> {
                        lp.x = sx + (e.rawX - tx).toInt()
                        lp.y = sy + (e.rawY - ty).toInt()
                        if (Math.abs(e.rawX - tx) > 10 || Math.abs(e.rawY - ty) > 10) moved = true
                        wm.updateViewLayout(v, lp)
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!moved) Thread { doRestore() }.start()
                    }
                }
                true
            }
            try {
                wm.addView(tv, lp)
                bubble = tv
            } catch (e: Throwable) {
            }
        }
    }

    private fun removeBubble() {
        val v = bubble ?: return
        try {
            getSystemService(WindowManager::class.java).removeView(v)
        } catch (e: Throwable) {
        }
        bubble = null
    }
}

class CodeReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val code = RemoteInput.getResultsFromIntent(i)?.getCharSequence("code")?.toString()?.trim().orEmpty()
        val pending = goAsync()
        Thread {
            val port = Adb.pairingPort
            var ok = false
            if (code.length == 6 && port > 0) {
                ok = Adb.pair(c, port, code)
                if (ok) {
                    Thread.sleep(1500)
                    ok = Adb.connect(c)
                }
            }
            if (ok) Store.onboarded = true
            Adb.stopPairDiscovery()
            val text = when {
                ok -> "ADB siap dipakai"
                port <= 0 -> "Buka layar kode pairing dulu, lalu mulai pairing lagi"
                else -> "Kode salah atau koneksi gagal, mulai pairing lagi"
            }
            val n = Notification.Builder(c, "boost")
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setContentTitle(if (ok) "Terhubung" else "Pairing gagal")
                .setContentText(text)
                .setAutoCancel(true)
                .build()
            c.getSystemService(NotificationManager::class.java).notify(3, n)
            c.stopService(Intent(c, BoostService::class.java))
            pending.finish()
        }.start()
    }
}
