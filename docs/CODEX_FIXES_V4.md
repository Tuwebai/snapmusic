# Fixes V4 por slices — rendimiento raíz SnapMusic

**Basado en:** `docs/PERFORMANCE_AUDIT_V4.md`  
**Objetivo:** recuperar startup rápido, bajar jank global y dejar playback estable a 60fps sin volver a romper descargas ni reproducción.

## Orden obligatorio de ejecución

1. **Slice 1 — Arranque sin boot ansioso del playback stack**
2. **Slice 2 — Startup silencioso: sacar trabajo global no pedido**
3. **Slice 3 — Recorte fuerte de fan-out en Home/Watch Next**
4. **Slice 4 — Bajar collectors altos y adelgazar Preview/Nav**
5. **Slice 5 — Overlay/progreso sin polling caro**
6. **Slice 6 — Revalidación dura con métricas y smoke**

---

## Slice 1 — Arranque sin boot ansioso del playback stack

### Objetivo
Evitar que la app cree controllers, levante el service y arme `ExoPlayer` apenas abre.

### Archivos foco
- `app/src/main/java/com/juan/snapmusic/navigation/SnapMusicNavHost.kt`
- `app/src/main/java/com/juan/snapmusic/feature/youtube/YouTubePlaybackController.kt`
- `app/src/main/java/com/juan/snapmusic/feature/preview/PreviewPlaybackController.kt`
- `app/src/main/java/com/juan/snapmusic/core/platform/SnapMusicPlaybackService.kt`

### Fixes
- Sacar `rememberManagedYouTubePlayer(viewModel)` y `rememberManagedPreviewPlayer(viewModel)` de la raíz eager del `NavHost`.
- Crear controllers **on-demand** solo cuando exista una de estas condiciones:
  - YouTube player visible
  - YouTube mini-player visible
  - Preview detail visible
  - Preview mini-player visible
  - restore desde notificación/PiP realmente requerido
- Unificar el ownership del controller en hosts hoja, no en la raíz completa del `Scaffold`.
- Si no hay reproducción activa ni restore pendiente, no pedir `MediaController`.
- En `SnapMusicPlaybackService`, evitar trabajo pesado antes del primer uso real del player:
  - separar inicialización del player de wiring accesorio
  - no construir extras no necesarios hasta que haya media real

### Check de cierre
- Abrir la app en frío sin playback previo no debe iniciar el stack completo de reproducción.
- El service no debe levantarse solo por dibujar Home.

---

## Slice 2 — Startup silencioso: sacar trabajo global no pedido

### Objetivo
Que el arranque no dispare trabajo de YouTube/Convertir si el usuario todavía no entró ahí.

### Archivos foco
- `app/src/main/java/com/juan/snapmusic/feature/home/SnapMusicViewModel.kt`
- `app/src/main/java/com/juan/snapmusic/feature/home/HomeScreen.kt`
- `app/src/main/java/com/juan/snapmusic/SnapMusicApplication.kt`

### Fixes
- En `SnapMusicViewModel.init`:
  - sacar `refreshPopularDownloadSearches()` del arranque global
  - moverlo a carga bajo demanda al entrar a Convertir/Descargar
- En `restoreYouTubeHomeFeedCache()`:
  - dejar de prefetchar a los `3s` por default
  - solo prefetchar si la pestaña YouTube está realmente visible o si el usuario la abrió
- `restoreYouTubePlaybackSnapshot()` debe seguir siendo barata:
  - cero resolve de streams
  - cero prefetch asociado en startup
- `HomeScreen`:
  - mover `inspectClipboardCandidate(...)` a una ruta menos intrusiva o con debounce posterior al primer frame útil
  - no re-leer portapapeles agresivamente en cada resume si no cambia el contexto UX
- Revisar `workExecutor` de WorkManager:
  - no dejar un pool fijo innecesariamente grande para el arranque

### Check de cierre
- Startup sin abrir YouTube/Convertir no debe disparar prefetched resolve ni popular queries remotas/pesadas.
- El primer frame útil debe llegar sin ruido de playback o extractor.

---

## Slice 3 — Recorte fuerte de fan-out en Home/Watch Next

### Objetivo
Dejar de saturar extractor/red/CPU cada vez que se entra a YouTube o se abre un video.

### Archivos foco
- `app/src/main/java/com/juan/snapmusic/data/recommendation/MusicHomeFeedRepository.kt`
- `app/src/main/java/com/juan/snapmusic/feature/home/SnapMusicViewModel.kt`

### Fixes
- `loadMusicHomeFeed()`:
  - bajar `queryCount`
  - bajar `queryVideoLimit`
  - limitar concurrencia real de búsquedas
  - preferir carga incremental por batches, no tormenta inicial
- Evitar mezclar demasiados candidatos antes del primer paint de feed.
- `recommendWatchNext()`:
  - reducir cantidad de búsquedas auxiliares
  - no enriquecer mientras el usuario todavía está estabilizando el playback inicial
- `enrichWatchNextQueue()`:
  - debouncing más estricto
  - cancelar fuerte al cambiar de video/ruta
  - no tocar queue/watch-next si el usuario no llegó a una sesión estable
