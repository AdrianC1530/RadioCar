package com.radiocar.launcher

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent

/**
 * Lee lo que está sonando en cualquier app (Spotify, YouTube Music, música BT de la radio…)
 * y la controla. Requiere que el usuario habilite el acceso a notificaciones de RadioCar.
 */
class MediaHub(
    private val context: Context,
    private val onChange: (State) -> Unit,
) {
    data class State(
        val hasAccess: Boolean,
        val title: String? = null,
        val artist: String? = null,
        val source: String? = null,
        val packageName: String? = null,
        val art: Bitmap? = null,
        val accent: Int? = null,
        val playing: Boolean = false,
        val duration: Long = 0L,
    )

    private val handler = Handler(Looper.getMainLooper())
    private val sessions = context.getSystemService(MediaSessionManager::class.java)
    private val audio = context.getSystemService(AudioManager::class.java)
    private val listenerComponent = ComponentName(context, MediaListenerService::class.java)
    private val labels = HashMap<String, String>()

    private var controller: MediaController? = null
    private var hasAccess = false
    private var artKey: String? = null
    private var art: Bitmap? = null
    private var accent: Int? = null

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list ->
        select(list.orEmpty())
    }

    private val callback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = publish()
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
        override fun onSessionDestroyed() = refresh()
    }

    fun start() {
        try {
            sessions.addOnActiveSessionsChangedListener(sessionsListener, listenerComponent, handler)
            hasAccess = true
            select(sessions.getActiveSessions(listenerComponent))
        } catch (e: SecurityException) {
            hasAccess = false
            attach(null)
            publish()
        }
    }

    fun stop() {
        try {
            sessions.removeOnActiveSessionsChangedListener(sessionsListener)
        } catch (_: Exception) {
        }
        attach(null)
    }

    val sessionActivity get() = controller?.sessionActivity
    val currentPackage get() = controller?.packageName

    fun playPause() {
        val c = controller
        if (c == null) {
            sendKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
        } else if (isPlaying(c)) {
            c.transportControls.pause()
        } else {
            c.transportControls.play()
        }
    }

    fun next() = controller?.transportControls?.skipToNext() ?: sendKey(KeyEvent.KEYCODE_MEDIA_NEXT)

    fun previous() = controller?.transportControls?.skipToPrevious() ?: sendKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)

    /** Posición estimada de la canción en ms (se calcula sin preguntarle a la app cada segundo). */
    fun position(): Long {
        val s = controller?.playbackState ?: return 0L
        var p = s.position
        if (s.state == PlaybackState.STATE_PLAYING && s.lastPositionUpdateTime > 0) {
            p += ((SystemClock.elapsedRealtime() - s.lastPositionUpdateTime) * s.playbackSpeed).toLong()
        }
        return p.coerceAtLeast(0L)
    }

    private fun refresh() {
        if (!hasAccess) return
        try {
            select(sessions.getActiveSessions(listenerComponent))
        } catch (e: SecurityException) {
            hasAccess = false
            attach(null)
            publish()
        }
    }

    private fun select(list: List<MediaController>) {
        val best = list.firstOrNull { isPlaying(it) } ?: list.firstOrNull()
        if (best?.sessionToken != controller?.sessionToken) attach(best)
        publish()
    }

    private fun attach(c: MediaController?) {
        controller?.unregisterCallback(callback)
        controller = c
        c?.registerCallback(callback, handler)
    }

    private fun publish() {
        if (!hasAccess) {
            onChange(State(hasAccess = false))
            return
        }
        val c = controller ?: run {
            onChange(State(hasAccess = true))
            return
        }
        val md = c.metadata
        val title = md?.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: md?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
        val artist = md?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: md?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: md?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
        updateArt(c.packageName, title, md)
        onChange(
            State(
                hasAccess = true,
                title = title,
                artist = artist,
                source = labelOf(c.packageName),
                packageName = c.packageName,
                art = art,
                accent = accent,
                playing = isPlaying(c),
                duration = md?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L,
            ),
        )
    }

    private fun updateArt(pkg: String, title: String?, md: MediaMetadata?) {
        val key = "$pkg|$title|${md?.getString(MediaMetadata.METADATA_KEY_ALBUM)}"
        val src = md?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: md?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: md?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        if (key == artKey && (art != null || src == null)) return
        artKey = key
        art = src?.let { shrink(it, 320) }
        accent = art?.let { dominantColor(it) }
    }

    /** Reduce la carátula: con 1 GB de RAM no conviene guardar imágenes de 1000 px. */
    private fun shrink(src: Bitmap, max: Int): Bitmap {
        val biggest = maxOf(src.width, src.height)
        if (biggest <= max) return src
        val scale = max.toFloat() / biggest
        return try {
            Bitmap.createScaledBitmap(src, (src.width * scale).toInt(), (src.height * scale).toInt(), true)
        } catch (e: Exception) {
            src
        }
    }

    /** Color promedio de la carátula, avivado, para teñir el resplandor de la tarjeta. */
    private fun dominantColor(bitmap: Bitmap): Int? = try {
        val px = Bitmap.createScaledBitmap(bitmap, 1, 1, true).getPixel(0, 0)
        val hsv = FloatArray(3)
        Color.colorToHSV(px, hsv)
        if (hsv[1] < 0.15f) {
            null // carátula casi gris: mejor el verde camaleón
        } else {
            hsv[1] = hsv[1].coerceAtLeast(0.55f)
            hsv[2] = hsv[2].coerceAtLeast(0.8f)
            Color.HSVToColor(hsv)
        }
    } catch (e: Exception) {
        null
    }

    private fun isPlaying(c: MediaController): Boolean {
        val s = c.playbackState?.state
        return s == PlaybackState.STATE_PLAYING || s == PlaybackState.STATE_BUFFERING
    }

    private fun sendKey(code: Int) {
        val t = SystemClock.uptimeMillis()
        audio.dispatchMediaKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0))
        audio.dispatchMediaKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_UP, code, 0))
    }

    private fun labelOf(pkg: String): String = labels.getOrPut(pkg) {
        try {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        } catch (e: Exception) {
            pkg
        }
    }
}
