package com.radiocar.launcher

import android.content.Context
import android.content.res.Configuration
import java.util.Calendar
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Tamaño y tema (claro/oscuro) de la interfaz, aplicados SOLO a RadioCar.
 *
 * - Tamaño: la radio viene a 160 dpi (1024x600) y todo se ve diminuto; ajustamos la densidad para
 *   que la pantalla mida [Size.heightDp] de alto.
 * - Tema: de día, con el sol de frente, lo oscuro no se ve; el tema claro usa fondo claro y
 *   texto oscuro. En automático cambia solo según la hora.
 */
object UiScale {

    enum class Size(val key: String, val title: Int, val heightDp: Int) {
        LARGE("large", R.string.size_large, 400),
        MEDIUM("medium", R.string.size_medium, 470),
        SYSTEM("system", R.string.size_system, 0),
    }

    enum class Theme(val key: String, val title: Int) {
        AUTO("auto", R.string.theme_auto),
        LIGHT("light", R.string.theme_light),
        DARK("dark", R.string.theme_dark),
    }

    /** En automático: claro de 6:00 a 18:29, oscuro el resto. */
    private const val DAY_START_MIN = 6 * 60
    private const val DAY_END_MIN = 18 * 60 + 30

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("ui", Context.MODE_PRIVATE)

    fun current(ctx: Context): Size =
        Size.entries.firstOrNull { it.key == prefs(ctx).getString("size", null) } ?: Size.LARGE

    fun set(ctx: Context, size: Size) {
        prefs(ctx).edit().putString("size", size.key).commit()
    }

    fun theme(ctx: Context): Theme =
        Theme.entries.firstOrNull { it.key == prefs(ctx).getString("theme", null) } ?: Theme.AUTO

    fun setTheme(ctx: Context, theme: Theme) {
        prefs(ctx).edit().putString("theme", theme.key).commit()
    }

    fun isNightNow(ctx: Context): Boolean = when (theme(ctx)) {
        Theme.LIGHT -> false
        Theme.DARK -> true
        Theme.AUTO -> {
            val c = Calendar.getInstance()
            val m = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
            m < DAY_START_MIN || m >= DAY_END_MIN
        }
    }

    /** Densidad que usa RadioCar con el tamaño actual (para el diagnóstico). */
    fun effectiveDpi(ctx: Context): Int = ctx.resources.displayMetrics.densityDpi

    fun wrap(base: Context): Context {
        val cfg = Configuration(base.resources.configuration)

        val nightBits = if (isNightNow(base)) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        cfg.uiMode = (cfg.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or nightBits

        val size = current(base)
        if (size.heightDp > 0) {
            val dm = base.resources.displayMetrics
            val shortPx = min(dm.widthPixels, dm.heightPixels)
            val longPx = max(dm.widthPixels, dm.heightPixels)
            val dpi = (shortPx * 160f / size.heightDp).roundToInt()
            if (dpi > dm.densityDpi) { // nunca achicar respecto al sistema
                val landscape = dm.widthPixels >= dm.heightPixels
                cfg.densityDpi = dpi
                cfg.screenWidthDp = (if (landscape) longPx else shortPx) * 160 / dpi
                cfg.screenHeightDp = (if (landscape) shortPx else longPx) * 160 / dpi
                cfg.smallestScreenWidthDp = shortPx * 160 / dpi
            }
        }
        return base.createConfigurationContext(cfg)
    }
}