- `preResolveNextQueueItem()`:
  - priorizar cache
  - usar red solo en condiciones más estrictas
  - no competir con el arranque del video actual
- `prefetchFeedItems()`:
  - dejar de correr automáticamente sobre resultados recién cargados si la sesión ya está pesada

### Check de cierre
- Entrar a YouTube no debe disparar tormenta de búsquedas paralelas.
- Abrir un video no debe sentirse peor por enriquecer Watch Next en segundo plano.

---

## Slice 4 — Bajar collectors altos y adelgazar Preview/Nav

### Objetivo
Recortar recomposición global y sacar estado ancho de hosts altos.

### Archivos foco
- `app/src/main/java/com/juan/snapmusic/navigation/SnapMusicNavHost.kt`
- `app/src/main/java/com/juan/snapmusic/feature/preview/PreviewScreen.kt`
- `app/src/main/java/com/juan/snapmusic/feature/home/SnapMusicViewModel.kt`

### Fixes
- En `SnapMusicNavHost`:
  - dejar un solo collector por preocupación real
  - separar hosts mínimos para notificación, PiP, mini-player y bottom bar
  - evitar colectar el mismo estado raíz en tres composables distintos si alcanza con flags más finos
- En `rememberManagedPreviewPlayer()`:
  - sacar `currentPositionMs` del contrato normal
  - pasar solo sesión/playlist cuando realmente cambia
  - dejar el seek como comando puntual, no como parte del render state ancho
- En Preview:
  - `PreviewDetailHost` no debe colectar `detailState` y `libraryState` enteros juntos
  - separar item activo / acciones / selección / biblioteca en hosts hoja
  - `PreviewLibraryRoot` no debe recalcular búsqueda/selección completa por cambios laterales del shell de descargas
- En el ViewModel:
  - revisar proyecciones que todavía mezclan listas completas con flags pequeños
  - usar `distinctUntilChangedBy` donde el estado alto solo necesita un subconjunto

### Check de cierre
- Cambios de descargas, restore o mini-player no deben arrastrar recomposición amplia de Preview/Nav.
- El player local no debe vivir colgado de estado de posición en la raíz.

---

## Slice 5 — Overlay/progreso sin polling caro

### Objetivo
Mantener controles fluidos sin loops periódicos agresivos en rutas calientes.

### Archivos foco
- `app/src/main/java/com/juan/snapmusic/feature/player/PlaybackOverlayState.kt`
- `app/src/main/java/com/juan/snapmusic/feature/youtube/YouTubeFeedComponents.kt`
- `app/src/main/java/com/juan/snapmusic/feature/preview/PreviewPlaybackUi.kt`
- `app/src/main/java/com/juan/snapmusic/feature/youtube/YouTubePlaybackController.kt`

### Fixes
- Reemplazar el modelo actual por:
  - `Player.Listener` para play/pause/buffering/duration/tracks
  - ticker de posición solo cuando:
    - el player esté reproduciendo
    - el overlay visible realmente necesite progreso
- Evitar polling en idle.
- Evitar que fullscreen/inline dupliquen loops si comparten el mismo estado.
- Si el usuario no está interactuando con el slider, usar un ritmo más barato o directamente nada fuera de eventos.
- Mantener reporte de progreso persistible separado del overlay visual.

### Check de cierre
- Video watch/fullscreen y preview video deben mantener controles fluidos sin loops sobrantes.

### Estado 2026-05-23
- Aplicado.
- `PlaybackOverlayState` dejó de pollinear en idle:
  - ahora espera eventos del `Player.Listener` cuando no hace falta progreso vivo
  - solo mantiene ticker mientras el player reproduce y el overlay realmente necesita posición
- Se conserva el mismo contrato visual; no hubo cambios de UI.

---

## Slice 6 — Revalidación dura con métricas y smoke

### Objetivo
Cerrar la ola con números, no por percepción solamente.

### Checks mínimos
- `:app:compileDebugKotlin`
- `:app:testDebugUnitTest`
- `:benchmark:assembleBenchmark`

### Medición obligatoria
- Cold start real en dispositivo medio
- `gfxinfo`
- macrobenchmark de:
  - Home startup
  - tabs Home
  - YouTube feed
  - abrir watch
  - fullscreen / minimizar / restore
  - Preview local

### Smoke manual obligatorio
- abrir la app en frío
- navegar Home → YouTube → Preview → Settings
- abrir video y cambiar entre fullscreen/mini-player
- abrir audio/video local
- verificar que descargas activas no hagan tiritar Home o Preview

### Criterio de aceptación
- startup percibido cercano a `~2s`
- `p95 <= 20 ms` en Home/Search
- `p95 <= 16.6 ms` y jank `< 3%` en playback
- sin regresiones en:
  - notificación
  - restore
  - autoplay
  - descargas
  - audio/video local

---

## Regla de implementación

- No mezclar todas las slices en un parche gigante.
- Ejecutar **slice por slice**, validar, medir y recién después seguir.
- Si una slice toca playback, no cerrarla sin smoke funcional de:
  - play/pause
  - next/previous
  - mini-player
  - fullscreen
  - restore desde notificación
