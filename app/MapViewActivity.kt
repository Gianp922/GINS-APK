package com.gnss.locator

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.gnss.locator.gnss.FixResult
import com.gnss.locator.gnss.GnssService
import com.gnss.locator.gnss.SatObs
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.MapEventsReceiver
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

class MapViewActivity : AppCompatActivity() {
    private lateinit var map: MapView
    private val track = ArrayList<GeoPoint>()
    private val pois = ArrayList<GeoPoint>()

    private val listener = object : GnssService.Listener {
        override fun onLocation(lat: Double, lon: Double, alt: Double, speed: Float,
                                hdop: Double, accuracy: Double, utc: Long, valid: Boolean) {
            runOnUiThread { updateMap(lat, lon) }
        }
        override fun onMeasurements(sats: List<SatObs>) {}
        override fun onFixComputed(fix: FixResult, sats: List<SatObs>) {}
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().userAgentValue = packageName
        map = MapView(this).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)          // 双指缩放/滑动
            controller.setZoom(17.5)
        }
        setContentView(map)

        // 长按地图添加兴趣点PI
        map.overlays.add(MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean = false
            override fun longPressHelper(p: GeoPoint?): Boolean {
                p?.let {
                    pois.add(it)
                    map.overlays.add(Marker(map).apply { position = it; title = "PI ${pois.size}" })
                    map.invalidate()
                }
                return true
            }
        }))
    }

    override fun onResume() { super.onResume(); map.onResume(); LocatorApp.gnss?.addListener(listener) }
    override fun onPause() { super.onPause(); map.onPause(); LocatorApp.gnss?.removeListener(listener) }

    private fun updateMap(lat: Double, lon: Double) {
        val gp = GeoPoint(lat, lon)
        track.add(gp)
        map.overlays.removeAll { it is Marker || (it is Polyline) }
        if (track.size > 1) map.overlays.add(Polyline().apply {          // 历史轨迹弱化显示
            setPoints(track); outlinePaint.color = 0x663366FF.toInt(); outlinePaint.strokeWidth = 4f })
        map.overlays.add(Marker(map).apply {                             // 当前定位点
            position = gp; setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM); title = "当前位置" })
        pois.forEach { pi ->                                             // PI连线与距离
            map.overlays.add(Polyline().apply {
                setPoints(listOf(gp, pi)); outlinePaint.color = 0xAAFF6600.toInt(); outlinePaint.strokeWidth = 2f })
            val d = FloatArray(1)
            android.location.Location.distanceBetween(gp.latitude, gp.longitude,
                pi.latitude, pi.longitude, d)
            map.overlays.add(Marker(map).apply { position = pi; title = "PI 距离%.0fm".format(d[0]) })
        }
        map.controller.animateTo(gp)
        map.invalidate()
    }
}
