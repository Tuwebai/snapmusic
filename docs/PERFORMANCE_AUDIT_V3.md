# Auditoría de rendimiento V3

## Resumen
- La app sigue cargando jank estructural por recomposición amplia y observación duplicada del player.
- El hotspot más claro hoy estaba en Preview: había dos productores de estado de playback al mismo tiempo.
- También seguían faltando anotaciones `@Immutable` en contratos que Compose usa en rutas calientes.
- La raíz de la app seguía observando preferencias más anchas de lo necesario.

## Culpables confirmados
1. `feature/preview/PreviewPlaybackUi.kt`
   - `rememberPreviewPlaybackState()` mantenía `Player.Listener` + polling propio.
   - La tarjeta de video además armaba otro estado para overlay.
   - Impacto: trabajo duplicado, más snapshots y más recomposición durante reproducción local.

2. `feature/player/PlaybackOverlayState.kt`
   - Seguía pollingeando aunque el overlay no necesitara progreso visible.
   - Impacto: loops vivos sin valor real cuando los controles estaban ocultos.

3. `core/model/Models.kt` y `feature/home/HomeUiState.kt`
   - Faltaban `@Immutable` en estados de playback/snapshots/badge.
   - Impacto: Compose no puede podar recomposición con la misma agresividad.

4. `MainActivity.kt`
   - Observaba `UserPreferences` completo para tema.
   - Impacto: cualquier cambio de preferencias podía tocar la raíz visual.

## Remediación aplicada en esta ola
- Preview ahora usa un solo productor de estado de reproducción por superficie activa.
- El overlay compartido pasa a pollinear progreso solo cuando realmente hace falta.
- Se agregaron `@Immutable` a contratos de playback que seguían inestables.
- La raíz de actividad ahora observa solo `themeMode`.
- YouTube ahora separa:
  - sesión de reproducción
  - seek explícito por `requestId`
  - autoplay/play-pause
- El controller ya no se vuelve a armar por progreso persistido ni por mezclar seek/autoplay con setup de media item.

## Validación requerida
- `:app:compileDebugKotlin`
- `:app:testDebugUnitTest`
- `:benchmark:assembleBenchmark`
- Macrobenchmark:
  - `homeFeedTabsAndPlayback`
  - `searchToYoutubeResults`
  - `youtubeWatchAndMiniplayer`
  - `youtubeFullscreenToggleAndReturn`
  - `previewLibraryAndPlayback`

## Pendiente para la siguiente ola
- Bajar más collectors de `PreviewScreen` y `SnapMusicNavHost` a hosts hoja.
- Revalidar `gfxinfo` y `benchmark` en dispositivo medio con esta base nueva.
