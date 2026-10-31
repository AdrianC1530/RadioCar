# 02 · Hardware de la radio

## Esta radio (verificado con el Diagnóstico de RadioCar, octubre 2026)

| Campo | Valor |
|---|---|
| Marca en pantalla | HC-Racing |
| Placa | Allwinner QUAD-CORE **T3**, modelo `k2001-nwd` (en Ajustes dice "TOPWAY T3L", modelo A7) |
| Firmware | **NWD** (no Topway) |
| Android | Dice "10", pero es **Android 7.1 (API 25)** |
| RAM / almacenamiento | 1 GB / 16 GB |
| Pantalla | 1024×600, **160 dpi** (RadioCar usa 240 dpi para que todo se vea grande) |
| Bluetooth del teléfono | Módulo propio **"BC8-Android"**, PIN 0000 (servicio `com.bt.bc03`), no la pila de Android |
| Vehículo | Ford F-150 2013 |

### Apps de fábrica

| Función | Paquete / actividad |
|---|---|
| Launcher de fábrica | `com.android.launcher/com.android.launcher2.Launcher` |
| Teléfono + Música BT (misma app, pestañas) | `com.nwd.android.phone/.home_horizontalActivity` |
| Radio FM | `com.nwd.radio/.home_horizontalActivity` (servicio `com.nwd.radio.service`) |
| Reproductor de video | `com.nwd.android.video.ui/.home_horizontalActivity` |
| Música local | `com.nwd.android.music.ui/.home_horizontalActivity` |
| Gestor de archivos | `com.nwd.filemanager/.FileManager` |
| Servicio BT | `com.bt.bc03` («BTService») — **no tocar** |

### Lo que significa para RadioCar

- **El launcher de fábrica se vuelve a imponer**: aunque elijas RadioCar como app de inicio, el
  sistema vuelve a poner `com.android.launcher` (el Diagnóstico lo mostraba como inicio por defecto).
  Solución sin cables: la **protección de inicio** de RadioCar (servicio de accesibilidad que trae
  RadioCar al frente cuando aparece el launcher de fábrica). Ver [03 · Instalación](03-instalacion.md).
- **Bluetooth**: Android no ve el teléfono conectado a "BC8-Android". Los botones Música BT y
  Teléfono abren `com.nwd.android.phone`. Nunca se abre el Bluetooth de Android (es otro distinto y
  confundía al iPhone).
- **No hay protocolo TXZNet** (eso es de Topway): el Diagnóstico no recibió avisos.

## Pendiente

| Dato | Estado |
|---|---|
| ¿El reproductor NWD abre videos enviados desde RadioCar? | _pendiente_ |
| Pantalla interna para abrir directo la pestaña de Música BT | _pendiente_ (ver «PANTALLAS DE APPS DE FÁBRICA» en el Diagnóstico) |
| ¿La app BT publica la canción (MediaSession)? | _pendiente_ |

---

## Referencia: otra T3L con firmware Topway

Del proyecto [t3l-android-headunit-revival](https://github.com/nurfaizfoat/t3l-android-headunit-revival)
(Android 8.1, 160 dpi). RadioCar también reconoce estas apps:

| Función | Paquete / actividad |
|---|---|
| Launcher de fábrica ("Theme") | `com.dofun.variety/com.dofun.overseasvariety.Launcher` |
| Bluetooth | `com.tw.bt/.ATBluetoothActivity` |
| Radio FM | `com.tw.radio/.RadioActivity` (protocolo TXZNet) |
| Video / música / archivos | `com.tw.video`, `com.tw.music`, `com.tw.twfileexplore` |
