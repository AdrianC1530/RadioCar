# 05 · Arquitectura

## Decisiones

| Decisión | Por qué |
|---|---|
| Kotlin + vistas clásicas (no Jetpack Compose) | Compose consume más RAM y se siente lento en un Cortex-A7 |
| Sin AndroidX ni librerías | APK de ~60 KB, menos memoria; todo lo necesario está en el framework |
| Sin root, sin firmware, sin MCU/CAN | Cero riesgo de dañar la radio; se desinstala como cualquier app |
| Todo se detiene en `onPause` | Mientras ves un video, el launcher no gasta CPU (ni GPS, ni música, ni temporizadores) |
| Dos juegos de tamaños (`values/` y `values-h500dp/`) | La T3L puede venir con densidad 160 o 240 en la misma pantalla de 1024×600 |
| R8 + shrinkResources en release | Recorta código y recursos no usados |

## Archivos

```
app/src/main/java/com/radiocar/launcher/
├── MainActivity.kt          Pantalla de inicio: arma la UI y conecta todo
├── AppRepository.kt         Apps instaladas + qué app abre cada acceso (Slot)
├── MediaHub.kt              Lee/controla lo que suena (MediaSessionManager)
├── MediaListenerService.kt  Listener de notificaciones (Android lo exige para MediaHub)
├── BtStatus.kt              Teléfono conectado por Bluetooth (si Android lo ve)
├── UsbVideos.kt             Detecta la USB, lista sus videos y arma el intent para reproducirlos
├── Backgrounds.kt           Fondos (Horizonte camaleón dibujado por código, Aurora, Negro, foto)
├── SpeedTracker.kt          Velocímetro GPS
└── AppGridAdapter.kt        Cuadrícula del cajón de apps
app/src/main/res/
├── layout/activity_main.xml Barra lateral, barra superior, tarjetas, reproductor, cajón
├── layout/app_button.xml    Botón grande ícono + texto
├── values/ · values-h500dp/ Tamaños para pantalla compacta / normal
└── drawable/                Íconos vectoriales y fondos (degradado camaleón)
```

## Accesos (`Slot`)

Cada botón busca su app en este orden:

1. La que el usuario eligió (mantener pulsado) — guardada en `SharedPreferences`.
2. Paquetes conocidos (p. ej. `com.google.android.youtube`, `com.tw.bt`).
3. Palabras en el nombre de la app ("radio", "bt music"…).

`BT_MUSIC` invierte 2 y 3 (`preferKeywords`), porque en Topway la música BT y el teléfono
suelen ser el **mismo paquete** con dos actividades distintas.

| Slot | Paquetes probados |
|---|---|
| VIDEO | Reproductor de fábrica (`com.tw.video`…), VLC, MX Player |
| FILES | Explorador de fábrica (`com.tw.filemanager`…), DocumentsUI, MiXplorer |
| BT_MUSIC | `com.tw.btmusic`, `com.tw.bt`, `com.syu.*` |
| PHONE | `com.tw.bt`, `com.syu.bt`, marcador |
| RADIO | `com.tw.radio`, `com.topway.radio`… |
| NAV | Maps, Waze, Sygic, OsmAnd… |

## Música (`MediaHub`)

- Con el acceso a notificaciones habilitado, `MediaSessionManager.getActiveSessions()` da todas
  las sesiones activas; se elige la que está sonando.
- La carátula se reduce a 320 px y se calcula su color dominante para el brillo.
- La posición de la canción se estima localmente (sin preguntar a la app cada segundo).
- Si no hay sesión (p. ej. app BT de fábrica que no publica nada), los botones envían
  teclas multimedia con `AudioManager.dispatchMediaKeyEvent`.

## Bluetooth (`BtStatus`)

Usa `BluetoothAdapter.bondedDevices` + `BluetoothDevice.isConnected()` (oculto, por reflexión).
Muchas Topway usan un **chip BT aparte** controlado por su propia app: ahí Android no ve el
teléfono y la tarjeta muestra un texto genérico. Se detecta música BT también por el paquete
de la sesión (`com.android.bluetooth`, o el paquete BT de fábrica).

## Videos de la USB (`UsbVideos`)

1. `StorageManager.storageVolumes` → volúmenes **extraíbles y montados** (la USB/SD).
   La ruta sale de `getPath()` (oculto, por reflexión) en Android 8.1, o de `getDirectory()` en 11+.
2. Consulta el índice de medios (`MediaStore.Video`) filtrando por la ruta de la USB,
   ordenado por fecha. Si la USB aún no está indexada, recorre carpetas (4 niveles, máx. 2000 archivos).
3. Si no hay USB, muestra los videos de la memoria interna.
4. Para reproducir: `ACTION_VIEW` con `video/*` dirigido al reproductor elegido.
   En Android < 10 se envía la ruta `file://` (los reproductores de Topway la entienden mejor);
   para eso se relaja `StrictMode` (`FileUriExposedException`). En 10+ se usa `content://`.
5. Se refresca en `onResume` y con los broadcasts `MEDIA_MOUNTED/UNMOUNTED/EJECT/SCANNER_FINISHED`.
   Todo el escaneo corre en un hilo aparte.

## Fondos (`Backgrounds`)

- **Horizonte camaleón**: un `Drawable` propio que se dibuja según el tamaño real de la pantalla
  (sin imágenes): franja de color a lo ancho + máscara vertical que la apaga hacia arriba y abajo
  + línea de horizonte fina + halo central. Pensado para pantalla horizontal.
- **Foto propia**: `ACTION_GET_CONTENT` → se reduce (`inSampleSize`), se recorta al centro a
  1024×600 y se guarda como `files/fondo.jpg`. Al cargarla se le pinta encima un degradado oscuro
  para que el texto se lea. No depende de que la USB siga puesta.

## Probar sin la radio

```powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb shell wm size 1024x600
& $adb shell wm density 160    # y luego 240
& $adb shell settings put system accelerometer_rotation 0
& $adb shell settings put system user_rotation 0
# al terminar: wm size reset; wm density reset
```
