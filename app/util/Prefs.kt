package com.gnss.locator.util

import android.content.Context

class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("gnss_config", Context.MODE_PRIVATE)

    var intervalMs: Long
        get() = sp.getLong("interval", 1000L)
        set(v) = sp.edit().putLong("interval", v).apply()
    var sectors: Int
        get() = sp.getInt("sectors", 1)
        set(v) = sp.edit().putInt("sectors", v).apply()
    var minElev: Double
        get() = sp.getFloat("minElev", 10f).toDouble()
        set(v) = sp.edit().putFloat("minElev", v.toFloat()).apply()
    var constellations: Set<String>
        get() = sp.getStringSet("const", setOf("GPS", "BDS", "GAL")) ?: setOf("GPS")
        set(v) = sp.edit().putStringSet("const", v).apply()
    var mode: Int
        get() = sp.getInt("mode", 0)
        set(v) = sp.edit().putInt("mode", v).apply()
    var markerShape: Int
        get() = sp.getInt("shape", 0)
        set(v) = sp.edit().putInt("shape", v).apply()
    var username: String get() = sp.getString("user", "") ?: ""
    var password: String get() = sp.getString("pwd", "") ?: ""
    fun saveUser(u: String, p: String) =
        sp.edit().putString("user", u).putString("pwd", p).apply()
}
