# 02 · Hardware: Topway T3L

Datos tomados de *Ajustes → Estado de la Radio* (octubre 2026):

| Campo | Valor |
|---|---|
| Modelo | A7 |
| Procesador | TOPWAY T3L (Allwinner T3, 4× Cortex-A7 ~1.2 GHz) |
| RAM | 1 GB |
| Almacenamiento | 16 GB |
| Android (según la radio) | 10 — probablemente 8.1 (API 27) real |
| Pantalla | 1024×600, 160 dpi (por eso RadioCar agranda su interfaz) |
| Nombre Bluetooth | **BC8-Android** (módulo BT propio, no la pila de Android) |
| Vehículo | Ford F-150 2013 |

## Apps de fábrica (verificadas en otra T3L)

Fuente: inventario de paquetes del proyecto
[t3l-android-headunit-revival](https://github.com/nurfaizfoat/t3l-android-headunit-revival)
(misma placa, Android 8.1, 160 dpi).

| Función | Paquete / actividad |
|---|---|
| Launcher de fábrica ("Theme") | `com.dofun.variety/com.dofun.overseasvariety.Launcher` |
| Bluetooth (teléfono y música BT) | `com.tw.bt/.ATBluetoothActivity` |
| Radio FM | `com.tw.radio/.RadioActivity` |
| Reproductor de video | `com.tw.video/.VideoActivity` |
| Música local | `com.tw.music/.MusicActivity` |
| Explorador de archivos | `com.tw.twfileexplore/.ui.view.activity.ExploreActivity` |
| Ecualizador | `com.tw.eq/.EQActivity` |
| Volante | `com.tw.keypad/.SteeringWheelActivity` |
| Núcleo / servicios | `com.tw.core`, `com.tw.service` — **no tocar** |

## Detalles importantes

- **"Theme" se impone**: tiene un servicio en primer plano y el botón Inicio lo abre. Para usar
  otro launcher hay que desactivarlo (ver [03 · Instalación](03-instalacion.md#2--dejarlo-como-pantalla-de-inicio)).
- **El Bluetooth del teléfono no pasa por Android**: lo maneja el módulo BC8 (proceso nativo
  `gocsdk` en `/dev/ttyS3`) a través de `com.tw.bt`. Por eso Android no "ve" el teléfono conectado.
- Las apps de fábrica hablan con el launcher por broadcasts **TXZNet**
  (`com.txznet.launcher.<radio|bt|btmusic|call>.action.send/recv`). RadioCar solo **escucha**
  los `recv` (para el diagnóstico y, si viene, el título de la canción BT); nunca envía comandos.

## Pendiente de confirmar en ESTA radio

Usa RadioCar → F-150 → **Diagnóstico** y saca una foto.

| Dato | Valor |
|---|---|
| API real | _pendiente_ |
| ¿Mismos paquetes `com.tw.*` que la otra T3L? | _pendiente_ |
| ¿El reproductor de fábrica abre videos desde RadioCar? | _pendiente_ |
| ¿La app BT anuncia la canción por TXZNet? | _pendiente_ |
