package com.radiocar.launcher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

/** Texto de diagnóstico para sacarle foto y mandarlo (sin necesidad de ADB). */
object Diagnostics {

    private val INTERESTING = Regex(
        "com\\.tw\\.|dofun|txz|goc|syu|fyt|bt|bluetooth|radio|video|music|launcher|phone|dialer|file|explor",
        RegexOption.IGNORE_CASE,
    )

    fun build(context: Context, apps: AppRepository, bridge: FactoryBridge): String = buildString {
        val pm = context.packageManager
        val version = try {
            pm.getPackageInfo(context.packageName, 0).versionName
        } catch (e: Exception) {
            "?"
        }
        val sys = context.applicationContext.resources.displayMetrics
        appendLine("RadioCar $version")
        appendLine("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("Pantalla ${sys.widthPixels}x${sys.heightPixels} px · sistema ${sys.densityDpi} dpi · RadioCar ${UiScale.effectiveDpi(context)} dpi")

        val home = pm.resolveActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY,
        )?.activityInfo
        appendLine("Inicio por defecto: ${home?.packageName}/${home?.name}")
        val factory = bridge.factoryHome()
        appendLine(
            "Launcher de fábrica: ${factory?.flattenToShortString() ?: "no encontrado"}" +
                (factory?.let { if (bridge.isEnabled(it.packageName)) " (activo)" else " (DESACTIVADO)" } ?: ""),
        )
        appendLine(
            "RadioCar es inicio: ${if (HomeGuard.isDefaultHome(context)) "sí" else "no"} · " +
                "Protección: ${if (HomeGuard.isEnabled(context)) "activada" else "desactivada"} · " +
                "Tema: ${if (UiScale.isNightNow(context)) "oscuro" else "claro"}",
        )

        appendLine()
        appendLine("ACCESOS")
        for (slot in AppRepository.Slot.entries) {
            val e = apps.resolve(slot)
            appendLine("  ${context.getString(slot.title)} → ${e?.component?.flattenToShortString() ?: "(ninguna)"}")
        }

        appendLine()
        appendLine("APPS DE FÁBRICA / RELEVANTES")
        @Suppress("QueryPermissionsNeeded")
        pm.getInstalledApplications(0)
            .filter { INTERESTING.containsMatchIn(it.packageName) }
            .sortedBy { it.packageName }
            .take(45)
            .forEach { info ->
                val launch = pm.getLaunchIntentForPackage(info.packageName)?.component?.shortClassName ?: "-"
                val label = pm.getApplicationLabel(info).toString()
                appendLine("  ${info.packageName} «$label» $launch")
            }

        // Pantallas internas de las apps de fábrica: sirven para abrir, p. ej., la música BT directo.
        appendLine()
        appendLine("PANTALLAS DE APPS DE FÁBRICA")
        @Suppress("QueryPermissionsNeeded")
        pm.getInstalledApplications(0)
            .map { it.packageName }
            .filter { it.startsWith("com.nwd.") || it.startsWith("com.tw.") || it.startsWith("com.bt.") }
            .sorted()
            .forEach { pkg ->
                val acts = try {
                    pm.getPackageInfo(pkg, PackageManager.GET_ACTIVITIES).activities.orEmpty()
                        .filter { it.exported }
                        .map { it.name.removePrefix(pkg) }
                } catch (e: Exception) {
                    emptyList()
                }
                if (acts.isNotEmpty()) appendLine("  $pkg: ${acts.take(8).joinToString(" ")}")
            }

        appendLine()
        appendLine("AVISOS DE APPS DE FÁBRICA (TXZNet)")
        val events = bridge.recentEvents()
        if (events.isEmpty()) appendLine("  (ninguno todavía — abre Bluetooth/Radio y vuelve)")
        events.forEach { appendLine("  $it") }
    }
}
