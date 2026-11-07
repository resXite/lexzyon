package com.resxite.gameboost

import android.content.Context
import android.content.SharedPreferences

object Store {
    // merah, biru, putih, hijau, ungu, pink
    val ACCENTS = intArrayOf(
        0xFFFF6B6B.toInt(), 0xFF6EC8FF.toInt(), 0xFFF2F4F7.toInt(),
        0xFF7CFFB2.toInt(), 0xFFB49BFF.toInt(), 0xFFFF8FC7.toInt()
    )

    private lateinit var sp: SharedPreferences

    fun init(c: Context) {
        sp = c.getSharedPreferences("gb", Context.MODE_PRIVATE)
    }

    var onboarded: Boolean
        get() = sp.getBoolean("onb", false)
        set(v) { sp.edit().putBoolean("onb", v).apply() }

    var theme: Int
        get() = sp.getInt("theme", 3)
        set(v) { sp.edit().putInt("theme", v).apply() }

    var floating: Boolean
        get() = sp.getBoolean("float", false)
        set(v) { sp.edit().putBoolean("float", v).apply() }

    fun games(): List<Pair<String, Int>> =
        (sp.getString("games", "") ?: "").split("|")
            .filter { it.contains("=") }
            .map { it.substringBefore("=") to (it.substringAfter("=").toIntOrNull() ?: 720) }

    private fun save(m: Map<String, Int>) {
        sp.edit().putString("games", m.entries.joinToString("|") { "${it.key}=${it.value}" }).apply()
    }

    fun setGame(pkg: String, dpi: Int) {
        val m = LinkedHashMap<String, Int>()
        games().forEach { m[it.first] = it.second }
        m[pkg] = dpi
        save(m)
    }

    fun removeGame(pkg: String) {
        val m = LinkedHashMap<String, Int>()
        games().forEach { m[it.first] = it.second }
        m.remove(pkg)
        save(m)
    }
}
