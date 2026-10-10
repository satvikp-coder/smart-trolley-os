package com.dashline.launcher

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.TextView
import org.mapsforge.core.graphics.Style
import org.mapsforge.core.model.BoundingBox
import org.mapsforge.core.model.LatLong
import org.mapsforge.core.model.Point
import org.mapsforge.map.android.graphics.AndroidGraphicFactory
import org.mapsforge.map.android.rendertheme.AssetsRenderTheme
import org.mapsforge.map.android.util.AndroidUtil
import org.mapsforge.map.android.view.MapView
import org.mapsforge.map.layer.Layer
import org.mapsforge.map.layer.cache.TileCache
import org.mapsforge.map.layer.overlay.Marker
import org.mapsforge.map.layer.overlay.Polyline
import org.mapsforge.map.layer.renderer.TileRendererLayer
import org.mapsforge.map.reader.MapFile
import java.io.File

class OfflineMapSurface(
    context: Context,
    initialCenter: TravelCoordinate,
    initialZoom: Double,
    private val onMapTap: (TravelCoordinate) -> Unit
) : FrameLayout(context) {
    val mapDirectory: File = File(context.getExternalFilesDir(null) ?: context.filesDir, "maps")
    val mapDataFile: File? = mapDirectory.listFiles()
        ?.filter { it.isFile && it.extension.equals("map", ignoreCase = true) }
        ?.sortedBy { it.name }
        ?.firstOrNull()
    var mapDescription: String = ""
        private set
    var mapView: MapView? = null
        private set
    private var mapFile: MapFile? = null
    private var tileCache: TileCache? = null
    private var tileLayer: TileRendererLayer? = null
    private var destinationMarker: Marker? = null
    private var locationMarker: Marker? = null
    private var routeLayer: Polyline? = null
    private var tapLayer: Layer? = null

    init {
        setBackgroundColor(0xff152338.toInt())
        mapDirectory.mkdirs()
        try {
            ensureGraphicsFactory(context.applicationContext)
            val file = mapDataFile ?: throw IllegalStateException("No .map file found in ${mapDirectory.absolutePath}")
            if (file.length() < MIN_MAP_BYTES) throw IllegalArgumentException("Map file is too small to be valid")
            val reader = MapFile(file)
            val mapBounds = reader.boundingBox()
            if (mapBounds == null || mapBounds.minLatitude < -90 || mapBounds.maxLatitude > 90 ||
                mapBounds.minLongitude < -180 || mapBounds.maxLongitude > 180
            ) {
                reader.close()
                throw IllegalArgumentException("Map file has invalid geographic bounds")
            }
            mapFile = reader
            mapDescription = "Offline map: ${file.name} · ${file.length() / (1024 * 1024)} MiB"
            mapView = MapView(context).apply {
                setBuiltInZoomControls(true)
                setZoomLevelMin(3)
                setZoomLevelMax(19)
                val center = if (mapBounds.contains(initialCenter.latitude, initialCenter.longitude)) {
                    initialCenter.toLatLong()
                } else {
                    mapBounds.centerPoint
                }
                setCenter(center)
                setZoomLevel(initialZoom.toInt().coerceIn(3, 19).toByte())
            }
            val view = mapView!!
            val cache = AndroidUtil.createTileCache(context, "trolley-vector-map", 256, 1f, 1.5)
            tileCache = cache
            val renderer = TileRendererLayer(
                cache, reader, view.model.mapViewPosition, false, true, false,
                AndroidGraphicFactory.INSTANCE
            ).apply {
                setXmlRenderTheme(AssetsRenderTheme(context.assets, "mapsforge/default.xml", ""))
            }
            tileLayer = renderer
            view.addLayer(renderer)
            val inputLayer = object : Layer() {
                override fun draw(boundingBox: BoundingBox, zoomLevel: Byte, canvas: org.mapsforge.core.graphics.Canvas, topLeftPoint: Point) = Unit
                override fun onTap(tapLatLong: LatLong, layerXY: Point, tapXY: Point): Boolean {
                    onMapTap(TravelCoordinate(tapLatLong.latitude, tapLatLong.longitude))
                    return true
                }
            }
            tapLayer = inputLayer
            view.addLayer(inputLayer)
            view.mapScaleBar.isVisible = true
            addView(view, LayoutParams(-1, -1))
        } catch (error: Exception) {
            mapView?.destroyAll()
            mapView = null
            tileCache?.destroy()
            tileCache = null
            mapFile?.close()
            mapFile = null
            mapDescription = error.message ?: "Map file could not be opened"
            val notice = TextView(context).apply {
                text = "OFFLINE MAP UNAVAILABLE\n\n${mapDescription}\n\nCopy a Mapsforge .map file to:\n${mapDirectory.absolutePath}"
                setTextColor(0xffe9f0f7.toInt())
                textSize = 15f
                gravity = Gravity.CENTER
                setPadding(24, 24, 24, 24)
            }
            addView(notice, LayoutParams(-1, -1))
        }
    }

    fun center(): TravelCoordinate? = mapView?.model?.mapViewPosition?.center?.let {
        TravelCoordinate(it.latitude, it.longitude)
    }

    fun zoom(): Double? = mapView?.model?.mapViewPosition?.zoomLevel?.toDouble()

    fun showDestination(point: TravelCoordinate, center: Boolean) {
        val view = mapView ?: return
        destinationMarker?.let { view.layerManager.layers.remove(it); it.onDestroy() }
        destinationMarker = marker(point, 0xff21cfa2.toInt()).also(view::addLayer)
        if (center) view.model.mapViewPosition.animateTo(point.toLatLong())
        view.layerManager.redrawLayers()
    }

    fun showCurrentLocation(point: TravelCoordinate, center: Boolean = true) {
        val view = mapView ?: return
        locationMarker?.let { view.layerManager.layers.remove(it); it.onDestroy() }
        locationMarker = marker(point, 0xff4c9dff.toInt()).also(view::addLayer)
        if (center) view.model.mapViewPosition.animateTo(point.toLatLong())
        view.layerManager.redrawLayers()
    }

    fun showRoute(points: List<TravelCoordinate>, color: Int, width: Float) {
        val view = mapView ?: return
        clearRoute()
        if (points.size < 2) return
        val paint = AndroidGraphicFactory.INSTANCE.createPaint().apply {
            setColor(color)
            setStrokeWidth(width)
            setStyle(Style.STROKE)
        }
        routeLayer = Polyline(paint, AndroidGraphicFactory.INSTANCE).apply {
            setPoints(points.map { it.toLatLong() })
        }.also(view::addLayer)
        view.layerManager.redrawLayers()
    }

    fun clearRoute() {
        val view = mapView ?: return
        routeLayer?.let { view.layerManager.layers.remove(it); it.onDestroy() }
        routeLayer = null
        view.layerManager.redrawLayers()
    }

    fun dispose() {
        val view = mapView ?: return
        view.destroyAll()
        tileCache?.destroy()
        tileCache = null
        mapFile?.close()
        mapFile = null
        destinationMarker = null
        locationMarker = null
        routeLayer = null
        tileLayer = null
        tapLayer = null
    }

    private fun marker(point: TravelCoordinate, color: Int): Marker {
        val drawable = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
            setStroke(3, 0xffffffff.toInt())
            setSize(dp(22), dp(22))
        }
        val icon = AndroidGraphicFactory.convertToBitmap(drawable)
        return Marker(point.toLatLong(), icon, 0, -dp(11))
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()

    companion object {
        private const val MIN_MAP_BYTES = 1024L
        @Volatile private var graphicsFactoryReady = false

        @Synchronized
        private fun ensureGraphicsFactory(context: Context) {
            if (!graphicsFactoryReady) {
                AndroidGraphicFactory.createInstance(context)
                graphicsFactoryReady = true
            }
        }
    }
}
