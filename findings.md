# Findings — SnapMusic Android

## Remediación nueva de playback 60fps — 2026-05-21
- El cuello principal confirmado en reproducción era estructural y no solo visual:
  - YouTube creaba polling de progreso/buffer duplicado entre overlay inline y fullscreen.
  - varios `AndroidView(PlayerView)` seguían embebidos dentro de shells que recomponían por estado de reproducción.
- La corrección elegida para cortar jank por recomposición fue:
  - introducir un estado compartido `PlaybackOverlayState`
  - mover la producción de progreso/buffer a un único helper reusable
  - aislar `PlayerView` en un leaf común `PlayerSurface`
  - reutilizar bindings comunes para slider/seek en vez de repetir lógica local
- Preview ya tenía una base mejor que YouTube porque compartía `rememberPreviewPlaybackState`, así que la remediación ahí se enfocó en:
  - unificar surfaces Media3
  - evitar duplicación de wiring entre inline, PiP, hero y fullscreen
  - reutilizar bindings de seek para los dos paneles locales
- Se agregó además un escenario macrobenchmark nuevo para medir:
  - watch
  - fullscreen
  - salida
  - minimizado
  - retorno al player

## ConvertIO actual
- La app desktop actual usa `yt-dlp` + `ffmpeg` y una UI Tkinter.
- La lógica importante a portar es:
  - validación de URL
  - resolución de metadata
  - selección de formato/calidad
  - progreso
  - cola
  - historial
  - cancelación
  - preview del último resultado
  - notificaciones

## Donor SnapMusic-Nativa
- Existe una base Compose nativa con:
  - `Theme.kt`
  - `UrlScreen.kt`
  - `BrandHeader`, `AppHeader`, badges y tarjetas
  - assets `snapmusic_logo.png` y `snapmusic_splash.png`
- El donor sirve para branding, lenguaje visual y composición base, no para la lógica de descarga.

## NewPipeExtractor
- La documentación pública muestra:
  - `NewPipe.init(downloader, localization, contentCountry)`
  - `StreamInfo.getInfo(url)`
  - `StreamInfo.getAudioStreams()`
  - `StreamInfo.getVideoStreams()`
  - `StreamInfo.getVideoOnlyStreams()`
- Esto alcanza para mapear metadata y variantes de descarga.

## FFmpegKit
- El fork `thebytearray/ffmpeg-kit` sigue publicando bundles Android AAR en releases.
- La estrategia del proyecto será vendorizar el AAR dentro del repo y cargarlo como dependencia local.

## Dirección de implementación
- Modular monolith en un solo módulo `app`.
- Persistencia con Room/DataStore.
- Descargas largas con WorkManager en foreground.
- Preview local con Media3/ExoPlayer.

## Estado real de la slice implementada
- Proyecto Android funcional generado y compilando.
- `NewPipeExtractor` ya quedó integrado para análisis de URL y mapeo de streams.
- Cola, historial y preferencias ya persisten localmente.
- Descarga directa ya funciona para variantes que no requieren transcodificación ni mux.
- MP3 y mux avanzado siguen encapsulados detrás de `TranscodeEngine`, con implementación temporal `NoOpTranscodeEngine`.

## Research nuevo: guía SnapTube
- Las páginas públicas de SnapTube repiten cuatro pilares:
  - link directo o compartir
  - formatos y calidades visibles
  - descargas en segundo plano
  - biblioteca offline simple
- Para SnapMusic conviene tomar solo lo normal:
  - share target
  - clipboard hint
  - presets rápidos
  - MP3/M4A/MP4 con lógica real

## Auditoría nueva: feed YouTube y watch-next sin tope artificial
- La ayuda oficial de YouTube indica que:
  - Home depende principalmente del historial/intereses.
  - “A continuación” depende primero del video actual.
  - las sugerencias de búsqueda mezclan texto escrito, historial y popularidad.
- La documentación oficial de NewPipeExtractor sí expone paginación real para:
  - `SearchInfo.getMoreItems(...)`
  - `KioskInfo.getMoreItems(...)`
- SnapMusic estaba desaprovechando eso:
  - `loadTrending()` y `searchVideos()` solo leían la primera página.
  - el feed Home se sentía “corto” porque el ranking recalculaba sobre un pool limitado.
  - watch-next también se quedaba corto porque dependía de búsquedas y relacionados demasiado acotados.
- Para mantener 60fps no conviene cargar cientos de items de golpe:
  - la solución correcta es paginación real por lotes y ranking previo al render.

