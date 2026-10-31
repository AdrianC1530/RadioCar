package com.radiocar.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

/** Fondos de pantalla. Todos pensados para pantalla HORIZONTAL (1024x600). */
class Backgrounds(private val context: Context) {

    enum class Style(val key: String, val title: Int) {
        HORIZON("horizon", R.string.bg_horizon),
        AURORA("aurora", R.string.bg_aurora),
        BLACK("black", R.string.bg_black),
        PHOTO("photo", R.string.bg_photo),
    }

    private val prefs = context.getSharedPreferences("background", Context.MODE_PRIVATE)
    private val photoFile get() = File(context.filesDir, "fondo.jpg")

    var style: Style
        get() = Style.entries.firstOrNull { it.key == prefs.getString("style", null) } ?: Style.HORIZON
        set(value) = prefs.edit().putString("style", value.key).apply()

    val hasPhoto get() = photoFile.exists()

    /** Crea el fondo. La foto se lee de disco: llamar fuera del hilo principal si es PHOTO. */
    fun build(style: Style = this.style): Drawable = when (style) {
        Style.HORIZON -> HorizonDrawable(context)
        Style.AURORA -> context.getDrawable(R.drawable.bg_aurora)!!
        Style.BLACK -> ColorDrawable(Color.BLACK)
        Style.PHOTO -> loadPhoto() ?: HorizonDrawable(context)
    }

    private fun loadPhoto(): Drawable? {
        val bmp = BitmapFactory.decodeFile(
            photoFile.path,
            BitmapFactory.Options().apply { inMutable = true },
        ) ?: return null
        // Oscurece la foto (pintado sobre la imagen) para que los textos se lean bien encima.
        val h = bmp.height.toFloat()
        Canvas(bmp).drawRect(0f, 0f, bmp.width.toFloat(), h, Paint(Paint.DITHER_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, 0f, h,
                intArrayOf(0xC7000000.toInt(), 0x99000000.toInt(), 0xD9000000.toInt()),
                null, Shader.TileMode.CLAMP,
            )
        })
        return BitmapDrawable(context.resources, bmp).apply { isFilterBitmap = true }
    }

    /**
     * Copia la foto elegida, recortada al tamaño horizontal de la pantalla, a la memoria de la app.
     * Así sigue funcionando aunque saques la USB, y ocupa poco (≈1024x600).
     */
    fun importPhoto(uri: Uri): Boolean = try {
        val dm = context.resources.displayMetrics
        val targetW = max(dm.widthPixels, dm.heightPixels)
        val targetH = minOf(dm.widthPixels, dm.heightPixels)

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= targetW && bounds.outHeight / (sample * 2) >= targetH) sample *= 2

        val src = context.contentResolver.openInputStream(uri)!!.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        }!!
        val cropped = centerCrop(src, targetW, targetH)
        FileOutputStream(photoFile).use { cropped.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        if (cropped != src) src.recycle()
        cropped.recycle()
        true
    } catch (e: Exception) {
        false
    }

    private fun centerCrop(src: Bitmap, w: Int, h: Int): Bitmap {
        val scale = max(w.toFloat() / src.width, h.toFloat() / src.height)
        val scaledW = (src.width * scale).toInt()
        val scaledH = (src.height * scale).toInt()
        val scaled = Bitmap.createScaledBitmap(src, scaledW, scaledH, true)
        val out = Bitmap.createBitmap(scaled, (scaledW - w) / 2, (scaledH - h) / 2, w, h)
        if (scaled != src && scaled != out) scaled.recycle()
        return out
    }
}

/**
 * "Horizonte camaleón": negro arriba y una franja de luz verde → turquesa → violeta
 * a lo ancho de la parte baja, como el reflejo de la pintura bajo el sol del atardecer.
 * Se dibuja según el tamaño real de la pantalla, sin imágenes.
 */
private class HorizonDrawable(context: Context) : Drawable() {
    private val base = context.getColor(R.color.bg)
    private val c1 = context.getColor(R.color.accent_1)
    private val c2 = context.getColor(R.color.accent_2)
    private val c3 = context.getColor(R.color.accent_3)

    private val bandPaint = Paint(Paint.DITHER_FLAG)
    private val fadePaint = Paint(Paint.DITHER_FLAG)
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.DITHER_FLAG)
    private var horizonY = 0f
    private var bandTop = 0f

    override fun onBoundsChange(b: Rect) {
        val w = b.width().toFloat()
        val h = b.height().toFloat()
        horizonY = b.top + h * 0.64f
        bandTop = horizonY - h * 0.34f

        // Color a lo ancho: verde a la izquierda, turquesa al centro, violeta a la derecha.
        bandPaint.shader = LinearGradient(
            b.left.toFloat(), 0f, b.right.toFloat(), 0f,
            intArrayOf(alpha(c1, 0x52), alpha(c2, 0x3D), alpha(c3, 0x4A)), null, Shader.TileMode.CLAMP,
        )
        // Máscara vertical con el color de fondo: oscuro arriba, casi transparente en el horizonte,
        // y se vuelve a oscurecer hacia abajo (reflejo). Sin bordes duros.
        val horizonPos = (horizonY - bandTop) / (b.bottom - bandTop)
        fadePaint.shader = LinearGradient(
            0f, bandTop, 0f, b.bottom.toFloat(),
            intArrayOf(base, alpha(base, 0xB3), alpha(base, 0x1A), alpha(base, 0x99), base),
            floatArrayOf(0f, horizonPos * 0.6f, horizonPos, horizonPos + (1 - horizonPos) * 0.45f, 1f),
            Shader.TileMode.CLAMP,
        )
        // Línea de horizonte fina, que se desvanece hacia los bordes.
        linePaint.shader = LinearGradient(
            b.left.toFloat(), 0f, b.right.toFloat(), 0f,
            intArrayOf(Color.TRANSPARENT, alpha(c1, 0x59), alpha(c2, 0x80), alpha(c3, 0x59), Color.TRANSPARENT),
            null, Shader.TileMode.CLAMP,
        )
        linePaint.strokeWidth = max(1f, h / 600f)
        // Halo amplio y tenue sobre el centro del horizonte.
        glowPaint.shader = RadialGradient(
            b.exactCenterX(), horizonY, w * 0.6f,
            intArrayOf(alpha(c2, 0x1F), Color.TRANSPARENT), null, Shader.TileMode.CLAMP,
        )
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        val l = b.left.toFloat()
        val r = b.right.toFloat()
        canvas.drawColor(base)
        canvas.drawRect(l, bandTop, r, b.bottom.toFloat(), bandPaint)
        canvas.drawRect(l, bandTop, r, b.bottom.toFloat(), fadePaint)
        canvas.drawRect(l, b.top.toFloat(), r, b.bottom.toFloat(), glowPaint)
        canvas.drawLine(l, horizonY, r, horizonY, linePaint)
    }

    private fun alpha(color: Int, a: Int) = Color.argb(a, Color.red(color), Color.green(color), Color.blue(color))

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    @Deprecated("Requerido por Drawable")
    override fun getOpacity() = PixelFormat.OPAQUE
}
