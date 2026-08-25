package com.gnss.locator.gnss

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.GnssMeasurementsEvent
import android.location.GnssNavigationMessage
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import com.gnss.locator.util.CoordinateUtils
import com.gnss.locator.util.Ecef
import com.gnss.locator.util.Llh
import java.util.concurrent.CopyOnWriteArrayList

data class SatObs(
    val constellation: String, val svid: Int, val cn0: Double,
    val pseudorange: Double?, val dopplerHz: Double?, val carrierPhase: Double?,
    val ttna: Long, val clockBiasUsed: Boolean, var valid: Boolean,
    var azimuth: Double? = null, var elevation: Double? = null)

data class FixResult(
    val llh: Llh?, val ecef: Ecef?, val hdop: Double,
    val residuals: List<Double>, val epoch: Long, val valid: Boolean)

class GnssService(private val ctx: Context) {
    interface Listener {
        fun onLocation(lat: Double, lon: Double, alt: Double, speed: Float,
                       hdop: Double, accuracy: Double, utc: Long, valid: Boolean)
        fun onMeasurements(sats: List<SatObs>)
        fun onFixComputed(fix: FixResult, sats: List<SatObs>)
    }

    private val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val listeners = CopyOnWriteArrayList<Listener>()

    var intervalMs = 1000L
    var minElevationDeg = 10.0
    var enabledConstellations = setOf("GPS", "BDS", "GAL")
    var mode = 0
    var sectors = 1

    private val navParser = NavigationMessageParser()
    private val wls = WlsSolver()
    private var lastFixEcef: Ecef? = null
    private var lastLocation: Location? = null
    private val systemAzEl = HashMap<Pair<String, Int>, Pair<Double, Double>>()

    fun addListener(l: Listener) { listeners.add(l) }
    fun removeListener(l: Listener) { listeners.remove(l) }

    private val locCb = object : LocationListener {
        override fun onLocationChanged(loc: Location) {
            lastLocation = loc
            val hdop = loc.extras?.getDouble("hdop")?.takeIf { it > 0 } ?: 0.0
            listeners.forEach { it.onLocation(loc.latitude, loc.longitude, loc.altitude,
                loc.speed, hdop, loc.accuracy.toDouble(), loc.time, loc.accuracy < 50f) }
        }
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    }

    private val statusCb = object : GnssStatus.Callback() {
        override fun onSatelliteStatusReceived(status: GnssStatus) {
            systemAzEl.clear()
            for (i in 0 until status.satelliteCount) {
                systemAzEl[constellationName(status.getConstellationType(i)) to status.getSvid(i)] =
                    status.getAzimuthDegrees(i).toDouble() to status.getElevationDegrees(i).toDouble()
            }
        }
    }

    private val measCb = object : GnssMeasurementsEvent.Callback() {
        override fun onGnssMeasurementsReceived(event: GnssMeasurementsEvent) {
            val clock = event.gnssClock
            val tow = PseudorangeCalculator.towSeconds(clock)
            val sats = event.measurements.mapNotNull { m ->
                val cons = constellationName(m.constellationType)
                if (cons !in enabledConstellations) return@mapNotNull null
                val pr = PseudorangeCalculator.compute(m, clock)
                SatObs(cons, m.svid, m.cn0DbHz.toDouble(), pr,
                    m.pseudorangeRateHzMetersPerSecond.toDouble(),
                    m.accumulatedDeltaRangeMeters, m.receivedSvTimeNanos,
                    pr != null, pr != null && m.cn0DbHz > 20.0)
            }
            listeners.forEach { it.onMeasurements(sats) }
            computeFix(sats, tow)
        }
        override fun onStatusChanged(status: Int) {}
    }

    private val navCb = object : GnssNavigationMessage.Callback() {
        override fun onGnssNavigationMessageReceived(message: GnssNavigationMessage) {
            navParser.feed(message)
        }
        override fun onStatusChanged(status: Int) {}
    }

    fun start() {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) return
        stop()
        try {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, intervalMs, 0f, locCb, Looper.getMainLooper())
            lm.registerGnssStatusCallback(statusCb, Looper.getMainLooper())
            lm.registerGnssMeasurementsCallback(measCb, Looper.getMainLooper())
            @Suppress("DEPRECATION")
            lm.registerGnssNavigationMessageCallback(navCb, Looper.getMainLooper())
        } catch (_: SecurityException) {}
    }

    fun stop() {
        lm.removeUpdates(locCb)
        lm.unregisterGnssStatusCallback(statusCb)
        lm.unregisterGnssMeasurementsCallback(measCb)
        @Suppress("DEPRECATION")
        lm.unregisterGnssNavigationMessageCallback(navCb)
    }

    private fun computeFix(sats: List<SatObs>, towRx: Double?) {
        val approx = lastFixEcef ?: lastLocation?.let {
            CoordinateUtils.llhToEcef(Llh(Math.toRadians(it.latitude),
                Math.toRadians(it.longitude), it.altitude))
        }
        if (approx == null || towRx == null) return notifyInvalid(sats)

        val withPos = sats.mapNotNull { obs ->
            val pr = obs.pseudorange ?: return@mapNotNull null
            val eph = navParser.getEphemeris(obs.svid) ?: return@mapNotNull null
            Triple(obs, SatellitePosition.compute(eph, pr, towRx), pr)
        }
        if (withPos.size < 4) return notifyInvalid(sats)

        val sol = wls.solve(withPos.map { it.second to it.third }, approx)
        lastFixEcef = sol.position
        val llh = CoordinateUtils.ecefToLlh(sol.position)

        val enriched = sats.map { obs ->
            val sp = withPos.firstOrNull { it.first === obs }?.second
            if (sp != null) {
                val enu = CoordinateUtils.ecefDeltaToEnu(
                    Ecef(sp.x - sol.position.x, sp.y - sol.position.y, sp.z - sol.position.z),
                    llh.lat, llh.lon)
                val (az, el) = CoordinateUtils.enuToAzEl(enu)
                obs.azimuth = az; obs.elevation = el
            } else {
                systemAzEl[obs.constellation to obs.svid]?.let {
                    obs.azimuth = it.first; obs.elevation = it.second }
            }
            if (obs.elevation != null && obs.elevation!! < minElevationDeg) obs.valid = false
            obs
        }
        listeners.forEach { it.onFixComputed(
            FixResult(llh, sol.position, sol.hdop, sol.residuals,
                System.currentTimeMillis(), sol.converged), enriched) }
    }

    private fun notifyInvalid(sats: List<SatObs>) {
        val enriched = sats.map { obs ->
            systemAzEl[obs.constellation to obs.svid]?.let {
                obs.azimuth = it.first; obs.elevation = it.second }
            obs
        }
        listeners.forEach { it.onFixComputed(
            FixResult(null, null, 0.0, emptyList(), System.currentTimeMillis(), false), enriched) }
    }

    private fun constellationName(type: Int) = when (type) {
        GnssStatus.CONSTELLATION_GPS -> "GPS"
        GnssStatus.CONSTELLATION_BEIDOU -> "BDS"
        GnssStatus.CONSTELLATION_GALILEO -> "GAL"
        GnssStatus.CONSTELLATION_GLONASS -> "GLO"
        else -> "OTH"
    }
}
