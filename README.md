# RadioCar

Launcher (pantalla de inicio) moderno y liviano para la radio Android china
**Topway T3L** (pantalla horizontal 1024×600) de una **Ford F-150 2013**, pensado para lo que
más se usa en el carro: **videos desde la memoria USB** y **música del teléfono por Bluetooth**.

![Pantalla de inicio](docs/img/inicio-v0.3.png)

- **No toca firmware, ni root, ni el MCU/CAN.** Es una app normal; el launcher de fábrica
  sigue instalado y se puede volver a él en cualquier momento.
- **APK de ~75 KB**, sin librerías externas: la radio solo tiene 1 GB de RAM.
- Tema oscuro con acento **"verde camaleón"** (verde → turquesa → violeta), como la pintura de la troca.

## Qué hace

| Zona | Función |
|---|---|
| **Videos** | Detecta la memoria USB y cuántos videos tiene. Botones al **Reproductor** de fábrica y a **Archivos**, y accesos a los últimos videos: un toque y se reproducen |
| **Bluetooth** | Estado del teléfono / lo que suena por BT, y botones a **Música BT** y **Teléfono** de la radio |
| **Reproduciendo ahora** | Carátula, título, artista, progreso y controles de lo que esté sonando |
| **Barra superior** | Hora, fecha, saludo, velocímetro GPS e insignia F-150 (tócala para cambiar el fondo) |
| **Barra lateral** | Inicio, Navegación, Radio FM, Teléfono, todas las apps, Ajustes |
| **Fondo** | Horizonte camaleón (por defecto), Aurora, Negro o **una foto tuya** |

**Mantén pulsado** cualquier botón para elegir qué app abre. Detalles en [docs/04-uso.md](docs/04-uso.md).

## Instalar rápido

1. Copia [`dist/RadioCar-0.3.0.apk`](dist/) a un pendrive.
2. En la radio: administrador de archivos → toca el APK → instalar.
3. Pulsa **Inicio** → elige **RadioCar** → **Siempre**.
4. Acepta los permisos (ubicación y archivos).

Guía completa (permisos, ADB, cómo volver al de fábrica): [docs/03-instalacion.md](docs/03-instalacion.md).

## Documentación

| Documento | Contenido |
|---|---|
| [01 · Investigación](docs/01-investigacion.md) | Qué son estas radios, qué launchers existen y por qué hacer uno propio |
| [02 · Hardware T3L](docs/02-hardware-t3l.md) | Especificaciones de la radio y lo que falta confirmar |
| [03 · Instalación](docs/03-instalacion.md) | Pendrive, ADB, permisos y cómo volver atrás |
| [04 · Uso](docs/04-uso.md) | Videos USB, Bluetooth, fondo, consejos |
| [05 · Arquitectura](docs/05-arquitectura.md) | Cómo está hecho el código y por qué |
| [CHANGELOG](CHANGELOG.md) | Historial de versiones |

## Compilar

Requiere Android Studio (usa su JDK) y el SDK de Android 36.

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat assembleRelease
# → app\build\outputs\apk\release\app-release.apk
```

Kotlin + vistas clásicas de Android · AGP 9.0.1 · Gradle 9.1 · minSdk 24 · sin AndroidX.