## Auditoría nueva: calidad de video real en playback
- La ayuda oficial de YouTube confirma que:
  - la calidad es adaptativa por red/pantalla/compatibilidad;
  - la selección manual real vive en el flujo “Advanced”.
- La documentación oficial de Media3 confirma que:
  - la selección exacta de video se hace con `TrackSelectionOverride`;
  - los límites de viewport/tamaño afectan qué track termina seleccionado;
  - el estado real conviene leerlo desde `onTracksChanged`.
- En SnapMusic había dos problemas mezclados:
  - la UI adelantaba una calidad “esperada” antes de validar la realmente seleccionada;
  - el override manual de video tomaba solo el primer `TrackGroup`, lo que podía dejar 360p aunque el stream adaptativo ofreciera alturas mayores.
- La corrección elegida fue:
  - mantener playback sobre manifest adaptativo cuando existe;
  - elegir el override manual sobre el mejor track soportado entre todos los grupos de video;
  - mostrar en UI la calidad real aplicada y no prometer la pedida si todavía no se confirmó.
- Auditoría adicional de raíz sobre `NewPipeExtractor` mostró un caso más duro:
  - en muchos videos públicos `dashMpdUrl` y `hlsUrl` vienen vacíos;
  - aun así existen `videoOnlyStreams` en 720p/1080p y `audioStreams` separados;
  - por eso SnapMusic no podía llegar a HD real solo con progresivos y necesitaba playback fusionado video+audio dentro de Media3.
  - about screen con autor visible
- No conviene sumar ahora extras como navegador interno, vault, status saver o feed propio.

## Auditoría enterprise 2026-05-17
- `lintDebug` hoy falla con 33 errores y 85 warnings.
- Los blockers confirmados de compatibilidad son:
  - uso de `PictureInPictureParams.Builder` y `RemoteAction` sin encapsulado completo para `minSdk 24`
  - uso de `java.util.Base64` en `YouTubePlaybackSnapshotCodec` sin desugaring ni alternativa compatible
  - uso inválido de `SessionResult.RESULT_ERROR_NOT_SUPPORTED`
- Hay deuda estructural fuerte por tamaño y concentración de responsabilidades:
  - `SnapMusicViewModel.kt` ~1010 líneas
  - `QueueScreen.kt` ~710
  - `SettingsPanels.kt` ~673
  - `YouTubeFeedComponents.kt` ~579
  - `HomeLandingPanels.kt` ~573
  - `PreviewPlaybackUi.kt` ~507
- `refreshLocalPreviewLibrary()` delega a `StorageRepository.listLocalMedia()` sin forzar `Dispatchers.IO`, mientras que el repositorio consulta audio y video, arma listas completas y ordena en memoria.
- La raíz visual observa demasiado estado global a la vez:
- `SnapMusicApp`
- `SnapMusicNavHost`

## Auditoría observable SnapTube vs SnapMusic — 2026-05-19
- SnapTube instalado en el dispositivo:
  - paquete: `com.snaptube.premium`
  - versión: `7.58.1.75872701`

## Auditoría integral de flujos — 2026-05-21
- Evidencia real tomada del teléfono con `adb`:
  - `adb shell am start -W -n com.juan.snapmusic/.MainActivity`
  - `adb shell dumpsys gfxinfo com.juan.snapmusic`
  - `adb logcat -d`
- Hallazgo más duro:
  - `gfxinfo` del proceso vivo dio `118` frames renderizados y `108` frames con jank (`91.53%`)
  - percentiles:
    - p50 `101ms`
    - p90 `350ms`
    - p95 `450ms`
    - p99 `1450ms`
  - causas dominantes reportadas por el sistema:
    - `Slow UI thread: 99`
    - `Slow issue draw commands: 108`
- Los logs además muestran secuencias repetidas de:
  - `AudioTrack pause`
  - `AudioTrack stop`
  - `AudioTrack start`
  compatibles con reconfiguraciones del playback todavía demasiado frecuentes.
- Conclusión técnica actual:
  - el cuello más crítico ya no es el feed solo ni la calidad sola;
  - hoy la prioridad real pasa por:
    1. arranque del watch player
    2. jank estructural de la raíz/navegación
    3. biblioteca local con miniaturas y listas largas
- Reporte persistido en:
  - `docs/auditoria-flujos-completos-app.md`

