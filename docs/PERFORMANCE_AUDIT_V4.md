# Auditoría integral de rendimiento V4 — SnapMusic

**Fecha:** 2026-05-23  
**Scope:** startup, navegación raíz, Home, YouTube watch/feed, Preview y playback stack compartido.

## Resumen ejecutivo

- La regresión de arranque `~2s -> ~5s` no sale de un único bug: hoy la app enciende demasiado trabajo **antes** de que el usuario haga nada.
- El culpable principal del cold start es el **boot ansioso del stack de reproducción**: la raíz crea dos `MediaController` al entrar, eso levanta `SnapMusicPlaybackService`, y el service construye `ExoPlayer + MediaSession` en caliente.
- El segundo bloque grave es **trabajo no pedido en `SnapMusicViewModel.init`**: snapshot/cache/populares se restauran siempre, y el cache de YouTube vuelve a prefetchar solo 3 segundos después aunque el usuario no haya abierto YouTube.
- El tercer bloque grave es **fan-out de red/extractor sobredimensionado** en YouTube: Home y Watch Next abren demasiadas búsquedas paralelas y luego agregan más prefetch/pre-resolve encima.
- El cuarto bloque sigue siendo **recomposición amplia en raíces**: `SnapMusicNavHost` todavía concentra collectors y los players gestionados viven demasiado arriba.
- El quinto bloque es **polling de overlays**: aunque ya mejoró respecto a olas anteriores, la capa de controles de video sigue usando ticker periódico en rutas calientes.

## Culpables críticos confirmados 🔴

### C1 — `SnapMusicNavHost` crea los dos players gestionados apenas entra la app

**Evidencia**
- `app/src/main/java/com/juan/snapmusic/navigation/SnapMusicNavHost.kt:71-72`
- `app/src/main/java/com/juan/snapmusic/navigation/SnapMusicNavHost.kt:524-573`
- `app/src/main/java/com/juan/snapmusic/feature/youtube/YouTubePlaybackController.kt:104-131`
- `app/src/main/java/com/juan/snapmusic/feature/preview/PreviewPlaybackController.kt:29-57`

**Qué pasa**
- La raíz llama siempre a `rememberManagedYouTubePlayer(viewModel)` y `rememberManagedPreviewPlayer(viewModel)`.
- Ambos terminan en `MediaController.Builder(...).buildAsync()` aunque no haya reproducción activa, mini-player visible ni pantalla de detalle abierta.

**Impacto**
- Cold start más lento por conexión temprana al `MediaSessionService`.
- Se instancian listeners, rutas y estado de player demasiado arriba.
- La app paga costo de playback incluso cuando el usuario solo abre Home o Configuración.

**Veredicto**
- Confirmado como culpable raíz del arranque lento.

### C2 — `SnapMusicPlaybackService` construye `ExoPlayer` y `MediaSession` en el primer connect

**Evidencia**
- `app/src/main/java/com/juan/snapmusic/core/platform/SnapMusicPlaybackService.kt:22-89`

**Qué pasa**
- En `onCreate()` del service se arma en el acto:
  - `DefaultTrackSelector`
  - `DefaultLoadControl`
  - `ExoPlayer`
  - `SnapMusicPlaybackMediaSourceFactory`
  - `MediaSession`

**Impacto**
- El costo entra pegado al primer `MediaController`, o sea pegado al arranque por culpa de `C1`.
- Todo esto ocurre en la ruta de creación del service, antes de que exista una reproducción real.

**Veredicto**
- Confirmado como segundo culpable raíz del startup regression.

### C3 — `SnapMusicViewModel.init` sigue disparando trabajo global no pedido

**Evidencia**
- `app/src/main/java/com/juan/snapmusic/feature/home/SnapMusicViewModel.kt:1059-1073`
- `app/src/main/java/com/juan/snapmusic/feature/home/SnapMusicViewModel.kt:1826-1829`
- `app/src/main/java/com/juan/snapmusic/feature/home/SnapMusicViewModel.kt:2274-2307`
- `app/src/main/java/com/juan/snapmusic/feature/home/SnapMusicViewModel.kt:2969-2979`

