# 03 · Instalación

> Haz todo esto **con el carro estacionado**.

## Opción A · Pendrive (la más fácil)

1. Copia `dist/RadioCar-<versión>.apk` a una memoria USB.
2. En la radio, abre el administrador de archivos y toca el APK.
   Si lo pide, permite instalar desde "orígenes desconocidos".
3. Abre **RadioCar** desde el menú de apps y pruébalo como app normal.
4. Si te gusta: pulsa **Inicio** → elige **RadioCar** → **Siempre**.

### Permisos

| Permiso | Para qué | Cómo |
|---|---|---|
| Ubicación | Velocímetro GPS | Lo pide al abrir (o toca el recuadro de km/h) |
| Archivos / videos | Leer los videos de la memoria USB | Lo pide al abrir (o toca *Permitir acceso a la USB*) |
| Acceso a notificaciones | Ver y controlar lo que suena (video, música BT…) | Toca la barra "Reproduciendo ahora" → activa *RadioCar · control de música* |

Si la radio esconde el ajuste de notificaciones, usa ADB (abajo).

## Opción B · ADB

Requiere activar *Opciones de desarrollador → Depuración USB* en la radio
(en algunas Topway piden una clave de fábrica: pídela al vendedor, no pruebes claves al azar).

```powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb connect <IP-DE-LA-RADIO>:5555        # solo si es por Wi-Fi
& $adb install -r dist\RadioCar-0.3.0.apk
& $adb shell pm grant com.radiocar.launcher android.permission.ACCESS_FINE_LOCATION
& $adb shell pm grant com.radiocar.launcher android.permission.READ_EXTERNAL_STORAGE
& $adb shell cmd notification allow_listener com.radiocar.launcher/com.radiocar.launcher.MediaListenerService
& $adb shell am start -n com.radiocar.launcher/.MainActivity
```

Al terminar, si usaste ADB por Wi-Fi, desactívalo (no dejes el puerto 5555 abierto).

## Actualizar

Instala el APK nuevo encima (pendrive o `adb install -r`). Se conservan los atajos y las apps elegidas.

## Volver al launcher de fábrica

- *Ajustes → Apps → Apps predeterminadas → App de inicio* → elige el original, **o**
- desinstala RadioCar: `adb shell pm uninstall com.radiocar.launcher`.
