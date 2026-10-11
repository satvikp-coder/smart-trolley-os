package com.dashline.launcher

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class TrolleyHomeActivity : AppCompatActivity() {
    private val ink = Color.rgb(14, 25, 39)
    private val panel = Color.rgb(22, 37, 55)
    private val panelLight = Color.rgb(29, 47, 67)
    private val accent = Color.rgb(46, 210, 166)
    private val muted = Color.rgb(155, 174, 192)
    private val white = Color.rgb(240, 246, 250)
    private val amber = Color.rgb(255, 190, 92)
    private val handler = Handler(Looper.getMainLooper())
    private val probeExecutor = Executors.newSingleThreadExecutor()
    private val navigationExecutor = Executors.newSingleThreadExecutor()
    private val navigationProvider: NavigationProvider?
        get() {
            val preferences = getSharedPreferences(PREF_TRAVEL_API, Context.MODE_PRIVATE)
            val base = preferences.getString(KEY_TRAVEL_API_BASE_URL, BuildConfig.TRAVEL_API_BASE_URL).orEmpty()
            val token = preferences.getString(KEY_TRAVEL_API_TOKEN, null)
            return GraphHopperProxyProvider.configured(base, token)
        }
    private val formValues = mutableMapOf<String, String>()
    private val fields = mutableMapOf<String, EditText>()
    private lateinit var content: LinearLayout
    private lateinit var timeLabel: TextView
    private lateinit var networkLabel: TextView
    private lateinit var batteryLabel: TextView
    private var batteryPercent = -1
    private var batteryStatus = BatteryManager.BATTERY_STATUS_UNKNOWN
    private var internetState = "CHECKING"
    private var probeRunning = false
    private var lastProbeAt = 0L
    private var activePage = PAGE_HOME
    private var receiverRegistered = false
    private var formParent: LinearLayout? = null
    private var formRow: LinearLayout? = null
    private var formColumn = 0
    private var formWide = false
    private var mapView: OfflineMapSurface? = null
    private var currentLocation: TravelCoordinate? = null
    private var locationRequested = false
    private var navigationStatus: TextView? = null
    private var navigationResults: LinearLayout? = null
    private var navigationRouteSummary: TextView? = null
    private var travelLookupResults: LinearLayout? = null
    private val travelReferenceDatabase by lazy { TravelReferenceDatabase(this) }

    private val locationManager by lazy {
        getSystemService(Context.LOCATION_SERVICE) as LocationManager
    }

    private val gpsListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            currentLocation = location.toTravelCoordinate()
            updateCurrentLocationMarker(location, false)
            navigationStatus?.text = "GPS fix · accuracy ±${location.accuracy.toInt()} m"
        }

        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
        override fun onProviderEnabled(provider: String) = Unit
        override fun onProviderDisabled(provider: String) {
            if (provider == LocationManager.GPS_PROVIDER) {
                navigationStatus?.text = "GPS is disabled in Android Location settings"
            }
        }
    }

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            batteryPercent = if (level >= 0 && scale > 0) level * 100 / scale else -1
            batteryStatus = intent.getIntExtra(
                BatteryManager.EXTRA_STATUS,
                BatteryManager.BATTERY_STATUS_UNKNOWN
            )
            refreshStatus()
        }
    }

    private val clockTicker = object : Runnable {
        override fun run() {
            if (this@TrolleyHomeActivity::timeLabel.isInitialized) {
                val now = Date()
                timeLabel.text = SimpleDateFormat("EEE, d MMM  ·  HH:mm", Locale.getDefault()).format(now)
                refreshStatus()
                handler.postDelayed(this, 30_000L)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        activePage = savedInstanceState?.getString(KEY_PAGE) ?: PAGE_HOME
        savedInstanceState?.getBundle(KEY_FORMS)?.let { state ->
            for (key in state.keySet()) formValues[key] = state.getString(key, "")
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (activePage != PAGE_HOME) showPage(PAGE_HOME)
            }
        })
        registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        receiverRegistered = true
        buildShell()
        showPage(activePage)
    }

    override fun onResume() {
        super.onResume()
        applyImmersiveMode()
        if (locationRequested) startGpsUpdates()
        handler.removeCallbacks(clockTicker)
        handler.post(clockTicker)
    }

    override fun onPause() {
        stopGpsUpdates()
        super.onPause()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_LOCATION && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            locationRequested = true
            startGpsUpdates()
        } else if (requestCode == REQUEST_LOCATION) {
            navigationStatus?.text = "Location permission was not granted"
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyImmersiveMode()
    }

    @Suppress("DEPRECATION")
    private fun applyImmersiveMode() {
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )
    }

    private fun buildShell() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(ink)
        }
        val rail = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(12), dp(12))
            background = rounded(panel, 0, 0, 0, 0)
        }
        root.addView(rail, LinearLayout.LayoutParams(dp(204), LinearLayout.LayoutParams.MATCH_PARENT))

        val brand = TextView(this).apply {
            text = "SMART\nTROLLEY OS"
            setTextColor(white)
            textSize = 20f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), 0, 0, dp(10))
        }
        rail.addView(brand, LinearLayout.LayoutParams(-1, dp(76)))

        addNavButton(rail, "HOME", PAGE_HOME)
        addNavButton(rail, "NAVIGATION", PAGE_NAV)
        addNavButton(rail, "RAILWAY", PAGE_RAIL)
        addNavButton(rail, "AIRPORT", PAGE_AIRPORT)
        addNavButton(rail, "TROLLEY", PAGE_TROLLEY)
        val admin = textButton("ADMIN  ·  MAINTENANCE", accent, ink, 12f).apply {
            setOnClickListener { showPage(PAGE_ADMIN) }
        }
        rail.addView(admin, LinearLayout.LayoutParams(-1, dp(44)).apply { topMargin = dp(8) })

        val spacer = View(this)
        rail.addView(spacer, LinearLayout.LayoutParams(1, 0, 1f))
        batteryLabel = TextView(this).apply {
            setTextColor(white)
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
        }
        networkLabel = TextView(this).apply {
            setTextColor(muted)
            textSize = 12f
            setPadding(0, dp(5), 0, 0)
        }
        rail.addView(batteryLabel)
        rail.addView(networkLabel)

        val main = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(8))
        }
        root.addView(main, LinearLayout.LayoutParams(0, -1, 1f))
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val pageTitle = TextView(this).apply {
            tag = "pageTitle"
            setTextColor(white)
            textSize = 21f
            typeface = Typeface.DEFAULT_BOLD
        }
        header.addView(pageTitle, LinearLayout.LayoutParams(0, -1, 1f))
        timeLabel = TextView(this).apply {
            setTextColor(white)
            textSize = 17f
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(timeLabel)
        main.addView(header, LinearLayout.LayoutParams(-1, dp(54)))
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        main.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        refreshStatus()
    }

    private fun addNavButton(parent: LinearLayout, label: String, page: String) {
        val button = textButton(label, panelLight, white, 14f).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), 0, dp(8), 0)
            setOnClickListener { showPage(page) }
        }
        val params = LinearLayout.LayoutParams(-1, dp(48)).apply { bottomMargin = dp(4) }
        parent.addView(button, params)
    }

    private fun showPage(page: String) {
        activePage = page
        val title = findViewWithTag<TextView>("pageTitle") ?: return
        title.text = when (page) {
            PAGE_NAV -> "Navigation"
            PAGE_RAIL -> "Railway Assistant"
            PAGE_AIRPORT -> "Airport Assistant"
            PAGE_TROLLEY -> "Trolley Dashboard"
            PAGE_ADMIN -> "Administrator Maintenance"
            else -> "Your journey starts here"
        }
        fields.clear()
        travelLookupResults = null
        cleanupNavigationMap()
        content.removeAllViews()
        val scroll = ScrollView(this).apply { isFillViewport = true }
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(2), dp(4), dp(2), dp(16))
        }
        scroll.addView(body)
        content.addView(scroll, LinearLayout.LayoutParams(-1, -1))
        when (page) {
            PAGE_NAV -> showNavigation(body)
            PAGE_RAIL -> showRail(body)
            PAGE_AIRPORT -> showAirport(body)
            PAGE_TROLLEY -> showTrolley(body)
            PAGE_ADMIN -> showAdmin(body)
            else -> showHome(body)
        }
        refreshStatus()
    }

    private fun showHome(body: LinearLayout) {
        addText(body, "READY FOR THE NEXT JOURNEY", accent, 12f, true, dp(8))
        addText(body, "Smart Trolley OS", white, 29f, true, dp(4))
        addText(body, "Navigation, travel information and trolley status in one place.", muted, 15f, false, dp(16))

        val firstRow = row(body)
        addHomeTile(firstRow, "01", "NAVIGATION", "Find a destination", PAGE_NAV)
        addHomeTile(firstRow, "02", "RAILWAY ASSISTANT", "Train journey details", PAGE_RAIL)
        val secondRow = row(body)
        addHomeTile(secondRow, "03", "AIRPORT ASSISTANT", "Flight journey details", PAGE_AIRPORT)
        addHomeTile(secondRow, "04", "TROLLEY DASHBOARD", "Tablet and trolley status", PAGE_TROLLEY)

        val statusCard = card(body)
        val labels = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        addMetric(labels, "WI-FI", wifiState(), 1f)
        addMetric(labels, "INTERNET", internetState, 1f)
        addMetric(labels, "TABLET BATTERY", batteryText(), 1f)
        statusCard.addView(labels)
        statusCard.layoutParams = statusCard.layoutParams.apply { height = dp(78) }
    }

    private fun addHomeTile(parent: LinearLayout, number: String, title: String, subtitle: String, page: String) {
        val tile = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(10), dp(16), dp(10))
            background = rounded(panelLight, 16)
            isClickable = true
            isFocusable = true
            setOnClickListener { showPage(page) }
        }
        addText(tile, number, accent, 13f, true, dp(4))
        addText(tile, title, white, 18f, true, dp(3))
        addText(tile, subtitle, muted, 13f, false, 0)
        val params = LinearLayout.LayoutParams(0, dp(150), 1f).apply {
            marginEnd = dp(10)
            bottomMargin = dp(10)
        }
        parent.addView(tile, params)
    }

    private fun showNavigation(body: LinearLayout) {
        addText(body, "OUTDOOR DIRECTIONS", accent, 12f, true, dp(8))
        addText(body, "Navigation stays inside Smart Trolley OS", white, 21f, true, dp(8))
        val map = createNavigationMap()
        body.addView(map, LinearLayout.LayoutParams(-1, dp(226)).apply { bottomMargin = dp(3) })
        map.mapView?.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN,
                android.view.MotionEvent.ACTION_MOVE -> view.parent?.requestDisallowInterceptTouchEvent(true)
                android.view.MotionEvent.ACTION_UP,
                android.view.MotionEvent.ACTION_CANCEL -> view.parent?.requestDisallowInterceptTouchEvent(false)
            }
            false
        }
        addText(body, "© OpenStreetMap contributors · ODbL", muted, 11f, false, dp(6), gravity = Gravity.END)

        val status = TextView(this).apply {
            setTextColor(muted)
            textSize = 13f
            text = map.mapDescription
        }
        navigationStatus = status
        body.addView(status, LinearLayout.LayoutParams(-1, dp(32)))

        val form = card(body)
        beginForm(form, true)
        val destination = input(form, KEY_DESTINATION_QUERY, "Destination or coordinates", "Name (with proxy) or latitude, longitude")
        val searchRow = row(form)
        searchRow.addView(textButton("SEARCH / PIN", accent, ink, 13f).apply {
            setOnClickListener { searchDestination(destination.text.toString()) }
        }, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(8) })
        searchRow.addView(textButton("SAVE PLACE", panelLight, white, 13f).apply {
            setOnClickListener { saveSelectedPlace() }
        }, LinearLayout.LayoutParams(0, dp(48), 1f))

        val actionRow = row(form)
        actionRow.addView(textButton("USE MY GPS LOCATION", panelLight, white, 12f).apply {
            setOnClickListener { requestCurrentLocation() }
        }, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(8) })
        actionRow.addView(textButton("CALCULATE WALK ROUTE", panelLight, white, 12f).apply {
            setOnClickListener { calculateWalkingRoute() }
        }, LinearLayout.LayoutParams(0, dp(48), 1f))

        navigationResults = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(navigationResults, LinearLayout.LayoutParams(-1, -2))
        navigationRouteSummary = TextView(this).apply {
            setTextColor(white)
            textSize = 13f
        }
        val routeCard = card(body)
        addText(routeCard, "ROUTE", accent, 12f, true, dp(5))
        routeCard.addView(navigationRouteSummary, LinearLayout.LayoutParams(-1, -2))
        navigationRouteSummary?.text = formValues[KEY_ROUTE_SUMMARY] ?: "No route calculated. A configured routing service is required."

        addNavigationPlacesSection(body, PREF_SAVED_PLACES, "SAVED DESTINATIONS", "No saved destinations yet")
        addNavigationPlacesSection(body, PREF_RECENT_PLACES, "RECENT DESTINATIONS", "Coordinates you pin appear here")
        addNote(body, "Search accepts latitude, longitude offline. Place-name geocoding and walking routes need a configured authorized HTTPS proxy. GPS is used only after you request it; indoor positioning is not provided.")

        val savedLat = formValues[KEY_DESTINATION_LAT]?.toDoubleOrNull()
        val savedLon = formValues[KEY_DESTINATION_LON]?.toDoubleOrNull()
        if (savedLat != null && savedLon != null && GraphHopperProxyProvider.validCoordinates(savedLat, savedLon)) {
            updateDestinationMarker(
                NavigationPlace(formValues[KEY_DESTINATION_NAME] ?: "Selected destination", TravelCoordinate(savedLat, savedLon)),
                centerMap = false
            )
        }
    }

    private fun createNavigationMap(): OfflineMapSurface {
        val center = TravelCoordinate(
            formValues[KEY_MAP_LAT]?.toDoubleOrNull() ?: DEFAULT_MAP_LATITUDE,
            formValues[KEY_MAP_LON]?.toDoubleOrNull() ?: DEFAULT_MAP_LONGITUDE
        )
        val zoom = formValues[KEY_MAP_ZOOM]?.toDoubleOrNull() ?: DEFAULT_MAP_ZOOM
        return OfflineMapSurface(this, center, zoom) { point ->
            val label = String.format(Locale.getDefault(), "Pinned %.5f, %.5f", point.latitude, point.longitude)
            formValues[KEY_DESTINATION_QUERY] = "${point.latitude}, ${point.longitude}"
            fields[KEY_DESTINATION_QUERY]?.setText(formValues[KEY_DESTINATION_QUERY])
            setDestination(NavigationPlace(label, point), true)
        }.also { mapView = it }
    }

    private fun searchDestination(query: String) {
        val normalized = query.trim()
        if (normalized.isEmpty()) {
            fields[KEY_DESTINATION_QUERY]?.error = "Enter a place or coordinates"
            return
        }
        val coordinates = parseCoordinates(normalized)
        if (coordinates != null) {
            setDestination(NavigationPlace("${coordinates.latitude}, ${coordinates.longitude}", coordinates), true)
            navigationStatus?.text = "Coordinate pinned. No geocoding or route was invented."
            return
        }
        val provider = navigationProvider
        if (provider == null) {
            navigationStatus?.text = "Place-name search is unavailable: configure an authorized HTTPS navigation proxy or enter coordinates."
            return
        }
        navigationStatus?.text = "Searching the configured place service…"
        navigationResults?.removeAllViews()
        navigationExecutor.execute {
            try {
                val places = provider.search(normalized)
                handler.post {
                    if (activePage != PAGE_NAV || mapView == null) return@post
                    navigationResults?.removeAllViews()
                    if (places.isEmpty()) {
                        navigationStatus?.text = "No matching places returned. Try coordinates or another query."
                    } else {
                        navigationStatus?.text = "Choose a search result to place it on the map."
                        places.forEach { place ->
                            navigationResults?.addView(textButton(place.name, panelLight, white, 13f).apply {
                                setOnClickListener { setDestination(place, true) }
                            }, LinearLayout.LayoutParams(-1, dp(44)).apply { bottomMargin = dp(4) })
                        }
                    }
                }
            } catch (exception: Exception) {
                handler.post {
                    if (activePage == PAGE_NAV) {
                        navigationStatus?.text = "Place search failed: ${exception.message ?: "network unavailable"}"
                    }
                }
            }
        }
    }

    private fun parseCoordinates(query: String): TravelCoordinate? {
        val parts = query.split(',', ' ', '\t').filter { it.isNotBlank() }
        if (parts.size != 2) return null
        val latitude = parts[0].toDoubleOrNull() ?: return null
        val longitude = parts[1].toDoubleOrNull() ?: return null
        if (!GraphHopperProxyProvider.validCoordinates(latitude, longitude)) return null
        return TravelCoordinate(latitude, longitude)
    }

    private fun setDestination(place: NavigationPlace, addToRecent: Boolean) {
        formValues[KEY_DESTINATION_NAME] = place.name
        formValues[KEY_DESTINATION_LAT] = place.point.latitude.toString()
        formValues[KEY_DESTINATION_LON] = place.point.longitude.toString()
        fields[KEY_DESTINATION_QUERY]?.setText("${place.point.latitude}, ${place.point.longitude}")
        updateDestinationMarker(place)
        mapView?.clearRoute()
        navigationRouteSummary?.text = "Destination selected; no route has been calculated."
        formValues.remove(KEY_ROUTE_SUMMARY)
        navigationStatus?.text = "Destination marker placed."
        if (addToRecent) addPlaceToList(PREF_RECENT_PLACES, place, MAX_RECENT_PLACES)
    }

    private fun updateDestinationMarker(place: NavigationPlace, centerMap: Boolean = true) {
        mapView?.showDestination(place.point, centerMap)
    }

    private fun saveSelectedPlace() {
        val name = formValues[KEY_DESTINATION_NAME]
        val latitude = formValues[KEY_DESTINATION_LAT]?.toDoubleOrNull()
        val longitude = formValues[KEY_DESTINATION_LON]?.toDoubleOrNull()
        if (name == null || latitude == null || longitude == null) {
            navigationStatus?.text = "Pin or search a destination before saving it."
            return
        }
        addPlaceToList(PREF_SAVED_PLACES, NavigationPlace(name, TravelCoordinate(latitude, longitude)), MAX_SAVED_PLACES)
        navigationStatus?.text = "Destination saved on this tablet."
        showPage(PAGE_NAV)
    }

    private fun addNavigationPlacesSection(body: LinearLayout, key: String, title: String, emptyMessage: String) {
        val places = readPlaceList(key)
        val section = card(body)
        addText(section, title, accent, 12f, true, dp(6))
        if (places.isEmpty()) {
            addText(section, emptyMessage, muted, 13f, false, 0)
        } else {
            places.forEach { place ->
                section.addView(textButton(place.name, panelLight, white, 13f).apply {
                    setOnClickListener {
                        formValues[KEY_DESTINATION_QUERY] = "${place.point.latitude}, ${place.point.longitude}"
                        fields[KEY_DESTINATION_QUERY]?.setText(formValues[KEY_DESTINATION_QUERY])
                        setDestination(place, key == PREF_SAVED_PLACES)
                    }
                }, LinearLayout.LayoutParams(-1, dp(42)).apply { bottomMargin = dp(4) })
            }
        }
    }

    private fun readPlaceList(key: String): List<NavigationPlace> {
        val stored = getSharedPreferences(PREF_NAVIGATION, Context.MODE_PRIVATE).getString(key, "[]") ?: "[]"
        return try {
            val values = JSONArray(stored)
            (0 until values.length()).mapNotNull { index ->
                val value = values.optJSONObject(index) ?: return@mapNotNull null
                val latitude = value.optDouble("lat", Double.NaN)
                val longitude = value.optDouble("lon", Double.NaN)
                if (!GraphHopperProxyProvider.validCoordinates(latitude, longitude)) return@mapNotNull null
                NavigationPlace(value.optString("name", "Saved location"), TravelCoordinate(latitude, longitude))
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun addPlaceToList(key: String, place: NavigationPlace, maxItems: Int) {
        val places = (listOf(place) + readPlaceList(key)).distinctBy {
            "${it.point.latitude},${it.point.longitude}"
        }.take(maxItems)
        val values = JSONArray()
        places.forEach {
            values.put(JSONObject().put("name", it.name).put("lat", it.point.latitude).put("lon", it.point.longitude))
        }
        getSharedPreferences(PREF_NAVIGATION, Context.MODE_PRIVATE).edit().putString(key, values.toString()).apply()
    }

    private fun requestCurrentLocation() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), REQUEST_LOCATION)
            return
        }
        locationRequested = true
        startGpsUpdates()
    }

    private fun startGpsUpdates() {
        if (!locationRequested || activePage != PAGE_NAV || mapView == null) return
        try {
            if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                navigationStatus?.text = "GPS hardware is reported by Android, but the GPS provider is disabled in Location settings."
                return
            }
            val lastKnown = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            if (lastKnown != null) {
                currentLocation = lastKnown.toTravelCoordinate()
                updateCurrentLocationMarker(lastKnown)
                navigationStatus?.text = "Last known GPS fix · accuracy ±${lastKnown.accuracy.toInt()} m; waiting for an update."
            } else {
                navigationStatus?.text = "Waiting for a GPS fix. Indoor position is not available."
            }
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5000L, 10f, gpsListener, Looper.getMainLooper())
        } catch (_: SecurityException) {
            navigationStatus?.text = "Location permission is unavailable."
        } catch (exception: Exception) {
            navigationStatus?.text = "GPS unavailable: ${exception.message ?: "provider error"}"
        }
    }

    private fun stopGpsUpdates() {
        try {
            locationManager.removeUpdates(gpsListener)
        } catch (_: Exception) {
        }
    }

    private fun updateCurrentLocationMarker(location: Location, center: Boolean = true) {
        mapView?.showCurrentLocation(location.toTravelCoordinate(), center)
    }

    private fun calculateWalkingRoute() {
        val provider = navigationProvider
        if (provider == null) {
            navigationStatus?.text = "No route calculated. Configure a private HTTPS routing proxy; no route is simulated."
            return
        }
        val origin = currentLocation
        val destinationLat = formValues[KEY_DESTINATION_LAT]?.toDoubleOrNull()
        val destinationLon = formValues[KEY_DESTINATION_LON]?.toDoubleOrNull()
        if (origin == null) {
            navigationStatus?.text = "A current GPS fix is needed as the route origin."
            requestCurrentLocation()
            return
        }
        if (destinationLat == null || destinationLon == null) {
            navigationStatus?.text = "Select a destination marker before calculating a route."
            return
        }
        val destination = TravelCoordinate(destinationLat, destinationLon)
        navigationStatus?.text = "Calculating a walking route…"
        navigationExecutor.execute {
            try {
                val route = provider.route(origin, destination)
                handler.post {
                    if (activePage != PAGE_NAV || mapView == null) return@post
                    mapView?.showRoute(route.points, accent, dp(5).toFloat())
                    val details = buildString {
                        append("REAL ROUTE · ")
                        append(GraphHopperProxyProvider.formatDistance(route.distanceMeters))
                        append(" · ")
                        append(GraphHopperProxyProvider.formatDuration(route.durationMillis))
                        route.instructions.take(8).forEachIndexed { index, instruction ->
                            append("\n${index + 1}. $instruction")
                        }
                    }
                    formValues[KEY_ROUTE_SUMMARY] = details
                    navigationRouteSummary?.text = details
                    navigationStatus?.text = "Route returned by the configured routing service."
                }
            } catch (exception: Exception) {
                handler.post {
                    if (activePage == PAGE_NAV) {
                        navigationStatus?.text = "Route unavailable: ${exception.message ?: "service error"}. No route is shown."
                    }
                }
            }
        }
    }

    private fun hasNetworkConnection(): Boolean {
        val manager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return manager.activeNetworkInfo?.isConnected == true
    }

    private fun saveMapViewport() {
        val map = mapView ?: return
        val center = map.center() ?: return
        formValues[KEY_MAP_LAT] = center.latitude.toString()
        formValues[KEY_MAP_LON] = center.longitude.toString()
        formValues[KEY_MAP_ZOOM] = map.zoom()?.toString() ?: DEFAULT_MAP_ZOOM.toString()
    }

    private fun cleanupNavigationMap() {
        saveMapViewport()
        locationRequested = false
        stopGpsUpdates()
        mapView?.dispose()
        mapView = null
        navigationStatus = null
        navigationResults = null
        navigationRouteSummary = null
        formValues.remove(KEY_ROUTE_SUMMARY)
    }

    private fun showRail(body: LinearLayout) {
        addText(body, "INDIAN RAILWAYS", accent, 12f, true, dp(6))
        addText(body, "Railway Assistant", white, 24f, true, dp(4))
        addText(body, "Live railway boards are blocked pending an authorized data provider.", amber, 14f, false, dp(12))
        val form = card(body)
        beginForm(form)
        val train = input(form, "rail_train", "Train number or name", "Train number or name")
        val station = input(form, "rail_station", "Railway station", "Station name or code")
        val arrival = input(form, "rail_arrival", "Arrival time", "HH:MM or not provided")
        val departure = input(form, "rail_departure", "Departure time", "HH:MM or not provided")
        val platform = input(form, "rail_platform", "Platform number", "Not provided")
        val status = input(form, "rail_status", "Train status", "Enter status or not provided")
        val actions = row(form)
        actions.addView(textButton("SAVE MANUAL DETAILS", accent, ink, 13f).apply {
            setOnClickListener {
                if (train.text.toString().trim().isEmpty() && station.text.toString().trim().isEmpty()) {
                    train.error = "Enter a train or station"
                } else {
                    saveManualResult("rail", "MANUAL ENTRY · NOT LIVE", listOf(
                        "Train" to train.text.toString(), "Station" to station.text.toString(),
                        "Arrival" to arrival.text.toString(), "Departure" to departure.text.toString(),
                        "Platform" to platform.text.toString(), "Train status" to status.text.toString()
                    ))
                    hideKeyboard()
                    showPage(PAGE_RAIL)
                }
            }
        }, LinearLayout.LayoutParams(0, dp(54), 1f).apply { marginEnd = dp(8) })
        actions.addView(textButton("LOAD SIMULATION", panelLight, white, 13f).apply {
            setOnClickListener { showDemoRailwayBoard(station.text.toString()) }
        }, LinearLayout.LayoutParams(0, dp(54), 1f))
        body.addView(textButton("SAVE STATION AS LOCAL MANUAL REFERENCE", panelLight, white, 13f).apply {
            setOnClickListener {
                if (travelReferenceDatabase.saveManualStation(station.text.toString())) {
                    showMessage("Saved locally as MANUAL reference; no schedule or coordinates implied")
                } else {
                    station.error = "Enter a station name or code"
                }
            }
        }, LinearLayout.LayoutParams(-1, dp(48)).apply { bottomMargin = dp(6) })
        body.addView(textButton("SEARCH LOCAL STATIONS / CHECK PROVIDER", accent, ink, 13f).apply {
            setOnClickListener { lookupRailway(station.text.toString()) }
        }, LinearLayout.LayoutParams(-1, dp(48)).apply { bottomMargin = dp(6) })
        body.addView(textButton("SEARCH PLACES IN OFFLINE AHMEDABAD MAP", panelLight, white, 13f).apply {
            setOnClickListener { searchRailwayMapPlaces(station.text.toString()) }
        }, LinearLayout.LayoutParams(-1, dp(48)).apply { bottomMargin = dp(6) })
        travelLookupResults = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(travelLookupResults, LinearLayout.LayoutParams(-1, -2))
        addSavedResult(body, "rail")
        addExternalButton(body, "OPEN OFFICIAL NTES", "https://enquiry.indianrail.gov.in/ntes/")
        addJourneyNavigationButton(body, "NAVIGATE TO STATION", "rail_station")
        addNote(body, "LIVE STATUS: BLOCKED BY PROVIDER ACCESS. Offline place search reads named features from the Ahmedabad–Gandhinagar map. The full-day train board and every platform shown by LOAD SIMULATION are fictional examples, not real services or station assignments. Verify travel information with official railway sources.")
    }

    private fun showAirport(body: LinearLayout) {
        addText(body, "FLIGHT DETAILS", accent, 12f, true, dp(6))
        addText(body, "Airport Assistant", white, 24f, true, dp(4))
        addText(body, "Offline airport reference search plus optional authorized live provider lookup.", muted, 14f, false, dp(12))
        val form = card(body)
        beginForm(form)
        val airport = input(form, "air_airport", "Airport", "Airport name or code")
        val flight = input(form, "air_flight", "Flight number", "Airline flight number")
        val airline = input(form, "air_airline", "Airline", "Airline name")
        val terminal = input(form, "air_terminal", "Terminal", "Not provided")
        val gate = input(form, "air_gate", "Gate", "Not provided")
        val time = input(form, "air_time", "Departure / arrival time", "HH:MM or not provided")
        val status = input(form, "air_status", "Flight status", "Enter status or not provided")
        val actions = row(form)
        actions.addView(textButton("SAVE MANUAL DETAILS", accent, ink, 13f).apply {
            setOnClickListener {
                if (flight.text.toString().trim().isEmpty() && airport.text.toString().trim().isEmpty()) {
                    flight.error = "Enter a flight or airport"
                } else {
                    saveManualResult("air", "MANUAL ENTRY · NOT LIVE", listOf(
                        "Airport" to airport.text.toString(), "Flight" to flight.text.toString(),
                        "Airline" to airline.text.toString(), "Terminal" to terminal.text.toString(),
                        "Gate" to gate.text.toString(), "Time" to time.text.toString(),
                        "Flight status" to status.text.toString()
                    ))
                    hideKeyboard()
                    showPage(PAGE_AIRPORT)
                }
            }
        }, LinearLayout.LayoutParams(0, dp(54), 1f).apply { marginEnd = dp(8) })
        actions.addView(textButton("LOAD SIMULATION", panelLight, white, 13f).apply {
            setOnClickListener {
                saveManualResult("air", "SIMULATION · NOT LIVE", listOf(
                    "Airport" to "DEMO Airport", "Flight" to "DEMO 6E000", "Airline" to "Example Air",
                    "Terminal" to "Example only", "Gate" to "Example only", "Time" to "12:00 example",
                    "Flight status" to "Simulated demonstration"
                ))
                showPage(PAGE_AIRPORT)
            }
        }, LinearLayout.LayoutParams(0, dp(54), 1f))
        body.addView(textButton("SEARCH AIRPORT REFERENCE / LIVE FLIGHTS", accent, ink, 13f).apply {
            setOnClickListener { lookupAirports(airport.text.toString()) }
        }, LinearLayout.LayoutParams(-1, dp(48)).apply { bottomMargin = dp(6) })
        travelLookupResults = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(travelLookupResults, LinearLayout.LayoutParams(-1, -2))
        addSavedResult(body, "air")
        addFlightLookupButton(body)
        addJourneyNavigationButton(body, "NAVIGATE TO AIRPORT", "air_airport")
        addNote(body, "Airport references are static OurAirports public-domain data. Live flight status, gate and terminal require configured provider credentials and are never inferred.")
        addNote(body, "Airport terminal maps and indoor positioning are not configured.")
    }

    private fun lookupRailway(query: String) {
        val value = query.trim()
        val results = travelLookupResults ?: return
        results.removeAllViews()
        if (value.isEmpty()) {
            addText(results, "Enter a station name or code.", amber, 14f, false, dp(6))
            return
        }
        addText(results, "LOCAL MANUAL REFERENCES · NOT A TIMETABLE", amber, 12f, true, dp(6))
        val localStations = travelReferenceDatabase.findManualStations(value)
        if (localStations.isEmpty()) {
            addText(results, "No saved local station reference matches.", muted, 13f, false, dp(4))
        } else {
            localStations.forEach { station ->
                results.addView(textButton(station, panelLight, white, 14f).apply {
                    setOnClickListener { fields["rail_station"]?.setText(station) }
                }, LinearLayout.LayoutParams(-1, dp(42)).apply { bottomMargin = dp(4) })
            }
        }
        val api = configuredTravelApi()
        if (api == null) {
            addText(results, "LIVE: BLOCKED BY PROVIDER ACCESS · no authorized railway provider configured.", amber, 13f, false, dp(8))
            return
        }
        addText(results, "Checking configured railway provider…", muted, 13f, false, dp(6))
        navigationExecutor.execute {
            try {
                val response = api.get("api/railways/stations/search", mapOf("q" to value))
                handler.post { if (activePage == PAGE_RAIL) renderTravelEnvelope(results, response) }
            } catch (error: Exception) {
                handler.post {
                    if (activePage == PAGE_RAIL) addText(results, "Railway lookup unavailable: ${error.message ?: "connection failed"}", amber, 13f, false, dp(6))
                }
            }
        }
    }

    private fun searchRailwayMapPlaces(query: String) {
        val value = query.trim()
        val results = travelLookupResults ?: return
        results.removeAllViews()
        if (value.isEmpty()) {
            addText(results, "Enter a place name, station, landmark or area to search the offline map.", amber, 14f, false, dp(6))
            return
        }
        val mapFile = File(getExternalFilesDir(null) ?: filesDir, "maps")
            .listFiles()?.filter { it.isFile && it.extension.equals("map", true) }
            ?.sortedBy { it.name }?.firstOrNull()
        if (mapFile == null) {
            addText(results, "OFFLINE MAP NOT FOUND. Copy the Mapsforge map into the app's maps folder first.", amber, 13f, false, dp(8))
            return
        }
        addText(results, "SEARCHING OFFLINE MAP FEATURES · Ahmedabad–Gandhinagar · OSM reference", accent, 12f, true, dp(6))
        navigationExecutor.execute {
            try {
                val places = AhmedabadMapPlaceSearch.search(mapFile, value)
                handler.post {
                    if (activePage != PAGE_RAIL) return@post
                    if (places.isEmpty()) {
                        addText(results, "No matching named map features found for ‘$value’. Try a shorter place name or landmark.", muted, 13f, false, dp(6))
                    } else {
                        addText(results, "${places.size} OFFLINE MAP RESULT(S) · NOT LIVE TRANSIT DATA", muted, 12f, true, dp(5))
                        places.forEach { place ->
                            val label = "${place.name}  ·  ${String.format(Locale.US, "%.5f, %.5f", place.coordinate.latitude, place.coordinate.longitude)}"
                            results.addView(textButton(label, panelLight, white, 13f).apply {
                                setOnClickListener { openReferenceOnMap(place.name, place.coordinate.latitude, place.coordinate.longitude) }
                            }, LinearLayout.LayoutParams(-1, dp(46)).apply { bottomMargin = dp(4) })
                        }
                    }
                }
            } catch (error: Exception) {
                handler.post {
                    if (activePage == PAGE_RAIL) addText(results, "Offline map search failed: ${error.message ?: "map data could not be read"}", amber, 13f, false, dp(6))
                }
            }
        }
    }

    private fun showDemoRailwayBoard(stationQuery: String) {
        val stationName = stationQuery.trim().ifEmpty { "Example station (not a real station result)" }
        val results = travelLookupResults ?: return
        results.removeAllViews()
        addText(results, "SIMULATION · NOT LIVE · FICTIONAL EXAMPLE ONLY", amber, 14f, true, dp(8))
        addText(results, "Illustrative full-day board for: $stationName", white, 16f, true, dp(4))
        addText(results, "These train names, times and platform numbers are invented demonstration values. They do not describe actual railway services or assignments.", muted, 12f, false, dp(8))
        DemoRailwayBoard.trains.forEach { train ->
            val item = card(results)
            addText(item, "${train.time}  ·  ${train.name}", white, 14f, true, dp(3))
            addText(item, "Platform ${train.platform} (SIMULATED)  ·  Example status: DEMO", amber, 12f, false, dp(2))
        }
    }

    private fun lookupAirports(query: String) {
        val value = query.trim()
        val results = travelLookupResults ?: return
        results.removeAllViews()
        if (value.isEmpty()) {
            addText(results, "Enter an airport name, city or code.", amber, 14f, false, dp(6))
            return
        }
        addText(results, "OFFLINE AIRPORT REFERENCE · STATIC DATA", accent, 12f, true, dp(6))
        val airports = travelReferenceDatabase.findAirports(value)
        if (airports.isEmpty()) {
            addText(results, "No local airport reference found. This database currently includes AMD, BOM, DEL and BLR.", muted, 13f, false, dp(4))
        }
        airports.forEach { airport ->
            val item = card(results)
            addText(item, "${airport.name} · ${airport.iata} / ${airport.icao}", white, 14f, true, dp(4))
            addText(item, "${airport.city}, ${airport.country} · ${airport.latitude}, ${airport.longitude} · REFERENCE", muted, 12f, false, dp(4))
            val actions = row(item)
            actions.addView(textButton("SHOW ON MAP", panelLight, white, 12f).apply {
                setOnClickListener { openReferenceOnMap(airport.name, airport.latitude, airport.longitude) }
            }, LinearLayout.LayoutParams(0, dp(42), 1f).apply { marginEnd = dp(5) })
            actions.addView(textButton("ARRIVALS", panelLight, white, 12f).apply {
                setOnClickListener { lookupFlights(airport.iata, "arrivals") }
            }, LinearLayout.LayoutParams(0, dp(42), 1f).apply { marginEnd = dp(5) })
            actions.addView(textButton("DEPARTURES", panelLight, white, 12f).apply {
                setOnClickListener { lookupFlights(airport.iata, "departures") }
            }, LinearLayout.LayoutParams(0, dp(42), 1f))
        }
        if (airports.isEmpty()) lookupFlightByNumber(value)
    }

    private fun lookupFlights(airportCode: String, direction: String) {
        val results = travelLookupResults ?: return
        val api = configuredTravelApi()
        if (api == null) {
            addText(results, "LIVE FLIGHTS: NOT CONFIGURED · set an authorized HTTPS travel API host in Administrator.", amber, 13f, false, dp(8))
            return
        }
        addText(results, "Loading $direction for $airportCode…", muted, 13f, false, dp(6))
        navigationExecutor.execute {
            try {
                val response = api.get("api/airports/${airportCode}/flights", mapOf("direction" to direction))
                handler.post { if (activePage == PAGE_AIRPORT) renderTravelEnvelope(results, response) }
            } catch (error: Exception) {
                handler.post { if (activePage == PAGE_AIRPORT) addText(results, "Flight lookup unavailable: ${error.message ?: "connection failed"}", amber, 13f, false, dp(6)) }
            }
        }
    }

    private fun lookupFlightByNumber(flightNumber: String) {
        val results = travelLookupResults ?: return
        val api = configuredTravelApi()
        if (api == null) {
            addText(results, "No local airport match. Live flight lookup is not configured.", amber, 13f, false, dp(6))
            return
        }
        navigationExecutor.execute {
            try {
                val normalizedNumber = flightNumber.filterNot { it.isWhitespace() }
                if (!normalizedNumber.matches(Regex("[A-Za-z0-9]{2,16}"))) throw IllegalArgumentException("Enter a valid flight number")
                val response = api.get("api/flights/$normalizedNumber")
                handler.post { if (activePage == PAGE_AIRPORT) renderTravelEnvelope(results, response) }
            } catch (error: Exception) {
                handler.post { if (activePage == PAGE_AIRPORT) addText(results, "Flight lookup unavailable: ${error.message ?: "connection failed"}", amber, 13f, false, dp(6)) }
            }
        }
    }

    private fun renderTravelEnvelope(parent: LinearLayout, response: JSONObject) {
        val category = response.optString("category", "UNKNOWN").uppercase(Locale.ROOT)
        val provider = response.optString("provider", "Unknown provider")
        val sourceTime = response.optString("source_timestamp", "Not supplied")
        addText(parent, "$category · $provider · source time: $sourceTime", if (category == "LIVE") accent else amber, 12f, true, dp(6))
        val message = response.optString("message")
        if (message.isNotBlank()) addText(parent, message, muted, 13f, false, dp(4))
        val records = response.optJSONArray("records") ?: return
        if (records.length() == 0 && message.isBlank()) addText(parent, "No records returned.", muted, 13f, false, dp(4))
        for (index in 0 until records.length()) {
            val record = records.optJSONObject(index) ?: continue
            val item = card(parent)
            val keys = record.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                if (!record.isNull(key)) addMetricRow(item, key.replace('_', ' ').uppercase(Locale.ROOT), record.optString(key))
            }
        }
    }

    private fun configuredTravelApi(): TravelApiClient? {
        val preferences = getSharedPreferences(PREF_TRAVEL_API, Context.MODE_PRIVATE)
        val baseUrl = preferences.getString(KEY_TRAVEL_API_BASE_URL, BuildConfig.TRAVEL_API_BASE_URL).orEmpty()
        val token = preferences.getString(KEY_TRAVEL_API_TOKEN, null)
        return TravelApiClient.configured(baseUrl, token)
    }

    private fun openReferenceOnMap(name: String, latitude: Double, longitude: Double) {
        formValues[KEY_DESTINATION_NAME] = name
        formValues[KEY_DESTINATION_LAT] = latitude.toString()
        formValues[KEY_DESTINATION_LON] = longitude.toString()
        formValues[KEY_DESTINATION_QUERY] = "$latitude, $longitude"
        formValues[KEY_MAP_LAT] = latitude.toString()
        formValues[KEY_MAP_LON] = longitude.toString()
        formValues[KEY_MAP_ZOOM] = "13"
        showPage(PAGE_NAV)
    }

    private fun showTrolley(body: LinearLayout) {
        addText(body, "SYSTEM STATUS", accent, 12f, true, dp(6))
        addText(body, "Trolley Dashboard", white, 24f, true, dp(12))
        val device = card(body)
        addMetricRow(device, "Tablet battery", batteryText())
        addMetricRow(device, "Charging state", chargingText())
        addMetricRow(device, "Wi-Fi", wifiState())
        addMetricRow(device, "Internet check", internetState)
        addMetricRow(device, "Current time", SimpleDateFormat("EEE, d MMM  ·  HH:mm:ss", Locale.getDefault()).format(Date()))
        addMetricRow(device, "Device", "${Build.MANUFACTURER} ${Build.MODEL}")
        addMetricRow(device, "Android", "${Build.VERSION.RELEASE} · API ${Build.VERSION.SDK_INT}")
        addMetricRow(device, "Smart Trolley OS", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        val external = card(body)
        addText(external, "EXTERNAL TROLLEY BATTERY", amber, 13f, true, dp(5))
        addText(external, "Not connected · no trolley battery sensor is configured", white, 16f, true, dp(5))
        addNote(body, "The tablet reports only its own battery and connectivity. No trolley battery, motor or charging controller is attached or controlled by this software.")
    }

    private fun showAdmin(body: LinearLayout) {
        addText(body, "ADMINISTRATOR ROUTE", amber, 12f, true, dp(8))
        addText(body, "Maintenance and recovery", white, 24f, true, dp(8))
        addNote(body, "This is a convenience launcher, not a security kiosk. Android system screens, hardware controls and system error screens can remain accessible.")
        val apiCard = card(body)
        addText(apiCard, "TRAVEL API · HTTPS ONLY", accent, 13f, true, dp(6))
        beginForm(apiCard, true)
        val preferences = getSharedPreferences(PREF_TRAVEL_API, Context.MODE_PRIVATE)
        val apiUrl = input(apiCard, KEY_TRAVEL_API_BASE_URL, "Backend base URL", "https://your-host.example/")
        val apiToken = input(apiCard, KEY_TRAVEL_API_TOKEN, "Backend client token (optional)", "Client token only; never provider keys")
        apiUrl.setText(preferences.getString(KEY_TRAVEL_API_BASE_URL, BuildConfig.TRAVEL_API_BASE_URL).orEmpty())
        apiToken.setText(preferences.getString(KEY_TRAVEL_API_TOKEN, "").orEmpty())
        apiCard.addView(textButton("SAVE AND TEST BACKEND", panelLight, white, 13f).apply {
            setOnClickListener {
                val configured = TravelApiClient.configured(apiUrl.text.toString().trim(), apiToken.text.toString())
                if (configured == null) {
                    apiUrl.error = "Enter a valid HTTPS base URL"
                    return@setOnClickListener
                }
                preferences.edit()
                    .putString(KEY_TRAVEL_API_BASE_URL, apiUrl.text.toString().trim().trimEnd('/') + "/")
                    .putString(KEY_TRAVEL_API_TOKEN, apiToken.text.toString()).apply()
                showMessage("Saved. Testing HTTPS health endpoint…")
                probeExecutor.execute {
                    try {
                        val result = configured.get("api/health")
                        handler.post { if (activePage == PAGE_ADMIN) showMessage("Backend health: ${result.optString("status", "unknown")}") }
                    } catch (error: Exception) {
                        handler.post { if (activePage == PAGE_ADMIN) showMessage("Backend not reachable: ${error.message ?: "connection failed"}") }
                    }
                }
            }
        }, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(5) })
        addNote(apiCard, "Provider secrets stay on the backend. The tablet validates TLS certificates; use a publicly trusted chain compatible with Android 4.4.")
        addExternalButton(body, "OPEN ANDROID SETTINGS", "settings:")
        body.addView(textButton("RETURN TO DASHBOARD", accent, ink, 15f).apply {
            setOnClickListener { showPage(PAGE_HOME) }
        }, LinearLayout.LayoutParams(-1, dp(54)))
        addNote(body, "To choose another home screen, open Android Settings and clear this app’s default launcher preference, then press the tablet’s Home button. ADB recovery and rollback steps are documented in README.md.")
    }

    private fun input(parent: LinearLayout, key: String, label: String, hint: String): EditText {
        val wrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(2), dp(10), dp(4))
        }
        addText(wrapper, label.uppercase(Locale.getDefault()), muted, 11f, true, dp(3))
        val field = EditText(this).apply {
            setSingleLine(true)
            setTextColor(white)
            setHintTextColor(muted)
            textSize = 16f
            this.hint = hint
            setPadding(dp(12), 0, dp(12), 0)
            background = rounded(panelLight, 10)
            setText(formValues[key] ?: "")
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(text: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(text: CharSequence?, start: Int, before: Int, count: Int) {
                    formValues[key] = text?.toString().orEmpty()
                }
                override fun afterTextChanged(editable: Editable?) = Unit
            })
        }
        fields[key] = field
        wrapper.addView(field, LinearLayout.LayoutParams(-1, dp(48)))
        if (formWide) {
            parent.addView(wrapper, LinearLayout.LayoutParams(-1, dp(78)))
        } else {
            if (formParent !== parent || formColumn == 0 || formRow == null) {
                formRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                formParent = parent
                formColumn = 0
                parent.addView(formRow, LinearLayout.LayoutParams(-1, dp(78)))
            }
            formRow?.addView(wrapper, LinearLayout.LayoutParams(0, dp(78), 1f))
            formColumn += 1
            if (formColumn == 2) formColumn = 0
        }
        return field
    }

    private fun beginForm(parent: LinearLayout, wide: Boolean = false) {
        formParent = parent
        formRow = null
        formColumn = 0
        formWide = wide
    }

    private fun saveManualResult(prefix: String, source: String, values: List<Pair<String, String>>) {
        formValues["${prefix}_source"] = source
        values.forEachIndexed { index, pair ->
            formValues["${prefix}_result_$index"] = pair.first + "\n" + pair.second.ifBlank { "Not provided" }
        }
        formValues["${prefix}_result_count"] = values.size.toString()
    }

    private fun addSavedResult(body: LinearLayout, prefix: String) {
        val source = formValues["${prefix}_source"] ?: return
        val result = card(body)
        val color = if (source.startsWith("SIMULATION")) amber else accent
        addText(result, source, color, 13f, true, dp(8))
        val count = formValues["${prefix}_result_count"]?.toIntOrNull() ?: 0
        repeat(count) { index ->
            val value = formValues["${prefix}_result_$index"] ?: return@repeat
            val parts = value.split("\n", limit = 2)
            addMetricRow(result, parts[0], parts.getOrElse(1) { "Not provided" })
        }
    }

    private fun addResult(body: LinearLayout, label: String, value: String) {
        val result = card(body)
        addText(result, label, accent, 12f, true, dp(5))
        addText(result, value, white, 17f, true, 0)
    }

    private fun addExternalButton(body: LinearLayout, label: String, address: String) {
        val button = textButton(label, panelLight, white, 14f).apply {
            setOnClickListener {
                if (address == "settings:") {
                    try {
                        startActivity(Intent(Settings.ACTION_SETTINGS))
                    } catch (_: Exception) {
                        showMessage("Android Settings is unavailable")
                    }
                } else {
                    openUrl(address)
                }
            }
        }
        body.addView(button, LinearLayout.LayoutParams(-1, dp(52)).apply { bottomMargin = dp(8) })
    }

    private fun addJourneyNavigationButton(body: LinearLayout, label: String, fieldKey: String) {
        body.addView(textButton(label, panelLight, white, 14f).apply {
            setOnClickListener {
                val destination = formValues[fieldKey]?.trim().orEmpty()
                if (destination.isEmpty()) {
                    showMessage("Enter a destination first")
                } else {
                    formValues[KEY_DESTINATION_QUERY] = destination
                    showPage(PAGE_NAV)
                    searchDestination(destination)
                }
            }
        }, LinearLayout.LayoutParams(-1, dp(52)).apply { bottomMargin = dp(8) })
    }

    private fun addFlightLookupButton(body: LinearLayout) {
        body.addView(textButton("LOOK UP FLIGHT EXTERNALLY", panelLight, white, 14f).apply {
            setOnClickListener {
                val flight = formValues["air_flight"]?.trim().orEmpty()
                if (flight.isEmpty()) {
                    showMessage("Enter a flight number first")
                } else {
                    openUrl("https://www.flightaware.com/live/flight/${Uri.encode(flight)}")
                }
            }
        }, LinearLayout.LayoutParams(-1, dp(52)).apply { bottomMargin = dp(8) })
    }

    private fun addNote(parent: LinearLayout, text: String) {
        val box = card(parent)
        addText(box, text, muted, 13f, false, 0)
    }

    private fun addMetricRow(parent: LinearLayout, label: String, value: String) {
        val line = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(8), dp(4), dp(8))
        }
        addText(line, label, muted, 14f, false, 0, 1f)
        addText(line, value, white, 15f, true, 0, 1.2f, Gravity.END)
        line.getChildAt(1).tag = "metric:$label"
        parent.addView(line, LinearLayout.LayoutParams(-1, dp(42)))
        val divider = View(this).apply { setBackgroundColor(Color.rgb(43, 61, 79)) }
        parent.addView(divider, LinearLayout.LayoutParams(-1, dp(1)))
    }

    private fun addMetric(parent: LinearLayout, label: String, value: String, weight: Float) {
        val block = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        addText(block, label, muted, 10f, true, dp(2))
        addText(block, value, white, 14f, true, 0)
        block.getChildAt(1).tag = "metric:$label"
        parent.addView(block, LinearLayout.LayoutParams(0, -2, weight))
    }

    private fun addText(
        parent: LinearLayout,
        text: String,
        color: Int,
        size: Float,
        bold: Boolean,
        bottom: Int,
        weight: Float = 0f,
        gravity: Int = Gravity.START
    ) {
        val view = TextView(this).apply {
            this.text = text
            setTextColor(color)
            textSize = size
            if (bold) typeface = Typeface.DEFAULT_BOLD
            this.gravity = gravity or Gravity.CENTER_VERTICAL
        }
        val params = if (weight > 0f) LinearLayout.LayoutParams(0, -2, weight)
        else LinearLayout.LayoutParams(-1, -2)
        params.bottomMargin = bottom
        parent.addView(view, params)
    }

    private fun row(parent: LinearLayout): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            parent.addView(this, LinearLayout.LayoutParams(-1, -2))
        }
    }

    private fun card(parent: LinearLayout): LinearLayout {
        val view = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = rounded(panel, 14)
        }
        parent.addView(view, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(9) })
        return view
    }

    private fun textButton(label: String, backgroundColor: Int, textColor: Int, size: Float): Button {
        return Button(this).apply {
            text = label
            textSize = size
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(textColor)
            background = rounded(backgroundColor, 12)
            isAllCaps = false
            minHeight = 0
            minimumHeight = 0
            minWidth = 0
            minimumWidth = 0
        }
    }

    private fun rounded(color: Int, radius: Int, topLeft: Int = radius, topRight: Int = radius, bottomLeft: Int = radius): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadii = floatArrayOf(
                dp(topLeft).toFloat(), dp(topLeft).toFloat(),
                dp(topRight).toFloat(), dp(topRight).toFloat(),
                dp(radius).toFloat(), dp(radius).toFloat(),
                dp(bottomLeft).toFloat(), dp(bottomLeft).toFloat()
            )
        }
    }

    private fun refreshStatus() {
        if (!this::batteryLabel.isInitialized) return
        if (!isNetworkConnected()) internetState = "OFFLINE"
        batteryLabel.text = if (batteryPercent >= 0) "▰  $batteryPercent%" else "▰  --%"
        networkLabel.text = "${wifiState()}  ·  $internetState"
        if (networkLabel.text.contains("OFFLINE")) networkLabel.setTextColor(amber) else networkLabel.setTextColor(muted)
        setMetricValue("WI-FI", wifiState())
        setMetricValue("INTERNET", internetState)
        setMetricValue("TABLET BATTERY", batteryText())
        setMetricValue("Tablet battery", batteryText())
        setMetricValue("Charging state", chargingText())
        setMetricValue("Wi-Fi", wifiState())
        setMetricValue("Internet check", internetState)
        setMetricValue("Current time", SimpleDateFormat("EEE, d MMM  ·  HH:mm:ss", Locale.getDefault()).format(Date()))
        if (isNetworkConnected() && System.currentTimeMillis() - lastProbeAt > 45_000L) probeInternet()
    }

    private fun setMetricValue(label: String, value: String) {
        findViewWithTag<TextView>("metric:$label")?.text = value
    }

    @Suppress("DEPRECATION")
    private fun activeNetwork(): NetworkInfo? {
        val manager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return manager.activeNetworkInfo
    }

    private fun isNetworkConnected(): Boolean = try {
        activeNetwork()?.isConnected == true
    } catch (_: Exception) {
        false
    }

    @Suppress("DEPRECATION")
    private fun wifiState(): String = try {
        val manager = applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
        if (!manager.isWifiEnabled) "OFF" else if ((manager.connectionInfo?.networkId ?: -1) >= 0) "CONNECTED" else "ON · NOT CONNECTED"
    } catch (_: Exception) {
        "UNAVAILABLE"
    }

    private fun probeInternet() {
        if (probeRunning || !isNetworkConnected()) {
            if (!isNetworkConnected()) internetState = "OFFLINE"
            return
        }
        probeRunning = true
        internetState = "CHECKING"
        probeExecutor.execute {
            var connected = false
            var connection: HttpURLConnection? = null
            try {
                connection = URL("https://connectivitycheck.gstatic.com/generate_204").openConnection() as HttpURLConnection
                connection.connectTimeout = 3500
                connection.readTimeout = 3500
                connection.instanceFollowRedirects = false
                connection.useCaches = false
                connection.requestMethod = "GET"
                connected = connection.responseCode == 204
            } catch (_: Exception) {
                connected = false
            } finally {
                connection?.disconnect()
            }
            handler.post {
                probeRunning = false
                lastProbeAt = System.currentTimeMillis()
                internetState = when {
                    connected -> "ONLINE"
                    isNetworkConnected() -> "NO INTERNET"
                    else -> "OFFLINE"
                }
                refreshStatus()
                if (activePage == PAGE_HOME) showPage(PAGE_HOME)
                if (activePage == PAGE_TROLLEY) showPage(PAGE_TROLLEY)
            }
        }
    }

    private fun batteryText(): String = if (batteryPercent >= 0) "$batteryPercent% · ${chargingText()}" else "Unavailable"

    private fun chargingText(): String = when (batteryStatus) {
        BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
        BatteryManager.BATTERY_STATUS_FULL -> "Full"
        BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
        BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Not charging"
        else -> "Unknown"
    }

    private fun openUrl(address: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(address)))
        } catch (_: Exception) {
            showMessage("No compatible browser is installed")
        }
    }

    private fun hideKeyboard() {
        val manager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        manager.hideSoftInputFromWindow(window.decorView.windowToken, 0)
    }

    private fun showMessage(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()

    @Suppress("UNCHECKED_CAST")
    private inline fun <reified T : View> findViewWithTag(tag: String): T? = window.decorView.findViewWithTag(tag) as? T

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        saveMapViewport()
        outState.putString(KEY_PAGE, activePage)
        val values = Bundle()
        formValues.forEach { (key, value) -> values.putString(key, value) }
        outState.putBundle(KEY_FORMS, values)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        if (receiverRegistered) unregisterReceiver(batteryReceiver)
        cleanupNavigationMap()
        probeExecutor.shutdownNow()
        navigationExecutor.shutdownNow()
        super.onDestroy()
    }

    companion object {
        private const val PAGE_HOME = "home"
        private const val PAGE_NAV = "navigation"
        private const val PAGE_RAIL = "railway"
        private const val PAGE_AIRPORT = "airport"
        private const val PAGE_TROLLEY = "trolley"
        private const val PAGE_ADMIN = "admin"
        private const val KEY_PAGE = "page"
        private const val KEY_FORMS = "forms"
        private const val REQUEST_LOCATION = 402
        private const val DEFAULT_MAP_LATITUDE = 23.0225
        private const val DEFAULT_MAP_LONGITUDE = 72.5714
        private const val DEFAULT_MAP_ZOOM = 11.0
        private const val KEY_DESTINATION_QUERY = "destination"
        private const val KEY_DESTINATION_NAME = "nav_destination_name"
        private const val KEY_DESTINATION_LAT = "nav_destination_lat"
        private const val KEY_DESTINATION_LON = "nav_destination_lon"
        private const val KEY_ORIGIN_LAT = "nav_origin_lat"
        private const val KEY_ORIGIN_LON = "nav_origin_lon"
        private const val KEY_ORIGIN_ACCURACY = "nav_origin_accuracy"
        private const val KEY_MAP_LAT = "nav_map_lat"
        private const val KEY_MAP_LON = "nav_map_lon"
        private const val KEY_MAP_ZOOM = "nav_map_zoom"
        private const val KEY_ROUTE_SUMMARY = "nav_route_summary"
        private const val PREF_NAVIGATION = "navigation_places"
        private const val PREF_SAVED_PLACES = "saved_places"
        private const val PREF_RECENT_PLACES = "recent_places"
        private const val PREF_TRAVEL_API = "travel_api"
        private const val KEY_TRAVEL_API_BASE_URL = "base_url"
        private const val KEY_TRAVEL_API_TOKEN = "client_token"
        private const val MAX_SAVED_PLACES = 12
        private const val MAX_RECENT_PLACES = 8
    }
}
