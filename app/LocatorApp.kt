package com.gnss.locator

import android.app.Application
import com.gnss.locator.gnss.GnssService
import com.gnss.locator.util.Prefs

class LocatorApp : Application() {
    companion object { var gnss: GnssService? = null }

    override fun onCreate() {
        super.onCreate()
        val prefs = Prefs(this)
        gnss = GnssService(this).apply {
            intervalMs = prefs.intervalMs
            minElevationDeg = prefs.minElev
            enabledConstellations = prefs.constellations
            sectors = prefs.sectors
            mode = prefs.mode
        }
    }
}