**Qué pasa**
- Al crearse el ViewModel se ejecuta siempre:
  - `restoreInterruptedDownloads()`
  - colección de preferencias para autoplay
  - `restoreYouTubeHomeFeedCache()`
  - `restoreYouTubePlaybackSnapshot()`
  - `refreshPopularDownloadSearches()`
- `restoreYouTubeHomeFeedCache()` ya no resuelve streams de una, pero si encuentra cache hace `delay(3_000)` y luego `prefetchFeedItems(cachedItems)`.
- `refreshPopularDownloadSearches()` sigue leyendo perfil/historial aunque el usuario no abra la pestaña Convertir.

**Impacto**
- Más I/O y churn de estado apenas abre la app.
- Trabajo diferido que vuelve a pegar 3 segundos después del arranque.
- El startup ya no está “limpio”; solo está menos roto que antes.

**Veredicto**
- Confirmado. No es el peor culpable, pero sí uno de los que ensucia el arranque apenas se crea la UI.

### C4 — `MusicHomeFeedRepository.loadMusicHomeFeed()` abre un fan-out exagerado de búsquedas

**Evidencia**
- `app/src/main/java/com/juan/snapmusic/data/recommendation/MusicHomeFeedRepository.kt:24-69`
- `app/src/main/java/com/juan/snapmusic/feature/home/SnapMusicViewModel.kt:1579-1616`

**Qué pasa**
- Para armar Home de YouTube, el repo puede lanzar:
  - `loadTrending(...)`
  - entre `10` y `64` llamadas a `resolverRepository.searchVideos(...)`
- Después mezcla, deduplica y rankea todo ese set en memoria.

**Impacto**
- Pico fuerte de extractor, red, CPU y GC cuando el usuario entra a YouTube/Home.
- Cualquier scroll, animación o transición que conviva con eso queda más expuesta a jank.

**Veredicto**
- Confirmado como culpable estructural del mal rendimiento al abrir/usar YouTube.

### C5 — Watch Next sigue agregando tormenta de requests mientras ya se está reproduciendo

**Evidencia**
- `app/src/main/java/com/juan/snapmusic/data/recommendation/MusicHomeFeedRepository.kt:104-145`
- `app/src/main/java/com/juan/snapmusic/feature/home/SnapMusicViewModel.kt:2467-2528`
- `app/src/main/java/com/juan/snapmusic/feature/home/SnapMusicViewModel.kt:2642-2662`

**Qué pasa**
- Al abrir un video:
  - `enrichWatchNextQueue()` dispara `recommendWatchNext(...)` después de un delay.
  - `recommendWatchNext(...)` mezcla `loadRelatedVideos(...)` con varias `searchVideos(...)`.
  - además `preResolveNextQueueItem(...)` intenta resolver de antemano el siguiente ítem.

**Impacto**
- La reproducción convive con trabajo de red/resolución que no es imprescindible para el primer frame.
- Se mete churn extra de estado en `playbackQueue`, `watchNextItems`, `nextUpItem` y `preloadedNextFeatured`.

**Veredicto**
- Confirmado como culpable de jank/responsividad pobre durante watch.

### C6 — La raíz todavía concentra collectors y estado demasiado ancho

**Evidencia**
- `app/src/main/java/com/juan/snapmusic/navigation/SnapMusicNavHost.kt:180-308`
- `app/src/main/java/com/juan/snapmusic/feature/home/SnapMusicViewModel.kt:991-1056`
- `app/src/main/java/com/juan/snapmusic/feature/preview/PreviewScreen.kt:134-155`
- `app/src/main/java/com/juan/snapmusic/feature/preview/PreviewScreen.kt:263-290`

**Qué pasa**
- `SnapMusicNavHost` sigue colectando varias veces en la raíz:
  - `navHostPlaybackState`
  - `bottomBarUiState`
  - estados PiP/mini-player
- `rememberManagedPreviewPlayer()` sigue colgado de `previewPlaybackRenderState`, que mezcla:
  - `preview`
  - `autoPlayRequestId`
  - `playlist`
  - `currentPositionMs`
- `PreviewDetailHost` junta `detailState` + `libraryState` en el mismo host.
- `PreviewLibraryRoot` junta `libraryState` + `activeDownloadCount` y además deriva búsqueda/selección sobre la lista completa.

**Impacto**
- Más recomposición de la necesaria en hosts altos.
- Cambios laterales de Preview o navegación siguen pegando donde no deberían.

