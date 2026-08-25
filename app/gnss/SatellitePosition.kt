package com.gnss.locator.gnss

import com.gnss.locator.util.CoordinateUtils
import com.gnss.locator.util.Ecef
import kotlin.math.*

object SatellitePosition {
    private const val MU = 3.986005e14
    private const val OM_E = CoordinateUtils.OMegaE
    private const val C = 299792458.0

    /** 开普勒轨道递推：由星历+信号传播时间计算发射时刻卫星ECEF坐标 */
    fun compute(e: Ephemeris, pseudorange: Double, towAtReception: Double): Ecef {
        val transmitSec = towAtReception - pseudorange / C
        val dt = e.af0 + e.af1 * transmitSec + e.af2 * transmitSec.pow(2) - e.tgd
        val t = transmitSec - dt
        var tk = t - e.toeSow
        if (tk < -302400.0) tk += 604800.0
        if (tk > 302400.0) tk -= 604800.0

        val a = e.sqrtA * e.sqrtA
        val n = sqrt(MU / a.pow(3)) + e.deltaN
        val ma = e.m0 + n * tk
        var E = ma
        repeat(10) { E -= (E - e.ecc * sin(E) - ma) / (1 - e.ecc * cos(E)) }
        val v = atan2(sqrt(1 - e.ecc.pow(2)) * sin(E), cos(E) - e.ecc)
        val phi = v + e.omega
        val du = e.cus * sin(2 * phi) + e.cuc * cos(2 * phi)
        val dr = e.crs * sin(2 * phi) + e.crc * cos(2 * phi)
        val di = e.cis * sin(2 * phi) + e.cic * cos(2 * phi)
        val u = phi + du
        val r = a * (1 - e.ecc * cos(E)) + dr
        val i = e.i0 + di + e.idot * tk
        val om = e.omega0 + (e.omegaDot - OM_E) * tk - OM_E * e.toeSow
        val xp = r * cos(u); val yp = r * sin(u)
        return Ecef(xp * cos(om) - yp * cos(i) * sin(om),
                    xp * sin(om) + yp * cos(i) * cos(om),
                    yp * sin(i))
    }
}
