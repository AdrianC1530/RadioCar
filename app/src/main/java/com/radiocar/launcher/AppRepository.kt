package com.radiocar.launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper

data class AppEntry(
    val label: String,
    val packageName: String,
    val activityName: String,
    val icon: Drawable,
) {
    val component: ComponentName get() = ComponentName(packageName, activityName)
}

/** Lista de apps instaladas + qué app abre cada acceso rápido. */
class AppRepository(private val context: Context) {

    /**
     * Cada acceso rápido busca su app en este orden:
     * 1) la que elegiste manteniendo pulsado, 2) paquetes conocidos, 3) palabras en el nombre.
     * Los paquetes "com.tw.*" son los que suele usar el firmware de Topway.
     */
    enum class Slot(
        val key: String,
        val title: Int,
        val icon: Int,
        val packages: List<String>,
        val keywords: List<String>,
        /** Música BT y Teléfono suelen vivir en el MISMO paquete de Topway: ahí manda el nombre. */
        val preferKeywords: Boolean = false,
    ) {
        NAV(
            "nav", R.string.slot_nav, R.drawable.ic_navigation,
            listOf(
                "com.google.android.apps.maps", "com.waze", "com.sygic.aura",
                "com.here.app.maps", "net.osmand.plus", "net.osmand", "app.organicmaps",
            ),
            listOf("maps", "waze", "navega", "mapa", "gps"),
        ),
        RADIO(
            "radio", R.string.slot_radio, R.drawable.ic_radio,
            listOf("com.tw.radio", "com.topway.radio", "com.syu.radio", "com.android.fmradio"),
            listOf("radio", "fm"),
        ),
        VIDEO(
            "video", R.string.slot_video, R.drawable.ic_video,
            listOf(
                "com.tw.video", "com.tw.videoplayer", "com.tw.media.video", "com.syu.video",
                "org.videolan.vlc", "com.mxtech.videoplayer.ad", "com.mxtech.videoplayer.pro",
            ),
            listOf("video", "vídeo", "视频", "reproductor", "movie", "película"),
        ),
        FILES(
            "files", R.string.slot_files, R.drawable.ic_folder,
            listOf(
                "com.tw.filemanager", "com.tw.file", "com.android.documentsui",
                "com.google.android.documentsui", "com.mixplorer", "com.ghisler.android.TotalCommander",
            ),
            listOf("archivos", "explorador", "gestor", "file", "files"),
        ),
        BT_MUSIC(
            "bt_music", R.string.slot_bt_music, R.drawable.ic_bluetooth,
            listOf("com.tw.btmusic", "com.tw.bt", "com.syu.btmusic", "com.syu.bt"),
            listOf("bt music", "música bt", "musica bt", "bluetooth music", "a2dp", "btmusic"),
            preferKeywords = true,
        ),
        PHONE(
            "phone", R.string.slot_phone, R.drawable.ic_phone,
            listOf("com.tw.bt", "com.syu.bt", "com.android.dialer"),
            listOf("bluetooth", "teléfono", "telefono", "phone", "llamada"),
        ),
        SETTINGS(
            "settings", R.string.slot_settings, R.drawable.ic_settings,
            listOf("com.android.settings"),
            listOf("ajustes", "settings", "configuración"),
        ),
    }

    private val pm = context.packageManager
    private val prefs = context.getSharedPreferences("shortcuts", Context.MODE_PRIVATE)
    private val main = Handler(Looper.getMainLooper())

    var apps: List<AppEntry> = emptyList()
        private set

    /** Carga las apps en un hilo aparte para no trabar la pantalla (el CPU de la T3L es lento). */
    fun load(onLoaded: () -> Unit) {
        Thread {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val list = pm.queryIntentActivities(intent, 0)
                .filter { it.activityInfo.packageName != context.packageName }
                .map {
                    AppEntry(
                        label = it.loadLabel(pm).toString(),
                        packageName = it.activityInfo.packageName,
                        activityName = it.activityInfo.name,
                        icon = it.loadIcon(pm),
                    )
                }
                .sortedBy { it.label.lowercase() }
            main.post {
                apps = list
                onLoaded()
            }
        }.apply { name = "load-apps" }.start()
    }

    fun resolve(slot: Slot): AppEntry? {
        prefs.getString(slot.key, null)?.let { saved ->
            apps.firstOrNull { it.component.flattenToString() == saved }?.let { return it }
        }
        val byKeyword = {
            apps.firstOrNull { entry ->
                val label = entry.label.lowercase()
                slot.keywords.any { label.contains(it) }
            }
        }
        if (slot.preferKeywords) byKeyword()?.let { return it }
        for (pkg in slot.packages) {
            apps.firstOrNull { it.packageName == pkg }?.let { return it }
        }
        return byKeyword()
    }

    /** ¿Este paquete es la app de Bluetooth de la radio (o la pila BT de Android)? */
    fun isBluetoothPackage(pkg: String): Boolean =
        pkg.startsWith("com.android.bluetooth") ||
            pkg == resolve(Slot.BT_MUSIC)?.packageName ||
            (pkg == resolve(Slot.PHONE)?.packageName && pkg != "com.android.dialer") ||
            Slot.BT_MUSIC.packages.contains(pkg)

    fun assign(slot: Slot, entry: AppEntry) {
        prefs.edit().putString(slot.key, entry.component.flattenToString()).apply()
    }

    fun launch(entry: AppEntry): Boolean = try {
        context.startActivity(
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(entry.component)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED),
        )
        true
    } catch (e: Exception) {
        false
    }
}
