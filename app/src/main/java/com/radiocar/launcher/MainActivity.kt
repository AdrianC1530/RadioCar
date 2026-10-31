package com.radiocar.launcher

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextClock
import android.widget.TextView
import android.widget.Toast
import com.radiocar.launcher.AppRepository.Slot
import java.util.Calendar

class MainActivity : Activity() {

    private lateinit var apps: AppRepository
    private lateinit var media: MediaHub
    private lateinit var speed: SpeedTracker
    private lateinit var bt: BtStatus
    private lateinit var searches: VideoSearches
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var greeting: TextView
    private lateinit var speedValue: TextView
    private lateinit var speedUnit: TextView
    private lateinit var videoSub: TextView
    private lateinit var videoChips: LinearLayout
    private lateinit var btDot: View
    private lateinit var btStatusText: TextView
    private lateinit var mediaGlow: View
    private lateinit var mediaArt: ImageView
    private lateinit var mediaSource: TextView
    private lateinit var mediaTitle: TextView
    private lateinit var mediaArtist: TextView
    private lateinit var mediaProgress: ProgressBar
    private lateinit var btnPlay: ImageButton
    private lateinit var drawer: View
    private lateinit var gridAdapter: AppGridAdapter

    /** Botones que muestran el ícono real de la app asignada (YouTube, YT Music). */
    private val appButtons = LinkedHashMap<Slot, View>()

    private var mediaState: MediaHub.State? = null
    private var btState: BtStatus.State = BtStatus.State.Unknown
    private var glowColor = 0
    private var greetingRes = 0
    private var ticks = 0

