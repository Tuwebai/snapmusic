# Auditoría final de lag en dispositivo — SnapMusic

## Alcance de esta etapa
- Cerrar el lag residual fuera del player base.
- Medir y recortar recomposición en:
  - Buscar
  - feed/ranking musical
  - listas largas
  - restauraciones
  - navegación entre estados
- Mantener la validación anclada a:
  - build `debug` instalada en el teléfono
  - telemetría de frame/jank ya integrada
  - macrobenchmark ampliado
  - chequeos locales de compilación

## Línea base confirmada
- El feed y la búsqueda ya no son el cuello dominante de una sola causa; el lag residual venía de varias capas acopladas:
  1. `YouTubeTabContent` observaba a la vez player, comentario, feed y sheet de descarga.
  2. `youtubeFeedScreen` mezclaba visibilidad del player, cola activa y feed visible en la misma proyección consumida por la UI.
  3. El overlay de sugerencias de Buscar compartía corpus/ranking con demasiado trabajo repetido por tecla.
  4. La navegación seguía pagando recomposición lateral cuando cambiaba el item activo, el mini player o el estado de restauración.

## Escenas auditadas

### 1. Inicio → tab Buscar
- Síntoma visible:
  - tirón leve al abrir el overlay de búsqueda y al volver.
- Trigger exacto:
  - abrir/cerrar overlay mientras el feed y el player seguían vivos debajo.
- Causa raíz probable:
  - el estado del overlay estaba demasiado cerca del árbol completo de Home.
- Estado actual del fix:
  - **mejorado**
- Impacto esperado:
  - menos invalidación lateral del `HorizontalPager` y de la landing.

### 2. Inicio → tab YouTube
- Síntoma visible:
  - el scroll del feed seguía pagando trabajo extra al convivir con watch screen, comentario y sheet.
- Trigger exacto:
  - cambio de player, autoplay o descarga preparada.
- Causa raíz probable:
  - `YouTubeTabContent` estaba observando estado ancho y recalculando lista visible en el mismo host.
- Estado actual del fix:
  - **resuelto en esta etapa**
- Impacto esperado:
  - el feed ya no depende de ticks del player ni de sheet de descarga.

### 3. Inicio → tab Convertir
- Síntoma visible:
  - sensación más estable que YouTube, con costo marginal al volver desde otras tabs.
- Trigger exacto:
  - navegación entre tabs superiores con páginas pesadas ya cargadas.
- Causa raíz probable:
  - shell del `HorizontalPager` todavía comparte costo de navegación.
- Estado actual del fix:
  - **mejorado**
- Impacto esperado:
  - transición más consistente en gama media/baja.

### 4. Tipear en Buscar
- Síntoma visible:
  - jank leve al construir sugerencias letra por letra.
- Trigger exacto:
  - reconstrucción repetida del corpus y mezcla remota/fallback por tecla.
- Causa raíz probable:
  - corpus y fallback no estaban suficientemente proyectados antes del render.
- Estado actual del fix:
  - **resuelto en esta etapa**
- Impacto esperado:
  - menos trabajo por tecla y menos lag al abrir el teclado.

### 5. Enviar búsqueda al tab YouTube
- Síntoma visible:
  - tirón corto al pasar de Buscar a resultados.
- Trigger exacto:
  - submit que cambiaba tab, query y feed al mismo tiempo.
- Causa raíz probable:
  - dependencia del flujo de búsqueda con el mismo estado amplio de YouTube.
- Estado actual del fix:
  - **mejorado**
- Impacto esperado:
  - submit más limpio y sin reconstruir el watch host.

### 6. Scroll 5+ pantallas del feed
- Síntoma visible:
  - stutter residual cuando coexistían feed largo + watch screen + thumbnails.
- Trigger exacto:
  - lista visible recalculada al mismo tiempo que cambios de player/cola.
- Causa raíz probable:
  - proyección de watch-next y lista visible acopladas al mismo host.
- Estado actual del fix:
  - **resuelto en esta etapa**
- Impacto esperado:
  - scroll más estable y menos parones dominantes.

### 7. Abrir video
- Síntoma visible:
  - el watch screen quedaba correcto, pero debajo el feed seguía pagando costo de composición.
- Trigger exacto:
  - transición a video y aparición del comentario/sugerencias.
- Causa raíz probable:
  - un solo composable host manejaba todo.
- Estado actual del fix:
  - **resuelto en esta etapa**
- Impacto esperado:
  - apertura más limpia y costo de render repartido por host.

### 8. Minimizar / restaurar
- Síntoma visible:
  - restauraciones podían arrastrar trabajo sobre el feed.
