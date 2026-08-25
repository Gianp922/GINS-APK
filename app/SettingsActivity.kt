package com.gnss.locator

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.gnss.locator.util.Prefs

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        val prefs = Prefs(this)

        val intervals = longArrayOf(100, 500, 1000)
        val spInterval = findViewById<Spinner>(R.id.spInterval)
        spInterval.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item,
            listOf("100 ms", "500 ms", "1000 ms"))
        spInterval.setSelection(intervals.indexOfFirst { it == prefs.intervalMs }.let { if (it < 0) 2 else it })

        val spSectors = findViewById<Spinner>(R.id.spSectors)
        spSectors.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item,
            listOf("1（室外）", "2（隧道）", "3（室内）", "4（室内）"))
        spSectors.setSelection((prefs.sectors - 1).coerceIn(0, 3))

        findViewById<CheckBox>(R.id.cbGps).isChecked = "GPS" in prefs.constellations
        findViewById<CheckBox>(R.id.cbBds).isChecked = "BDS" in prefs.constellations
        findViewById<CheckBox>(R.id.cbGal).isChecked = "GAL" in prefs.constellations

        val sbElev = findViewById<SeekBar>(R.id.sbElev)
        val tvElev = findViewById<TextView>(R.id.tvElev)
        sbElev.max = 30; sbElev.progress = prefs.minElev.toInt()
        tvElev.text = "最低仰角: ${prefs.minElev.toInt()}°"
        sbElev.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, u: Boolean) { tvElev.text = "最低仰角: $p°" }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        val spMode = findViewById<Spinner>(R.id.spMode)
        spMode.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item,
            listOf("室外定位", "室内模式", "隧道模式"))
        spMode.setSelection(prefs.mode.coerceIn(0, 2))

        val spShape = findViewById<Spinner>(R.id.spShape)
        spShape.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item,
            listOf("圆形", "三角形", "矩形", "十字"))
        spShape.setSelection(prefs.markerShape.coerceIn(0, 3))

        val etUser = findViewById<EditText>(R.id.etUser)
        val etPwd = findViewById<EditText>(R.id.etPwd)
        etUser.setText(prefs.username); etPwd.setText(prefs.password)

        findViewById<Button>(R.id.btnSave).setOnClickListener {
            prefs.intervalMs = intervals[spInterval.selectedItemPosition]
            prefs.sectors = spSectors.selectedItemPosition + 1
            val cons = mutableSetOf<String>()
            if (findViewById<CheckBox>(R.id.cbGps).isChecked) cons.add("GPS")
            if (findViewById<CheckBox>(R.id.cbBds).isChecked) cons.add("BDS")
            if (findViewById<CheckBox>(R.id.cbGal).isChecked) cons.add("GAL")
            if (cons.isEmpty()) cons.add("GPS")
            prefs.constellations = cons
            prefs.minElev = sbElev.progress.toDouble()
            prefs.mode = spMode.selectedItemPosition
            prefs.markerShape = spShape.selectedItemPosition
            prefs.saveUser(etUser.text.toString(), etPwd.text.toString())
            LocatorApp.gnss?.apply {
                intervalMs = prefs.intervalMs; sectors = prefs.sectors
                minElevationDeg = prefs.minElev; enabledConstellations = prefs.constellations
                mode = prefs.mode
            }
            Toast.makeText(this, "已保存", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