    /** Un "tic" por segundo mientras la pantalla está visible. */
    private val ticker = object : Runnable {
        override fun run() {
            updateProgress()
            updateGreeting()
            if (ticks++ % 5 == 0) refreshBt()
            handler.postDelayed(this, 1000L)
        }
    }

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = reloadApps()
    }

    private val btReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = refreshBt()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        apps = AppRepository(this)
        media = MediaHub(this, ::renderMedia)
        speed = SpeedTracker(this, ::renderSpeed)
        bt = BtStatus(this)
        searches = VideoSearches(this)

        bindViews()
        reloadApps()

        registerSystemReceiver(packageReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        })
        registerSystemReceiver(btReceiver, IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        })

        if (!speed.hasPermission()) requestLocation()
    }

    override fun onResume() {
        super.onResume()
        media.start()
        speed.start()
        handler.post(ticker)
    }

    override fun onPause() {
        super.onPause()
        media.stop()
        speed.stop()
        handler.removeCallbacks(ticker)
    }

    override fun onDestroy() {
        unregisterReceiver(packageReceiver)
        unregisterReceiver(btReceiver)
        super.onDestroy()
    }

    /** Al tocar "Inicio" estando ya en el launcher: cerrar el cajón. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        closeDrawer()
    }

    @Deprecated("Launcher: atrás solo cierra el cajón")
    override fun onBackPressed() {
        closeDrawer()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_LOCATION) speed.start()
    }

    // ================================================================ vistas

    private fun bindViews() {
        greeting = findViewById(R.id.greeting)
        speedValue = findViewById(R.id.speed_value)
        speedUnit = findViewById(R.id.speed_unit)
        videoSub = findViewById(R.id.video_sub)
        videoChips = findViewById(R.id.video_chips)
        btDot = findViewById(R.id.bt_dot)
        btStatusText = findViewById(R.id.bt_status)
        mediaGlow = findViewById(R.id.media_glow)
        mediaArt = findViewById(R.id.media_art)
        mediaSource = findViewById(R.id.media_source)
        mediaTitle = findViewById(R.id.media_title)
        mediaArtist = findViewById(R.id.media_artist)
        mediaProgress = findViewById(R.id.media_progress)
        btnPlay = findViewById(R.id.btn_play)
        drawer = findViewById(R.id.drawer)

        // Respeta las barras del sistema (la radio puede tener barra de estado/navegación propia).
        findViewById<View>(R.id.root).setOnApplyWindowInsetsListener { v, insets ->
            @Suppress("DEPRECATION")
            v.setPadding(
                insets.systemWindowInsetLeft, insets.systemWindowInsetTop,
                insets.systemWindowInsetRight, insets.systemWindowInsetBottom,
            )
            insets
        }

        mediaArt.clipToOutline = true
        applyChameleonGradient(findViewById<TextClock>(R.id.clock))
        setGlow(getColor(R.color.accent_1))
        showArtPlaceholder()
        updateGreeting()

        findViewById<View>(R.id.speed_chip).setOnClickListener {
            if (!speed.hasPermission()) requestLocation()
        }

        // Videos
        setupAppButton(R.id.btn_video, Slot.VIDEO)
        setupAppButton(R.id.btn_video_music, Slot.MUSIC)
        renderChips()

        // Bluetooth
        setupFixedButton(R.id.btn_bt_music, Slot.BT_MUSIC, R.drawable.ic_music_note, R.string.bt_music, accent = true)
        setupFixedButton(R.id.btn_bt_phone, Slot.PHONE, R.drawable.ic_phone, R.string.slot_phone, accent = false)
        findViewById<View>(R.id.bt_card).setOnClickListener { launchSlot(Slot.BT_MUSIC) }

        // Reproduciendo ahora
        findViewById<View>(R.id.media_card).setOnClickListener { openMediaApp() }
        findViewById<View>(R.id.btn_prev).setOnClickListener { media.previous() }
        findViewById<View>(R.id.btn_next).setOnClickListener { media.next() }
        btnPlay.setOnClickListener { media.playPause() }

        // Barra lateral
        findViewById<View>(R.id.rail_home).setOnClickListener { closeDrawer() }
        findViewById<View>(R.id.rail_apps).setOnClickListener { openDrawer() }
        setupRail(R.id.rail_nav, Slot.NAV)
        setupRail(R.id.rail_radio, Slot.RADIO)
        setupRail(R.id.rail_phone, Slot.PHONE)
        setupRail(R.id.rail_settings, Slot.SETTINGS)

        // Cajón
        gridAdapter = AppGridAdapter(layoutInflater)
        findViewById<GridView>(R.id.app_grid).apply {
            adapter = gridAdapter
            setOnItemClickListener { _, _, position, _ ->
                launch(gridAdapter.getItem(position))
                closeDrawer()
            }
            setOnItemLongClickListener { _, _, position, _ ->
                openAppInfo(gridAdapter.getItem(position).packageName)
                true
            }
        }
        findViewById<View>(R.id.drawer_close).setOnClickListener { closeDrawer() }
    }

    private fun setupAppButton(id: Int, slot: Slot) {
        val button = findViewById<View>(id)
        button.findViewById<TextView>(R.id.btn_label).setText(slot.title)
        button.setOnClickListener { launchSlot(slot) }
        button.setOnLongClickListener { pickApp(slot); true }
        appButtons[slot] = button
    }

    private fun setupFixedButton(id: Int, slot: Slot, icon: Int, label: Int, accent: Boolean) {
        val button = findViewById<View>(id)
        val fg = getColor(if (accent) R.color.on_accent else R.color.text_primary)
        button.findViewById<ImageView>(R.id.btn_icon).apply {
            setImageResource(icon)
            imageTintList = ColorStateList.valueOf(fg)
            val pad = dp(3)
            setPadding(pad, pad, pad, pad)
        }
        button.findViewById<TextView>(R.id.btn_label).apply {
            setText(label)
            setTextColor(fg)
        }
        if (accent) button.setBackgroundResource(R.drawable.bg_button_accent)
        button.setOnClickListener { launchSlot(slot) }
        button.setOnLongClickListener { pickApp(slot); true }
    }

    private fun setupRail(id: Int, slot: Slot) {
        findViewById<View>(id).apply {
            setOnClickListener { launchSlot(slot) }
            setOnLongClickListener { pickApp(slot); true }
        }
    }

    /** Pone el ícono y nombre reales de la app asignada a cada botón. */
    private fun refreshAppButtons() {
        for ((slot, button) in appButtons) {
            val entry = apps.resolve(slot)
            val icon = button.findViewById<ImageView>(R.id.btn_icon)
            if (entry != null) {
                icon.imageTintList = null
                icon.setImageDrawable(entry.icon)
            } else {
                icon.setImageResource(slot.icon)
                icon.imageTintList = ColorStateList.valueOf(getColor(R.color.text_secondary))
            }
            button.findViewById<TextView>(R.id.btn_label).text = entry?.label ?: getString(slot.title)
        }
        val video = apps.resolve(Slot.VIDEO)
        videoSub.text = if (video != null) {
            getString(R.string.video_sub_app, video.label)
        } else {
            getString(R.string.video_sub_missing)
        }
    }

    /** Degradado verde → turquesa → violeta sobre el texto (el "camaleón"). */
    private fun applyChameleonGradient(text: TextView) {
        val colors = intArrayOf(getColor(R.color.accent_1), getColor(R.color.accent_2), getColor(R.color.accent_3))
        text.addOnLayoutChangeListener { v, left, _, right, _, _, _, _, _ ->
            val width = (right - left).toFloat()
            if (width > 0) {
                (v as TextView).paint.shader = LinearGradient(0f, 0f, width, 0f, colors, null, Shader.TileMode.CLAMP)
                v.invalidate()
            }
        }
    }

    private fun updateGreeting() {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val res = when {
            hour < 5 -> R.string.greeting_night
            hour < 12 -> R.string.greeting_morning
            hour < 19 -> R.string.greeting_afternoon
            else -> R.string.greeting_night
        }
        if (res != greetingRes) {
            greetingRes = res
            greeting.setText(res)
        }
    }

    // ================================================================ videos

    private fun renderChips() {
        videoChips.removeAllViews()
        videoChips.addView(makeChip(getString(R.string.chip_search), R.drawable.ic_search, highlight = true) {
            promptSearch()
        })
        for (query in searches.list()) {
            videoChips.addView(
                makeChip(query, null, onLongClick = { confirmRemoveSearch(query) }) { searchVideos(query) },
            )
        }
        videoChips.addView(makeChip(getString(R.string.chip_add), R.drawable.ic_add) { promptAddSearch() })
    }

    private fun makeChip(
        text: String,
        icon: Int?,
        highlight: Boolean = false,
        onLongClick: (() -> Unit)? = null,
        onClick: () -> Unit,
    ): TextView {
        val color = getColor(if (highlight) R.color.accent_1 else R.color.text_primary)
        return TextView(this).apply {
            this.text = text
            setTextColor(color)
            setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.chip_text))
            gravity = Gravity.CENTER_VERTICAL
            isSingleLine = true
            setBackgroundResource(R.drawable.bg_chip)
            setPadding(dp(14), 0, dp(16), 0)
            if (icon != null) {
                val d = getDrawable(icon)!!.mutate()
                d.setTint(color)
                d.setBounds(0, 0, dp(18), dp(18))
                setCompoundDrawablesRelative(d, null, null, null)
                compoundDrawablePadding = dp(6)
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.MATCH_PARENT,
            ).apply { marginEnd = dp(8) }
            setOnClickListener { onClick() }
            if (onLongClick != null) setOnLongClickListener { onLongClick(); true }
        }
    }

    private fun searchVideos(query: String) {
        val pkg = apps.resolve(Slot.VIDEO)?.packageName
        for (intent in VideoSearches.searchIntents(pkg, query)) {
            try {
                startActivity(intent)
                return
            } catch (_: Exception) {
            }
        }
        Toast.makeText(this, R.string.search_failed, Toast.LENGTH_SHORT).show()
    }

    /** Diálogo de búsqueda libre, con opción de guardarla como atajo. */
    private fun promptSearch() {
        val input = searchField()
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle(R.string.search_dialog_title)
            .setView(wrapField(input))
            .setPositiveButton(R.string.search_go) { _, _ ->
                input.text.toString().trim().takeIf { it.isNotEmpty() }?.let { searchVideos(it) }
            }
            .setNeutralButton(R.string.search_save) { _, _ ->
                input.text.toString().trim().takeIf { it.isNotEmpty() }?.let {
                    searches.add(it)
                    renderChips()
                    searchVideos(it)
                }
            }
            .setNegativeButton(R.string.search_cancel, null)
            .show()
        input.requestFocus()
    }

    private fun promptAddSearch() {
        val input = searchField()
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle(R.string.search_add_title)
            .setView(wrapField(input))
            .setPositiveButton(R.string.search_save) { _, _ ->
                searches.add(input.text.toString())
                renderChips()
            }
            .setNegativeButton(R.string.search_cancel, null)
            .show()
        input.requestFocus()
    }

    private fun confirmRemoveSearch(query: String) {
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle(getString(R.string.search_remove_title, query))
            .setPositiveButton(R.string.search_remove) { _, _ ->
                searches.remove(query)
                renderChips()
            }
            .setNegativeButton(R.string.search_cancel, null)
            .show()
    }

    private fun searchField() = EditText(this).apply {
        setHint(R.string.search_hint)
        isSingleLine = true
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        imeOptions = EditorInfo.IME_ACTION_DONE
    }

    private fun wrapField(field: EditText) = FrameLayout(this).apply {
        setPadding(dp(20), dp(8), dp(20), 0)
        addView(field)
    }

    // ================================================================ bluetooth

    private fun refreshBt() {
        btState = bt.read()
        renderBt()
    }

    private fun renderBt() {
        val s = mediaState
        val btPlaying = s?.title != null && s.packageName?.let { apps.isBluetoothPackage(it) } == true
        val state = btState
        val (color, text) = when {
            btPlaying -> R.color.accent_1 to getString(R.string.bt_playing, s!!.title)
            state is BtStatus.State.Connected -> R.color.accent_1 to getString(R.string.bt_connected, state.name)
            state == BtStatus.State.Off -> R.color.text_tertiary to getString(R.string.bt_off)
            state == BtStatus.State.Disconnected -> R.color.text_tertiary to getString(R.string.bt_disconnected)
            else -> R.color.accent_2 to getString(R.string.bt_hint)
        }
        btDot.backgroundTintList = ColorStateList.valueOf(getColor(color))
        btStatusText.text = text
    }

    // ================================================================ reproduciendo ahora

    private fun renderMedia(state: MediaHub.State) {
        mediaState = state
        when {
            !state.hasAccess -> {
                mediaSource.setText(R.string.media_label)
                mediaTitle.setText(R.string.media_no_access_title)
                mediaArtist.setText(R.string.media_no_access_sub)
            }
            state.title == null -> {
                mediaSource.text = state.source?.uppercase() ?: getString(R.string.media_label)
                mediaTitle.setText(R.string.media_idle_title)
                mediaArtist.setText(R.string.media_idle_sub)
            }
            else -> {
                mediaSource.text = state.source?.uppercase() ?: getString(R.string.media_label)
                mediaTitle.text = state.title
                mediaArtist.text = state.artist.orEmpty()
            }
        }

        if (state.art != null) {
            mediaArt.imageTintList = null
            mediaArt.setPadding(0, 0, 0, 0)
            mediaArt.scaleType = ImageView.ScaleType.CENTER_CROP
            mediaArt.setImageBitmap(state.art)
        } else {
            showArtPlaceholder()
        }

        btnPlay.setImageResource(if (state.playing) R.drawable.ic_pause else R.drawable.ic_play)
        setGlow(state.accent ?: getColor(R.color.accent_1))
        updateProgress()
        renderBt()
    }

    private fun showArtPlaceholder() {
        mediaArt.setImageResource(R.drawable.ic_music_note)
        mediaArt.scaleType = ImageView.ScaleType.FIT_CENTER
        val pad = (resources.getDimensionPixelSize(R.dimen.now_art) * 0.26f).toInt()
        mediaArt.setPadding(pad, pad, pad, pad)
        mediaArt.imageTintList = ColorStateList.valueOf(getColor(R.color.on_accent))
    }

    /** Resplandor de la barra de música, teñido con el color de la carátula. */
    private fun setGlow(color: Int) {
        if (color == glowColor) return
        glowColor = color
        val start = Color.argb(0x59, Color.red(color), Color.green(color), Color.blue(color))
        mediaGlow.background = GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(start, Color.TRANSPARENT, Color.TRANSPARENT),
        ).apply { cornerRadius = resources.getDimension(R.dimen.card_radius) }
    }

    private fun updateProgress() {
        val duration = mediaState?.duration ?: 0L
        if (duration <= 0L) {
            mediaProgress.visibility = View.INVISIBLE
            return
        }
        mediaProgress.visibility = View.VISIBLE
        mediaProgress.progress = ((media.position() * 1000L) / duration).toInt().coerceIn(0, 1000)
    }

    private fun openMediaApp() {
        val state = mediaState
        if (state == null || !state.hasAccess) {
            openListenerSettings()
            return
        }
        media.sessionActivity?.let {
            try {
                it.send()
                return
            } catch (_: Exception) {
            }
        }
        val intent = media.currentPackage?.let { packageManager.getLaunchIntentForPackage(it) }
        if (intent != null) startActivity(intent) else launchSlot(Slot.VIDEO)
    }

    private fun openListenerSettings() {
        try {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, R.string.media_listener_hidden, Toast.LENGTH_LONG).show()
        }
    }

    // ================================================================ velocidad

    private fun renderSpeed(reading: SpeedTracker.Reading) {
        when (reading) {
            SpeedTracker.Reading.NoPermission -> {
                speedValue.text = getString(R.string.gps_enable)
                speedUnit.visibility = View.GONE
            }
            SpeedTracker.Reading.Searching -> {
                speedValue.text = "--"
                speedUnit.visibility = View.VISIBLE
            }
            is SpeedTracker.Reading.Kmh -> {
                speedValue.text = reading.value.toString()
                speedUnit.visibility = View.VISIBLE
            }
        }
    }

    private fun requestLocation() {
        requestPermissions(
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
            REQ_LOCATION,
        )
    }

    // ================================================================ apps

    private fun reloadApps() {
        apps.load {
            gridAdapter.items = apps.apps
            refreshAppButtons()
            renderBt()
        }
    }

    private fun launch(entry: AppEntry) {
        if (!apps.launch(entry)) Toast.makeText(this, R.string.launch_failed, Toast.LENGTH_SHORT).show()
    }

    private fun launchSlot(slot: Slot) {
        val entry = apps.resolve(slot)
        when {
            entry != null -> launch(entry)
            slot == Slot.SETTINGS -> startActivity(Intent(Settings.ACTION_SETTINGS))
            slot == Slot.BT_MUSIC -> startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
            else -> pickApp(slot)
        }
    }

    private fun pickApp(slot: Slot) {
        val list = apps.apps
        if (list.isEmpty()) {
            Toast.makeText(this, R.string.apps_loading, Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle(getString(R.string.pick_app_title, getString(slot.title)))
            .setItems(list.map { it.label }.toTypedArray()) { _, which ->
                apps.assign(slot, list[which])
                refreshAppButtons()
                renderBt()
            }
            .show()
    }

    private fun openAppInfo(pkg: String) {
        try {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", pkg, null)))
        } catch (_: Exception) {
        }
    }

    private fun openDrawer() {
        if (drawer.visibility == View.VISIBLE) return
        drawer.alpha = 0f
        drawer.visibility = View.VISIBLE
        drawer.animate().alpha(1f).setDuration(160L).withEndAction(null).start()
    }

    private fun closeDrawer() {
        if (drawer.visibility != View.VISIBLE) return
        drawer.animate().alpha(0f).setDuration(140L).withEndAction { drawer.visibility = View.GONE }.start()
    }

    // ================================================================ util

    private fun registerSystemReceiver(receiver: BroadcastReceiver, filter: IntentFilter) {
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(receiver, filter)
        }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val REQ_LOCATION = 1
    }
}
