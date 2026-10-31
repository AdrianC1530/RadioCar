package com.radiocar.launcher

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent

/**
 * "Protección de inicio".
 *
 * En algunas radios (p. ej. firmware NWD k2001) el sistema vuelve a poner el launcher de fábrica
 * como inicio, o el botón ⌂ físico lo abre directo, aunque elijas otra app de inicio.
 * Sin root ni ADB, la única forma fiable es esta: un servicio de accesibilidad que ve cuándo
 * aparece la pantalla del launcher de fábrica y trae RadioCar al frente.
 *
 * Solo escucha "cambio de ventana" (no lee contenido de pantalla) y solo actúa sobre la
 * actividad de inicio del launcher de fábrica.
 */
object HomeGuard {

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("factory", Context.MODE_PRIVATE)

    /** Launcher de fábrica recordado por [FactoryBridge.factoryHome]. */
    fun factoryHome(ctx: Context): ComponentName? =
        prefs(ctx).getString("home", null)?.let { ComponentName.unflattenFromString(it) }

    /** Permite abrir el launcher de fábrica a propósito (hasta volver a RadioCar). */
    fun setBypass(ctx: Context, on: Boolean) {
        prefs(ctx).edit().putBoolean("bypass", on).apply()
    }

    fun bypass(ctx: Context) = prefs(ctx).getBoolean("bypass", false)

    fun isEnabled(ctx: Context): Boolean {
        val enabled = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        val me = ComponentName(ctx, Service::class.java)
        return enabled.split(':').any {
            ComponentName.unflattenFromString(it)?.let { c -> c == me } == true ||
                it.equals(me.flattenToShortString(), ignoreCase = true)
        }
    }

    fun isDefaultHome(ctx: Context): Boolean =
        ctx.packageManager.resolveActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY,
        )?.activityInfo?.packageName == ctx.packageName

    class Service : AccessibilityService() {
        private var lastLaunch = 0L

        override fun onAccessibilityEvent(event: AccessibilityEvent) {
            if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
            val home = factoryHome(this) ?: return
            if (event.packageName?.toString() != home.packageName) return
            // Solo la pantalla de inicio, no otras pantallas de ese paquete (p. ej. elegir fondo).
            val cls = event.className?.toString() ?: return
            if (cls != home.className) return
            if (bypass(this)) return
            val now = System.currentTimeMillis()
            if (now - lastLaunch < 800) return
            lastLaunch = now
            try {
                startActivity(
                    Intent(this, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED),
                )
            } catch (_: Exception) {
            }
        }

        override fun onInterrupt() {}
    }
}