**Veredicto**
- Confirmado. La ola V3 bajó bastante, pero la raíz todavía no quedó delgada.

### C7 — `PlaybackOverlayState` sigue con polling periódico en rutas de video

**Evidencia**
- `app/src/main/java/com/juan/snapmusic/feature/player/PlaybackOverlayState.kt:22-88`
- `app/src/main/java/com/juan/snapmusic/feature/youtube/YouTubeFeedComponents.kt:321-322`
- `app/src/main/java/com/juan/snapmusic/feature/preview/PreviewPlaybackUi.kt:282-289`

**Qué pasa**
- El estado del overlay usa:
  - `Player.Listener`
  - más `while (isActive) { ... delay(...) }`
- Mientras los controles están activos, el ticker sigue vivo.

**Impacto**
- No es el peor culpable del startup, pero sí agrega trabajo periódico en watch/fullscreen y preview video.
- Sigue siendo un amplificador de jank cuando la UI ya viene cargada por otros bloques.

**Veredicto**
- Confirmado como amplificador, no como raíz única.

## Hallazgos importantes 🟡

### I1 — El arranque sigue leyendo el portapapeles apenas entra y en cada `ON_RESUME`

**Evidencia**
- `app/src/main/java/com/juan/snapmusic/feature/home/HomeScreen.kt:46-65`

**Impacto**
- No explica por sí solo los 5 segundos, pero mete trabajo de sistema/UI en una ruta que debería ser barata.

### I2 — `WorkManager` usa un `fixedThreadPool(6)` propio desde `Application`

**Evidencia**
- `app/src/main/java/com/juan/snapmusic/SnapMusicApplication.kt:10`
- `app/src/main/java/com/juan/snapmusic/SnapMusicApplication.kt:18-21`

**Impacto**
- Puede adelantar creación de threads e incrementar presión de arranque aunque no haya cola activa todavía.
- Quedó como deuda del audit de startup anterior.

### I3 — `prefetchFeedItems()` sigue resolviendo items extras después de Home/Búsqueda

**Evidencia**
- `app/src/main/java/com/juan/snapmusic/feature/home/SnapMusicViewModel.kt:2958-2966`
- `app/src/main/java/com/juan/snapmusic/feature/home/SnapMusicViewModel.kt:1615`
- `app/src/main/java/com/juan/snapmusic/feature/home/SnapMusicViewModel.kt:1665`

**Impacto**
- Son solo 2 items, pero se suman a un pipeline que ya viene cargado.

## Qué ya no aparece como culpable principal 🟢

- `cleanupFfmpegWorkDir()` ya corre fuera del main thread.
- El estado principal de playback ya tiene la mayor parte de los `@Immutable` importantes.
- Preview ya no mantiene el doble productor viejo inline/fullscreen que existía antes de V3.

## Ranking real de culpables raíz

1. Boot ansioso de `MediaController` en `SnapMusicNavHost`
2. Inicialización pesada de `SnapMusicPlaybackService`
3. Trabajo global no pedido en `SnapMusicViewModel.init`
4. Fan-out excesivo de `loadMusicHomeFeed()`
5. Fan-out de `recommendWatchNext()` + `preResolveNextQueueItem()`
6. Collectors altos todavía anchos en NavHost/Preview
7. Polling de overlays como amplificador

## Criterio de cierre para la próxima ola

- **Cold start sin playback activo:** volver a un arranque percibido cercano a `~2s` en gama media.
- **Home/Search:** `p95 <= 20 ms` y sin saltos visibles al abrir o cambiar tabs.
- **YouTube/Preview playback:** `p95 <= 16.6 ms`, jank `< 3%`.
- **Sin regresiones funcionales** en:
  - mini-player
  - fullscreen
  - restore desde notificación
  - autoplay
  - watch next
  - downloads activas en paralelo

## Conclusión

La app no está lenta por un detalle aislado: hoy el problema es una combinación de **boot prematuro del playback stack**, **trabajo automático que arranca sin que el usuario lo pida**, **demasiado fan-out de extractor/red en YouTube**, y **estado todavía demasiado ancho en la raíz**.  
La próxima remediación tiene que atacar esos cuatro bloques en ese orden; si no, seguir parchando jank local no va a devolver un startup rápido ni una UI estable a 60fps.
