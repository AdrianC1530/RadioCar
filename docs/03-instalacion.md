# 03 · Instalación

> Haz todo esto **con el carro estacionado**.

## 1 · Instalar el APK

### Opción A · Pendrive (la más fácil)

1. Descarga el APK desde [Releases](https://github.com/AdrianC1530/RadioCar/releases/latest)
   y cópialo a una memoria USB.
2. En la radio, abre el administrador de archivos (en la T3L: **TWFileExplore / Archivos**) y toca el APK.
   Si lo pide, permite instalar desde "orígenes desconocidos".
3. Abre **RadioCar** desde el menú de apps y pruébalo como app normal.

### Opción B · ADB

Requiere *Opciones de desarrollador → Depuración USB* en la radio
(en algunas Topway piden una clave de fábrica: pídela al vendedor, no pruebes claves al azar).

```powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb connect <IP-DE-LA-RADIO>:5555        # solo si es por Wi-Fi
& $adb install -r dist\RadioCar-0.4.0.apk
& $adb shell pm grant com.radiocar.launcher android.permission.ACCESS_FINE_LOCATION
& $adb shell pm grant com.radiocar.launcher android.permission.READ_EXTERNAL_STORAGE
& $adb shell cmd notification allow_listener com.radiocar.launcher/com.radiocar.launcher.MediaListenerService
```

### Permisos

| Permiso | Para qué | Cómo |
|---|---|---|
| Ubicación | Velocímetro GPS | Lo pide al abrir (o toca el recuadro de km/h) |
| Archivos / videos | Leer los videos de la memoria USB | Lo pide al abrir (o toca *Permitir acceso a la USB*) |
| Acceso a notificaciones | Ver y controlar lo que suena | Toca la barra "Reproduciendo ahora" → activa *RadioCar · control de música* |

## 2 · Dejarlo como pantalla de inicio

En la Topway T3L, **elegir RadioCar como "app de inicio" no basta**: el launcher de fábrica
**"Theme"** (`com.dofun.variety`) tiene un servicio propio y el botón Inicio de la radio lo abre
directamente. Hay que **desactivar "Theme"** (no se borra; se puede reactivar cuando quieras):

1. En RadioCar toca la insignia **F-150** (arriba a la derecha) → **Launcher de fábrica (Theme)**.
2. Toca **Abrir ajustes** → se abre la ficha de la app "Theme".
3. Toca **Desactivar** → **Aceptar**.
4. Pulsa **Inicio**: ahora sale RadioCar. Si pregunta, elige **RadioCar → Siempre**.

> Antes de desactivar Theme, comprueba que RadioCar abre bien como app normal.

### Si el botón "Desactivar" está gris

Con ADB:

```powershell
& $adb shell pm disable-user --user 0 com.dofun.variety
& $adb shell cmd package set-home-activity --user 0 com.radiocar.launcher/.MainActivity
```

## Volver al launcher de fábrica

- RadioCar → insignia **F-150** → **Launcher de fábrica** → **Abrir ajustes** → **Activar**.
  Luego pulsa Inicio y elige el original.
- O desde *Ajustes → Apps → Theme → Activar* (el engrane de la barra lateral abre Ajustes).
- Por ADB:

```powershell
& $adb shell pm enable --user 0 com.dofun.variety
& $adb shell cmd package set-home-activity --user 0 com.dofun.variety/com.dofun.overseasvariety.Launcher
```

## Actualizar

Instala el APK nuevo encima (pendrive o `adb install -r`). Se conservan el fondo, el tamaño y las apps elegidas.

## Si algo no funciona

RadioCar → insignia **F-150** → **Diagnóstico**. Muestra la versión de Android, la pantalla,
qué app abre cada botón y las apps de fábrica instaladas. **Sácale una foto** y compártela.
