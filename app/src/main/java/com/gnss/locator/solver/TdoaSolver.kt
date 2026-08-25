package com.gnss.locator.solver

import kotlin.math.*

data class Point2(val x: Double, val y: Double)
data class TdoaResult(val position: Point2?, val residual: Double, val iterations: Int)

class TdoaSolver {
    /** A为参考站；dab=|QA|-|QB|观测量，dac=|QA|-|QC|观测量 */
    fun solve(A: Point2, B: Point2, C: Point2, dab: Double, dac: Double,
              seed: Point2 = Point2((A.x+B.x+C.x)/3, (A.y+B.y+C.y)/3)): TdoaResult {
        var q = seed
        repeat(50) { iter ->
            val ra = hypot(q.x-A.x, q.y-A.y); if (ra < 1e-6) return@repeat
            val rb = hypot(q.x-B.x, q.y-B.y); if (rb < 1e-6) return@repeat
            val rc = hypot(q.x-C.x, q.y-C.y); if (rc < 1e-6) return@repeat
            val r1 = (ra - rb) - dab          // 残差1
            val r2 = (ra - rc) - dac          // 残差2
            // 雅可比
            val j11 = (q.x-A.x)/ra - (q.x-B.x)/rb; val j12 = (q.y-A.y)/ra - (q.y-B.y)/rb
            val j21 = (q.x-A.x)/ra - (q.x-C.x)/rc; val j22 = (q.y-A.y)/ra - (q.y-C.y)/rc
            val det = j11*j22 - j12*j21
            if (abs(det) < 1e-12) return TdoaResult(null, Double.MAX_VALUE, iter)
            val dx = -( j22*r1 - j12*r2) / det
            val dy = -(-j21*r1 + j11*r2) / det
            q = Point2(q.x + dx, q.y + dy)
            if (hypot(dx, dy) < 1e-4)
                return TdoaResult(q, sqrt(r1.pow(2)+r2.pow(2)), iter)
        }
        return TdoaResult(q, Double.MAX_VALUE, 50)
    }
}
