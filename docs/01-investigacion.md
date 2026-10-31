# 01 · Investigación

*Octubre 2026.*

## La idea

En la web se habla de "actualizar" las radios Android chinas para que tengan una interfaz más
amigable. En la práctica, lo que se está haciendo **no es una actualización de firmware**, sino
**reemplazar el launcher** (la pantalla de inicio) por uno moderno.

En Android, la pantalla de inicio es solo una app que responde a la acción `HOME`. Por eso se
puede cambiar **sin root y sin flashear nada**, y el launcher de fábrica sigue ahí como respaldo.

## Las radios chinas

Marcas/plataformas comunes: FYT (UIS7862), Topway (TS10/TS18/T3L), Joying, Teyes, Atoto…
Traen un Android AOSP genérico con un launcher hecho para vender, no para usar.

## Launchers que existen (2026)

| Proyecto | Notas |
|---|---|
| [Mini AA](https://zhuwubx-blip.github.io/mini-aa/) | Oscuro, multimedia, velocímetro; la versión gratis necesita platform keys de AOSP |
| [AB Launcher](https://xdaforums.com/t/ab-launcher-lightweight-split-screen-launcher-for-android-car-head-units-ai-boxes-beta-looking-for-testers.4792605/) | Pantalla dividida, ligero |
| [Car Launcher](https://carlauncher.app/) | Widgets, trip meter; avisa que en Android 13+ algunos fabricantes bloquean el cambio de launcher |
| AGAMA | El clásico, con temas y logos |
| [Dofun iLauncher](https://www.fixmydash.com/dofun-ilauncher-topway-android-head-unit/) | Comercial, solo placas Topway |
| [t3l-android-headunit-revival](https://github.com/nurfaizfoat/t3l-android-headunit-revival) | **Hecho para la T3L.** Launcher ligero: bajó la RAM del launcher de ~84 MB a ~7–10 MB (medición del autor) |

## Por qué uno propio

- La T3L tiene **1 GB de RAM** y un CPU Cortex-A7 lento: cada MB cuenta, sobre todo para ver videos.
- Los launchers genéricos no están pensados para el uso real del carro: **videos de música y
  música BT del teléfono**.
- Uno propio se adapta al gusto (tema verde camaleón de la F-150) y no trae anuncios ni rastreo.

## Riesgos que se evitan

- **No** se flashea firmware ni se "sube" la versión de Android: puede dejar la radio inservible
  y casi nunca hay firmware de recuperación.
- **No** se tocan los ajustes de vehículo / MCU / CAN.
- **No** root.

## Fuentes

- [How to update android on Chinese head unit (XDA)](https://xdaforums.com/t/how-to-update-android-on-chinese-head-unit.4776220/)
- [Allwinner T3L P1 latest Firmware (XDA)](https://xdaforums.com/t/allwinner-t3l-p1-latest-firmware.4347653/)
- [TopWay / Dofun UI Theme Launcher (XDA)](https://xdaforums.com/t/app-topway-dofun-ui-theme-launcher-apk.4467505/)
- [NavRadio+ para Topway T3/T8 (XDA)](https://xdaforums.com/t/dev-new-navradio-app-for-topway-based-units-t3-and-t8allwinner-ts9-intel-ts10-uis7862-and-ts18-uis8151.4024701/page-45)
- [The Android Headunit Launcher (android-headunits.com)](https://android-headunits.com/android-head-unit-launcher/)
