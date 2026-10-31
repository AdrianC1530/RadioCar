# 02 · Hardware: Topway T3L

Datos tomados de *Ajustes → Estado de la Radio* (octubre 2026):

| Campo | Valor |
|---|---|
| Modelo | A7 |
| Procesador | TOPWAY T3L (Allwinner T3, 4× Cortex-A7 ~1.2 GHz) |
| RAM | 1 GB |
| Almacenamiento | 16 GB |
| Android (según la radio) | 10 |
| Vehículo | Ford F-150 2013 |

## Ojo con el "Android 10"

En XDA hay reportes de T3L idénticas (1 GB / 16 GB) que dicen "Android 10" en ajustes pero por
dentro son **Android 8.1 (API 27)**. El launcher está hecho para funcionar en ambos (minSdk 24).

## Pendiente de confirmar en la radio

Con ADB conectado:

```powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb shell getprop ro.build.version.sdk      # 27 = Android 8.1 real, 29 = Android 10
& $adb shell wm size                           # resolución (¿1024x600?)
& $adb shell wm density                        # densidad (¿160 o 240?)
& $adb shell "pm list packages | grep -i -E 'tw|radio|bt|music'"   # apps de fábrica
& $adb shell dumpsys meminfo com.radiocar.launcher | findstr TOTAL
```

| Dato | Valor |
|---|---|
| API real | _pendiente_ |
| Resolución / densidad | _pendiente_ |
| Paquete Radio FM | _pendiente_ (se prueba `com.tw.radio`) |
| Paquete Bluetooth / Música BT | _pendiente_ (se prueba `com.tw.bt`, `com.tw.btmusic`) |
| ¿El BT usa la pila de Android o un chip aparte? | _pendiente_ |
| ¿La app BT publica lo que suena (MediaSession)? | _pendiente_ |
| RAM usada por RadioCar | _pendiente_ |
