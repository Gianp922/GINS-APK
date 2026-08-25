package com.gnss.locator.gnss

import android.location.GnssClock
import android.location.GnssMeasurement

object PseudorangeCalculator {
    private const val C = 299792458.0
    private const val WEEK_NS = 604800L * 1_000_000_000L

    /** 接收时刻对应的周内秒 */
    fun towSeconds(clk: GnssClock): Double? {
        if (!clk.hasFullBiasNanos()) return null
        val t = clk.timeNanos - (clk.fullBiasNanos + clk.biasNanos)
        return (((t % WEEK_NS) + WEEK_NS) % WEEK_NS) * 1e-9
    }

    /** 伪距 ρ = (tRX − tTX) × c，含周翻转处理与合理性检核(<300ms) */
    fun compute(m: GnssMeasurement, clk: GnssClock): Double? {
        if (!m.hasReceivedSvTimeNanos() || !clk.hasFullBiasNanos()) return null
        val tRx = towSeconds(clk) ?: return null
        val tTx = (m.receivedSvTimeNanos % WEEK_NS) * 1e-9
        var d = tRx - tTx
        if (d < 0) d += 604800.0
        if (d <= 0 || d > 0.3) return null
        return d * C
    }
}