- Trigger exacto:
  - back, swipe down o cambio de visibilidad del watch player.
- Causa raíz probable:
  - el estado de restauración convivía con la misma proyección del feed.
- Estado actual del fix:
  - **mejorado**
- Impacto esperado:
  - menor lag cuando el mini player aparece o se restaura.

### 9. Abrir Reproducir
- Síntoma visible:
  - mejoró mucho, aunque el costo de thumbnails locales sigue siendo sensible en equipos bajos.
- Trigger exacto:
  - entrada a biblioteca local con lista extensa.
- Causa raíz probable:
  - decodificación de miniaturas y render del item activo.
- Estado actual del fix:
  - **mejorado pero no cerrado**
- Impacto esperado:
  - menos stutter, pero todavía hay margen en thumbnails locales y listas muy largas.

### 10. Scroll largo de biblioteca local
- Síntoma visible:
  - sigue siendo uno de los puntos más sensibles después de YouTube.
- Trigger exacto:
  - listas largas + thumbnails + estado activo/descargas.
- Causa raíz probable:
  - costo de imagen y lista local todavía alto para gama baja.
- Estado actual del fix:
  - **mejorado pero no cerrado**
- Impacto esperado:
  - mejor base, con siguiente cuello real concentrado en miniaturas locales y player/detail compartido.

## Remediación aplicada en esta etapa
- Se crearon proyecciones mínimas nuevas:
  - `DownloadSearchSuggestionUiState`
  - `YouTubeSuggestionsUiState`
  - `YouTubeWatchNextUiState`
- Se movió la construcción del corpus y del watch-next a capas previas al render:
  - `BuildSearchSuggestionCorpusUseCase`
  - `BuildWatchNextProjectionUseCase`
- `YouTubeTabContent` quedó partido en hosts finos:
  - host del player/watch
  - host del comentario
  - host de sugerencias
  - host del sheet de descarga
- El feed visible dejó de depender directamente del mismo host que observa:
  - autoplay
  - mini player
  - descarga preparada
  - comentario del watch
- Se amplió `SnapMusicMacrobenchmark` con el flujo:
  - Buscar → YouTube

## Escenas resueltas
- Feed de YouTube desacoplado del host del player.
- Watch-next/sugerencias ya no se recalculan dentro del composable principal.
- Buscar usa corpus ya armado y sugerencias con menos trabajo por tecla.

## Escenas mejoradas pero no cerradas
- Restauración / minimización todavía puede mejorar un poco más en equipos bajos.
- Reproducir y biblioteca local siguen siendo el frente más caro fuera de YouTube.
- El submit Buscar → YouTube ya está mejor, pero todavía puede bajar otro poco el costo de transición.

## Siguiente cuello real priorizado
- **Miniaturas locales + lista larga de Reproducir + detalle activo compartido**.
- Prioridad siguiente:
  1. recortar más costo de thumbnails locales
  2. desacoplar aún más el item activo de la lista completa
  3. validar scroll largo en dispositivo con lista grande real
# Actualizacion 2026-05-18 - cierre parcial del lag residual

- La validacion principal pasa a `benchmark`/`release profileable arm64`; `debug` queda solo para iteracion funcional rapida.
- Se amplio `SnapMusicMacrobenchmark` para cubrir:
  - cambio entre tabs de Inicio
  - abrir video y minimizar/restaurar
  - submit Buscar -> YouTube esperando resultados reales
  - scroll mas largo en Reproducir con back al mini player
- `PreviewScreen` dejo de observar `previewState` completo en la raiz:
  - ahora usa `PreviewPerformanceUiState`
  - se evita recomponer la pantalla completa de Reproducir por ticks de progreso, metadata o cambios internos del item actual
- Se recorto otro frente residual en Reproducir:
  - el mini reproductor ya no navega de nuevo a Reproducir si la ruta actual ya es `Preview`
  - eso evita una restauracion redundante del `NavHost` al tocar el mini player dentro de la misma pantalla
  - las filas de biblioteca dejaron de cambiar fondo completo al alternar item activo
  - las miniaturas locales ahora piden un tamaño menor y reutilizan `memoryCacheKey`
- Se corrigio un bug funcional que tambien afectaba fluidez y consistencia:
  - descargar otro item desde `Segui mirando` ya no toca el `featured` que se esta reproduciendo
  - el sheet de formatos se resuelve con estado dedicado y no corta el stream actual
  - la notificacion de reproduccion ahora restaura el stream activo via `sessionActivity`
- Estado del cuello real:
  - Home/YouTube: mejorado
  - Reproducir: mejorado pero no cerrado
  - Miniaturas locales + scroll largo: sigue siendo el frente residual prioritario
