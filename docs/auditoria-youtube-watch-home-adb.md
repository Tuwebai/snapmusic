# Auditoría ADB de YouTube — home, watch y fullscreen observable

## Alcance

Esta auditoría usa solo evidencia **observable por ADB** del paquete de YouTube instalado en el teléfono.  
No se intentó inferir lógica interna no visible.

## Dispositivo y paquete auditado

- Dispositivo: `23129RA5FL`
- Paquete principal observado: `com.google.android.youtube`
- `versionName` visible en `dumpsys`: `21.19.286`
- También aparece una variante/base anterior en dumps con `versionName`: `20.25.35`
- `targetSdk` visible: `36`

## Evidencia usada

- `adb shell dumpsys package com.google.android.youtube`
- `adb shell uiautomator dump /sdcard/youtube-home.xml`
- `adb shell uiautomator dump /sdcard/youtube-watch.xml`
- pulls locales:
  - `docs/youtube-home.xml`
  - `docs/youtube-watch.xml`

## Lo nuevo que sí quedó visible y útil

### 1. Home desacoplada del watch

En `youtube-home.xml` quedaron visibles:

- `toolbar`
- `youtube_logo`
- acción de notificaciones
- acción de búsqueda
- `filter_bar`
- `results`
- `pivot_bar`

Lectura UX:

- el home tiene chrome propio y liviano
- los filtros viven en una capa separada del feed
- el watch no necesita compartir la misma estructura compositiva del home

### 2. Filtros horizontales como capa propia

ADB mostró chips horizontales dentro de `filter_bar`, con elementos como:

- `Todo`
- `Videojuegos`
- `API`
- `Seguridad`
- `Inteligencia artificial`
- `Pódcasts`

Lectura UX:

- YouTube no fuerza una búsqueda para explorar
- usa filtros rápidos horizontales de baja fricción
- estos filtros viven fuera del player y fuera del scroll principal

### 3. Watch page separada por dominios reales

En `youtube-watch.xml` quedaron expuestos contenedores distintos:

- `watch_player`
- `player_overlays`
- `youtube_controls_overlay`
- `watch_while_time_bar_view`
- `video_metadata_layout`
- `watch_panel`
- `watch_list`
- `watch_cinematic_background`
- `cinematic_scrim`

Lectura UX:

- el player, la barra de tiempo, metadata y relacionados **no** están metidos en un bloque único amorfo
- hay separación real entre:
  - superficie de video
  - overlays
  - barra de progreso
  - panel de contenido debajo
  - fondo cinemático

### 4. Mini/expand está modelado como shell propio

ADB mostró `player_overlays` con `content-desc="Ampliar mini reproductor"`.

Lectura UX:

- el estado mini/watch no parece un parche sobre la lista
- se trata como una superficie propia, con acción explícita de expansión
- esto reduce el riesgo de que el feed o la metadata “peleen” con el player

### 5. La barra de tiempo vive aislada

El `SeekBar` aparece dentro de `watch_while_time_bar_view`, separado del resto del panel.

Lectura UX:

- esto ayuda a no recomponer toda la pantalla por cada tick del progreso
- es una referencia directa para seguir endureciendo 60fps en SnapMusic

### 6. Fondo cinemático desacoplado del panel

ADB dejó visibles:

- `watch_cinematic_background`
- `cinematic_image_background`
- `cinematic_scrim`

Lectura UX:

- el efecto visual no invade el player
- el fondo se resuelve como capa independiente
- es un patrón mejor que mezclar blur/scrim/metadata/video en el mismo árbol

## Fullscreen: lo observable sin inventar

En esta pasada no se forzó una orientación nueva ni un tap fiable de fullscreen vía ADB que permita documentar todos los controles exactos.  
Lo que sí quedó probado:

- YouTube ya entra a un watch shell estructurado con player arriba y panel abajo
- la capa de overlays y la barra de tiempo están separadas
- el sistema ya prepara estados de mini/expand con contenedores dedicados

Por lo tanto, la mejora útil para SnapMusic no es “copiar iconos de YouTube” a ciegas, sino copiar esta **separación de superficies**.

## Comparación puntual con SnapMusic

### Lo que SnapMusic ya tiene bien encaminado

- player y mini player propios
- overlay de video unificado
- watch screen con relacionados debajo
- PiP y restore ya encaminados

### Gaps que esta auditoría deja más claros

1. **Watch y feed todavía deben quedar más desacoplados**.  
   YouTube separa player, barra, metadata y relacionados con contenedores propios.

2. **El fondo visual del watch** en SnapMusic todavía debe sostenerse como capa independiente y barata.  
   YouTube lo trata como `cinematic_background`, no como mezcla pesada con todo el panel.

3. **La barra de progreso y overlays** deben seguir aisladas del resto del panel.  
   Esto pega directo en 60fps.

4. **Mini player / expand / restore** conviene tratarlos como shells, no como mutaciones grandes del feed.

## Mejoras concretas recomendadas para SnapMusic

1. Seguir separando:
   - player surface
   - overlays
   - time bar
   - metadata
   - relacionados

2. Mantener el fondo del watch como capa cinemática barata y no como árbol cargado.

3. Evitar que comentarios, `Sigue:` o relacionados dependan del progreso del stream.

4. Reforzar el patrón de “shell estable” para:
   - mini reproductor
   - restore
   - fullscreen

## Impacto esperado si se adopta bien

- menos recomposición lateral
- menos jank en watch screen
- fullscreen y mini player más sólidos
- mejor base para llegar a 60fps reales sin recortar funcionalidad