## Re-auditoría de regresión y corrección — 2026-05-21
- Se aislaron tres culpables concretos del jank reintroducido:
  1. `SnapMusicApp` recompunía toda la raíz por flujos de PiP/autoplay que solo debían actualizar la `Activity`.
  2. `PreviewScreen` observaba el `PreviewDownloadsState` completo en raíz, por lo que el progreso de descargas invalidaba `Reproducir`.
  3. `YouTubeFeedComponents` mezclaba polling de progreso/buffer con el mismo shell que hospeda `PlayerView`, spinner y fullscreen.
- Corrección aplicada:
  - consumo de PiP/autoplay movido a `LaunchedEffect + combine(...)`
  - nuevo `PreviewDownloadsShellState` mínimo para la raíz de `PreviewScreen`
  - polling visual del video movido a hosts de overlay dedicados
- Validación ejecutada:
  - `.\gradlew.bat :app:compileDebugKotlin`
  - `minSdk=21`
  - `targetSdk=35`
  - `primaryCpuAbi=arm64-v8a`
  - `base.apk` observado: ~`46,81 MB`
- SnapMusic observado en el mismo dispositivo:
  - versión: `1.0.77`
  - `minSdk=24`
  - `targetSdk=34`
  - APK benchmark arm64 actual: ~`43,72 MB`
- Permisos/flags visibles en SnapTube que **no conviene copiar ciegamente**:
  - `MANAGE_EXTERNAL_STORAGE`
  - `SYSTEM_ALERT_WINDOW`
  - `REQUEST_LEGACY_EXTERNAL_STORAGE`
  - `LARGE_HEAP`
- Capacidades observables que **sí vale la pena usar como referencia**:
  - `SEND` + `SEND_MULTIPLE`
  - muchos `VIEW/BROWSABLE`
  - continuidad entre foreground de descarga, playback y reentrada desde notificación
- Estado actual de SnapMusic:
  - ya usa scoped storage moderno (`READ_MEDIA_AUDIO`, `READ_MEDIA_VIDEO`)
  - no depende de permisos invasivos
  - ya tiene foreground services separados por `mediaPlayback` y `dataSync`
- Hallazgo clave:
  - el plan correcto para SnapMusic no es “parecerse” a SnapTube en permisos, sino mejorar routing externo, share target y reentrada sin romper el modelo de seguridad actual.
  - la mejora visible correcta para Fase D era separar el routing externo del string único compartido y tratar múltiples links como una selección explícita, no como autoplay ni cola forzada.
  - `HomeScreen`
  - `PreviewScreen`
- El flujo de transcodificación sigue dependiendo de `NoOpTranscodeEngine`, por lo que la promesa funcional de ciertos formatos todavía está degradada.
- Calidad de release actual:
  - `isMinifyEnabled = false`
  - sin `androidTest`
  - sin baseline ni política de cierre de lint
  - `targetSdk = 34`, ya por detrás del último ciclo

## Rendimiento real auditado en listas y feed
- El hot spot más claro estaba en `syncYouTubePlaybackProgress()`:
  - escribía `currentPositionMs` en `youtubeState` en cada pulso de reproducción
  - eso arrastraba recomposiciones evitables sobre la capa YouTube y su navegación asociada
- `HomeScreen` estaba manteniendo páginas pesadas precargadas dentro del `HorizontalPager`, incluso fuera de foco.
- `YouTubeTabContent` observaba un estado demasiado ancho para resolver a la vez:

## Auditoría ADB nueva de SnapTube y YouTube sobre stream/fullscreen/UX
- SnapTube, observado por ADB en `com.snaptube.premium`, confirmó un patrón de UX bien distinto al flujo actual de SnapMusic:
  - landing de **búsqueda primero**
  - tabs superiores de dominio (`Buscar`, `YouTube`, `Música`, `Más`)
  - bottom nav corta (`Descargar`, `Reproducir`, `Configuración`)
  - sheet de descarga con **progresive disclosure** (`Más formatos`) y CTA fijo abajo
  - el link compartido puede caer directo en análisis/formatos sin obligar a abrir player primero
- En los dumps de SnapTube quedaron visibles resource ids útiles para copiar criterio de UX sin inventar lógica:
  - `search_view`
  - `tabs`
  - `format_listview`
  - `tv_more_formats`
  - `download_button`
- YouTube, observado por ADB en `com.google.android.youtube`, confirmó que el watch moderno separa claramente:
  - contenedor del player (`watch_player`)
  - overlays del player (`player_overlays`, `youtube_controls_overlay`)
  - barra de tiempo aislada (`watch_while_time_bar_view`)
  - panel watch/metadata/relacionados por debajo (`watch_panel`, `watch_list`)
  - fondo cinemático separado (`watch_cinematic_background`, `cinematic_scrim`)
