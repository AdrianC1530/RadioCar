package com.radiocar.launcher

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/**
 * Qué teléfono está conectado por Bluetooth.
 *
 * Ojo: muchas radios Topway manejan el Bluetooth con un chip aparte y su propia app,
 * no con la pila Bluetooth de Android. En ese caso Android no "ve" el teléfono y
 * devolvemos [State.Unknown]; la tarjeta muestra un texto genérico y los botones
 * abren la app BT de fábrica igual.
 */
class BtStatus(private val context: Context) {

    sealed interface State {
        data object Unknown : State
        data object Off : State
        data object Disconnected : State
        data class Connected(val name: String) : State
    }

    @SuppressLint("MissingPermission")
    fun read(): State {
        if (Build.VERSION.SDK_INT >= 31 &&
            context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        ) return State.Unknown

        val adapter = try {
            context.getSystemService(BluetoothManager::class.java)?.adapter
        } catch (e: Exception) {
            null
        } ?: return State.Unknown

        return try {
            if (!adapter.isEnabled) return State.Off
            val device = adapter.bondedDevices.orEmpty().firstOrNull { isConnected(it) }
                ?: return State.Disconnected
            State.Connected(device.name ?: device.address)
        } catch (e: SecurityException) {
            State.Unknown
        }
    }

    /** BluetoothDevice.isConnected() existe pero está oculto; en Android 8.1 se puede llamar por reflexión. */
    private fun isConnected(device: BluetoothDevice): Boolean = try {
        device.javaClass.getMethod("isConnected").invoke(device) as Boolean
    } catch (e: Exception) {
        false
    }
}
