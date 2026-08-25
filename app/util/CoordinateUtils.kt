package com.gnss.locator.util

import kotlin.math.*

data class Ecef(val x: Double, val y: Double, val z: Double)
data class Llh(val lat: Double, val lon: Double, val h: Double)   // 弧度/弧度/米
data class Enu(val e: Double, val n: Double, val u: Double)

object CoordinateUtils {
    private const val A = 6378137.0            // WGS84 长半轴
    private const val F = 1.0 / 298.257223563  // 扁率
    private const val E2 = F * (2 - F)
    const val OMegaE = 7.2921151467e-5         // 地球自转角速度

    /** ECEF -> 大地坐标（经纬高） */
    fun ecefToLlh(p: Ecef): Llh {
        val r = hypot(p.x, p.y)
        var lat = atan2(p.z, r)
        var h = 0.0
        repeat(8) {
            val n = A / sqrt(1 - E2 * sin(lat).pow(2))
            h = r / cos(lat) - n
            lat = atan2(p.z, r * (1 - E2 * n / (n + h)))
        }
        return Llh(lat, atan2(p.y, p.x), h)
    }

    /** 大地坐标 -> ECEF */
    fun llhToEcef(g: Llh): Ecef {
        val n = A / sqrt(1 - E2 * sin(g.lat).pow(2))
        return Ecef((n + g.h) * cos(g.lat) * cos(g.lon),
                    (n + g.h) * cos(g.lat) * sin(g.lon),
                    (n * (1 - E2) + g.h) * sin(g.lat))
    }

    /** ECEF差向量 -> 站心ENU */
    fun ecefDeltaToEnu(d: Ecef, lat: Double, lon: Double): Enu {
        val (sx, cx) = sin(lat) to cos(lat)
        val (sy, cy) = sin(lon) to cos(lon)
        return Enu(-sy * d.x + cy * d.y,
                   -sx * cy * d.x - sx * sy * d.y + cx * d.z,
                    cx * cy * d.x + cx * sy * d.y + sx * d.z)
    }

    /** 由ENU向量计算 [方位角, 俯仰角]（度，方位角0~360） */
    fun enuToAzEl(enu: Enu): Pair<Double, Double> {
        val r = sqrt(enu.e.pow(2) + enu.n.pow(2) + enu.u.pow(2))
        if (r < 1e-9) return 0.0 to 0.0
        val az = (atan2(enu.e, enu.n) * 180 / PI + 360.0) % 360.0
        val el = asin(enu.u / r) * 180 / PI
        return az to el
    }

    /** 按方位角扇区分类（需求：分方位角对卫星分类） */
    fun azimuthSector(azDeg: Double, sectors: Int): Int =
        (azDeg / (360.0 / sectors)).toInt().coerceIn(0, sectors - 1)
}
