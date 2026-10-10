package com.dashline.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
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
import java.net.HttpURLConnection
import java.net.URL
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
        handler.removeCallbacks(clockTicker)
        handler.post(clockTicker)
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
        addText(body, "Where would you like to go?", white, 24f, true, dp(6))
        addText(body, "Enter a place or address. Navigation opens in an installed map app or browser.", muted, 14f, false, dp(14))
        val form = card(body)
        beginForm(form, true)
        val destination = input(form, "destination", "Destination", "Station, airport or address")
        val open = textButton("OPEN DIRECTIONS", accent, ink, 15f).apply {
            setOnClickListener {
                val query = destination.text.toString().trim()
                if (query.isEmpty()) {
                    destination.error = "Enter a destination"
                } else {
                    formValues["destination_selected"] = query
                    openDirections(query)
                }
            }
        }
        form.addView(open, LinearLayout.LayoutParams(-1, dp(56)))
        addResult(body, "SELECTED DESTINATION", formValues["destination_selected"] ?: "No destination selected")
        addNote(body, "Indoor station maps and indoor positioning are not configured. This screen uses external map directions and does not claim indoor location support.")
    }

    private fun showRail(body: LinearLayout) {
        addText(body, "INDIAN RAILWAYS", accent, 12f, true, dp(6))
        addText(body, "Railway Assistant", white, 24f, true, dp(4))
        addText(body, "Enter details manually, or check current information on the official NTES service.", muted, 14f, false, dp(12))
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
            setOnClickListener {
                saveManualResult("rail", "SIMULATION · NOT LIVE", listOf(
                    "Train" to "DEMO 12345", "Station" to "Example Central", "Arrival" to "09:15",
                    "Departure" to "09:20", "Platform" to "Example only", "Train status" to "Simulated demonstration"
                ))
                showPage(PAGE_RAIL)
            }
        }, LinearLayout.LayoutParams(0, dp(54), 1f))
        addSavedResult(body, "rail")
        addExternalButton(body, "OPEN OFFICIAL NTES", "https://enquiry.indianrail.gov.in/ntes/")
        addJourneyNavigationButton(body, "NAVIGATE TO STATION", "rail_station")
        addNote(body, "No live railway feed is connected. Results entered here are manual; the demo is simulated. NTES is an external official information service and may require a compatible browser and internet access.")
    }

    private fun showAirport(body: LinearLayout) {
        addText(body, "FLIGHT DETAILS", accent, 12f, true, dp(6))
        addText(body, "Airport Assistant", white, 24f, true, dp(4))
        addText(body, "Record flight information or open an external flight-information service.", muted, 14f, false, dp(12))
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
        addSavedResult(body, "air")
        addFlightLookupButton(body)
        addJourneyNavigationButton(body, "NAVIGATE TO AIRPORT", "air_airport")
        addNote(body, "No live flight feed is connected. Gate, terminal, time and status are never inferred. FlightAware AeroAPI is a potential authorized provider requiring an account, API key and applicable commercial terms; no credential is embedded in this app.")
        addNote(body, "Airport terminal maps and indoor positioning are not configured.")
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
                    formValues["destination_selected"] = destination
                    openDirections(destination)
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

    private fun openDirections(destination: String) {
        val encoded = Uri.encode(destination)
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$encoded")))
        } catch (_: Exception) {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$encoded")))
            } catch (_: Exception) {
                showMessage("No compatible map app or browser is installed")
            }
        }
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
        outState.putString(KEY_PAGE, activePage)
        val values = Bundle()
        formValues.forEach { (key, value) -> values.putString(key, value) }
        outState.putBundle(KEY_FORMS, values)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        if (receiverRegistered) unregisterReceiver(batteryReceiver)
        probeExecutor.shutdownNow()
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
    }
}
