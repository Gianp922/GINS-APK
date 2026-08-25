package com.gnss.locator

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.util.AttributeSet
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.gnss.locator.solver.Point2
import com.gnss.locator.solver.TdoaSolver
import kotlin.math.hypot

class IndoorActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_indoor)

        // 配置表（生产环境读入 PZ-ASlist / DList，此处为演示值）
        val as1 = Point2(0.0, 0.0); val as2 = Point2(60.0, 0.0); val as3 = Point2(30.0, 50.0)
        val tce = Point2(20.0, 30.0)
        val dL12 = 5.2        // ΔL12（含TCE时延差Δdln修正）
        val dL13 = -3.8

        val res = TdoaSolver().solve(as1, as2, as3, dL12, dL13)

        findViewById<TextView>(R.id.tvIndoor).text =
            "小区:PZ-01  AS1(0,0) AS2(60,0) AS3(30,50)\nTCE坐标:(20,30)\n" +
            "ΔL12=%.2fm  ΔL13=%.2fm\n".format(dL12, dL13) +
            "UE解算: " + (res.position?.let { "(%.2f, %.2f) m".format(it.x, it.y) } ?: "失败") +
            "\n残差: %.3f m（迭代%d次）".format(res.residual, res.iterations) +
            "\nUE→As1:%.1fm  As2:%.1fm  As3:%.1fm".format(
                res.position?.let { hypot(it.x - as1.x, it.y - as1.y) } ?: 0.0,
                res.position?.let { hypot(it.x - as2.x, it.y - as2.y) } ?: 0.0,
                res.position?.let { hypot(it.x - as3.x, it.y - as3.y) } ?: 0.0)

        findViewById<RelativePlotView>(R.id.xyView).apply {
            stations = listOf(as1, as2, as3); tcePos = tce; ue = res.position
        }
        findViewById<Button>(R.id.btnBack).setOnClickListener { finish() }
    }
}

class RelativePlotView(ctx: Context, attrs: AttributeSet? = null) : View(ctx, attrs) {
    var stations: List<Point2> = emptyList()
    var tcePos: Point2? = null
    var ue: Point2? = null

    override fun onDraw(c: Canvas) {
        if (stations.isEmpty()) return
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 30f }
        val w = width.toFloat(); val h = height.toFloat()
        val maxX = (stations.maxOf { it.x } + 15).coerceAtLeast(1.0)
        val maxY = (stations.maxOf { it.y } + 15).coerceAtLeast(1.0)
        fun px(x: Double) = (x / maxX * (w - 80)).toFloat() + 40f
        fun py(y: Double) = h - 40f - (y / maxY * (h - 100)).toFloat()
        fun mark(pt: Point2, label: String, color: Int) {
            p.color = color; c.drawCircle(px(pt.x), py(pt.y), 12f, p)
            p.color = Color.BLACK; c.drawText(label, px(pt.x) + 14f, py(pt.y), p)
        }
        stations.forEachIndexed { i, s -> mark(s, "As${i + 1}", Color.BLUE) }
        tcePos?.let { mark(it, "TCE", Color.rgb(0, 150, 0)) }
        ue?.let { u ->
            mark(u, "UE", Color.RED)
            p.color = Color.GRAY; p.strokeWidth = 2f
            stations.forEach {
                c.drawLine(px(u.x), py(u.y), px(it.x), py(it.y), p)
                c.drawText("%.0fm".format(hypot(u.x - it.x, u.y - it.y)),
                    (px(u.x) + px(it.x)) / 2, (py(u.y) + py(it.y)) / 2 - 8, p)
            }
        }
    }
}
