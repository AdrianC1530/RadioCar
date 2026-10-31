package com.radiocar.launcher

import android.content.Context
import android.content.res.Configuration
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Tamaño de la interfaz. La T3L viene a 160 dpi (1024x600): todo se ve diminuto en el carro.
 * En vez de depender de la densidad del sistema, ajustamos la densidad SOLO de RadioCar para que
 * la pantalla mida [Size.heightDp] de alto: así todo crece parejo y se lee de un vistazo.
 */
object UiScale {

    enum class Size(val key: String, val title: Int, val heightDp: Int) {
        LARGE("large", R.string.size_large, 400),
        MEDIUM("medium", R.string.size_medium, 470),
        SYSTEM("system", R.string.size_system, 0),
    }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("ui", Context.MODE_PRIVATE)

    fun current(ctx: Context): Size =
        Size.entries.firstOrNull { it.key == prefs(ctx).getString("size", null) } ?: Size.LARGE

    fun set(ctx: Context, size: Size) {
        prefs(ctx).edit().putString("size", size.key).commit()
    }

    /** Densidad que usa RadioCar con el tamaño actual (para el diagnóstico). */
    fun effectiveDpi(ctx: Context): Int = ctx.resources.displayMetrics.densityDpi

    fun wrap(base: Context): Context {
        val size = current(base)
        if (size.heightDp == 0) return base
        val dm = base.resources.displayMetrics
        val shortPx = min(dm.widthPixels, dm.heightPixels)
        val longPx = max(dm.widthPixels, dm.heightPixels)
        val dpi = (shortPx * 160f / size.heightDp).roundToInt()
        if (dpi <= dm.densityDpi) return base // nunca achicar respecto al sistema

        val cfg = Configuration(base.resources.configuration)
        val landscape = dm.widthPixels >= dm.heightPixels
        cfg.densityDpi = dpi
        cfg.screenWidthDp = (if (landscape) longPx else shortPx) * 160 / dpi
        cfg.screenHeightDp = (if (landscape) shortPx else longPx) * 160 / dpi
        cfg.smallestScreenWidthDp = shortPx * 160 / dpi
        return base.createConfigurationContext(cfg)
    }
}
