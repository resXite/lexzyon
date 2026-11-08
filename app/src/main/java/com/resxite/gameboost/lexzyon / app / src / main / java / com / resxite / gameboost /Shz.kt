package com.resxite.gameboost

import android.content.Context
import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

object Shz {
    const val PKG = "moe.shizuku.privileged.api"

    fun installed(ctx: Context): Boolean = try {
        ctx.packageManager.getPackageInfo(PKG, 0)
        true
    } catch (e: Throwable) {
        false
    }

    fun running(): Boolean = try {
        Shizuku.pingBinder()
    } catch (e: Throwable) {
        false
    }

    fun ready(): Boolean = try {
        Shizuku.pingBinder() && !Shizuku.isPreV11() &&
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (e: Throwable) {
        false
    }

    // 0 belum terpasang, 1 belum dijalankan, 2 belum diberi izin, 3 siap
    fun state(ctx: Context): Int = when {
        ready() -> 3
        running() -> 2
        installed(ctx) -> 1
        else -> 0
    }

    fun request() {
        try {
            Shizuku.requestPermission(1001)
        } catch (e: Throwable) {
        }
    }

    fun run(cmd: String): String = try {
        val m = Shizuku::class.java.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java
        )
        m.isAccessible = true
        val p = m.invoke(null, arrayOf("sh", "-c", cmd), null, null) as Process
        val out = p.inputStream.bufferedReader().readText()
        p.waitFor()
        out
    } catch (e: Throwable) {
        ""
    }
}