- El home real de YouTube observado en el teléfono mantiene:
  - toolbar superior mínima con logo + acciones
  - barra de chips/filtros horizontal
  - feed desacoplado del watch
  - bottom nav fija
- Hallazgo comparativo clave para SnapMusic:
  - ya no hace falta copiar más permisos ni intents de SnapTube/YouTube para mejorar UX
  - sí conviene copiar **estructura de flujo**:
    - análisis directo desde share/deeplink
    - sheet de formatos resumida con expansión manual
    - watch shell más desacoplado entre player, overlays y relacionados
    - continuidad clara entre analizar, reproducir y descargar

## Corrección de foco: auditoría específica para clonado de player/watch/fullscreen
- La auditoría amplia anterior se abrió demasiado hacia home, permisos y flujo general.
- Para clonar bien el player hacía falta separar dos fuentes:
  - **SnapTube** como referencia visual aprobada en el chat para controles, tamaños y jerarquía
  - **YouTube** como referencia ADB estructural para watch shell, overlays, time bar y panel inferior
- El patrón correcto para SnapMusic no es copiar “la app completa”, sino cerrar este stack:
  - watch shell vertical
  - overlay de controles exacta
  - fullscreen horizontal como host propio
  - mini player / restore como continuidad de la misma familia visual
  - feed
  - player destacado
  - sheet de descarga
  - autoplay
  - errores
  Separarlo reduce trabajo cuando cambia el reproductor pero no la lista.
- `QueueScreen` recalculaba `activeItems` y `archivedItems` en cada recomposición aunque la cola base no hubiera cambiado.
- El cambio de tabs en Inicio también tenía trabajo duplicado:
  - los taps hacían `animateScrollToPage(...)`
  - al mismo tiempo el estado externo de tab empujaba otro `scrollToPage(...)`
  - eso metía sensación de transición pesada y más trabajo del necesario
- La watch screen de YouTube seguía dejando fondo negro hasta primer frame:
  - el `PlayerView` entraba antes de renderizar video
  - la UI no retenía el thumbnail como puente visual
  - el spinner reforzaba la percepción de latencia aunque el stream ya estuviera resolviéndose

## Auditoría 60fps + tamaño 2026-05-18
- El APK debug actual quedó en ~`99.45 MB`.
- La causa dominante del tamaño sigue siendo `ffmpeg-kit-full-6.1.4.aar`:
  - pesa ~`33.32 MB`
  - incluye binarios `arm64-v8a` y `armeabi-v7a`
  - `libavcodec.so`, `libavfilter.so` y `libavformat.so` son los más pesados
- Con shrink/minify + splits ABI, los outputs release medidos quedaron así:
  - `app-arm64-v8a-release-unsigned.apk` ~`43.66 MB`
  - `app-armeabi-v7a-release-unsigned.apk` ~`39.62 MB`
- Se confirmó que el arranque de la app cargaba la biblioteca local demasiado temprano:
  - `refreshLocalPreviewLibrary()` se disparaba en `init`
  - `PreviewScreen` además duplicaba refresh por efectos separados
- Se confirmó que `SnapMusicApp` seguía observando más estado del necesario solo para PiP:
  - antes tomaba `youtubePlaybackRenderState` completo
  - para PiP solo hace falta el booleano real de reproducción/autoplay del item actual
- Polling confirmado como deuda visible:
  - YouTube overlay: `250ms` para posición y `650ms` para duración
  - Preview local: `900ms` para progreso activo
- El módulo `benchmark` ya existe y quedó listo para ampliarse; hasta ahora medía solo:
  - startup
  - tabs/feed/playback simple

## Auditoría final de lag residual 2026-05-18
- `YouTubeTabContent` seguía siendo un host demasiado ancho:
  - observaba player
  - comentario
  - feed/sugerencias
  - sheet de descarga
  - eso reinyectaba trabajo al scroll del feed cuando cambiaban cosas del watch screen
- `youtubeFeedProjection` servía tanto para la pantalla completa como para la lista visible:
  - el watch-next visible se recalculaba con la misma proyección que el resto del feed
  - el costo no estaba en Compose puro, sino en seguir usando una proyección demasiado general
