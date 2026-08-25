package com.gnss.locator

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.gnss.locator.gnss.FixResult
import com.gnss.locator.gnss.GnssService
import com.gnss.locator.gnss.SatObs
import com.gnss.locator.util.azimuthSector
import kotlin.math.sqrt

class MainActivity : AppCompatActivity(), GnssService.Listener {
    private lateinit var tvFix: TextView
    private lateinit var adapter: SatAdapter
    private lateinit var gnss: GnssService
    private var sortMode = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        tvFix = findViewById(R.id.tvFix)
        adapter = SatAdapter()
        findViewById<RecyclerView>(R.id.rvSats).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = this@MainActivity.adapter
        }
        gnss = LocatorApp.gnss!!

        findViewById<Spinner>(R.id.spSort).apply {
            adapter = ArrayAdapter(this@MainActivity,
                android.R.layout.simple_spinner_dropdown_item,
                listOf("按星座", "按卫星号", "按方位角", "按C/N0"))
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p: AdapterView<*>?, v: View?, i: Int, id: Long) { sortMode = i }
                override fun onNothingSelected(p: AdapterView<*>?) {}
            }
        }
        findViewById<Button>(R.id.btnMap).setOnClickListener { startActivity(Intent(this, MapViewActivity::class.java)) }
        findViewById<Button>(R.id.btnIndoor).setOnClickListener { startActivity(Intent(this, IndoorActivity::class.java)) }
        findViewById<ImageButton>(R.id.btnSettings).setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
        findViewById<ImageButton>(R.id.btnHelp).setOnClickListener {
            Toast.makeText(this, "高精度GNSS定位：WLS伪距解算 + 方位角卫星分类 + 室内TDOA", Toast.LENGTH_LONG).show() }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED)
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION), 1)
    }

    override fun onRequestPermissionsResult(rq: Int, p: Array<out String>, res: IntArray) {
        super.onRequestPermissionsResult(rq, p, res)
        if (res.isNotEmpty() && res[0] == PackageManager.PERMISSION_GRANTED) gnss.start()
    }

    override fun onResume() { super.onResume(); gnss.addListener(this); gnss.start() }
    override fun onPause() { super.onPause(); gnss.removeListener(this) }

    override fun onLocation(lat: Double, lon: Double, alt: Double, speed: Float,
                            hdop: Double, accuracy: Double, utc: Long, valid: Boolean) {
        runOnUiThread {
            tvFix.text = "经度: %.7f°  纬度: %.7f°\n高程: %.1fm  速度: %.1fm/s\nUTC: %s  HDOP: %.2f\n定位误差: %.1f m  状态: %s"
                .format(lon, lat, alt, speed,
                    java.text.SimpleDateFormat("HH:mm:ss").format(java.util.Date(utc)),
                    hdop, accuracy, if (valid) "有效" else "无效")
        }
    }

    override fun onMeasurements(sats: List<SatObs>) {}

    override fun onFixComputed(fix: FixResult, sats: List<SatObs>) {
        runOnUiThread {
            val sorted = when (sortMode) {
                0 -> sats.sortedWith(compareBy({ it.constellation }, { it.svid }))
                1 -> sats.sortedBy { it.svid }
                2 -> sats.sortedBy { it.azimuth ?: 999.0 }
                else -> sats.sortedByDescending { it.cn0 }
            }
            adapter.submit(sorted, gnss.sectors)
            if (fix.valid && fix.residuals.isNotEmpty()) {
                val rms = sqrt(fix.residuals.map { it * it }.average())
                tvFix.append("\n[WLS] 独立解算成功！残差RMS: %.2f m（可用卫星: %d）"
                    .format(rms, sats.count { it.valid }))
            }
        }
    }
}

class SatAdapter : RecyclerView.Adapter<SatAdapter.VH>() {
    private var items: List<SatObs> = emptyList()
    private var sectors = 1
    fun submit(l: List<SatObs>, s: Int) { items = l; sectors = s; notifyDataSetChanged() }
    class VH(val tv: TextView) : RecyclerView.ViewHolder(tv)

    override fun onCreateViewHolder(p: android.view.ViewGroup, i: Int): VH {
        val tv = TextView(p.context).apply { textSize = 13f; setPadding(28, 10, 28, 10) }
        return VH(tv)
    }

    override fun onBindViewHolder(h: VH, i: Int) {
        val s = items[i]
        val sector = s.azimuth?.let { azimuthSector(it, sectors) + 1 } ?: 0
        h.tv.text = "%-4s %02d  C/N0:%5.1f  ρ:%s  Az:%s  El:%s  分区:%d"
            .format(s.constellation, s.svid, s.cn0,
                s.pseudorange?.let { "%.0fm".format(it) } ?: "--",
                s.azimuth?.let { "%.0f°".format(it) } ?: "--",
                s.elevation?.let { "%.0f°".format(it) } ?: "--", sector)
        h.tv.setTextColor(if (s.valid) 0xFF1565C0.toInt() else 0xFF9E9E9E.toInt())
    }
    override fun getItemCount() = items.size
}
