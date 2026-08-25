package com.gnss.locator.solver

import com.gnss.locator.util.Ecef
import kotlin.math.*

class WlsSolver {
    data class Solution(val position: Ecef, val clockBias: Double,
                        val hdop: Double, val residuals: List<Double>, val converged: Boolean)

    /** HᵀWH 矩阵求逆（4x4，高斯消元） */
    fun solve(obs: List<Pair<Ecef, Double>>, approx: Ecef): Solution {
        var pos = approx; var dt = 0.0
        val C = 299792458.0
        var residuals = emptyList<Double>(); var h = Array(4){DoubleArray(4)}
        repeat(12) {
            val rows = obs.map { (sat, pr) ->
                val dx = sat.x - pos.x; val dy = sat.y - pos.y; val dz = sat.z - pos.z
                val r = sqrt(dx*dx + dy*dy + dz*dz)
                Triple(doubleArrayOf(dx/r, dy/r, dz/r, 1.0), pr - r + dt * C, r)
            }
            residuals = rows.map { it.second }
            // 权阵：仰角越高权重越大（含C/N0可进一步细化）
            val H = Array(rows.size) { rows[it].first }
            val W = DoubleArray(rows.size) { 1.0 }   // 可按 1/sin²(el) 或 C/N0 加权
            // 正规方程 N·δx = b
            val N = Array(4){DoubleArray(5)}
            for (i in 0 until 4) for (j in 0 until 4) {
                var s = 0.0; for (k in rows.indices) s += H[k][i] * W[k] * H[k][j]
                N[i][j] = s
            }
            for (i in 0 until 4) {
                var s = 0.0; for (k in rows.indices) s += H[k][i] * W[k] * rows[k].second
                N[i][4] = s
            }
            val dx = gaussSolve(N) ?: return Solution(pos, dt, 0.0, residuals, false)
            pos = Ecef(pos.x + dx[0], pos.y + dx[1], pos.z + dx[2]); dt -= dx[3] / C
            if (sqrt(dx[0].pow(2)+dx[1].pow(2)+dx[2].pow(2)) < 1e-3) {
                h = N.map { it.copyOf(4) }.toTypedArray()
                return Solution(pos, dt, hdop(h), residuals, true)
            }
        }
        return Solution(pos, dt, 0.0, residuals, false)
    }

    private fun hdop(N: Array<DoubleArray>): Double {
        val inv = invert4(N) ?: return 0.0
        return sqrt(max(inv[0][0] + inv[1][1], 0.0))
    }

    private fun gaussSolve(a: Array<DoubleArray>): DoubleArray? {
        val n = 4
        for (i in 0 until n) {
            var p = i; for (j in i+1 until n) if (abs(a[j][i]) > abs(a[p][i])) p = j
            if (abs(a[p][i]) < 1e-12) return null
            val t = a[i]; a[i] = a[p]; a[p] = t
            for (j in i+1..n) a[i][j] /= a[i][i]
            for (k in 0 until n) if (k != i) for (j in i+1..n) a[k][j] -= a[k][i]*a[i][j]
        }
        return DoubleArray(n) { a[it][n] }
    }

    private fun invert4(m: Array<DoubleArray>): Array<DoubleArray>? {
        val n = 4; val a = Array(n){DoubleArray(2*n)}
        for (i in 0 until n) { System.arraycopy(m[i], 0, a[i], 0, n); a[i][n+i] = 1.0 }
        for (i in 0 until n) {
            var p = i; for (j in i+1 until n) if (abs(a[j][i]) > abs(a[p][i])) p = j
            if (abs(a[p][i]) < 1e-12) return null
            val t = a[i]; a[i] = a[p]; a[p] = t
            for (j in i until 2*n) a[i][j] /= a[i][i]
            for (k in 0 until n) if (k != i) { val f = a[k][i]
                for (j in i until 2*n) a[k][j] -= f * a[i][j] }
        }
        return Array(n) { a[it].copyOfRange(n, 2*n) }
    }
}
