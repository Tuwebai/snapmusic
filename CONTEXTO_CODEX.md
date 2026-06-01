# Contexto y Reglas para Codex — SnapMusic
**Versión:** 1.0 | Este archivo debe leerse COMPLETO antes de tocar cualquier archivo

---

## Qué es esta app

SnapMusic es una app Android nativa que imita dos productos:
- **SnapTube**: descarga de YouTube, share de links, selector de formatos/calidades, flujo buscar→reproducir→descargar
- **YouTube**: watch player, feed de videos, calidades reales (360p/720p/1080p), buffering adaptativo, relacionados, mini player, fullscreen

**NO es** una webview. **NO es** React Native. **NO es** una app web. Es Kotlin nativo + Jetpack Compose.

---

## Stack técnico — MEMORIZAR

```
Lenguaje:       Kotlin
UI:             Jetpack Compose (NO XML layouts)
Playback:       Media3 / ExoPlayer
Extracción:     NewPipe Extractor (streams de YouTube)
Descargas:      WorkManager + HttpTransferEngine (OkHttp)
Transcode/Mux:  FFmpegKit (executeAsync, NO execute)
DI:             Manual (SnapMusicGraph, lazy)
Persistencia:   Room + DataStore
Red:            OkHttp (dos clientes: okHttpClient + extractorOkHttpClient)
```

---

## Prioridades absolutas de producto (en orden)

1. **60fps estable** — ningún cambio que baje el frame rate es aceptable
2. **Cero jank** — no se permiten stalls de UI
3. **Cero regresiones** — si algo funciona, no se toca sin necesidad
4. **Descargas rápidas y confiables** — en todos los formatos y calidades
5. **Playback fluido** — sin pausas ni doble prepare
6. **Startup rápido** — < 2 segundos en frío

### Punto seguro de rendimiento

- **Scroll fluido YouTube:** commit `a4270d7` (`Optimize feed paging and thumbnails`).
- Si una regresión vuelve a trabar el scroll del feed, usar ese commit como punto de retorno antes de seguir tocando performance.
- Ese commit dejó el feed de YouTube fluido porque redujo el trabajo pesado durante scroll:
  - eligió thumbnails más cercanos al tamaño real de renderizado en vez de usar siempre la imagen más grande;
  - calculó el tamaño de request de Coil según densidad real de pantalla;
  - mantuvo cache keys estables para thumbnails;
  - limitó el prefetch para no competir con el fling;
  - movió merge/dedupe de resultados fuera del Main Thread;
  - agregó single-flight para evitar cargas concurrentes de páginas;
  - configuró un `ImageLoader` compartido con cache y paralelismo acotado;
  - dejó la telemetría de frames apagada en builds no-debug salvo flag explícito.
- Métrica validada después del commit en APK `perf`: scroll del feed con jank cercano a `2.31%`, p95 `23 ms`, p99 `28 ms`, sin crash y sin cambios visuales.

---

## Reglas de código — OBLIGATORIAS

### Red y I/O

- **NUNCA** hacer I/O de red o disco en el Main Thread
- **SIEMPRE** usar `withContext(Dispatchers.IO)` para I/O
- **NUNCA** usar `runBlocking` en funciones accesibles desde el Main Thread
- **NUNCA** hacer requests de red en `Application.onCreate()`, `init {}` de ViewModel, ni `init {}` de repositorios accesibles antes del primer `withContext(IO)`
- I/O en `Application.onCreate()` → siempre en un `Thread { }.also { it.isDaemon = true }.start()`

### Compose y recomposición

- **SIEMPRE** agregar `@Immutable` a `data class` usadas como parámetros de `@Composable`
- **NUNCA** poner `artworkData`, thumbnails ni ByteArray como dependency keys de LaunchedEffect que llamen `prepare()` o `setMediaItems()`
- **SIEMPRE** usar `distinctUntilChanged()` antes de `stateIn()` en flows derivados
- **SIEMPRE** separar en LaunchedEffect independientes: (a) carga del stream, (b) actualización de metadata/artwork
- **NUNCA** colectar el `youtubeState` completo en composables que solo necesitan un campo — usar los flows derivados (`youtubeMiniPlayerState`, `youtubePlaybackRenderState`, etc.)
- LazyColumn con items → **SIEMPRE** con `key = { item.id }` o `key = { item.url }`

### ExoPlayer / Media3

- **NUNCA** llamar `prepare()` más de una vez por video
- `replaceMediaItem()` con solo cambio de metadata (mismo URI) → NO reinicia el stream
- `replaceMediaItem()` con URI diferente → SÍ reinicia el stream → evitarlo excepto en cambio de calidad
- Los callbacks de `Player.Listener` llegan en el Main Thread → procesarlos rápido, delegar trabajo a coroutines
- `trackSelectionParameters` solo cambiar cuando el player NO está en `STATE_IDLE`

### FFmpegKit

- **SIEMPRE** usar `FFmpegKit.executeAsync()` con `suspendCancellableCoroutine`
- **NUNCA** usar `FFmpegKit.execute()` (bloqueante)
- **SIEMPRE** llamar `FFmpegKit.cancel(session.sessionId)` en el `invokeOnCancellation`

### WorkManager y descargas

- Cada tarea de descarga va a su propia cadena `"snapmusic_queue_${laneIndex}"`
- El buffer de copia de stream es `ByteArray(512 * 1024)` (512KB) — no cambiar
- `@Synchronized` en callbacks de coroutines → reemplazar con `Mutex` de coroutines o `AtomicLong`
- Reportar progreso máximo cada 250ms — no más frecuente

### NewPipe Extractor

