# Auditoría ADB focalizada de YouTube — watch player, controles y fullscreen

## Alcance

Esta auditoría mira **solo**:

- watch player
- overlays del player
- barra de tiempo
- panel inferior del watch
- fullscreen estructural observable

No repite permisos ni hallazgos ya cubiertos en auditorías previas.

## Evidencia usada

- `dumpsys package com.google.android.youtube`
- `docs/youtube-home.xml`
- `docs/youtube-watch.xml`

## Hallazgos estructurales confirmados por ADB

### 1. El watch de YouTube está partido en contenedores reales

En `youtube-watch.xml` quedaron visibles:

- `watch_player`
- `player_overlays`
- `youtube_controls_overlay`
- `watch_while_time_bar_view`
- `video_metadata_layout`
- `watch_panel`
- `watch_list`

Esto confirma una arquitectura UI muy clara:

- superficie del video
- overlays del video
- barra de progreso
- metadata/acciones
- lista relacionada

cada bloque vive separado.

### 2. La barra de tiempo es una superficie independiente

ADB muestra un `SeekBar` dentro de `watch_while_time_bar_view`.

Qué aporta a SnapMusic:

- la barra no debería colgar del mismo estado ancho que comentarios o relacionados
- el progreso puede actualizarse sin invalidar todo el watch

### 3. Los overlays del player no comparten árbol con el panel inferior

`player_overlays` y `youtube_controls_overlay` viven arriba del player, mientras `watch_panel` y `watch_list` viven abajo.

Qué aporta a SnapMusic:

- los controles fullscreen y los controles sobre video deben quedar desacoplados del panel `Sigue:` / relacionados
- esto baja jank y simplifica el modelo mental del usuario

### 4. El fondo cinemático es otra capa, no el panel principal

ADB dejó visibles:

- `watch_cinematic_background`
- `cinematic_image_background`
- `cinematic_scrim`

Qué aporta a SnapMusic:

- el fondo visual del watch debe ser una capa barata y aparte
- no conviene mezclar blur, gradiente, metadata y video en la misma composición pesada

### 5. El mini/expand ya se trata como shell

`player_overlays` tiene `content-desc="Ampliar mini reproductor"`.

Qué aporta a SnapMusic:

- mini player y restore deben seguir tratándose como shells propios
- no como side effects grandes sobre el feed o la pantalla completa

## Qué se puede clonar correctamente desde esta auditoría

### Sí

- separación de dominios del watch
- aislamiento de la barra de tiempo
- separación entre overlays y panel inferior
- uso de fondo cinemático como capa independiente
- criterio de shell estable para expandir/minimizar

### No, todavía no con ADB sola

- iconografía exacta de cada control en fullscreen
- microespaciados exactos del overlay completo
- todas las transiciones frame a frame del fullscreen horizontal

Para esos puntos, la referencia correcta sigue siendo la captura visual aprobada en el chat.

## Comparación puntual con SnapMusic

### Lo que SnapMusic ya acercó

- player host propio
- overlay propia
- watch con relacionados
- fullscreen horizontal funcional
- mini player y PiP encaminados

### Lo que sigue faltando para quedar al nivel del shell de YouTube

1. **Separar todavía más el watch panel del progreso del stream**.
2. **Reducir acople entre overlays y relacionados**.
3. **Mantener fullscreen como layout independiente** y no como simple variante de la vertical.
4. **Bajar más el costo visual del fondo del watch** cuando compite con el video.

## Recomendaciones cerradas

1. Consolidar un host específico para:
   - player
   - overlays
   - time bar
   - metadata
   - relacionados

2. Hacer que fullscreen lea solo su estado mínimo.

3. No dejar que:
   - comentarios
   - `Sigue:`
   - feed relacionado
   dependan del tick de progreso.

4. Mantener el fondo cinemático como capa visual opcional y barata.
