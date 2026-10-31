package com.radiocar.launcher

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Puente con las apps de fábrica de Topway.
 *
 * - Detecta el launcher de fábrica ("Theme", com.dofun.variety en la T3L).
 * - Escucha (solo escucha, nunca envía comandos) los avisos que las apps de fábrica mandan al
 *   launcher por el protocolo TXZNet. La radio usa `com.txznet.launcher.radio.action.recv`
 *   (documentado en el proyecto t3l-android-headunit-revival); la app BT usa el mismo patrón.
 *   Los guardamos para el diagnóstico y, si traen el nombre de la canción BT, lo mostramos.
 */
class FactoryBridge(private val context: Context, private val onBtTitle: (String) -> Unit) {

    private val prefs = context.getSharedPreferences("factory", Context.MODE_PRIVATE)
    private val events = ArrayDeque<String>()
    private val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.US)

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            val action = intent.action?.removePrefix("com.txznet.launcher.") ?: return
            val extras = intent.extras
            val desc = extras?.keySet()?.joinToString(", ") { "$it=${extras.get(it)}" }.orEmpty()
            synchronized(events) {
                events.addFirst("${timeFmt.format(Date())} $action {$desc}")
                while (events.size > 15) events.removeLast()
            }
            if (action.startsWith("btmusic") || action.startsWith("bt.")) {
                extras?.keySet()
                    ?.firstOrNull { k -> TITLE_KEYS.any { k.contains(it, ignoreCase = true) } }
                    ?.let { k -> extras.get(k)?.toString()?.takeIf { it.isNotBlank() } }
                    ?.let(onBtTitle)
            }
        }
    }

    fun register() {
        val filter = IntentFilter().apply { RECV_ACTIONS.forEach { addAction(it) } }
        if (Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
    }

    fun unregister() = try {
        context.unregisterReceiver(receiver)
    } catch (_: Exception) {
    }

    fun recentEvents(): List<String> = synchronized(events) { events.toList() }

    /** Launcher de fábrica (aunque esté desactivado). Se recuerda la primera vez que se ve. */
    fun factoryHome(): ComponentName? {
        val pm = context.packageManager
        val homes = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)
            .map { ComponentName(it.activityInfo.packageName, it.activityInfo.name) }
            .filter { it.packageName != context.packageName && it.packageName != "com.android.settings" }
        homes.firstOrNull()?.let {
            prefs.edit().putString("home", it.flattenToString()).apply()
            return it
        }
        prefs.getString("home", null)?.let { return ComponentName.unflattenFromString(it) }
        return KNOWN_HOMES.firstOrNull { isInstalled(it.packageName) }
    }

    fun isEnabled(pkg: String): Boolean = try {
        context.packageManager.getApplicationInfo(pkg, 0).enabled
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    private fun isInstalled(pkg: String) = try {
        context.packageManager.getApplicationInfo(pkg, PackageManager.MATCH_DISABLED_COMPONENTS)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    private companion object {
        val RECV_ACTIONS = listOf(
            "com.txznet.launcher.radio.action.recv",
            "com.txznet.launcher.bt.action.recv",
            "com.txznet.launcher.btmusic.action.recv",
            "com.txznet.launcher.call.action.recv",
            "com.txznet.launcher.music.action.recv",
        )
        val TITLE_KEYS = listOf("title", "song", "music", "name")
        val KNOWN_HOMES = listOf(ComponentName("com.dofun.variety", "com.dofun.overseasvariety.Launcher"))
    }
}