- `NewPipe.init()` se llama **una sola vez** — proteger con flag `@Volatile isInitialized`
- Máximo **8 búsquedas concurrentes** simultáneas en `MusicHomeFeedRepository`
- `extractorOkHttpClient` tiene pool de 5 conexiones — respetar ese límite
- Las URLs de stream de YouTube expiran — ante HTTP 403, re-resolver antes de reportar error

### Startup

- `restoreYouTubePlaybackSnapshot()` → restaurar estado con `isReady = false`, NO resolver el stream automáticamente
- `prefetchFeedItems()` desde restore de cache → agregar `delay(3_000L)` antes de disparar
- `cleanupFfmpegWorkDir()` → siempre en background thread, nunca en Main Thread

---

## Paleta de colores — NO cambiar

```kotlin
AccentRed         = #FF3131
AccentRedStrong   = #FF4747
BackgroundPrimary = #050505
BackgroundSecondary = #101010
SurfacePrimary    = #121212
SurfaceElevated   = #1B1B1B
TextPrimary       = #F8F8F8
TextSecondary     = #B8B8B8
SuccessGreen      = #32D583
WarningAmber      = #FFB020
```

---

## Arquitectura de la app — cómo está organizada

```
SnapMusicGraph          → DI manual, todos los singletons lazy
SnapMusicViewModel      → único ViewModel, 3494 líneas (God ViewModel intencional)
SnapMusicNavHost        → navegación Compose, colecta flows específicos (no youtubeState completo)

Flows principales del ViewModel:
  _youtubeState         → YouTubeUiState @Immutable — estado completo de YouTube
  youtubePlaybackRenderState → solo lo que necesita el player (posición, autoplay)
  youtubeMiniPlayerState     → solo lo que necesita el mini player
  navHostPlaybackState       → solo lo que necesita la navegación
  bottomBarUiState           → solo lo que necesita el bottom bar

Descarga:
  DownloadCoordinator → encola en WorkManager (lanes paralelas)
  DownloadWorker      → ejecuta en background, usa HttpTransferEngine + FfmpegKitTranscodeEngine
  HttpTransferEngine  → descarga por chunks con paralelismo configurable
  DownloadSourcePlanner → decide qué estrategia usar (DIRECT, TRANSCODE_AUDIO, MUX_VIDEO_AUDIO)

Extracción:
  NewPipeStreamResolverRepository → todo lo que habla con YouTube via NewPipe
  DownloadSourcePlanner           → convierte streams de NewPipe en planes de descarga
```

---

## Flujos de usuario críticos — no romper

### Flujo 1: Tocar un video en el feed
1. `YouTubeFeedCard.onClick` → `viewModel.openYouTubeWatch(item)`
2. ViewModel resuelve el stream en background (`resolveFeaturedVideo`)
3. Watch player aparece con estado loading
4. `rememberYouTubePlayer` recibe el featured con `playbackUrl` → LaunchedEffect carga stream → `prepare()`
5. Artwork descarga en paralelo → `LaunchedEffect(artworkData)` SEPARADO actualiza solo metadata

### Flujo 2: Descargar un video
1. Usuario abre sheet de formatos → `DownloadFormatSheet`
2. Selecciona calidad → `viewModel.enqueueDownload(request)`
3. `DownloadCoordinator.enqueue()` → WorkManager
4. `DownloadWorker.doWork()` → `resolverRepository.resolveDownloadPlan()` → descarga con `HttpTransferEngine`
5. Si es mux: video + audio en paralelo con `coroutineScope { async {} }`
6. Si requiere transcode: `FfmpegKitTranscodeEngine.extractAudio()` (async)
7. Notificación de éxito/error

### Flujo 3: Compartir link desde otra app
1. Intent recibido en `MainActivity`
2. `viewModel.handleIncomingShare(url)`
3. Resolución del link → si es YouTube: abrir watch player
4. Si no se puede reproducir: ofrecer solo descarga

---

## Qué NO hacer nunca

- ❌ No cambiar el esquema de colores
- ❌ No convertir a XML layouts — todo es Compose
- ❌ No introducir nuevas dependencias sin verificar que no hay una ya en el proyecto que haga lo mismo
- ❌ No romper el mini player ni el fullscreen
- ❌ No tocar `SnapMusicPlaybackService` sin entender que es el proceso de Media3
- ❌ No colectar `youtubeState` completo en NavHost o composables de navegación
- ❌ No hacer queries de Room en el Main Thread
- ❌ No lanzar más de 8 coroutines concurrentes sobre `extractorOkHttpClient`
- ❌ No poner delay() en el Main Thread
- ❌ No usar `runBlocking` en ningún lugar nuevo

---

## Checklist antes de commitear cualquier cambio

- [ ] ¿El cambio hace I/O en el Main Thread? → **rechazar**
- [ ] ¿Agregué artworkData/ByteArray como key de un LaunchedEffect que llama `prepare()`? → **rechazar**
- [ ] ¿Hay alguna `data class` nueva usada en Compose sin `@Immutable`? → **agregar @Immutable**
- [ ] ¿Los items de LazyColumn/LazyRow tienen `key`? → **agregar key**
- [ ] ¿El cambio puede causar más recomposiciones que antes? → **medir antes de merguear**
- [ ] ¿Las descargas de 720p/1080p siguen funcionando? → **probar**
- [ ] ¿El mini player sigue funcionando después del cambio? → **probar**
- [ ] ¿Las notificaciones de descarga siguen apareciendo? → **probar**
- [ ] `./gradlew assembleDebug` sin errores ni warnings nuevos → **obligatorio**

---

## APK final para dispositivo

- El APK final para instalar en el dispositivo y medir fluidez real es el build `perf`: `app/build/outputs/apk/perf/app-arm64-v8a-perf.apk`.
- El build `debug` queda solo para desarrollo y diagnóstico; no usarlo como referencia de 60 FPS.
