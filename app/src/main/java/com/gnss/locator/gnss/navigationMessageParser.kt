package com.gnss.locator.gnss

import android.location.GnssNavigationMessage
import kotlin.math.PI

data class Ephemeris(
    val svid: Int, val week: Int, val toeSow: Double, val tocSow: Double,
    val sqrtA: Double, val ecc: Double, val m0: Double, val deltaN: Double,
    val omega: Double, val omega0: Double, val omegaDot: Double,
    val i0: Double, val idot: Double,
    val cuc: Double, val cus: Double, val crc: Double, val crs: Double,
    val cic: Double, val cis: Double,
    val af0: Double, val af1: Double, val af2: Double, val tgd: Double,
    val collectedAt: Long)

class NavigationMessageParser {
    private val SEMI = PI                       // 半周→弧度
    private val P5 = 0.03125                    // 2^-5
    private val P19 = 1.9073486328125e-6        // 2^-19
    private val P29 = 1.862645149230957e-9      // 2^-29
    private val P31 = 4.656612873077393e-10     // 2^-31
    private val P33 = 1.1641532182693481e-10    // 2^-33
    private val P43 = 1.1368683772161603e-13    // 2^-43
    private val P55 = 2.7755575615628914e-17    // 2^-55

    private val wordBuf = HashMap<Int, ArrayDeque<Long>>()   // svid -> 30bit字缓冲
    private val sfData = HashMap<Int, HashMap<Int, List<Long>>>()
    private val ephMap = HashMap<Int, Ephemeris>()

    fun feed(msg: GnssNavigationMessage) {
        if (msg.type != GnssNavigationMessage.TYPE_GPS_L1CA) return
        val d = msg.data
        if (d <= 0 || d > 0x3FFFFFFFL) return                 // 仅接受30bit字
        val q = wordBuf.getOrPut(msg.svid) { ArrayDeque() }
        q.addLast(d)
        if (q.size > 10) q.removeFirst()
        if (q.size < 10) return
        val words = q.toList()
        if ((words[0] ushr 22) != 0x8BL) return               // TLM前导码10001011同步
        val sfid = ((words[1] ushr 8) and 0x7L).toInt()
        if (sfid !in 1..5) return
        sfData.getOrPut(msg.svid) { HashMap() }[sfid] = words
        if (sfid in 1..3) tryBuild(msg.svid)
    }

    private fun tryBuild(svid: Int) {
        val sf = sfData[svid] ?: return
        val w1 = sf[1] ?: return; val w2 = sf[2] ?: return; val w3 = sf[3] ?: return
        // 展开为240个数据位（每字24数据+6校验，去校验位）
        fun bits(words: List<Long>): IntArray {
            val b = IntArray(240)
            words.forEachIndexed { i, w ->
                val d24 = (w ushr 6).toInt()
                for (j in 0 until 24) b[i * 24 + j] = (d24 ushr (23 - j)) and 1
            }
            return b
        }
        val b1 = bits(w1); val b2 = bits(w2); val b3 = bits(w3)
        fun u(b: IntArray, start: Int, len: Int): Long {
            var v = 0L
            for (i in 0 until len) v = (v shl 1) or b[start - 1 + i].toLong()
            return v
        }
        fun s(b: IntArray, start: Int, len: Int): Long {
            val r = u(b, start, len)
            return if (r >= (1L shl (len - 1))) r - (1L shl len) else r
        }
        ephMap[svid] = Ephemeris(
            svid, u(b1, 55, 10).toInt(),
            u(b2, 217, 16) * 16.0, u(b1, 177, 16) * 16.0,
            u(b2, 185, 32) * P19, u(b2, 137, 32) * P33,
            s(b2, 89, 32) * P31 * SEMI, s(b2, 73, 16) * P43 * SEMI,
            s(b3, 161, 32) * P31 * SEMI, s(b3, 65, 32) * P31 * SEMI,
            s(b3, 193, 24) * P43 * SEMI, s(b3, 113, 32) * P31 * SEMI,
            s(b3, 225, 14) * P43 * SEMI,
            s(b2, 121, 16) * P29, s(b2, 169, 16) * P29,
            s(b3, 145, 16) * P5, s(b2, 57, 16) * P5,
            s(b3, 49, 16) * P29, s(b3, 97, 16) * P29,
            s(b1, 217, 22) * P31, s(b1, 201, 16) * P43, s(b1, 193, 8) * P55,
            s(b1, 161, 8) * P31, System.currentTimeMillis())
    }

    fun getEphemeris(svid: Int): Ephemeris? {
        val e = ephMap[svid] ?: return null
        return if (System.currentTimeMillis() - e.collectedAt < 4 * 3600_000L) e else null
    }
}
