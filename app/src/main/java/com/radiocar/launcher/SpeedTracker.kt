package com.radiocar.launcher

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import kotlin.math.roundToInt

/** Velocímetro por GPS. Solo escucha mientras la pantalla de inicio está visible. */
class SpeedTracker(
    private val context: Context,
    private val onReading: (Reading) -> Unit,
) : LocationListener {

    sealed interface Reading {
        data object NoPermission : Reading
        data object Searching : Reading
        data class Kmh(val value: Int) : Reading
    }

    private val manager = context.getSystemService(LocationManager::class.java)
    private var running = false

    fun hasPermission() =
        context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun start() {
        if (running) return
        if (!hasPermission()) {
            onReading(Reading.NoPermission)
            return
        }
        onReading(Reading.Searching)
        try {
            manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, this, Looper.getMainLooper())
            running = true
        } catch (e: Exception) {
            // Radio sin antena GPS o proveedor deshabilitado: se queda en "Buscando".
        }
    }

    fun stop() {
        if (!running) return
        manager.removeUpdates(this)
        running = false
    }

    override fun onLocationChanged(location: Location) {
        val kmh = if (location.hasSpeed()) (location.speed * 3.6f).roundToInt() else 0
        // El GPS "tiembla" estando detenido; por debajo de 3 km/h mostramos 0.
        onReading(Reading.Kmh(if (kmh < 3) 0 else kmh))
    }

    // Android 8.1 declara estos métodos como abstractos: hay que implementarlos o la app se cierra.
    @Deprecated("Requerido en API < 29")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) = onReading(Reading.Searching)
}
