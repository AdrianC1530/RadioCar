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
import android.os.StrictMode
import android.provider.Settings
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
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
    private lateinit var videos: UsbVideos
    private lateinit var backgrounds: Backgrounds
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var root: View
    private lateinit var greeting: TextView
    private lateinit var speedValue: TextView
    private lateinit var speedUnit: TextView
    private lateinit var videoStatus: TextView
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

    private var mediaState: MediaHub.State? = null
    private var btState: BtStatus.State = BtStatus.State.Unknown
    private var glowColor = 0
    private var greetingRes = 0
    private var ticks = 0
    @Volatile private var scanning = false

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

    /** USB conectada / retirada / terminó de indexarse. */
    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // Al montar, el índice tarda unos segundos: refrescamos ahora y otra vez luego.
            refreshVideos()
            handler.postDelayed({ refreshVideos() }, 4000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT < 29) {
            // Permite pasar rutas file:// al reproductor de fábrica (es lo que mejor entienden).
            StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder().build())
        }
        setContentView(R.layout.activity_main)

        apps = AppRepository(this)
        media = MediaHub(this, ::renderMedia)
        speed = SpeedTracker(this, ::renderSpeed)
        bt = BtStatus(this)
        videos = UsbVideos(this)
        backgrounds = Backgrounds(this)

        bindViews()
        applyBackground()
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
        registerSystemReceiver(usbReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_MEDIA_MOUNTED)
            addAction(Intent.ACTION_MEDIA_UNMOUNTED)
            addAction(Intent.ACTION_MEDIA_REMOVED)
            addAction(Intent.ACTION_MEDIA_EJECT)
            addAction(Intent.ACTION_MEDIA_SCANNER_FINISHED)
            addDataScheme("file")
        })

        requestMissingPermissions()
    }

    override fun onResume() {
        super.onResume()
        media.start()
        speed.start()
        refreshVideos()
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
        unregisterReceiver(usbReceiver)
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
        if (requestCode == REQ_PERMISSIONS) {
            speed.start()
            refreshVideos()
        }
    }

    @Deprecated("Sin AndroidX usamos la API clásica")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data
        if (requestCode == REQ_PHOTO && resultCode == RESULT_OK && uri != null) {
            Thread {
                val ok = backgrounds.importPhoto(uri)
                handler.post {
                    if (ok) {
                        backgrounds.style = Backgrounds.Style.PHOTO
                        applyBackground()
                    } else {
                        Toast.makeText(this, R.string.bg_photo_failed, Toast.LENGTH_SHORT).show()
                    }
                }
            }.start()
        }
    }

    // ================================================================ vistas

    private fun bindViews() {
        root = findViewById(R.id.root)
        greeting = findViewById(R.id.greeting)
        speedValue = findViewById(R.id.speed_value)
        speedUnit = findViewById(R.id.speed_unit)
        videoStatus = findViewById(R.id.video_sub)
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
        root.setOnApplyWindowInsetsListener { v, insets ->
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
            if (!speed.hasPermission()) requestMissingPermissions()
        }
        // Personalizar el fondo: tocar la insignia F-150 o mantener pulsado el fondo.
        findViewById<View>(R.id.badge).setOnClickListener { showBackgroundPicker() }
        root.setOnLongClickListener { showBackgroundPicker(); true }

        // Videos (USB)
        setupActionButton(R.id.btn_video, Slot.VIDEO, R.drawable.ic_play, R.string.video_player, accent = true)
        setupActionButton(R.id.btn_files, Slot.FILES, R.drawable.ic_folder, R.string.video_files, accent = false)
        findViewById<View>(R.id.video_card).setOnClickListener { launchSlot(Slot.VIDEO) }
        videoStatus.setText(R.string.video_loading)

        // Bluetooth
        setupActionButton(R.id.btn_bt_music, Slot.BT_MUSIC, R.drawable.ic_music_note, R.string.bt_music, accent = true)
        setupActionButton(R.id.btn_bt_phone, Slot.PHONE, R.drawable.ic_phone, R.string.slot_phone, accent = false)
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

    /** Botón grande ícono + texto. Mantener pulsado = elegir otra app. */
    private fun setupActionButton(id: Int, slot: Slot, icon: Int, label: Int, accent: Boolean) {
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

    // ================================================================ fondo

    private fun applyBackground() {
        if (backgrounds.style == Backgrounds.Style.PHOTO) {
            Thread {
                val d = backgrounds.build()
                handler.post { root.background = d }
            }.start()
        } else {
            root.background = backgrounds.build()
        }
    }

    private fun showBackgroundPicker() {
        val styles = Backgrounds.Style.entries
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle(R.string.bg_title)
            .setSingleChoiceItems(
                styles.map { getString(it.title) }.toTypedArray(),
                styles.indexOf(backgrounds.style),
            ) { dialog, which ->
                dialog.dismiss()
                val chosen = styles[which]
                if (chosen == Backgrounds.Style.PHOTO) {
                    pickPhoto()
                } else {
                    backgrounds.style = chosen
                    applyBackground()
                }
            }
            .show()
    }

    private fun pickPhoto() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE)
        try {
            @Suppress("DEPRECATION")
            startActivityForResult(intent, REQ_PHOTO)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, R.string.bg_no_picker, Toast.LENGTH_LONG).show()
        }
    }

    // ================================================================ videos USB

    private fun refreshVideos() {
        if (scanning) return
        scanning = true
        Thread {
            val result = videos.scan()
            handler.post {
                scanning = false
                renderVideos(result)
            }
        }.apply { name = "scan-usb" }.start()
    }

    private fun renderVideos(scan: UsbVideos.Scan) {
        videoStatus.text = when {
            !scan.hasPermission -> getString(R.string.video_no_permission)
            scan.usbMounted && scan.total > 0 -> getString(R.string.video_usb, scan.usbLabel, scan.total)
            scan.usbMounted -> getString(R.string.video_usb_empty, scan.usbLabel)
            scan.total > 0 -> getString(R.string.video_internal, scan.total)
            else -> getString(R.string.video_no_usb)
        }

        videoChips.removeAllViews()
        if (!scan.hasPermission) {
            videoChips.addView(makeChip(getString(R.string.video_allow), R.drawable.ic_usb, highlight = true) {
                requestMissingPermissions()
            })
            return
        }
        for (video in scan.recent) {
            videoChips.addView(makeChip(video.title, R.drawable.ic_play, maxWidthDp = 220) { play(video) })
        }
    }

    private fun play(video: UsbVideos.Video) {
        val pkg = apps.resolve(Slot.VIDEO)?.packageName
        for (intent in videos.playIntents(video, pkg)) {
            try {
                startActivity(intent)
                return
            } catch (_: Exception) {
            }
        }
        Toast.makeText(this, R.string.video_play_failed, Toast.LENGTH_SHORT).show()
    }

    private fun makeChip(
        text: String,
        icon: Int?,
        highlight: Boolean = false,
        maxWidthDp: Int = 0,
        onClick: () -> Unit,
    ): TextView {
        val color = getColor(if (highlight) R.color.accent_1 else R.color.text_primary)
        return TextView(this).apply {
            this.text = text
            setTextColor(color)
            setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.chip_text))
            gravity = Gravity.CENTER_VERTICAL
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.END
            if (maxWidthDp > 0) maxWidth = dp(maxWidthDp)
            setBackgroundResource(R.drawable.bg_chip)
            setPadding(dp(12), 0, dp(16), 0)
            if (icon != null) {
                val d = getDrawable(icon)!!.mutate()
                d.setTint(if (highlight) color else getColor(R.color.accent_1))
                d.setBounds(0, 0, dp(18), dp(18))
                setCompoundDrawablesRelative(d, null, null, null)
                compoundDrawablePadding = dp(6)
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.MATCH_PARENT,
            ).apply { marginEnd = dp(8) }
            setOnClickListener { onClick() }
        }
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

    /** Pide de una vez los permisos que falten: ubicación (velocímetro) y lectura de la USB. */
    private fun requestMissingPermissions() {
        val missing = buildList {
            if (!speed.hasPermission()) {
                add(Manifest.permission.ACCESS_FINE_LOCATION)
                add(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
            if (!videos.hasPermission()) add(videos.permission)
        }
        if (missing.isNotEmpty()) requestPermissions(missing.toTypedArray(), REQ_PERMISSIONS)
    }

    // ================================================================ apps

    private fun reloadApps() {
        apps.load {
            gridAdapter.items = apps.apps
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
        const val REQ_PERMISSIONS = 1
        const val REQ_PHOTO = 2
    }
}