- El overlay de Buscar ya había mejorado, pero el corpus de fallback seguía siendo una oportunidad de recorte:
  - faltaba fijarlo en una proyección reutilizable
  - y dejar de rearmar mezcla por tecla más allá de lo necesario
- Reproducir ya dejó atrás la mayor parte del polling agresivo, pero la biblioteca local sigue siendo el cuello residual más claro fuera de YouTube.
## Rendimiento residual 2026-05-18 - benchmark y Reproducir
- `SnapMusicMacrobenchmark` quedo ampliado para medir mejor:
  - tabs de Inicio
  - Buscar -> YouTube con resultados reales
  - watch/minimize
  - scroll largo en Reproducir con vuelta atras
- La raiz de `PreviewScreen` seguia observando `previewState` completo solo para telemetria de escena.
- Eso implicaba riesgo de recomposicion lateral en toda la pantalla de Reproducir por progreso, metadata o cambios del medio actual.
- La salida correcta fue crear `PreviewPerformanceUiState` y dejar en la raiz solo `isReady`/`isVideo`.
- El cuello residual dominante ya no es YouTube base sino:
  - miniaturas locales
  - scroll largo de biblioteca
  - restauracion del mini player con lista extensa
- Otra causa residual confirmada:
  - el mini reproductor de Preview siempre ejecutaba `navigateTo(Preview)` incluso si la ruta actual ya era `Preview`
  - eso metia restauracion redundante del `NavHost` y costo lateral innecesario al volver desde el mini player dentro de Reproducir
- Ajuste aplicado:
  - el mini player de Preview y el de YouTube ahora solo navegan si la ruta actual realmente cambio
  - en Reproducir se simplifico el resaltado del item activo y se abarato la carga de miniaturas locales

## Bug de descarga desde otro video del feed 2026-05-19
- Causa raiz confirmada:
  - `prepareYouTubeDownload(item)` reutilizaba `youtubeState.featured` y escondia `showPlayer/showMiniPlayer`
  - al pedir descargar otro item del feed, forzaba un pseudo-cambio de featured para resolver formatos
  - eso cortaba la capa visual del stream actual y podia dejar el sheet sin opciones o la pantalla sin player/mini player
- Correccion aplicada:
  - se separo el estado de descarga en `YouTubeDownloadSheetState`
  - resolver formatos de otro item ya no toca el stream actual ni la visibilidad del player
  - `enqueueYoutubeVariant()` ahora usa el media del sheet en vez de depender del `featured` en reproduccion
- Otra correccion relacionada:
  - la notificacion de reproduccion ahora abre `ROUTE_PLAYBACK`
  - `MediaSession` ya tiene `sessionActivity`
  - al tocar la notificacion se restaura el stream activo correcto sin pasar por rutas de descargas

## Branding en miniatura de notificacion de reproduccion 2026-05-19
- La miniatura del playback notification venia solo desde `artworkUri` del `MediaMetadata`.
- Para acercarlo al estilo SnapTube sin tocar la logica del player, se agrego `PlaybackArtworkBadgeHelper`.
- El helper:
  - carga la miniatura en `Dispatchers.IO`
  - la reduce a un maximo razonable
  - superpone `playback_badge_logo.png` en la esquina inferior derecha
  - cachea el resultado en memoria
- YouTube y Preview local ahora publican `artworkData` badged en vez de depender solo de la URL cruda cuando el badge ya esta listo.

## Continuidad local foreground/background 2026-05-20
- La reproduccion local compartia `SnapMusicPlaybackService` con YouTube, pero no persistia snapshot propio.
- Antes de esta slice, la reentrada desde `ROUTE_PLAYBACK` dependia demasiado del estado vivo del `NavHost`:
  - si la app estaba fria o el `ViewModel` habia perdido visibilidad local, la notificacion podia no restaurar la cancion correcta
  - la cola local no siempre sobrevivía a minimizar/restaurar ni a la reentrada desde sistema
- La raiz del problema estaba en dos huecos:
  - faltaba snapshot local persistido, paralelo al de YouTube
  - la cola local para `MediaController` podia degradarse a un solo item si la biblioteca todavia no estaba cargada
- La correccion correcta exige:
  - `PreviewPlaybackSnapshot` persistido en preferencias
  - codec propio para cola local, item actual, posicion y mini/detail
  - fallback de cola local desde snapshot cuando `MediaStore` todavia no devolvio la biblioteca
  - resolucion de `ROUTE_PLAYBACK` por prioridad: preview viva, snapshot local, YouTube vivo, snapshot YouTube
